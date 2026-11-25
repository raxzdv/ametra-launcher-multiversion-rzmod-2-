// Fondos animados del launcher: presets dibujados en canvas + tus GIF / imágenes / vídeos.
// Se guarda en S.lbg ('none' | 'aurora' | 'stars' | 'waves' | 'net' | 'file:<nombre>') y S.lbgDim (0-85).
(() => {
  const PRESETS = [['aurora', 'Aurora'], ['stars', 'Estrellas'], ['waves', 'Ondas'], ['net', 'Partículas'], ['none', 'Sin fondo animado']];
  const cv = $('bgCanvas'), ctx = cv.getContext('2d'), vid = $('bgVideo'), img = $('bgImg'), box = $('bgfx');
  let raf = 0, last = 0, t = 0, kind = 'none', url = '', stars = [], dots = [];
  const accent = () => (getComputedStyle(document.documentElement).getPropertyValue('--ac') || '#8a3be8').trim();
  const accent2 = () => (getComputedStyle(document.documentElement).getPropertyValue('--ac2') || '#c96cff').trim();
  const rgb = (hex) => { const h = hex.replace('#', ''); const n = parseInt(h.length === 3 ? h.replace(/./g, '$&$&') : h.slice(0, 6), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };

  function size() {
    const d = Math.min(devicePixelRatio || 1, 1.5); // tope: no gastar GPU de más
    cv.width = Math.ceil(innerWidth * d * 0.5); cv.height = Math.ceil(innerHeight * d * 0.5); // se dibuja a media resolución y se escala (suave y barato)
    stars = Array.from({ length: 140 }, () => ({ x: Math.random() * 2 - 1, y: Math.random() * 2 - 1, z: Math.random() }));
    dots = Array.from({ length: 55 }, () => ({ x: Math.random() * cv.width, y: Math.random() * cv.height, vx: (Math.random() - .5) * .35, vy: (Math.random() - .5) * .35 }));
  }

  const draw = {
    aurora(dt) {
      const w = cv.width, h = cv.height, [r, g, b] = rgb(accent()), c2 = rgb(accent2());
      ctx.fillStyle = '#050505'; ctx.fillRect(0, 0, w, h);
      ctx.globalCompositeOperation = 'lighter';
      for (let i = 0; i < 4; i++) {
        const x = w * (.5 + .42 * Math.sin(t * .00022 * (i + 1) + i * 2)), y = h * (.5 + .38 * Math.cos(t * .00018 * (i + 2) + i));
        const rad = Math.max(w, h) * (.38 + .08 * Math.sin(t * .0004 + i));
        const gr = ctx.createRadialGradient(x, y, 0, x, y, rad);
        const k = i % 2 ? c2 : [r, g, b];
        gr.addColorStop(0, `rgba(${k[0] | 0},${k[1] | 0},${k[2] | 0},.38)`); gr.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = gr; ctx.fillRect(0, 0, w, h);
      }
      ctx.globalCompositeOperation = 'source-over';
    },
    stars(dt) {
      const w = cv.width, h = cv.height, [r, g, b] = rgb(accent());
      ctx.fillStyle = 'rgba(5,5,5,.35)'; ctx.fillRect(0, 0, w, h);
      for (const s of stars) {
        s.z -= dt * .00022; if (s.z <= .02) { s.x = Math.random() * 2 - 1; s.y = Math.random() * 2 - 1; s.z = 1; }
        const px = w / 2 + s.x / s.z * w * .22, py = h / 2 + s.y / s.z * h * .22, q = 1 - s.z;
        if (px < 0 || px > w || py < 0 || py > h) { s.z = 1; continue; }
        ctx.fillStyle = `rgba(${r + (255 - r) * q | 0},${g + (255 - g) * q | 0},${b + (255 - b) * q | 0},${.25 + q * .75})`;
        ctx.fillRect(px, py, 1 + q * 2.2, 1 + q * 2.2);
      }
    },
    waves(dt) {
      const w = cv.width, h = cv.height, [r, g, b] = rgb(accent());
      ctx.fillStyle = '#050505'; ctx.fillRect(0, 0, w, h);
      for (let i = 0; i < 5; i++) {
        ctx.beginPath(); ctx.moveTo(0, h);
        for (let x = 0; x <= w; x += 8) ctx.lineTo(x, h * (.55 + i * .07) + Math.sin(x * .008 + t * .0007 * (1 + i * .25) + i) * h * .05 + Math.sin(x * .017 - t * .0005 + i * 3) * h * .02);
        ctx.lineTo(w, h); ctx.closePath();
        ctx.fillStyle = `rgba(${r},${g},${b},${.07 + i * .035})`; ctx.fill();
      }
    },
    net(dt) {
      const w = cv.width, h = cv.height, [r, g, b] = rgb(accent());
      ctx.fillStyle = '#050505'; ctx.fillRect(0, 0, w, h);
      for (const p of dots) {
        p.x += p.vx * dt * .06; p.y += p.vy * dt * .06;
        if (p.x < 0 || p.x > w) p.vx *= -1; if (p.y < 0 || p.y > h) p.vy *= -1;
      }
      const L = Math.min(w, h) * .2;
      for (let i = 0; i < dots.length; i++) {
        const a = dots[i];
        for (let j = i + 1; j < dots.length; j++) {
          const c = dots[j], d = Math.hypot(a.x - c.x, a.y - c.y);
          if (d < L) { ctx.strokeStyle = `rgba(${r},${g},${b},${(1 - d / L) * .35})`; ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(c.x, c.y); ctx.stroke(); }
        }
        ctx.fillStyle = `rgba(${r},${g},${b},.8)`; ctx.fillRect(a.x - 1, a.y - 1, 2.5, 2.5);
      }
    }
  };

  function loop(now) {
    raf = requestAnimationFrame(loop);
    if (document.hidden) return;
    const dt = Math.min(now - last, 100); if (dt < 33) return; // ~30 fps
    last = now; t += dt;
    draw[kind] && draw[kind](dt);
  }
  function stop() { cancelAnimationFrame(raf); raf = 0; }

  function clearMedia() {
    vid.pause(); vid.removeAttribute('src'); vid.hidden = true; vid.load();
    img.hidden = true; img.removeAttribute('src');
    if (url) { URL.revokeObjectURL(url); url = ''; }
  }
  const dim = () => { const v = Math.max(0, Math.min(85, Number(S.lbgDim ?? 45))); box.style.setProperty('--dim', (v / 100).toFixed(2)); $('lbgDimV').textContent = v + '%'; };

  async function apply() {
    stop(); clearMedia(); dim();
    const sel = S.lbg || 'aurora';
    document.body.classList.toggle('home-bg', S.homeBg !== false && sel !== 'none');
    cv.hidden = true; document.body.classList.toggle('has-bg', sel !== 'none');
    $('lbgDel').hidden = !sel.startsWith('file:');
    if (sel === 'none') { box.hidden = true; return; }
    box.hidden = false;
    if (draw[sel]) {
      kind = sel; cv.hidden = false; size(); last = performance.now(); raf = requestAnimationFrame(loop); return;
    }
    if (sel.startsWith('file:')) {
      const r = await ametra.lbgRead(sel.slice(5)).catch(() => null);
      if (!r) { S.lbg = 'aurora'; save(); return apply(); } // el archivo ya no existe
      url = URL.createObjectURL(new Blob([r.data], { type: r.mime }));
      if (r.mime.startsWith('video/')) { vid.src = url; vid.hidden = false; vid.muted = true; vid.play().catch(() => {}); }
      else { img.src = url; img.hidden = false; }
    }
  }

  async function fillSelect() {
    const files = await ametra.lbgList().catch(() => []);
    const sel = $('lbgSel'); sel.innerHTML = '';
    const add = (v, label, grp) => { const o = document.createElement('option'); o.value = v; o.textContent = label; (grp || sel).appendChild(o); };
    const g1 = document.createElement('optgroup'); g1.label = 'Integrados'; sel.appendChild(g1);
    PRESETS.forEach(([v, n]) => add(v, n, g1));
    if (files.length) { const g2 = document.createElement('optgroup'); g2.label = 'Tus fondos'; sel.appendChild(g2); files.forEach((f) => add('file:' + f, f, g2)); }
    sel.value = [...sel.options].some((o) => o.value === (S.lbg || 'aurora')) ? (S.lbg || 'aurora') : 'aurora';
  }

  $('lbgSel').onchange = () => { S.lbg = $('lbgSel').value; save(); apply(); };
  $('lbgDim').oninput = () => { S.lbgDim = Number($('lbgDim').value); dim(); };
  $('lbgDim').onchange = () => save();
  $('lbgFolder').onclick = () => ametra.lbgOpen();
  $('lbgAdd').onclick = async () => {
    const added = await ametra.lbgAdd();
    if (!added.length) return;
    S.lbg = 'file:' + added[0]; save(); await fillSelect(); apply();
  };
  $('lbgDel').onclick = async () => {
    const cur = S.lbg || '';
    if (!cur.startsWith('file:') || !confirm('¿Quitar este fondo de la carpeta?')) return;
    await ametra.lbgRemove(cur.slice(5));
    S.lbg = 'aurora'; save(); await fillSelect(); apply();
  };
  addEventListener('resize', () => { if (raf) size(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden && vid.src && !vid.hidden) vid.play().catch(() => {}); });

  $('lbgHome').checked = S.homeBg !== false;
  $('lbgHome').onchange = () => { S.homeBg = $('lbgHome').checked; save(); apply(); };
  // Botones sobre el Inicio: cambiar de fondo con un clic o añadir uno tuyo.
  $('homeBgNext').onclick = () => {
    const opts = [...$('lbgSel').options].map((o) => o.value), i = opts.indexOf(S.lbg || 'aurora');
    S.lbg = opts[(i + 1) % opts.length]; S.homeBg = S.lbg !== 'none' ? true : S.homeBg; $('lbgHome').checked = S.homeBg !== false;
    save(); $('lbgSel').value = S.lbg; apply();
  };
  $('homeBgAdd').onclick = () => $('lbgAdd').click();
  $('lbgDim').value = S.lbgDim ?? 45;
  fillSelect().then(apply);
})();
