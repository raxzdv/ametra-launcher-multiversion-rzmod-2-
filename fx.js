// Efectos del tema Prisma: el foco de las tarjetas sigue al cursor y la aurora se mueve un poco con el ratón.
(() => {
  const SEL = '.card,.newscard,.inst,.add,.mcard,.srv,.cos,.prof,.pack,.irow,.deal';
  const root = document.documentElement;
  let raf = 0, ev = null;
  document.addEventListener('pointermove', (e) => {
    ev = e;
    if (raf) return;
    raf = requestAnimationFrame(() => {
      raf = 0;
      const t = ev.target.closest && ev.target.closest(SEL);
      if (t) {
        const r = t.getBoundingClientRect();
        t.style.setProperty('--mx', ev.clientX - r.left + 'px');
        t.style.setProperty('--my', ev.clientY - r.top + 'px');
      }
      root.style.setProperty('--px', (ev.clientX / innerWidth - 0.5).toFixed(3));
      root.style.setProperty('--py', (ev.clientY / innerHeight - 0.5).toFixed(3));
    });
  }, { passive: true });
})();
