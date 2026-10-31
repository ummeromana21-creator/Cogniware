/* Watch the run: replay a recorded experiment as brain + raster + bars. */
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/addons/controls/OrbitControls.js';

const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
// sim group -> atlas region bins (FlyWire frame). Unmapped stages show in bars only.
const STAGE_REGIONS = {
  'antennal lobe PNs': ['AL'],
  'lateral horn': ['LH'],
  'mushroom body': ['MB_CA', 'MB_ML', 'MB_PED', 'MB_VL'],
  'central complex': ['EB', 'FB', 'PB', 'NO', 'LAL'],
  'optic lobes': ['ME', 'LO', 'LOP', 'AME', 'LA'],
};
const P = { rec: null, run: 0, step: 0, playing: false, speed: 20, raster: null, gmax: 0.001,
  clouds: {}, renderer: null, scene: null, camera: null, controls: null,raf: 0, lastT: 0,
  recorder: null, recChunks: [], ws: null, idSeq: 0, pending: {} };

function setStatus(live, text) {
  const el = $('status');
  el.classList.toggle('live', live);
  el.textContent = (live ? '● ' : '○ ') + text;
}
function fail(text) { $('err').textContent = text; }
const cssVar = n => getComputedStyle(document.documentElement).getPropertyValue(n).trim() || '#888';
const shellName = () => document.querySelector('#shellBar .shell-name');
const shellSub = () => document.querySelector('#shellBar .shell-sub');
function applyMode(m) {
  const dusk = (m || Feather.mode()) === 'dusk';
  if (P.scene) P.scene.background.setHex(dusk ? 0x14170f : 0xf6f1e7);
  if (P.somaMat) P.somaMat.color.setHex(dusk ? 0x4a5578 : 0x8a8fa8);
  for (const clouds of Object.values(P.clouds)) for (const c of clouds) c.material.color.setHex(dusk ? 0x2ee6c8 : 0x0f766e);
  if (P.rec) { P.raster = decodeRun(P.rec.rec_runs[P.run], dusk); drawFrame(); }
}

/* ---- lab link ---- */
function lab() {
  return new Promise(resolve => {
    setStatus(false, 'connecting…');
    let ws;
    try { ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.hostname || 'localhost'}:8765`); } catch { return resolve(false); }
    P.ws = ws;
    const to = setTimeout(() => resolve(false), 8000);
    ws.onopen = () => { clearTimeout(to); setStatus(true, 'live'); resolve(true); };
    ws.onmessage = ev => {
      let m;
      try { m = JSON.parse(ev.data); } catch { return; }
      if ((m.type === 'ack' || m.type === 'experiment_done') && m.id && P.pending[m.id]) {
        const cb = P.pending[m.id]; delete P.pending[m.id]; cb(m);
      }
    };
    ws.onclose = () => setStatus(false, 'offline — start the lab');
    ws.onerror = () => { try { ws.close(); } catch {} };
  });
}
function askp(obj, timeout = 15000) {
  return new Promise(resolve => {
    if (!P.ws) return resolve(null);
    const id = 'w' + (++P.idSeq);
    P.ws.send(JSON.stringify({ ...obj, id }));
    let done = false;
    P.pending[id] = m => { if (!done) { done = true; resolve(m); } };
    setTimeout(() => { if (!done) { done = true; delete P.pending[id]; resolve(null); } }, timeout);
  });
}

/* ---- record decode ---- */
function b64bytes(b64) {
  const bin = atob(b64), out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
function decodeRun(rr, dusk) {
  const rec = rr.rec, steps = rec.steps, n = rec.n_sample;
  const raw = b64bytes(rec.spikes_b64), bps = Math.floor(raw.length / steps);
  const H = 180, img = new ImageData(400, H);
  const BG = dusk ? [27, 31, 21] : [246, 241, 231];
  const FG = dusk ? [46, 230, 200] : [16, 119, 116];
  const stride = Math.max(1, Math.floor(n / H));
  for (let s = 0; s < steps; s++) {
    for (let y = 0; y < H; y++) {
      const i = y * stride;
      const bit = (raw[s * bps + (i >> 3)] >> (i & 7)) & 1;
      const p = (y * 400 + s) * 4;
      const c = bit ? FG : BG;
      img.data[p] = c[0]; img.data[p + 1] = c[1]; img.data[p + 2] = c[2]; img.data[p + 3] = 255;
    }
  }
  return { steps, img, groups: rec.groups || {}, window: rec.window || 25 };
}
function groupAt(g, step) {
  const vs = P.raster.groups[g] || [];
  if (!vs.length) return 0;
  return vs[Math.min(vs.length - 1, Math.floor(step / P.raster.window))];
}

/* ---- 3D brain ---- */
async function f32(path) {
  const r = await fetch(path);
  if (!r.ok) throw new Error('missing ' + path);
  return new Float32Array(await r.arrayBuffer());
}
async function initGL() {
  const canvas = $('gl');
  try {
    P.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  } catch (e) {
    $('glerr').hidden = false;
    $('glerr').textContent = 'WebGL unavailable on this browser — raster, bars and numbers below still work.';
    return false;
  }
  P.renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  P.scene = new THREE.Scene();
  P.scene.background = new THREE.Color(Feather.mode() === 'dusk' ? 0x14170f : 0xf6f1e7);
  P.camera = new THREE.PerspectiveCamera(40, 1, 0.1, 100);
  P.camera.position.set(0, 0.25, 1.9);
  P.controls = new OrbitControls(P.camera, canvas);
  P.controls.enableDamping = true;
  P.controls.autoRotate = !matchMedia('(prefers-reduced-motion: reduce)').matches;
  P.controls.autoRotateSpeed = 0.8;
  const somas = await f32('../../library/points/somas.bin');
  for (let i = 1; i < somas.length; i += 3) somas[i] = -somas[i];
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(somas, 3));
  const soma = new THREE.Points(g, new THREE.PointsMaterial({ size: 0.012, color: Feather.mode() === 'dusk' ? 0x4a5578 : 0x8a8fa8, transparent: true, opacity: 0.55, sizeAttenuation: true, depthWrite: false }));
  P.somaMat = soma.material;
  soma.frustumCulled = false;
  P.scene.add(soma);
  window.__watch = { somas: somas.length / 3 };
  for (const [stage, regs] of Object.entries(STAGE_REGIONS)) {
    const clouds = [];
    for (const r of regs) {
      try {
        const pts = await f32(`../../library/points/${r}.bin`);
        for (let i = 1; i < pts.length; i += 3) pts[i] = -pts[i];
        const gg = new THREE.BufferGeometry();
        gg.setAttribute('position', new THREE.BufferAttribute(pts, 3));
        const m = new THREE.Points(gg, new THREE.PointsMaterial({ size: 0.02, color: Feather.mode() === 'dusk' ? 0x2ee6c8 : 0x0f766e, transparent: true, opacity: 0, sizeAttenuation: true, depthWrite: false }));
        m.frustumCulled = false;
        m.renderOrder = 2;
        P.scene.add(m);
        clouds.push(m);
      } catch { /* region file missing: bars still carry this stage */ }
    }
    P.clouds[stage] = clouds;
  }
  const fit = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    P.renderer.setSize(w, h, false);
    P.camera.aspect = w / h;
    P.camera.updateProjectionMatrix();
  };
  new ResizeObserver(fit).observe(canvas);
  fit();
  return true;
}
function drawFrame() {
  const s = Math.floor(P.step);
  for (const [stage, clouds] of Object.entries(P.clouds)) {
    const v = groupAt(stage, s) / P.gmax;
    for (const m of clouds) m.material.opacity = (0.04 + 0.9 * Math.min(1, v)).toFixed(3);
  }
  // bars
  const bars = $('bars');
  if (!bars.children.length) {
    bars.innerHTML = Object.keys(P.raster.groups).map(g =>
      `<div class="gbar"><span class="nm">${esc(g)}</span><span class="bar"><i data-g="${esc(g)}"></i><u data-m="${esc(g)}"></u></span></div>`).join('');
    for (const g of Object.keys(P.raster.groups)) {
      const mx = Math.max(...P.raster.groups[g], 1e-9) / P.gmax;
      bars.querySelector(`u[data-m="${CSS.escape(g)}"]`).style.left = `${(100 * Math.min(1, mx)).toFixed(1)}%`;
    }
  }
  for (const g of Object.keys(P.raster.groups)) {
    bars.querySelector(`i[data-g="${CSS.escape(g)}"]`).style.width = `${(100 * Math.min(1, groupAt(g, s) / P.gmax)).toFixed(1)}%`;
  }
  // raster + clock
  const rc = $('raster'), ctx = rc.getContext('2d');
  ctx.putImageData(P.raster.img, 0, 0, 0, 0, 400, 180);
  ctx.fillStyle = cssVar('--sage');
  ctx.fillRect((s / P.raster.steps) * 400, 0, 2, 180);
  $('clock').textContent = `step ${s} / ${P.raster.steps}`;
  $('scrub').value = s;
}
function loop(t) {
  const dt = Math.min(0.1, (t - P.lastT) / 1000 || 0);
  P.lastT = t;
  if (P.playing) {
    P.step += P.speed * dt;
    if (P.step >= P.raster.steps) { P.step = P.raster.steps - 1; setPlaying(false); stopRec(); }
    drawFrame();
  }
  if (P.controls) P.controls.update();
  if (P.renderer) P.renderer.render(P.scene, P.camera);
  P.raf = requestAnimationFrame(loop);
}
function setPlaying(on) {
  P.playing = on;
  $('playBtn').textContent = on ? 'Pause' : 'Play';
}

/* ---- runs ---- */
function loadRun(i) {
  P.run = i;
  const rr = P.rec.rec_runs[i];
  P.raster = decodeRun(rr, Feather.mode() === 'dusk');
  P.step = 0;
  $('bars').innerHTML = '';
  $('scrub').max = P.raster.steps - 1;
  const pf = ((P.rec.details || {}).per_fly || {});
  const fl = ((P.rec.flies || {})[rr.slot] || {}).label || rr.slot;
  const d = ((pf[fl] || [])[0] || {});
  $('scalars').innerHTML = `recorded brain: <b>${esc(fl)}</b> (${esc(rr.slot)}, ${esc(rr.condition)}, repeat ${(rr.repeat || 0) + 1}). ` +
    `control ${(+d.control_mean || 0).toFixed(4)} vs treatment ${(+d.treatment_mean || 0).toFixed(4)} ${esc(P.rec.card.measure)}. Verdict ${esc(P.rec.verdict)}. ` +
    `Control runs are not spike-recorded — control appears as totals only.`;
  drawFrame();
}
function setRecord(rec) {
  P.rec = rec;
  if (!rec.rec_runs || !rec.rec_runs.length) { fail('This record has no spike recordings (older run?).'); return false; }
  let mx = 1e-9;
  for (const rr of rec.rec_runs) for (const vs of Object.values((rr.rec.groups || {}))) for (const v of vs) mx = Math.max(mx, v);
  P.gmax = mx;
  shellName().textContent = 'Watch: ' + (rec.card.title || rec.id);
  shellSub().textContent = `${rec.rec_runs.length} recorded runs · ${rec.verdict} · ${rec.id}`;
  $('runSel').innerHTML = rec.rec_runs.map((rr, i) => `<option value="${i}">${esc(rr.condition)} · rep ${(rr.repeat || 0) + 1} · ${esc(rr.slot)}</option>`).join('');
  $('picker').hidden = true;
  $('player').hidden = false;
  loadRun(0);
  return true;
}

/* ---- transport + export ---- */
function stopRec() {
  if (P.recorder && P.recorder.state !== 'inactive') P.recorder.stop();
}
function toggleRec() {
  if (P.recorder && P.recorder.state !== 'inactive') { stopRec(); return; }
  if (!P.renderer || !window.MediaRecorder) { fail('Video recording needs MediaRecorder (Chrome/Edge).'); return; }
  P.recChunks = [];
  const stream = $('gl').captureStream(30);
  P.recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
  P.recorder.ondataavailable = e => { if (e.data.size) P.recChunks.push(e.data); };
  P.recorder.onstop = () => {
    $('recBtn').textContent = 'Record video';
    $('recBtn').classList.remove('rec');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob(P.recChunks, { type: 'video/webm' }));
    a.download = (P.rec.id || 'run') + '.webm';
    a.click();
  };
  P.recorder.start();
  $('recBtn').textContent = 'Stop + save';
  $('recBtn').classList.add('rec');
  P.step = 0;
  setPlaying(true);
}

/* ---- boot ---- */
async function boot() {
  Feather.topbar(document.getElementById('shellBar'), { brand: 'Watch the run', sub: 'Loading…', base: '../../', current: 'ask', extras: ['#status'] });
  Feather.init({ onLight: applyMode });
  const ok = await lab();
  const id = new URLSearchParams(location.search).get('id');
  if (!ok) { fail(id ? 'Lab offline — start the server, then reload.' : 'Lab offline — start the server to list runs.'); return; }
  if (id) {
    const m = await askp({ cmd: 'get_experiment', exp: id });
    if (!m || !m.ok) { fail('No such experiment: ' + id); return; }
    if (!await initGL()) return;
    if (setRecord(m.data.record) !== true) return;
  } else {
    const m = await askp({ cmd: 'list_notebook' });
    const list = (m && m.ok && m.data.notebook) || [];
    $('picker').hidden = false;
    shellSub().textContent = list.length ? 'Pick one — every run with spikes replays.' : 'Notebook is empty — run something first.';
    $('picklist').innerHTML = list.map(e => {
      const eid = typeof e === 'string' ? e : (e.id || e.exp || '');
      const ttl = typeof e === 'string' ? e : (e.title || e.card && e.card.title || eid);
      return `<button data-exp="${esc(eid)}">${esc(ttl)}</button>`;
    }).join('');
    $('picklist').querySelectorAll('button').forEach(b => b.onclick = () => location.assign('?id=' + encodeURIComponent(b.dataset.exp)));
    return;
  }
  $('runSel').onchange = () => loadRun(+$('runSel').value);
  $('speedSel').onchange = () => { P.speed = +$('speedSel').value; };
  $('playBtn').onclick = () => setPlaying(!P.playing);
  $('restartBtn').onclick = () => { P.step = 0; drawFrame(); };
  if ($('recBtn')) $('recBtn').onclick = toggleRec;
  $('scrub').oninput = () => { P.step = +$('scrub').value; drawFrame(); };
  P.lastT = performance.now();
  setPlaying(true);
  P.raf = requestAnimationFrame(loop);
}
boot();
