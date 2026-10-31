// Fly Atlas: filterable library + 3D point-cloud detail view.
const HUBS = ["all", "optic", "VNC", "lateral", "olfactory", "GNG", "CX", "celltypes", "subsets", "unassigned", "nowire"];
const SEXES = [["both", "F + M"], ["F", "Female"], ["M", "Male"]];
const state = { q: "", hub: "all", level: "all", sex: "both", sort: "syn" };
let DATA = [];
const PATH = { data: [], cent: {} };  // pathways.json + per-region centroids
const byName = n => DATA.find(e => e.name === n);
// Full neuropil names (Ito et al. 2014 brain nomenclature; Court et al. 2020
// VNC nomenclature). Plain-word names need no entry and display as-is.
const EXP = {
  CX: "Central complex", GNG: "Gnathal ganglia", VNC: "Ventral nerve cord",
  FB: "Fan-shaped body", EB: "Ellipsoid body", PB: "Protocerebral bridge",
  ATL: "Antler", NO: "Noduli", GA: "Gall", BU: "Bulb",
  VES: "Vest", FLA: "Flange", AMMC: "Antennal mechanosensory and motor center",
  PRW: "Prow", SAD: "Saddle", EPA: "Epaulette", GOR: "Gorget",
  MesoNm: "Mesothoracic neuromere", ProNm: "Prothoracic neuromere",
  MetaNm: "Metathoracic neuromere", IntTct: "Intermediate tectulum",
  HTct: "Haltere tectulum", WTct: "Wing tectulum",
  AMNp: "Accessory mesothoracic neuropil", NTct: "Neck tectulum",
  LTct: "Lower tectulum", mVAC: "Medial ventral association center", Ov: "Ovoid",
  AVLP: "Anterior ventrolateral protocerebrum", SPS: "Superior posterior slope",
  IPS: "Inferior posterior slope", PLP: "Posterior lateral protocerebrum",
  LAL: "Lateral accessory lobe", SMP: "Superior medial protocerebrum",
  CRE: "Crepine", ICL: "Inferior clamp", PVLP: "Posterior ventrolateral protocerebrum",
  WED: "Wedge", IB: "Inframedial bridge", SLP: "Superior lateral protocerebrum",
  SCL: "Superior clamp", AOTU: "Anterior optic tubercle",
  SIP: "Superior intermediate protocerebrum", CAN: "Cantle",
  AL: "Antennal lobe", LH: "Lateral horn", MB_CA: "Mushroom body calyx",
  MB_VL: "Mushroom body vertical lobe", MB_PED: "Mushroom body pedunculus",
  MB_ML: "Mushroom body medial lobe",
  ME: "Medulla", LO: "Lobula", LOP: "Lobula plate", AME: "Accessory medulla",
  LA: "Lamina",
  CB_UNASGD: "Central brain (unassigned)", OL_UNASGD: "Optic lobe (unassigned)",
  VNC_UNASGD: "VNC (unassigned)",
};
const dispName = e => {
  const m = e.name.match(/^(.+)_([LR])$/);
  if (m && EXP[m[1]]) return `${m[1]} (${m[2] === "L" ? "left" : "right"}) - ${EXP[m[1]]}`;
  return EXP[e.name] ? `${e.name} - ${EXP[e.name]}` : e.name;
};
// Null-safe DOM helpers: a stale cached page must degrade, never whitescreen.
const $ = id => document.getElementById(id);
const on = (id, ev, fn) => $(id)?.addEventListener(ev, fn);
const click = (id, fn) => { const e = $(id); if (e) e.onclick = fn; };

const fmt = n => n == null ? "—" : n.toLocaleString("en-US");
const hay = e => `${e.name} ${EXP[e.name] || ""} ${e.hub} ${e.cls || ""} ${e.sub || ""} ${e.func} ${e.desc}`.toLowerCase();
function sortKey(e) {
  switch (state.sort) {
    case "fsyn": return -(e.fs || 0);
    case "msyn": return -(e.ms || 0);
    case "neu": return -((e.fn || 0) + (e.mn || 0));
    case "name": return e.name;
    case "ratio": return typeof e.ratio === "number" ? -e.ratio : 1e9;
    default: return -((e.fs || 0) + (e.ms || 0));
  }
}
const filtered = () => DATA
  .filter(e => state.hub === "all" || e.hub === state.hub)
  .filter(e => state.level === "all" || e.level === state.level)
  .filter(e => !state.q || hay(e).includes(state.q))
  .sort((a, b) => { const ka = sortKey(a), kb = sortKey(b); return ka < kb ? -1 : ka > kb ? 1 : 0; });

// ---------- library view ----------
function statMini(e) {
  const r = [];
  if (e.fn != null && state.sex !== "M") r.push(`<dt>F neurons</dt><dd>${fmt(e.fn)}</dd>`);
  if (e.mn != null && state.sex !== "F") r.push(`<dt>M neurons</dt><dd>${fmt(e.mn)}</dd>`);
  if (state.sex !== "M") r.push(`<dt>F synapses</dt><dd>${fmt(e.fs)}</dd>`);
  if (state.sex !== "F") r.push(`<dt>M synapses</dt><dd>${fmt(e.ms)}</dd>`);
  r.push(`<dt>M/F ratio</dt><dd>${e.ratio == null ? "—" : e.ratio}</dd>`);
  return r.join("");
}
function renderLibrary() {
  const list = filtered();
  const grid = $("grid");
  if (!grid) return;
  grid.innerHTML = list.length ? list.map(e => `
    <article class="card" data-name="${e.name}" tabindex="0" role="link" aria-label="${dispName(e)}, open detail view">
      <img src="${e.img}" alt="" loading="lazy" decoding="async">
      <div class="body">
        <div class="hd"><h2>${dispName(e)}</h2><span class="tag">${e.level === "hub" ? e.hub + " hub" : e.hub}</span></div>
        <p class="func">${e.func}</p>
        <dl class="stats">${statMini(e)}</dl>
      </div>
    </article>`).join("")
    : `<p class="empty">No regions match these filters.</p>`;
  const count = $("count");
  if (count) count.textContent = `${list.length} of ${DATA.length} entries`;
  document.querySelectorAll("#hubs button").forEach(b => b.classList.toggle("on", b.dataset.v === state.hub));
  document.querySelectorAll("#sex button").forEach(b => b.classList.toggle("on", b.dataset.v === state.sex));
}

// ---------- 3D stage (lazy three.js) ----------
let T = null; // three module
const stage = {
  on: false, renderer: null, scene: null, camera: null, soma: null, lit: null,
  theta: 0.6, phi: 1.25, dist: 3.4, home: { theta: 0.6, phi: 1.25, dist: 3.4 },
  target: null, targetGoal: null, vel: { x: 0, y: 0 }, lastT: 0, idle: 0,
  closeup: false, centroid: null, raf: 0,
};
const SOMAC = {
  day: { optic: 0x2f5da3, central: 0x8a6d1a, sensory: 0x1e7d4f, visual_projection: 0x6a4fb3, descending: 0xb03a3a, ascending: 0xc26a8a, visual_centrifugal: 0x2a7f9e, motor: 0x555555, endocrine: 0x888888 },
  dusk: { optic: 0x4c9be8, central: 0xf2b134, sensory: 0x3ec28f, visual_projection: 0x9b7bea, descending: 0xef5b5b, ascending: 0xff8fb1, visual_centrifugal: 0x7fd4e8, motor: 0xffffff, endocrine: 0xc0c0c0 },
};
const HUBC = {
  day: { optic: 0x1d4ed8, VNC: 0xdc2626, lateral: 0x92400e, olfactory: 0x15803d, GNG: 0xc2410c, CX: 0x0f766e },
  dusk: { optic: 0x7ab8ff, VNC: 0xff7a7a, lateral: 0xffd23f, olfactory: 0x7dffb6, GNG: 0xffb066, CX: 0x5ef2ff },
};
const theme = () => $("hero")?.dataset.light ?? "day";
async function buf(path) { return new Float32Array(await (await fetch(path)).arrayBuffer()); }
async function u8(path) { return new Uint8Array(await (await fetch(path)).arrayBuffer()); }

async function initGL() {
  T = await import("three");
  const canvas = document.getElementById("gl");
  stage.renderer = new T.WebGLRenderer({ canvas, antialias: true, alpha: true });
  stage.renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  stage.scene = new T.Scene();
  stage.camera = new T.PerspectiveCamera(40, 1, 0.1, 100);
  stage.target = new T.Vector3(0, 0, 0);
  stage.targetGoal = new T.Vector3(0, 0, 0);
  // somas (flip y: FAFB +y runs down, three.js +y runs up)
  const P = await buf("points/somas.bin"), C = await u8("points/classes.bin");
  const idx = JSON.parse(await (await fetch("points/index.json")).text());
  for (let i = 1; i < P.length; i += 3) P[i] = -P[i];
  const g = new T.BufferGeometry();
  g.setAttribute("position", new T.BufferAttribute(P, 3));
  const col = new Float32Array(P.length), tmp = new T.Color();
  const cls = C, names = idx.classes;
  stage.paintSomas = () => {
    const pal = SOMAC[theme()];
    for (let i = 0; i < cls.length; i++) {
      tmp.setHex(pal[names[cls[i]]] ?? 0x888888);
      col[i * 3] = tmp.r; col[i * 3 + 1] = tmp.g; col[i * 3 + 2] = tmp.b;
    }
    g.attributes.color.needsUpdate = true;
  };
  g.setAttribute("color", new T.BufferAttribute(col, 3));
  stage.paintSomas();
  stage.soma = new T.Points(g, new T.PointsMaterial({ size: 0.012, vertexColors: true, transparent: true, opacity: 0.55, sizeAttenuation: true, depthWrite: false }));
  stage.soma.frustumCulled = false;
  stage.scene.add(stage.soma);
  stage.lit = new T.Points(new T.BufferGeometry(), new T.PointsMaterial({ size: 0.02, transparent: true, opacity: 0.95, sizeAttenuation: true, depthWrite: false }));
  stage.lit.frustumCulled = false;
  stage.lit.renderOrder = 2;
  stage.scene.add(stage.lit);
  stage.on = true;
  new ResizeObserver(fitGL).observe(document.getElementById("stage"));
  fitGL();
  stage.lastT = performance.now();
  const loop = t => {
    if (!stage.on) return;
    const dt = Math.min(0.05, (t - stage.lastT) / 1000);
    stage.lastT = t;
    step(dt, t / 1000);
    stage.renderer.render(stage.scene, stage.camera);
    stage.raf = requestAnimationFrame(loop);
  };
  stage.raf = requestAnimationFrame(loop);
}
function fitGL() {
  if (!stage.on) return;
  const el = document.getElementById("stage");
  const w = el.clientWidth, h = el.clientHeight;
  if (!w || !h) return;
  stage.renderer.setSize(w, h, false);
  stage.camera.aspect = w / h;
  stage.camera.updateProjectionMatrix();
}
function paintLit(hub) {
  if (stage.lit) stage.lit.material.color.setHex(HUBC[theme()][hub] ?? 0x4d5a3d);
}
async function setLit(entry) {
  // regions to light: neuropil itself, hub merges its brain members
  let regs = entry.level === "hub"
    ? DATA.filter(e => e.hub === entry.hub && e.level === "neuropil").map(e => e.name)
    : [entry.name];
  regs = regs.filter(r => !["VNC", "unassigned", "nowire"].includes(byName(r)?.hub));
  const per = Math.max(1500, Math.floor(30000 / Math.max(1, regs.length)));
  const parts = [];
  for (const r of regs) {
    try {
      let a = await buf(`points/${r}.bin`);
      if (a.length / 3 > per) {
        const n = a.length / 3, keep = new Float32Array(per * 3), step = n / per;
        for (let i = 0; i < per; i++) keep.set(a.subarray(Math.floor(i * step) * 3, Math.floor(i * step) * 3 + 3), i * 3);
        a = keep;
      }
      parts.push(a);
    } catch { /* region file absent: skip */ }
  }
  const n = parts.reduce((s, a) => s + a.length, 0);
  const merged = new Float32Array(n);
  let o = 0;
  for (const a of parts) { merged.set(a, o); o += a.length; }
  for (let i = 1; i < merged.length; i += 3) merged[i] = -merged[i];
  stage.lit.geometry.dispose();
  stage.lit.geometry = new T.BufferGeometry();
  stage.lit.geometry.setAttribute("position", new T.BufferAttribute(merged, 3));
  // centroid for close-up
  const c = new T.Vector3();
  for (let i = 0; i < merged.length; i += 3) c.x += merged[i], c.y += merged[i + 1], c.z += merged[i + 2];
  c.multiplyScalar(1 / Math.max(1, merged.length / 3));
  stage.centroid = c;
  paintLit(entry.hub);
  paintPaths();
}
async function paintPaths() {
  if (stage.paths) { stage.scene.remove(stage.paths); stage.paths.geometry.dispose(); stage.paths = null; }
  const on = PATH.show && PATH.data.length && !fallbackMode;
  if (!on) return;
  const top = PATH.data.filter(e => e.a !== e.b).slice(0, 30);
  const names = [...new Set(top.flatMap(e => [e.a, e.b]))].filter(n => !(n in PATH.cent));
  await Promise.all(names.map(async n => {
    try {
      const a = await buf(`points/${n}.bin`);
      let x = 0, y = 0, z = 0;
      for (let i = 0; i < a.length; i += 3) { x += a[i]; y -= a[i + 1]; z += a[i + 2]; }
      const k = Math.max(1, a.length / 3);
      PATH.cent[n] = [x / k, y / k, z / k];
    } catch { PATH.cent[n] = null; }
  }));
  const pos = [];
  for (const e of top) {
    const A = PATH.cent[e.a], B = PATH.cent[e.b];
    if (A && B) pos.push(...A, ...B);
  }
  if (!pos.length || !PATH.show || fallbackMode) return;
  const g = new T.BufferGeometry();
  g.setAttribute("position", new T.BufferAttribute(new Float32Array(pos), 3));
  stage.paths = new T.LineSegments(g, new T.LineBasicMaterial({ color: 0x0f766e, transparent: true, opacity: 0.45 }));
  stage.paths.userData.lit = true;
  stage.scene.add(stage.paths);
}
function step(dt, t) {
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  stage.idle += dt;
  if (stage.idle > 2 && !reduced && !stage.closeup) stage.theta += dt * 0.15 * Math.min(1, (stage.idle - 2) / 1.5);
  stage.theta += stage.vel.x * dt; stage.phi += stage.vel.y * dt;
  stage.vel.x *= Math.exp(-dt * 4.5); stage.vel.y *= Math.exp(-dt * 4.5);
  stage.phi = Math.min(Math.PI - 0.15, Math.max(0.15, stage.phi));
  stage.target.lerp(stage.targetGoal, 1 - Math.exp(-dt * 6));
  const sp = Math.sin(stage.phi), y = Math.cos(stage.phi);
  stage.camera.position.set(
    stage.target.x + stage.dist * sp * Math.sin(stage.theta),
    stage.target.y + stage.dist * y,
    stage.target.z + stage.dist * sp * Math.cos(stage.theta));
  stage.camera.lookAt(stage.target);
}
function zoomBy(f) {
  stage.dist = Math.min(8.5, Math.max(0.85, stage.dist / f));
  stage.closeup = stage.dist < 2.2;
  document.getElementById("closeup")?.setAttribute("aria-pressed", String(stage.closeup));
  stage.idle = 0;
}
function resetView() {
  Object.assign(stage, { theta: stage.home.theta, phi: stage.home.phi, dist: stage.home.dist });
  stage.targetGoal.set(0, 0, 0);
  stage.closeup = false;
  document.getElementById("closeup")?.setAttribute("aria-pressed", "false");
  stage.idle = 0;
}
function bindStage() {
  const el = $("stage");
  if (!el) return;
  let drag = null, pinch = 0, tap = null, tipTimer = 0;
  const tip = $("partTip");
  const ray = { c: null };
  const hideTip = () => { if (tip) tip.hidden = true; };
  // Hover the lit region cloud (the part on the card), not the dim somas.
  function partHit(cx, cy) {
    if (!stage.on || fallbackMode || !current || !T) return null;
    const r = el.getBoundingClientRect();
    if (!ray.c) { ray.c = new T.Raycaster(); ray.c.params.Points.threshold = 0.04; }
    ray.c.setFromCamera(new T.Vector2(((cx - r.left) / r.width) * 2 - 1, -((cy - r.top) / r.height) * 2 + 1), stage.camera);
    return ray.c.intersectObject(stage.lit, false).length ? current : null;
  }
  function moveTip(cx, cy, e) {
    if (!tip) return;
    const r = el.getBoundingClientRect();
    tip.textContent = `${dispName(e)} · ${e.func}`;
    tip.hidden = false;
    tip.style.left = "0px";
    tip.style.top = `${cy - r.top}px`;
    const w = tip.offsetWidth;
    let x = cx - r.left + 14;
    if (x + w > r.width - 6) x = cx - r.left - 14 - w;
    tip.style.left = `${x}px`;
  }
  el.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    if (!e.target.closest("button")) tap = { x: e.clientX, y: e.clientY, t: performance.now() };
    else tap = null;
    el.setPointerCapture(e.pointerId);
    drag = { x: e.clientX, y: e.clientY, t: performance.now() };
    el.classList.add("grabbing");
    hideTip();
  });
  el.addEventListener("pointermove", e => {
    if (!drag) return;
    const k = 0.005;
    const dx = (e.clientX - drag.x) * k, dy = (e.clientY - drag.y) * k;
    stage.theta -= dx; stage.phi -= dy;
    stage.vel.x = -dx * 60; stage.vel.y = -dy * 60;
    drag = { x: e.clientX, y: e.clientY };
    stage.idle = 0;
    document.getElementById("hint").classList.add("gone");
  });
  const up = e => {
    drag = null; el.classList.remove("grabbing");
    // Tap (no hover on touch): name the part briefly.
    if (tap && e && e.type === "pointerup" && e.clientX !== undefined &&
        Math.hypot(e.clientX - tap.x, e.clientY - tap.y) < 8 &&
        performance.now() - tap.t < 350) {
      const hit = partHit(e.clientX, e.clientY);
      if (hit) {
        moveTip(e.clientX, e.clientY, hit);
        clearTimeout(tipTimer);
        tipTimer = setTimeout(hideTip, 1600);
      }
    }
    tap = null;
  };
  el.addEventListener("pointerup", up);
  el.addEventListener("pointercancel", up);
  el.addEventListener("pointermove", e => {
    if (e.pointerType === "touch") return;
    if (drag) { hideTip(); return; }
    const hit = partHit(e.clientX, e.clientY);
    if (hit) moveTip(e.clientX, e.clientY, hit);
    else hideTip();
  });
  el.addEventListener("pointerleave", e => { if (e.pointerType !== "touch") hideTip(); });
  el.addEventListener("wheel", e => {
    e.preventDefault();
    hideTip();
    zoomBy(Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0016)));
    document.getElementById("hint").classList.add("gone");
  }, { passive: false });
  el.addEventListener("touchmove", e => {
    if (e.touches.length === 2) {
      e.preventDefault();
      const d = Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);
      if (pinch) zoomBy(d / pinch);
      pinch = d;
    }
  }, { passive: false });
  el.addEventListener("touchend", () => pinch = 0);
  el.addEventListener("dblclick", () => { hideTip(); resetView(); });
  el.addEventListener("keydown", e => {
    const rot = 0.18;
    if (e.key === "ArrowLeft") stage.theta += rot;
    else if (e.key === "ArrowRight") stage.theta -= rot;
    else if (e.key === "ArrowUp") stage.phi -= rot;
    else if (e.key === "ArrowDown") stage.phi += rot;
    else if (e.key === "+" || e.key === "=") zoomBy(1.2);
    else if (e.key === "-" || e.key === "_") zoomBy(1 / 1.2);
    else if (e.key === "0") resetView();
    else return;
    e.preventDefault(); stage.idle = 0; hideTip();
  });
  document.getElementById("zin").onclick = () => zoomBy(1.35);
  document.getElementById("zout").onclick = () => zoomBy(1 / 1.35);
  document.getElementById("zreset").onclick = resetView;
}

// ---------- detail view ----------
let current = null, sibs = [];
const pillFor = e => e.level === "hub"
  ? [e.hub + " hub", `${DATA.filter(x => x.hub === e.hub && x.level === "neuropil").length} regions`, e.func]
  : e.level === "celltype"
  ? [e.cls || "unclassified", e.flow || "", e.func].filter(Boolean)
  : [e.hub, e.level, e.func];
function traitRow(k, v) { return `<div class="trait"><span class="k">${k}</span><span class="v">${v}</span></div>`; }
function renderDetail(e) {
  current = e;
  const sibEntries = e.level === "celltype" && e.cls
    ? DATA.filter(x => x.cls === e.cls)
    : e.level === "side"
    ? DATA.filter(x => x.name === e.base || x.name === e.base + "_L" || x.name === e.base + "_R")
    : DATA.filter(x => x.hub === e.hub);
  sibs = sibEntries.map(x => x.name);
  document.getElementById("sibNav").innerHTML =
    `<span class="cap">${e.level === "celltype" ? e.cls : e.level === "side" ? e.base : e.hub} · ${sibEntries.length}</span>` + sibEntries.map(x =>
      `<button data-n="${x.name}" class="${x.name === e.name ? "on" : ""}">${dispName(x)}<small>${x.func}</small></button>`).join("");
  const neurons = e.fn != null || e.mn != null;
  document.getElementById("detail").innerHTML = `
    <span class="eyebrow">${e.hub}${e.level === "hub" ? " · hub" : ""}</span>
    <h1>${dispName(e)}</h1>
    <div class="pills">${pillFor(e).map(p => `<span>${p}</span>`).join("")}</div>
    <p class="desc">${e.desc}</p>
    <h2>Key numbers</h2>
    <div class="traits">
      ${neurons ? traitRow("Female neurons", fmt(e.fn)) + traitRow("Male neurons", fmt(e.mn)) : ""}
      ${traitRow("Female synapses", fmt(e.fs))}${traitRow("Male synapses", fmt(e.ms))}
      ${traitRow("M/F ratio", e.ratio == null ? "—" : e.ratio)}
    </div>
    <button class="closeup" id="closeup" aria-pressed="false">
      <span><span class="t">Close-up Detail</span><span class="d" style="display:block">${e.level === "hub" ? "Zoom to every region in this hub" : "Zoom to this region in 3D"}</span></span>
      <svg class="ic"><use href="#i-chev"/></svg>
    </button>
    <button class="closeup" id="pathways" aria-pressed="false">
      <span><span class="t">Pathways</span><span class="d" style="display:block">Draw the 30 strongest between-region wires</span></span>
      <svg class="ic"><use href="#i-chev"/></svg>
    </button>
    <a class="asklink" href="../ask/?q=${encodeURIComponent("what does " + e.name + " do (" + e.func + ")")}">Ask about this region</a>`;
  document.getElementById("caption").textContent = e.func;
  document.getElementById("closeup").onclick = () => {
    if (!stage.on || fallbackMode) return;
    if (stage.closeup) { resetView(); return; }
    if (stage.centroid) stage.targetGoal.copy(stage.centroid);
    stage.dist = 1.4;
    stage.closeup = true;
    document.getElementById("closeup").setAttribute("aria-pressed", "true");
    stage.idle = 0;
  };
  document.getElementById("pathways").onclick = () => {
    if (!stage.on || fallbackMode) return;
    PATH.show = !PATH.show;
    document.getElementById("pathways").setAttribute("aria-pressed", String(!!PATH.show));
    stage.idle = 0;
    paintPaths();
  };
}
let fallbackMode = false;
async function openDetail(name, push = true) {
  const e = byName(name);
  if (!e || !$("detailView")) return;
  if (current && current.name === name) return; // hash echo of our own navigation: skip double load
  fallbackMode = ["VNC", "unassigned", "nowire", "celltypes", "subsets"].includes(e.hub) || e.level === "side";
  if ($("libraryView")) $("libraryView").hidden = true;
  $("detailView").hidden = false;
  if ($("partTip")) $("partTip").hidden = true;
  renderDetail(e);
  document.getElementById("fallback").hidden = true;
  document.getElementById("gl").style.display = fallbackMode ? "none" : "block";
  document.getElementById("loader").hidden = true;
  if (fallbackMode) {
    const fb = document.getElementById("fallback");
    fb.src = e.img; fb.alt = `${e.name} render`;
    fb.hidden = false;
    document.getElementById("caption").textContent =
      e.level === "side" ? "Side counts — render shows the whole region" :
      e.level === "subset" ? "Male-only subset file — counts only, no 3D position" :
      e.hub === "VNC" ? `${e.func} — schematic drawing, no cord coordinates` :
      e.hub === "nowire" ? "Absent from the wiring files — nothing to show" :
      e.hub === "celltypes" ? "Schematic card — per-type 3D positions not built yet" :
      "Scattered contacts with no consensus position";
    document.getElementById("closeup").style.display = "none";
  } else {
    document.getElementById("closeup").style.display = "";
    document.getElementById("loader").hidden = false;
    if (!stage.on) await initGL();
    resetView();
    await setLit(e);
    document.getElementById("loader").hidden = true;
    fitGL();
  }
  if (push) location.hash = "/" + encodeURIComponent(name);
  window.scrollTo(0, 0);
}
function closeDetail(push = true) {
  if ($("detailView")) $("detailView").hidden = true;
  if ($("libraryView")) $("libraryView").hidden = false;
  if ($("partTip")) $("partTip").hidden = true;
  current = null;
  if (push) location.hash = "";
}
function stepDetail(d) {
  if (!current) return;
  const i = sibs.indexOf(current.name);
  openDetail(sibs[(i + d + sibs.length) % sibs.length]);
}

// ---------- boot ----------
Feather.topbar(document.querySelector("#libraryView .top"), { brand: "Fly Atlas", sub: "Region library", base: "../", current: "atlas", extras: [".topsearch", "#filterBtn", "#lightBtn"], light: false });
Feather.init({ onLight: () => { if (stage.on) { stage.paintSomas(); if (current) paintLit(current.hub); } } });
Promise.all([fetch("data.json").then(r => r.json()), fetch("celltypes.json").then(r => r.json()).catch(() => []), fetch("sides.json").then(r => r.json()).catch(() => []), fetch("subsets.json").then(r => r.json()).catch(() => []), fetch("pathways.json").then(r => r.json()).catch(() => [])]).then(([d, ct, sd, ss, pw]) => {
  DATA = d.concat(ct, sd, ss);
  PATH.data = pw;
  const f = d.filter(e => e.level === "hub").reduce((s, e) => s + (e.fs || 0), 0);
  const m = d.filter(e => e.level === "hub").reduce((s, e) => s + (e.ms || 0), 0);
  const sub = document.querySelector("#libraryView .shell-sub");
  if (sub) sub.textContent =
    `${d.length} hubs and neuropils + ${ct.length} cell types + ${sd.length} sides + ${ss.length} subsets · ${(f / 1e6).toFixed(1)}M female + ${(m / 1e6).toFixed(1)}M male synapses`;
  if ($("hubs")) $("hubs").innerHTML =
    HUBS.map(h => `<button data-v="${h}">${h === "all" ? "All" : h}</button>`).join("");
  if ($("sex")) $("sex").innerHTML =
    SEXES.map(([v, l]) => `<button data-v="${v}">${l}</button>`).join("");
  on("q", "input", e => { state.q = e.target.value.toLowerCase(); renderLibrary(); });
  on("level", "change", e => { state.level = e.target.value; renderLibrary(); });
  on("sort", "change", e => { state.sort = e.target.value; renderLibrary(); });
  on("hubs", "click", e => { if (e.target.dataset.v) { state.hub = e.target.dataset.v; renderLibrary(); } });
  on("sex", "click", e => { if (e.target.dataset.v) { state.sex = e.target.dataset.v; renderLibrary(); } });
  on("grid", "click", e => {
    const c = e.target.closest(".card");
    if (c) openDetail(c.dataset.name);
  });
  on("grid", "keydown", e => {
    const c = e.target.closest(".card");
    if (c && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); openDetail(c.dataset.name); }
  });
  click("filterBtn", () => $("filterPanel")?.classList.toggle("hidden"));
  if (matchMedia("(max-width: 760px)").matches) $("filterPanel")?.classList.add("hidden");
  click("backBtn", () => closeDetail());
  click("prevBtn", () => stepDetail(-1));
  click("nextBtn", () => stepDetail(1));
  on("sibNav", "click", e => {
    const b = e.target.closest("button");
    if (b) openDetail(b.dataset.n);
  });
  bindStage();
  renderLibrary();
  const open = () => {
    const m = location.hash.match(/^#\/(.+)$/);
    if (m && byName(decodeURIComponent(m[1]))) openDetail(decodeURIComponent(m[1]), false);
    else if (!location.hash) closeDetail(false);
  };
  addEventListener("hashchange", open);
  open();
});
