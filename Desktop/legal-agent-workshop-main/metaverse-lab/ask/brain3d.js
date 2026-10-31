/* Ask-page 3D brain: atlas-style viewer with a control handle.
 * Drag to rotate, wheel to zoom, double-click to reset. Brightness per
 * region = treatment firing sampled live during the run (setValues in). */
import * as THREE from 'three';

const STAGE_REGIONS = {
  'antennal lobe PNs': ['AL'],
  'lateral horn': ['LH'],
  'mushroom body': ['MB_CA', 'MB_ML', 'MB_PED', 'MB_VL'],
  'central complex': ['EB', 'FB', 'PB', 'NO', 'LAL'],
  'optic lobes': ['ME', 'LO', 'LOP', 'AME', 'LA'],
};
let somaGeo = null;
async function f32(path) {
  const r = await fetch(path);
  if (!r.ok) throw new Error('missing ' + path);
  return new Float32Array(await r.arrayBuffer());
}
async function sharedSomas() {
  if (somaGeo) return somaGeo;
  const somas = await f32('../library/points/somas.bin');
  for (let i = 1; i < somas.length; i += 3) somas[i] = -somas[i];
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(somas, 3));
  somaGeo = g;
  return g;
}

export async function brain3D(canvas, values, opts = {}) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(26, 1, 0.1, 100);
  const st = { theta: 0.75, phi: 1.45, dist: 6.4, home: { theta: 0.75, phi: 1.45, dist: 6.4 },
    idle: 0, lastT: performance.now(), yawQ: 0, vel: 0 };
  const place = () => {
    const sp = Math.sin(st.phi);
    camera.position.set(st.dist * sp * Math.sin(st.theta), st.dist * Math.cos(st.phi), st.dist * sp * Math.cos(st.theta));
    camera.lookAt(0, 0, 0);
  };
  const soma = new THREE.Points(await sharedSomas(),
    new THREE.PointsMaterial({ size: 0.012, color: opts.soma || 0x8a8fa8, transparent: true, opacity: 0.5, sizeAttenuation: true, depthWrite: false }));
  soma.frustumCulled = false;
  scene.add(soma);
  const mats = [];
  const clouds = [];
  for (const [stage, regs] of Object.entries(STAGE_REGIONS)) {
    for (const r of regs) {
      try {
        const pts = await f32(`../library/points/${r}.bin`);
        for (let i = 1; i < pts.length; i += 3) pts[i] = -pts[i];
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.BufferAttribute(pts, 3));
        const m = new THREE.Points(g, new THREE.PointsMaterial({ size: 0.02, color: 0x2ee6c8, transparent: true, opacity: 0.04, sizeAttenuation: true, depthWrite: false }));
        m.frustumCulled = false;
        m.renderOrder = 2;
        m.userData.stage = stage;
        scene.add(m);
        mats.push([stage, m.material]);
        clouds.push(m);
      } catch { /* region file absent: bars still carry this stage */ }
    }
  }
  const setValues = vs => {
    const mx = Math.max(1e-9, ...Object.values(vs || {}));
    for (const [stage, mat] of mats) mat.opacity = 0.04 + 0.9 * Math.min(1, ((vs || {})[stage] || 0) / mx);
  };
  setValues(values);
  const el = opts.stage || canvas;
  const frame = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    if (opts.stage) {
      const r = opts.stage.getBoundingClientRect(), c = canvas.getBoundingClientRect();
      const cx = r.left - c.left + r.width / 2, cy = r.top - c.top + r.height * 0.40;
      camera.setViewOffset(w, h, w / 2 - cx, h / 2 - cy, w, h);
    } else {
      camera.clearViewOffset();
    }
    camera.updateProjectionMatrix();
  };
  new ResizeObserver(frame).observe(canvas);
  if (opts.stage) new ResizeObserver(frame).observe(opts.stage);
  frame();
  const hintGone = () => document.getElementById('stageHint')?.classList.add('gone');
  let drag = null;
  el.addEventListener('pointerdown', e => {
    if (e.target.closest && e.target.closest('button')) return;
    drag = { x: e.clientX, y: e.clientY }; el.setPointerCapture(e.pointerId); st.idle = 0; hintGone();
  });
  el.addEventListener('pointermove', e => {
    if (!drag) return;
    const k = 0.005;
    const dx = (e.clientX - drag.x) * k;
    st.theta -= dx;
    st.phi = Math.min(Math.PI - 0.15, Math.max(0.15, st.phi - (e.clientY - drag.y) * k));
    st.vel = -dx * 60;
    drag = { x: e.clientX, y: e.clientY };
    st.idle = 0;
  });
  const up = () => { drag = null; };
  el.addEventListener('pointerup', up);
  el.addEventListener('pointercancel', up);
  const zoomBy = f => {
    st.dist = Math.min(9.5, Math.max(1.5, st.dist / f));
    st.idle = 0;
    hintGone();
    return st.dist;
  };
  el.addEventListener('wheel', e => { e.preventDefault(); zoomBy(Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0016))); }, { passive: false });
  const reset = () => Object.assign(st, st.home, { idle: 0, yawQ: 0, vel: 0 });
  el.addEventListener('dblclick', reset);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let frames = 0;
  const loop = t => {
    const dt = Math.min(0.05, (t - st.lastT) / 1000);
    st.lastT = t;
    st.idle += dt;
    if (opts.stage && (++frames % 20 === 0)) frame();
    if (st.yawQ) { const s = st.yawQ * (1 - Math.exp(-dt * 4.2)); st.theta += s; st.yawQ -= s; }
    if (!drag && st.vel) { st.theta += st.vel * dt; st.vel *= Math.exp(-dt * 4.5); }
    if (st.idle > 1.8 && !reduced && !drag) st.theta += dt * 0.12 * Math.min(1, (st.idle - 1.8) / 1.5);
    place();
    renderer.render(scene, camera);
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
  const ray = new THREE.Raycaster();
  ray.params.Points.threshold = 0.04;
  const hover = (cx, cy) => {
    const r = canvas.getBoundingClientRect();
    ray.setFromCamera(new THREE.Vector2(((cx - r.left) / r.width) * 2 - 1, -((cy - r.top) / r.height) * 2 + 1), camera);
    const hits = ray.intersectObjects(clouds, false);
    return hits.length ? hits[0].object.userData.stage : null;
  };
  return {
    setValues,
    zoomBy,
    zoom: () => st.home.dist / st.dist,
    queueYaw: r => { st.yawQ += r; st.idle = 0; },
    reset,
    setSoma: hex => soma.material.color.setHex(hex),
    hover,
  };
}
