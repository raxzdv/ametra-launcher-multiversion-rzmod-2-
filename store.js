// ---------- Tienda de cosméticos ----------
// Las gemas se ganan jugando (y con el bono diario) y viven en este launcher: no hay dinero real ni servidor.
// Lo que compras se guarda en S.owned y se escribe en config/ametra.json ("owned") para que el mod lo sepa.
const GEM_PER_MIN = 6, WELCOME_GEMS = 500, DEAL_OFF = 0.3;

(function initEconomy() {
  if (typeof S.gems !== 'number') S.gems = WELCOME_GEMS;
  if (!Array.isArray(S.owned)) S.owned = [];
  // lo que ya llevabas puesto antes de existir la tienda sigue siendo tuyo
  COS_CATS.forEach(([c]) => { const id = S.cos[c]; if (id && id !== 'none' && !S.owned.includes(c + ':' + id)) S.owned.push(c + ':' + id); });
  save();
  try { writeConfig(['owned']); } catch { /* sin instancia todavía */ }
})();

// Con la sesión developer iniciada (pestaña Servidores → Developer) todo cuenta como tuyo.
const isDev = () => typeof P !== 'undefined' && !!P.dev;
// Con sesión de cuenta Ametra, las gemas y lo comprado/regalado viven en el servidor (ACC); sin sesión, en este PC.
const ownedNow = () => (ACC.on ? ACC.owned : S.owned);
const gemsNow = () => (ACC.on ? ACC.gems : S.gems);
function hasCos(cat, id) {
  const it = findCos(cat, id);
  return !!it && (isDev() || it.price === 0 || ownedNow().includes(cat + ':' + id));
}
const localDay = (d = new Date()) => d.toLocaleDateString('en-CA'); // AAAA-MM-DD en hora local

// ----- Oferta del día: un cosmético de pago distinto cada día, con 30 % de descuento -----
function dealItem() {
  if (ACC.on) return ACC.deal ? (COS_DATA.find((it) => it.cat === ACC.deal.cat && it.id === ACC.deal.id) || null) : null;
  const pool = COS_DATA.filter((it) => it.price > 0 && !hasCos(it.cat, it.id));
  if (!pool.length) return null;
  let h = 0; for (const ch of localDay()) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return pool[h % pool.length];
}
function priceOf(it) {
  if (ACC.on) return ACC.deal && ACC.deal.cat === it.cat && ACC.deal.id === it.id ? ACC.deal.price : it.price;
  const d = dealItem();
  return d && d.cat === it.cat && d.id === it.id ? Math.round(it.price * (1 - DEAL_OFF)) : it.price;
}

// ----- Ganar gemas -----
function awardPlaytime(ms) {
  const mins = Math.floor(ms / 60000);
  if (mins < 1) return;
  if (ACC.on) { // las gemas las da el servidor (solo por tiempo real y con tope diario)
    const before = ACC.gems;
    ametra.acctCall('earn', { ms }).then((v) => { accApply(v); S.lastEarn = Math.max(0, v.gems - before); save(); }).catch(() => { /* sin conexión: ese tiempo no cuenta */ });
    return;
  }
  const g = mins * GEM_PER_MIN;
  S.gems += g; S.lastEarn = g; save();
  if ($('page-store').classList.contains('active')) renderStore();
}
async function claimDaily() {
  if (ACC.on) {
    if (ACC.daily.claimed) return;
    try {
      const v = await ametra.acctCall('daily'); accApply(v);
      ST.msg = `+${v.got} gemas del bono diario (racha de ${v.daily.streak} día${v.daily.streak > 1 ? 's' : ''}). Vuelve mañana para ganar más.`;
    } catch (e) { ST.msg = cleanErr(e); }
    renderStore(); return;
  }
  const d = S.daily || { day: '', streak: 0 };
  if (d.day === localDay()) return;
  const y = new Date(); y.setDate(y.getDate() - 1);
  const streak = d.day === localDay(y) ? Math.min(d.streak + 1, 7) : 1;
  const g = 100 + (streak - 1) * 20;
  S.daily = { day: localDay(), streak }; S.gems += g; save();
  ST.msg = `+${g} gemas del bono diario (racha de ${streak} día${streak > 1 ? 's' : ''}). Vuelve mañana para ganar más.`;
  renderStore();
}

// ----- Estado y pantalla -----
const ST = { cat: 'all', sel: null, msg: '', busy: false };
const stViewer = makeViewer($('stStage'), $('stModel'), true);
stViewer.cosSrc = () => ({ ...S.cos, ...(ST.sel ? { [ST.sel.cat]: ST.sel.id } : {}) });
const ST_TABS = [['all', 'Todo'], ['mine', 'Mis cosméticos'], ...COS_CATS];

function openStoreItem(cat, id) {
  ST.cat = cat; ST.sel = findCos(cat, id) || null; ST.msg = '';
  go('store');
}
function gemEl(n) {
  const w = document.createElement('span'); w.className = 'price';
  const g = document.createElement('i'); g.className = 'gem';
  w.append(g, document.createTextNode(String(n)));
  return w;
}
// ----- Sets (Ángel / Demonio / Vacío): comprar de golpe lo que te falta cuesta SET_OFF menos (el servidor valida el precio) -----
const SET_OFF = 0.15, SET_NAMES = { angel: 'Ángel', hell: 'Soberano del Infierno', void: 'Vacío' };
const setKeyOf = (it) => { const m = /^(angel|hell|void)_/.exec(it.id); return m ? m[1] : null; };
function setInfo(key) {
  const pieces = COS_DATA.filter((it) => it.price > 0 && setKeyOf(it) === key);
  const missing = pieces.filter((it) => !ownedNow().includes(it.cat + ':' + it.id));
  const base = missing.reduce((n, it) => n + it.price, 0);
  return { key, name: SET_NAMES[key], pieces, missing, have: pieces.length - missing.length, base, price: Math.round(base * (1 - SET_OFF)) };
}

// ----- Historial de compras (con cuenta viene del servidor; sin cuenta se guarda en este PC) -----
function addLocalHistory(h) {
  if (!Array.isArray(S.history)) S.history = [];
  S.history.push({ ...h, at: Date.now() });
  if (S.history.length > 30) S.history.splice(0, S.history.length - 30);
}
function renderHistory() {
  const box = $('stHist'); box.textContent = '';
  const list = (ACC.on ? ACC.history : (S.history || []).slice().reverse()).slice(0, 6);
  box.hidden = !list.length;
  if (!list.length) return;
  const h = document.createElement('h4'); h.textContent = 'ÚLTIMAS COMPRAS'; box.appendChild(h);
  list.forEach((x) => {
    const it = x.cat ? findCos(x.cat, x.id) : null;
    const nm = it ? `"${it.name}"` : 'un cosmético';
    const p = document.createElement('p');
    const l = document.createElement('span');
    l.textContent = x.k === 'gems' ? `Recargaste ${x.gems.toLocaleString('es')} gemas` : x.k === 'set' ? `Set ${SET_NAMES[x.set] || x.set} (${x.n} piezas)` : x.k === 'gift' ? `Regalaste ${nm} a ${x.to}` : `Compraste ${nm}`;
    const r = document.createElement('span');
    r.textContent = `${x.k === 'gems' ? '+' + x.gems : '−' + x.price} gemas · ${new Date(x.at).toLocaleDateString('es', { day: 'numeric', month: 'short' })}`;
    p.append(l, r); box.appendChild(p);
  });
}

// ----- Diálogo de compra: qué compras, cuánto cuesta, cuánto te queda y, si no llega, cuánto falta -----
// o = { title, name, sub, icon (cosmético para el dibujo), lines: [[nombre, precio]] (solo en sets), base, price, canEquip }
// Devuelve { ok, equip }.
function askBuy(o) {
  return new Promise((resolve) => {
    const dlg = $('dlgBuy'), have = gemsNow(), left = have - o.price, short = -left;
    $('buyTitle').textContent = o.title;
    $('buyName').textContent = o.name; $('buySub').textContent = o.sub || '';
    const cv = $('buyIcon'); cv.getContext('2d').clearRect(0, 0, cv.width, cv.height);
    if (o.icon) drawCosIcon(cv, o.icon, o.icon.cat);
    const ul = $('buyList'); ul.textContent = ''; ul.hidden = !(o.lines && o.lines.length);
    (o.lines || []).forEach(([n, pr]) => { const li = document.createElement('li'); const a = document.createElement('span'); a.textContent = n; const b = document.createElement('span'); b.textContent = pr; li.append(a, b); ul.appendChild(li); });
    const sum = $('buySum'); sum.textContent = '';
    const row = (k, v, cls) => { const d = document.createElement('div'); if (cls) d.className = cls; const a = document.createElement('span'); a.textContent = k; const b = typeof v === 'string' ? document.createTextNode(v) : v; d.append(a, b); sum.appendChild(d); };
    if (o.base && o.base !== o.price) {
      row('Precio normal', gemEl(o.base));
      row(o.offLabel || 'Descuento', `−${o.base - o.price}`, 'off');
    }
    row('Tienes', gemEl(have));
    row('Total', gemEl(o.price), 'tot');
    row('Te quedan', gemEl(Math.max(left, 0)), left < 0 ? 'low' : '');
    const warn = $('buyWarn');
    if (short > 0) {
      const mins = Math.ceil(short / GEM_PER_MIN);
      const daily = (ACC.on ? !ACC.daily.claimed : (S.daily && S.daily.day) !== localDay()) ? ' El bono diario te da gemas gratis.' : '';
      warn.textContent = `Te faltan ${short} gemas (unos ${mins} min jugando).${daily}`;
    } else warn.textContent = '';
    $('buyOk').disabled = short > 0;
    $('buyOk').textContent = 'Comprar · ' + o.price + ' gemas';
    $('buyEqRow').hidden = !o.canEquip;
    $('buyEquip').checked = S.equipOnBuy !== false;
    dlg.returnValue = '';
    $('buyCancel').onclick = () => dlg.close('cancel');
    dlg.addEventListener('close', () => {
      const ok = dlg.returnValue === 'ok';
      if (ok && o.canEquip) { S.equipOnBuy = $('buyEquip').checked; save(); }
      resolve({ ok, equip: ok && o.canEquip && $('buyEquip').checked });
    }, { once: true });
    dlg.showModal();
    $('buyOk').focus();
  });
}

// Cada intento de compra lleva un id: si la respuesta se pierde y se reintenta, el servidor no cobra dos veces.
const newRid = () => (self.crypto && crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2, 12));
async function acctBuyCall(route, body) {
  const rid = newRid();
  let last;
  for (let i = 0; i < 3; i++) {
    try { return await ametra.acctCall(route, { ...body, rid }); }
    catch (e) {
      last = e;
      if (!/conectar|Error del servidor/i.test(cleanErr(e))) throw e; // solo se reintenta si fue la red o el servidor
      await new Promise((r) => setTimeout(r, 700 * (i + 1)));
    }
  }
  throw last;
}

async function buyItem(it) {
  if (ST.busy) return;
  const price = priceOf(it);
  const r = await askBuy({
    title: 'Confirmar compra', name: it.name, sub: RAR_NAME[it.rar] + ' · ' + it.desc, icon: it,
    base: it.price, price, offLabel: 'Oferta del día', canEquip: true
  });
  if (!r.ok) return;
  ST.busy = true; renderStore();
  try {
    if (ACC.on) {
      accApply(await acctBuyCall('buy', { cat: it.cat, id: it.id, expect: price }));
    } else {
      if (S.gems < price) throw new Error(`Te faltan ${price - S.gems} gemas.`);
      if (S.owned.includes(it.cat + ':' + it.id)) throw new Error('Ya lo tienes.');
      S.gems -= price; S.owned.push(it.cat + ':' + it.id);
      addLocalHistory({ k: 'buy', cat: it.cat, id: it.id, price });
    }
    if (r.equip) S.cos[it.cat] = it.id;
    save(); writeConfig(['cape', 'cosmetics', 'owned']);
    ST.msg = `¡Es tuyo! "${it.name}" ${r.equip ? 'ya está equipado' : 'está en Mis cosméticos'}.`;
  } catch (e) {
    ST.msg = cleanErr(e);
    if (ACC.on) await accRefresh(); // por si cambió el precio, el saldo o ya lo tenías
  }
  ST.busy = false; renderStore();
}
async function buySet(key) {
  if (ST.busy) return;
  const q = setInfo(key);
  if (q.missing.length < 2) return;
  const r = await askBuy({
    title: 'Comprar set', name: 'Set ' + q.name, sub: `${q.missing.length} piezas que te faltan (tienes ${q.have} de ${q.pieces.length})`, icon: q.missing[0],
    lines: q.missing.map((it) => [it.name, it.price]), base: q.base, price: q.price, offLabel: `Descuento de set −${Math.round(SET_OFF * 100)} %`, canEquip: false
  });
  if (!r.ok) return;
  ST.busy = true; renderStore();
  try {
    if (ACC.on) {
      accApply(await acctBuyCall('buy-set', { set: key, expect: q.price }));
    } else {
      if (S.gems < q.price) throw new Error(`Te faltan ${q.price - S.gems} gemas.`);
      S.gems -= q.price; q.missing.forEach((it) => S.owned.push(it.cat + ':' + it.id));
      addLocalHistory({ k: 'set', set: key, n: q.missing.length, price: q.price });
    }
    save(); writeConfig(['owned']);
    ST.msg = `¡Set ${q.name} tuyo! Ya puedes equipar sus ${q.missing.length} piezas nuevas.`;
  } catch (e) {
    ST.msg = cleanErr(e);
    if (ACC.on) await accRefresh();
  }
  ST.busy = false; renderStore();
}
function toggleEquip(it) {
  S.cos[it.cat] = S.cos[it.cat] === it.id ? 'none' : it.id;
  save(); writeConfig(['cape', 'cosmetics']); ST.msg = ''; renderStore();
}

function renderStore() {
  $('gemCount').textContent = gemsNow().toLocaleString('es');
  if ($('gemTop')) $('gemTop').textContent = gemsNow().toLocaleString('es');
  const claimed = ACC.on ? ACC.daily.claimed : (S.daily && S.daily.day) === localDay();

  // aviso si no hay cuenta
  const sa = $('stAcct'); sa.textContent = ''; sa.hidden = ACC.on;
  if (!ACC.on) {
    const t = document.createElement('span'); t.textContent = 'Sin cuenta: tus gemas y compras solo viven en este PC. Con una cuenta Ametra las guardas en el servidor y puedes regalar cosméticos a tus amigos.';
    const b = document.createElement('button'); b.className = 'btn'; b.textContent = 'Iniciar sesión / Crear cuenta'; b.onclick = () => go('accounts');
    sa.append(t, b);
  }
  // regalos y premios recibidos que aún no has visto
  const gb = $('giftBox'); gb.textContent = '';
  const unseen = ACC.on ? accUnseen() : [];
  gb.hidden = !unseen.length;
  if (unseen.length) {
    unseen.slice(0, 5).forEach((n) => { const p = document.createElement('p'); p.textContent = '🎁 ' + accNoteText(n); gb.appendChild(p); });
    const ok = document.createElement('button'); ok.className = 'btn'; ok.textContent = 'Entendido';
    ok.onclick = () => { ametra.acctCall('seen').then(accApply).catch(() => {}); };
    gb.appendChild(ok);
  }
  $('dailyBtn').disabled = claimed;
  $('dailyBtn').textContent = claimed ? 'Bono reclamado ✓' : 'Bono diario';

  // oferta del día
  const box = $('dealBox'); box.textContent = '';
  const d = dealItem();
  box.hidden = !d;
  if (d) {
    const cv = document.createElement('canvas'); cv.width = 72; cv.height = 72; cv.dataset.cos = d.cat + ':' + d.id; drawCosIcon(cv, d, d.cat);
    const t = document.createElement('div'); t.className = 'dealtxt';
    const tag = document.createElement('span'); tag.className = 'tag'; tag.textContent = 'OFERTA DEL DÍA · −' + Math.round(DEAL_OFF * 100) + ' %';
    const n = document.createElement('b'); n.textContent = d.name;
    const m = document.createElement('span'); m.className = 'meta';
    const useSrv = ACC.on && ACC.dealEnds;
    const left = useSrv ? new Date(ACC.dealEnds) : new Date(); if (!useSrv) left.setHours(24, 0, 0, 0);
    const mins = Math.max(1, Math.round((left - new Date()) / 60000));
    m.textContent = `Termina en ${Math.floor(mins / 60)} h ${mins % 60} min · ${d.desc}`;
    const p = document.createElement('div'); p.className = 'dealprice';
    const old = document.createElement('s'); old.textContent = d.price;
    p.append(old, gemEl(priceOf(d)));
    t.append(tag, n, m, p);
    const go_ = document.createElement('button'); go_.className = 'btn primary'; go_.textContent = 'Probar';
    go_.onclick = () => { ST.cat = 'all'; ST.sel = d; renderStore(); };
    box.append(cv, t, go_);
  }

  // pestañas
  const tabs = $('stTabs'); tabs.textContent = '';
  ST_TABS.forEach(([id, name]) => {
    const b = document.createElement('button'); b.className = 'tab' + (ST.cat === id ? ' on' : ''); b.textContent = name;
    b.onclick = () => { ST.cat = id; renderStore(); };
    tabs.appendChild(b);
  });

  // cuadrícula
  const grid = $('stGrid'); grid.textContent = '';
  let list = COS_DATA.filter((it) => ST.cat === 'all' || (ST.cat === 'mine' ? hasCos(it.cat, it.id) : it.cat === ST.cat));
  list = list.slice().sort((a, b) => (hasCos(a.cat, a.id) - hasCos(b.cat, b.id)) || (RAR_ORDER[a.rar] - RAR_ORDER[b.rar]) || a.name.localeCompare(b.name));
  if (!list.length) { const p = document.createElement('p'); p.className = 'meta'; p.textContent = 'Aquí no hay nada todavía.'; grid.appendChild(p); }
  list.forEach((it) => {
    const have = hasCos(it.cat, it.id);
    const el = document.createElement('button');
    el.className = 'cos' + (ST.sel && ST.sel.cat === it.cat && ST.sel.id === it.id ? ' sel' : '') + (have ? ' have' : '');
    const cv = document.createElement('canvas'); cv.width = 72; cv.height = 72; cv.dataset.cos = it.cat + ':' + it.id; drawCosIcon(cv, it, it.cat);
    const n = document.createElement('span'); n.textContent = it.name;
    const r = document.createElement('span'); r.className = 'rar ' + it.rar; r.textContent = RAR_NAME[it.rar];
    el.append(cv, n, r);
    if (have) { const o = document.createElement('span'); o.className = 'owned'; o.textContent = S.cos[it.cat] === it.id ? 'EQUIPADO' : (isDev() && !ownedNow().includes(it.cat + ':' + it.id) && it.price > 0 ? 'DEV' : 'TUYO'); el.appendChild(o); }
    else {
      const pr = gemEl(priceOf(it));
      if (priceOf(it) !== it.price) { const old = document.createElement('s'); old.textContent = it.price; pr.prepend(old); }
      el.appendChild(pr);
    }
    el.onclick = () => { ST.sel = it; ST.msg = ''; renderStore(); };
    grid.appendChild(el);
  });

  // panel del cosmético elegido (probador)
  const info = $('stInfo'); info.textContent = '';
  const it = ST.sel;
  if (!it) {
    const p = document.createElement('p'); p.className = 'meta';
    p.textContent = 'Elige un cosmético para probártelo antes de comprarlo. Arrastra el personaje para girarlo.';
    info.appendChild(p);
  } else {
    const have = hasCos(it.cat, it.id);
    const h = document.createElement('b'); h.textContent = it.name;
    const rr = document.createElement('span'); rr.className = 'rar ' + it.rar; rr.textContent = RAR_NAME[it.rar];
    const ds = document.createElement('p'); ds.className = 'meta'; ds.textContent = it.desc;
    info.append(h, rr, ds);
    const b = document.createElement('button'); b.className = 'btn primary';
    if (have) { const on = S.cos[it.cat] === it.id; b.textContent = on ? 'Quitar' : 'Equipar'; if (on) b.classList.remove('primary'); b.onclick = () => toggleEquip(it); }
    else {
      const pr = priceOf(it), short = pr - gemsNow();
      b.textContent = ST.busy ? 'Comprando…' : short > 0 ? `Faltan ${short} gemas · ${pr}` : 'Comprar · ' + pr + ' gemas';
      if (ST.busy) b.classList.add('buying');
      b.onclick = () => buyItem(it);
    }
    b.disabled = !!ST.busy;
    info.appendChild(b);
    if (it.price > 0) {
      const g = document.createElement('button'); g.className = 'btn'; g.textContent = '🎁 Regalar · ' + priceOf(it) + ' gemas';
      g.disabled = !!ST.busy; g.onclick = () => openGift(it); info.appendChild(g);
    }
    const sk = setKeyOf(it);
    if (sk) {
      const q = setInfo(sk);
      const box = document.createElement('div'); box.className = 'setbox';
      const t = document.createElement('b'); t.textContent = `Set ${q.name} · ${q.have}/${q.pieces.length}`;
      const bar = document.createElement('div'); bar.className = 'setbar'; const fill = document.createElement('i'); fill.style.width = Math.round(100 * q.have / q.pieces.length) + '%'; bar.appendChild(fill);
      box.append(t, bar);
      if (q.missing.length >= 2) {
        const sb = document.createElement('button'); sb.className = 'btn';
        sb.textContent = `Comprar las ${q.missing.length} que faltan · ${q.price} gemas (−${Math.round(SET_OFF * 100)} %)`;
        sb.disabled = !!ST.busy; sb.onclick = () => buySet(sk); box.appendChild(sb);
      } else {
        const m = document.createElement('span'); m.className = 'meta'; m.textContent = q.missing.length ? 'Solo te falta 1 pieza: cómprala suelta.' : 'Set completo ✓'; box.appendChild(m);
      }
      info.appendChild(box);
    }
  }
  const msg = ST.msg || (S.lastEarn ? `Tu última partida te dio ${S.lastEarn} gemas (${GEM_PER_MIN} por minuto jugado).` : `Ganas ${GEM_PER_MIN} gemas por cada minuto jugando. Las gemas son solo de este launcher.`);
  $('stMsg').textContent = msg;
  renderHistory();
  stViewer.rebuild();
}
$('dailyBtn').onclick = claimDaily;

// ----- Comprar gemas con dinero real (Tebex, se paga en el navegador) -----
$('gemsBtn').onclick = async () => {
  const dlg = $('dlgGems'), list = $('packList'); list.textContent = ''; $('gemsErr').textContent = '';
  if (!ACC.on) { go('accounts'); $('accErr').textContent = 'Inicia sesión o crea una cuenta Ametra para comprar gemas.'; return; }
  $('gemsInfo').textContent = 'Cargando…'; dlg.showModal();
  let info;
  try { info = await ametra.acctCall('packs'); } catch (e) { $('gemsInfo').textContent = ''; $('gemsErr').textContent = cleanErr(e); return; }
  if (!info.enabled) { $('gemsInfo').textContent = ''; $('gemsErr').textContent = 'Los pagos aún no están activados en el servidor.'; return; }
  $('gemsInfo').textContent = 'Pagas de forma segura en la web de Tebex. Las gemas llegan a tu cuenta en unos segundos.';
  info.packs.forEach((pk) => {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'btn pack';
    const price = new Intl.NumberFormat('es', { style: 'currency', currency: info.currency.toUpperCase() }).format(pk.cents / 100);
    b.textContent = `${pk.gems.toLocaleString('es')} gemas · ${price}`;
    b.onclick = async () => {
      list.querySelectorAll('button').forEach((x) => { x.disabled = true; }); $('gemsErr').textContent = '';
      try {
        const r = await ametra.acctCall('pay/checkout', { pack: pk.id, mcname: S.mode === 'ms' ? S.name : '' });
        ametra.openUrl(r.url); dlg.close();
        ST.msg = 'Se abrió la página de pago. Cuando termines, tus gemas aparecerán aquí solas.'; renderStore();
        const before = ACC.gems; let n = 0;
        const iv = setInterval(async () => { await accRefresh(); if (ACC.gems > before) { clearInterval(iv); ST.msg = `¡Pago recibido! +${(ACC.gems - before).toLocaleString('es')} gemas.`; renderStore(); } else if (++n > 60) clearInterval(iv); }, 4000);
      } catch (e) { $('gemsErr').textContent = cleanErr(e); list.querySelectorAll('button').forEach((x) => { x.disabled = false; }); }
    };
    list.appendChild(b);
  });
};
