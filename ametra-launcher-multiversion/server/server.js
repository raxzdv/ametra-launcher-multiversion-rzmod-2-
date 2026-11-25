'use strict';
// Servidor de cuentas de Ametra. Sin dependencias: solo Node 18+.
//   node server.js                       → arranca el servidor
//   node server.js admin <usuario> on    → hace admin a una cuenta (con el servidor PARADO)
//   node server.js admin <usuario> off
//   node server.js reset <usuario> <contraseña nueva>   (con el servidor PARADO)
//   node server.js list                  → lista las cuentas
// Variables: TEBEX_PUBLIC_TOKEN, TEBEX_WEBHOOK_SECRET, PUBLIC_URL (pagos), PORT (8787), HOST (127.0.0.1), DATA_DIR, ADMIN_USERS (usuarios admin separados por comas), TRUST_PROXY=1
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const vm = require('vm');

const PORT = Number(process.env.PORT) || 8787;
const HOST = process.env.HOST || '127.0.0.1';
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'accounts.json');
const TRUST_PROXY = process.env.TRUST_PROXY === '1';
const ENV_ADMINS = new Set(String(process.env.ADMIN_USERS || '').split(',').map((s) => s.trim().toLowerCase()).filter(Boolean));

const WELCOME_GEMS = 500, DEAL_OFF = 0.3, SET_OFF = 0.15, DAILY_EARN_CAP = 1200, GEM_PER_MIN = 6, GIFTS_PER_DAY = 20;
const SESSION_MS = 30 * 864e5, MAX_SESSIONS = 5, MAX_NOTES = 50, MAX_HISTORY = 60, MAX_TX = 40, TX_TTL = 10 * 60e3;
const RESERVED = new Set(['ametra', 'admin', 'administrador', 'dev', 'developer', 'staff', 'system', 'sistema', 'root', 'mod']);

// ---------- Catálogo (precios) ----------
// Se lee de assets/cosmetics/data.js (el mismo que usa el launcher) y se guarda en catalog.json para poder
// subir solo la carpeta server/ al hosting.
function loadCatalog() {
  const cache = path.join(__dirname, 'catalog.json');
  const src = process.env.CATALOG || path.join(__dirname, '..', 'assets', 'cosmetics', 'data.js');
  let list = null;
  try {
    const data = vm.runInNewContext(fs.readFileSync(src, 'utf8') + '\n;COS_DATA', {}, { timeout: 2000 });
    list = data.map((c) => ({ cat: String(c.cat), id: String(c.id), name: String(c.name), price: Number(c.price) || 0 }));
    try { fs.writeFileSync(cache, JSON.stringify(list)); } catch { /* carpeta de solo lectura */ }
  } catch {
    try { list = JSON.parse(fs.readFileSync(cache, 'utf8')); } catch { /* sin catálogo */ }
  }
  if (!Array.isArray(list) || !list.length) { console.error('No encuentro el catálogo de cosméticos (assets/cosmetics/data.js o server/catalog.json).'); process.exit(1); }
  return list;
}
const CATALOG = loadCatalog();
const find = (cat, id) => CATALOG.find((c) => c.cat === cat && c.id === id) || null;

// ---------- Base de datos (un JSON, escritura atómica) ----------
fs.mkdirSync(DATA_DIR, { recursive: true });
let DB = { users: {} };
try { DB = JSON.parse(fs.readFileSync(DB_FILE, 'utf8')); if (!DB.users) DB.users = {}; } catch { /* primera vez */ }
function persist() {
  const tmp = DB_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(DB));
  fs.renameSync(tmp, DB_FILE);
}
const TOKENS = new Map(); // sha256(token) -> clave de usuario
const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');
function indexTokens() {
  TOKENS.clear();
  const now = Date.now();
  for (const [k, u] of Object.entries(DB.users)) {
    if (!Array.isArray(u.history)) u.history = []; // cuentas creadas antes del historial de compras
    if (!Array.isArray(u.tx)) u.tx = [];
    u.sessions = (u.sessions || []).filter((s) => s.exp > now);
    u.sessions.forEach((s) => TOKENS.set(s.h, k));
  }
}
indexTokens();

// ---------- Utilidades ----------
const utcDay = (d = new Date()) => d.toISOString().slice(0, 10);
const nextMidnight = () => { const d = new Date(); d.setUTCHours(24, 0, 0, 0); return d.getTime(); };
const isAdmin = (u) => !!u && (u.admin === true || ENV_ADMINS.has(u.name.toLowerCase()));
class HttpError extends Error { constructor(status, msg) { super(msg); this.status = status; } }
const bad = (msg, status = 400) => new HttpError(status, msg);

const hashPw = (pw, salt) => new Promise((res, rej) => crypto.scrypt(String(pw), salt, 32, (e, k) => (e ? rej(e) : res(k))));
const DUMMY_SALT = crypto.randomBytes(16).toString('hex');

const buckets = new Map();
function limit(key, max, ms) {
  const now = Date.now();
  let b = buckets.get(key);
  if (!b || b.t < now) { b = { n: 0, t: now + ms }; buckets.set(key, b); }
  if (++b.n > max) throw bad('Demasiados intentos. Espera un poco.', 429);
}
setInterval(() => { const now = Date.now(); for (const [k, b] of buckets) if (b.t < now) buckets.delete(k); }, 60000).unref();

// ---------- Ofertas y vistas ----------
function dealFor(u) {
  const pool = CATALOG.filter((c) => c.price > 0 && !u.owned.includes(c.cat + ':' + c.id));
  if (!pool.length) return null;
  let h = 0; for (const ch of utcDay()) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return pool[h % pool.length];
}
const dealPrice = (it) => Math.round(it.price * (1 - DEAL_OFF));
function priceFor(u, it) { const d = dealFor(u); return d && d.cat === it.cat && d.id === it.id ? dealPrice(it) : it.price; }

// Sets: las piezas "angel_*", "hell_*" y "void_*" forman un set. Comprar las que te faltan de golpe cuesta SET_OFF menos.
const SET_KEYS = ['angel', 'hell', 'void'];
const setOf = (c) => { const m = /^(angel|hell|void)_/.exec(c.id); return m ? m[1] : null; };
function setQuote(u, key) {
  if (!SET_KEYS.includes(key)) throw bad('Ese set no existe.');
  const pieces = CATALOG.filter((c) => c.price > 0 && setOf(c) === key);
  const missing = pieces.filter((c) => !u.owned.includes(c.cat + ':' + c.id));
  const base = missing.reduce((n, c) => n + c.price, 0);
  return { key, pieces, missing, base, price: Math.round(base * (1 - SET_OFF)) };
}

// Compras idempotentes: el launcher manda un "rid" por intento. Si la respuesta se pierde (corte de red, timeout) y reintenta
// con el mismo rid, el servidor devuelve el resultado de la primera vez en vez de cobrar otra vez.
const cleanRid = (b) => (/^[\w-]{8,64}$/.test(String(b.rid || '')) ? String(b.rid) : null);
const expectOf = (b) => (b.expect === undefined || b.expect === null || !Number.isFinite(Number(b.expect)) ? null : Math.trunc(Number(b.expect)));
function replayOf(u, rid) { return rid ? u.tx.find((t) => t.rid === rid && Date.now() - t.at < TX_TTL) || null : null; }
function remember(u, rid, result) { if (rid) { u.tx.push({ rid, at: Date.now(), result }); if (u.tx.length > MAX_TX) u.tx.splice(0, u.tx.length - MAX_TX); } }
function addHistory(u, h) { u.history.push({ ...h, at: Date.now() }); if (u.history.length > MAX_HISTORY) u.history.splice(0, u.history.length - MAX_HISTORY); }
function view(u) {
  const d = dealFor(u);
  return {
    user: u.name, admin: isAdmin(u), gems: u.gems, owned: u.owned.slice(),
    daily: { claimed: u.daily.day === utcDay(), streak: u.daily.streak },
    deal: d ? { cat: d.cat, id: d.id, base: d.price, price: dealPrice(d) } : null,
    dealEnds: nextMidnight(),
    notes: u.notes.slice(-20).reverse(),
    history: u.history.slice(-15).reverse()
  };
}
function note(u, n) { u.notes.push({ ...n, at: Date.now(), seen: false }); if (u.notes.length > MAX_NOTES) u.notes.splice(0, u.notes.length - MAX_NOTES); }
function userByName(name) {
  const u = DB.users[String(name || '').trim().toLowerCase()];
  if (!u) throw bad('No existe ninguna cuenta con ese nombre.', 404);
  return u;
}
function newUser(name, salt, hash) {
  return { name, salt, hash, created: Date.now(), gems: WELCOME_GEMS, owned: [], daily: { day: '', streak: 0 }, earn: { last: 0, day: '', gems: 0 }, gifts: { day: '', n: 0 }, notes: [], sessions: [], history: [], tx: [], admin: false };
}
function startSession(key, u) {
  const token = crypto.randomBytes(32).toString('hex');
  u.sessions.push({ h: sha(token), exp: Date.now() + SESSION_MS });
  while (u.sessions.length > MAX_SESSIONS) TOKENS.delete(u.sessions.shift().h);
  TOKENS.set(sha(token), key);
  return token;
}

// ---------- Rutas ----------
const R = {};
R['POST /api/register'] = async ({ body, ip }) => {
  limit('reg:' + ip, 5, 3600e3);
  const name = String(body.username || '').trim();
  const pw = String(body.password || '');
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw bad('El usuario debe tener de 3 a 16 caracteres: letras, números o _.');
  if (pw.length < 6 || pw.length > 128) throw bad('La contraseña debe tener entre 6 y 128 caracteres.');
  const key = name.toLowerCase();
  if (RESERVED.has(key) && !ENV_ADMINS.has(key)) throw bad('Ese nombre está reservado.');
  if (DB.users[key]) throw bad('Ese usuario ya existe.', 409);
  const salt = crypto.randomBytes(16).toString('hex');
  const u = newUser(name, salt, (await hashPw(pw, salt)).toString('hex'));
  DB.users[key] = u;
  const token = startSession(key, u);
  persist();
  return { token, ...view(u) };
};
R['POST /api/login'] = async ({ body, ip }) => {
  limit('login-ip:' + ip, 30, 600e3);
  const key = String(body.username || '').trim().toLowerCase();
  limit('login-u:' + key, 10, 600e3);
  const u = DB.users[key];
  const calc = await hashPw(String(body.password || '').slice(0, 128), u ? u.salt : DUMMY_SALT);
  if (!u || !crypto.timingSafeEqual(calc, Buffer.from(u.hash, 'hex'))) throw bad('Usuario o contraseña incorrectos.', 401);
  const token = startSession(key, u);
  persist();
  return { token, ...view(u) };
};
R['POST /api/logout'] = ({ u, token }) => {
  u.sessions = u.sessions.filter((s) => s.h !== sha(token));
  TOKENS.delete(sha(token));
  persist();
  return { ok: true };
};
R['GET /api/me'] = ({ u }) => view(u);
R['POST /api/seen'] = ({ u }) => { u.notes.forEach((n) => { n.seen = true; }); persist(); return view(u); };

R['POST /api/buy'] = ({ u, body }) => {
  limit('buy:' + u.name, 40, 60e3);
  const rid = cleanRid(body);
  const prev = replayOf(u, rid);
  if (prev) return { ...view(u), bought: prev.result, replay: true };
  const it = find(String(body.cat), String(body.id));
  if (!it || it.price <= 0) throw bad('Ese cosmético no se puede comprar.');
  if (u.owned.includes(it.cat + ':' + it.id)) throw bad('Ya lo tienes.');
  const price = priceFor(u, it);
  const exp = expectOf(body);
  if (exp !== null && exp !== price) throw bad(`El precio cambió: ahora cuesta ${price} gemas. No se te cobró nada; revisa y vuelve a intentarlo.`, 409);
  if (u.gems < price) throw bad(`Te faltan ${price - u.gems} gemas.`);
  u.gems -= price; u.owned.push(it.cat + ':' + it.id);
  const result = { cat: it.cat, id: it.id, price };
  addHistory(u, { k: 'buy', cat: it.cat, id: it.id, price });
  remember(u, rid, result);
  persist();
  return { ...view(u), bought: result };
};
// Comprar de golpe lo que te falta de un set (con descuento). Todo o nada.
R['POST /api/buy-set'] = ({ u, body }) => {
  limit('buy:' + u.name, 40, 60e3);
  const rid = cleanRid(body);
  const prev = replayOf(u, rid);
  if (prev) return { ...view(u), bought: prev.result, replay: true };
  const q = setQuote(u, String(body.set));
  if (q.missing.length < 2) throw bad('Te falta una pieza o ninguna de ese set: cómprala suelta.');
  const exp = expectOf(body);
  if (exp !== null && exp !== q.price) throw bad(`El precio del set cambió: ahora cuesta ${q.price} gemas. No se te cobró nada; revisa y vuelve a intentarlo.`, 409);
  if (u.gems < q.price) throw bad(`Te faltan ${q.price - u.gems} gemas.`);
  u.gems -= q.price;
  q.missing.forEach((c) => u.owned.push(c.cat + ':' + c.id));
  const result = { set: q.key, count: q.missing.length, price: q.price, items: q.missing.map((c) => ({ cat: c.cat, id: c.id })) };
  addHistory(u, { k: 'set', set: q.key, n: q.missing.length, price: q.price });
  remember(u, rid, result);
  persist();
  return { ...view(u), bought: result };
};
R['POST /api/gift'] = ({ u, body }) => {
  limit('buy:' + u.name, 40, 60e3);
  const rid = cleanRid(body);
  const prev = replayOf(u, rid);
  if (prev) return { ...view(u), gifted: prev.result, replay: true };
  const it = find(String(body.cat), String(body.id));
  if (!it || it.price <= 0) throw bad('Ese cosmético no se puede regalar.');
  const to = userByName(body.to);
  if (to === u) throw bad('No puedes regalarte cosas a ti mismo: cómpralo desde la tienda.');
  const k = it.cat + ':' + it.id;
  if (to.owned.includes(k)) throw bad(`${to.name} ya tiene ese cosmético.`);
  const day = utcDay();
  if (u.gifts.day !== day) u.gifts = { day, n: 0 };
  if (u.gifts.n >= GIFTS_PER_DAY) throw bad('Has llegado al máximo de regalos de hoy.', 429);
  const price = priceFor(u, it);
  const exp = expectOf(body);
  if (exp !== null && exp !== price) throw bad(`El precio cambió: ahora cuesta ${price} gemas. No se te cobró nada; revisa y vuelve a intentarlo.`, 409);
  if (u.gems < price) throw bad(`Te faltan ${price - u.gems} gemas.`);
  u.gems -= price; u.gifts.n++;
  to.owned.push(k);
  note(to, { t: 'gift', from: u.name, cat: it.cat, id: it.id });
  const result = { to: to.name, cat: it.cat, id: it.id, price };
  addHistory(u, { k: 'gift', cat: it.cat, id: it.id, price, to: to.name });
  remember(u, rid, result);
  persist();
  return { ...view(u), gifted: result };
};
R['POST /api/daily'] = ({ u }) => {
  const day = utcDay();
  if (u.daily.day === day) throw bad('Ya reclamaste el bono de hoy.');
  const streak = u.daily.day === utcDay(new Date(Date.now() - 864e5)) ? Math.min(u.daily.streak + 1, 7) : 1;
  const g = 100 + (streak - 1) * 20;
  u.daily = { day, streak }; u.gems += g;
  persist();
  return { ...view(u), got: g };
};
// El launcher cuenta el tiempo jugado; el servidor solo da gemas por el tiempo que de verdad ha pasado
// desde el último cobro y con un tope diario.
R['POST /api/earn'] = ({ u, body }) => {
  const now = Date.now(), day = utcDay();
  if (u.earn.day !== day) u.earn = { last: u.earn.last, day, gems: 0 };
  const claimed = Math.floor(Math.min(Math.max(Number(body.ms) || 0, 0), 4 * 3600e3) / 60000);
  const sinceLast = u.earn.last ? Math.floor((now - u.earn.last) / 60000) : 240;
  let mins = Math.min(claimed, Math.max(sinceLast, 0));
  mins = Math.min(mins, Math.floor((DAILY_EARN_CAP - u.earn.gems) / GEM_PER_MIN));
  if (mins > 0) { const g = mins * GEM_PER_MIN; u.gems += g; u.earn.gems += g; u.earn.last = now; persist(); }
  return view(u);
};


// ---------- Pagos con Tebex (gemas) ----------
// Variables: TEBEX_PUBLIC_TOKEN (token público de tu webstore), TEBEX_WEBHOOK_SECRET (secreto del endpoint de webhooks),
// PUBLIC_URL (https://tu-dominio, para las páginas de retorno), TEBEX_CHECK_IP=1 (opcional: solo acepta las IP de Tebex).
// Los paquetes se definen en tebex-packs.json: { id, gems, price (solo para mostrar), tebex (ID del paquete en Tebex) }.
// El launcher manda el id del paquete, nunca el importe; las gemas solo se acreditan al llegar el webhook firmado por Tebex,
// una sola vez por transacción.
const TEBEX_TOKEN = process.env.TEBEX_PUBLIC_TOKEN || '', TEBEX_WH = process.env.TEBEX_WEBHOOK_SECRET || '';
const PUBLIC_URL = (process.env.PUBLIC_URL || '').replace(/\/+$/, ''), CURRENCY = (process.env.TEBEX_CURRENCY || 'usd').toLowerCase();
const TEBEX_IPS = new Set(['18.209.80.3', '54.87.231.232']);
let PACKS = [];
try { PACKS = JSON.parse(fs.readFileSync(path.join(__dirname, 'tebex-packs.json'), 'utf8')).map((x) => ({ id: String(x.id), gems: Number(x.gems) || 0, cents: Math.round((Number(x.price) || 0) * 100), tebex: String(x.tebex || '') })).filter((x) => x.id && x.gems > 0 && x.tebex); } catch { /* sin paquetes */ }
if (!DB.payments) DB.payments = {};   // transaction_id de Tebex -> { user, gems, at, refunded }
if (!DB.baskets) DB.baskets = {};     // cesta de Tebex -> { user, pack, at }
const payEnabled = () => !!(TEBEX_TOKEN && TEBEX_WH && PUBLIC_URL && PACKS.length);
const tebexApi = async (route, body) => {
  let r, j = {};
  try { r = await fetch(`https://headless.tebex.io/api/accounts/${encodeURIComponent(TEBEX_TOKEN)}/${route}`, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) }); j = await r.json(); }
  catch { throw bad('No se pudo contactar con Tebex. Inténtalo en un momento.', 502); }
  if (!r.ok || !j.data) { console.error('Tebex', r.status, JSON.stringify(j).slice(0, 300)); throw bad('Tebex rechazó la operación.', 502); }
  return j.data;
};

R['GET /api/packs'] = () => ({ enabled: payEnabled(), currency: CURRENCY, packs: PACKS.map((x) => ({ id: x.id, gems: x.gems, cents: x.cents })) });
R['POST /api/pay/checkout'] = async ({ u, body }) => {
  if (!payEnabled()) throw bad('Los pagos no están activados todavía.', 503);
  limit('pay:' + u.name, 10, 600e3);
  const pk = PACKS.find((x) => x.id === String(body.pack));
  if (!pk) throw bad('Paquete desconocido.');
  const mc = /^\w{3,16}$/.test(String(body.mcname || '')) ? String(body.mcname) : undefined; // las tiendas Minecraft de Tebex piden usuario
  const key = u.name.toLowerCase();
  let basket = await tebexApi('baskets', { complete_url: PUBLIC_URL + '/pago/ok', cancel_url: PUBLIC_URL + '/pago/cancelado', complete_auto_redirect: false, custom: { user: key, pack: pk.id }, ...(mc ? { username: mc } : {}) });
  DB.baskets[basket.ident] = { user: key, pack: pk.id, at: Date.now() };
  const ks = Object.keys(DB.baskets); if (ks.length > 2000) delete DB.baskets[ks[0]];
  const withPkg = await tebexApi(`${encodeURIComponent(basket.ident)}/packages`, { package_id: pk.tebex, quantity: 1 });
  const url = (withPkg.links && withPkg.links.checkout) || (basket.links && basket.links.checkout);
  if (!url) throw bad('Tebex no devolvió el enlace de pago.', 502);
  persist();
  return { url };
};

// Firma de Tebex: cabecera X-Signature = HMAC-SHA256(secreto, SHA256(cuerpo crudo)). Se acepta el hash como texto hex o como bytes.
function tebexEvent(raw, header) {
  if (!TEBEX_WH) throw bad('Webhook no configurado.', 503);
  const h = crypto.createHash('sha256').update(raw).digest();
  const given = String(header || '').trim().toLowerCase();
  const g = /^[a-f0-9]{64}$/.test(given) ? Buffer.from(given, 'hex') : Buffer.alloc(0);
  const ok = [h.toString('hex'), h].some((m) => { const c = crypto.createHmac('sha256', TEBEX_WH).update(m).digest(); return g.length === c.length && crypto.timingSafeEqual(g, c); });
  if (!ok) throw bad('Firma inválida.', 400);
  return JSON.parse(raw);
}
function unmatched(why, ev) { // pago que no se pudo acreditar solo: queda anotado para revisarlo a mano
  console.error('Pago Tebex sin acreditar (' + why + '):', JSON.stringify(ev).slice(0, 500));
  try { fs.appendFileSync(path.join(DATA_DIR, 'pagos-sin-acreditar.log'), new Date().toISOString() + ' ' + why + ' ' + JSON.stringify(ev) + '\n'); } catch { /* sin permiso */ }
}
function tebexUser(sub) { // la cuenta Ametra viaja en el "custom" de la cesta; según el evento puede venir en la compra o en cada producto
  const cands = [sub.custom, ...(Array.isArray(sub.products) ? sub.products.map((x) => x && x.custom) : [])];
  for (const c of cands) { const k = c && typeof c === 'object' && String(c.user || '').toLowerCase(); if (k && DB.users[k]) return k; }
  return null;
}
function handleTebex(ev) {
  const sub = ev.subject || {}, tx = String(sub.transaction_id || '');
  if (ev.type === 'payment.completed') {
    if (!tx || DB.payments[tx]) return; // ya acreditado: Tebex reintenta si no recibe 2xx
    const user = tebexUser(sub);
    // gemas según el paquete comprado (no según lo que diga el cliente)
    const gems = (Array.isArray(sub.products) ? sub.products : []).reduce((n, x) => { const pk = PACKS.find((p) => p.tebex === String(x && x.id)); return n + (pk ? pk.gems * (Number(x.quantity) || 1) : 0); }, 0);
    if (!user || !gems) return unmatched(!user ? 'sin usuario' : 'paquete desconocido', ev);
    const u = DB.users[user];
    u.gems += gems;
    DB.payments[tx] = { user, gems, at: Date.now() };
    addHistory(u, { k: 'gems', gems, price: 0 });
    note(u, { t: 'gems', from: 'Tienda', amount: gems });
    persist();
  } else if (ev.type === 'payment.refunded' || ev.type === 'payment.dispute.opened') { // reembolso o contracargo: se quitan esas gemas
    const p = DB.payments[tx];
    if (!p || p.refunded) return;
    const u = DB.users[p.user]; if (u) u.gems = Math.max(0, u.gems - p.gems);
    p.refunded = true; persist();
  }
}

// ---------- Panel developer ----------
const needAdmin = (u) => { if (!isAdmin(u)) throw bad('Esta cuenta no es developer.', 403); };
function pickItems(body) {
  if (body.all === true) return CATALOG.filter((c) => c.price > 0);
  const list = Array.isArray(body.items) ? body.items : [{ cat: body.cat, id: body.id }];
  const out = list.slice(0, 100).map((x) => find(String(x && x.cat), String(x && x.id)));
  if (!out.length || out.some((x) => !x)) throw bad('Cosmético desconocido.');
  return out;
}
R['POST /api/admin/lookup'] = ({ u, body }) => {
  needAdmin(u);
  const t = userByName(body.user);
  return { user: t.name, admin: isAdmin(t), gems: t.gems, owned: t.owned.slice(), created: t.created };
};
R['POST /api/admin/grant'] = ({ u, body }) => {
  needAdmin(u);
  const t = userByName(body.user);
  const items = pickItems(body).filter((c) => !t.owned.includes(c.cat + ':' + c.id));
  items.forEach((c) => t.owned.push(c.cat + ':' + c.id));
  if (items.length === 1) note(t, { t: 'grant', from: u.name, cat: items[0].cat, id: items[0].id });
  else if (items.length) note(t, { t: 'grant', from: u.name, count: items.length });
  persist();
  return { user: t.name, added: items.length, owned: t.owned.length };
};
R['POST /api/admin/revoke'] = ({ u, body }) => {
  needAdmin(u);
  const t = userByName(body.user);
  const keys = new Set(pickItems(body).map((c) => c.cat + ':' + c.id));
  const before = t.owned.length;
  t.owned = t.owned.filter((k) => !keys.has(k));
  persist();
  return { user: t.name, removed: before - t.owned.length, owned: t.owned.length };
};
R['POST /api/admin/gems'] = ({ u, body }) => {
  needAdmin(u);
  const t = userByName(body.user);
  const n = Math.trunc(Number(body.amount));
  if (!Number.isFinite(n) || n === 0 || Math.abs(n) > 1e6) throw bad('Pon una cantidad de gemas (puede ser negativa).');
  t.gems = Math.max(0, t.gems + n);
  if (n > 0) note(t, { t: 'gems', from: u.name, amount: n });
  persist();
  return { user: t.name, gems: t.gems };
};

// ---------- HTTP ----------
const PUBLIC = new Set(['POST /api/register', 'POST /api/login', 'GET /api/packs']);
function readBody(req) {
  return new Promise((resolve, reject) => {
    let n = 0; const chunks = [];
    req.on('data', (c) => { n += c.length; if (n > 4096) { reject(bad('Petición demasiado grande.', 413)); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch { reject(bad('JSON inválido.')); } });
    req.on('error', reject);
  });
}
const server = http.createServer(async (req, res) => {
  const send = (status, obj) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(obj)); };
  try {
    const ip = (TRUST_PROXY && String(req.headers['x-forwarded-for'] || '').split(',')[0].trim()) || req.socket.remoteAddress || '?';
    limit('all:' + ip, 300, 60e3);
    const url = new URL(req.url, 'http://x');
    if (req.method === 'GET' && url.pathname === '/api/health') return send(200, { ok: true, catalog: CATALOG.length });
    const route = `${req.method} ${url.pathname}`;
    if (req.method === 'GET' && (url.pathname === '/pago/ok' || url.pathname === '/pago/cancelado')) {
      const ok = url.pathname === '/pago/ok';
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(`<meta charset=utf-8><title>Ametra</title><body style="font:18px system-ui;background:#0b0b10;color:#fff;display:grid;place-items:center;height:100vh;margin:0;text-align:center"><div><h1>${ok ? '¡Pago recibido!' : 'Pago cancelado'}</h1><p>${ok ? 'Vuelve al launcher: tus gemas aparecen en unos segundos.' : 'No se te cobró nada. Ya puedes cerrar esta pestaña.'}</p></div>`);
    }
    if (route === 'POST /api/pay/webhook') { // Tebex: cuerpo crudo para verificar la firma
      if (process.env.TEBEX_CHECK_IP === '1' && !TEBEX_IPS.has(String(ip).replace(/^::ffff:/, ''))) return send(404, { error: 'No existe.' });
      const chunks = []; let n = 0;
      for await (const c of req) { n += c.length; if (n > 262144) return send(413, { error: 'Demasiado grande.' }); chunks.push(c); }
      const ev = tebexEvent(Buffer.concat(chunks).toString('utf8'), req.headers['x-signature']);
      if (ev.type === 'validation.webhook') return send(200, { id: ev.id }); // Tebex valida el endpoint así
      handleTebex(ev);
      return send(200, { received: true });
    }
    const fn = R[route];
    if (!fn) return send(404, { error: 'No existe.' });
    const body = req.method === 'POST' ? await readBody(req) : {};
    let u = null, token = null;
    if (!PUBLIC.has(route)) {
      token = (/^Bearer ([a-f0-9]{64})$/.exec(req.headers.authorization || '') || [])[1];
      const key = token && TOKENS.get(sha(token));
      u = key && DB.users[key];
      const s = u && u.sessions.find((x) => x.h === sha(token));
      if (!u || !s || s.exp < Date.now()) return send(401, { error: 'Sesión caducada. Inicia sesión otra vez.' });
    }
    send(200, await fn({ req, body, ip, u, token }));
  } catch (e) {
    if (e instanceof HttpError) return send(e.status, { error: e.message });
    console.error(e);
    send(500, { error: 'Error del servidor.' });
  }
});

// ---------- Comandos de administración ----------
const [, , cmd, a, b] = process.argv;
if (cmd === 'admin' && a) {
  const u = DB.users[a.toLowerCase()];
  if (!u) { console.error('No existe esa cuenta.'); process.exit(1); }
  u.admin = b !== 'off'; persist();
  console.log(`${u.name}: admin ${u.admin ? 'ACTIVADO' : 'desactivado'}`);
} else if (cmd === 'reset' && a && b) {
  const u = DB.users[a.toLowerCase()];
  if (!u || b.length < 6) { console.error('Cuenta inexistente o contraseña de menos de 6 caracteres.'); process.exit(1); }
  const salt = crypto.randomBytes(16).toString('hex');
  hashPw(b, salt).then((h) => { u.salt = salt; u.hash = h.toString('hex'); u.sessions = []; persist(); console.log('Contraseña cambiada para ' + u.name); });
} else if (cmd === 'list') {
  Object.values(DB.users).forEach((u) => console.log(`${u.name}${isAdmin(u) ? ' [admin]' : ''} · ${u.gems} gemas · ${u.owned.length} cosméticos`));
} else if (cmd) {
  console.error('Comandos: admin <usuario> on|off · reset <usuario> <contraseña> · list');
  process.exit(1);
} else {
  server.listen(PORT, HOST, () => console.log(`Cuentas Ametra en http://${HOST}:${PORT} · ${CATALOG.length} cosméticos · ${Object.keys(DB.users).length} cuentas`));
}
if (!cmd) {
  process.on('SIGINT', () => process.exit(0));
}
module.exports = { server };
