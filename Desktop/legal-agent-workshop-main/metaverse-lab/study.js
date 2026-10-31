import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

/* ================= 1. CONFIG + ERROR OVERLAY ================= */
const WS_SCHEME = location.protocol === 'https:' ? 'wss' : 'ws';
const WS_URL = `${WS_SCHEME}://${location.hostname || 'localhost'}:8765`;
const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;
const errBox = document.getElementById('err');
function showErr(msg) { errBox.style.display = 'block'; errBox.textContent += msg + '\n'; }
addEventListener('error', e => showErr('Error: ' + (e.message || e.error)));
addEventListener('unhandledrejection', e => showErr('Rejected: ' + (e.reason && e.reason.message || e.reason)));

/* ================= 2. LIVE DATA LAYER ================= */
const Live = {
  ws: null, hello: null, state: null, idSeq: 0, pending: {},
  subs: { hello: [], state: [], result: [] },
  on(ev, fn) { (this.subs[ev] || (this.subs[ev] = [])).push(fn); },
  emit(ev, data) { for (const fn of this.subs[ev] || []) { try { fn(data); } catch (e) { showErr(ev + ': ' + e.message); } } },
  get connected() { return !!this.ws && this.ws.readyState === 1; },
  send(obj) {
    if (!this.connected) { setStatus('off', 'lab offline — command dropped'); return null; }
    const id = 's' + (++this.idSeq);
    this.ws.send(JSON.stringify({ ...obj, id }));
    return id;
  },
  ask(obj, cb, timeout = 15000) {
    const id = this.send(obj);
    if (!id) return;
    this.pending[id] = cb;
    setTimeout(() => delete this.pending[id], timeout);
  },
  connect() {
    setStatus('off', 'connecting…');
    let ws;
    try { ws = new WebSocket(WS_URL); } catch { return void setTimeout(() => this.connect(), 2000); }
    this.ws = ws;
    ws.onopen = () => setStatus('off', 'waiting for lab…');
    ws.onmessage = ev => {
      let m;
      try { m = JSON.parse(ev.data); } catch { return; }
      if (m.type === 'hello') { this.hello = m; this.emit('hello', m); }
      else if (m.type === 'state') { this.state = m; this.emit('state', m); }
      else if (m.type === 'result') { this.emit('result', m); }
      else if (m.type === 'experiment_progress' || m.type === 'experiment_done' || m.type === 'experiment_cancelled') { this.emit(m.type, m); }
      else if (m.type === 'notebook_log') { this.emit('notebook_log', m); }
      else if (m.type === 'ack') {
        const cb = this.pending[m.id];
        if (cb) { delete this.pending[m.id]; cb(m); }
      }
    };
    ws.onclose = () => { this.ws = null; setStatus('off', 'offline — retrying…'); setTimeout(() => this.connect(), 2000); };
    ws.onerror = () => { try { ws.close(); } catch {} };
  },
};
const statusEl = document.getElementById('status');
const statusText = document.getElementById('statusText');
function setStatus(mode, text) {
  statusEl.classList.toggle('live', mode === 'live');
  statusText.textContent = text;
}
let liveTick = 0;
Live.on('state', st => {
  liveTick++;
  if (liveTick % 10 === 1) {
    const txt = innerWidth < 600 ? `live · ${st.sps}/s` : `live · step ${st.t} · ${st.sps}/s`;
    setStatus('live', txt);
  }
});

/* ================= 3. SHARED HELPERS ================= */
const SLOTS = ['control:banc', 'control:malecns', 'treatment:banc', 'treatment:malecns'];
const STAGES8 = ['retina', 'lamina', 'medulla', 'lobula', 'antennal', 'mushroom', 'central', 'motor'];
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
function slotShort(slot) { return slot.startsWith('control') ? 'C·' + slot.slice(8, 10).toUpperCase() : 'V·' + slot.slice(10, 12).toUpperCase(); }
function flyName(slot) {
  const box = slot.startsWith('control') ? 'C' : 'V';
  const label = (Live.hello && Live.hello.flies[slot] && Live.hello.flies[slot].label) || slotShort(slot);
  return `${box} · ${label}`;
}
function fmtV(v) {
  if (v === null || v === undefined) return '—';
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : v.toFixed(3);
  if (typeof v === 'boolean') return v ? 'yes' : 'no';
  return null;
}
function drugOptions(sel) {
  const drugs = (Live.hello && Live.hello.drugs) || {};
  return Object.keys(drugs).map(d => `<option ${d === sel ? 'selected' : ''}>${esc(d)}</option>`).join('');
}
function groupRate(groups, stage) {
  if (!groups) return null;
  const g = groups[stage];
  if (Array.isArray(g)) return { L: g[0] || 0, R: g[1] || 0 };
  if (g && typeof g === 'object') return { L: g.L || 0, R: g.R || 0 };
  return { L: 0, R: 0 };
}
function drugList(fs) {
  if (!fs || !fs.drugs) return '—';
  const parts = Object.entries(fs.drugs).map(([d, a]) => `${esc(d)} ${(+a).toFixed(3)}`);
  return parts.length ? parts.join(', ') : 'clean';
}

/* ================= 4. OBSERVATION DOCK (3D jars, always visible) ================= */
const dock = { flies: {}, jars: [] };
function initDock() {
  const cv = document.getElementById('dock');
  const wrap = document.getElementById('dockWrap');
  const renderer = new THREE.WebGLRenderer({ canvas: cv, antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x07090d);
  scene.fog = new THREE.FogExp2(0x07090d, 0.028);
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0, 3.9, 11.6);
  const controls = new OrbitControls(camera, cv);
  controls.target.set(0, 2.0, 0);
  controls.enableDamping = true; controls.dampingFactor = 0.08;
  controls.minDistance = 7; controls.maxDistance = 22;
  controls.maxPolarAngle = Math.PI * 0.52;
  scene.add(new THREE.HemisphereLight(0x8899ff, 0x080808, 0.75));
  const key = new THREE.DirectionalLight(0xfff2d9, 1.9);
  key.position.set(5, 10, 7); scene.add(key);
  const rim = new THREE.DirectionalLight(0x2ee6c8, 0.7);
  rim.position.set(-7, 5, -6); scene.add(rim);
  const bench = new THREE.Mesh(
    new THREE.BoxGeometry(13.5, 0.5, 8),
    new THREE.MeshStandardMaterial({ color: 0x232b3d, roughness: 0.85 }));
  bench.position.y = -0.25; scene.add(bench);
  const glassMat = new THREE.MeshPhysicalMaterial({
    color: 0xbcd2ff, transparent: true, opacity: 0.13, roughness: 0.08,
    side: THREE.DoubleSide, depthWrite: false });
  const mkLabel = text => {
    const c = document.createElement('canvas'); c.width = 256; c.height = 64;
    const g = c.getContext('2d');
    g.fillStyle = 'rgba(8,10,14,0.85)'; g.fillRect(0, 0, 256, 64);
    g.strokeStyle = '#3a4358'; g.strokeRect(3, 3, 250, 58);
    g.fillStyle = '#e8eaf0'; g.font = 'bold 30px system-ui,sans-serif';
    g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(text, 128, 34);
    const tex = new THREE.CanvasTexture(c);
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 0.65),
      new THREE.MeshBasicMaterial({ map: tex, transparent: true, side: THREE.DoubleSide }));
    m.position.y = 4.55; return m;
  };
  const mkJar = (x, z, labelText, tint) => {
    const grp = new THREE.Group(); grp.position.set(x, 0, z);
    const glass = new THREE.Mesh(new THREE.CylinderGeometry(1.75, 1.75, 3.9, 40, 1, true), glassMat);
    glass.position.y = 1.95; grp.add(glass);
    const baseDisc = new THREE.Mesh(new THREE.CircleGeometry(1.75, 40),
      new THREE.MeshStandardMaterial({ color: tint, roughness: 0.9 }));
    baseDisc.rotation.x = -Math.PI / 2; baseDisc.position.y = 0.02; grp.add(baseDisc);
    const lid = new THREE.Mesh(new THREE.CylinderGeometry(1.82, 1.82, 0.16, 40),
      new THREE.MeshStandardMaterial({ color: 0x39415a, roughness: 0.6, metalness: 0.4 }));
    lid.position.y = 3.98; grp.add(lid);
    const label = mkLabel(labelText); grp.add(label);
    const leader = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.5, 0.03),
      new THREE.MeshBasicMaterial({ color: 0x9aa0b2 }));
    leader.position.y = 4.2; grp.add(leader);
    const food = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.5, 0.9),
      new THREE.MeshStandardMaterial({ color: 0x6b4a1a, roughness: 1 }));
    food.position.set(0.5, 0.27, 0.5); food.rotation.y = 0.4; grp.add(food);
    for (let i = 0; i < 2; i++) {
      const drop = new THREE.Mesh(new THREE.SphereGeometry(0.16, 10, 10),
        new THREE.MeshStandardMaterial({ color: 0x8a2be2, roughness: 0.4 }));
      drop.position.set((Math.random() - 0.5) * 2, 0.2 + Math.random() * 0.4, (Math.random() - 0.5) * 2);
      grp.add(drop);
    }
    scene.add(grp);
    return grp;
  };
  dock.jars = [
    mkJar(-3.3, 0.3, 'CONTROL', 0x1e3a2a),
    mkJar(3.3, 0.3, 'VARIABLE', 0x3a2a1e),
  ];
  const mkFly = female => {
    const grp = new THREE.Group();
    const bodyMat = new THREE.MeshStandardMaterial({ color: female ? 0xff8fb3 : 0x8fc7ff, roughness: 0.55 });
    const body = new THREE.Mesh(new THREE.SphereGeometry(0.16, 14, 12), bodyMat);
    body.scale.set(0.8, 0.7, 1.25); grp.add(body);
    const stripe = new THREE.Mesh(new THREE.TorusGeometry(0.13, 0.022, 8, 20),
      new THREE.MeshStandardMaterial({ color: 0x222222 }));
    stripe.rotation.x = Math.PI / 2 - 0.3; stripe.position.z = -0.08; grp.add(stripe);
    const eyeMat = new THREE.MeshStandardMaterial({ color: 0xcc1f1f, roughness: 0.25 });
    for (const s of [-1, 1]) {
      const eye = new THREE.Mesh(new THREE.SphereGeometry(0.055, 10, 8), eyeMat);
      eye.position.set(s * 0.09, 0.06, 0.14); grp.add(eye);
      const wing = new THREE.Mesh(new THREE.PlaneGeometry(0.3, 0.13),
        new THREE.MeshBasicMaterial({ color: 0xdfe8ff, transparent: true, opacity: 0.5, side: THREE.DoubleSide }));
      wing.position.set(s * 0.2, 0.12, -0.02); wing.rotation.z = s * 0.35;
      wing.userData.side = s; grp.add(wing);
      for (let l = 0; l < 3; l++) {
        const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.22, 5),
          new THREE.MeshStandardMaterial({ color: 0x111111 }));
        leg.position.set(s * 0.12, -0.13, 0.08 - l * 0.09);
        leg.rotation.z = s * 0.5; grp.add(leg);
      }
    }
    grp.userData.bodyMat = bodyMat;
    return grp;
  };
  const jarX = { control: -3.3, treatment: 3.3 };
  dock.flies = {};
  for (const slot of SLOTS) {
    const grp = mkFly(slot.includes('banc'));
    const [box] = slot.split(':');
    grp.position.set(jarX[box] + (Math.random() - 0.5), 1 + Math.random() * 2, (Math.random() - 0.5) * 2);
    scene.add(grp);
    dock.flies[slot] = grp;
  }
  const dotGeo = new THREE.SphereGeometry(0.08, 8, 6);
  const bananaMat = new THREE.MeshStandardMaterial({ color: 0xffe135, emissive: 0x553f00 });
  const bananaMeshes = {};
  for (const slot of SLOTS) {
    const [box] = slot.split(':');
    const mesh = new THREE.Mesh(dotGeo, bananaMat);
    mesh.position.set(jarX[box], 0.6, 0); scene.add(mesh);
    bananaMeshes[slot] = mesh;
  }
  dock.render = () => {
    const w = wrap.clientWidth, h = wrap.clientHeight;
    if (!w || !h) return;
    if (cv.width !== Math.floor(w * renderer.getPixelRatio()) ||
        cv.height !== Math.floor(h * renderer.getPixelRatio())) {
      renderer.setSize(w, h, false);
      camera.aspect = w / h; camera.updateProjectionMatrix();
    }
    controls.update();
    renderer.render(scene, camera);
  };
  dock.update = (state, t) => {
    if (!state || !state.flies) return;
    for (const slot of SLOTS) {
      const fs = state.flies[slot];
      const grp = dock.flies[slot];
      if (!fs || !grp) continue;
      const [box] = slot.split(':');
      const tx = jarX[box] + (fs.fly.x / 60 - 1), tz = (fs.fly.y / 60 - 1) * 1.4;
      grp.position.x += (tx - grp.position.x) * 0.12;
      grp.position.z += (tz - grp.position.z) * 0.12;
      const wig = REDUCED ? 0 : Math.sin(t * 3 + slot.length) * 0.12;
      grp.position.y += ((fs.fly.caught !== false ? 0.35 : 1.2 + wig) - grp.position.y) * 0.1;
      grp.rotation.y = -fs.fly.angle;
      const flash = Math.min(1, (fs.rates.all || 0) / 0.05);
      grp.userData.bodyMat.emissive.setRGB(flash * 0.5, flash * 0.12, 0);
      for (const child of grp.children) {
        if (child.geometry && child.geometry.type === 'PlaneGeometry') {
          child.rotation.z = child.userData.side * (REDUCED ? 0.35 : 0.35 + Math.sin(t * 31) * 0.55);
        }
      }
      const bm = bananaMeshes[slot];
      if (bm && fs.banana) {
        bm.position.x = jarX[box] + (fs.banana[0] / 60 - 1);
        bm.position.z = (fs.banana[1] / 60 - 1) * 1.4;
      }
    }
  };
}

/* ================= 5. LAB PAGES 1–5 ================= */
const page = document.getElementById('page');
const Labs = {
  current: null, refs: {},
  open(id) {
    const lab = LABS.find(l => l.id === id);
    if (!lab) return;
    this.current = id;
    this.refs = {};
    document.getElementById('pagewrap').scrollTop = 0;
    lab.render();
    this.tick();
  },
  tick() {
    if (!Live.state) return;
    const lab = LABS.find(l => l.id === this.current);
    if (lab && lab.tick) { try { lab.tick(); } catch (e) { showErr('tick ' + this.current + ': ' + e.message); } }
  },
};
const head = (t, s) => `<h2>${t}</h2><p class="sub">${s}</p>`;

/* ---- 1. COMPUTATIONAL NEUROSCIENCE (the story) ---- */
/* ---- TOUR: a live script, not narration over pictures ----
   say -> ask? (waits for a click) -> do? (a real command) -> wait? (a lab
   message) -> result? (a template filled from numbers that just arrived). */
const Tour = { offline: false, dropped: false, figCache: {}, savedLog: [], savedP7: [], tok: 0, cancelAsk: null };
const sleep = ms => new Promise(r => setTimeout(r, ms));
function tourCap(t) { const c = document.getElementById('nCap'); if (c) c.textContent = t; }
async function tourSay(text, id) {
  tourCap(text);
  if (!Narr.playing) return;
  try {
    const r = NARR_AUDIO ? await fetch(`audio/comp-${id}.mp3`, { method: 'HEAD' }) : { ok: false };
    if (r.ok && Narr.playing) {
      await new Promise(resolve => {
        const a = new Audio(`audio/comp-${id}.mp3`);
        Narr.audio = a;
        a.onended = resolve; a.onerror = resolve;
        setTimeout(resolve, 60000);
        a.play().catch(resolve);
      });
      return;
    }
  } catch {}
  await narrSay(text);
}
function tourProgress(text) {  // short wait announcements; never talks over speech
  if (!Narr.playing) return;
  try { if (speechSynthesis.speaking) return; } catch {}
  tourCap(text);
  try { speechSynthesis.speak(new SpeechSynthesisUtterance(text)); } catch {}
}
function tourShow(show) {
  if (!show) return;
  lab1go(show.view, show.exp);
}
function tourAsk(q, opts) {
  return new Promise(resolve => {
    const box = document.getElementById('tourAsk');
    tourCap(q);
    const done = v => { if (box) { box.style.display = 'none'; box.innerHTML = ''; } Tour.cancelAsk = null; resolve(v); };
    Tour.cancelAsk = () => done(null);
    if (!box) return done(opts[0][0]);
    box.style.display = 'block';
    box.innerHTML = `<p class="note">${esc(q)}</p><div class="btns">` +
      opts.map(([v, l]) => `<button class="act" data-v="${esc(v)}">${esc(l)}</button>`).join('') + `</div>`;
    box.querySelectorAll('button').forEach(b => b.onclick = () => done(b.dataset.v));
  });
}
async function lab1waitRec(e) {
  for (let i = 0; i < 20; i++) {
    const r = LAB1.recs[e.id];
    if (r && r.ok === true) return r;
    if (r && r.ok === false) return null;
    if (!Narr.playing) return null;
    await sleep(500);
  }
  const r = LAB1.recs[e.id];
  return r && r.ok ? r : null;
}
function tourResult(e) {
  const s = LAB1.shown[e.id];
  if (!s) return 'No numbers landed for this one — the screen says why.';
  const v = lab1verdictline(e, s.data.rec, s.source);
  const so = lab1sowhat(e, s.data, s.source);
  return so + (v ? ` In short: ${v}.` : '');
}
const tourRes = (ctx, e) => tourResult(e);
const tourDate = iso => { try { return new Date(iso + 'T12:00:00').toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }); } catch { return iso; } };
const tourSeeds = s => (s && s.length > 1) ? `seeds ${s.join(', ')}` : `seed ${s && s[0]}`;
const predWord = p => p === '↑' ? 'up' : p === '↓' ? 'down' : p === '→' ? 'unchanged' : p;
const ATLAS8 = ['nose', 'antennal lobe PNs', 'lateral horn', 'mushroom body', 'central complex', 'descending neurons', 'nerve cord', 'legs'];
const ATLAS_SHORT = ['nose', 'ant. lobe', 'lat. horn', 'mushroom', 'central cx', 'descending', 'nerve cord', 'legs'];
const ASK_DIRECTION = [['↑', '↑ firing up'], ['↓', '↓ firing down'], ['→', '→ no change']];
const ASK_CATCH = [['↑', '↑ treatment catches more'], ['↓', '↓ treatment catches fewer'], ['→', '→ about the same']];
const ASK_CROSS = [['maxime', 'near Maxime'], ['hagrid', 'near Hagrid']];
const PRED_MAP = { '↑': 'higher', '↓': 'lower', '→': 'different' };
const TOUR = [
  { id: 't0', show: { view: 'intro' },
    say: 'Hi. You are in a virtual fly lab. Two simulated flies live here. We will run ten experiments on them, one at a time.',
    sayOff: 'Hi. The lab is offline, so this whole tour runs on saved records. We will replay ten experiments, one at a time.' },
  { id: 't1', show: { view: 'exp', exp: 0 }, exp: 0, do: 'replay',
    say: 'First, one brain cell. It charges up like a tiny battery, then fires. Watch it blip.', result: tourRes },
  { id: 't2', show: { view: 'exp', exp: 1 }, exp: 1, do: 'replay',
    say: 'Now the whole brain at rest. Each row is a neuron. Each dot is a spike. This picture is normal.', result: tourRes },
  { id: 't3', show: { view: 'exp', exp: 2 }, exp: 2, do: 'replay', live: true,
    say: 'Experiment two. We hush the background chatter in the treatment box. What happens to firing?',
    ask: { q: 'The hushed box will fire…?', opts: ASK_DIRECTION }, result: tourRes },
  { id: 't4', show: { view: 'exp', exp: 3 }, exp: 3, do: 'replay', live: true,
    say: 'Experiment three. We add static to every step. What does noise do to firing?',
    ask: { q: 'The noisy box will fire…?', opts: ASK_DIRECTION }, result: tourRes },
  { id: 't5', show: { view: 'exp', exp: 4 }, exp: 4, do: 'replay', live: true,
    say: 'Experiment four. Same synapses, shuffled addresses. Does firing survive?',
    ask: { q: 'The shuffled box will fire…?', opts: ASK_DIRECTION }, result: tourRes },
  { id: 't6', show: { view: 'exp', exp: 5 }, exp: 5, do: 'replay', live: true,
    say: 'Experiment five. Caffeine in, then washed out. Watch each stage rise and fall.',
    ask: { q: 'The dosed box will fire…?', opts: ASK_DIRECTION }, result: tourRes },
  { id: 't7', show: { view: 'exp', exp: 6 }, exp: 6, do: 'replay', replayNote: true,
    say: 'Experiment six. Five doses, both sexes. More drug, more effect?',
    ask: { q: 'Full dose vs no drug: firing will go…?', opts: ASK_DIRECTION }, result: tourRes },
  { id: 't8', show: { view: 'exp', exp: 7 }, exp: 7, do: 'replay', live: true,
    say: 'Experiment seven. Our favourite failure. Can the flies steer toward the banana by smell? We predict no. Expect tiny lucky numbers, not big hauls — and the waterfall shows why.',
    ask: { q: 'The treatment box will catch…?', opts: ASK_CATCH }, result: tourRes },
  { id: 't9', show: { view: 'exp', exp: 8 }, exp: 8, do: 'replay', replayNote: true,
    say: 'Experiment eight. A map of what two knobs make. Our lab settings are the star.', result: tourRes },
  { id: 't10', show: { view: 'exp', exp: 9 }, exp: 9, do: 'replay', replayNote: true,
    say: 'Experiment nine. The brain swap. Is behaviour set by wiring or by settings?',
    ask: { q: 'Cross-A has Maxime’s wiring. It will fire near…?', opts: ASK_CROSS }, result: tourRes },
  { id: 't11', show: { view: 'board' }, do: 'report',
    say: 'That is all ten. The board holds every badge. Your report is downloading now.' },
];
/* Every Lab 1 experiment: one config drives its screen, its replay, its live card, the board and the tour. */
const EXPS = [
  { id: 'demo0', num: '0', title: 'One neuron', kind: 'demo',
    question: 'What does a single brain cell do?',
    setup: ['One cell from each fly, watched for 400 steps.', 'Nothing dosed, nothing cut. Just the cell, charging and firing.'],
    rec: 'fig/vtrace-banc.json', rec2: 'fig/vtrace-malecns.json',
    views: [{ label: 'Voltage traces', pngs: ['fig/vtrace-banc.png', 'fig/vtrace-malecns.png'], caps: ['Maxime (female)', 'Hagrid (male)'] }],
    notshow: 'a recording from a real fly — this is our simulated neuron, computed step by step.',
    prof: 'Leaky integrate-and-fire point neuron (Shiu et al. 2024 style). Yellow dots mark steps where the membrane crossed threshold; the dot sits on the pre-reset voltage.' },
  { id: 'demo1', num: '1', title: 'Baseline brain', kind: 'demo',
    question: 'What does the brain look like when nothing is happening?',
    setup: ['Both flies, both boxes, 400 quiet steps.', 'No stimulus, no drug, no surgery. This picture is “normal” — every experiment after this is a comparison against it.'],
    rec: 'fig/raster-banc.json', rec2: 'fig/raster-malecns.json',
    views: [{ label: 'Rasters', pngs: ['fig/raster-banc.png', 'fig/raster-malecns.png'], caps: ['Maxime (female)', 'Hagrid (male)'] }],
    notshow: 'a response to anything. Rows are neurons sorted nose → brain → legs; dots are spikes.',
    prof: 'Full-brain spike raster, one row per neuron sorted by region. Banc: 157,789 rows. Male CNS: 166,700 rows.' },
  { id: 'quiet', num: '2', title: 'Quiet brain', kind: 'card', measure: 'firing_all',
    question: 'What happens to firing when the background chatter is turned down?',
    setup: ['Both flies, both boxes.', 'Treatment box: background drive turned down to 0.8. Control box: untouched.', 'Measured: share of all neurons firing per step, both flies.'],
    ask: ASK_DIRECTION,
    card: { hypothesis: 'Turning down background drive lowers whole-brain firing.', control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [{ name: 'quiet', setup: [{ cmd: 'restore' }, { cmd: 'set_param', key: 'I_bg', value: 0.8 }] }], measure: 'firing_all', repeats: 3, steps: 400 },
    rec: 'rec/rec-quiet.json', atlas: true,
    notshow: 'proof a real fly would do this — it proves what this model does when you hush it.',
    prof: 'Treatment setup: restore, then set_param I_bg 0.8 on the treatment box. Verdict: experiments.py rule, control box vs quiet, per fly.' },
  { id: 'noisy', num: '3', title: 'Noisy brain', kind: 'card', measure: 'firing_all',
    question: 'What happens to firing when every step gets noisier?',
    setup: ['Both flies, both boxes.', 'Treatment box: per-step current noise 0.5 → 2.0. Control box: untouched.', 'Measured: share of all neurons firing per step, both flies.'],
    ask: ASK_DIRECTION,
    card: { hypothesis: 'Noisier steps change whole-brain firing.', control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [{ name: 'noisy', setup: [{ cmd: 'restore' }, { cmd: 'set_param', key: 'noise', value: 2.0 }] }], measure: 'firing_all', repeats: 3, steps: 400 },
    rec: 'rec/rec-noisy.json', atlas: true,
    notshow: 'a drug test. Noise here is random jiggle added to every neuron, every step.',
    prof: 'Treatment setup: restore, then set_param noise 2.0 on the treatment box. Verdict: experiments.py rule, control box vs noisy, per fly.' },
  { id: 'scramble', num: '4', title: 'Scramble wiring', kind: 'card', measure: 'firing_all',
    question: 'Does behaviour survive when the wiring is shuffled but nothing else changes?',
    setup: ['Both flies, both boxes.', 'Treatment box: same synapses, random targets — structure destroyed, nothing else. Control box: untouched.', 'Measured: share of all neurons firing per step, both flies.'],
    ask: ASK_DIRECTION,
    card: { hypothesis: 'Shuffled wiring changes whole-brain firing.', control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [{ name: 'scrambled', setup: [{ cmd: 'scramble' }] }], measure: 'firing_all', repeats: 3, steps: 400 },
    rec: 'rec/rec-scramble.json', atlas: true,
    notshow: 'a lesion study with a target. Scrambling keeps every synapse and re-deals every address.',
    prof: 'Treatment setup: scramble on the treatment box (targets redrawn uniformly). Verdict: experiments.py rule, control box vs scrambled, per fly.' },
  { id: 'drugs', num: '5', title: 'Drugs over time', kind: 'drugs', measure: 'firing_all',
    question: 'How does caffeine change each brain stage minute by minute — and does washing undo it?',
    setup: ['Both flies, both boxes.', 'Treatment box: caffeine 0.5, then washout. Control box: same minutes, no drug.', 'Measured: firing per stage and whole-brain firing in 25-step windows.'],
    ask: ASK_DIRECTION,
    card: { hypothesis: 'Caffeine raises whole-brain firing.', control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [{ name: 'caffeine 0.5', setup: [{ cmd: 'restore' }, { cmd: 'dose', drug: 'caffeine', amount: 0.5 }] }], measure: 'firing_all', repeats: 3, steps: 400 },
    rec: 'rec/rec-drugs.json',
    views: [{ label: 'Stage rates', pngs: ['fig/rates-banc.png', 'fig/rates-malecns.png'], caps: ['Maxime (female)', 'Hagrid (male)'] },
      { label: 'Time course', pngs: ['fig/timecourse.png'], caps: ['all four flies'] }],
    notshow: 'advice for any living thing. Dose here is unitless — a knob position, not milligrams.',
    prof: 'Recording: 200 quiet steps, 400 dosed, 400 washout × 3 repeats. Verdict: treatment-during vs control-during firing, per fly.' },
  { id: 'dose', num: '6', title: 'Dose–response', kind: 'dose', measure: 'firing_all',
    question: 'More drug, more effect — and do males and females answer differently?',
    setup: ['Both flies, both boxes.', 'Treatment box: one substance at five doses (0 → 2). Control box: no drug.', 'Measured: whole-brain firing per step. Pick a substance below.'],
    ask: ASK_DIRECTION, picker: ['caffeine', 'ethanol', 'isoflurane'],
    cardOf: drug => ({ hypothesis: `${drug} changes whole-brain firing with dose.`, control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [0.25, 0.5, 1.0].map(d => ({ name: `${drug} ${d}`, setup: [{ cmd: 'restore' }, { cmd: 'dose', drug, amount: d }] })), measure: 'firing_all', repeats: 3, steps: 400 }),
    rec: 'rec/rec-dose.json',
    notshow: 'a safety claim of any kind. Simulated flies, unitless doses.',
    prof: 'Recording: 5 doses × 3 repeats × both sexes per substance. Verdict per substance per fly: full dose vs no drug.' },
  { id: 'p7', num: '7', title: 'Smell path (P7)', kind: 'p7', measure: 'bananas',
    question: 'Can the flies steer toward the banana by smell?',
    setup: ['Both flies, both boxes, 3 runs of 400 steps.', 'Nothing differs between the boxes — that is the point. Steering flies would hunt the banana down in both boxes, while wandering flies just get lucky.', 'Measured: bananas caught, both boxes, both flies. Expect small, luck-dominated numbers — the crisp proof is the waterfall, not the count.'],
    ask: ASK_CATCH,
    card: { hypothesis: 'Flies steer toward the banana by smell, so the treatment box catches differently.', control: { name: 'control box', setup: [{ cmd: 'restore' }] },
      treatments: [{ name: 'treatment box', setup: [{ cmd: 'restore' }] }], measure: 'bananas', repeats: 3, steps: 400 },
    rec: 'rec/rec-p7.json',
    views: [{ label: 'Waterfall', pngs: ['fig/waterfall-banc.png', 'fig/waterfall-malecns.png'], caps: ['Maxime (female)', 'Hagrid (male)'] }],
    notshow: 'a broken test. Predicting the failure BEFORE running, then showing it, is the scientific claim.',
    prof: 'Waterfall: left/right information per stage over time (green knows, red forgot). The count card compares two identical boxes with luck-dominated catches, so its verdict only says whether the counts differ — the failure claim rests on the waterfall, where the smell signal dies at the first relay in both brains.' },
  { id: 'regime', num: '8', title: 'Regime map', kind: 'map',
    question: 'What kind of brain do two knob settings make?',
    setup: ['One map per fly: background drive × noise, 9 × 9 settings, 200 steps each.', 'Live run is not offered: the full map takes minutes, so this screen replays the recording. The command to remake it is in the footer.'],
    rec: 'fig/regime-banc.json', rec2: 'fig/regime-malecns.json', live: false,
    views: [{ label: 'Maps', pngs: ['fig/regime-banc.png', 'fig/regime-malecns.png'], caps: ['Maxime (female)', 'Hagrid (male)'] }],
    notshow: 'a medical map. These axes exist only inside this simulation. A map gets no verdict — there is nothing to compare.',
    prof: 'Left: mean firing per setting. Right: burstiness (fraction of steps above 2× median). Star: lab defaults.' },
  { id: 'cross', num: '9', title: 'Brain swap', kind: 'cross',
    question: 'Is behaviour set by the wiring or by the settings?',
    setup: ['Four specimens, same trials: Maxime, Hagrid, and two crosses.', 'Cross-A is Maxime’s wiring on Hagrid’s drive. Cross-B is the reverse.', 'Live run is not offered: crossed brains are built offline. The command is in the footer.'],
    ask: ASK_CROSS, rec: 'cross_results.json', live: false,
    notshow: 'a transplant operation. No neurons moved — settings were crossed, honestly labeled.',
    prof: 'Verdicts: Cross-A vs Maxime and Cross-B vs Hagrid, prediction “different”, measure firing. Repeats barely wobble, so even a small gap reads SUPPORTED (measurably different) — the “wiring wins” claim comes from distances: Cross-A sits nearer Maxime than Hagrid.' },
];
const LAB1 = { view: 'intro', exp: 0, recs: {}, live: {}, pred: {}, prog: '', liveTrace: [], view2: 0, doseDrug: 'caffeine', lock: {}, shown: {}, running: null };
try {
  LAB1.live = JSON.parse(localStorage.getItem('lab1live') || '{}');
  LAB1.pred = JSON.parse(localStorage.getItem('lab1pred') || '{}');
} catch { LAB1.live = {}; LAB1.pred = {}; }
function lab1save() { try { localStorage.setItem('lab1live', JSON.stringify(LAB1.live)); localStorage.setItem('lab1pred', JSON.stringify(LAB1.pred)); } catch {} }
const NOTDONE = [
  ['Compass check', 'fig/ringbump-banc.png', 'One dot each time a compass (EPG) neuron fires. Scattered dots — no traveling bump. Not done properly: firing is sparse, index order is not anatomy, and nothing stimulated the compass.'],
  ['CO₂ activation map', null, 'CO₂ receptor neurons (ORN_V) should light the V glomerulus. Not done: the sim has no CO₂ channel yet — only the banana-smell channel exists.'],
  ['CO₂ high-dose sedation', null, 'Assumption, medium confidence: same family as isoflurane, the standard fly-lab anaesthetic. Not an experiment yet.'],
  ['CO₂ avoidance walking', null, 'Pre-registered test we EXPECT TO FAIL until wind navigation exists (P7: no smell-steering in any real brain).'],
];
const NARR_AUDIO = false; // flip to true when studio files exist at audio/comp-chN.mp3
const Narr = { playing: false, audio: null };
function narrStop() {
  Narr.playing = false;
  if (Tour.cancelAsk) { try { Tour.cancelAsk(); } catch {} Tour.cancelAsk = null; }
  try { if (Narr.audio) { Narr.audio.pause(); Narr.audio = null; } } catch {}
  try { speechSynthesis.cancel(); } catch {}
  const box = document.querySelector('.narr');
  if (box) box.classList.remove('talking');
}
function narrSay(text) {
  return new Promise(resolve => {
    if (!Narr.playing) return resolve();
    let done = false;
    const fin = () => { if (!done) { done = true; resolve(); } };
    const words = text.split(/\s+/).length;
    const safety = setTimeout(fin, words * 450 + 4000);
    const speak = () => {
      try {
        const u = new SpeechSynthesisUtterance(text);
        u.onend = () => { clearTimeout(safety); fin(); };
        u.onerror = () => { clearTimeout(safety); fin(); };
        speechSynthesis.speak(u);
      } catch { clearTimeout(safety); fin(); }
    };
    if (!Narr.playing) { clearTimeout(safety); return fin(); }
    speak();
  });
}
/* old section-driven tour engine removed; chapters now drive the experiment screens */
async function tourChapter(ch) {
  tourShow(ch.show);
  await tourSay((Tour.offline && ch.sayOff) ? ch.sayOff : ch.say, ch.id);
  if (!Narr.playing) return false;
  const e = (ch.exp != null) ? EXPS[ch.exp] : null;
  const ctx = {};
  if (ch.ask && e) {
    await tourSay(ch.ask.q, ch.id + 'q');
    if (!Narr.playing) return false;
    const p = await tourAsk(ch.ask.q, ch.ask.opts);
    if (!Narr.playing || p == null) return false;
    LAB1.pred[e.id] = p; lab1save();
    if (LAB1.view === 'exp') lab1go('exp', ch.exp, true);
    ctx.pred = p;
  }
  if (ch.do === 'replay' && e) {
    const r = await lab1waitRec(e);
    if (!r) {
      await tourSay('The recording for this one is missing — skipping ahead.', ch.id + 'f');
      return true;
    }
    if (!Tour.offline && ch.live) {
      await tourSay('Running it live now.', ch.id + 'w');
      const prog = x => tourProgress(`repeat ${x.repeat || '?'} of ${x.n_rep || '?'}${x.condition ? `, ${x.condition} box` : ''}`);
      const rec = await lab1runLive(e, prog);
      if (!rec) {
        if (!Live.connected) return await tourDropped(ch);
        await tourSay('The live run failed — showing the recording instead.', ch.id + 'f');
        lab1replay(e);
      } else ctx.source = 'live';
    } else {
      if (!Tour.offline && ch.replayNote) await tourSay(`This one takes a while live, so here is the recording — recorded ${r.dateline}.`, ch.id + 'w');
      lab1replay(e);
      ctx.source = 'recording';
    }
  } else if (ch.do === 'report') {
    downloadReport();
  }
  if (ch.result) await tourSay(await ch.result(ctx, e), ch.id + 'r');
  return !!Narr.playing;
}
async function tourDropped(ch) {  // lab died mid-procedure: stop, never read stale numbers
  Tour.dropped = false;
  await tourSay('The lab just dropped — stopping here rather than reading stale numbers.', ch.id + 'd');
  narrStop();
  return false;
}
async function tourPlay() {
  if (Narr.playing) return;
  if (Labs.current !== 'computational') Labs.open('computational');
  Narr.playing = true;
  Tour.offline = !Live.connected || !Live.state;
  Tour.dropped = false;
  const box = document.querySelector('.narr');
  if (box) box.classList.add('talking');
  for (const ch of TOUR) { if (!await tourChapter(ch)) break; }
  const ask = document.getElementById('tourAsk');
  if (ask) { ask.style.display = 'none'; ask.innerHTML = ''; }
  narrStop();
}
/* section-era figure helpers removed; screens use lab1figs + lab1figcap */
function renderComputational() {
  page.innerHTML = head('Computational neuroscience', 'one experiment at a time') + `
  <div id="l1status" class="l1status">waiting for lab…</div>
  <div class="narr"><div class="face">M</div><div style="flex:1">
    <div class="cap" id="nCap">Press play and I’ll walk you through the lab, experiment by experiment.</div>
    <div id="tourAsk" style="display:none"></div>
    <div class="row"><button class="act" id="nPlay" style="flex:none">▶ Play the tour</button>
    <button class="act" id="nStop" style="flex:none">Stop</button>
    <span class="note" id="nVoice"></span></div>
  </div></div>
  <div id="l1view"></div>`;
  Labs.refs = {};
  document.getElementById('nPlay').onclick = tourPlay;
  document.getElementById('nStop').onclick = narrStop;
  document.getElementById('nVoice').textContent = ('speechSynthesis' in window)
    ? 'voice: this browser’s voice (studio mp3s: flip NARR_AUDIO in study.js)' : 'voice: captions only on this browser';
  page.onclick = ev => { if (ev.target.closest('#l1view') || ev.target.closest('#l1status')) lab1click(ev); };
  lab1go(LAB1.view || 'intro');
  lab1fetchRecs();
}
function lab1go(view, exp, noscroll) {
  LAB1.view = view;
  if (exp !== undefined) { LAB1.exp = exp; LAB1.view2 = 0; }
  const v = document.getElementById('l1view');
  if (!v) return;
  if (view === 'intro') v.innerHTML = lab1intro();
  else if (view === 'list') v.innerHTML = lab1list();
  else if (view === 'board') v.innerHTML = lab1board();
  else v.innerHTML = lab1exp(EXPS[LAB1.exp]);
  if (!noscroll) { const w = document.getElementById('pagewrap'); if (w) w.scrollTo(0, 0); }
  tickL1Status();
}
function lab1click(e) {
  const b = e.target.closest('[data-act]');
  if (!b) return;
  const a = b.dataset.act, x = b.dataset.arg;
  if (a === 'goto') lab1go(x);
  else if (a === 'exp') lab1go('exp', +x);
  else if (a === 'prev') lab1go('exp', (LAB1.exp + EXPS.length - 1) % EXPS.length);
  else if (a === 'next') lab1go('exp', (LAB1.exp + 1) % EXPS.length);
  else if (a === 'pred') { const id = b.dataset.exp; if (!LAB1.lock[id] && !LAB1.running) { LAB1.pred[id] = x; lab1save(); lab1go('exp', LAB1.exp, true); } }
  else if (a === 'replay') lab1replay(EXPS[LAB1.exp]);
  else if (a === 'live') lab1runLive(EXPS[LAB1.exp]);
  else if (a === 'view2') { LAB1.view2 = +x; lab1go('exp', LAB1.exp, true); }
  else if (a === 'drug') { LAB1.doseDrug = x; lab1go('exp', LAB1.exp, true); }
  else if (a === 'heal') healBrain();
  else if (a === 'download') downloadReport();
}
function lab1intro() {
  return `<h2>Before you start</h2>
  <p class="body">Nobody put a real fly brain in this computer. We took the wiring diagram — which neuron connects to which — plus simple rules for how neurons fire, and we run it forward, about 28 steps a second. Each fly lives in a box, walks around its box, and hunts a banana. The fly that finds it is just those equations playing out. Like a flight simulator: no wings, but pilots can train in it.</p>
  <h3>MEET THE MODELS</h3>
  <div class="grid2">
    <div class="card"><h4>Maxime · female · BANC</h4><p>157,789 neurons. Every count checked against the original data — all pass.</p></div>
    <div class="card"><h4>Hagrid · male · CNS</h4><p>166,700 neurons. Every count checked against the original data — all pass.</p></div>
  </div>
  <div class="card"><h4>FlyWire Maxime · reference</h4><p>139,255 neurons — third validated brain, reference only.</p></div>
  <details class="how"><summary>How we know (for the professors)</summary>
  <b>Did we copy the brain correctly?</b> We checked all three virtual brains against the original FlyWire Codex data, item by item: neuron counts, transmitter types, cell types, synapse totals. Everything matched — like checking a photocopy against the original, page by page.
  <b>Is it wired like a real fly?</b> We checked two circuits where biology tells us exactly what to expect: the leg-motor pools (391 and 381 command neurons) land precisely where physiology says they should, and the CO₂ smell path — receptor neurons called ORN_V feeding into a relay ball called the V glomerulus — matches measurements from both sides of the brain.</details>
  <div class="btns"><button class="act go" data-act="goto" data-arg="list">Start: the experiment list</button></div>`;
}
function lab1chip(e) {
  if (LAB1.live[e.id]) return '<span class="l1chip done">run live by you</span>';
  if (LAB1.recs[e.id] && LAB1.recs[e.id].ok) return '<span class="l1chip rec">recorded</span>';
  if (LAB1.recs[e.id] && LAB1.recs[e.id].ok === false) return '<span class="l1chip none">planned — not yet run</span>';
  return '<span class="l1chip none">…</span>';
}
function verdictBadge(v) {
  if (!v) return '';
  const c = v === 'SUPPORTED' ? 'v-sup' : v === 'NOT SUPPORTED' ? 'v-not' : v === 'MIXED' ? 'v-mix' : 'v-cant';
  return `<span class="vbadge ${c}">${esc(v)}</span>`;
}
function lab1verdict(e) {
  if (e.kind === 'demo' || e.kind === 'map') return '<span class="vbadge v-none">recorded, no comparison</span>';
  const live = LAB1.live[e.id], rec = LAB1.recs[e.id];
  const v = (live && live.verdict) || (rec && rec.ok && rec.verdict);
  if (v) return verdictBadge(v);
  if (rec && rec.ok === false && !live) return '<span class="vbadge v-none">planned — not yet run</span>';
  return '';
}
function lab1list() {
  const rows = EXPS.map((e, i) => `<div class="l1row" data-act="exp" data-arg="${i}" role="button" tabindex="0">
    <span class="n">${e.num}</span><span><b>${esc(e.title)}</b><br><span class="note">${esc(e.question)}</span></span>
    <span>${lab1chip(e)} ${lab1verdict(e)}</span></div>`).join('');
  const nd = NOTDONE.map(([t, png, why]) => `<div class="card"><h4>${esc(t)} — not done properly yet</h4>
    ${png ? `<img src="${png}" alt="${esc(t)}" loading="lazy" style="max-width:100%">` : ''}<p class="note">${esc(why)}</p></div>`).join('');
  return `<h2>Experiment list</h2><p class="note">One at a time. Pick one, predict, then replay the recording or run it live.</p>
  ${rows}<h3>Not done properly yet</h3>${nd}
  <div class="btns"><button class="act" data-act="goto" data-arg="board">Results board</button></div>`;
}
function lab1board() {
  const rows = EXPS.map(e => `<div class="l1row"><span><b>${esc(e.title)}</b><br><span class="note">${esc(lab1oneline(e))}</span></span>
    <span>${lab1chip(e)} ${lab1verdict(e)}</span></div>`).join('');
  return `<h2>Results board</h2><p class="note">Built from the experiments’ records — nothing hand-written.</p>
  ${rows}<div class="btns"><button class="act" data-act="goto" data-arg="list">Back to the list</button></div>`;
}
function lab1exp(e) {
  const pred = LAB1.pred[e.id], locked = LAB1.lock[e.id] || LAB1.running === e.id;
  const ask = e.ask ? `<h3>Predict</h3><div class="btns">` + e.ask.map(([v, l]) =>
    `<button class="act${pred === v ? ' l1sel' : ''}" data-act="pred" data-exp="${e.id}" data-arg="${esc(v)}"${locked ? ' disabled' : ''}>${esc(l)}</button>`).join('') +
    `</div><p class="note">${locked ? 'Locked — a run is using this prediction.' : pred ? `You picked ${esc(pred)}. It locks when you run.` : 'Pick one first — it locks when you run.'}</p>`
    : `<h3>Predict</h3><p class="note">Demonstrations don’t ask for predictions — just watch.</p>`;
  const views = e.views ? `<div class="btns">` + e.views.map((v, i) =>
    `<button class="act${LAB1.view2 === i ? ' l1sel' : ''}" data-act="view2" data-arg="${i}">${esc(v.label)}</button>`).join('') + `</div>
    ${lab1figs(e, e.views[LAB1.view2] || e.views[0])}` : '';
  const picker = e.picker ? `<div class="btns">` + e.picker.map(d =>
    `<button class="act${LAB1.doseDrug === d ? ' l1sel' : ''}" data-act="drug" data-arg="${d}">${esc(d)}</button>`).join('') + `</div>
    ${lab1figs(e, { pngs: [`fig/dose-${LAB1.doseDrug}.png`], caps: [LAB1.doseDrug + ', both sexes'] })}` : '';
  const atlas = e.atlas ? atlasSvg() + `<p class="note">Live: eight brain stages, brightness from each stage’s firing right now. <span class="badge live">LIVE</span></p>` : '';
  const rec = LAB1.recs[e.id];
  const replayLabel = !rec ? 'Replay the recording (loading…)' : rec.ok ? `Replay the recording · ${rec.dateline}` : 'Replay the recording (missing)';
  const liveBtn = e.live === false ? `<p class="note">${esc(e.setup[e.setup.length - 1])}</p>`
    : Live.connected ? `<button class="act go" data-act="live"${LAB1.running ? ' disabled' : ''}>Run live now</button>`
    : `<p class="note">Run live now needs the lab — you’re offline, so replays only.</p>`;
  const shown = LAB1.shown[e.id];
  return `<h2>${e.num} · ${esc(e.title)}</h2><p class="body l1q">${esc(e.question)}</p>
  <h3>Setup</h3><ul class="l1setup">${e.setup.map(s => `<li>${esc(s)}</li>`).join('')}</ul>
  ${ask}${atlas}${views}${picker}
  <h3>Run</h3><div class="btns"><button class="act" data-act="replay"${!rec || !rec.ok ? ' disabled' : ''}>${esc(replayLabel)}</button>${liveBtn}</div>
  <div class="kv" id="l1prog">${esc(LAB1.prog || '')}</div>
  <canvas id="l1trace" width="600" height="90" style="display:none;width:100%;height:90px"></canvas>
  <div id="l1result">${shown ? lab1resultHTML(e, shown.data, shown.source) : '<p class="note">Nothing shown yet — replay the recording or run it live.</p>'}</div>
  <div class="btns"><button class="act" data-act="prev">← Previous</button><button class="act" data-act="goto" data-arg="list">List</button><button class="act" data-act="next">Next →</button></div>`;
}
function lab1figs(e, view) {
  return (view.pngs || []).map((p, i) => {
    const stem = p.replace('fig/', '').replace('.png', '');
    const id = `l1f${LAB1.exp}_${LAB1.view2}_${i}`;
    setTimeout(() => lab1figcap(stem, id), 0);
    return `<div class="fig ok"><img src="${p}" alt="${esc(view.caps[i] || '')}" loading="lazy"><div class="kv">${esc(view.caps[i] || '')}</div><div class="kv figlabel" id="${id}">loading label…</div></div>`;
  }).join('');
}
async function lab1figcap(stem, id) {
  try {
    const r = await fetch(`fig/${stem}.json`);
    if (!r.ok) throw 0;
    const m = await r.json();
    const el = document.getElementById(id);
    if (el) el.textContent = `recorded ${m.date || '?'} · ${m.seeds ? (m.seeds.length > 1 ? 'seeds ' + m.seeds.join(', ') : 'seed ' + m.seeds[0]) : 'seed ' + m.seed} · ${m.dataset || ''}`;
  } catch { const el = document.getElementById(id); if (el) el.textContent = 'label missing'; }
}
function atlasSvg() {
  const boxes = ATLAS8.map((s, i) => {
    const x = 8 + i * 76;
    return `<rect id="ab${i}" x="${x}" y="14" width="68" height="52" rx="6" style="fill:var(--teal)" opacity="0.18"/>
      <text x="${x + 34}" y="42" style="fill:var(--ink)" font-size="10" text-anchor="middle">${ATLAS_SHORT[i]}</text>`;
  }).join('');
  return `<svg id="atlasSvg" viewBox="0 0 616 80" role="img" aria-label="live stage firing">${boxes}</svg>`;
}
function lab1fetchRecs() {
  for (const e of EXPS) {
    if (LAB1.recs[e.id]) continue;
    LAB1.recs[e.id] = { ok: null };
    (async () => {
      try {
        const r = await fetch(e.rec);
        if (!r.ok) throw 0;
        const j = await r.json();
        let j2 = null;
        if (e.rec2) { const r2 = await fetch(e.rec2); if (r2.ok) j2 = await r2.json(); }
        LAB1.recs[e.id] = { ok: true, json: j, json2: j2, dateline: lab1dateline(e, j), verdict: lab1recverdict(e, j) };
      } catch { LAB1.recs[e.id] = { ok: false }; }
      if (LAB1.view === 'list' || LAB1.view === 'board') lab1go(LAB1.view, undefined, true);
      if (LAB1.view === 'exp' && EXPS[LAB1.exp].id === e.id && !LAB1.running) lab1go('exp', LAB1.exp, true);
    })();
  }
}
function lab1dateline(e, j) {
  const d = tourDate(j.date || '') || '?';
  if (e.kind === 'card' || e.kind === 'p7') { const n = j.seeds ? Object.keys(j.seeds).length : 0; return `${d} · ${n} seeded runs`; }
  if (e.kind === 'cross') return `${d} · ${(j.runs || []).length} seeded runs`;
  const s = j.seeds || [j.seed];
  return `${d} · ${s.length > 1 ? 'seeds ' + s.join(', ') : 'seed ' + s[0]}`;
}
function lab1recverdict(e, j) {
  if (e.kind === 'card' || e.kind === 'p7') return j.verdict || null;
  if (e.kind === 'dose') {
    const vs = [];
    for (const d of e.picker) {
      const V = (j.verdicts || {})[d] || {};
      for (const s of ['banc', 'malecns']) { if ((V[s] || {}).verdict) vs.push(V[s].verdict); }
    }
    return vs.length ? (vs.every(v => v === vs[0]) ? vs[0] : 'MIXED') : null;
  }
  if (e.kind === 'drugs') {
    const V = j.verdicts || {}, a = (V.banc || {}).verdict, b = (V.malecns || {}).verdict;
    return a && a === b ? a : (a || b ? 'MIXED' : null);
  }
  if (e.kind === 'cross') {
    const vs = Object.values(j.verdicts || {});
    return vs.length ? (vs.every(v => v.verdict === vs[0].verdict) ? vs[0].verdict : 'MIXED') : null;
  }
  return null;
}
function lab1oneline(e) {
  const live = LAB1.live[e.id], rec = LAB1.recs[e.id];
  if (!live && !(rec && rec.ok)) return 'planned — not yet run';
  if (e.kind === 'demo' || e.kind === 'map') return 'recorded, no comparison';
  return lab1verdictline(e, live || rec.json, live ? 'live' : 'rec');
}
/* ---- Lab 1 engine: replays, live cards, results ---- */
function lab1needPred(e) {
  if (!e.ask || LAB1.pred[e.id]) return false;
  LAB1.prog = 'Pick a prediction first — it locks when you run.';
  lab1go('exp', LAB1.exp, true);
  return true;
}
function lab1recdata(e) {
  const r = LAB1.recs[e.id];
  if (!r || !r.ok) return null;
  return { rec: r.json, rec2: r.json2, drug: LAB1.doseDrug };
}
function lab1replay(e) {
  if (lab1needPred(e)) return;
  const d = lab1recdata(e);
  if (!d) return;
  LAB1.shown[e.id] = { data: d, source: 'recording' };
  LAB1.prog = '';
  lab1go('exp', LAB1.exp, true);
}
function lab1askp(obj) {
  return new Promise(resolve => {
    if (!Live.connected) return resolve(null);
    let done = false;
    const id = Live.send(obj);
    if (!id) return resolve(null);
    Live.pending[id] = m => { if (!done) { done = true; resolve(m); } };
    setTimeout(() => { if (!done) { done = true; delete Live.pending[id]; resolve(null); } }, 30000);
  });
}
async function lab1runLive(e, onprog) {
  if (!Live.connected || LAB1.running) return null;
  if (lab1needPred(e)) return null;
  const base = e.cardOf ? e.cardOf(LAB1.doseDrug) : e.card;
  const card = JSON.parse(JSON.stringify(base));
  card.title = e.title + (e.cardOf ? ` (${LAB1.doseDrug})` : '');
  card.prediction = PRED_MAP[LAB1.pred[e.id]];
  card.author = 'lab1';
  LAB1.running = e.id; LAB1.lock[e.id] = true; LAB1.liveTrace = [];
  LAB1.prog = 'Writing the card…';
  lab1go('exp', LAB1.exp, true);
  const tc = document.getElementById('l1trace');
  if (tc) tc.style.display = 'block';
  try {
    const ack = await lab1askp({ cmd: 'run_experiment', card });
    if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'lab did not answer');
    const rec = await lab1waitDone(ack.data.id, e, onprog);
    if (!rec) throw new Error('run ended early');
    LAB1.live[e.id] = rec;
    LAB1.shown[e.id] = { data: { rec, drug: LAB1.doseDrug }, source: 'live' };
    LAB1.prog = '';
    lab1save();
  } catch (err) {
    LAB1.prog = 'Live run failed: ' + err.message;
  }
  LAB1.running = null; LAB1.lock[e.id] = false;
  if (LAB1.view === 'exp' && EXPS[LAB1.exp].id === e.id) lab1go('exp', LAB1.exp, true);
  return LAB1.live[e.id] || null;
}
function lab1waitDone(eid, e, onprog) {
  return new Promise(resolve => {
    let last = '', lastRep = 0;
    const tok = (Tour.tok = (Tour.tok || 0) + 1);
    const fin = v => { Tour.tok++; clearInterval(iv); resolve(v); };
    Live.on('experiment_done', m => { if (tok === Tour.tok && m.id === eid) fin(m.record); });
    Live.on('experiment_cancelled', m => { if (tok === Tour.tok && m.id === eid) fin(null); });
    const iv = setInterval(() => {
      if (tok !== Tour.tok) { clearInterval(iv); return; }
      if (!Live.connected) return void fin(null);
      const x = Live.state && Live.state.experiment;
      if (x && x.id === eid) {
        const s = `${x.condition} · repeat ${x.repeat} of ${x.n_rep} · ${Math.round(100 * x.progress)}%`;
        if (s !== last) {
          last = s; LAB1.prog = s;
          const p = document.getElementById('l1prog');
          if (p) p.textContent = s;
          if (x.repeat !== lastRep) { lastRep = x.repeat; if (onprog) onprog(x); }
        }
      }
    }, 700);
    setTimeout(() => { if (tok === Tour.tok) fin(null); }, 600000);
  });
}
function lab1resultHTML(e, data, source) {
  const tag = source === 'live' ? '<span class="badge live">LIVE</span>' : '<span class="badge">REPLAY</span>';
  let body = '';
  if (e.kind === 'card' || e.kind === 'p7' || (source === 'live' && (e.kind === 'dose' || e.kind === 'drugs')))
    body = lab1cardHTML(e, data.rec, source);
  else if (e.kind === 'dose') body = lab1doseHTML(e, data.rec, data.drug);
  else if (e.kind === 'drugs') body = lab1drugsHTML(e, data.rec);
  else if (e.kind === 'demo') body = lab1demoHTML(e, data.rec, data.rec2);
  else if (e.kind === 'map') body = lab1mapHTML(e, data.rec, data.rec2);
  else if (e.kind === 'cross') body = lab1crossHTML(e, data.rec);
  return `<h3>Result ${tag}</h3>${body}<h3>So what</h3><p class="body">${esc(lab1sowhat(e, data, source))}</p>
  <p class="note"><b>Does NOT show:</b> ${esc(e.notshow)}</p>${lab1footerHTML(e, data, source)}`;
}
function lab1mw(vs) {
  const [m, s] = meanSD(vs);
  return `${m.toFixed(4)} ± ${s.toFixed(4)} (n=${vs.length})`;
}
function lab1matchArrow(pred, gap, verdict) {
  if (pred === '↑') return verdict === 'SUPPORTED' && gap > 0;
  if (pred === '↓') return verdict === 'SUPPORTED' && gap < 0;
  return verdict === 'NOT SUPPORTED';
}
function lab1predline(e, gap, verdict) {
  const p = LAB1.pred[e.id];
  if (!p) return '';
  const m = lab1matchArrow(p, gap, verdict);
  return `<p class="note">You said ${esc(predWord(p))} — rule says ${esc(verdict)} (gap ${gap >= 0 ? '+' : ''}${gap.toFixed(4)}): <b>${m ? 'match' : 'miss'}</b>.</p>`;
}
function lab1cardHTML(e, rec, source) {
  const pf = (rec.details || {}).per_fly || {};
  const flies = Object.keys(pf);
  const ti = (e.kind === 'dose' && source === 'live') ? 2 : 0;
  let gaps = [];
  const rows = flies.map(f => {
    const d = (pf[f] || [])[ti] || {};
    gaps.push(d.gap || 0);
    const [cm, cs] = meanSD(d.control_values || []);
    const [tm, ts] = meanSD(d.values || []);
    return `<tr><td>${esc(f)}</td><td>${esc(d.treatment || '')}</td><td>${cm.toFixed(4)} ± ${cs.toFixed(4)}</td><td>${tm.toFixed(4)} ± ${ts.toFixed(4)}</td><td>${verdictBadge(d.verdict)}</td></tr>`;
  }).join('');
  const det = flies.map(f => {
    const d = (pf[f] || [])[ti] || {};
    return `<div>${esc(f)} control runs: ${(d.control_values || []).map(v => (+v).toFixed(4)).join(', ')}</div><div>${esc(f)} treatment runs: ${(d.values || []).map(v => (+v).toFixed(4)).join(', ')}</div>`;
  }).join('');
  const gap = gaps.length ? gaps.reduce((a, b) => a + b, 0) / gaps.length : 0;
  return `<p>Overall: ${verdictBadge(rec.verdict)}</p>
  <table class="mini"><tr><th>fly</th><th>condition</th><th>control box</th><th>treatment box</th><th>verdict</th></tr>${rows}</table>
  <details class="runs"><summary>individual runs</summary>${det}</details>${lab1predline(e, gap, rec.verdict)}`;
}
function lab1doseHTML(e, rec, drug) {
  const R = rec.runs[drug] || {};
  const V = rec.verdicts[drug] || {};
  const rows = ['banc', 'malecns'].map(s => {
    const by = R[s] || {}, ds = Object.keys(by).map(Number).sort((a, b) => a - b);
    const lo = by[String(ds[0])] || [], hi = by[String(ds[ds.length - 1])] || [];
    return `<tr><td>${s === 'banc' ? 'Maxime (female)' : 'Hagrid (male)'}</td><td>${lab1mw(lo)}</td><td>${lab1mw(hi)}</td><td>${verdictBadge((V[s] || {}).verdict)}</td></tr>`;
  }).join('');
  const det = ['banc', 'malecns'].map(s => {
    const by = R[s] || {};
    return `<div>${s}: ` + Object.keys(by).map(Number).sort((a, b) => a - b).map(d =>
      `dose ${d}: ${(by[String(d)] || []).map(v => (+v).toFixed(4)).join(', ')}`).join(' · ') + `</div>`;
  }).join('');
  const gaps = ['banc', 'malecns'].map(s => {
    const by = R[s] || {}, ds = Object.keys(by).map(Number).sort((a, b) => a - b);
    const m = a => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
    return m(by[String(ds[ds.length - 1])] || []) - m(by[String(ds[0])] || []);
  });
  const gap = (gaps[0] + gaps[1]) / 2;
  const v = V.banc && V.malecns && V.banc.verdict === V.malecns.verdict ? V.banc.verdict : 'MIXED';
  return `<p>${esc(drug)}: Maxime ${verdictBadge((V.banc || {}).verdict)} Hagrid ${verdictBadge((V.malecns || {}).verdict)}</p>
  <table class="mini"><tr><th>fly</th><th>no drug</th><th>full dose</th><th>verdict</th></tr>${rows}</table>
  <details class="runs"><summary>individual runs</summary>${det}</details>${lab1predline(e, gap, v)}`;
}
function lab1drugsHTML(e, rec) {
  const V = rec.verdicts || {};
  const rows = ['banc', 'malecns'].map(s => {
    const v = V[s] || {};
    return `<tr><td>${s === 'banc' ? 'Maxime (female)' : 'Hagrid (male)'}</td><td>${lab1mw(v.control || [])}</td><td>${lab1mw(v.treatment || [])}</td><td>${verdictBadge(v.verdict)}</td></tr>`;
  }).join('');
  const det = ['banc', 'malecns'].map(s => {
    const v = V[s] || {};
    return `<div>${s} control-during runs: ${(v.control || []).map(x => (+x).toFixed(4)).join(', ')}</div><div>${s} dosed-during runs: ${(v.treatment || []).map(x => (+x).toFixed(4)).join(', ')}</div>`;
  }).join('');
  const m = a => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
  const gap = (m((V.banc || {}).treatment || []) - m((V.banc || {}).control || []) + m((V.malecns || {}).treatment || []) - m((V.malecns || {}).control || [])) / 2;
  const v = V.banc && V.malecns && V.banc.verdict === V.malecns.verdict ? V.banc.verdict : 'MIXED';
  return `<p>Dosed vs control during-phase firing: Maxime ${verdictBadge((V.banc || {}).verdict)} Hagrid ${verdictBadge((V.malecns || {}).verdict)}</p>
  <table class="mini"><tr><th>fly</th><th>control box</th><th>dosed box</th><th>verdict</th></tr>${rows}</table>
  <details class="runs"><summary>individual runs (during-phase means)</summary>${det}</details>${lab1predline(e, gap, v)}`;
}
function lab1demoHTML(e, rec, rec2) {
  if (e.id === 'demo0') {
    const a = (rec.results || {}).n_spikes, b = ((rec2 || {}).results || {}).n_spikes;
    return `<p>Maxime’s cell: <b>${a}</b> spikes in 400 steps. Hagrid’s cell: <b>${b}</b> spikes in 400 steps.</p>
    <p class="note">A demonstration, not a comparison — no verdict.</p>`;
  }
  const n1 = (rec.results || {}).neurons, s1 = (rec.results || {}).steps;
  const n2 = ((rec2 || {}).results || {}).neurons;
  return `<p>Maxime: <b>${n1}</b> neurons over ${s1} quiet steps. Hagrid: <b>${n2}</b> neurons over ${s1} quiet steps.</p>
  <p class="note">A demonstration, not a comparison — no verdict.</p>`;
}
function lab1mapHTML(e, rec, rec2) {
  const row = (name, j) => { const r = ((j || {}).results || {}), d = (((j || {}).params || {}).default || []);
    return `<tr><td>${name}</td><td>${d[0]}, ${d[1]}</td><td>${r.firing_at_default}</td><td>${r.firing_min} – ${r.firing_max}</td></tr>`; };
  return `<table class="mini"><tr><th>fly</th><th>our star (drive, noise)</th><th>firing there</th><th>whole range</th></tr>
  ${row('Maxime (female)', rec)}${row('Hagrid (male)', rec2)}</table>
  <p class="note">A map, not a comparison — no verdict.</p>`;
}
function lab1crossHTML(e, rec) {
  const by = {};
  for (const r of rec.runs || []) (by[r.specimen] = by[r.specimen] || []).push(r.metrics.firing_all);
  const m = k => by[k].reduce((a, b) => a + b, 0) / by[k].length;
  const names = { maxime: 'Maxime', hagrid: 'Hagrid', cross_a: 'Cross-A', cross_b: 'Cross-B' };
  const rows = ['maxime', 'cross_a', 'hagrid', 'cross_b'].map(k => {
    const seeds = (rec.runs || []).filter(r => r.specimen === k).map(r => r.seed).join(', ');
    return `<tr><td>${names[k]}</td><td>${lab1mw(by[k])}</td><td class="note">seeds ${seeds}</td></tr>`;
  }).join('');
  const dAM = Math.abs(m('cross_a') - m('maxime')), dAH = Math.abs(m('cross_a') - m('hagrid'));
  const nearA = dAM < dAH ? 'maxime' : 'hagrid';
  const det = ['maxime', 'cross_a', 'hagrid', 'cross_b'].map(k =>
    `<div>${names[k]} runs: ${by[k].map(v => (+v).toFixed(4)).join(', ')}</div>`).join('');
  const v = rec.verdicts || {};
  const p = LAB1.pred[e.id];
  const match = p ? `<p class="note">You said Cross-A fires near ${esc(p === 'maxime' ? 'Maxime' : 'Hagrid')} — measured: nearer ${nearA} (${dAM.toFixed(4)} vs ${dAH.toFixed(4)}): <b>${p === nearA ? 'match' : 'miss'}</b>.</p>` : '';
  return `<table class="mini"><tr><th>specimen</th><th>firing/step</th><th>seeds</th></tr>${rows}</table>
  <p>Cross-A vs Maxime: ${verdictBadge((v.cross_a_vs_maxime || {}).verdict)} Cross-B vs Hagrid: ${verdictBadge((v.cross_b_vs_hagrid || {}).verdict)}</p>
  <details class="runs"><summary>individual runs</summary>${det}</details>${match}`;
}
/* old sections 8–9 removed in the one-experiment reorg; see lab1crossHTML + NOTDONE */
function lab1sowhat(e, data, source) {
  const rec = data.rec;
  const avg = a => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
  if (e.kind === 'card' || e.kind === 'p7' || source === 'live') {
    const pf = (rec.details || {}).per_fly || {};
    const fs = Object.keys(pf);
    const ti = (e.kind === 'dose' && source === 'live') ? 2 : 0;
    const cs = fs.map(f => (((pf[f] || [])[ti] || {}).control_mean) || 0);
    const ts = fs.map(f => (((pf[f] || [])[ti] || {}).treatment_mean) || 0);
    const c = avg(cs), t = avg(ts);
    const pct = c ? Math.round(100 * (t - c) / c) : 0;
    if (e.id === 'p7') {
      const tot = fs.reduce((a, f) => a + (((pf[f] || [])[ti] || {}).values || []).reduce((x, y) => x + y, 0), 0);
      return rec.verdict === 'NOT SUPPORTED'
        ? `Both boxes together caught ${tot} bananas over 3 runs each and look alike — no steering, exactly the failure we predicted.`
        : `The boxes caught ${tot} bananas and the rule says ${rec.verdict} — read the table before believing anything.`;
    }
    return `${e.title}: treatment moved firing from ${c.toFixed(4)} to ${t.toFixed(4)} per step — ${Math.abs(pct)}% ${pct >= 0 ? 'up' : 'down'}. Verdict: ${rec.verdict}.`;
  }
  if (e.kind === 'dose') {
    const d = data.drug, R = (rec.runs[d] || {});
    const pct = s => { const by = R[s] || {}, ds = Object.keys(by).map(Number).sort((a, b) => a - b);
      const lo = avg(by[String(ds[0])] || []), hi = avg(by[String(ds[ds.length - 1])] || []);
      return lo ? Math.round(100 * (hi - lo) / lo) : 0; };
    const a = pct('banc'), b = pct('malecns');
    const w = v => `${v >= 0 ? 'up' : 'down'} ${Math.abs(v)}%`;
    return `At full dose, ${d} moves Maxime ${w(a)} and Hagrid ${w(b)}.`;
  }
  if (e.kind === 'drugs') {
    const V = rec.verdicts || {};
    const r = s => { const v = V[s] || {}; const c = avg(v.control || []), t = avg(v.treatment || []); return c ? (t / c).toFixed(1) : '?'; };
    return `Dosed firing ran ${r('banc')}× control in Maxime and ${r('malecns')}× in Hagrid during dosing, then washed back.`;
  }
  if (e.id === 'demo0') {
    const a = (rec.results || {}).n_spikes, b = ((data.rec2 || {}).results || {}).n_spikes;
    return `One cell blips ${a} times (Maxime) and ${b} times (Hagrid) in 400 steps — thought is thousands of these at once.`;
  }
  if (e.id === 'demo1') {
    const n1 = (rec.results || {}).neurons, s1 = (rec.results || {}).steps;
    return `This is normal: ${n1} neurons idling for ${s1} steps (Maxime), and as many again for Hagrid.`;
  }
  if (e.kind === 'map') {
    const r1 = rec.results || {}, r2 = (data.rec2 || {}).results || {};
    return `Maxime hums at ${r1.firing_at_default}/step and Hagrid at ${r2.firing_at_default}/step, both at their stars.`;
  }
  if (e.kind === 'cross') {
    const mean = k => { const vs = rec.runs.filter(x => x.specimen === k).map(x => x.metrics.firing_all); return avg(vs); };
    const a = mean('cross_a'), m = mean('maxime'), h = mean('hagrid');
    return `Cross-A fires ${a.toFixed(4)}, near Maxime's ${m.toFixed(4)} and far from Hagrid's ${h.toFixed(4)}: wiring wins.`;
  }
  return '';
}
const LAB1CMD = {
  demo0: 'python3 -m compute.figures vtrace --out metaverse-lab/fig (and --subject malecns)',
  demo1: 'python3 -m compute.figures raster --out metaverse-lab/fig (and --subject malecns)',
  quiet: 'python3 -m compute.recordings quiet --out metaverse-lab/rec',
  noisy: 'python3 -m compute.recordings noisy --out metaverse-lab/rec',
  scramble: 'python3 -m compute.recordings scramble --out metaverse-lab/rec',
  drugs: 'python3 -m compute.figures timecourse --out metaverse-lab/fig --recout metaverse-lab/rec',
  dose: 'python3 -m compute.figures dose --out metaverse-lab/fig --recout metaverse-lab/rec',
  p7: 'python3 -m compute.recordings p7 --out metaverse-lab/rec',
  regime: 'python3 -m compute.figures regime --out metaverse-lab/fig (and --subject malecns)',
  cross: 'python3 run_cross.py --repeats 3 --steps 400',
};
function lab1seedsHTML(e, rec, source) {
  if ((e.kind === 'card' || e.kind === 'p7' || source === 'live') && rec.seeds) {
    if (Array.isArray(rec.seeds)) return 'runs: ' + rec.seeds.map(s => `run ${s.run} ${s.condition}: ${Object.values(s.seeds).join('/')}`).join(' · ');
    const ks = Object.keys(rec.seeds);
    return `${ks.length} runs: ` + ks.map(k => `${k.split(' / ')[0]} ${rec.seeds[k]}`).join(', ');
  }
  if ((e.kind === 'card' || e.kind === 'p7') && source === 'live' && !rec.seeds) return 'runs not individually seeded — restart the lab with patch 004';
  if (rec.seeds) return 'seeds ' + rec.seeds.join(', ');
  if (e.kind === 'cross') return 'seeds ' + (rec.runs || []).map(r => r.seed).join(', ');
  return 'seed ' + rec.seed;
}
function lab1footerHTML(e, data, source) {
  const rec = data.rec;
  const commit = (rec.label && rec.label.git_commit) || (source === 'live' ? '(server doesn’t report it — check Fly Brain’s git log)' : '?');
  const dataset = (rec.label && rec.label.dataset) || [...new Set(Object.values(rec.flies || {}).map(f => f.dataset).filter(Boolean))].join(' + ') || '?';
  const settings = rec.card ? `steps ${rec.card.steps}, repeats ${rec.card.repeats}, setups ${rec.card.treatments.map(t => t.name + ':' + t.setup.map(s => s.cmd).join('+')).join('; ')}`
    : e.kind === 'dose' ? `doses ${(rec.doses || []).join(', ')}, both sexes`
    : e.kind === 'drugs' ? `drug ${rec.drug}, window ${rec.window}, 3 repeats`
    : e.kind === 'cross' ? `repeats 3, steps 400` : `params ${JSON.stringify(rec.params || {})}`;
  const analyses = [];
  if (rec.analysis) analyses.push(rec.analysis);
  if (rec.verdicts) for (const [k, v] of Object.entries(rec.verdicts)) { if (v.analysis) analyses.push(`${k}: ${v.analysis}`); }
  const nb = source === 'live' ? `Saved in the lab notebook as ${rec.id}.` : `Recording ${rec.id} — shipped with the page.`;
  return `<h3>Footer</h3><div class="kv">commit ${esc(commit)} · ${esc(dataset)} · ${esc(lab1seedsHTML(e, rec, source))} · ${esc(settings)}</div>
  <div class="kv">record ${esc(rec.id || '?')} · ${esc(nb)}</div>
  <div class="kv">remake it: <b>${esc(LAB1CMD[e.id] || '')}</b></div>
  <details class="how"><summary>For the professors</summary>${esc(e.prof)}<br><br>${analyses.map(esc).join('<br><br>')}</details>`;
}
function lab1verdictline(e, rec, source) {
  const avg = a => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
  if (e.kind === 'card' || e.kind === 'p7' || source === 'live') {
    const pf = (rec.details || {}).per_fly || {};
    const fs = Object.keys(pf);
    const c = avg(fs.map(f => (((pf[f] || [])[0] || {}).control_mean) || 0));
    const t = avg(fs.map(f => (((pf[f] || [])[0] || {}).treatment_mean) || 0));
    const pct = c ? Math.round(100 * (t - c) / c) : 0;
    return `gap ${pct}% (${c.toFixed(4)} → ${t.toFixed(4)}), ${rec.verdict}`;
  }
  if (e.kind === 'dose') {
    return e.picker.map(d => { const V = ((rec.verdicts || {})[d] || {});
      return `${d} ${((V.banc || {}).verdict || '?') === ((V.malecns || {}).verdict || '?') ? (V.banc || {}).verdict : 'split'}`; }).join(' · ');
  }
  if (e.kind === 'drugs') {
    const V = rec.verdicts || {};
    return `Maxime ${(V.banc || {}).verdict}, Hagrid ${(V.malecns || {}).verdict}`;
  }
  if (e.kind === 'cross') {
    const mean = k => { const vs = rec.runs.filter(x => x.specimen === k).map(x => x.metrics.firing_all); return avg(vs); };
    return `A≈M ${Math.abs(mean('cross_a') - mean('maxime')).toFixed(4)}, B≈H ${Math.abs(mean('cross_b') - mean('hagrid')).toFixed(4)}`;
  }
  return rec.verdict || '';
}
function tickL1Status() {
  const st = document.getElementById('l1status');
  const on = Live.connected && Live.state;
  if (st && st._mode !== (on ? 'on' : 'off')) {
    st._mode = on ? 'on' : 'off';
    st.innerHTML = on
      ? `<span class="dot on"></span> <span id="l1stxt"></span> <span class="tick" id="l1tick"></span><span class="sp"></span><button class="act sm" data-act="heal">Heal treatment box</button> <button class="act sm" data-act="download">Download report</button>`
      : `<span class="dot off"></span> offline — replays only <span class="sp"></span><button class="act sm" data-act="download">Download report</button>`;
  }
  if (st && on) {
    const s = Live.state, f = s.flies[s.focus] || {};
    const hz = ((f.rates && f.rates.all) || 0) * (s.sps || 28);
    const legs = f.legs ? f.legs.reduce((a, b) => a + Math.abs(b), 0) / f.legs.length : 0;
    const t = document.getElementById('l1stxt'), k = document.getElementById('l1tick');
    if (t) t.textContent = `live · step ${s.t} · ${s.sps}/s · focus ${flyName(s.focus)}`;
    if (k) k.textContent = `nose → brain ${hz.toFixed(1)}/s → legs ${legs.toFixed(2)}`;
  }
  const s = Live.state;
  for (let i = 0; i < 8; i++) {
    const r = document.getElementById('ab' + i);
    if (!r) break;
    let v = 0;
    if (s && Live.connected) {
      const gr = groupRate((((s.flies || {})[s.focus] || {})).groups || {}, ATLAS8[i]);
      if (gr) v = (gr.L + gr.R) / 2;
    }
    r.setAttribute('opacity', (0.15 + 0.85 * Math.min(1, v / 0.05)).toFixed(2));
  }
  if (LAB1.running && s && Live.connected) {
    const f = s.flies[s.focus] || {};
    LAB1.liveTrace.push(((f.rates && f.rates.all) || 0) * (s.sps || 28));
    if (LAB1.liveTrace.length > 600) LAB1.liveTrace.shift();
  }
  const cv = document.getElementById('l1trace');
  if (cv && LAB1.liveTrace.length > 1) {
    cv.style.display = 'block';
    const ctx = cv.getContext('2d'), W = cv.width, H = cv.height, tr = LAB1.liveTrace;
    const mx = Math.max(0.1, ...tr);
    ctx.fillStyle = cssVar('--panel'); ctx.fillRect(0, 0, W, H);
    ctx.strokeStyle = cssVar('--teal'); ctx.lineWidth = 1.5; ctx.beginPath();
    tr.forEach((v, i) => { const x = (i / (tr.length - 1)) * W, y = H - 4 - (v / mx) * (H - 24); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
    ctx.stroke();
    ctx.fillStyle = cssVar('--muted'); ctx.font = '10px sans-serif';
    ctx.fillText(`live firing, focus fly (max ${mx.toFixed(1)}/s)`, 6, 12);
  }
}
function tickComputational() { tickL1Status(); }

/* ---- 2. COMPARATIVE NEUROSCIENCE ---- */
function renderComparative() {
  page.innerHTML = head('Comparative neuroscience', 'female vs male, control vs variable — same moment, four flies') + `
    <table class="mini" id="xTable"></table>
    <p class="note">Neurons: Maxime 157,789 · Hagrid 166,700. Firing is live spikes/s whole-brain.</p>`;
  Labs.refs = { table: document.getElementById('xTable') };
}
function tickComparative() {
  const R = Labs.refs, st = Live.state;
  if (!R.table) return;
  const cols = SLOTS.map(s => {
    const fs = st.flies[s];
    return `<td>${esc(flyName(s))}</td>`;
  });
  const row = (name, fn) => `<tr><td>${name}</td>${SLOTS.map(s => `<td>${fn(st.flies[s])}</td>`).join('')}</tr>`;
  R.table.innerHTML = `<tr><th></th>${cols.join('')}</tr>` +
    row('firing /s', fs => fs ? ((fs.rates.all || 0) * st.sps).toFixed(1) : '—') +
    row('position', fs => fs ? `(${fs.fly.x.toFixed(0)}, ${fs.fly.y.toFixed(0)})` : '—') +
    row('banana', fs => fs ? (fs.fly.caught === false ? 'free' : 'caught') : '—') +
    row('drugs', fs => drugList(fs));
}

/* ---- 3. CONNECTOMICS / NEUROANATOMY ---- */
function renderConnectomics() {
  page.innerHTML = head('Connectomics / neuroanatomy', 'the validated wiring — fixed anatomy, not live data') + `
    <div class="kv" id="nFocus"></div>
    <h3>DATASETS</h3>
    <div class="grid2">
      <div class="card"><h4>BANC · Maxime (female)</h4><p>157,789 neurons. Full brain + VNC wiring audit: provenance all pass.</p></div>
      <div class="card"><h4>Male CNS · Hagrid (male)</h4><p>166,700 neurons. Provenance all pass.</p></div>
    </div>
    <div class="card"><h4>FlyWire · Maxime v783</h4><p>139,255 neurons. Reference brain, provenance all pass.</p></div>
    <h3>WIRING AUDIT (INDEPENDENT)</h3>
    <div class="card"><h4>Leg motor pools — exact match</h4><p>391 / 381 command neurons land exactly where physiology says they should.</p></div>
    <div class="card"><h4>Smell: ORN_V → V glomerulus</h4><p>CO₂ receptor neurons are cell type ORN_V in all three brains; bilateral ORN data confirms the path.</p></div>
    <div class="card"><h4>P7 — smell-steering is absent</h4><p>In all three brains the left/right smell signal is lost at the first synapse: no fly steers by smell. Holds for every experiment below.</p></div>`;
  Labs.refs = { focus: document.getElementById('nFocus') };
}
function tickConnectomics() {
  const R = Labs.refs, st = Live.state;
  if (R.focus) R.focus.innerHTML = `live focus: <b>${esc(flyName(st.focus))}</b> — anatomy pages don’t change; the sim does`;
}

/* ---- 4. NEUROINFORMATICS ---- */
function renderNeuroinfo() {
  page.innerHTML = head('Neuroinformatics', 'query the live numbers + the substance library') + `
    <h3>LIVE STATE TABLE</h3>
    <table class="mini" id="iTable"></table>
    <h3>SUBSTANCE LOOKUP</h3>
    <label>name <input type="text" id="iQ" placeholder="caffeine, ethanol, DEET…"></label>
    <div class="btns"><button class="act" id="iGo">Look up</button></div>
    <div id="iOut"></div>`;
  Labs.refs = { table: document.getElementById('iTable') };
  document.getElementById('iGo').onclick = () => {
    const q = document.getElementById('iQ').value.trim();
    if (!q) return;
    Live.ask({ cmd: 'lookup', name: q }, ack => {
      const out = document.getElementById('iOut');
      if (!out) return;
      const d = ack.data || {};
      const m = d.match || {};
      out.innerHTML = ack.ok ? `<div class="card"><h4>${esc(m.name || q)}</h4>
        <p>${esc(m.mechanism || 'no mechanism on file')}</p>
        <p class="note">confidence ${esc(m.confidence || '?')} · testable ${esc(m.testable || '?')} · dose as ${esc(m.dose_as || '?')}</p>
        <p class="note">${esc(m.evidence || '')}</p></div>`
        : `<p class="note">lookup failed: ${esc(ack.error || 'unknown')}</p>`;
    });
  };
}
function tickNeuroinfo() {
  const R = Labs.refs, st = Live.state;
  if (!R.table) return;
  R.table.innerHTML = `<tr><th>fly</th><th>x</th><th>y</th><th>head°</th><th>firing/s</th><th>drugs</th></tr>` +
    SLOTS.map(s => {
      const fs = st.flies[s];
      if (!fs) return '';
      return `<tr><td>${esc(flyName(s))}</td><td>${fs.fly.x.toFixed(1)}</td><td>${fs.fly.y.toFixed(1)}</td>` +
        `<td>${(fs.fly.angle * 180 / Math.PI).toFixed(0)}</td><td>${((fs.rates.all || 0) * st.sps).toFixed(1)}</td><td>${drugList(fs)}</td></tr>`;
    }).join('');
}

/* ---- 5. ELECTROPHYSIOLOGY ---- */
const Ephys = { slot: 'control:banc' };
function renderEphys() {
  const btns = SLOTS.map(s => `<button class="act" data-slot="${s}">${esc(flyName(s))}</button>`).join('');
  page.innerHTML = head('Electrophysiology', 'spikes per second, per brain stage — pick a fly, watch it fire') + `
    <div class="btns">${btns}</div>
    <div id="eBody" style="margin-top:8px"></div>`;
  Labs.refs = { body: document.getElementById('eBody') };
  page.querySelectorAll('button[data-slot]').forEach(b => { b.onclick = () => { Ephys.slot = b.dataset.slot; tickEphys(); }; });
}
function tickEphys() {
  const R = Labs.refs, st = Live.state;
  if (!R.body) return;
  const fs = st.flies[Ephys.slot];
  if (!fs || !fs.groups) {
    R.body.innerHTML = `<p class="note">waiting for group data on <b>${esc(flyName(Ephys.slot))}</b>…</p>`;
    return;
  }
  R.body.innerHTML = `<p class="note"><b>${esc(flyName(Ephys.slot))}</b> · blue L / orange R</p>` +
    STAGES8.map(s => {
      const v = groupRate(fs.groups, s);
      const mx = Math.max(v.L, v.R, 1);
      return `<div class="gbar"><span class="nm">${s}</span><span class="tr">
        <span class="fl" style="width:${(v.L / mx * 50).toFixed(0)}%"></span>
        <span class="fr" style="width:${(v.R / mx * 50).toFixed(0)}%"></span></span>
        <span class="vv">${v.L.toFixed(1)} / ${v.R.toFixed(1)}</span></div>`;
    }).join('');
}

/* ================= 6. LAB PAGES 6–9 ================= */
/* ---- 6. NEUROPHARMACOLOGY ---- */
const Pharma = { drug: 'caffeine', amount: 0.5 };
function renderPharma() {
  page.innerHTML = head('Neuropharmacology', 'dose the VARIABLE jar — CONTROL stays clean') + `
    <label>chemical <select id="pDrug">${drugOptions(Pharma.drug)}</select></label>
    <label>amount <span id="pAmtV">${Pharma.amount}</span>
      <input type="range" id="pAmt" min="0" max="2" step="0.05" value="${Pharma.amount}"></label>
    <div class="btns">
      <button class="act warn" id="pDose">Dose</button>
      <button class="act" id="pWash">Washout</button>
      <button class="act" id="pRestore">Restore</button>
    </div>
    <h3>FLIES RIGHT NOW</h3>
    <div class="kv" id="pFlies"></div>`;
  Labs.refs = { flies: document.getElementById('pFlies') };
  const amt = document.getElementById('pAmt');
  amt.oninput = () => { Pharma.amount = +amt.value; document.getElementById('pAmtV').textContent = amt.value; };
  document.getElementById('pDrug').onchange = e => Pharma.drug = e.target.value;
  document.getElementById('pDose').onclick = () => Live.send({ cmd: 'dose', box: 'treatment', drug: Pharma.drug, amount: Pharma.amount });
  document.getElementById('pWash').onclick = () => Live.send({ cmd: 'washout', box: 'treatment' });
  document.getElementById('pRestore').onclick = () => Live.send({ cmd: 'restore' });
}
function tickPharma() {
  const R = Labs.refs, st = Live.state;
  if (!R.flies) return;
  R.flies.innerHTML = SLOTS.map(s => {
    const fs = st.flies[s];
    return `<div>${esc(flyName(s))}: firing ${((fs.rates.all || 0) * st.sps).toFixed(1)}/s · ${drugList(fs)}</div>`;
  }).join('');
}

/* ---- 7. PHARMACOKINETICS ---- */
const PK = { hist: [] };
function pkSample(st) {
  const sum = box => SLOTS.filter(s => s.startsWith(box)).reduce((a, s) => {
    const fs = st.flies[s];
    return a + (fs && fs.drugs ? Object.values(fs.drugs).reduce((x, v) => x + (+v || 0), 0) : 0);
  }, 0);
  const fir = slot => { const fs = st.flies[slot]; return fs ? (fs.rates.all || 0) * st.sps : 0; };
  PK.hist.push({ t: st.t, vDrug: sum('treatment'), vFir: fir('treatment:banc'), cFir: fir('control:banc') });
  if (PK.hist.length > 600) PK.hist.shift();
}
function renderPK() {
  page.innerHTML = head('Pharmacokinetics', 'drug level and firing in the variable jar over the last minute — sampled live') + `
    <canvas id="pkChart"></canvas>
    <p class="note">amber = variable-box drug level · teal = variable Maxime firing/s · dim = control Maxime firing/s</p>
    <div class="kv" id="pkNow"></div>`;
  Labs.refs = { now: document.getElementById('pkNow'), cv: document.getElementById('pkChart') };
}
function tickPK() {
  const R = Labs.refs;
  if (!R.cv) return;
  const cv = R.cv, r = Math.min(devicePixelRatio || 1, 2);
  const W = cv.clientWidth, H = cv.clientHeight;
  if (cv.width !== Math.floor(W * r) || cv.height !== Math.floor(H * r)) { cv.width = Math.floor(W * r); cv.height = Math.floor(H * r); }
  const g = cv.getContext('2d');
  g.setTransform(r, 0, 0, r, 0, 0);
  g.clearRect(0, 0, W, H);
  const h = PK.hist;
  if (h.length > 1) {
    const mx = Math.max(1, ...h.map(p => Math.max(p.vDrug, p.vFir, p.cFir)));
    const Y = v => H - 10 - (v / mx) * (H - 20);
    const line = (key, col) => {
      g.strokeStyle = col; g.lineWidth = 1.6; g.beginPath();
      h.forEach((p, i) => { const x = 8 + (i / Math.max(1, h.length - 1)) * (W - 16); i ? g.lineTo(x, Y(p[key])) : g.moveTo(x, Y(p[key])); });
      g.stroke();
    };
    g.strokeStyle = cssVar('--line'); g.lineWidth = 1;
    g.beginPath(); g.moveTo(8, H - 10); g.lineTo(W - 8, H - 10); g.stroke();
    line('cFir', cssVar('--muted')); line('vFir', cssVar('--teal')); line('vDrug', cssVar('--amber'));
  } else {
    g.fillStyle = cssVar('--muted'); g.font = '12px system-ui';
    g.fillText('sampling… dose something in Neuropharmacology and watch here', 12, 24);
  }
  const last = h[h.length - 1];
  if (R.now && last) R.now.textContent = `now: drug ${last.vDrug.toFixed(3)} · V firing ${last.vFir.toFixed(1)}/s · C firing ${last.cFir.toFixed(1)}/s`;
}

/* ---- 8. CIRCUIT & SYSTEMS ---- */
const Circuits = { mode: 'live' };
function renderCircuits() {
  page.innerHTML = head('Circuit & systems neuroscience', 'which region drives the difference — treatment minus control') + `
    <div class="btns">
      <button class="act" id="sLive">Live (focus fly)</button>
      <button class="act" id="sDelta">Δ variable−control</button>
    </div>
    <div id="sBody" style="margin-top:8px"></div>
    <p class="note">P7 holds here: no left/right smell signal survives the first synapse, so lateral differences never mean smell-steering.</p>`;
  document.getElementById('sLive').onclick = () => { Circuits.mode = 'live'; tickCircuits(); };
  document.getElementById('sDelta').onclick = () => { Circuits.mode = 'delta'; tickCircuits(); };
  Labs.refs = { body: document.getElementById('sBody') };
}
function tickCircuits() {
  const R = Labs.refs, st = Live.state;
  if (!R.body) return;
  if (Circuits.mode === 'live') {
    const fs = st.flies[st.focus];
    if (!fs || !fs.groups) {
      R.body.innerHTML = `<p class="note">waiting for group data on <b>${esc(flyName(st.focus))}</b>…</p>`;
      return;
    }
    R.body.innerHTML = `<p class="note"><b>${esc(flyName(st.focus))}</b> · spikes/s per stage · blue L / orange R</p>` +
      STAGES8.map(s => {
        const v = groupRate(fs.groups, s);
        const mx = Math.max(v.L, v.R, 1);
        return `<div class="gbar"><span class="nm">${s}</span><span class="tr">
          <span class="fl" style="width:${(v.L / mx * 50).toFixed(0)}%"></span>
          <span class="fr" style="width:${(v.R / mx * 50).toFixed(0)}%"></span></span>
          <span class="vv">${v.L.toFixed(1)} / ${v.R.toFixed(1)}</span></div>`;
      }).join('');
    return;
  }
  const need = SLOTS;
  const ok = need.every(s => st.flies[s] && st.flies[s].groups);
  if (!ok) {
    R.body.innerHTML = '<p class="note">Δ needs firing-per-group on all four flies (patched server).</p>';
    return;
  }
  const dF = {}, dM = {};
  let mx = 0.001;
  for (const s of STAGES8) {
    const cf = groupRate(st.flies['control:banc'].groups, s), vf = groupRate(st.flies['treatment:banc'].groups, s);
    const cm = groupRate(st.flies['control:malecns'].groups, s), vm = groupRate(st.flies['treatment:malecns'].groups, s);
    dF[s] = (vf.L + vf.R) / 2 - (cf.L + cf.R) / 2;
    dM[s] = (vm.L + vm.R) / 2 - (cm.L + cm.R) / 2;
    mx = Math.max(mx, Math.abs(dF[s]), Math.abs(dM[s]));
  }
  const bar = v => {
    const w = Math.abs(v) / mx * 50;
    return v >= 0
      ? `<span class="bl" style="left:50%;width:${w}%;background:#3ddc84"></span>`
      : `<span class="bl" style="right:50%;width:${w}%;background:#ff6b6b"></span>`;
  };
  R.body.innerHTML = '<p class="note">treatment − control, spikes/s · green up / red down</p>' +
    STAGES8.map(s => `<div class="drow"><span class="nm">${s} ♀</span><span class="tr"><span class="mid"></span>${bar(dF[s])}</span>
      <span style="width:64px;text-align:right;font-variant-numeric:tabular-nums">${dF[s] >= 0 ? '+' : ''}${dF[s].toFixed(1)}</span></div>
      <div class="drow"><span class="nm">${s} ♂</span><span class="tr"><span class="mid"></span>${bar(dM[s])}</span>
      <span style="width:64px;text-align:right;font-variant-numeric:tabular-nums">${dM[s] >= 0 ? '+' : ''}${dM[s].toFixed(1)}</span></div>`).join('');
}

/* ---- 9. BEHAVIOURAL NEUROSCIENCE & ETHOLOGY ---- */
const Behaviour = { steps: 400, onboard: true, lastResult: null };
function renderBehaviour() {
  page.innerHTML = head('Behavioural neuroscience & ethology', 'run trials, watch what the flies actually do') + `
    <label>trial steps <input type="number" id="bSteps" min="50" max="2000" step="50" value="${Behaviour.steps}"></label>
    <label><input type="checkbox" id="bOnboard" ${Behaviour.onboard ? 'checked' : ''} style="width:auto;max-width:none"> onboard chart (banana approaches)</label>
    <div class="btns"><button class="act go" id="bRun">Run trial</button></div>
    <div class="bar"><div id="bBar"></div></div>
    <div class="kv" id="bTrial"></div>
    <h3>FLIES RIGHT NOW</h3>
    <div class="kv" id="bFlies"></div>
    <div id="bResult"></div>`;
  Labs.refs = {
    bar: document.getElementById('bBar'), trial: document.getElementById('bTrial'),
    flies: document.getElementById('bFlies'), result: document.getElementById('bResult'),
  };
  document.getElementById('bSteps').onchange = e => Behaviour.steps = Math.max(50, +e.target.value || 400);
  document.getElementById('bOnboard').onchange = e => Behaviour.onboard = e.target.checked;
  document.getElementById('bRun').onclick = () => Live.send({ cmd: 'run_trial', label: 'behaviour', steps: Behaviour.steps, onboard: Behaviour.onboard });
  if (Behaviour.lastResult) showBehaviourResult(Behaviour.lastResult);
}
function showBehaviourResult(m) {
  Behaviour.lastResult = m;
  const R = Labs.refs;
  if (Labs.current !== 'behaviour' || !R.result) return;
  const rows = Object.entries(m.metrics || {}).map(([k, v]) => {
    const f = fmtV(v);
    return f === null ? '' : `<tr><td>${esc(k)}</td><td>${f}</td></tr>`;
  }).join('');
  const pf = m.per_fly ? Object.entries(m.per_fly).map(([s, v]) =>
    `<tr><td>${slotShort(s)}</td><td>${fmtV(v.activity)}</td><td>${fmtV(v.firing_all)}</td><td>${fmtV(v.bananas)}</td></tr>`).join('') : '';
  R.result.innerHTML = `<div class="card"><h4>${esc(m.label || 'trial')} — focus ${slotShort(m.focus || '')}</h4>
    <table class="mini"><tr><th>metric</th><th>value</th></tr>${rows}</table>
    ${pf ? `<table class="mini"><tr><th>fly</th><th>act</th><th>fir</th><th>ban</th></tr>${pf}</table>` : ''}</div>`;
  const card = R.result.firstChild;
  if (card && card.scrollIntoView) card.scrollIntoView({ block: 'nearest' });
}
function tickBehaviour() {
  const R = Labs.refs, st = Live.state;
  if (!R.trial) return;
  R.bar.style.width = (st.trial ? st.trial.progress * 100 : 0).toFixed(0) + '%';
  R.trial.textContent = st.trial ? `${st.trial.label} — ${(st.trial.progress * 100).toFixed(0)}%` : 'idle';
  R.flies.innerHTML = SLOTS.map(s => {
    const fs = st.flies[s];
    return `<div>${esc(flyName(s))}: (${fs.fly.x.toFixed(0)}, ${fs.fly.y.toFixed(0)}) · ${fs.fly.caught === false ? 'free' : 'caught'} · ${drugList(fs)}</div>`;
  }).join('');
}
Live.on('result', showBehaviourResult);

/* ================= 6b. SURGEON ENGINE + REPORT ================= */
const SURG = { log: [] };  // heal entries, read by the report
function meanSD(vs) {
  const m = vs.reduce((a, b) => a + b, 0) / vs.length;
  const sd = Math.sqrt(vs.reduce((a, b) => a + (b - m) * (b - m), 0) / vs.length);
  return [m, sd];
}
/* surgeon sampling engine removed; live runs use experiment cards (lab1runLive) */
function healBrain() {
  if (Labs.current !== 'computational') return;
  Live.send({ cmd: 'restore', target: 'treatment' });
  SURG.log.push({ title: 'Heal (restore)', desc: 'pristine wiring + default params on the treatment jar', ops: [{ cmd: 'restore', target: 'treatment' }], before: null, after: null, t: new Date().toISOString() });
  const prog = document.getElementById('sProg');
  if (prog) prog.textContent = 'Heal sent: wiring and params restoring…';
  const rb = document.getElementById('repBtn');
  if (rb) rb.disabled = false;
}
/* trial-based P7 probe + old cross loader removed; P7 and swap run as experiment screens */
const REPORT_CITES = [
  ['Suh et al. 2004 — CO₂ sensed by Gr63a (ab1C) → V glomerulus, drives aversion', 'unverified'],
  ['Hallem & Carlson 2006 — fruit-ester glomeruli incl. the banana set', 'cited in sim code'],
  ['Gaudry et al. 2013 — ORN same-side bias ×1.3', 'cited in sim code'],
  ['Shiu et al. 2024 — LIF dynamics + transmitter sign convention', 'cited in sim code'],
  ['Dorkenwald / Schlegel / Eckstein et al. 2024 — FlyWire v783 + transmitters', 'cited in sim code'],
  ['Cell 2026 — male CNS: 262 sex-specific + 114 dimorphic cell types', 'unverified'],
];
function downloadReport() {
  const L = [`# Fly Study lab report — computational neuroscience`, ``, `Generated ${new Date().toISOString()} on this page.`, ``];
  L.push(`## Experiments (one at a time)`, ``);
  const vl = (e, r, s) => { const v = lab1verdictline(e, r, s); return v ? ` ${v}.` : ''; };
  let any = false;
  for (const e of EXPS) {
    const live = LAB1.live[e.id], shown = LAB1.shown[e.id], rec = LAB1.recs[e.id];
    const p = LAB1.pred[e.id];
    L.push(`### ${e.num} · ${e.title}`, ``, `Q: ${e.question}`, ``,
      `Prediction: ${p ? `${p} (${predWord(p)})` : 'none picked'}.`, ``);
    if (live) {
      any = true;
      L.push(`Run live by you.${vl(e, live, 'live')}`, ``, `Seeds: ${lab1seedsHTML(e, live, 'live')}. Record ${live.id || '?'}.`, ``);
    } else if (shown) {
      any = true;
      L.push(`Shown from ${shown.source === 'live' ? 'a live run' : 'the recording'}.${vl(e, shown.data.rec, shown.source)}`, ``,
        `Seeds: ${lab1seedsHTML(e, shown.data.rec, shown.source)}.`, ``);
    } else if (rec && rec.ok) L.push(`Recorded (not shown yet).`, ``);
    else L.push(`Planned — not yet run.`, ``);
  }
  for (const e of SURG.log) {
    if (!e.title || !e.title.startsWith('Heal')) continue;
    any = true;
    L.push(`### ${e.title}`, ``, `${e.desc}`, ``);
  }
  if (!any) L.push(`No procedures run yet — run anything above, then download again.`, ``);
  /* legacy trial-P7 block removed; P7 runs as experiment 7 */
  L.push(`## How to read this`, ``,
    `Each experiment compares a treatment box against a control box over repeated runs (mean ± wobble, individual runs on screen). `,
    `Verdicts come from the lab's own rule in experiments.py. Your prediction is logged before each run. Nothing here is predicted or filled in.`, ``,
    `## Citations`, ``);
  for (const [c, v] of REPORT_CITES) L.push(`- ${c} [${v}]`);
  L.push(``);
  const blob = new Blob([L.join('\n')], { type: 'text/markdown' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'fly-study-report.md';
  document.body.appendChild(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 2000);
}

/* ================= 7. REGISTRY, NAV, MINI, BOOT ================= */
const LABS = [
  { id: 'computational', name: 'Computational neuroscience', render: renderComputational, tick: tickComputational },
  { id: 'comparative', name: 'Comparative neuroscience', render: renderComparative, tick: tickComparative },
  { id: 'connectomics', name: 'Connectomics / neuroanatomy', render: renderConnectomics, tick: tickConnectomics },
  { id: 'neuroinfo', name: 'Neuroinformatics', render: renderNeuroinfo, tick: tickNeuroinfo },
  { id: 'ephys', name: 'Electrophysiology', render: renderEphys, tick: tickEphys },
  { id: 'pharma', name: 'Neuropharmacology', render: renderPharma, tick: tickPharma },
  { id: 'pk', name: 'Pharmacokinetics', render: renderPK, tick: tickPK },
  { id: 'circuits', name: 'Circuit & systems', render: renderCircuits, tick: tickCircuits },
  { id: 'behaviour', name: 'Behaviour & ethology', render: renderBehaviour, tick: tickBehaviour },
];
const cssVar = n => getComputedStyle(document.documentElement).getPropertyValue(n).trim() || '#888';
function initNav() {
  Feather.topbar(document.getElementById('shellBar'), { brand: 'Fly Study', sub: 'computational neuroscience', base: './', current: 'study' });
  Feather.init();
}
const mini = document.getElementById('mini');
function tickMini() {
  const st = Live.state;
  if (!st) { mini.innerHTML = '<p class="note">waiting for lab…</p>'; return; }
  mini.innerHTML = `<div class="kv"><b>step ${st.t}</b> · ${st.sps}/s · focus ${esc(flyName(st.focus))}</div>
    <div class="kv">${st.trial ? `trial ${esc(st.trial.label)} ${(st.trial.progress * 100).toFixed(0)}%` : 'no trial'}</div>
    <div class="kv">${st.experiment ? `exp ${esc(st.experiment.label)} ${(st.experiment.progress * 100).toFixed(0)}%` : 'no experiment'}</div>
    <div class="kv" style="margin-top:6px">${SLOTS.map(s => {
      const fs = st.flies[s];
      return `<div>${esc(flyName(s))}: ${((fs.rates.all || 0) * st.sps).toFixed(1)}/s · ${drugList(fs)}</div>`;
    }).join('')}</div>`;
}
let lastT = performance.now(), tickAcc = 0;
function frame(now) {
  const dt = Math.min(0.05, (now - lastT) / 1000);
  lastT = now;
  try {
    dock.update(Live.state, now / 1000);
    dock.render();
    tickAcc += dt;
    if (tickAcc > 0.12) {
      tickAcc = 0;
      if (Live.state) pkSample(Live.state);
      Labs.tick();
      tickMini();
    }
  } catch (e) { showErr('frame: ' + e.message); }
  requestAnimationFrame(frame);
}
initDock();
initNav();
Labs.open('computational');
Live.connect();
Live.on('hello', () => Labs.open(Labs.current || 'computational'));
requestAnimationFrame(frame);
