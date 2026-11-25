// ---------- Biblioteca de mods: lista lista-para-instalar, buscador de Modrinth, packs y gestor ----------
// Todo se instala con el mismo sistema de siempre (client:toggle): busca la versión para tu Minecraft, baja las
// dependencias obligatorias y quita versiones viejas del mismo mod. Lo que no exista para tu versión se marca.
const LIB_CATS = [['feat', 'Destacados'], ['pvp', 'PvP'], ['perf', 'Rendimiento'], ['visual', 'Visual'], ['util', 'Utilidad'], ['map', 'Mapas'], ['build', 'Construcción'], ['mr', 'Todo Modrinth']];
// slug de Modrinth + categoría. Los que no existan o no tengan versión para tu Minecraft se ocultan o se marcan solos.
const LIB = [
  ['sodium', 'perf'], ['lithium', 'perf'], ['ferrite-core', 'perf'], ['modernfix', 'perf'], ['entityculling', 'perf'], ['immediatelyfast', 'perf'],
  ['moreculling', 'perf'], ['krypton', 'perf'], ['dynamic-fps', 'perf'], ['c2me-fabric', 'perf'], ['noisium', 'perf'], ['sodium-extra', 'perf'], ['reeses-sodium-options', 'perf'],
  ['iris', 'visual'], ['continuity', 'visual'], ['entitytexturefeatures', 'visual'], ['lambdynamiclights', 'visual'], ['skinlayers3d', 'visual'], ['visuality', 'visual'], ['sound-physics-remastered', 'visual'], ['not-enough-animations', 'visual'], ['distanthorizons', 'visual'],
  ['ok-zoomer', 'pvp'], ['zoomify', 'pvp'], ['appleskin', 'pvp'], ['betterf3', 'pvp'], ['better-ping-display-fabric', 'pvp'], ['freecam', 'pvp'], ['replaymod', 'pvp'], ['mouse-tweaks', 'pvp'], ['no-chat-reports', 'pvp'], ['chat-heads', 'pvp'], ['bettermounthud', 'pvp'],
  ['modmenu', 'util'], ['yacl', 'util'], ['cloth-config', 'util'], ['roughly-enough-items', 'util'], ['emi', 'util'], ['jade', 'util'], ['wthit', 'util'], ['inventory-profiles-next', 'util'], ['shulkerboxtooltip', 'util'], ['fastquit', 'util'], ['fabric-api', 'util'],
  ['xaeros-minimap', 'map'], ['xaeros-world-map', 'map'], ['journeymap', 'map'],
  ['worldedit', 'build'], ['litematica', 'build'], ['malilib', 'build'], ['minihud', 'build'], ['tweakeroo', 'build']
].map(([slug, cat]) => ({ slug, cat }));
// "Destacados": los más pedidos de cada grupo, como en Feather.
const FEATURED = ['sodium', 'lithium', 'iris', 'modmenu', 'ok-zoomer', 'appleskin', 'xaeros-minimap', 'xaeros-world-map', 'replaymod', 'litematica', 'jade', 'freecam', 'entityculling', 'ferrite-core', 'lambdynamiclights', 'continuity', 'no-chat-reports', 'betterf3', 'mouse-tweaks', 'zoomify'];

const PACKS = [
  { id: 'pvp', name: 'Pack PvP', desc: 'FPS altos y lo útil para pelear: Sodium, Lithium, Zoom, AppleSkin, Mouse Tweaks.', slugs: ['sodium', 'lithium', 'ferrite-core', 'ok-zoomer', 'appleskin', 'mouse-tweaks', 'no-chat-reports'] },
  { id: 'fps', name: 'Pack FPS', desc: 'Todo el rendimiento: Sodium, Lithium, ModernFix, Entity Culling, ImmediatelyFast…', slugs: ['sodium', 'lithium', 'ferrite-core', 'modernfix', 'entityculling', 'immediatelyfast', 'moreculling', 'dynamic-fps', 'krypton'] },
  { id: 'vis', name: 'Pack Visual', desc: 'Shaders y detalles: Iris, Continuity, luz dinámica, sonido realista.', slugs: ['sodium', 'iris', 'continuity', 'lambdynamiclights', 'sound-physics-remastered', 'entitytexturefeatures'] },
  { id: 'surv', name: 'Pack Survival', desc: 'Mapa, información y comodidad: Xaero, Jade, Mod Menu, inventario.', slugs: ['sodium', 'lithium', 'xaeros-minimap', 'xaeros-world-map', 'jade', 'modmenu', 'appleskin', 'mouse-tweaks'] },
  { id: 'build', name: 'Pack Constructor', desc: 'Para construir: Litematica, MiniHUD, Tweakeroo, WorldEdit, Freecam.', slugs: ['sodium', 'litematica', 'malilib', 'minihud', 'tweakeroo', 'worldedit', 'freecam'] }
];

const LB = { cat: 'feat', q: '', offset: 0, total: 0, meta: {}, results: [], installed: [], busy: false, ver: '', timer: 0 };
const fmtN = (n) => n >= 1e6 ? (n / 1e6).toFixed(1).replace('.0', '') + ' M' : n >= 1e3 ? Math.round(n / 1e3) + ' k' : String(n);
const fmtSize = (b) => b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB';

// ----- Selector de sección dentro de la pestaña Mods -----
function setModSec(sec) {
  $('page-mods').dataset.sec = sec;
  document.querySelectorAll('#modSw button').forEach((b) => b.classList.toggle('on', b.dataset.sec === sec));
  if (sec === 'lib') renderModLib();
  if (sec === 'inst') renderModInst();
}
document.querySelectorAll('#modSw button').forEach((b) => { b.onclick = () => setModSec(b.dataset.sec); });
$('page-mods').dataset.sec = 'ametra';

// ----- Instalar / quitar -----
async function libToggle(slug, title, on) {
  const cur = current();
  if (LB.busy) return false;
  LB.busy = true;
  $('libMsg').textContent = on ? `Instalando ${title}…` : `Quitando ${title}…`;
  try {
    LB.installed = await ametra.toggleMod({ id: cur.id, version: cur.version, slug, on });
    if (on) { S.fabric = true; $('fabric').checked = true; save(); }
    $('libMsg').textContent = on ? `${title} listo. Se activa al jugar con Fabric.` : `${title} quitado.`;
    LB.busy = false;
    return true;
  } catch (e) {
    $('libMsg').textContent = `No se pudo con ${title}: ${cleanErr(e)}`;
    LB.busy = false;
    return false;
  }
}
async function installPack(pk) {
  if (LB.busy) return;
  const cur = current();
  $('libMsg').textContent = `Preparando ${pk.name}…`;
  let meta = {};
  try { meta = await ametra.modsMeta(pk.slugs, cur.version); } catch { /* sin internet */ }
  const todo = pk.slugs.filter((s) => meta[s] && meta[s].ok && !LB.installed.includes(s));
  const skipped = pk.slugs.filter((s) => !meta[s] || !meta[s].ok);
  let done = 0, failed = [];
  LB.busy = true;
  for (const s of todo) {
    $('libMsg').textContent = `${pk.name}: instalando ${meta[s].title} (${done + 1}/${todo.length})…`;
    try { LB.installed = await ametra.toggleMod({ id: cur.id, version: cur.version, slug: s, on: true }); done++; } catch { failed.push(meta[s].title); }
  }
  LB.busy = false;
  if (done) { S.fabric = true; $('fabric').checked = true; save(); }
  $('libMsg').textContent = `${pk.name}: ${done} instalado(s)` + (todo.length === 0 ? ' (ya lo tenías todo)' : '') +
    (skipped.length ? `; sin versión para ${cur.version}: ${skipped.join(', ')}` : '') + (failed.length ? `; fallaron: ${failed.join(', ')}` : '') + '.';
  renderLibGrid();
}

// ----- Tarjeta de mod -----
function libCard(m) {
  const on = LB.installed.includes(m.slug);
  const card = document.createElement('div');
  card.className = 'mcard' + (on ? ' on' : '') + (m.ok === false ? ' na' : '');
  const top = document.createElement('div'); top.className = 'mtop';
  const ico = document.createElement('div'); ico.className = 'mico'; ico.style.background = MCOL[(m.title.charCodeAt(0) || 0) % MCOL.length];
  if (m.icon) {
    const im = document.createElement('img'); im.src = m.icon; im.alt = ''; im.loading = 'lazy';
    im.onerror = () => { im.remove(); ico.textContent = m.title[0]; };
    ico.classList.add('has-img'); ico.appendChild(im);
  } else ico.textContent = m.title[0];
  const t = document.createElement('div'); t.className = 'mname';
  const b = document.createElement('b'); b.textContent = m.title;
  const c = document.createElement('span'); c.className = 'mcat'; c.textContent = `${fmtN(m.downloads || 0)} descargas · Modrinth`;
  t.append(b, c); top.append(ico, t);
  const p = document.createElement('p'); p.textContent = m.desc || '';
  const foot = document.createElement('div'); foot.className = 'mfoot';
  const st = document.createElement('span'); st.className = 'mstate';
  st.textContent = m.ok === false ? `Sin versión para ${LB.ver}` : on ? 'Instalado' : 'No instalado';
  foot.appendChild(st);
  if (m.ok !== false) {
    const sw = document.createElement('button'); sw.className = 'sw' + (on ? ' on' : ''); sw.setAttribute('aria-label', m.title);
    sw.onclick = async () => { sw.disabled = true; await libToggle(m.slug, m.title, !on); renderLibGrid(); };
    foot.appendChild(sw);
  }
  card.append(top, p, foot);
  return card;
}

function renderLibGrid() {
  const grid = $('libGrid'); grid.textContent = '';
  LB.results.forEach((m) => grid.appendChild(libCard(m)));
  if (!LB.results.length) { const e = document.createElement('p'); e.className = 'meta'; e.textContent = LB.loading ? 'Cargando…' : 'No hay mods que coincidan.'; grid.appendChild(e); }
  $('libMore').hidden = !(LB.online && LB.results.length < LB.total);
  const cat = document.getElementById('libChips');
  cat.querySelectorAll('.tab').forEach((x) => x.classList.toggle('on', x.dataset.cat === LB.cat));
}

// ----- Cargar la lista (curada o de Modrinth) -----
async function loadLib(append) {
  const cur = current();
  LB.ver = cur.version;
  const seq = LB.seq = (LB.seq || 0) + 1;
  LB.online = !!LB.q || LB.cat === 'mr';
  LB.loading = true;
  if (!append) { LB.results = []; LB.offset = 0; renderLibGrid(); }
  try {
    if (LB.online) {
      const r = await ametra.modsSearch({ query: LB.q, category: '', version: cur.version, offset: LB.offset, index: 'downloads' });
      if (seq !== LB.seq) return;
      LB.total = r.total; LB.results = LB.results.concat(r.hits); LB.offset = LB.results.length;
    } else {
      const slugs = LB.cat === 'feat' ? FEATURED : LIB.filter((x) => x.cat === LB.cat).map((x) => x.slug);
      const meta = await ametra.modsMeta(slugs, cur.version);
      if (seq !== LB.seq) return;
      LB.results = slugs.map((s) => meta[s]).filter(Boolean).sort((a, b) => (b.ok - a.ok) || (b.downloads - a.downloads));
      LB.total = LB.results.length;
    }
    $('libMsg').textContent = LB.busy ? $('libMsg').textContent : '';
  } catch (e) {
    if (seq !== LB.seq) return;
    $('libMsg').textContent = 'Sin conexión con Modrinth: ' + cleanErr(e);
  }
  LB.loading = false;
  renderLibGrid();
}

async function renderModLib() {
  const cur = current();
  if (!cur || cur.edition === 'bedrock') return;
  if ($('page-mods').dataset.sec !== 'lib') return;
  $('libInst').textContent = `Instancia: ${cur.name} · ${cur.version} (Fabric). Cada mod se busca para esta versión exacta.`;
  try { LB.installed = await ametra.clientMods(cur.id); } catch { LB.installed = []; }
  // packs
  const box = $('libPacks'); box.textContent = '';
  PACKS.forEach((pk) => {
    const el = document.createElement('div'); el.className = 'pack';
    const b = document.createElement('b'); b.textContent = pk.name;
    const d = document.createElement('span'); d.className = 'meta'; d.textContent = pk.desc;
    const bt = document.createElement('button'); bt.className = 'btn'; bt.textContent = `Instalar (${pk.slugs.length})`;
    bt.onclick = () => installPack(pk);
    el.append(b, d, bt); box.appendChild(el);
  });
  // chips
  const chips = $('libChips'); chips.textContent = '';
  LIB_CATS.forEach(([id, name]) => {
    const b = document.createElement('button'); b.className = 'tab' + (LB.cat === id ? ' on' : ''); b.dataset.cat = id; b.textContent = name;
    b.onclick = () => { LB.cat = id; loadLib(false); };
    chips.appendChild(b);
  });
  loadLib(false);
}
$('libSearch').oninput = () => {
  clearTimeout(LB.timer);
  LB.timer = setTimeout(() => { LB.q = $('libSearch').value.trim(); loadLib(false); }, 350);
};
$('libMore').onclick = () => loadLib(true);

// ----- Instalados: todo lo que hay en la carpeta de mods (también lo que añadiste a mano) -----
async function renderModInst() {
  const cur = current();
  if (!cur || cur.edition === 'bedrock') return;
  const box = $('instList'); box.textContent = '';
  let list = [];
  try { list = await ametra.modsList(cur.id); } catch (e) { $('instMsg').textContent = cleanErr(e); return; }
  $('instSub').textContent = `${list.length} archivo(s) en la carpeta de mods de "${cur.name}".`;
  if (!list.length) { const p = document.createElement('p'); p.className = 'meta'; p.textContent = 'Todavía no hay mods. Instala alguno desde la Biblioteca.'; box.appendChild(p); }
  list.forEach((f) => {
    const row = document.createElement('div'); row.className = 'irow' + (f.off ? ' off' : '');
    const n = document.createElement('div'); n.className = 'iname';
    const b = document.createElement('b'); b.textContent = f.file.replace(/\.disabled$/i, '');
    const m = document.createElement('span'); m.className = 'meta';
    const own = /^(rzmod|ametra-client)/i.test(f.file);
    m.textContent = fmtSize(f.size) + (f.slug ? ' · instalado desde la Biblioteca' : own ? ' · mod de RzClient' : ' · añadido a mano');
    n.append(b, m);
    const sw = document.createElement('button'); sw.className = 'sw' + (f.off ? '' : ' on'); sw.title = f.off ? 'Activar' : 'Desactivar'; sw.setAttribute('aria-label', 'Activar o desactivar');
    sw.onclick = async () => { await ametra.modsEnable({ id: cur.id, file: f.file, on: f.off }); renderModInst(); };
    row.append(n, sw);
    if (!own) {
      const del = document.createElement('button'); del.className = 'del'; del.textContent = '×'; del.title = 'Borrar'; del.setAttribute('aria-label', 'Borrar mod');
      del.onclick = async () => {
        if (!confirm(`¿Borrar "${f.file}"?`)) return;
        await ametra.modsRemove({ id: cur.id, file: f.file }); renderModInst();
      };
      row.appendChild(del);
    }
    box.appendChild(row);
  });
}
$('instAdd').onclick = async () => {
  const n = await ametra.addMods(current().id);
  $('instMsg').textContent = n ? `${n} mod(s) añadido(s).` : '';
  renderModInst();
};
$('instOpen').onclick = () => ametra.openMods(current().id);
$('instShots').onclick = () => ametra.shotsOpen(current().id);
