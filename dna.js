// ADN de skin (exclusivo de Ametra): el launcher, los fondos animados y el HUD del juego toman los colores de TU skin.
// Lee los píxeles de la skin, saca su paleta (3 colores) y los aplica como acento. S.dnaOn (por defecto true) lo activa.
(() => {
  const root = document.documentElement;
  const hsl2hex = (h, s, l) => {
    h = ((h % 360) + 360) % 360; s = Math.min(1, Math.max(0, s)); l = Math.min(1, Math.max(0, l));
    const k = (n) => (n + h / 30) % 12, a = s * Math.min(l, 1 - l);
    const f = (n) => Math.round(255 * (l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))));
    return '#' + [f(0), f(8), f(4)].map((v) => v.toString(16).padStart(2, '0')).join('');
  };
  const rgb2hsl = (r, g, b) => {
    r /= 255; g /= 255; b /= 255;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn, l = (mx + mn) / 2;
    if (!d) return [0, 0, l];
    const s = d / (1 - Math.abs(2 * l - 1));
    const h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [(h * 60 + 360) % 360, s, l];
  };

  function palette(img) {
    const c = document.createElement('canvas'); c.width = 64; c.height = 64;
    const x = c.getContext('2d', { willReadFrequently: true }); x.imageSmoothingEnabled = false;
    x.drawImage(img, 0, 0, 64, img.naturalHeight < 64 ? 32 : 64);
    const d = x.getImageData(0, 0, 64, 64).data;
    const B = 24, score = new Float64Array(B), sat = new Float64Array(B), lig = new Float64Array(B), cnt = new Float64Array(B);
    for (let i = 0; i < d.length; i += 4) {
      if (d[i + 3] < 200) continue;
      const [h, s, l] = rgb2hsl(d[i], d[i + 1], d[i + 2]);
      if (s < .28 || l < .12 || l > .92) continue;              // grises, negros y blancos no son "color"
      const b = Math.floor(h / (360 / B)) % B, w = s * s * (1 - Math.abs(l - .5));
      score[b] += w; sat[b] += s * w; lig[b] += l * w; cnt[b] += w;
    }
    const order = [...score.keys()].sort((a, b) => score[b] - score[a]).filter((b) => score[b] > 0);
    if (!order.length || score[order[0]] < 1.5) return null;    // skin sin colores vivos: no se toca el tema
    const picked = [];
    for (const b of order) {
      const h = (b + .5) * (360 / B);
      if (picked.every((p) => { const dh = Math.abs(p.h - h); return Math.min(dh, 360 - dh) >= 40; })) picked.push({ h, s: sat[b] / cnt[b], l: lig[b] / cnt[b] });
      if (picked.length === 3) break;
    }
    while (picked.length < 3) { const p = picked[picked.length - 1]; picked.push({ h: p.h + 38, s: p.s, l: p.l }); } // se completa con tonos vecinos
    // Se ajustan para que se vean bien sobre fondo oscuro (ni apagados ni quemados).
    return picked.map((p) => hsl2hex(p.h, Math.max(.6, Math.min(.95, p.s)), Math.max(.5, Math.min(.66, p.l + .12))));
  }

  function paint(p) {
    const sw = $('dnaSwatches'); if (!sw) return;
    sw.innerHTML = '';
    (p || []).forEach((c) => { const i = document.createElement('i'); i.style.background = c; i.title = c.toUpperCase(); sw.appendChild(i); });
    $('dnaState').textContent = p ? (S.dnaOn !== false ? 'Activo: el launcher y el HUD usan los colores de tu skin.' : 'Paleta detectada (desactivado).') : 'Tu skin no tiene colores vivos: se mantiene tu color de acento.';
  }
  function apply(p) {
    if (!p) return;
    S.dna = p;
    if (S.dnaOn !== false) {
      root.style.setProperty('--ac2', p[1]); root.style.setProperty('--ac3', p[2]);
      if (S.accent !== p[0]) { S.accent = p[0]; applyTheme(); writeConfig(['accent']); } // el mod toma el mismo color para el HUD
      $('accent').value = S.accent; $('accentHex').textContent = S.accent.toUpperCase();
    }
    save(); paint(p);
  }
  function fromSrc(src) {
    const img = new Image();
    img.onload = () => { try { apply(palette(img)); } catch { /* skin ilegible: se ignora */ } };
    img.src = src;
  }

  // Se engancha a setSkin: solo con la skin real de la cuenta (no con la vista previa ni la skin por defecto).
  const orig = setSkin;
  setSkin = function (src, slim, real, label) {
    orig.apply(this, arguments);
    if (real && label !== 'Vista previa') fromSrc(src);
  };

  $('dnaOn').checked = S.dnaOn !== false;
  $('dnaOn').onchange = () => {
    S.dnaOn = $('dnaOn').checked; save();
    if (S.dnaOn && S.dna) apply(S.dna); else { root.style.removeProperty('--ac2'); root.style.removeProperty('--ac3'); paint(S.dna); }
  };
  $('dnaNow').onclick = () => { S.dnaOn = true; $('dnaOn').checked = true; const s = skinState.src; if (s && skinImg) fromSrc(s); else $('dnaState').textContent = 'Primero elige o inicia sesión con una skin.'; };
  // Si cambias el color a mano, el ADN se desactiva para respetar tu elección.
  $('accent').addEventListener('change', () => { S.dnaOn = false; $('dnaOn').checked = false; save(); paint(S.dna); });

  if (S.dna) { paint(S.dna); if (S.dnaOn !== false) { root.style.setProperty('--ac2', S.dna[1]); root.style.setProperty('--ac3', S.dna[2]); } }
  else paint(null), $('dnaState').textContent = 'Se calcula al cargar tu skin.';
  // La skin del arranque se cargó antes de enganchar setSkin: se recalcula una vez cuando esté lista.
  setTimeout(() => { if (skinImg && skinState.src && S.dnaOn !== false && !S.dna) fromSrc(skinState.src); }, 900);
})();
