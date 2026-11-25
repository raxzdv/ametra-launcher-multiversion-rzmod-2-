const { app, BrowserWindow, ipcMain, shell, dialog, safeStorage } = require('electron');
const path = require('path');
const fs = require('fs');
const { Client, Authenticator } = require('minecraft-launcher-core');
const { Auth } = require('msmc');
const { execFile } = require('child_process');
const os = require('os');
const crypto = require('crypto');
const net = require('net');
const dns = require('dns');
const opt = require('./optimizer');

const ROOT = () => {
  const dir = path.join(app.getPath('appData'), '.ametra');
  const old = path.join(app.getPath('appData'), '.hovyclient'); // nombre anterior: se mueve para no perder instancias
  try { if (!fs.existsSync(dir) && fs.existsSync(old)) fs.renameSync(old, dir); } catch { /* en uso: se usa la carpeta nueva vacía */ }
  return dir;
};
// Archivo de caché de clases (CDS) por instancia y versión de Java: acelera el arranque del juego.
// Si no se puede crear la carpeta, devuelve null y el juego arranca como antes.
const cdsPathFor = (id, major) => {
  try {
    const d = path.join(ROOT(), 'cache', 'cds');
    fs.mkdirSync(d, { recursive: true });
    return path.join(d, `${String(id).replace(/[^\w.-]/g, '_')}-java${major}.jsa`);
  } catch { return null; }
};
const modsDir = (id) => {
  const d = path.join(ROOT(), 'instances', id, 'mods');
  fs.mkdirSync(d, { recursive: true });
  return d;
};
const UA = { 'User-Agent': 'ametra-launcher/1.0' };

let win;
let msAccount = null; // sesión de Microsoft (solo en memoria)

// ---------- Actualización automática del launcher ----------
// Usa electron-updater: mira la última release de GitHub (ver "publish" en package.json).
// Si hay versión nueva, la descarga en segundo plano y se instala al cerrar el launcher.
// Si la librería no está instalada, el launcher sigue funcionando sin actualizarse.
let autoUpdater = null;
try { ({ autoUpdater } = require('electron-updater')); } catch { autoUpdater = null; }
const setSplashStatus = (win2, text) => {
  try { if (win2 && !win2.isDestroyed()) win2.webContents.executeJavaScript(`setStatus(${JSON.stringify(text)})`).catch(() => {}); } catch { /* sin pantalla */ }
};
function checkLauncherUpdate(splash2) {
  if (!autoUpdater || !app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('checking-for-update', () => setSplashStatus(splash2, 'Buscando actualizaciones…'));
  autoUpdater.on('update-available', (info) => setSplashStatus(splash2, `Descargando RzClient ${info.version}…`));
  autoUpdater.on('update-not-available', () => setSplashStatus(splash2, 'Todo al día'));
  autoUpdater.on('update-downloaded', (info) => setSplashStatus(splash2, `Listo: se instalará al cerrar (${info.version})`));
  autoUpdater.on('error', () => setSplashStatus(splash2, 'Sin conexión: se abre la versión actual'));
  autoUpdater.checkForUpdates().catch(() => setSplashStatus(splash2, 'Sin conexión: se abre la versión actual'));
}

function createWindow() {
  // Pantalla de carga animada (splash) mientras se prepara la ventana principal.
  const splash = new BrowserWindow({
    width: 420, height: 340, frame: false, resizable: false, movable: true, show: false,
    backgroundColor: '#050505', alwaysOnTop: true, skipTaskbar: true
  });
  splash.loadFile('splash.html', { query: { v: app.getVersion() } });
  splash.once('ready-to-show', () => splash.show());
  const t0 = Date.now();
  checkLauncherUpdate(splash);

  win = new BrowserWindow({
    width: 1280, height: 760, minWidth: 1100, minHeight: 680, frame: false, resizable: true, backgroundColor: '#050505', show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.loadFile('index.html');
  win.once('ready-to-show', () => {
    const wait = Math.max(0, 2300 - (Date.now() - t0)); // deja ver la animación completa
    setTimeout(() => {
      if (!splash.isDestroyed()) splash.close();
      win.show();
    }, wait);
  });
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

ipcMain.on('win:min', () => win.minimize());
ipcMain.on('win:close', () => win.close());
ipcMain.on('skin:open', () => shell.openExternal('https://www.minecraft.net/msaprofile/mygames/editskin'));

// ---------- Microsoft (premium) ----------
async function msLogin(silent) {
  const file = path.join(app.getPath('userData'), 'ms-session.bin');
  const auth = new Auth('select_account');
  let xbox = null;
  try {
    if (fs.existsSync(file) && safeStorage.isEncryptionAvailable()) {
      xbox = await auth.refresh(safeStorage.decryptString(fs.readFileSync(file)));
    }
  } catch { xbox = null; }
  if (!xbox) {
    if (silent) throw new Error('Sin sesión guardada.');
    xbox = await auth.launch('electron');
  }
  const mc = await xbox.getMinecraft();
  if (safeStorage.isEncryptionAvailable()) fs.writeFileSync(file, safeStorage.encryptString(xbox.save()));
  msAccount = mc.mclc();
  msCheckedAt = Date.now();
  const skins = mc.profile.skins || [];
  const sk = skins.find((s) => s.state === 'ACTIVE') || skins[0];
  let skin = null, slim = false;
  if (sk && sk.url) {
    try {
      skin = 'data:image/png;base64,' + Buffer.from(await (await fetch(sk.url)).arrayBuffer()).toString('base64');
      slim = /slim/i.test(sk.variant || '');
    } catch { skin = null; }
  }
  return { name: mc.profile.name, skin, slim };
}
ipcMain.handle('login:ms', (_e, silent) => msLogin(silent));

// El token de Minecraft dura ~24 h. Si el launcher llevaba horas abierto (o el token ya venía viejo) el servidor
// respondía "La sesión no es válida". Antes de cada Jugar se comprueba contra Mojang y, si no sirve, se renueva
// en silencio con el token de refresco guardado.
let msCheckedAt = 0;
const msToken = () => (msAccount && (msAccount.access_token || msAccount.accessToken)) || '';
async function msTokenValid() {
  const t = msToken();
  if (!t) return null;
  try {
    const r = await fetch('https://api.minecraftservices.com/minecraft/profile', { headers: { Authorization: 'Bearer ' + t, ...UA }, signal: AbortSignal.timeout(8000) });
    if (r.status === 200) return true;
    if (r.status === 401 || r.status === 403) return false;
    if (r.status === 404) return 'nogame'; // cuenta Microsoft sin Minecraft Java
    return null; // 429/5xx: no se sabe, se deja pasar
  } catch { return null; } // sin internet hacia Mojang: no se bloquea el juego por esto
}
async function ensureMsSession(send) {
  if (msAccount && Date.now() - msCheckedAt < 5 * 60e3) return; // validado hace poco
  let ok = msAccount ? await msTokenValid() : false;
  if (ok === 'nogame') throw new Error('Esa cuenta de Microsoft no tiene Minecraft Java. Entra con la cuenta que compró el juego.');
  if (ok === false) {
    send('info', 'Renovando tu sesión de Microsoft…');
    try { await msLogin(true); }
    catch { msAccount = null; throw new Error('Tu sesión de Microsoft caducó. Vuelve a pulsar "Microsoft (premium)" en Cuenta e inicia sesión.'); }
    ok = await msTokenValid();
    if (ok === false) { msAccount = null; throw new Error('Mojang rechazó tu sesión. Cierra sesión y vuelve a entrar con Microsoft (premium).'); }
    if (ok === 'nogame') throw new Error('Esa cuenta de Microsoft no tiene Minecraft Java. Entra con la cuenta que compró el juego.');
  }
  msCheckedAt = Date.now();
}

// ---------- Cambiar skin desde el launcher ----------
let pendingSkin = null;
ipcMain.handle('skin:pick', async () => {
  const r = await dialog.showOpenDialog(win, {
    title: 'Elige tu skin', properties: ['openFile'],
    filters: [{ name: 'Skin (PNG)', extensions: ['png'] }]
  });
  if (r.canceled || !r.filePaths[0]) return null;
  const buf = fs.readFileSync(r.filePaths[0]);
  if (buf.length < 24 || buf.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') return { error: 'Ese archivo no es un PNG válido.' };
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) return { error: 'La skin debe medir 64×64 (o 64×32). La tuya mide ' + w + '×' + h + '.' };
  pendingSkin = buf;
  return { dataUrl: 'data:image/png;base64,' + buf.toString('base64') };
});
ipcMain.handle('skin:upload', async (_e, variant) => {
  if (!pendingSkin) throw new Error('Primero elige una skin.');
  if (!msAccount) throw new Error('Inicia sesión con Microsoft (premium) para cambiar tu skin.');
  const slim = variant === 'slim';
  const send = () => {
    const fd = new FormData();
    fd.append('variant', slim ? 'slim' : 'classic');
    fd.append('file', new Blob([pendingSkin], { type: 'image/png' }), 'skin.png');
    const token = msAccount.access_token || msAccount.accessToken;
    return fetch('https://api.minecraftservices.com/minecraft/profile/skins', { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd });
  };
  let res = await send();
  if (res.status === 401) { await msLogin(true); res = await send(); }
  if (!res.ok) {
    if (res.status === 400) throw new Error('Minecraft rechazó la skin (formato no válido).');
    if (res.status === 401 || res.status === 403) throw new Error('Tu sesión caducó. Vuelve a iniciar sesión con Microsoft.');
    if (res.status === 429) throw new Error('Demasiados cambios seguidos. Espera unos minutos.');
    throw new Error('Minecraft respondió con el error ' + res.status + '.');
  }
  return { skin: 'data:image/png;base64,' + pendingSkin.toString('base64'), slim };
});

// Skin para cuentas NO premium: se guarda en el launcher y se copia a la instancia al jugar; el mod (OfflineSkin) la muestra en el juego.
const offSkinPng = () => path.join(ROOT(), 'offline-skin.png');
const offSkinJson = () => path.join(ROOT(), 'offline-skin.json');
ipcMain.handle('skin:local', (_e, variant) => {
  if (!pendingSkin) throw new Error('Primero elige una skin.');
  const slim = variant === 'slim';
  fs.mkdirSync(ROOT(), { recursive: true });
  fs.writeFileSync(offSkinPng(), pendingSkin);
  fs.writeFileSync(offSkinJson(), JSON.stringify({ slim }));
  return { skin: 'data:image/png;base64,' + pendingSkin.toString('base64'), slim };
});
ipcMain.handle('skin:localClear', () => { try { fs.unlinkSync(offSkinPng()); fs.unlinkSync(offSkinJson()); } catch { /* ya no estaba */ } return true; });
function syncOfflineSkin(id, mode) {
  const d = path.join(instDir(id), 'ametra');
  const png = path.join(d, 'skin.png'), js = path.join(d, 'skin.json');
  try {
    if (mode !== 'ms' && fs.existsSync(offSkinPng())) {
      fs.mkdirSync(d, { recursive: true });
      fs.copyFileSync(offSkinPng(), png);
      fs.writeFileSync(js, fs.existsSync(offSkinJson()) ? fs.readFileSync(offSkinJson(), 'utf8').replace(/\s+/g, '') : '{"slim":false}');
    } else {
      for (const f of [png, js]) if (fs.existsSync(f)) fs.unlinkSync(f); // premium: se usa la skin de Mojang
    }
  } catch { /* la skin es opcional: no debe impedir jugar */ }
}

// ---------- Servidores partner + modo developer ----------
// Para que TODOS los jugadores vean los cambios sin sacar una versión nueva, sube tu partners.json
// a un enlace https (por ejemplo GitHub "raw") y pega la dirección aquí. Si lo dejas vacío se usa el partners.json incluido.
const PARTNERS_URL = '';

const pf = (n) => path.join(app.getPath('userData'), n);
const readJson = (f) => { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return null; } };

function cleanPartners(arr) {
  if (!Array.isArray(arr)) return [];
  let mainTaken = false;
  return arr.slice(0, 30).map((p, i) => {
    p = p || {};
    const host = String(p.host || '').trim();
    if (!/^[\w.-]+(:\d{1,5})?$/.test(host)) return null;
    const url = String(p.url || '').trim();
    return {
      id: String(p.id || 'p' + i).replace(/[^\w-]/g, '').slice(0, 32) || 'p' + i,
      name: String(p.name || host).trim().slice(0, 28),
      host,
      desc: String(p.desc || '').trim().slice(0, 120),
      color: /^#[0-9a-f]{6}$/i.test(String(p.color || '')) ? p.color : '#8a5be0',
      url: /^https:\/\/[^\s]+$/i.test(url) ? url.slice(0, 200) : '',
      // Dirección para Bedrock (si es distinta a la de Java); vacío = se usa la misma
      bedrock: /^[\w.-]+(:\d{1,5})?$/.test(String(p.bedrock || '').trim()) ? String(p.bedrock).trim() : '',
      // Logo propio del partner: imagen PNG/JPG/WebP ya reducida, guardada dentro del propio partners.json
      // Servidor principal: al pulsar Jugar el launcher entra directo a este (solo puede haber uno)
      main: p.main === true && !mainTaken && (mainTaken = true),
      logo: /^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(String(p.logo || '')) && String(p.logo).length < 200000 ? String(p.logo) : ''
    };
  }).filter(Boolean);
}
async function loadPartners() {
  const dev = readJson(pf('partners-dev.json'));
  if (dev && Array.isArray(dev.partners)) return { list: cleanPartners(dev.partners), source: 'dev' };
  if (/^https:\/\//i.test(PARTNERS_URL)) {
    try {
      const r = await fetch(PARTNERS_URL, { signal: AbortSignal.timeout(5000), headers: { 'Cache-Control': 'no-cache' } });
      if (r.ok) {
        const list = cleanPartners((await r.json()).partners);
        fs.writeFileSync(pf('partners-cache.json'), JSON.stringify({ partners: list }));
        return { list, source: 'remote' };
      }
    } catch {}
    const c = readJson(pf('partners-cache.json'));
    if (c) return { list: cleanPartners(c.partners), source: 'cache' };
  }
  const b = readJson(path.join(__dirname, 'partners.json'));
  return { list: cleanPartners(b && b.partners), source: 'bundled' };
}

let devAuthed = false, devFails = 0, devLockUntil = 0;
const authFile = () => pf('dev-auth.json');
const hashPw = (pw, salt) => crypto.scryptSync(String(pw), salt, 32);
const needDev = () => { if (!devAuthed) throw new Error('Inicia sesión como developer.'); };

ipcMain.handle('dev:status', () => ({ hasPassword: fs.existsSync(authFile()), authed: devAuthed }));
ipcMain.handle('dev:setPassword', (_e, pw) => {
  if (fs.existsSync(authFile())) throw new Error('Ya hay una contraseña creada. Inicia sesión.');
  pw = String(pw || '');
  if (pw.length < 6) throw new Error('Usa al menos 6 caracteres.');
  const salt = crypto.randomBytes(16).toString('hex');
  fs.writeFileSync(authFile(), JSON.stringify({ salt, hash: hashPw(pw, salt).toString('hex') }));
  devAuthed = true;
  return true;
});
ipcMain.handle('dev:login', (_e, pw) => {
  if (Date.now() < devLockUntil) throw new Error('Demasiados intentos. Espera 30 segundos.');
  const a = readJson(authFile());
  if (!a) throw new Error('Todavía no hay contraseña developer.');
  const ok = crypto.timingSafeEqual(hashPw(pw, a.salt), Buffer.from(a.hash, 'hex'));
  if (!ok) {
    if (++devFails >= 5) { devLockUntil = Date.now() + 30000; devFails = 0; }
    throw new Error('Contraseña incorrecta.');
  }
  devFails = 0; devAuthed = true;
  return true;
});
ipcMain.handle('dev:logout', () => { devAuthed = false; return true; });

ipcMain.handle('partners:get', async () => ({ ...(await loadPartners()), dev: devAuthed }));
ipcMain.handle('partners:save', (_e, list) => {
  needDev();
  const clean = cleanPartners(list);
  fs.writeFileSync(pf('partners-dev.json'), JSON.stringify({ partners: clean }, null, 2));
  return clean;
});
ipcMain.handle('partners:reset', () => { needDev(); try { fs.unlinkSync(pf('partners-dev.json')); } catch {} return true; });
ipcMain.handle('partners:export', async () => {
  needDev();
  const { list } = await loadPartners();
  const r = await dialog.showSaveDialog(win, { defaultPath: 'partners.json', filters: [{ name: 'JSON', extensions: ['json'] }] });
  if (r.canceled || !r.filePath) return false;
  fs.writeFileSync(r.filePath, JSON.stringify({ partners: list }, null, 2));
  return true;
});
// ---------- Cuentas Ametra (servidor de cuentas: carpeta server/) ----------
// Dirección de TU servidor de cuentas. Para publicar el launcher pon aquí tu https (o define AMETRA_ACCOUNTS_URL).
// Con http solo se acepta localhost, para que las contraseñas nunca viajen sin cifrar por internet.
const ACCOUNTS_URL = process.env.AMETRA_ACCOUNTS_URL || 'https://ametracuentas.onrender.com';
// Dirección variable (túnel gratis de Cloudflare, que cambia al reiniciarse): pon aquí el enlace "raw" de un archivo JSON
// con este contenido: { "url": "https://algo.trycloudflare.com" }. El launcher lo lee al usar la cuenta y, si no
// puede, usa la última dirección que funcionó. Déjalo vacío si tienes una dirección fija en ACCOUNTS_URL.
const ACCOUNTS_URL_FILE = process.env.AMETRA_ACCOUNTS_URL_FILE || '';
const urlOk = (u) => /^https:\/\/[^\s/]+/i.test(u) || /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?(\/|$)/i.test(u);
let acctBase = ACCOUNTS_URL, acctBaseAt = 0;
async function accountsBase() {
  if (!/^https:\/\//i.test(ACCOUNTS_URL_FILE)) return ACCOUNTS_URL;
  if (Date.now() - acctBaseAt < 60000) return acctBase;
  const cache = pf('acct-url.json');
  try {
    const r = await fetch(ACCOUNTS_URL_FILE + (ACCOUNTS_URL_FILE.includes('?') ? '&' : '?') + 't=' + Date.now(), { signal: AbortSignal.timeout(4000), headers: { 'Cache-Control': 'no-cache', ...UA } });
    const u = r.ok ? String((await r.json()).url || '').trim().replace(/\/+$/, '') : '';
    if (u && urlOk(u)) { acctBase = u; fs.writeFileSync(cache, JSON.stringify({ url: u })); acctBaseAt = Date.now(); return acctBase; }
  } catch { /* sin conexión al archivo: se usa la última dirección buena */ }
  const c = readJson(cache);
  if (c && c.url && urlOk(c.url)) acctBase = c.url;
  acctBaseAt = Date.now() - 50000; // reintenta pronto
  return acctBase;
}
const acctSecure = () => urlOk(acctBase);
const sessFile = () => pf('acct-session.json');
// La sesión (token) se guarda cifrada con el almacén de Windows; si no hay cifrado disponible solo dura hasta cerrar el launcher.
let acctToken = null, acctUser = '';
function acctLoad() {
  try {
    const j = readJson(sessFile());
    if (j && j.data && safeStorage.isEncryptionAvailable()) { acctToken = safeStorage.decryptString(Buffer.from(j.data, 'base64')); acctUser = String(j.user || ''); }
  } catch { acctToken = null; }
}
app.whenReady().then(acctLoad); // el cifrado del sistema solo está listo cuando la app ya arrancó
function acctSave(token, user) {
  acctToken = token; acctUser = user || '';
  try {
    if (token && safeStorage.isEncryptionAvailable()) fs.writeFileSync(sessFile(), JSON.stringify({ user: acctUser, data: safeStorage.encryptString(token).toString('base64') }));
    else fs.unlinkSync(sessFile());
  } catch { /* nada que borrar */ }
}
async function acctFetch(method, route, body, auth = true) {
  await accountsBase();
  if (!acctSecure()) throw new Error('El servidor de cuentas debe usar https.');
  const headers = { 'Content-Type': 'application/json', ...UA };
  if (auth && acctToken) headers.Authorization = 'Bearer ' + acctToken;
  let r;
  try {
    r = await fetch(acctBase.replace(/\/+$/, '') + '/api/' + route, { method, headers, body: method === 'POST' ? JSON.stringify(body || {}) : undefined, signal: AbortSignal.timeout(60000) });
  } catch { throw new Error('No se pudo conectar con el servidor de cuentas.'); }
  let j = {}; try { j = await r.json(); } catch { /* sin cuerpo */ }
  if (!r.ok) {
    if (r.status === 401 && auth) acctSave(null);
    throw new Error(j.error || 'Error del servidor de cuentas (' + r.status + ').');
  }
  return j;
}
const ACCT_ROUTES = new Set(['buy', 'buy-set', 'pay/checkout', 'gift', 'daily', 'earn', 'seen', 'admin/grant', 'admin/revoke', 'admin/gems', 'admin/lookup']);
ipcMain.handle('acct:info', async () => { await accountsBase(); return { host: (() => { try { return new URL(acctBase).host; } catch { return ''; } })(), secure: acctSecure(), saved: acctToken ? acctUser : '' }; });
async function acctEnter(route, u, p) {
  const j = await acctFetch('POST', route, { username: String(u || ''), password: String(p || '') }, false);
  const { token, ...view } = j;
  acctSave(token, view.user);
  return view;
}
ipcMain.handle('acct:register', (_e, u, p) => acctEnter('register', u, p));
ipcMain.handle('acct:login', (_e, u, p) => acctEnter('login', u, p));
ipcMain.handle('acct:restore', async () => (acctToken ? acctFetch('GET', 'me') : null));
ipcMain.handle('acct:logout', async () => { try { if (acctToken) await acctFetch('POST', 'logout'); } catch { /* da igual: se borra en local */ } acctSave(null); return true; });
ipcMain.handle('acct:call', async (_e, route, body) => {
  if (route === 'me') return acctFetch('GET', 'me');
  if (route === 'packs') return acctFetch('GET', 'packs', null, false);
  if (!ACCT_ROUTES.has(route)) throw new Error('Operación no permitida.');
  if (!acctToken) throw new Error('Inicia sesión con tu cuenta RzClient.');
  return acctFetch('POST', route, body && typeof body === 'object' ? body : {});
});

// ---------- Minecraft Bedrock (Windows) ----------
// Bedrock es una app de la Microsoft Store: no hay loader ni mods. Se abre con el enlace minecraft:// y los
// packs/add-ons (.mcpack, .mcaddon, .mcworld…) se instalan abriéndolos con el propio juego.
const BEDROCK_EXT = ['mcpack', 'mcaddon', 'mcworld', 'mcstructure', 'mctemplate'];
const bedrockDir = () => path.join(process.env.LOCALAPPDATA || '', 'Packages', 'Microsoft.MinecraftUWP_8wekyb3d8bbwe', 'LocalState', 'games', 'com.mojang');
ipcMain.handle('bedrock:launch', async (_e, srv) => {
  if (process.platform !== 'win32') return { ok: false, error: 'Bedrock solo se puede abrir desde Windows 10/11.' };
  let url = 'minecraft://';
  if (srv && /^[\w.-]{1,253}$/.test(String(srv.host || ''))) {
    const port = /^\d{1,5}$/.test(String(srv.port || '')) && String(srv.port) !== '25565' ? String(srv.port) : '19132';
    url = `minecraft://connect/?serverUrl=${encodeURIComponent(srv.host)}&serverPort=${port}`;
  }
  try { await shell.openExternal(url); return { ok: true }; }
  catch { return { ok: false, error: 'No se pudo abrir Minecraft Bedrock. ¿Está instalado desde la Microsoft Store (Windows 10/11 Edition)?' }; }
});
ipcMain.handle('bedrock:import', async () => {
  const r = await dialog.showOpenDialog(win, { filters: [{ name: 'Packs y add-ons de Bedrock', extensions: BEDROCK_EXT }], properties: ['openFile', 'multiSelections'] });
  if (r.canceled) return 0;
  let n = 0;
  for (const f of r.filePaths) { if ((await shell.openPath(f)) === '') n++; } // lo abre Minecraft y lo importa
  return n;
});
ipcMain.handle('bedrock:ametra', async () => {
  const src = path.join(__dirname, 'bedrock', 'RzClient-Bedrock.mcpack');
  if (!fs.existsSync(src)) return false;
  const tmp = path.join(app.getPath('temp'), 'RzClient-Bedrock.mcpack'); // fuera del .asar para que Windows pueda abrirlo
  fs.copyFileSync(src, tmp);
  return (await shell.openPath(tmp)) === '';
});
// Lista lo instalado en com.mojang: packs de recursos, add-ons (behavior) y mundos.
const readName = (d, fallback) => { try { return fs.readFileSync(path.join(d, 'levelname.txt'), 'utf8').trim() || fallback; } catch { return fallback; } };
function packName(d, fallback) {
  try {
    const m = JSON.parse(fs.readFileSync(path.join(d, 'manifest.json'), 'utf8').replace(/^\uFEFF/, ''));
    const n = String(m.header && m.header.name || '');
    return n && !/^pack\./.test(n) ? n : fallback; // "pack.name" es una clave de idioma, no un nombre
  } catch { return fallback; }
}
ipcMain.handle('bedrock:list', () => {
  const base = bedrockDir();
  const out = { found: fs.existsSync(base), resource: [], behavior: [], worlds: [] };
  if (!out.found) return out;
  const scan = (sub, fn) => { try { return fs.readdirSync(path.join(base, sub), { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => fn(e.name, path.join(base, sub, e.name))); } catch { return []; } };
  out.resource = scan('resource_packs', (n, d) => ({ dir: n, name: packName(d, n) }));
  out.behavior = scan('behavior_packs', (n, d) => ({ dir: n, name: packName(d, n) }));
  out.worlds = scan('minecraftWorlds', (n, d) => ({ dir: n, name: readName(d, n) }));
  return out;
});
const BKIND = { resource: 'resource_packs', behavior: 'behavior_packs' };
ipcMain.handle('bedrock:remove', (_e, { kind, dir }) => {
  if (!BKIND[kind] || !/^[\w .{}()+-]{1,120}$/.test(String(dir)) || String(dir).includes('..')) return false;
  const target = path.join(bedrockDir(), BKIND[kind], String(dir));
  if (!fs.existsSync(target)) return false;
  fs.rmSync(target, { recursive: true, force: true });
  return true;
});
// Copia de seguridad de un mundo: carpeta -> .mcworld (un zip). Usa tar.exe de Windows 10+, que guarda rutas con "/".
ipcMain.handle('bedrock:backup', async (_e, dir) => {
  if (!/^[\w .{}()+=-]{1,120}$/.test(String(dir)) || String(dir).includes('..')) return { ok: false, error: 'Mundo no válido.' };
  const world = path.join(bedrockDir(), 'minecraftWorlds', String(dir));
  if (!fs.existsSync(world)) return { ok: false, error: 'No se encontró el mundo.' };
  const label = readName(world, String(dir)).replace(/[^\w .-]/g, '_').slice(0, 40);
  const stamp = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');
  const r = await dialog.showSaveDialog(win, { defaultPath: path.join(app.getPath('documents'), `${label}-${stamp}.mcworld`), filters: [{ name: 'Mundo de Bedrock', extensions: ['mcworld'] }] });
  if (r.canceled || !r.filePath) return { ok: false, canceled: true };
  const tmp = path.join(app.getPath('temp'), `ametra-${Date.now()}.zip`);
  try {
    await new Promise((res, rej) => require('child_process').execFile('tar.exe', ['-a', '-c', '-f', tmp, '-C', world, '.'], { windowsHide: true }, (e) => (e ? rej(e) : res())));
    fs.copyFileSync(tmp, r.filePath);
    return { ok: true, file: r.filePath };
  } catch { return { ok: false, error: 'No se pudo crear la copia (hace falta tar.exe de Windows 10 o 11).' }; }
  finally { try { fs.unlinkSync(tmp); } catch { /* ya no está */ } }
});
ipcMain.handle('bedrock:folder', async () => {
  const d = bedrockDir();
  if (!fs.existsSync(d)) return false;
  await shell.openPath(d);
  return true;
});
ipcMain.on('open:url', (_e, u) => { if (/^https:\/\/[^\s]+$/i.test(String(u))) shell.openExternal(String(u)); });

// ---------- Estado de servidores (logo, MOTD, jugadores, ping) ----------
const NAMED = { black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000', dark_purple: '#aa00aa', gold: '#ffaa00', gray: '#aaaaaa', dark_gray: '#555555', blue: '#5555ff', green: '#55ff55', aqua: '#55ffff', red: '#ff5555', light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff' };
const CODES = '0123456789abcdef'.split('');
const CODE_COLORS = [NAMED.black, NAMED.dark_blue, NAMED.dark_green, NAMED.dark_aqua, NAMED.dark_red, NAMED.dark_purple, NAMED.gold, NAMED.gray, NAMED.dark_gray, NAMED.blue, NAMED.green, NAMED.aqua, NAMED.red, NAMED.light_purple, NAMED.yellow, NAMED.white];

function motdSegments(desc) {
  const out = [];
  const push = (text, st) => { if (text) out.push({ t: text, c: st.c || null, b: !!st.b, i: !!st.i, u: !!st.u, s: !!st.s }); };
  const legacy = (text, base) => {
    const st = { ...base };
    let buf = '';
    for (let k = 0; k < text.length; k++) {
      if (text[k] === '§' && k + 1 < text.length) {
        push(buf, st); buf = '';
        const ch = text[++k].toLowerCase();
        const ci = CODES.indexOf(ch);
        if (ci >= 0) { st.c = CODE_COLORS[ci]; st.b = st.i = st.u = st.s = false; }
        else if (ch === 'l') st.b = true; else if (ch === 'o') st.i = true;
        else if (ch === 'n') st.u = true; else if (ch === 'm') st.s = true;
        else if (ch === 'r') { st.c = null; st.b = st.i = st.u = st.s = false; }
      } else buf += text[k];
    }
    push(buf, st);
  };
  const walk = (node, inh) => {
    if (node == null) return;
    if (typeof node === 'string' || typeof node === 'number') { legacy(String(node), inh); return; }
    if (Array.isArray(node)) { node.forEach((n) => walk(n, inh)); return; }
    if (typeof node !== 'object') return;
    const st = { ...inh };
    if (node.color) { const col = NAMED[node.color] || (/^#[0-9a-f]{6}$/i.test(node.color) ? node.color : null); if (col) st.c = col; }
    if (typeof node.bold === 'boolean') st.b = node.bold;
    if (typeof node.italic === 'boolean') st.i = node.italic;
    if (typeof node.underlined === 'boolean') st.u = node.underlined;
    if (typeof node.strikethrough === 'boolean') st.s = node.strikethrough;
    if (node.text != null) legacy(String(node.text), st);
    if (Array.isArray(node.extra)) node.extra.forEach((n) => walk(n, st));
  };
  walk(desc, {});
  let total = 0;
  return out.filter((s) => (total += s.t.length) < 400);
}

const vint = (n) => { const b = []; do { let t = n & 0x7f; n >>>= 7; if (n) t |= 0x80; b.push(t); } while (n); return Buffer.from(b); };
function readVint(buf, off) {
  let r = 0, shift = 0, i = off;
  for (;;) {
    if (i >= buf.length) return null;
    const b = buf[i++];
    r |= (b & 0x7f) << shift;
    if (!(b & 0x80)) break;
    shift += 7;
    if (shift > 35) throw new Error('varint');
  }
  return { value: r, size: i - off };
}
const mcPacket = (id, data) => { const body = Buffer.concat([vint(id), data]); return Buffer.concat([vint(body.length), body]); };
const mcStr = (s) => { const b = Buffer.from(s, 'utf8'); return Buffer.concat([vint(b.length), b]); };

function pingOnce(host, port, addrName) {
  return new Promise((resolve, reject) => {
    const sock = net.connect({ host, port });
    let buf = Buffer.alloc(0), json = null, tSent = 0, tFirst = 0, done = false;
    const end = (err, val) => { if (done) return; done = true; clearTimeout(to); clearTimeout(fb); sock.destroy(); err ? reject(err) : resolve(val); };
    const to = setTimeout(() => end(new Error('timeout')), 4500);
    let fb = null;
    sock.setNoDelay(true);
    sock.on('error', (e) => end(e));
    sock.on('connect', () => {
      tFirst = Date.now();
      const port16 = Buffer.alloc(2); port16.writeUInt16BE(port);
      sock.write(mcPacket(0x00, Buffer.concat([vint(767), mcStr(addrName), port16, vint(1)])));
      sock.write(mcPacket(0x00, Buffer.alloc(0)));
    });
    sock.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      if (buf.length > 600000) return end(new Error('respuesta enorme'));
      try {
        const len = readVint(buf, 0);
        if (!len || buf.length < len.size + len.value) return;
        const body = buf.subarray(len.size, len.size + len.value);
        buf = buf.subarray(len.size + len.value);
        const id = readVint(body, 0);
        if (!id) return;
        if (!json) {
          if (id.value !== 0) return end(new Error('paquete inesperado'));
          const sl = readVint(body, id.size);
          json = JSON.parse(body.subarray(id.size + sl.size, id.size + sl.size + sl.value).toString('utf8'));
          const first = Date.now() - tFirst;
          tSent = Date.now();
          sock.write(mcPacket(0x01, Buffer.alloc(8)));
          fb = setTimeout(() => end(null, { json, ping: first }), 1500);
        } else {
          end(null, { json, ping: Date.now() - tSent });
        }
      } catch (e) { end(e); }
    });
  });
}

const statusCache = new Map();
async function serverStatus(hostStr) {
  const m = /^([\w.-]+)(?::(\d{1,5}))?$/.exec(String(hostStr || '').trim());
  if (!m) return { online: false };
  let host = m[1], port = m[2] ? parseInt(m[2], 10) : 25565;
  if (port < 1 || port > 65535) return { online: false };
  const key = host.toLowerCase() + ':' + port;
  const hit = statusCache.get(key);
  if (hit && Date.now() - hit.t < 30000) return hit.v;
  const addrName = host;
  if (!m[2] && !/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) {
    try {
      const srv = await Promise.race([dns.promises.resolveSrv('_minecraft._tcp.' + host), new Promise((_r, rej) => setTimeout(() => rej(new Error('srv')), 1500))]);
      if (srv && srv[0]) { host = srv[0].name; port = srv[0].port; }
    } catch {}
  }
  let v;
  try {
    const { json, ping } = await pingOnce(host, port, addrName);
    const fav = typeof json.favicon === 'string' && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(json.favicon) && json.favicon.length < 250000 ? json.favicon : null;
    v = {
      online: true, ping,
      motd: motdSegments(json.description),
      players: { online: Number(json.players && json.players.online) || 0, max: Number(json.players && json.players.max) || 0 },
      version: String((json.version && json.version.name) || '').slice(0, 24),
      favicon: fav
    };
  } catch { v = { online: false }; }
  statusCache.set(key, { t: Date.now(), v });
  return v;
}
ipcMain.handle('server:status', (_e, host) => serverStatus(host));

// ---------- Versiones ----------
ipcMain.handle('versions:list', async () => {
  const r = await fetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', { headers: UA });
  const data = await r.json();
  return data.versions.filter((v) => v.type === 'release').map((v) => v.id);
});

// ---------- Mods ----------
ipcMain.handle('mods:add', async (_e, id) => {
  const r = await dialog.showOpenDialog(win, { filters: [{ name: 'Mods', extensions: ['jar'] }], properties: ['openFile', 'multiSelections'] });
  if (r.canceled) return 0;
  const dir = modsDir(id);
  r.filePaths.forEach((f) => fs.copyFileSync(f, path.join(dir, path.basename(f))));
  return r.filePaths.length;
});
ipcMain.handle('mods:open', (_e, id) => shell.openPath(modsDir(id)));
// Carpeta de fondos del menú principal del mod (se pueden cambiar también desde el juego: Shift derecho -> Fondos).
ipcMain.handle('bg:open', (_e, id) => {
  const d = path.join(instDir(id), 'ametra', 'fondos');
  fs.mkdirSync(d, { recursive: true });
  return shell.openPath(d);
});


// ---------- Fondos animados del LAUNCHER (presets + tus archivos) ----------
const lbgDir = () => { const d = path.join(ROOT(), 'fondos-launcher'); fs.mkdirSync(d, { recursive: true }); return d; };
const LBG_MIME = { '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.gif': 'image/gif', '.mp4': 'video/mp4', '.webm': 'video/webm' };
const lbgList = () => fs.readdirSync(lbgDir()).filter((f) => LBG_MIME[path.extname(f).toLowerCase()]).sort((a, b) => a.localeCompare(b));
ipcMain.handle('lbg:list', () => lbgList());
ipcMain.handle('lbg:add', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Elige tus fondos', properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'Imagen, GIF o vídeo', extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'mp4', 'webm'] }] });
  if (r.canceled) return [];
  const added = [];
  for (const f of r.filePaths) {
    const name = path.basename(f).replace(/[^\w. -]+/g, '_');
    if (fs.statSync(f).size > 150 * 1024 * 1024) continue; // demasiado pesado para un fondo
    fs.copyFileSync(f, path.join(lbgDir(), name)); added.push(name);
  }
  return added;
});
ipcMain.handle('lbg:open', () => shell.openPath(lbgDir()));
ipcMain.handle('lbg:read', (_e, name) => {
  if (typeof name !== 'string' || name !== path.basename(name)) return null;
  const mime = LBG_MIME[path.extname(name).toLowerCase()];
  const f = path.join(lbgDir(), name);
  if (!mime || !fs.existsSync(f)) return null;
  return { mime, data: fs.readFileSync(f) };
});
ipcMain.handle('lbg:remove', (_e, name) => {
  if (typeof name !== 'string' || name !== path.basename(name) || !LBG_MIME[path.extname(name).toLowerCase()]) return false;
  try { fs.unlinkSync(path.join(lbgDir(), name)); return true; } catch { return false; }
});

// ---------- Estudio 3D de cosméticos propios ----------
const studioDir = () => path.join(ROOT(), 'cosmeticos-propios');
const studioName = (n) => String(n || '').toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 24);
const STUDIO_SLOTS = ['cape', 'wings', 'hat', 'glasses', 'pack', 'back', 'boots', 'fists', 'aura', 'face'];

/** Copia los cosméticos propios a la carpeta de cada instancia (o solo a una) para que el mod los lea. */
function syncStudio(onlyId) {
  try {
    const src = studioDir();
    fs.mkdirSync(src, { recursive: true });
    const files = fs.readdirSync(src).filter((f) => f.endsWith('.json'));
    const root = path.join(ROOT(), 'instances');
    const ids = onlyId ? [onlyId] : (fs.existsSync(root) ? fs.readdirSync(root) : []);
    for (const id of ids) {
      const base = path.join(root, id);
      if (!fs.existsSync(base) && !onlyId) continue;
      const d = path.join(base, 'ametra', 'cosmeticos');
      fs.mkdirSync(d, { recursive: true });
      for (const f of fs.readdirSync(d)) if (f.endsWith('.json') && !f.startsWith('_') && !files.includes(f)) fs.unlinkSync(path.join(d, f));
      for (const f of files) fs.copyFileSync(path.join(src, f), path.join(d, f));
    }
  } catch { /* sin permisos: el jugador puede copiarlos a mano */ }
}
let studioWin = null;
ipcMain.handle('studio:open', () => {
  if (studioWin && !studioWin.isDestroyed()) { studioWin.focus(); return true; }
  studioWin = new BrowserWindow({
    width: 1280, height: 800, minWidth: 900, minHeight: 600, backgroundColor: '#0c0c12', autoHideMenuBar: true, title: 'Estudio de cosméticos',
    webPreferences: { preload: path.join(__dirname, 'studio-preload.js'), contextIsolation: true }
  });
  studioWin.loadFile('studio.html');
  return true;
});
ipcMain.handle('studio:list', () => {
  try { return fs.readdirSync(studioDir()).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort(); } catch { return []; }
});
ipcMain.handle('studio:load', (_e, n) => {
  try { return JSON.parse(fs.readFileSync(path.join(studioDir(), studioName(n) + '.json'), 'utf8')); } catch { return null; }
});
ipcMain.handle('studio:save', (_e, d) => {
  try {
    const name = studioName(d && d.name);
    if (!name || !d || d.format !== 'ametra-voxel' || !STUDIO_SLOTS.includes(d.slot) || !Array.isArray(d.voxels) || d.voxels.length < 1 || d.voxels.length > 512) return { ok: false };
    const voxels = d.voxels.map((v) => [v[0] | 0, v[1] | 0, v[2] | 0, String(v[3]).replace(/[^0-9a-f]/gi, '').padStart(6, '0').slice(0, 6)]);
    fs.mkdirSync(studioDir(), { recursive: true });
    fs.writeFileSync(path.join(studioDir(), name + '.json'), JSON.stringify({ format: 'ametra-voxel', v: 1, name, slot: d.slot, voxels }));
    syncStudio();
    return { ok: true };
  } catch { return { ok: false }; }
});
ipcMain.handle('studio:remove', (_e, n) => { try { fs.unlinkSync(path.join(studioDir(), studioName(n) + '.json')); } catch { /* ya no existe */ } syncStudio(); return true; });
ipcMain.handle('studio:folder', () => { fs.mkdirSync(studioDir(), { recursive: true }); return shell.openPath(studioDir()); });

// ---------- Módulos del cliente (mods reales de Modrinth) ----------
const instDir = (id) => path.join(ROOT(), 'instances', id);
const regFile = (id) => path.join(instDir(id), 'ametra-mods.json');
const readReg = (id) => { try { return JSON.parse(fs.readFileSync(regFile(id), 'utf8')); } catch { return {}; } };

// "sodium-fabric-0.6.13+mc1.21.1.jar" -> "sodium-fabric": sirve para borrar versiones viejas del mismo mod.
const modPrefix = (fn) => String(fn).replace(/\.jar$/i, '').replace(/[-_+]v?\d.*$/, '').toLowerCase();

// before = fecha (ISO) del mod que lo pide: así Sodium no queda más nuevo que el Iris instalado y viceversa.
async function installMod(slug, version, dir, seen = new Set(), loader = 'fabric', before = null) {
  if (seen.has(slug)) return null;
  seen.add(slug);
  const url = `https://api.modrinth.com/v2/project/${slug}/version?game_versions=${encodeURIComponent(JSON.stringify([version]))}&loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
  const vers = await (await fetch(url, { headers: UA })).json();
  if (!Array.isArray(vers) || !vers.length) throw new Error(`Este mod no tiene versión para Minecraft ${version}.`);
  let pool = vers;
  if (before) {
    const lim = Date.parse(before) + 36e5; // 1 h de margen
    const older = vers.filter((x) => Date.parse(x.date_published) <= lim);
    if (older.length) pool = older;
  }
  const v = pool.find((x) => x.version_type === 'release') || pool[0]; // mejor una versión estable que una alpha
  for (const d of v.dependencies || []) {
    if (d.dependency_type === 'required' && d.project_id) {
      try { await installMod(d.project_id, version, dir, seen, loader, v.date_published); } catch { /* dependencia opcional o no disponible */ }
    }
  }
  const f = v.files.find((x) => x.primary) || v.files[0];
  const target = path.join(dir, f.filename);
  if (!fs.existsSync(target)) {
    fs.writeFileSync(target, Buffer.from(await (await fetch(f.url, { headers: UA })).arrayBuffer()));
    // borra otras versiones del mismo mod (dos jars del mismo mod = Fabric no arranca)
    const pre = modPrefix(f.filename);
    for (const o of fs.readdirSync(dir)) {
      if (o !== f.filename && /\.jar$/i.test(o) && modPrefix(o) === pre) { try { fs.unlinkSync(path.join(dir, o)); } catch { /* en uso */ } }
    }
  }
  return f.filename;
}

// Iris solo funciona con la versión de Sodium para la que se hizo. Si hay Iris instalado, deja Sodium a juego
// (y quita More Culling, que exige un Sodium más nuevo). Devuelve los nombres de archivo que cambió.
async function alignWithIris(dir, version, loader = 'fabric') {
  const changed = { sodium: null, removed: [] };
  let files = [];
  try { files = fs.readdirSync(dir); } catch { return changed; }
  const iris = files.find((f) => /^iris.*\.jar$/i.test(f));
  if (!iris) return changed;
  try {
    const url = `https://api.modrinth.com/v2/project/iris/version?game_versions=${encodeURIComponent(JSON.stringify([version]))}&loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
    const vers = await (await fetch(url, { headers: UA })).json();
    const mine = Array.isArray(vers) && vers.find((v) => v.files.some((f) => f.filename === iris));
    if (!mine) return changed;
    changed.sodium = await installMod('sodium', version, dir, new Set(), loader, mine.date_published);
  } catch { /* sin internet: se deja como está */ }
  for (const f of fs.readdirSync(dir)) {
    if (/^moreculling.*\.jar$/i.test(f)) { try { fs.unlinkSync(path.join(dir, f)); changed.removed.push('moreculling'); } catch { /* en uso */ } }
  }
  return changed;
}

// Los mods de la Biblioteca se bajan para UNA versión de Minecraft. Se apunta cuál (ametra-mods.mc) y, si la
// instancia cambia de versión, se vuelven a bajar los mismos mods para la nueva. Los que no existan ahí se quitan.
const mcFile = (id) => path.join(instDir(id), 'ametra-mods.mc');
const readMc = (id) => { try { return fs.readFileSync(mcFile(id), 'utf8').trim(); } catch { return ''; } };
async function syncMods(id, version, send) {
  const reg = readReg(id);
  const slugs = Object.keys(reg);
  const was = readMc(id);
  fs.mkdirSync(instDir(id), { recursive: true });
  if (!slugs.length || !was || was === version) { fs.writeFileSync(mcFile(id), version); return { changed: false, installed: [], dropped: [] }; }
  const dir = modsDir(id);
  const installed = [], dropped = [];
  let n = 0;
  for (const slug of slugs) {
    n++;
    if (send) send('info', `Mods para Minecraft ${version}: ${slug} (${n}/${slugs.length})…`);
    const old = reg[slug];
    try {
      reg[slug] = await installMod(slug, version, dir);
      installed.push(slug);
    } catch {
      dropped.push(slug); // no tiene versión para esta Minecraft
      delete reg[slug];
    }
    // el jar de la versión anterior ya no sirve (installMod borra los del mismo mod; esto cubre el resto)
    if (old && reg[slug] !== old) { try { fs.unlinkSync(path.join(dir, old)); } catch { /* ya no está */ } }
  }
  await alignWithIris(dir, version);
  fs.writeFileSync(regFile(id), JSON.stringify(reg));
  fs.writeFileSync(mcFile(id), version);
  return { changed: true, installed, dropped };
}
ipcMain.handle('client:sync', (event, { id, version }) => syncMods(id, version, (ch, d) => event.sender.send(ch, d)));

ipcMain.handle('client:mods', (_e, id) => Object.keys(readReg(id)));
ipcMain.handle('mod:status', (_e, v) => !!modJarFor(v || '1.21.1'));
ipcMain.handle('client:toggle', async (_e, { id, version, slug, on }) => {
  const reg = readReg(id);
  const dir = modsDir(id);
  if (on) {
    reg[slug] = await installMod(slug, version, dir);
    fs.mkdirSync(instDir(id), { recursive: true });
    fs.writeFileSync(mcFile(id), version);
    const al = await alignWithIris(dir, version);
    if (al.sodium) reg.sodium = al.sodium;
    if (al.removed.includes('moreculling')) delete reg.moreculling;
  } else if (reg[slug]) {
    try { fs.unlinkSync(path.join(dir, reg[slug])); } catch { /* ya no existe */ }
    delete reg[slug];
  }
  fs.writeFileSync(regFile(id), JSON.stringify(reg));
  return Object.keys(reg);
});
// El mod (Ametra Client) y el launcher comparten config/ametra.json: se mezcla, no se pisa.
const cfgFile = (id) => path.join(instDir(id), 'config', 'ametra.json');
const readCfg = (id) => { try { return JSON.parse(fs.readFileSync(cfgFile(id), 'utf8')); } catch { return {}; } };
ipcMain.handle('client:config', (_e, { id, config }) => {
  fs.mkdirSync(path.dirname(cfgFile(id)), { recursive: true });
  const cur = readCfg(id);
  const next = { ...cur, ...config };
  if (config.hud) next.hud = { ...(cur.hud || {}), ...config.hud };
  fs.writeFileSync(cfgFile(id), JSON.stringify(next, null, 2));
});
ipcMain.handle('client:readConfig', (_e, id) => readCfg(id));

// ---------- Biblioteca de mods (Modrinth): metadatos, búsqueda, lista y borrado ----------
const MR = 'https://api.modrinth.com/v2';
const metaCache = new Map(); // clave -> { t, data }
const cached = async (key, ttl, fn) => {
  const c = metaCache.get(key);
  if (c && Date.now() - c.t < ttl) return c.data;
  const data = await fn();
  metaCache.set(key, { t: Date.now(), data });
  return data;
};
const mrGet = async (u) => {
  const r = await fetch(MR + u, { headers: UA });
  if (!r.ok) throw new Error('Modrinth respondió ' + r.status);
  return r.json();
};
const slimProject = (p, version) => ({
  slug: p.slug, title: p.title, desc: p.description, icon: p.icon_url || '', downloads: p.downloads || 0,
  ok: !version || ((p.game_versions || []).includes(version) && (p.loaders || []).includes('fabric'))
});
// Datos de una lista de slugs (nombre, icono, descargas y si existe para esa versión). Los que no existen se omiten.
ipcMain.handle('mods:meta', async (_e, { slugs, version }) => {
  const list = (Array.isArray(slugs) ? slugs : []).filter((x) => /^[\w.-]{2,64}$/.test(x)).slice(0, 100);
  if (!list.length) return {};
  const arr = await cached('meta:' + list.join(',') + ':' + version, 10 * 60e3, () => mrGet('/projects?ids=' + encodeURIComponent(JSON.stringify(list))));
  const out = {};
  for (const p of arr) out[p.slug] = slimProject(p, version);
  return out;
});
ipcMain.handle('mods:search', async (_e, { query, category, version, offset, index }) => {
  const facets = [['project_type:mod'], ['categories:fabric']];
  if (/^[\w.+-]{1,20}$/.test(version || '')) facets.push([`versions:${version}`]);
  if (/^[a-z-]{2,24}$/.test(category || '')) facets.push([`categories:${category}`]);
  const idx = ['relevance', 'downloads', 'follows', 'newest', 'updated'].includes(index) ? index : 'downloads';
  const q = String(query || '').slice(0, 60);
  const url = `/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(JSON.stringify(facets))}&index=${q ? 'relevance' : idx}&limit=24&offset=${Math.max(0, Number(offset) || 0)}`;
  const r = await cached('s:' + url, 2 * 60e3, () => mrGet(url));
  return {
    total: r.total_hits,
    hits: r.hits.map((h) => ({ slug: h.slug, title: h.title, desc: h.description, icon: h.icon_url || '', downloads: h.downloads || 0, ok: true }))
  };
});
// Todos los .jar de la carpeta de mods de la instancia (también los que añadiste a mano).
ipcMain.handle('mods:list', (_e, id) => {
  const dir = modsDir(id);
  const reg = readReg(id);
  const bySlug = {};
  for (const [slug, file] of Object.entries(reg)) bySlug[file] = slug;
  return fs.readdirSync(dir).filter((f) => /\.jar(\.disabled)?$/i.test(f)).map((f) => {
    let size = 0; try { size = fs.statSync(path.join(dir, f)).size; } catch { /* ya no está */ }
    return { file: f, size, slug: bySlug[f] || null, off: /\.disabled$/i.test(f) };
  }).sort((a, b) => a.file.localeCompare(b.file));
});
// Activar/desactivar un mod sin borrarlo (Fabric ignora los .jar.disabled).
ipcMain.handle('mods:enable', (_e, { id, file, on }) => {
  const dir = modsDir(id);
  const f = path.basename(String(file));
  const from = path.join(dir, f);
  const to = on ? from.replace(/\.disabled$/i, '') : (/\.disabled$/i.test(f) ? from : from + '.disabled');
  if (from !== to && fs.existsSync(from) && !fs.existsSync(to)) fs.renameSync(from, to);
  const reg = readReg(id);
  for (const k of Object.keys(reg)) if (reg[k] === f) { reg[k] = path.basename(to); }
  fs.writeFileSync(regFile(id), JSON.stringify(reg));
  return true;
});
ipcMain.handle('mods:remove', (_e, { id, file }) => {
  const dir = modsDir(id);
  const f = path.basename(String(file));
  try { fs.unlinkSync(path.join(dir, f)); } catch { /* ya no existe */ }
  const reg = readReg(id);
  for (const k of Object.keys(reg)) if (reg[k] === f) delete reg[k];
  fs.writeFileSync(regFile(id), JSON.stringify(reg));
  return Object.keys(reg);
});
// Capturas de pantalla: carpeta de la instancia (la usa también el mod, pero el launcher puede abrirla).
ipcMain.handle('shots:open', (_e, id) => {
  const d = path.join(instDir(id), 'screenshots');
  fs.mkdirSync(d, { recursive: true });
  return shell.openPath(d);
});

// ---------- Fabric / Legacy Fabric ----------
const META = { fabric: 'https://meta.fabricmc.net/v2/versions/loader', 'legacy-fabric': 'https://meta.legacyfabric.net/v2/versions/loader' };
async function ensureLoader(version, kind = 'fabric') {
  const base = META[kind];
  const list = await (await fetch(`${base}/${version}`, { headers: UA })).json();
  if (!Array.isArray(list) || !list.length) throw new Error(`${kind === 'fabric' ? 'Fabric' : 'Legacy Fabric'} no soporta la versión ${version}.`);
  const loader = (list.find((l) => l.loader.stable) || list[0]).loader.version;
  const prof = await (await fetch(`${base}/${version}/${loader}/profile/json`, { headers: UA })).text();
  let id = `fabric-loader-${loader}-${version}`;
  try { id = JSON.parse(prof).id || id; } catch { /* se queda con el nombre calculado */ }
  const dir = path.join(ROOT(), 'versions', id);
  const file = path.join(dir, `${id}.json`);
  if (!fs.existsSync(file)) {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(file, prof);
  }
  return id;
}
const ensureFabric = (version) => ensureLoader(version, 'fabric');
async function ensureFabricApi(version, dir) {
  if (fs.readdirSync(dir).some((f) => /^fabric-api/i.test(f))) return;
  const url = `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=${encodeURIComponent(JSON.stringify([version]))}&loaders=${encodeURIComponent('["fabric"]')}`;
  const vers = await (await fetch(url, { headers: UA })).json();
  const best = Array.isArray(vers) && (vers.find((x) => x.version_type === 'release') || vers[0]);
  const files = best && best.files;
  const f = files && (files.find((x) => x.primary) || files[0]);
  if (!f) return;
  fs.writeFileSync(path.join(dir, f.filename), Buffer.from(await (await fetch(f.url, { headers: UA })).arrayBuffer()));
}

// ---------- Java automático ----------
const run = (cmd, args) => new Promise((res) => {
  try { execFile(cmd, args, { timeout: 15000, windowsHide: true }, (err, so, se) => res({ err, out: `${so || ''}${se || ''}` })); }
  catch (e) { res({ err: e, out: '' }); }
});
async function javaInfoOf(p) {
  const { out } = await run(p, ['-version']);
  const m = /version "(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:_(\d+))?/.exec(out);
  if (!m) return { major: 0, update: 0 };
  if (m[1] === '1') return { major: Number(m[2]), update: Number(m[4] || 0) }; // 1.8.0_432 -> 8 update 432
  return { major: Number(m[1]), update: 0 };
}
// Qué Java necesita una versión de Minecraft (Mojang lo dice en el JSON de la versión; si no hay internet, se calcula).
async function javaMajor(version) {
  try {
    const man = await (await fetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', { headers: UA })).json();
    const v = man.versions.find((x) => x.id === version);
    if (v) {
      const j = await (await fetch(v.url, { headers: UA })).json();
      if (j.javaVersion && j.javaVersion.majorVersion) return j.javaVersion.majorVersion;
    }
  } catch { /* sin internet */ }
  const [, minor, patch] = (/^1\.(\d+)(?:\.(\d+))?/.exec(version) || []).map(Number);
  if (!minor) return 21;
  if (minor > 20 || (minor === 20 && (patch || 0) >= 5)) return 21;
  if (minor >= 17) return 17;
  return 8;
}
function subdirs(d) { try { return fs.readdirSync(d, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(d, e.name)); } catch { return []; } }
function javaCandidates() {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const out = ['java'];
  if (process.env.JAVA_HOME) out.push(path.join(process.env.JAVA_HOME, 'bin', exe));
  const roots = [];
  if (process.platform === 'win32') {
    for (const pf of [process.env['ProgramFiles'], process.env['ProgramFiles(x86)']].filter(Boolean)) {
      for (const v of ['Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft', 'Semeru', 'AdoptOpenJDK']) roots.push(path.join(pf, v));
    }
    // Java que trae el launcher oficial de Minecraft
    for (const r of subdirs(path.join(app.getPath('appData'), '.minecraft', 'runtime'))) {
      for (const p of subdirs(r)) for (const q of subdirs(p)) out.push(path.join(q, 'bin', exe));
    }
  } else if (process.platform === 'darwin') {
    for (const j of subdirs('/Library/Java/JavaVirtualMachines')) out.push(path.join(j, 'Contents', 'Home', 'bin', exe));
  } else {
    roots.push('/usr/lib/jvm');
  }
  for (const r of roots) for (const j of subdirs(r)) out.push(path.join(j, 'bin', exe));
  return out;
}
function findJavaBin(dir, depth = 0) {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const direct = path.join(dir, 'bin', exe);
  if (fs.existsSync(direct)) return direct;
  if (depth > 4) return null;
  for (const s of subdirs(dir)) { const r = findJavaBin(s, depth + 1); if (r) return r; }
  return null;
}
async function downloadJava(major, send) {
  const osName = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  const arch = process.arch === 'arm64' ? 'aarch64' : 'x64';
  if (!osName) throw new Error('Sistema no soportado para descargar Java.');
  const dest = path.join(ROOT(), 'runtime', `java-${major}`);
  const hit = fs.existsSync(dest) && findJavaBin(dest);
  if (hit) return hit;
  fs.mkdirSync(dest, { recursive: true });
  const url = `https://api.adoptium.net/v3/binary/latest/${major}/ga/${osName}/${arch}/jre/hotspot/normal/eclipse`;
  send('info', `Descargando Java ${major}…`);
  const r = await fetch(url, { headers: UA });
  if (!r.ok) throw new Error(`No se pudo descargar Java ${major} (HTTP ${r.status}). Instálalo a mano desde adoptium.net.`);
  const total = Number(r.headers.get('content-length')) || 0;
  const file = path.join(os.tmpdir(), `ametra-java-${major}${osName === 'windows' ? '.zip' : '.tar.gz'}`);
  const ws = fs.createWriteStream(file);
  let got = 0, last = 0;
  const reader = r.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    ws.write(Buffer.from(value));
    got += value.length;
    if (total && Date.now() - last > 300) { last = Date.now(); send('info', `Descargando Java ${major}… ${Math.round((got / total) * 100)}%`); }
  }
  await new Promise((res) => ws.end(res));
  send('info', `Instalando Java ${major}…`);
  // tar viene con Windows 10+, macOS y Linux, y también abre .zip
  const t = await run('tar', [osName === 'windows' ? '-xf' : '-xzf', file, '-C', dest]);
  try { fs.unlinkSync(file); } catch { /* ya no está */ }
  const bin = findJavaBin(dest);
  if (!bin) throw new Error(`No se pudo instalar Java ${major}. ${t.err ? t.err.message : ''}`);
  return bin;
}
// Devuelve la ruta del Java correcto: el tuyo si sirve, uno instalado, o uno descargado.
// minUpdate: para Java 8, rechaza builds viejos (antes de 8u312 fallan TLS con Mojang y tienen bugs de GC).
async function resolveJava(major, userPath, send, minUpdate = 0, allowNewer = true) {
  send('info', `Buscando Java ${major}…`);
  const seen = new Set();
  const found = [];
  const list = [userPath, ...javaCandidates()].filter(Boolean);
  for (const p of list) {
    if (seen.has(p)) continue;
    seen.add(p);
    if (p !== 'java' && !fs.existsSync(p)) continue;
    const j = await javaInfoOf(p);
    if (j.major) found.push({ p, m: j.major, u: j.update });
  }
  const exact = found.find((f) => f.m === major && (!minUpdate || f.u >= minUpdate));
  if (exact) return exact.p;
  const newer = allowNewer && major > 8 && found.find((f) => f.m > major && f.m <= major + 4);
  if (newer) return newer.p;
  if (minUpdate && found.some((f) => f.m === major)) send('info', `Tu Java ${major} es antiguo: descargando el más reciente…`);
  return downloadJava(major, send);
}

// ---------- Mod Ametra Client: un .jar por versión de Minecraft + auto-actualización ----------
// Repo de GitHub donde publicas las releases (el workflow sube rzmod-<mc>.jar). CÁMBIALO por el tuyo.
const AMETRA_REPO = process.env.AMETRA_REPO || 'raxzdv/RzClient-multiversion';
const modCache = () => path.join(app.getPath('userData'), 'mod-cache');
const modIndexFile = () => path.join(modCache(), 'index.json');
const readModIndex = () => { try { return JSON.parse(fs.readFileSync(modIndexFile(), 'utf8')); } catch { return {}; } };

// mod-compat.json: versión de Minecraft -> jar que la cubre (p. ej. 1.21 y 1.21.1 usan rzmod-1.21.1.jar). Las versiones sin jar publicado (1.19.x y 1.21.2+) avisan en vez de fallar.
const MOD_COMPAT = (() => { try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'mod-compat.json'), 'utf8')); } catch { return {}; } })();
const modKey = (v) => MOD_COMPAT[v] || v;
// Qué jar usar para esta versión: 1) el descargado (más nuevo)  2) el de mod/rzmod-<jar>.jar  3) nombres viejos (ametra-client-*) y mod/rzmod.jar (solo 1.21.1)
function modJarFor(version) {
  const k = modKey(version);
  const c = [path.join(modCache(), `rzmod-${k}.jar`), path.join(__dirname, 'mod', `rzmod-${k}.jar`),
             path.join(modCache(), `ametra-client-${k}.jar`), path.join(__dirname, 'mod', `ametra-client-${k}.jar`)];
  if (k === '1.21.1') c.push(path.join(__dirname, 'mod', 'rzmod.jar'), path.join(__dirname, 'mod', 'ametra-client.jar'));
  return c.find((f) => fs.existsSync(f)) || null;
}

// Mira la última release de GitHub y baja el jar de ESTA versión si es nuevo. Nunca lanza (sin internet = se usa lo que haya).
async function updateAmetraMod(version, send) {
  try {
    if (AMETRA_REPO.startsWith('TU_USUARIO')) return;
    const r = await fetch(`https://api.github.com/repos/${AMETRA_REPO}/releases/latest`, { headers: UA, signal: AbortSignal.timeout(4000) });
    if (!r.ok) return;
    const rel = await r.json();
    const asset = (rel.assets || []).find((x) => x.name === `rzmod-${modKey(version)}.jar`);
    if (!asset) return;
    const idx = readModIndex();
    if (idx[modKey(version)] === rel.tag_name && fs.existsSync(path.join(modCache(), asset.name))) return; // ya está al día
    send('info', `Actualizando RzClient (${rel.tag_name})…`);
    const buf = Buffer.from(await (await fetch(asset.browser_download_url, { headers: UA, signal: AbortSignal.timeout(30000) })).arrayBuffer());
    const want = String(asset.digest || '').replace(/^sha256:/, '');
    if (want && crypto.createHash('sha256').update(buf).digest('hex') !== want) return; // descarga corrupta: no se usa
    fs.mkdirSync(modCache(), { recursive: true });
    const tmp = path.join(modCache(), asset.name + '.part');
    fs.writeFileSync(tmp, buf);
    fs.renameSync(tmp, path.join(modCache(), asset.name));
    idx[modKey(version)] = rel.tag_name; fs.writeFileSync(modIndexFile(), JSON.stringify(idx));
  } catch { /* sin internet o GitHub caído: se sigue con el jar local */ }
}

function ensureAmetraMod(version, dir, enabled, send) {
  for (const f of fs.readdirSync(dir)) {
    if (/^(ametra-client|rzmod).*\.jar$/i.test(f)) { try { fs.unlinkSync(path.join(dir, f)); } catch { /* en uso */ } }
  }
  if (!enabled) return;
  const src = modJarFor(version);
  if (!src) { send('warn', `RzClient aún no está publicado para Minecraft ${version}: se instalará solo en cuanto esté en la release de GitHub (conéctate a internet y vuelve a jugar).`); return; }
  fs.copyFileSync(src, path.join(dir, 'rzmod.jar'));
  send('warn', '');
}

// ---------- Pack de optimización ----------
// Registro propio (ametra-opt.json) para no mezclarlo con los mods que el usuario activa a mano.
const optFile = (id) => path.join(instDir(id), 'ametra-opt.json');
const readOpt = (id) => { try { return JSON.parse(fs.readFileSync(optFile(id), 'utf8')); } catch { return { files: {} }; } };

async function discoverLegacy(version) {
  // Mods de rendimiento reales para Legacy Fabric en esta versión exacta (los más descargados, que funcionen en cliente).
  const facets = JSON.stringify([['project_type:mod'], ['categories:legacy-fabric'], ['categories:optimization'], [`versions:${version}`]]);
  const url = `https://api.modrinth.com/v2/search?limit=12&index=downloads&facets=${encodeURIComponent(facets)}`;
  const r = await (await fetch(url, { headers: UA })).json();
  return (r.hits || []).filter((h) => h.client_side !== 'unsupported').slice(0, opt.LEGACY_DISCOVER_MAX).map((h) => ({ slug: h.slug, name: h.title }));
}

// Instala (o repara) el pack en la instancia. Nunca lanza: devuelve qué se instaló y qué se saltó.
async function installPack(id, version, requiredJava, send) {
  const p = opt.plan(version, requiredJava);
  const dir = modsDir(id);
  const reg = readOpt(id);
  const out = { installed: [], skipped: [], failed: [], loader: p.loader };
  if (!p.loader) return out;
  reg.mc = version; reg.loader = p.loader; reg.files = reg.files || {};
  let items = p.mods.slice();
  try { if (p.discover) items = items.concat(await discoverLegacy(version)); } catch { /* sin internet: solo lo básico */ }
  const hasIris = fs.readdirSync(dir).some((f) => /^iris.*\.jar$/i.test(f));
  for (const m of items) {
    if (hasIris && m.slug === 'moreculling') { out.skipped.push(m.name); continue; } // choca con el Sodium que pide Iris
    const have = reg.files[m.slug];
    if (have && fs.existsSync(path.join(dir, have))) { out.installed.push(m.name); continue; }
    try {
      send('info', `Pack de optimización: ${m.name}…`);
      const f = await installMod(m.slug, version, dir, new Set(), p.loader);
      if (f) { reg.files[m.slug] = f; out.installed.push(m.name); }
    } catch { out.skipped.push(m.name); } // no hay build para esta versión: normal, se salta
  }
  const al = await alignWithIris(dir, version, p.loader);
  if (al.sodium) reg.files.sodium = al.sodium;
  if (al.removed.includes('moreculling')) delete reg.files.moreculling;
  fs.mkdirSync(instDir(id), { recursive: true });
  fs.writeFileSync(optFile(id), JSON.stringify(reg, null, 2));
  return out;
}
function removePack(id) {
  const reg = readOpt(id);
  for (const f of Object.values(reg.files || {})) { try { fs.unlinkSync(path.join(modsDir(id), f)); } catch { /* ya no está */ } }
  try { fs.unlinkSync(optFile(id)); } catch { /* no había */ }
}
// Ajustes de vídeo ligeros SOLO la primera vez (si el usuario ya tiene options.txt no se toca).
function seedVideoOptions(id, version) {
  const v = opt.videoOptions(version);
  const f = path.join(instDir(id), 'options.txt');
  if (!v || fs.existsSync(f)) return false;
  fs.mkdirSync(instDir(id), { recursive: true });
  fs.writeFileSync(f, Object.entries(v).map(([k, val]) => `${k}:${val}`).join('\n') + '\n');
  return true;
}

ipcMain.handle('opt:plan', async (_e, version) => {
  const p = opt.plan(version, await javaMajor(version));
  return { era: p.era, loader: p.loader, java: p.java, mods: p.mods.map((m) => ({ name: m.name, why: m.why })), discover: p.discover, video: !!p.video };
});
ipcMain.handle('opt:install', async (event, { id, version }) => {
  const send = (ch, d) => event.sender.send(ch, d);
  const major = await javaMajor(version);
  const p = opt.plan(version, major);
  if (p.loader) await ensureLoader(version, p.loader);
  if (p.loader === 'fabric') await ensureFabricApi(version, modsDir(id));
  return installPack(id, version, major, send);
});

// ---------- Jugar ----------
ipcMain.handle('play', async (event, { id, name, version, ram, java, mode, fabric, ametra, optimize, modernJava, server }) => {
  const send = (ch, d) => event.sender.send(ch, d);
  syncStudio(id);
  syncOfflineSkin(id, mode);
  try {
    let authorization;
    if (mode === 'ms') {
      if (!msAccount) { // p. ej. el launcher arrancó sin internet: se intenta recuperar la sesión guardada
        send('info', 'Recuperando tu sesión de Microsoft…');
        try { await msLogin(true); } catch { throw new Error('Inicia sesión con Microsoft en Cuenta.'); }
      }
      await ensureMsSession(send);
      authorization = msAccount;
    } else {
      authorization = Authenticator.getAuth(name);
    }

    const mods = modsDir(id);
    const ver = { number: version, type: 'release' };
    const required = await javaMajor(version);
    const plan = opt.plan(version, required);
    const useOpt = optimize !== false;
    if (ametra !== false && plan.loader === 'fabric') await updateAmetraMod(version, send);
    const wantAmetra = ametra !== false && !!modJarFor(version);

    // 1) Loader: el pack lo necesita en cualquier versión que tenga uno (Fabric o Legacy Fabric).
    const kind = (fabric || wantAmetra || useOpt) ? plan.loader : null;
    if (kind) {
      send('info', kind === 'fabric' ? 'Preparando Fabric…' : 'Preparando Legacy Fabric…');
      ver.custom = await ensureLoader(version, kind);
      if (kind === 'fabric') await ensureFabricApi(version, mods);
      if (kind === 'fabric') {
        try { await syncMods(id, version, send); } catch { send('warn', 'No se pudieron actualizar tus mods a esta versión (¿sin internet?).'); }
      }
      if (fabric || wantAmetra) ensureAmetraMod(version, mods, ametra !== false, send);
      if (useOpt) {
        try {
          const r = await installPack(id, version, required, send);
          const n = r.installed.length;
          send('warn', n ? '' : 'No se pudo instalar el pack de optimización (¿sin internet?). Se juega igual.');
        } catch { send('warn', 'No se pudo instalar el pack de optimización. Se juega igual.'); }
      }
    } else {
      send('warn', '');
    }
    if (!useOpt && fs.existsSync(optFile(id))) removePack(id); // lo apagaste: se quitan SOLO los mods del pack
    if (useOpt) seedVideoOptions(id, version);

    // 2) Java: sube a uno moderno cuando es seguro; en versiones viejas, al Java 8 más nuevo.
    const major = modernJava === false ? required : plan.java.target;
    const minUpdate = major === 8 ? opt.MIN_JAVA8_UPDATE : 0;
    const javaPath = await resolveJava(major, java, send, minUpdate, major === required);
    send('info', `Usando Java ${major}${major > required ? ` (subido desde ${required})` : ''}…`);

    const launcher = new Client();
    let tail = '', started = false;
    launcher.on('progress', (p) => send('progress', { type: p.type, percent: Math.round((p.task / p.total) * 100) }));
    launcher.on('debug', (m) => console.log('[debug]', m));
    launcher.on('data', (d) => {
      tail = (tail + String(d)).slice(-6000);
      if (!started) { started = true; send('started'); }
    });
    launcher.on('close', (code) => {
      if (code !== 0) {
        if (/UnsupportedClassVersionError|class file version|requires Java/i.test(tail)) send('hint', `Java incorrecto: esta versión necesita Java ${required}. Borra la ruta de Java en Ajustes para que RzClient use el correcto.`);
        else if (major > required && /InaccessibleObjectException|IllegalAccessError|NoClassDefFoundError|Unsupported class file|Unable to make .* accessible/i.test(tail)) send('hint', `Falló con Java ${major} (subido desde ${required}). Desactiva "Java moderno" en Ajustes y vuelve a probar.`);
        else if (/OutOfMemoryError|Could not reserve enough space/i.test(tail)) send('hint', 'Poca memoria: baja o sube la RAM en Ajustes según tu PC.');
        else if (/Incompatible mod set|Mod resolution|requires .* of (minecraft|fabric)/i.test(tail)) send('hint', 'Un mod no es compatible con esta versión de Minecraft. Quita mods en la pestaña Mods.');
      }
      send('closed', code);
    });

    launcher.launch({
      authorization,
      root: ROOT(),
      version: ver,
      memory: { max: `${ram}G`, min: '1G' },
      javaPath,
      customArgs: opt.jvmArgs(major, ram, cdsPathFor(id, major)),
      overrides: { gameDirectory: path.join(ROOT(), 'instances', id) },
      ...(server && /^[\w.-]+$/.test(String(server.host)) ? { server: { host: String(server.host), port: String(server.port || 25565) } } : {})
    });
  } catch (e) {
    send('error', e.message);
  }
});
