const $ = (id) => document.getElementById(id);

// ---------- Estado ----------
const COS_NONE = { cape: 'none', wings: 'none', hat: 'none', glasses: 'none', pack: 'none', back: 'none', boots: 'none', fists: 'none', aura: 'none', face: 'none' };
const DEF = { name: '', ram: 4, java: '', last: 'default', mode: 'offline', fabric: false,
  profile: 'pvp', ametra: true, optimize: true, modernJava: true, accent: '#8a3ffc',
  hud: { logo: true, cps: true, keys: true, fps: true, ping: false, clock: false, coords: false, speed: false, dir: false, ram: false, server: false, armor: false, zoom: true, fullbright: false, crosshair: false, hitbox: false, combo: false, day: false, autosprint: false },
  cos: { ...COS_NONE },
  servers: [{ id: 'hypixel', name: 'Hypixel', host: 'mc.hypixel.net' }],
  profiles: [
    { id: 'pvp', name: 'PvP / FPS', fixed: true, mods: ['sodium', 'lithium', 'ok-zoomer'], ram: 4, cos: {}, hud: { cps: true, keys: true, fps: true, coords: false } },
    { id: 'shaders', name: 'Shaders', fixed: true, mods: ['sodium', 'iris', 'lithium'], ram: 6, cos: {}, hud: { cps: false, keys: false, fps: true, coords: false } },
    { id: 'limpio', name: 'Limpio', fixed: true, mods: [], ram: 4, cos: {}, hud: { cps: false, keys: false, fps: false, coords: false } }
  ],
  instances: [{ id: 'default', name: 'Minecraft', version: '1.21.1', tag: 'Vanilla' }] };
let S;
try { S = { ...DEF, ...JSON.parse(localStorage.getItem('ametra') || localStorage.getItem('hovy') || '{}') }; } catch { S = { ...DEF }; }
if (!S.instances.length) S.instances = DEF.instances;
if (!Array.isArray(S.servers)) S.servers = DEF.servers;
// Migración: antes solo existía la capa (S.cape).
S.cos = { ...COS_NONE, ...(S.cos || (S.cape ? { cape: S.cape } : {})) };
const save = () => localStorage.setItem('ametra', JSON.stringify(S));
const current = () => S.instances.find((i) => i.id === S.last) || S.instances[0];
let startedAt = 0, runningId = null;

// ---------- Tema (color de acento) ----------
function applyTheme() {
  const ok = /^#[0-9a-f]{6}$/i.test(S.accent || '');
  document.documentElement.style.setProperty('--ac', ok ? S.accent : DEF.accent);
}
if (S.accent === '#8a5be0' || S.accent === '#f7931e') S.accent = '#8a3ffc';
applyTheme();

// ---------- Ventana ----------
$('min').onclick = () => ametra.minimize();
$('close').onclick = () => ametra.close();

// ---------- Navegación ----------
// Si cambiaste cosméticos dentro del juego (Shift derecho -> Cosméticos), el launcher lo recoge al abrir esta pantalla.
async function syncCosFromGame() {
  try {
    const cfg = await ametra.readConfig(current().id);
    if (cfg && cfg.cosmetics && typeof cfg.cosmetics === 'object') { S.cos = { ...S.cos, ...cfg.cosmetics }; save(); }
  } catch { /* sin config todavía */ }
}
function go(page) {
  document.body.classList.toggle('home-screen', page === 'home');
  document.querySelectorAll('[data-page]').forEach((x) => x.classList.toggle('on', x.dataset.page === page));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === 'page-' + page));
  if (page === 'mods') renderClient();
  syncEdition();
  if (page === 'cosmetics') { renderCos(); syncCosFromGame().then(() => renderCos()); }
  if (page === 'servers') { loadPartners(); renderServers(); }
  if (page === 'challenges') renderChallenges();
  if (page === 'store' && typeof renderStore === 'function') renderStore();
  if ((page === 'store' || page === 'accounts') && typeof accRefresh === 'function') accRefresh();
  if (page === 'mods' && typeof renderModLib === 'function') renderModLib();
}
document.querySelectorAll('[data-page]').forEach((b) => { b.onclick = () => go(b.dataset.page); });
$('lbInst').onclick = () => go('home');

// ---------- Bedrock ----------
async function renderBedrock() {
  const box = $('bedLists'); box.textContent = '';
  let r; try { r = await ametra.bedrockList(); } catch { return; }
  if (!r.found) { const p = document.createElement('p'); p.className = 'meta'; p.textContent = 'Abre Minecraft Bedrock una vez para que se cree su carpeta de datos.'; box.appendChild(p); return; }
  const section = (title, items, actions) => {
    const h = document.createElement('b'); h.textContent = `${title} (${items.length})`; box.appendChild(h);
    if (!items.length) { const p = document.createElement('p'); p.className = 'meta'; p.textContent = 'Nada instalado.'; box.appendChild(p); return; }
    const ul = document.createElement('ul'); ul.className = 'optmods';
    items.forEach((it) => {
      const li = document.createElement('li'); li.textContent = it.name; li.title = it.dir;
      actions(it).forEach((a) => { const bt = document.createElement('button'); bt.className = 'del'; bt.textContent = a.text; bt.title = a.title; bt.onclick = a.run; li.appendChild(bt); });
      ul.appendChild(li);
    });
    box.appendChild(ul);
  };
  const del = (kind) => (it) => [{ text: '×', title: 'Borrar', run: async () => {
    if (!confirm(`¿Borrar "${it.name}"? No se puede deshacer.`)) return;
    await ametra.bedrockRemove({ kind, dir: it.dir }); renderBedrock();
  } }];
  section('Packs de recursos', r.resource, del('resource'));
  section('Add-ons (behavior packs)', r.behavior, del('behavior'));
  section('Mundos', r.worlds, (it) => [{ text: '⭳', title: 'Guardar copia de seguridad (.mcworld)', run: async () => {
    $('bedMsg').textContent = 'Creando copia…';
    const b = await ametra.bedrockBackup(it.dir);
    $('bedMsg').textContent = b.ok ? `Copia guardada: ${b.file}` : (b.canceled ? '' : b.error);
  } }]);
}
function syncEdition() {
  const b = !!(current() && current().edition === 'bedrock');
  document.body.classList.toggle('is-bedrock', b);
  $('bedCard').hidden = !b;
  if (b) renderBedrock();
  if (b) $('modWarn').textContent = '';
}
$('bedImport').onclick = async () => {
  const n = await ametra.bedrockImport();
  $('bedMsg').textContent = n ? `Se abrieron ${n} archivo(s) con Minecraft: acepta la importación dentro del juego.` : 'No se importó nada.';
};
$('bedAmetra').onclick = async () => {
  $('bedMsg').textContent = (await ametra.bedrockAmetra())
    ? 'Se abrió con Minecraft: acepta la importación. Luego actívalo en Ajustes → Almacenamiento/Recursos globales.'
    : 'No se pudo abrir el pack (¿está instalado Minecraft Bedrock?).';
};
$('bedFolder').onclick = async () => {
  $('bedMsg').textContent = (await ametra.bedrockFolder()) ? '' : 'No se encontró la carpeta: abre Minecraft Bedrock una vez para que se cree.';
};

// ---------- Avatar de píxeles (según el nombre) ----------
function drawAvatar() {
  const c = $('avatar').getContext('2d');
  if (skinImg && (S.mode === 'ms' || S.offSkin)) {
    c.clearRect(0, 0, 8, 8);
    c.drawImage(skinImg, 8, 8, 8, 8, 0, 0, 8, 8);
    c.drawImage(skinImg, 40, 8, 8, 8, 0, 0, 8, 8);
    return;
  }
  const n = S.name || 'Steve';
  let h = 0; for (const ch of n) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const skins = ['#e8b98f', '#c98c62', '#f0cfae', '#8d5a3b'];
  const hairs = ['#3b2415', '#1b1b1b', '#7a4a21', '#b8862e'];
  const skin = skins[h % 4], hair = hairs[(h >> 3) % 4];
  c.clearRect(0, 0, 8, 8);
  c.fillStyle = skin; c.fillRect(0, 0, 8, 8);
  c.fillStyle = hair; c.fillRect(0, 0, 8, 2); c.fillRect(0, 2, 1, 1); c.fillRect(7, 2, 1, 1);
  c.fillStyle = '#fff'; c.fillRect(1, 4, 2, 1); c.fillRect(5, 4, 2, 1);
  c.fillStyle = '#3a2b8f'; c.fillRect(2, 4, 1, 1); c.fillRect(5, 4, 1, 1);
  c.fillStyle = '#8a4a3a'; c.fillRect(3, 6, 2, 1);
}

function drawVersionSkin() {
  const c = $('versionSkin');
  if (!c) return;
  const x = c.getContext('2d'); x.clearRect(0, 0, 64, 64); x.imageSmoothingEnabled = false;
  if (skinImg && (S.mode === 'ms' || S.offSkin)) {
    x.drawImage(skinImg, 8, 8, 8, 8, 8, 8, 48, 48);
    x.drawImage(skinImg, 40, 8, 8, 8, 8, 8, 48, 48);
    return;
  }
  const a = $('avatar');
  if (a) x.drawImage(a, 0, 0, 8, 8, 8, 8, 48, 48);
}

// ---------- Tiempo y estadísticas ----------
function fmtTime(ms) {
  const m = Math.floor(ms / 60000);
  if (m < 1) return '0 min';
  const h = Math.floor(m / 60);
  return h ? `${h} h ${m % 60} min` : `${m} min`;
}
function ago(t) {
  if (!t) return 'Nunca';
  const d = Math.floor((Date.now() - t) / 86400000);
  return d < 1 ? 'Hoy' : d === 1 ? 'Ayer' : `Hace ${d} días`;
}
function renderStats() {
  $('stTime').textContent = fmtTime(S.instances.reduce((a, i) => a + (i.time || 0), 0));
  $('stInst').textContent = S.instances.length;
  $('stLast').textContent = ago(Math.max(0, ...S.instances.map((i) => i.last || 0)));
}

// ---------- Inicio ----------
const COLORS = ['#f7931e', '#f0b27a', '#85c1e9', '#8fd6a8', '#f1948a'];

function greet() {
  const h = new Date().getHours();
  const hi = h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  $('greet').textContent = hi + ', ';
  const s = document.createElement('span');
  s.textContent = S.name || 'jugador';
  $('greet').appendChild(s);
  $('acctName').textContent = S.name || 'Jugador';
  $('acctMode').textContent = S.mode === 'ms' ? 'Microsoft' : 'Sin conexión';
}

function render() {
  greet(); drawAvatar();
  const cur = current();
  const meta = `${S.fabric && cur.edition !== 'bedrock' ? 'Fabric' : cur.tag} · ${cur.version} · ${fmtTime(cur.time || 0)} jugados`;
  $('versionPlayer').textContent = S.name || 'Jugador';
  $('versionPlayerMeta').textContent = `${S.mode === 'ms' ? 'Microsoft conectado' : 'Sin conexión'} · ${cur.version} seleccionado`;
  drawVersionSkin();
  $('heroName').textContent = cur.name;
  $('heroMeta').textContent = meta;
  $('lbName').textContent = cur.name;
  $('lbMeta').textContent = `${S.fabric && cur.edition !== 'bedrock' ? 'Fabric' : cur.tag} · ${cur.version}`;
  $('lbIco').textContent = cur.name[0].toUpperCase();

  $('heroUser').textContent = S.name || 'Jugador';
  const box = $('instances');
  box.textContent = '';
  const ed = (i) => (i.edition === 'bedrock' ? 'bedrock' : 'java');
  const list = S.instances.map((inst, i) => ({ inst, i })).filter((x) => ed(x.inst) === (window.recentEd || 'java'))
    .sort((a, b) => (b.inst.id === cur.id) - (a.inst.id === cur.id) || (b.inst.last || 0) - (a.inst.last || 0));
  list.forEach(({ inst, i }, k) => {
    const el = document.createElement('div');
    el.className = 'inst' + (inst.id === cur.id ? ' sel' : '') + (k === 0 ? ' first' : '');
    const ico = document.createElement('div');
    ico.className = 'ico'; ico.textContent = inst.name[0].toUpperCase();
    ico.style.background = COLORS[i % COLORS.length];
    const info = document.createElement('div');
    info.className = 'info';
    const b = document.createElement('b'); b.textContent = inst.name;
    const m = document.createElement('span'); m.className = 'meta'; m.textContent = `${inst.tag} ${inst.version} - ${ago(inst.last)}`;
    info.append(b, m);
    const play = document.createElement('button');
    play.className = 'gbtn'; play.textContent = '▶ Jugar';
    play.onclick = (e) => { e.stopPropagation(); S.last = inst.id; save(); render(); launch(mainServer()); };
    const gear = document.createElement('button');
    gear.className = 'gear'; gear.textContent = '⚙'; gear.title = 'Editar perfil'; gear.setAttribute('aria-label', 'Editar perfil');
    gear.onclick = (e) => { e.stopPropagation(); if (inst.edition === 'bedrock') { S.last = inst.id; save(); render(); go('mods'); } else openInstDialog(inst); };
    el.append(ico, info, play, gear);
    if (S.instances.length > 1) {
      const del = document.createElement('button');
      del.className = 'del'; del.textContent = '×'; del.title = 'Eliminar'; del.setAttribute('aria-label', 'Eliminar');
      del.onclick = (e) => { e.stopPropagation(); S.instances = S.instances.filter((x) => x.id !== inst.id); save(); render(); };
      el.append(del);
    }
    el.onclick = () => { S.last = inst.id; save(); render(); };
    box.appendChild(el);
  });
  renderStats();
  renderServers();
  syncEdition();
}

let editingInst = null; // instancia que se edita (null = nueva)
function openInstDialog(inst) {
  editingInst = inst ? inst.id : null;
  $('dlgTitle').textContent = inst ? 'Editar instancia' : 'Añadir instancia';
  $('dlgOk').textContent = inst ? 'Guardar' : 'Crear';
  $('iEdWrap').hidden = !!inst;
  $('iName').value = inst ? inst.name : '';
  $('iEdition').value = 'java';
  $('iVerWrap').hidden = false;
  if (inst) {
    const sel = $('iVersion');
    if (![...sel.options].some((o) => o.value === inst.version || o.textContent === inst.version)) {
      const o = document.createElement('option'); o.textContent = inst.version; sel.prepend(o);
    }
    sel.value = inst.version;
  }
  $('dlg').showModal(); $('iName').focus();
}
$('iEdition').onchange = () => { $('iVerWrap').hidden = $('iEdition').value === 'bedrock'; };
$('dlgForm').addEventListener('submit', async (e) => {
  if (e.submitter && e.submitter.value === 'ok' && editingInst) {
    const inst = S.instances.find((x) => x.id === editingInst);
    editingInst = null;
    if (!inst) return;
    inst.name = $('iName').value.trim() || inst.name;
    const nv = $('iVersion').value;
    const changed = nv && nv !== inst.version;
    inst.version = nv || inst.version;
    save(); render();
    if (changed) {
      // al cambiar de versión se bajan de nuevo, para esa versión, los mods que tenías instalados
      $('status').textContent = `Actualizando tus mods a Minecraft ${inst.version}…`;
      try {
        const r = await ametra.syncMods({ id: inst.id, version: inst.version });
        $('status').textContent = r.changed
          ? `Mods listos para ${inst.version}: ${r.installed.length} actualizado(s)` + (r.dropped.length ? `; sin versión para ${inst.version}: ${r.dropped.join(', ')}` : '') + '.'
          : `Instancia ahora en ${inst.version}.`;
      } catch (err) { $('status').textContent = 'No se pudieron actualizar los mods: ' + cleanErr(err); }
    }
    return;
  }
  editingInst = null;
  if (e.submitter && e.submitter.value === 'ok') {
    const name = $('iName').value.trim() || 'Nueva instancia';
    const id = 'i' + Date.now();
    if ($('iEdition').value === 'bedrock') S.instances.push({ id, name, version: 'Bedrock', tag: 'Bedrock', edition: 'bedrock' });
    else S.instances.push({ id, name, version: $('iVersion').value, tag: 'Vanilla' });
    S.last = id; save(); render();
  }
});

// ---------- Ajustes y perfil ----------
$('ram').value = S.ram; $('ramLabel').textContent = S.ram + ' GB';
$('ram').oninput = () => { S.ram = $('ram').value; $('ramLabel').textContent = S.ram + ' GB'; save(); };
$('java').value = S.java;
$('java').oninput = () => { S.java = $('java').value.trim(); save(); };
$('name').value = S.name;
$('name').oninput = () => { S.name = $('name').value.trim(); save(); greet(); drawAvatar(); };

// ---------- Progreso ----------
const labels = { assets: 'Descargando recursos', classes: 'Descargando librerías', natives: 'Preparando archivos nativos', 'version-jar': 'Descargando Minecraft' };
ametra.on('progress', ({ type, percent }) => {
  $('bar').hidden = false; $('fill').style.width = percent + '%';
  $('status').textContent = `${labels[type] || 'Descargando'}… ${percent}%`;
});
ametra.on('started', () => { $('fill').style.width = '100%'; $('status').textContent = 'Minecraft se está abriendo…'; startedAt = Date.now(); });
ametra.on('warn', (m) => { $('modWarn').textContent = m || ''; });
ametra.modStatus().then((ok) => {
  if (!ok && S.ametra !== false) $('modWarn').textContent = 'Falta el mod de RzClient (mod/rzmod.jar): sin él NO aparece el menú de Shift derecho. Compila el mod (README → GitHub Actions) y cópialo ahí.';
}).catch(() => {});
let lastHint = '';
ametra.on('hint', (m) => { lastHint = m; });
ametra.on('closed', (code) => {
  if (startedAt && runningId) {
    const i = S.instances.find((x) => x.id === runningId);
    if (i) { i.time = (i.time || 0) + (Date.now() - startedAt); i.last = Date.now(); save(); render(); }
    if (typeof awardPlaytime === 'function') awardPlaytime(Date.now() - startedAt);
    startedAt = 0;
  }
  resetPlay();
  $('status').textContent = code === 0 ? '' : (lastHint || `Minecraft se cerró con código ${code}.`);
  lastHint = '';
});

// ---------- Versiones ----------
const AMETRA_MC = new Set(['1.19','1.19.1','1.19.2','1.19.3','1.19.4','1.20','1.20.1','1.20.2','1.20.3','1.20.4','1.20.5','1.20.6','1.21','1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10','1.21.11']);
const FAMOUS = ['1.21.4', '1.21.1', '1.20.4', '1.20.1', '1.19.4', '1.18.2', '1.16.5', '1.12.2', '1.8.9', '1.7.10'];
async function loadVersions() {
  let all = [];
  try { all = await ametra.versions(); } catch { /* sin internet: solo las famosas */ }
  const sel = $('iVersion');
  sel.textContent = '';
  const group = (label, list) => {
    const g = document.createElement('optgroup');
    g.label = label;
    list.forEach((v) => { const o = document.createElement('option'); o.textContent = v; g.appendChild(o); });
    sel.appendChild(g);
  };
  group('Más famosas', FAMOUS);
  const rest = all.filter((v) => !FAMOUS.includes(v));
  if (rest.length) group('Todas las versiones', rest);
}
loadVersions();



// ---------- Cuenta ----------
let msOk = false;
function paintAccount() {
  const ms = S.mode === 'ms';
  $('modeOff').classList.toggle('on', !ms);
  $('modeMs').classList.toggle('on', ms);
  $('name').disabled = ms;
  $('acctInfo').textContent = ms
    ? (msOk ? 'Sesión de Microsoft iniciada.' : 'Pulsa "Microsoft (premium)" para iniciar sesión.')
    : 'Modo sin conexión: sirve para un jugador y servidores sin verificación. Los servidores premium dirán "La sesión no es válida": para esos usa "Microsoft (premium)".';
  greet();
}
$('modeOff').onclick = () => { S.mode = 'offline'; save(); paintAccount(); refreshSkin(); };
$('modeMs').onclick = async () => {
  S.mode = 'ms'; paintAccount(); refreshSkin();
  $('acctInfo').textContent = 'Abriendo inicio de sesión…';
  try {
    const r = await ametra.loginMs();
    applyLogin(r);
  } catch (e) { msOk = false; $('acctInfo').textContent = 'No se pudo iniciar sesión: ' + e.message; return; }
  paintAccount();
};
let pickedSrc = null, pickedVariant = 'classic';
function setVariant(v) {
  pickedVariant = v;
  $('varClassic').classList.toggle('on', v === 'classic');
  $('varSlim').classList.toggle('on', v === 'slim');
  if (pickedSrc) setSkin(pickedSrc, v === 'slim', true, 'Vista previa');
}
$('varClassic').onclick = () => setVariant('classic');
$('varSlim').onclick = () => setVariant('slim');
$('skin').onclick = async () => {
  const r = await ametra.pickSkin();
  if (!r) return;
  if (r.error) { $('skinMsg').textContent = r.error; return; }
  pickedSrc = r.dataUrl;
  $('skinApply').hidden = false;
  setVariant(S.slim ? 'slim' : 'classic');
  $('skinMsg').textContent = S.mode === 'ms' && msOk
    ? 'Así se verá. Elige el tipo de brazos y pulsa "Aplicar a mi cuenta".'
    : 'Así se verá. Elige el tipo de brazos y pulsa "Aplicar a mi cuenta": se verá en el juego con RzClient.';
};
$('skinSave').onclick = async () => {
  const b = $('skinSave'); b.disabled = true; b.textContent = 'Guardando…';
  try {
    if (S.mode !== 'ms') { // sin conexión (no premium): la skin se guarda en el launcher y la muestra el mod en el juego
      const res = await ametra.saveLocalSkin(pickedVariant);
      S.offSkin = res.skin; S.offSlim = !!res.slim; save();
      pickedSrc = null; $('skinApply').hidden = true;
      refreshSkin(); paintAccount();
      $('skinMsg').textContent = '¡Skin guardada! La verás en el juego (necesitas RzClient activado en Ajustes).';
    } else {
      if (!msOk) { $('skinMsg').textContent = 'Inicia sesión con Microsoft (premium) para aplicar la skin a tu cuenta.'; b.disabled = false; b.textContent = 'Aplicar a mi cuenta'; return; }
      const res = await ametra.uploadSkin(pickedVariant);
      S.skin = res.skin; S.slim = !!res.slim; save();
      pickedSrc = null; $('skinApply').hidden = true;
      refreshSkin();
      $('skinMsg').textContent = '¡Skin cambiada! Ya la verás en el juego.';
    }
  } catch (e) {
    $('skinMsg').textContent = String(e.message || e).replace(/^Error invoking remote method '[^']+': (Error: )?/, '');
  }
  b.disabled = false; b.textContent = 'Aplicar a mi cuenta';
};

// ---------- Fabric y ajustes del cliente ----------
$('fabric').checked = !!S.fabric;
$('ametra').checked = S.ametra !== false;
$('optimize').checked = S.optimize !== false;
$('modernJava').checked = S.modernJava !== false;
$('autoJoin').checked = S.autoJoin !== false;
$('autoJoin').onchange = () => { S.autoJoin = $('autoJoin').checked; save(); };
$('optimize').onchange = () => { S.optimize = $('optimize').checked; save(); renderOpt(); };
$('modernJava').onchange = () => { S.modernJava = $('modernJava').checked; save(); renderOpt(); };
$('ametra').onchange = () => { S.ametra = $('ametra').checked; save(); };
$('accent').onchange = () => {
  S.accent = $('accent').value; $('accentHex').textContent = S.accent.toUpperCase();
  applyTheme(); save(); writeConfig(['accent']);
};
$('fabric').onchange = () => { S.fabric = $('fabric').checked; save(); };
$('addMods').onclick = async () => {
  const n = await ametra.addMods(current().id);
  $('modsInfo').textContent = n ? `${n} mod(s) añadido(s) a "${current().name}".` : 'No se añadió ningún mod.';
};
$('openMods').onclick = () => ametra.openMods(current().id);
$('openBgs').onclick = () => ametra.openBackgrounds(current().id);
$('cosStudio').onclick = () => ametra.studioOpen();
document.querySelectorAll('.chip').forEach((c) => { c.onclick = () => { window.recentEd = c.dataset.ed; document.querySelectorAll('.chip').forEach((x) => { x.classList.toggle('on', x === c); x.textContent = x.dataset.ed === 'java' ? (x === c ? 'Java ✓' : 'JAVA') : (x === c ? 'Bedrock ✓' : 'BEDROCK'); }); render(); }; });
$('addInst').onclick = () => openInstDialog(null);
$('manageAll').onclick = () => { go('mods'); const b = document.querySelector('#modSw [data-sec="inst"]'); if (b) b.click(); };
$('addModsHome').onclick = () => { go('mods'); const b = document.querySelector('#modSw [data-sec="lib"]'); if (b) b.click(); };
$('heroMods').onclick = () => ametra.openMods(current().id);

// ---------- Jugar ----------
function resetPlay() {
  $('play').disabled = false; $('play').innerHTML = '▶&nbsp; Jugar';
  $('heroPlay').disabled = false; $('heroPlay').innerHTML = '▶&nbsp; Jugar';
  $('bar').hidden = true;
}
ametra.on('info', (m) => { $('status').textContent = m; });
ametra.on('error', (m) => {
  resetPlay(); $('status').textContent = m;
  if (/Microsoft/.test(m) && S.mode === 'ms') { msOk = false; paintAccount(); go('profile'); } // sesión caducada: te lleva a Cuenta para volver a entrar
});

async function launch(server) {
  const cur0 = current();
  if (cur0 && cur0.edition === 'bedrock') { // Bedrock: no hay login, Java ni mods; solo se abre el juego
    $('status').textContent = 'Abriendo Minecraft Bedrock…';
    const r = await ametra.bedrockPlay(server || null);
    $('status').textContent = r.ok ? 'Minecraft Bedrock se está abriendo…' : r.error;
    return;
  }
  const goProfile = () => go('profile');
  if (S.mode === 'ms' && !msOk) {
    $('status').textContent = 'Iniciando sesión con Microsoft…';
    try {
      const r = await ametra.loginMs(false);
      applyLogin(r);
    } catch (e) { $('status').textContent = 'No se pudo iniciar sesión: ' + e.message; goProfile(); return; }
  }
  if (S.mode === 'offline' && !/^\w{3,16}$/.test(S.name)) { goProfile(); $('name').focus(); return; }
  // Con la cuenta "sin conexión" los servidores premium (online-mode) echan al jugador con
  // "Error al iniciar sesión: La sesión no es válida". Se avisa una vez por servidor.
  if (S.mode === 'offline' && server && server.host) {
    const key = String(server.host).toLowerCase();
    S.offlineWarned = S.offlineWarned || {};
    if (!S.offlineWarned[key]) {
      const useMs = confirm(`Vas a entrar a ${server.host} con la cuenta SIN CONEXIÓN.\n\nSi ese servidor es premium (verifica cuentas), Minecraft mostrará "La sesión no es válida" y no te dejará entrar.\n\nAceptar = iniciar sesión con Microsoft (premium)\nCancelar = entrar igual sin conexión`);
      S.offlineWarned[key] = true; save();
      if (useMs) { $('modeMs').click(); return; }
    }
  }
  const cur = current();
  runningId = cur.id;
  syncServersCfg(true);
  $('play').disabled = true; $('play').textContent = 'Iniciando…';
  $('heroPlay').disabled = true; $('heroPlay').textContent = 'Iniciando…';
  $('status').textContent = 'Preparando…';
  ametra.play({ id: cur.id, name: S.name, version: cur.version, ram: S.ram, java: S.java, mode: S.mode, fabric: S.fabric,
    ametra: S.ametra !== false, optimize: S.optimize !== false, modernJava: S.modernJava !== false, server: server || null });
}
// Dirección a usar según la instancia: en Bedrock se prefiere la dirección Bedrock del servidor (puerto 19132 por defecto).
function targetOf(s) {
  const c = current();
  if (c && c.edition === 'bedrock') {
    const raw = (s.bedrock || s.host || '').trim();
    const m = /^([\w.-]+)(?::(\d{1,5}))?$/.exec(raw);
    if (!m) return null;
    return { host: m[1], port: m[2] || (s.bedrock ? '19132' : '25565') };
  }
  return parseHost(s.host);
}
// Servidor principal (partner marcado como principal): Jugar entra directo ahí, salvo que el jugador lo apague en Ajustes.
function mainServer() {
  if (S.autoJoin === false) return null;
  const own = (S.servers || []).find((x) => x.main);          // el que el propio jugador marcó
  if (own) return targetOf(own);
  if (!P || !Array.isArray(P.list)) return null;
  const m = P.list.find((x) => x.main);                       // el que marcó el developer
  return m ? targetOf(m) : null;
}
$('play').onclick = () => launch(mainServer());
$('heroPlay').onclick = () => launch(mainServer());


// ---------- Portada animada ----------
const STARS = Array.from({ length: 70 }, () => ({ x: Math.random(), y: Math.random(), z: Math.random() < 0.2 ? 3 : 2, p: Math.random() * 6 }));
const heroEl = $('hero'), heroC = $('heroBg'), hg = heroC.getContext('2d');
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
let hx = 0, hxT = 0;
heroEl.addEventListener('mousemove', (e) => { const b = heroEl.getBoundingClientRect(); hxT = (e.clientX - b.left) / b.width - 0.5; });
heroEl.addEventListener('mouseleave', () => { hxT = 0; });

function heroFrame(t) {
  const w = heroEl.clientWidth, h = heroEl.clientHeight;
  if (w && h) {
    if (heroC.width !== w || heroC.height !== h) { heroC.width = w; heroC.height = h; }
    if (!reduce) hx += (hxT - hx) * 0.06;
    const time = reduce ? 0 : t;
    const sky = hg.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#05010e'); sky.addColorStop(0.48, '#16052e'); sky.addColorStop(1, '#421078');
    hg.fillStyle = sky; hg.fillRect(0, 0, w, h);
    STARS.forEach((s) => { hg.fillStyle = `rgba(214,126,255,${0.28 + 0.5 * Math.abs(Math.sin(time / 900 + s.p))})`; hg.fillRect((s.x * w + hx * s.z * 30) % w, s.y * h * .72, s.z, s.z); });
    hg.save(); hg.translate(w * .68 + hx * 18, h * .2); hg.rotate(-.25); hg.fillStyle = '#b74dff'; hg.shadowColor = '#a82dff'; hg.shadowBlur = 28; hg.fillRect(-42, -42, 84, 84); hg.shadowBlur = 0; hg.fillStyle = '#d989ff'; hg.fillRect(-28, -28, 18, 18); hg.fillRect(10, -28, 18, 18); hg.fillRect(-28, 10, 18, 18); hg.fillRect(10, 10, 18, 18); hg.restore();
    [[0.34, '#26084b', 0.12, 66], [0.55, '#341060', 0.35, 52], [0.72, '#4d1780', 0.6, 42], [0.9, '#17072b', 0.85, 34]].forEach(([base, col, par, bw], li) => {
      hg.fillStyle = col;
      const off = hx * par * 70, shift = Math.floor(off / bw);
      for (let i = -2; i < w / bw + 2; i++) {
        const idx = i - shift;
        const n = Math.sin(idx * 0.7 + li * 3) + Math.sin(idx * 0.23 + li) * 1.5;
        const top = h * base - Math.round(n * 2) * 8;
        hg.fillRect(i * bw + (off % bw), top, bw + 1, h - top);
      }
    });
  }
  requestAnimationFrame(heroFrame);
}
requestAnimationFrame(heroFrame);



// ---------- Mods: catálogo estilo Feather ----------
// kind 'mod' = mod real de Modrinth (se instala en la instancia); kind 'hud' = módulo del cliente Ametra.
const CAT_NAME = { perf: 'Rendimiento', hud: 'HUD', visual: 'Visual', util: 'Utilidad' };
const CATALOG = [
  { kind: 'mod', slug: 'sodium', name: 'Sodium', cat: 'perf', desc: 'Más FPS con un motor de render optimizado.' },
  { kind: 'mod', slug: 'lithium', name: 'Lithium', cat: 'perf', desc: 'Optimiza la lógica del juego sin cambiar nada visible.' },
  { kind: 'mod', slug: 'iris', name: 'Iris Shaders', cat: 'visual', desc: 'Shaders compatibles con Sodium.' },
  { kind: 'mod', slug: 'ok-zoomer', name: 'Ok Zoomer', cat: 'util', desc: 'Zoom ajustable, como el de OptiFine.' },
  { kind: 'mod', slug: 'modmenu', name: 'Mod Menu', cat: 'util', desc: 'Lista y configura tus mods dentro del juego.' },
  { kind: 'mod', slug: 'appleskin', name: 'AppleSkin', cat: 'hud', desc: 'Muestra hambre y saturación.' },
  { kind: 'hud', key: 'fps', name: 'FPS', cat: 'hud', desc: 'Tus fotogramas por segundo en tiempo real.' },
  { kind: 'hud', key: 'cps', name: 'CPS', cat: 'hud', desc: 'Clics por segundo, izquierdo y derecho.' },
  { kind: 'hud', key: 'keys', name: 'Teclas', cat: 'hud', desc: 'WASD y ratón en pantalla, con animación.' },
  { kind: 'hud', key: 'ping', name: 'Ping', cat: 'hud', desc: 'Latencia con el servidor.' },
  { kind: 'hud', key: 'coords', name: 'Coordenadas', cat: 'hud', desc: 'Tu posición X, Y, Z.' },
  { kind: 'hud', key: 'clock', name: 'Reloj', cat: 'hud', desc: 'La hora de tu PC en el HUD.' },
  { kind: 'hud', key: 'speed', name: 'Velocidad', cat: 'hud', desc: 'Bloques por segundo.' },
  { kind: 'hud', key: 'dir', name: 'Dirección', cat: 'hud', desc: 'Hacia dónde estás mirando.' },
  { kind: 'hud', key: 'ram', name: 'Memoria', cat: 'hud', desc: 'RAM que está usando el juego.' },
  { kind: 'hud', key: 'server', name: 'Servidor', cat: 'hud', desc: 'Dirección del servidor actual.' },
  { kind: 'hud', key: 'armor', name: 'Armadura', cat: 'hud', desc: 'Durabilidad de tu armadura.' },
  { kind: 'hud', key: 'logo', name: 'Logo', cat: 'visual', desc: 'El logo de RzClient en pantalla.' },
  { kind: 'hud', key: 'zoom', name: 'Zoom', cat: 'util', desc: 'Mantén C para acercar la vista.' },
  { kind: 'hud', key: 'fullbright', name: 'Fullbright', cat: 'visual', desc: 'Brillo máximo para ver en la oscuridad.' },
  { kind: 'hud', key: 'crosshair', name: 'Crosshair', cat: 'visual', desc: 'Tu propia mira: estilo, color, tamaño y hueco.' },
  { kind: 'hud', key: 'hitbox', name: 'Hitbox', cat: 'visual', desc: 'Cajas de colisión de colores; el que apuntas se ve en rojo.' },
  { kind: 'hud', key: 'combo', name: 'Combo', cat: 'hud', desc: 'Golpes seguidos sin recibir daño.' },
  { kind: 'hud', key: 'day', name: 'Día', cat: 'hud', desc: 'Día del mundo y hora del juego.' },
  { kind: 'hud', key: 'autosprint', name: 'Sprint automático', cat: 'util', desc: 'Corres solo al avanzar, sin mantener la tecla.' }
];
const MODS = CATALOG.filter((m) => m.kind === 'mod'); // los que se instalan de verdad
const MCOL = ['#8a5be0', '#85c1e9', '#8fd6a8', '#f0b27a', '#f1948a', '#d7a8f0', '#ffd27a', '#56ccf2'];
let modBusy = false, modCat = 'all', installed = [];

document.querySelectorAll('#modTabs .tab').forEach((t) => {
  t.onclick = () => {
    modCat = t.dataset.cat;
    document.querySelectorAll('#modTabs .tab').forEach((x) => x.classList.toggle('on', x === t));
    renderModGrid();
  };
});
$('modSearch').oninput = () => renderModGrid();

function renderModGrid() {
  const cur = current();
  const q = $('modSearch').value.trim().toLowerCase();
  const grid = $('modGrid');
  grid.textContent = '';
  CATALOG.forEach((m, i) => {
    if (modCat !== 'all' && m.cat !== modCat) return;
    if (q && !(m.name + ' ' + m.desc).toLowerCase().includes(q)) return;
    const isOn = m.kind === 'mod' ? installed.includes(m.slug) : !!S.hud[m.key];
    const card = document.createElement('div');
    card.className = 'mcard' + (isOn ? ' on' : '');
    const top = document.createElement('div'); top.className = 'mtop';
    const ico = document.createElement('div'); ico.className = 'mico'; ico.textContent = m.name[0]; ico.style.background = MCOL[i % MCOL.length];
    const t = document.createElement('div');
    const b = document.createElement('b'); b.textContent = m.name;
    const c = document.createElement('span'); c.className = 'mcat'; c.textContent = CAT_NAME[m.cat] + (m.kind === 'mod' ? ' · Modrinth' : ' · RzClient');
    t.append(b, c); top.append(ico, t);
    const p = document.createElement('p'); p.textContent = m.desc;
    const foot = document.createElement('div'); foot.className = 'mfoot';
    const st = document.createElement('span'); st.className = 'mstate'; st.textContent = isOn ? (m.kind === 'mod' ? 'Instalado' : 'Activado') : (m.kind === 'mod' ? 'No instalado' : 'Desactivado');
    const sw = document.createElement('button'); sw.className = 'sw' + (isOn ? ' on' : ''); sw.setAttribute('aria-label', m.name);
    sw.onclick = async () => {
      if (m.kind === 'hud') { S.hud[m.key] = !S.hud[m.key]; save(); writeConfig(['hud']); renderModGrid(); return; }
      if (modBusy) return;
      modBusy = true; sw.disabled = true;
      const turnOn = !sw.classList.contains('on');
      $('clMsg').textContent = turnOn ? `Instalando ${m.name}…` : `Quitando ${m.name}…`;
      try {
        installed = await ametra.toggleMod({ id: cur.id, version: cur.version, slug: m.slug, on: turnOn });
        if (turnOn) { S.fabric = true; $('fabric').checked = true; save(); }
        $('clMsg').textContent = turnOn ? `${m.name} listo. Se activa al jugar con Fabric.` : `${m.name} quitado.`;
      } catch (e) { $('clMsg').textContent = `No se pudo: ${e.message}`; }
      modBusy = false;
      renderModGrid();
    };
    foot.append(st, sw);
    card.append(top, p, foot);
    grid.appendChild(card);
  });
  if (!grid.children.length) { const e = document.createElement('p'); e.className = 'meta'; e.textContent = 'No hay mods que coincidan.'; grid.appendChild(e); }
}

async function renderClient() {
  const cur = current();
  if (cur.edition === 'bedrock') { $('clInst').textContent = `Instancia: ${cur.name} · Bedrock. Aquí se instalan packs y add-ons.`; return; }
  $('clInst').textContent = `Instancia: ${cur.name} · ${cur.version}. Los mods se instalan aquí.`;
  try { installed = await ametra.clientMods(cur.id); } catch { installed = []; }
  try {
    const cfg = await ametra.readConfig(cur.id);
    if (cfg && cfg.hud) S.hud = { ...S.hud, ...cfg.hud };
    if (cfg && /^#[0-9a-f]{6}$/i.test(cfg.accent || '')) { S.accent = cfg.accent.toLowerCase(); applyTheme(); }
    if (cfg && cfg.cosmetics && typeof cfg.cosmetics === 'object') S.cos = { ...S.cos, ...cfg.cosmetics };
  } catch { /* sin config todavía */ }
  $('accent').value = S.accent || DEF.accent;
  $('accentHex').textContent = ($('accent').value || '').toUpperCase();
  renderModGrid();
  renderProfiles();
  renderOpt();
}

// ---------- Perfiles ----------
// Solo se escriben las claves indicadas: lo demás (posiciones, colores del juego) lo guarda el mod.
function writeConfig(keys = ['profile', 'cape', 'cosmetics', 'hud', 'owned']) {
  const p = S.profiles.find((x) => x.id === S.profile);
  const all = { servers: serverList(), profile: p ? p.name : null, cape: S.cos.cape || 'none', cosmetics: S.cos, hud: S.hud, accent: S.accent || DEF.accent, owned: (typeof ACC !== 'undefined' && ACC.on ? ACC.owned : S.owned) || [], dev: typeof P !== 'undefined' && !!P.dev };
  const cfg = {};
  keys.forEach((k) => { cfg[k] = all[k]; });
  ametra.saveConfig(current().id, cfg);
}
function renderProfiles() {
  const box = $('profList');
  box.textContent = '';
  S.profiles.forEach((p) => {
    const el = document.createElement('button');
    el.className = 'prof' + (p.id === S.profile ? ' sel' : '');
    const b = document.createElement('b'); b.textContent = p.name;
    const m = document.createElement('span'); m.className = 'meta'; m.textContent = `${p.mods.length} mods · ${p.ram} GB`;
    el.append(b, m);
    if (!p.fixed) {
      const x = document.createElement('span'); x.className = 'x'; x.textContent = '×';
      x.onclick = (e) => { e.stopPropagation(); S.profiles = S.profiles.filter((q) => q.id !== p.id); if (S.profile === p.id) S.profile = null; save(); renderProfiles(); };
      el.append(x);
    }
    el.onclick = () => applyProfile(p);
    box.appendChild(el);
  });
  const add = document.createElement('button');
  add.className = 'prof new'; add.textContent = '+ Guardar actual como perfil';
  add.onclick = () => { $('pName').value = ''; $('dlgProf').showModal(); $('pName').focus(); };
  box.appendChild(add);
}
async function applyProfile(p) {
  if (modBusy) return;
  modBusy = true;
  const cur = current();
  try {
    const have = await ametra.clientMods(cur.id);
    for (const m of MODS) {
      const want = p.mods.includes(m.slug);
      if (want !== have.includes(m.slug)) {
        $('clMsg').textContent = `${want ? 'Instalando' : 'Quitando'} ${m.name}…`;
        await ametra.toggleMod({ id: cur.id, version: cur.version, slug: m.slug, on: want });
      }
    }
    S.ram = p.ram; S.hud = { ...S.hud, ...p.hud }; S.profile = p.id; S.fabric = p.mods.length > 0;
    if (p.cos && Object.keys(p.cos).length) S.cos = { ...COS_NONE, ...p.cos };
    $('ram').value = S.ram; $('ramLabel').textContent = S.ram + ' GB'; $('fabric').checked = S.fabric;
    save(); writeConfig(['profile', 'cape', 'cosmetics', 'hud']);
    $('clMsg').textContent = `Perfil "${p.name}" aplicado a ${cur.name}.`;
  } catch (e) { $('clMsg').textContent = `No se pudo aplicar: ${e.message}`; }
  modBusy = false;
  renderClient();
}
$('dlgProfForm').addEventListener('submit', async (e) => {
  if (!e.submitter || e.submitter.value !== 'ok') return;
  const have = await ametra.clientMods(current().id);
  const p = { id: 'p' + Date.now(), name: $('pName').value.trim() || 'Mi perfil', mods: have.filter((s) => MODS.some((m) => m.slug === s)), ram: Number(S.ram), cos: { ...S.cos }, hud: { ...S.hud } };
  S.profiles.push(p); S.profile = p.id; save(); writeConfig(['profile']); renderProfiles();
});


// ---------- Cosméticos (datos) ----------
// Vienen de assets/cosmetics/data.js (lo genera tools/gen_cosmetics.py junto con las texturas del mod):
// así el launcher dibuja EXACTAMENTE las mismas texturas que verás en el juego.
// Coordenadas del generador: y=0 es el hombro, el frente del jugador es z negativo. En el visor 3D del launcher
// el frente es z positivo y el centro del torso es y=-2, por eso se convierte con y-8 y -z.
const COS_CATS = [['cape', 'Capas'], ['wings', 'Alas'], ['hat', 'Sombreros'], ['glasses', 'Gafas'], ['pack', 'Mochilas'], ['back', 'Espalda'], ['boots', 'Botas'], ['fists', 'Puños'], ['aura', 'Auras'], ['face', 'Caras']];
const COS = {};
COS_CATS.forEach(([c]) => { COS[c] = []; });
COS_DATA.forEach((it) => { if (COS[it.cat]) COS[it.cat].push(it); });
const RAR_NAME = { comun: 'COMÚN', raro: 'RARO', epico: 'ÉPICO', legendario: 'LEGENDARIO' };
const RAR_ORDER = { comun: 0, raro: 1, epico: 2, legendario: 3 };
const findCos = (cat, id) => (COS[cat] || []).find((i) => i.id === id);
const texUrl = (it) => 'assets/cosmetics/' + it.tex;
const TEX = {}; // imágenes ya cargadas (para los iconos)
COS_DATA.forEach((it) => { const im = new Image(); im.onload = () => { if (typeof repaintIcons === 'function') repaintIcons(); }; im.src = texUrl(it); TEX[it.tex] = im; });

// Ángulos de reposo que usa el mod: capa 6°, alas 24°.
const CAPE_REST = 6, WING_REST = 24;

// Icono 2D de un cosmético: dibuja la cara visible (frente en sombreros/gafas, espalda en el resto) con su textura.
function drawCosIcon(cv, item, cat) {
  const g = cv.getContext('2d');
  g.clearRect(0, 0, cv.width, cv.height);
  g.imageSmoothingEnabled = false;
  const N = cv.width;
  if (!item.boxes) {
    g.strokeStyle = '#666680'; g.lineWidth = 2; g.beginPath(); g.arc(N / 2, N / 2, N * 0.27, 0, Math.PI * 2); g.moveTo(N * 0.3, N * 0.7); g.lineTo(N * 0.7, N * 0.3); g.stroke();
    return;
  }
  const img = TEX[item.tex];
  if (!img || !img.complete || !img.naturalWidth) return;
  const front = ['hat', 'glasses', 'face', 'boots', 'fists', 'aura'].includes(cat);
  // cajas proyectadas: x de pantalla (desde detrás se invierte), y de pantalla, ancho, alto, región de textura
  let bs = [];
  item.boxes.forEach((b) => {
    if (cat === 'wings') {
      const a = WING_REST * Math.PI / 180;
      [-1, 1].forEach((sd) => bs.push({ x: sd * (3 + b.x * Math.cos(a)), y: b.y + 0.6, w: b.w * Math.cos(a), h: b.h, z: b.z, uv: b.uv, flip: sd > 0 }));
    } else if (cat === 'boots' || cat === 'fists') { // un par: pierna (x=±2, y=12) o brazo (x=±6, y=2)
      [-1, 1].forEach((sd) => bs.push({ x: sd * ((cat === 'boots' ? 2 : 6) + b.x), y: b.y + (cat === 'boots' ? 12 : 2), w: b.w, h: b.h, z: b.z, uv: b.uv, flip: sd > 0 }));
    } else if (cat === 'aura') { // las partículas con órbita se colocan según su fase
      const orb = (b.an & 1) !== 0, a = (b.aph || 0) * Math.PI / 180, r = orb ? b.x : 0;
      bs.push({ x: r * Math.cos(a), y: b.y - (b.ar || 0) * 0.5, w: b.w, h: b.h, z: r * Math.sin(a), uv: b.uv, flip: false });
    } else {
      bs.push({ x: front ? b.x : -b.x, y: b.y, w: b.w, h: b.h, z: b.z, uv: b.uv, flip: false });
    }
  });
  let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
  bs.forEach((b) => { x0 = Math.min(x0, b.x - b.w / 2); x1 = Math.max(x1, b.x + b.w / 2); y0 = Math.min(y0, b.y - b.h / 2); y1 = Math.max(y1, b.y + b.h / 2); });
  const s = Math.min((N * 0.82) / (x1 - x0), (N * 0.82) / (y1 - y0));
  const ox = N / 2 - ((x0 + x1) / 2) * s, oy = N / 2 - ((y0 + y1) / 2) * s;
  // lo más lejano primero: desde el frente se ve primero z positivo; desde detrás, z negativo
  bs.sort((a, b) => (front ? b.z - a.z : a.z - b.z)).forEach((b) => {
    const [u0, v0, u1, v1] = b.uv;
    const w = b.w * s, h = b.h * s, x = ox + b.x * s - w / 2, y = oy + b.y * s - h / 2;
    g.save();
    if (b.flip) { g.translate(x + w / 2, 0); g.scale(-1, 1); g.translate(-(x + w / 2), 0); }
    g.drawImage(img, u0, v0, u1 - u0, v1 - v0, x, y, w, h);
    g.restore();
  });
}
function repaintIcons() {
  document.querySelectorAll('canvas[data-cos]').forEach((cv) => {
    const [cat, id] = cv.dataset.cos.split(':');
    const it = id === 'none' ? { id: 'none' } : findCos(cat, id);
    if (it) drawCosIcon(cv, it, cat);
  });
}

// ---------- Cosméticos (pantalla) ----------
let cosCat = 'cape';
function renderCos() {
  const tabs = $('cosTabs');
  tabs.textContent = '';
  COS_CATS.forEach(([id, name]) => {
    const b = document.createElement('button');
    b.className = 'tab' + (id === cosCat ? ' on' : '');
    b.textContent = name;
    b.onclick = () => { cosCat = id; renderCos(); };
    tabs.appendChild(b);
  });
  const grid = $('cosGrid');
  grid.textContent = '';
  [{ id: 'none', name: 'Ninguno' }, ...COS[cosCat]].forEach((it) => {
    const have = it.id === 'none' || (typeof hasCos === 'function' ? hasCos(cosCat, it.id) : true);
    const el = document.createElement('button');
    el.className = 'cos' + ((S.cos[cosCat] || 'none') === it.id ? ' sel' : '') + (have ? '' : ' locked');
    const cv = document.createElement('canvas'); cv.width = 72; cv.height = 72; cv.dataset.cos = cosCat + ':' + it.id;
    drawCosIcon(cv, it, cosCat);
    const n = document.createElement('span'); n.textContent = it.name;
    el.append(cv, n);
    if (it.rar) { const r = document.createElement('span'); r.className = 'rar ' + it.rar; r.textContent = RAR_NAME[it.rar]; el.appendChild(r); }
    if (!have) { const pr = document.createElement('span'); pr.className = 'lockbadge'; pr.textContent = '🔒 ' + it.price; el.appendChild(pr); }
    el.onclick = () => {
      if (!have) { if (typeof openStoreItem === 'function') openStoreItem(cosCat, it.id); return; }
      S.cos[cosCat] = it.id; save(); writeConfig(['cape', 'cosmetics']); renderCos();
    };
    grid.appendChild(el);
  });
  const worn = COS_CATS.map(([c]) => findCos(c, S.cos[c])).filter(Boolean).map((i) => i.name);
  $('cosWear').textContent = worn.length ? 'Equipado: ' + worn.join(' · ') : 'Sin cosméticos equipados.';
  $('cosMsg').textContent = 'La vista previa usa las mismas texturas que verás en el juego (tercera persona F5 e inventario). Lo bloqueado 🔒 se compra en la Tienda. Si cambias algo con el juego abierto, se actualiza solo en unos segundos.';
  viewers.forEach((v) => { if (v.withCos) v.rebuild(); });
}
$('cosReset').onclick = () => { S.cos = { ...COS_NONE }; save(); writeConfig(['cape', 'cosmetics']); renderCos(); };

// ---------- Servidores ----------
function parseHost(h) {
  const m = /^([\w.-]+)(?::(\d{1,5}))?$/.exec((h || '').trim());
  return m ? { host: m[1], port: m[2] || '25565' } : null;
}
// ---------- Logo, MOTD y jugadores de cada servidor ----------
const SRV_STATUS = {};
function decorateServer(ico, info, nameEl, metaEl, host, fixedLogo) {
  const dot = document.createElement('span'); dot.className = 'dot';
  nameEl.prepend(dot);
  const motd = document.createElement('div'); motd.className = 'motd'; motd.hidden = true;
  info.appendChild(motd);
  if (!$('page-servers').classList.contains('active')) return; // solo se consulta con la pestaña abierta
  ametra.serverStatus(host).catch(() => ({ online: false })).then((st) => {
    SRV_STATUS[host] = st;
    const card = metaEl.closest('.srv-card');
    if (card) { card.dataset.online = st.online ? '1' : '0'; card.dataset.pl = st.players ? st.players.online || 0 : 0; }
    applySrvFilter();
    dot.className = 'dot ' + (st.online ? 'on' : 'off');
    if (!st.online) { metaEl.textContent = host + ' · sin conexión'; return; }
    const bits = [host];
    if (st.players && st.players.max) bits.push(st.players.online + '/' + st.players.max + ' jugadores');
    if (st.ping != null) bits.push(st.ping + ' ms');
    if (st.version) bits.push(st.version);
    metaEl.textContent = bits.join(' · ');
    if (st.favicon && !fixedLogo) { ico.style.backgroundImage = 'url("' + st.favicon + '")'; ico.classList.add('has-img'); ico.textContent = ''; }
    if (st.motd && st.motd.length) {
      motd.textContent = '';
      st.motd.forEach((sg) => {
        const sp = document.createElement('span'); sp.textContent = sg.t;
        if (sg.c) sp.style.color = sg.c;
        if (sg.b) sp.style.fontWeight = '700';
        if (sg.i) sp.style.fontStyle = 'italic';
        sp.style.textDecoration = [sg.u ? 'underline' : '', sg.s ? 'line-through' : ''].join(' ').trim() || 'none';
        motd.appendChild(sp);
      });
      motd.hidden = false;
    }
  });
}

// Servidores (partners + tuyos) que el mod muestra dentro del juego: pestaña SERVIDORES y lista de Multijugador.
function serverList() {
  const out = [];
  ((typeof P !== 'undefined' && P.list) || []).forEach((p) => out.push({ name: p.name, host: p.host, desc: p.desc || '', color: p.color || '#8a5be0', partner: true, main: !!p.main }));
  (S.servers || []).forEach((s) => { if (s && s.host) out.push({ name: s.name || s.host, host: s.port && Number(s.port) !== 25565 && !String(s.host).includes(':') ? s.host + ':' + s.port : s.host, desc: '', color: '#5865f2', partner: false, main: false }); });
  return out.slice(0, 40);
}
let lastSrvCfg = '';
function syncServersCfg(force) {
  try { const l = JSON.stringify(serverList()); if (!force && l === lastSrvCfg) return; lastSrvCfg = l; writeConfig(['servers']); } catch { /* aún no hay datos */ }
}
// ---------- Tarjetas de servidor (tuyos y partners) ----------
function mkSrvBtn(txt, label, fn, cls) {
  const x = document.createElement('button');
  x.className = cls || 'mini'; x.textContent = txt; x.title = label; x.setAttribute('aria-label', label);
  x.onclick = fn; return x;
}
function buildSrvCard(o) {
  const el = document.createElement('div');
  el.className = 'srv-card' + (o.partner ? ' is-partner' : '');
  el.dataset.name = String(o.name).toLowerCase();
  el.dataset.host = o.host;
  el.dataset.online = '0'; el.dataset.pl = '0';

  const banner = document.createElement('div'); banner.className = 'sc-banner';
  banner.style.background = `radial-gradient(120% 140% at 0% 0%, ${o.color}66, transparent 60%), linear-gradient(160deg, #1a1426, #09070d)`;
  const ico = document.createElement('div'); ico.className = 'sc-ico';
  ico.style.background = o.color;
  ico.textContent = (o.name[0] || '?').toUpperCase();
  if (o.logo) { ico.style.backgroundImage = 'url("' + o.logo + '")'; ico.classList.add('has-img'); ico.textContent = ''; }
  banner.appendChild(ico);
  const badges = document.createElement('div'); badges.className = 'sc-badges';
  if (o.partner) { const p = document.createElement('span'); p.className = 'badge'; p.textContent = 'PARTNER'; badges.appendChild(p); }
  if (o.main) { const mb = document.createElement('span'); mb.className = 'badge alt'; mb.textContent = 'ENTRA AL JUGAR'; badges.appendChild(mb); }
  banner.appendChild(badges);
  el.appendChild(banner);

  const body = document.createElement('div'); body.className = 'sc-body';
  const info = document.createElement('div'); info.className = 'info';
  const b = document.createElement('b'); b.textContent = o.name;
  const m = document.createElement('span'); m.className = 'meta'; m.textContent = o.host;
  info.append(b, m);
  if (o.desc) { const d = document.createElement('span'); d.className = 'meta desc'; d.textContent = o.desc; info.appendChild(d); }
  body.appendChild(info);

  const foot = document.createElement('div'); foot.className = 'sc-foot';
  const join = document.createElement('button'); join.className = 'btn primary'; join.textContent = 'Unirse';
  join.onclick = o.onJoin;
  foot.appendChild(join);
  (o.actions || []).forEach((x) => foot.appendChild(x));
  body.appendChild(foot);
  el.appendChild(body);

  decorateServer(ico, info, b, m, o.host, !!o.logo);
  return el;
}
function applySrvFilter() {
  const q = (($('srvQ') && $('srvQ').value) || '').trim().toLowerCase();
  const onlyOn = !!($('srvOnline') && $('srvOnline').checked);
  const mode = ($('srvSort') && $('srvSort').value) || 'rec';
  ['srvList', 'partnerList'].forEach((id) => {
    const box = $(id); if (!box) return;
    const cards = [...box.querySelectorAll('.srv-card')];
    cards.forEach((c) => {
      const st = SRV_STATUS[c.dataset.host];
      const match = !q || c.dataset.name.includes(q) || String(c.dataset.host).toLowerCase().includes(q);
      c.hidden = !match || (onlyOn && !(st && st.online));
    });
    cards.sort((x, y) => {
      const sx = SRV_STATUS[x.dataset.host] || {}, sy = SRV_STATUS[y.dataset.host] || {};
      if (mode === 'pl') return ((sy.players && sy.players.online) || 0) - ((sx.players && sx.players.online) || 0);
      if (mode === 'name') return x.dataset.name.localeCompare(y.dataset.name);
      return (sy.online ? 1 : 0) - (sx.online ? 1 : 0); // recomendado: online primero
    });
    cards.forEach((c) => box.appendChild(c));
  });
}
$('srvQ') && $('srvQ').addEventListener('input', applySrvFilter);
$('srvSort') && $('srvSort').addEventListener('change', applySrvFilter);
$('srvOnline') && $('srvOnline').addEventListener('change', applySrvFilter);

function renderServers() {
  syncServersCfg();
  const box = $('srvList');
  box.textContent = '';
  if (!S.servers.length) {
    const p = document.createElement('p'); p.className = 'meta srv-empty';
    p.textContent = 'Aún no tienes servidores. Añade uno con el botón de arriba.';
    box.appendChild(p);
    return;
  }
  S.servers.forEach((s, i) => {
    const el = buildSrvCard({
      name: s.name, host: s.host, color: COLORS[i % COLORS.length], logo: s.logo, main: !!s.main, partner: false,
      onJoin: () => { const p = targetOf(s); if (p) launch(p); },
      actions: [
        mkSrvBtn('✎', 'Editar servidor', () => openSrvDialog(s, i)),
        mkSrvBtn('×', 'Quitar servidor', () => { S.servers = S.servers.filter((x) => x.id !== s.id); save(); renderServers(); })
      ]
    });
    box.appendChild(el);
  });
  applySrvFilter();
}
let editingSrv = null, sColor = COLORS[0];
const sPick = logoPicker({ prev: 'sLogoPrev', btn: 'sLogoBtn', del: 'sLogoDel', file: 'sLogoFile', err: 'sErr', name: 'sName' }, () => sColor);
function openSrvDialog(s, idx) {
  editingSrv = s ? s.id : null;
  sColor = COLORS[(s ? idx : S.servers.length) % COLORS.length];
  $('sTitle').textContent = s ? 'Editar servidor' : 'Añadir servidor';
  $('sOk').textContent = s ? 'Guardar' : 'Añadir';
  $('sName').value = s ? s.name : ''; $('sHost').value = s ? s.host : ''; $('sBedrock').value = s && s.bedrock ? s.bedrock : '';
  $('sMain').checked = !!(s && s.main); $('sErr').textContent = '';
  sPick.set(s && s.logo);
  $('dlgSrv').showModal(); $('sName').focus();
}
$('srvAdd').onclick = () => openSrvDialog(null);
$('dlgSrvForm').addEventListener('submit', (e) => {
  if (!e.submitter || e.submitter.value !== 'ok') return;
  const p = parseHost($('sHost').value);
  if (!p) { $('status').textContent = 'Dirección de servidor no válida.'; return; }
  if ($('sBedrock').value.trim() && !/^[\w.-]+(:\d{1,5})?$/.test($('sBedrock').value.trim())) { $('status').textContent = 'Dirección Bedrock no válida.'; return; }
  const item = {
    id: editingSrv || 's' + Date.now(), name: $('sName').value.trim() || p.host,
    host: p.port === '25565' && !/:/.test($('sHost').value) ? p.host : `${p.host}:${p.port}`,
    bedrock: $('sBedrock').value.trim(), logo: sPick.get(), main: $('sMain').checked
  };
  if (item.main) S.servers.forEach((x) => { x.main = false; }); // solo uno entra al pulsar Jugar
  const k = S.servers.findIndex((x) => x.id === editingSrv);
  if (k >= 0) S.servers[k] = item; else S.servers.push(item);
  save(); renderServers();
});

// ---------- Servidores partner + developer ----------
let P = { list: [], dev: false, source: 'bundled' };
let editingPartner = null; // id del partner que se edita (null = nuevo)
const cleanErr = (e) => String((e && e.message) || e).replace(/^Error invoking remote method '[^']+': (Error: )?/, '');

// La sesión developer dura lo que dura el launcher: al abrirlo se vuelve a bloquear lo comprado dentro del juego.
setTimeout(() => { try { writeConfig(['dev']); } catch { /* sin instancia todavía */ } }, 0);

async function loadPartners() {
  try { P = await ametra.partnersGet(); } catch { P = { list: [], dev: false, source: 'bundled' }; }
  try { writeConfig(['dev']); } catch { /* sin instancia todavía */ }
  renderPartners();
  if (typeof renderStore === 'function' && $('page-store').classList.contains('active')) renderStore();
}
async function persistPartners() {
  try { P.list = await ametra.partnersSave(P.list); }
  catch (e) { $('status').textContent = cleanErr(e); await loadPartners(); return; }
  renderPartners();
}
function movePartner(i, d) {
  const j = i + d;
  if (j < 0 || j >= P.list.length) return;
  [P.list[i], P.list[j]] = [P.list[j], P.list[i]];
  persistPartners();
}
function renderPartners() {
  syncServersCfg();
  const box = $('partnerList');
  box.textContent = '';
  $('devTools').hidden = !P.dev;
  $('devBtn').textContent = P.dev ? 'Developer ✓' : 'Developer';
  if (!P.list.length) {
    const p = document.createElement('p'); p.className = 'meta srv-empty';
    p.textContent = P.dev ? 'Aún no hay partners. Añade el primero con el botón de arriba.' : 'Próximamente: servidores aliados de RzClient.';
    box.appendChild(p);
    return;
  }
  P.list.forEach((s, i) => {
    const actions = [];
    if (s.url) actions.push(mkSrvBtn('Web', 'Abrir web o Discord', () => ametra.openUrl(s.url), 'link'));
    if (P.dev) {
      actions.push(mkSrvBtn('↑', 'Subir', () => movePartner(i, -1)));
      actions.push(mkSrvBtn('↓', 'Bajar', () => movePartner(i, 1)));
      actions.push(mkSrvBtn('✎', 'Editar partner', () => openPartnerDialog(s)));
      actions.push(mkSrvBtn('×', 'Quitar partner', () => { P.list.splice(i, 1); persistPartners(); }));
    }
    const el = buildSrvCard({
      name: s.name, host: s.host, desc: s.desc || '', color: s.color || '#8a5be0', logo: s.logo,
      main: !!s.main, partner: true,
      onJoin: () => { const pr = targetOf(s); if (pr) launch(pr); },
      actions
    });
    box.appendChild(el);
  });
  applySrvFilter();
}

// --- Inicio de sesión developer ---
$('devBtn').onclick = async () => {
  if (P.dev) return;
  const st = await ametra.devStatus();
  const creating = !st.hasPassword;
  $('devTitle').textContent = creating ? 'Crear contraseña developer' : 'Iniciar sesión developer';
  $('devHelp').textContent = creating
    ? 'Crea una contraseña para gestionar los servidores partner desde este launcher.'
    : 'Introduce tu contraseña para editar los servidores partner.';
  $('devPw2Row').hidden = !creating;
  $('devOk').textContent = creating ? 'Crear' : 'Entrar';
  $('devPw').value = ''; $('devPw2').value = ''; $('devErr').textContent = '';
  $('dlgDev').dataset.mode = creating ? 'create' : 'login';
  $('dlgDev').showModal(); $('devPw').focus();
};
$('devCancel').onclick = () => $('dlgDev').close();
$('dlgDevForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const creating = $('dlgDev').dataset.mode === 'create';
  const pw = $('devPw').value;
  $('devErr').textContent = '';
  if (pw.length < 6) { $('devErr').textContent = 'La contraseña debe tener al menos 6 caracteres.'; $('devPw').focus(); return; }
  if (creating && pw !== $('devPw2').value) { $('devErr').textContent = 'Las contraseñas no coinciden.'; $('devPw2').focus(); return; }
  $('devOk').disabled = true;
  try {
    if (creating) {
      await ametra.devSetPassword(pw);
    } else {
      await ametra.devLogin(pw);
    }
  } catch (err) { $('devErr').textContent = cleanErr(err); $('devOk').disabled = false; return; }
  $('dlgDev').close();
  $('devOk').disabled = false;
  await loadPartners();
});
$('devOut').onclick = async () => { await ametra.devLogout(); await loadPartners(); };

// --- Añadir / editar partner ---
function openPartnerDialog(s) {
  editingPartner = s ? s.id : null;
  $('ptTitle').textContent = s ? 'Editar partner' : 'Añadir partner';
  $('ptName').value = s ? s.name : ''; $('ptHost').value = s ? s.host : ''; $('ptBedrock').value = s && s.bedrock ? s.bedrock : '';
  $('ptDesc').value = s ? s.desc : ''; $('ptUrl').value = s ? s.url : '';
  $('ptColor').value = s ? s.color : '#8a5be0'; $('ptErr').textContent = '';
  $('ptMain').checked = !!(s && s.main);
  ptPick.set(s && s.logo);
  $('dlgPartner').showModal(); $('ptName').focus();
}

// --- Logo de un servidor: se reduce a 128x128 (recorte cuadrado) y se guarda como imagen dentro de los datos ---
function fileToLogo(file) {
  return new Promise((resolve, reject) => {
    if (file.size > 8 * 1024 * 1024) { reject(new Error('La imagen es demasiado grande (máximo 8 MB).')); return; }
    const fr = new FileReader();
    fr.onerror = () => reject(new Error('No se pudo leer la imagen.'));
    fr.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('No se pudo leer la imagen.'));
      img.onload = () => {
        const c = document.createElement('canvas'); c.width = c.height = 128;
        const x = c.getContext('2d'); const sq = Math.min(img.width, img.height);
        x.drawImage(img, (img.width - sq) / 2, (img.height - sq) / 2, sq, sq, 0, 0, 128, 128);
        resolve(c.toDataURL('image/png'));
      };
      img.src = fr.result;
    };
    fr.readAsDataURL(file);
  });
}
// ids: prev, btn, del, file, err, name = ids de los elementos; bg() = color de fondo cuando no hay imagen.
function logoPicker(ids, bg) {
  let data = '';
  const paint = () => {
    const p = $(ids.prev);
    p.style.background = bg();
    if (data) { p.style.backgroundImage = 'url("' + data + '")'; p.style.backgroundSize = 'cover'; p.style.backgroundPosition = 'center'; p.textContent = ''; }
    else { p.style.backgroundImage = 'none'; p.textContent = ($(ids.name).value.trim()[0] || '?').toUpperCase(); }
  };
  $(ids.btn).onclick = () => $(ids.file).click();
  $(ids.file).onchange = async () => {
    const f = $(ids.file).files[0]; $(ids.file).value = '';
    if (!f) return;
    try { data = await fileToLogo(f); $(ids.err).textContent = ''; } catch (e) { $(ids.err).textContent = e.message; }
    paint();
  };
  $(ids.del).onclick = () => { data = ''; paint(); };
  $(ids.name).addEventListener('input', paint);
  return { get: () => data, set: (v) => { data = v || ''; paint(); }, paint };
}
const ptPick = logoPicker({ prev: 'ptLogoPrev', btn: 'ptLogoBtn', del: 'ptLogoDel', file: 'ptLogoFile', err: 'ptErr', name: 'ptName' }, () => $('ptColor').value);
$('ptColor').oninput = () => ptPick.paint();
$('pAdd').onclick = () => openPartnerDialog(null);
$('ptCancel').onclick = () => $('dlgPartner').close();
$('dlgPartnerForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const pr = parseHost($('ptHost').value);
  if (!pr) { $('ptErr').textContent = 'Dirección de servidor no válida.'; return; }
  if ($('ptBedrock').value.trim() && !/^[\w.-]+(:\d{1,5})?$/.test($('ptBedrock').value.trim())) { $('ptErr').textContent = 'Dirección Bedrock no válida.'; return; }
  const url = $('ptUrl').value.trim();
  if (url && !/^https:\/\/[^\s]+$/i.test(url)) { $('ptErr').textContent = 'El enlace debe empezar por https://'; return; }
  const item = {
    id: editingPartner || 'p' + Date.now(),
    name: $('ptName').value.trim() || pr.host,
    host: /:/.test($('ptHost').value) ? `${pr.host}:${pr.port}` : pr.host,
    bedrock: $('ptBedrock').value.trim(), desc: $('ptDesc').value.trim(), url, color: $('ptColor').value, logo: ptPick.get(), main: $('ptMain').checked
  };
  const i = P.list.findIndex((x) => x.id === editingPartner);
  if (item.main) P.list.forEach((x) => { x.main = false; }); // solo un servidor principal
  if (i >= 0) P.list[i] = item; else P.list.push(item);
  $('dlgPartner').close();
  persistPartners();
});
$('pExport').onclick = async () => {
  try { $('status').textContent = (await ametra.partnersExport()) ? 'partners.json exportado.' : ''; }
  catch (e) { $('status').textContent = cleanErr(e); }
};
$('pReset').onclick = async () => {
  try { await ametra.partnersReset(); $('status').textContent = 'Lista restablecida a la oficial.'; }
  catch (e) { $('status').textContent = cleanErr(e); }
  await loadPartners();
};
loadPartners();

// ---------- Skin en 3D (cuenta y cosméticos) ----------
const SK = 7;
let skinImg = null;
const skinState = { src: '', h: 64, slim: false };
const viewers = [];

function makeDefaultSkin() {
  const c = document.createElement('canvas'); c.width = c.height = 64;
  const g = c.getContext('2d');
  let h = 0; for (const ch of (S.name || 'Steve')) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const skin = ['#e8b98f', '#c98c62', '#f0cfae', '#8d5a3b'][h % 4], hair = ['#3b2415', '#1b1b1b', '#7a4a21', '#b8862e'][(h >> 3) % 4];
  const f = (col, x, y, w, hh) => { g.fillStyle = col; g.fillRect(x, y, w, hh); };
  f(skin, 0, 0, 32, 16); f(hair, 8, 0, 8, 8); f(hair, 0, 8, 32, 2); f(hair, 24, 8, 8, 8);
  f('#fff', 9, 12, 2, 1); f('#fff', 13, 12, 2, 1); f('#3a2b8f', 10, 12, 1, 1); f('#3a2b8f', 13, 12, 1, 1); f('#8a4a3a', 11, 14, 2, 1);
  f('#3aa6b5', 16, 16, 24, 16);
  [[40, 16], [32, 48]].forEach(([x, y]) => { f(skin, x, y, 16, 16); f('#3aa6b5', x, y + 4, 16, 4); });
  [[0, 16], [16, 48]].forEach(([x, y]) => { f('#3b44a5', x, y, 16, 16); f('#6b6b6b', x, y + 14, 16, 2); });
  return c.toDataURL();
}
function uv(ox, oy, w, h, d) {
  return { top: [ox + d, oy], bottom: [ox + d + w, oy], right: [ox, oy + d], front: [ox + d, oy + d], left: [ox + d + w, oy + d], back: [ox + d + w + d, oy + d] };
}
function skinFace(part, fw, fh, u, v, tf) {
  const f = document.createElement('div');
  f.className = 'face';
  f.style.width = fw * SK + 'px'; f.style.height = fh * SK + 'px';
  f.style.left = -fw * SK / 2 + 'px'; f.style.top = -fh * SK / 2 + 'px';
  f.style.backgroundImage = `url(${skinState.src})`;
  f.style.backgroundSize = `${64 * SK}px ${skinState.h * SK}px`;
  f.style.backgroundPosition = `${-u * SK}px ${-v * SK}px`;
  f.style.transform = tf;
  part.appendChild(f);
}
function skinCube(model, x, y, w, h, d, o) {
  const p = document.createElement('div');
  p.className = 'part';
  p.style.transform = `translate3d(${x * SK}px, ${y * SK}px, 0)`;
  const W = w * SK, H = h * SK, D = d * SK;
  skinFace(p, w, h, ...o.front, `translateZ(${D / 2}px)`);
  skinFace(p, w, h, ...o.back, `rotateY(180deg) translateZ(${D / 2}px)`);
  skinFace(p, d, h, ...o.right, `rotateY(-90deg) translateZ(${W / 2}px)`);
  skinFace(p, d, h, ...o.left, `rotateY(90deg) translateZ(${W / 2}px)`);
  skinFace(p, w, d, ...o.top, `rotateX(90deg) translateZ(${H / 2}px)`);
  skinFace(p, w, d, ...o.bottom, `rotateX(-90deg) translateZ(${H / 2}px)`);
  model.appendChild(p);
}
// Una cara con textura: region = [u0,v0,u1,v1] en texels de la hoja (it.ts, 64 por defecto); fw/fh = tamaño de la cara en píxeles del modelo.
function texFace(part, it, fw, fh, region, tf, f, flipX, flipY) {
  const e = document.createElement('div');
  e.className = 'face';
  const sx = (fw * SK) / (region[2] - region[0]), sy = (fh * SK) / (region[3] - region[1]);
  e.style.width = fw * SK + 'px'; e.style.height = fh * SK + 'px';
  e.style.left = -fw * SK / 2 + 'px'; e.style.top = -fh * SK / 2 + 'px';
  e.style.backgroundImage = `url(${texUrl(it)})`;
  const TS = it.ts || 64;
  e.style.backgroundSize = `${TS * sx}px ${TS * sy}px`;
  e.style.backgroundPosition = `${-region[0] * sx}px ${-region[1] * sy}px`;
  e.style.transform = tf + (flipX ? ' scaleX(-1)' : '') + (flipY ? ' scaleY(-1)' : '');
  if (f !== 1) e.style.filter = `brightness(${f})`;
  part.appendChild(e);
}
// Caja con textura. pos ya viene en el sistema del visor (x, y, z). flip = invierte la textura (ala izquierda).
function texBox(parent, it, b, flip) {
  const p = document.createElement('div');
  p.className = 'part';
  p.style.transform = `translate3d(${b.x * SK}px, ${b.y * SK}px, ${b.z * SK}px) rotateY(${b.ry || 0}deg)`;
  const W = b.w * SK, H = b.h * SK, D = b.d * SK, g = b.glow ? 1.15 : 1;
  texFace(p, it, b.w, b.h, b.uv, `translateZ(${D / 2}px)`, g, flip);
  texFace(p, it, b.w, b.h, b.uv, `rotateY(180deg) translateZ(${D / 2}px)`, b.glow ? 1.1 : 0.88, flip);
  texFace(p, it, b.d, b.h, b.uv2, `rotateY(-90deg) translateZ(${W / 2}px)`, b.glow ? 1.05 : 0.72, false);
  texFace(p, it, b.d, b.h, b.uv2, `rotateY(90deg) translateZ(${W / 2}px)`, b.glow ? 1.05 : 0.72, false);
  texFace(p, it, b.w, b.d, b.uv2, `rotateX(90deg) translateZ(${H / 2}px)`, 1.12, false, true);
  texFace(p, it, b.w, b.d, b.uv2, `rotateX(-90deg) translateZ(${H / 2}px)`, 0.6, false, true);
  parent.appendChild(p);
  return p;
}
// Monta un cosmético sobre el modelo: convierte del sistema del generador al del visor y aplica la pose de reposo del mod.
function mountCosmetic(model, cat, it) {
  const Y = -8; // y del visor = y del generador - 8
  if (cat === 'wings') {
    [-1, 1].forEach((sd) => {
      const hinge = document.createElement('div');
      hinge.className = 'part';
      hinge.style.transform = `translate3d(${sd * 3 * SK}px, ${(0.6 + Y) * SK}px, ${-2.8 * SK}px) rotateY(${sd * WING_REST}deg)`;
      it.boxes.forEach((b) => texBox(hinge, it, { ...b, x: sd * b.x, y: b.y, z: -b.z }, sd > 0));
      model.appendChild(hinge);
    });
  } else if (cat === 'cape') {
    const hinge = document.createElement('div');
    hinge.className = 'part';
    hinge.style.transform = `translate3d(0px, ${Y * SK}px, ${-2 * SK}px) rotateX(${-CAPE_REST}deg)`;
    it.boxes.forEach((b) => texBox(hinge, it, { ...b, z: -(b.z - 2) }, false));
    model.appendChild(hinge);
  } else if (cat === 'boots' || cat === 'fists') {
    // coordenadas de pierna (y=0 cadera) o de brazo (y=-2 hombro): se montan sobre la pierna / el brazo del visor
    const aw = skinState.slim ? 3 : 4;
    [-1, 1].forEach((sd) => {
      const cx = cat === 'boots' ? sd * 2 : sd * (4 + aw / 2), cy = cat === 'boots' ? 4 : -6;
      it.boxes.forEach((b) => texBox(model, it, { ...b, x: cx + sd * b.x, y: cy + b.y, z: -b.z }, sd > 0));
    });
  } else if (cat === 'aura') {
    // las partículas orbitan: cada una cuelga de un pivote con su fase y todo el conjunto gira con CSS
    const spin = document.createElement('div');
    spin.className = 'part auraspin';
    it.boxes.forEach((b) => {
      const orb = (b.an & 1) !== 0, hinge = document.createElement('div');
      hinge.className = 'part';
      hinge.style.transform = `rotateY(${-(b.aph || 0)}deg)`;
      texBox(hinge, it, orb ? { ...b, x: b.x, y: b.y + Y - (b.ar || 0) * 0.5, z: 0, ry: 0 } : { ...b, y: b.y + Y, z: -b.z }, false);
      spin.appendChild(hinge);
    });
    model.appendChild(spin);
  } else {
    it.boxes.forEach((b) => texBox(model, it, { ...b, y: b.y + Y, z: -b.z }, false));
  }
}

function makeViewer(stage, model, withCos) {
  const v = { stage, model, withCos, yaw: -25, pitch: -10, dragging: false, lx: 0, ly: 0 };
  v.rebuild = () => {
    if (!skinState.src) return;
    model.textContent = '';
    const aw = skinState.slim ? 3 : 4, old = skinState.h < 64;
    skinCube(model, 0, -12, 8, 8, 8, uv(0, 0, 8, 8, 8));
    skinCube(model, 0, -2, 8, 12, 4, uv(16, 16, 8, 12, 4));
    skinCube(model, -(4 + aw / 2), -2, aw, 12, 4, uv(40, 16, aw, 12, 4));
    skinCube(model, 4 + aw / 2, -2, aw, 12, 4, old ? uv(40, 16, aw, 12, 4) : uv(32, 48, aw, 12, 4));
    skinCube(model, -2, 10, 4, 12, 4, uv(0, 16, 4, 12, 4));
    skinCube(model, 2, 10, 4, 12, 4, old ? uv(0, 16, 4, 12, 4) : uv(16, 48, 4, 12, 4));
    if (withCos) {
      const worn = v.cosSrc ? v.cosSrc() : S.cos;
      COS_CATS.forEach(([cat]) => {
        const it = findCos(cat, worn[cat]);
        if (it) mountCosmetic(model, cat, it);
      });
    }
  };
  stage.addEventListener('pointerdown', (e) => { v.dragging = true; v.lx = e.clientX; v.ly = e.clientY; stage.setPointerCapture(e.pointerId); });
  stage.addEventListener('pointermove', (e) => {
    if (!v.dragging) return;
    v.yaw += (e.clientX - v.lx) * 0.8; v.pitch = Math.max(-40, Math.min(25, v.pitch - (e.clientY - v.ly) * 0.5));
    v.lx = e.clientX; v.ly = e.clientY;
  });
  stage.addEventListener('pointerup', () => { v.dragging = false; });
  (function spin() {
    if (stage.clientWidth) {
      if (!v.dragging && !matchMedia('(prefers-reduced-motion: reduce)').matches) v.yaw += 0.4;
      model.style.transform = `rotateX(${v.pitch}deg) rotateY(${v.yaw}deg)`;
    }
    requestAnimationFrame(spin);
  })();
  viewers.push(v);
  return v;
}
makeViewer($('skin3d'), $('model'), false);
makeViewer($('cosStage'), $('cosModel'), true);
makeViewer($('heroStage'), $('heroModel'), true);

function setSkin(src, slim, real, label) {
  const img = new Image();
  img.onload = () => {
    skinState.src = src; skinState.h = img.naturalHeight; skinState.slim = slim; skinImg = real ? img : null;
    viewers.forEach((v) => v.rebuild()); drawAvatar();
    $('skinName').textContent = label;
  };
  img.src = src;
}
function refreshSkin() {
  if (S.mode === 'ms' && S.skin) setSkin(S.skin, !!S.slim, true, S.name);
  else if (S.mode === 'ms') setSkin(makeDefaultSkin(), false, false, 'Sin skin personalizada');
  else if (S.offSkin) setSkin(S.offSkin, !!S.offSlim, true, 'Tu skin (sin conexión)');
  else setSkin(makeDefaultSkin(), false, false, 'Skin predeterminada (sin conexión)');
}
function applyLogin(a) {
  msOk = true; S.name = a.name; S.skin = a.skin || null; S.slim = !!a.slim;
  $('name').value = a.name; save(); paintAccount(); refreshSkin(); render();
}
$('name').addEventListener('input', () => { if (S.mode === 'offline') refreshSkin(); });

refreshSkin();
if (S.mode === 'ms') {
  ametra.loginMs(true).then(applyLogin).catch(() => {});
}
paintAccount();
render();
renderClient();

// ---------- Pack de optimización (tarjeta de la pestaña Cliente) ----------
const ERA = { fabric: 'Fabric', 'legacy-fabric': 'Legacy Fabric' };
let optSeq = 0;
async function renderOpt() {
  const cur = current(), seq = ++optSeq;
  if (cur.edition === 'bedrock') return;
  const on = S.optimize !== false, modern = S.modernJava !== false;
  $('optSummary').textContent = 'Calculando…';
  $('optMods').textContent = '';
  $('optNow').disabled = !on;
  let p;
  try { p = await ametra.optPlan(cur.version); } catch { $('optSummary').textContent = 'Sin internet: no se pudo calcular el plan de esta versión.'; return; }
  if (seq !== optSeq) return; // el usuario cambió de instancia mientras tanto
  const javaTxt = !modern ? `Java ${p.java.required} (el que pide Mojang)` : p.java.why;
  const loader = p.loader ? ERA[p.loader] : 'sin loader (versión muy antigua)';
  $('optSummary').textContent = on
    ? `${cur.name} · ${cur.version} · ${loader} · ${javaTxt}${p.video ? ' · ajustes de vídeo ligeros' : ''}`
    : 'Desactivado: esta instancia se juega sin el pack (los mods del pack ya instalados se quitan al jugar).';
  const ul = $('optMods');
  if (on) {
    if (p.loader === 'fabric' && AMETRA_MC.has(cur.version)) { const li = document.createElement('li'); li.className = 'ametra'; li.textContent = 'RzClient'; li.title = 'RzClient: menú con Shift derecho, HUD, crosshair y cosméticos'; ul.appendChild(li); }
    p.mods.forEach((m) => { const li = document.createElement('li'); li.textContent = m.name; li.title = m.why; ul.appendChild(li); });
    if (p.discover) { const li = document.createElement('li'); li.className = 'more'; li.textContent = '+ mods de rendimiento de Legacy Fabric (se buscan en Modrinth)'; ul.appendChild(li); }
    if (!p.mods.length && !p.discover) { const li = document.createElement('li'); li.textContent = 'Solo Java + flags de la JVM'; ul.appendChild(li); }
  }
}
$('optNow').onclick = async () => {
  const cur = current();
  $('optNow').disabled = true; $('optMsg').textContent = 'Instalando el pack…';
  try {
    const r = await ametra.optInstall({ id: cur.id, version: cur.version });
    $('optMsg').textContent = r.loader
      ? `Listo: ${r.installed.length} mod(s) instalados${r.skipped.length ? `; sin versión para ${cur.version}: ${r.skipped.join(', ')}` : ''}.`
      : 'Esta versión no tiene loader: solo se aplican Java y flags al jugar.';
  } catch (e) { $('optMsg').textContent = `No se pudo: ${e.message}`; }
  $('optNow').disabled = S.optimize === false;
};
