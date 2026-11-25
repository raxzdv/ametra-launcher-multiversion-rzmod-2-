// ---------- Desafíos semanales (pagan gemas) ----------
// Se cargan después de store.js. Cuentan el tiempo de juego a través de awardPlaytime(ms).
// Sin cuenta Ametra, las recompensas se dan en este PC. Con cuenta, el premio lo debe dar el servidor
// (hoy no está conectado), así que el botón Reclamar queda bloqueado para no perder gemas.

const CH_LIST = [
  { id: 'play120', title: 'Juega 120 minutos esta semana', desc: 'Tiempo real dentro del juego.', goal: 120, reward: 300, kind: 'minutes' },
  { id: 'days5', title: 'Juega en 5 días distintos', desc: 'Cada día cuenta una vez.', goal: 5, reward: 250, kind: 'days' },
  { id: 'streak3', title: 'Racha de 3 días del bono diario', desc: 'Reclama el bono diario sin saltarte días.', goal: 3, reward: 200, kind: 'streak' },
];

// Semana ISO (lunes como inicio), para reiniciar los desafíos cada semana
function chWeekKey(d = new Date()) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const wk = Math.ceil(((t - y0) / 86400000 + 1) / 7);
  return `${t.getUTCFullYear()}-W${String(wk).padStart(2, '0')}`;
}

function chState() {
  const wk = chWeekKey();
  if (!S.challenges || S.challenges.week !== wk) {
    S.challenges = { week: wk, minutes: 0, days: [], claimed: [] };
    save();
  }
  return S.challenges;
}

function chProgress(c) {
  const st = chState();
  if (c.kind === 'minutes') return Math.min(st.minutes, c.goal);
  if (c.kind === 'days') return Math.min(st.days.length, c.goal);
  if (c.kind === 'streak') return Math.min((S.daily && S.daily.streak) || 0, c.goal);
  return 0;
}

// Envolvemos awardPlaytime para contar el tiempo de juego de la semana
(function hookPlaytime() {
  if (typeof awardPlaytime !== 'function') return;
  const original = awardPlaytime;
  awardPlaytime = function (ms) {
    original(ms);
    const mins = Math.floor(ms / 60000);
    if (mins < 1) return;
    const st = chState();
    st.minutes += mins;
    const today = localDay();
    if (!st.days.includes(today)) st.days.push(today);
    save();
    if ($('page-challenges') && $('page-challenges').classList.contains('active')) renderChallenges();
  };
})();

function chClaim(c) {
  const st = chState();
  if (ACC.on) {
    ST.msg = 'Con cuenta Ametra, el premio lo entrega el servidor. Próximamente.';
    return;
  }
  if (st.claimed.includes(c.id) || chProgress(c) < c.goal) return;
  st.claimed.push(c.id);
  S.gems += c.reward;
  S.lastEarn = c.reward;
  save();
  if (typeof renderStore === 'function' && $('page-store').classList.contains('active')) renderStore();
  renderChallenges();
}

function renderChallenges() {
  const host = $('chList');
  if (!host) return;
  const st = chState();
  const weekLeft = (() => {
    const now = new Date();
    const end = new Date(now);
    end.setDate(now.getDate() + ((7 - (now.getDay() || 7)) + 1));
    end.setHours(0, 0, 0, 0);
    const h = Math.max(0, Math.floor((end - now) / 3600000));
    return `${Math.floor(h / 24)} d ${h % 24} h`;
  })();
  $('chReset').textContent = `Se reinicia en ${weekLeft}`;
  host.innerHTML = '';
  CH_LIST.forEach((c) => {
    const prog = chProgress(c);
    const done = prog >= c.goal;
    const claimed = st.claimed.includes(c.id);
    const pct = Math.round((prog / c.goal) * 100);

    const card = document.createElement('div');
    card.className = 'ch-card' + (claimed ? ' claimed' : '');
    card.innerHTML = `
      <div class="ch-info">
        <b></b><span></span>
        <div class="ch-bar"><i style="width:${pct}%"></i></div>
        <small>${prog} / ${c.goal}</small>
      </div>
      <div class="ch-side"></div>`;
    card.querySelector('b').textContent = c.title;
    card.querySelector('.ch-info > span').textContent = c.desc;

    const side = card.querySelector('.ch-side');
    const reward = document.createElement('div');
    reward.className = 'ch-reward';
    reward.textContent = `+${c.reward}`;
    const gem = document.createElement('i');
    gem.className = 'gem';
    reward.prepend(gem);
    side.appendChild(reward);

    const btn = document.createElement('button');
    btn.className = 'dbtn ' + (done && !claimed ? 'gold' : '');
    btn.disabled = !done || claimed || ACC.on;
    btn.textContent = claimed ? 'Reclamado' : done ? 'Reclamar' : 'En curso';
    btn.onclick = () => chClaim(c);
    side.appendChild(btn);

    host.appendChild(card);
  });
}
