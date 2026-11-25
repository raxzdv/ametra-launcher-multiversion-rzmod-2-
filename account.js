// ---------- Cuenta Ametra: iniciar sesión, crear cuenta, regalar cosméticos y panel developer ----------
// Con la sesión iniciada, las gemas y los cosméticos comprados viven en el servidor de cuentas (carpeta server/).
// Sin sesión todo sigue funcionando como antes, solo en este PC. Las llamadas de red las hace main.js.
const ACC = { on: false, user: '', admin: false, gems: 0, owned: [], daily: { claimed: false, streak: 0 }, deal: null, dealEnds: 0, notes: [], history: [], host: '', secure: true, saved: '' };
let accSig = '';

function accNoteText(n) {
  const it = n.cat ? findCos(n.cat, n.id) : null;
  const nm = it ? `"${it.name}"` : 'un cosmético';
  if (n.t === 'gift') return `${n.from} te regaló ${nm}`;
  if (n.t === 'gems' && n.from === 'Tienda') return `Recibiste ${n.amount.toLocaleString('es')} gemas de tu compra`;
  if (n.t === 'gems') return `${n.from} (developer) te dio ${n.amount} gemas`;
  if (n.count) return `${n.from} (developer) te dio ${n.count} cosméticos`;
  return `${n.from} (developer) te dio ${nm}`;
}
const accUnseen = () => ACC.notes.filter((n) => !n.seen);

// Si llevas puesto algo que esta cuenta (o este PC sin cuenta) no tiene, se quita para que no se cuele.
function accSanitizeCos() {
  if (typeof hasCos !== 'function') return;
  let changed = false;
  COS_CATS.forEach(([c]) => {
    const id = S.cos[c];
    if (id && id !== 'none' && !hasCos(c, id)) { S.cos[c] = 'none'; changed = true; }
  });
  if (changed) { save(); try { writeConfig(['cape', 'cosmetics']); } catch { /* sin instancia todavía */ } }
}

function accRender() {
  $('accOut').hidden = ACC.on; $('accIn').hidden = !ACC.on;
  $('accName').textContent = ACC.user;
  $('accAdminBadge').hidden = !ACC.admin;
  $('accStats').textContent = `${ACC.gems.toLocaleString('es')} gemas · ${ACC.owned.length} cosmético${ACC.owned.length === 1 ? '' : 's'} comprado${ACC.owned.length === 1 ? '' : 's'} o regalado${ACC.owned.length === 1 ? '' : 's'}`;
  $('accHost').textContent = ACC.host ? (ACC.secure ? 'Servidor de cuentas: ' + ACC.host : 'Servidor de cuentas: ' + ACC.host + ' — tiene que usar https') : '';
  $('accRetry').hidden = !(ACC.saved && !ACC.on);
  const dot = document.querySelector('.nav button[data-page="store"]');
  if (dot) dot.classList.toggle('hasnew', ACC.on && accUnseen().length > 0);
  accDevRender(); accNotesRender();
  if ($('page-store').classList.contains('active') && typeof renderStore === 'function') renderStore();
}

function accApply(v) {
  ACC.on = true; ACC.user = v.user; ACC.admin = !!v.admin; ACC.gems = v.gems; ACC.owned = v.owned || [];
  ACC.daily = v.daily || { claimed: false, streak: 0 }; ACC.deal = v.deal || null; ACC.dealEnds = v.dealEnds || 0; ACC.notes = v.notes || []; ACC.history = v.history || []; ACC.saved = '';
  accSanitizeCos();
  try { writeConfig(['owned']); } catch { /* sin instancia todavía */ }
  const sig = JSON.stringify([ACC.user, ACC.admin, ACC.gems, ACC.owned, ACC.daily, ACC.deal, ACC.notes.map((n) => [n.at, n.seen]), ACC.history.length && ACC.history[0].at]);
  if (sig !== accSig) { accSig = sig; accRender(); }
}
function accClear() {
  Object.assign(ACC, { on: false, user: '', admin: false, gems: 0, owned: [], daily: { claimed: false, streak: 0 }, deal: null, dealEnds: 0, notes: [], history: [], saved: '' });
  accSig = '';
  accSanitizeCos();
  try { writeConfig(['owned']); } catch { /* sin instancia todavía */ }
  accRender();
}
async function accRefresh() {
  if (!ACC.on) return;
  try { accApply(await ametra.acctCall('me')); }
  catch (e) { const m = cleanErr(e); if (/caducada|Inicia sesión/.test(m)) { accClear(); $('accErr').textContent = m; } }
}
setInterval(accRefresh, 120000); // por si te regalan algo mientras tienes el launcher abierto

// ----- Entrar / crear cuenta -----
function accBusy(b) { ['accLogin', 'accReg', 'accRetry', 'accOutBtn'].forEach((id) => { $(id).disabled = b; }); }
async function accEnter(kind) {
  const u = $('accUser').value.trim(), p = $('accPw').value;
  $('accErr').textContent = '';
  if (!u || !p) { $('accErr').textContent = 'Escribe tu usuario y tu contraseña.'; return; }
  accBusy(true);
  try {
    const v = await (kind === 'reg' ? ametra.acctRegister(u, p) : ametra.acctLogin(u, p));
    $('accPw').value = '';
    accApply(v);
    $('status').textContent = kind === 'reg' ? `¡Cuenta creada! Empiezas con ${v.gems} gemas.` : `Sesión iniciada como ${v.user}.`;
  } catch (e) { $('accErr').textContent = cleanErr(e); }
  accBusy(false);
}
$('accLogin').onclick = () => accEnter('login');
$('accReg').onclick = () => accEnter('reg');
$('accPw').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); accEnter('login'); } });
$('accOutBtn').onclick = async () => {
  accBusy(true);
  try { await ametra.acctLogout(); } catch { /* se borra en local igualmente */ }
  accBusy(false); accClear();
  $('accErr').textContent = '';
};
async function accRestore() {
  accBusy(true); $('accErr').textContent = '';
  try { const v = await ametra.acctRestore(); if (v) accApply(v); }
  catch (e) {
    $('accErr').textContent = cleanErr(e);
    try { ACC.saved = (await ametra.acctInfo()).saved; } catch { ACC.saved = ''; } // si el servidor la rechazó, ya no queda sesión guardada
    accRender();
  }
  accBusy(false);
}
$('accRetry').onclick = accRestore;
(async () => {
  try { const i = await ametra.acctInfo(); ACC.host = i.host; ACC.secure = i.secure; ACC.saved = i.saved; } catch { /* sin info */ }
  accRender();
  if (ACC.saved) await accRestore();
})();

// ----- Regalar un cosmético (lo pagas tú con tus gemas) -----
const GIFT = { it: null };
function openGift(it) {
  if (!ACC.on) { go('accounts'); $('accErr').textContent = 'Inicia sesión o crea una cuenta Ametra para regalar cosméticos.'; return; }
  GIFT.it = it;
  $('giftWhat').textContent = `${it.name} · ${priceOf(it)} gemas, que se descuentan de tu cuenta. Tu amigo lo recibe al instante.`;
  $('giftTo').value = ''; $('giftErr').textContent = '';
  $('dlgGift').showModal(); $('giftTo').focus();
}
$('giftCancel').onclick = () => $('dlgGift').close();
$('dlgGiftForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const it = GIFT.it, to = $('giftTo').value.trim();
  if (!it) return;
  if (!to) { $('giftErr').textContent = 'Escribe el nombre de su cuenta.'; return; }
  if (!confirm(`¿Regalar "${it.name}" a ${to} por ${priceOf(it)} gemas?`)) return;
  if (ST.busy) return;
  $('giftOk').disabled = true; ST.busy = true;
  try {
    const v = await acctBuyCall('gift', { to, cat: it.cat, id: it.id, expect: priceOf(it) });
    $('dlgGift').close();
    accApply(v);
    ST.msg = `🎁 Le has regalado "${it.name}" a ${v.gifted.to}.`;
    renderStore();
  } catch (err) { $('giftErr').textContent = cleanErr(err); accRefresh(); }
  $('giftOk').disabled = false; ST.busy = false;
});

// ----- Panel developer: dar o quitar cosméticos y gemas a una cuenta (el servidor comprueba que eres developer) -----
let accaFilled = false;
function accDevRender() {
  if (!accaFilled && typeof COS_DATA !== 'undefined') {
    accaFilled = true;
    const sel = $('accaItem');
    const add = (parent, v, t) => { const o = document.createElement('option'); o.value = v; o.textContent = t; parent.appendChild(o); };
    add(sel, '', '— elige un cosmético —');
    add(sel, '*', 'TODOS los de pago');
    COS_CATS.forEach(([c, name]) => {
      const g = document.createElement('optgroup'); g.label = name;
      COS_DATA.filter((it) => it.cat === c && it.price > 0).forEach((it) => add(g, it.cat + ':' + it.id, `${it.name} (${it.price})`));
      sel.appendChild(g);
    });
  }
  $('accaHelp').textContent = ACC.admin
    ? `Sesión developer: ${ACC.user}. Los cambios se aplican a la cuenta al instante.`
    : 'Para usar esto inicia sesión arriba con una cuenta developer del servidor.';
  ['accaGive', 'accaTake', 'accaGemsBtn', 'accaLook'].forEach((id) => { $(id).disabled = !ACC.admin; });
  $('accDevCard').hidden = !ACC.admin;
}
function accNotesRender() {
  const box = $('accNotes'); box.textContent = '';
  $('accNotesCard').hidden = !(ACC.on && ACC.notes.length);
  ACC.notes.slice(0, 15).forEach((n) => {
    const p = document.createElement('p'); p.className = 'meta';
    p.textContent = `${new Date(n.at).toLocaleDateString('es')} · 🎁 ${accNoteText(n)}`;
    box.appendChild(p);
  });
}
$('pAccounts').onclick = () => go('accounts');

async function accaRun(route, body, fmt) {
  $('accaMsg').textContent = '…';
  try { $('accaMsg').textContent = fmt(await ametra.acctCall(route, body)); accRefresh(); }
  catch (e) { $('accaMsg').textContent = cleanErr(e); }
}
function accaTarget() {
  const user = $('accaUser').value.trim();
  if (!user) { $('accaMsg').textContent = 'Escribe el nombre de la cuenta.'; return null; }
  return user;
}
function accaItemBody(user) {
  const v = $('accaItem').value;
  if (!v) { $('accaMsg').textContent = 'Elige un cosmético.'; return null; }
  if (v === '*') return { user, all: true };
  const [cat, id] = v.split(':');
  return { user, cat, id };
}
$('accaGive').onclick = () => {
  const u = accaTarget(); if (!u) return;
  const b = accaItemBody(u); if (!b) return;
  accaRun('admin/grant', b, (r) => (r.added ? `✔ ${r.user} recibe ${r.added} cosmético${r.added > 1 ? 's' : ''} (tiene ${r.owned}).` : `${r.user} ya lo tenía.`));
};
$('accaTake').onclick = () => {
  const u = accaTarget(); if (!u) return;
  const b = accaItemBody(u); if (!b) return;
  if (b.all && !confirm(`¿Quitarle a ${u} TODOS los cosméticos de pago?`)) return;
  accaRun('admin/revoke', b, (r) => (r.removed ? `✔ A ${r.user} se le quitan ${r.removed} (le quedan ${r.owned}).` : `${r.user} no tenía eso.`));
};
$('accaGemsBtn').onclick = () => {
  const u = accaTarget(); if (!u) return;
  const n = Math.trunc(Number($('accaGems').value));
  if (!n) { $('accaMsg').textContent = 'Pon una cantidad de gemas (puede ser negativa).'; return; }
  accaRun('admin/gems', { user: u, amount: n }, (r) => `✔ ${r.user} ahora tiene ${r.gems.toLocaleString('es')} gemas.`);
};
$('accaLook').onclick = () => {
  const u = accaTarget(); if (!u) return;
  accaRun('admin/lookup', { user: u }, (r) => {
    const names = r.owned.map((k) => { const [c, i] = k.split(':'); const it = findCos(c, i); return it ? it.name : k; });
    return `${r.user}${r.admin ? ' [developer]' : ''}: ${r.gems.toLocaleString('es')} gemas · ${r.owned.length} cosméticos${names.length ? ' → ' + names.slice(0, 12).join(', ') + (names.length > 12 ? '…' : '') : ''}`;
  });
};
