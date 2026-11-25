// Inicio "Santuario": botón JUGAR central y novedades que rotan solas.
(() => {
  const play = $('orbPlay'), real = $('play');
  play.onclick = () => real.click();
  // El botón central copia el estado del botón lateral (cargando / deshabilitado).
  const sync = () => { play.disabled = real.disabled; play.lastChild.textContent = real.disabled ? ' INICIANDO…' : ' JUGAR'; };
  new MutationObserver(sync).observe(real, { attributes: true, childList: true, characterData: true, subtree: true });
  sync();

  const cards = [...document.querySelectorAll('.sa-feed .newscard')], dots = $('newsDots');
  let k = 0, timer = 0;
  cards.forEach((_, i) => { const d = document.createElement('i'); d.onclick = () => { show(i); restart(); }; dots.appendChild(d); });
  function show(i) { k = i; cards.forEach((c, j) => c.classList.toggle('on', j === k)); [...dots.children].forEach((d, j) => d.classList.toggle('on', j === k)); }
  function restart() { clearInterval(timer); timer = setInterval(() => { if (!document.hidden) show((k + 1) % cards.length); }, 6000); }
  show(0); restart();

  // Órbita: los datos giran alrededor del jugador en una elipse; los de atrás se ven más pequeños y tenues.
  const sanct = document.querySelector('.sanct'), orbs = [...document.querySelectorAll('.sa-orbit .orb')], t0 = performance.now();
  (function tick(now) {
    requestAnimationFrame(tick);
    if (document.hidden || !$('page-home').classList.contains('active') || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const w = sanct.clientWidth, h = sanct.clientHeight, cx = w / 2, cy = h * 0.55;
    const rx = Math.max(100, Math.min(240, w / 2 - 360 - 56)), ry = Math.min(95, h * 0.12);
    const a0 = (now - t0) / 1000 * 0.45;
    orbs.forEach((o, i) => {
      const a = a0 + i * 2 * Math.PI / orbs.length, s = Math.sin(a), depth = (s + 1) / 2;
      o.style.transform = `translate(${(cx + Math.cos(a) * rx - 56).toFixed(1)}px, ${(cy + s * ry - 28).toFixed(1)}px) scale(${(0.78 + 0.32 * depth).toFixed(3)})`;
      o.style.opacity = (0.5 + 0.5 * depth).toFixed(2);
      o.style.zIndex = s > 0 ? 4 : 2;
    });
  })(t0);
})();
