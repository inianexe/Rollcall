// A lightweight canvas interpretation of React Bits' Shape Grid for Rollcall's sign-in surface.
(() => {
  const dialog = document.querySelector('#web-account-dialog');
  const canvas = document.querySelector('#auth-shape-grid');
  if (!dialog || !canvas) return;
  const ctx = canvas.getContext('2d');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const pointer = { x: -1000, y: -1000 };
  let frame = 0, width = 0, height = 0;
  function resize() {
    const scale = Math.min(devicePixelRatio || 1, 1.5);
    width = innerWidth; height = innerHeight;
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
  }
  function draw(now) {
    if (!dialog.open) { frame = 0; return; }
    ctx.clearRect(0, 0, width, height);
    const spacing = width < 700 ? 38 : 48;
    const drift = reduced.matches ? 0 : Math.sin(now / 3400) * 3;
    for (let y = spacing / 2; y < height; y += spacing) {
      for (let x = spacing / 2; x < width; x += spacing) {
        const dx = x - pointer.x, dy = y - pointer.y;
        const near = Math.max(0, 1 - Math.hypot(dx, dy) / 160);
        const shape = (Math.round(x / spacing) + Math.round(y / spacing)) % 4;
        const size = 5 + near * 6;
        ctx.save();
        ctx.translate(x, y + drift * Math.sin(x / 130));
        ctx.rotate(shape === 2 ? Math.PI / 4 : 0);
        ctx.lineWidth = near ? 1.6 : 1;
        ctx.strokeStyle = near ? `rgba(255,68,0,${.35 + near * .65})` : 'rgba(18,18,18,.22)';
        ctx.fillStyle = `rgba(255,68,0,${near * .32})`;
        if (shape === 1) { ctx.beginPath(); ctx.arc(0, 0, size * .55, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); }
        else { ctx.beginPath(); ctx.rect(-size / 2, -size / 2, size, size); ctx.fill(); ctx.stroke(); }
        ctx.restore();
      }
    }
    if (!reduced.matches) frame = requestAnimationFrame(draw);
  }
  function start() { cancelAnimationFrame(frame); resize(); frame = requestAnimationFrame(draw); }
  dialog.addEventListener('pointermove', event => { pointer.x = event.clientX; pointer.y = event.clientY; if (reduced.matches) start(); });
  dialog.addEventListener('pointerleave', () => { pointer.x = -1000; pointer.y = -1000; if (reduced.matches) start(); });
  new MutationObserver(() => { if (dialog.open) start(); else cancelAnimationFrame(frame); }).observe(dialog, { attributes: true, attributeFilter: ['open'] });
  addEventListener('resize', () => { if (dialog.open) start(); });
})();
