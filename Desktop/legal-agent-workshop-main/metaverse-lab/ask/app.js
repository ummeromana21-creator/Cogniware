/* Ask the flies: question -> card -> control/treatment run -> match vs expected. */
const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
let K = null;      // knowledge.json
let CARD = null;   // compiled card + parse
let RUN = null;    // live run state
let GENES = null;  // anatomy/genes.json (fly gene catalog)
let FOODS = null;  // foods.json (food -> lab compounds, built from FooDB + Duke's)
let AFFINITY = null;  // affinity.json (substance -> target actions, built from GtoPdb)
let BRIDGE = null;  // drug_targets.json bridge (substance -> mammalian target -> fly gene -> expression)

const IMG = {  // sim stage -> atlas render (rows from fly-brain-regions.xlsx)
  'antennal lobe PNs': '../library/img/row42_AL.jpg',
  'lateral horn': '../library/img/row43_LH.jpg',
  'mushroom body': '../library/img/row44_MB_CA.jpg',
  'central complex': '../library/img/row57_CX.jpg',
  'nerve cord': '../library/img/row10_VNC.jpg',
};
const BINS = {  // sim stage -> atlas region bins (also Fly Atlas entry names)
  'antennal lobe PNs': ['AL'], 'lateral horn': ['LH'],
  'mushroom body': ['MB_CA', 'MB_ML', 'MB_PED', 'MB_VL'],
  'central complex': ['EB', 'FB', 'PB', 'NO', 'LAL'],
  'optic lobes': ['ME', 'LO', 'LOP', 'AME', 'LA'],
};
const PRED_WORD = { higher: 'up', lower: 'down', different: 'different' };
const FLIES = [['Maxime (female)', 'control:banc', 'treatment:banc'], ['Hagrid (male)', 'control:malecns', 'treatment:malecns']];

/* ---- lab link ---- */
const Live = {
  ws: null, idSeq: 0, pending: {}, state: null, connected: false,
  connect() {
    setStatus(false, 'connecting…');
    let ws;
    try { ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.hostname || 'localhost'}:8765`); } catch { return void setTimeout(() => this.connect(), 2000); }
    this.ws = ws;
    ws.onopen = () => setStatus(false, this.lastState ? 'lab quiet — reconnecting…' : 'waiting for lab…');
    ws.onmessage = ev => {
      let m;
      try { m = JSON.parse(ev.data); } catch { return; }
      if (m.type === 'hello') { this.hello = m; }
      else if (m.type === 'state') { this.state = m; this.connected = true; this.lastState = Date.now(); setStatus(true, 'live'); onState(m); }
      else if (m.type === 'experiment_done' || m.type === 'experiment_cancelled') { const cb = this.pending[m.id]; if (cb) { delete this.pending[m.id]; cb(m); } }
      else if (m.type === 'ack') { const cb = this.pending[m.id]; if (cb) { delete this.pending[m.id]; cb(m); } }
    };
    ws.onclose = () => {
      this.ws = null; this.connected = false;
      setStatus(false, this.lastState ? 'lab quiet — reconnecting…' : 'offline — start the lab');
      setTimeout(() => this.connect(), 2000);
    };
    ws.onerror = () => { try { ws.close(); } catch {} };
  },
  watchdog() {
    if (!this.connected || !this.lastState) return;
    if (Date.now() - this.lastState > 6000) {
      this.connected = false;
      setStatus(false, 'lab quiet — reconnecting…');
      try { this.ws && this.ws.close(); } catch {}
    }
  },
  send(obj) {
    if (!this.connected || !this.ws) return null;
    const id = 'a' + (++this.idSeq);
    this.ws.send(JSON.stringify({ ...obj, id }));
    return id;
  },
};
function setRunEnabled(on) {
  for (const id of ['runBtn', 'runBtnM']) { const b = $(id); if (b) b.disabled = !on; }
}
function setStatus(live, text) {
  for (const id of ['status', 'statusM']) {
    const el = $(id);
    if (!el) continue;
    el.classList.toggle('live', live);
    el.textContent = (live ? '● ' : '○ ') + text;
  }
  if (CARD && CARD.feasible) setRunEnabled(live);
}
function onState(s) {
  if (!RUN) return;
  const x = s.experiment;
  if (x) $('prog').textContent = `${x.condition || ''} · repeat ${x.repeat || '?'} of ${x.n_rep || '?'} · ${Math.round(100 * (x.progress || 0))}%`;
  const f = (s.flies || {})[s.focus] || {};
  RUN.trace.push(((f.rates && f.rates.all) || 0) * (s.sps || 28));
  if (RUN.trace.length > 600) RUN.trace.shift();
  drawTrace();
  for (const slot of ['control:banc', 'control:malecns', 'treatment:banc', 'treatment:malecns']) {
    const fs = (s.flies || {})[slot];
    if (!fs || !fs.groups) continue;
    const acc = RUN.groups[slot] || (RUN.groups[slot] = {});
    for (const st of K.stages) {
      const g = (fs.groups || {})[st];
      const pair = Array.isArray(g) ? g : [0, 0];
      const v = ((pair[0] || 0) + (pair[1] || 0)) / 2 * (s.sps || 28);
      (acc[st] || (acc[st] = [])).push(v);
      if (acc[st].length > 900) acc[st].shift();
    }
  }
}
function drawTrace() {
  const cv = $('trace');
  if (!RUN || RUN.trace.length < 2) return;
  cv.style.display = 'block';
  const ctx = cv.getContext('2d'), W = cv.width, H = cv.height, tr = RUN.trace;
  const mx = Math.max(0.1, ...tr);
  const css = getComputedStyle(document.getElementById('hero'));
  ctx.fillStyle = css.getPropertyValue('--panel').trim() || '#fbf8f1'; ctx.fillRect(0, 0, W, H);
  ctx.strokeStyle = css.getPropertyValue('--teal').trim() || '#0f766e'; ctx.lineWidth = 1.5; ctx.beginPath();
  tr.forEach((v, i) => { const x = (i / (tr.length - 1)) * W, y = H - 4 - (v / mx) * (H - 24); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
  ctx.stroke();
  ctx.fillStyle = css.getPropertyValue('--muted').trim() || '#6d685d'; ctx.font = '10px sans-serif';
  ctx.fillText(`live firing, focus fly (max ${mx.toFixed(1)}/s)`, 6, 12);
}
const meanSD = vs => {
  if (!vs.length) return [0, 0];
  const m = vs.reduce((a, b) => a + b, 0) / vs.length;
  return [m, Math.sqrt(vs.reduce((a, b) => a + (b - m) * (b - m), 0) / vs.length)];
};

/* ---- compiler: question -> card ---- */
const MEASURE_KEYS = [
  ['sleep', ['sleep', 'sleeping', 'insomnia', 'awake', 'drowsy']],
  ['synchrony', ['synchrony', 'seizure', 'burst', 'epilep', 'mass firing']],
  ['banana_near', ['banana', 'find', 'forag', 'smell', 'nose', 'olfact', 'attract', 'near', 'close']],
  ['activity', ['activ', 'walk', 'move', 'locomot', 'run around', 'sedat']],
  ['tortuosity', ['wander', 'tortuos', 'meander', 'path']],
  ['mean_abs_turn', ['turn', 'steer', 'circle', 'orbit']],
  ['firing_peak', ['peak', 'max fir']],
];
function findSubstance(q) {
  let best = null;
  for (const [name, s] of Object.entries(K.substances)) {
    for (const a of [name, ...(s.aliases || [])]) {
      if (a && q.includes(a.toLowerCase()) && (!best || a.length > best.alias.length)) best = { name, alias: a };
    }
  }
  return best;
}
function findMeasure(q) {
  for (const [m, keys] of MEASURE_KEYS) {
    if (keys.some(k => q.includes(k))) return m;
  }
  return null;
}
const FOOD_STOP = new Set(['what', 'whats', 'does', 'do', 'are', 'is', 'in', 'the', 'and', 'food', 'foods', 'contain', 'contains', 'dose', 'a', 'an', 'of', 'any']);
function findFood(q) {
  if (!FOODS) return null;
  const words = q.toLowerCase().split(/[^a-z]+/).filter(w => w.length > 3 && !FOOD_STOP.has(w));
  let best = null;
  for (const name of Object.keys(FOODS)) {
    const hits = words.filter(w => name.toLowerCase().includes(w)).length;
    if (hits && (!best || hits > best.hits ||
        (hits === best.hits && FOODS[name].items.length > FOODS[best.name].items.length)))
      best = { name, hits };
  }
  return best ? best.name : null;
}
function compileFood(name) {
  const f = FOODS[name];
  const lines = f.items.map(e => {
    const s = K.substances[e.substance];
    const dose = s && s.dosable ? 'testable in the lab' : 'in our lists but not dosable';
    return `${e.substance}: ${e.amount} ${e.unit} — ${dose}`;
  });
  return { mode: 'explain', feasible: true, title: `What's in ${name}`,
    paragraphs: [
      `${name} carries ${f.items.length} lab compound${f.items.length > 1 ? 's' : ''}, ranked: ${lines.join('; ')}.`,
      f.note + '.'],
    refs: [...new Set(f.items.map(e => e.source))],
    notes: ['Doses in the lab run 0–2 dose units; food amounts are shown for ranking, not converted.'] };
}
function findGene(q) {
  if (!GENES) return null;
  const m = q.match(/\bfbgn\d+\b/);
  if (m) {
    const fb = 'FBgn' + m[0].slice(4);
    if (GENES[fb]) return fb;
  }
  if (!/(gene|ortholog|express|mutant|allele|fbgn|chromosome)/.test(q)) return null;
  let best = null;
  for (const [fb, g] of Object.entries(GENES)) {
    for (const a of [g.s, ...(g.syn || [])]) {
      if (!a || a.length < 3) continue;
      const rx = new RegExp('\\b' + a.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b');
      if (rx.test(q) && (!best || a.length > best.alias.length)) best = { fb, alias: a };
    }
  }
  return best && best.fb;
}
function compileGene(fb) {
  const g = GENES[fb];
  const paras = [
    `${g.s} (${fb})${g.n ? ' — ' + g.n : ''}. Fly gene, both sexes carry it.`,
  ];
  paras.push(g.h && g.h.length
    ? `Human lookalikes (best orthologs): ${g.h.join(', ')}. Same family, same job description — mapped by the Alliance, not guessed.`
    : 'No confident human ortholog on file.');
  paras.push(g.e && g.e.length
    ? `Seen switched on in: ${[...new Set(g.e.map(e => e.site))].join('; ')}.`
    : 'No anatomy-level expression on file.');
  const sim = [...new Set((g.e || []).flatMap(e => e.sim || []))];
  if (sim.length) paras.push(`That reaches our sim in: ${sim.join(', ')}.`);
  paras.push(g.c && g.c.length
    ? `Chemicals tied to this gene in fly papers: ${[...new Set(g.c.map(c => c.c))].join(', ')}.`
    : 'No fly chemical links on file.');
  paras.push(g.d && g.d.length
    ? `Human disease links (via the ${g.h[0] || 'human'} lookalike — inferred, not fly diagnoses): ${[...new Set(g.d.map(d => d.d))].slice(0, 6).join(', ')}.`
    : 'No disease links on file.');
  return {
    mode: 'explain', feasible: true, title: `${g.s} — fly gene`,
    paragraphs: paras,
    refs: [`FlyBase ${fb}`, 'Alliance of Genome Resources 9.2.0 (orthology, expression, disease)',
      'CTD chemical–gene rows (fly, taxon 7227)'],
  };
}
function compileCard(question) {
  const q = question.toLowerCase();
  const sub = findSubstance(q);
  if (!sub) {
    for (const u of K.untestable) {
      if (u.keys.some(k => q.includes(k))) return { feasible: false, reason: u.why, topic: true };
    }
    return { feasible: false, reason: 'no testable substance found', topic: false };
  }
  const s = K.substances[sub.name];
  if (!s.dosable) return { feasible: false, substance: sub.name, reason: s.not_modelled, confidence: s.confidence, evidence: s.evidence };
  if (!s.target || !s.behavior) return { feasible: false, substance: sub.name, reason: `${sub.name} has no target/behaviour on file — curate it under rule 1 before carding`, confidence: s.confidence, evidence: s.evidence };
  const measure = s.target, prediction = s.target_prediction;
  const secondary = Array.isArray(s.behavior) ? s.behavior : [s.behavior];
  const asked = findMeasure(q);
  const note_extra = (asked && asked !== measure && !secondary.includes(asked))
    ? `You asked about ${K.measures[asked]}; the standing rules measure ${K.measures[measure]} + ${secondary.map(b => K.measures[b]).join(' + ')} for this drug — ${K.measures[asked]} is recorded as an extra.`
    : null;
  const card = {
    title: `${sub.name} vs control (${measure} + ${secondary[0]})`,
    hypothesis: `${sub.name} ${s.expect.direction === 'different' ? 'changes' : (s.expect.direction === 'higher' ? 'raises' : 'lowers')} ${K.measures[secondary[0]]}.`,
    control: { name: 'control box', setup: [{ cmd: 'restore' }] },
    treatments: [{ name: `${sub.name} 0.5`, setup: [{ cmd: 'restore' }, { cmd: 'dose', drug: sub.name, amount: 0.5 }] }],
    measure, prediction, secondary, secondary_prediction: secondary.map(b =>
      b === 'banana_dist' ? { higher: 'lower', lower: 'higher', different: 'different' }[s.expect.direction] : s.expect.direction),
    repeats: 10, steps: 400, author: 'ask-page',
  };
  return { feasible: true, substance: sub.name, matched: sub.alias, measure, prediction, secondary, card,
    expect: s.expect, evidence: s.evidence, caveat: s.not_modelled, confidence: s.confidence,
    effect_confidence: s.effect_confidence, mechanism_confidence: s.mechanism_confidence,
    mechanism: s.mechanism, kind: s.kind, drug_class: s.drug_class, note_extra };
}
async function showCard(question) {
  if (K && isFollowup(question.toLowerCase())) { answerFollowup(question); return; }
  const el = $('card');
  el.hidden = false;
  el.innerHTML = '<h2>Ask the flies</h2><p class="dim">Writing the card…</p>';
  CARD = null;
  RUN = null;
  THREAD = null;
  $('run').hidden = true;
  $('out').innerHTML = '';
  $('thread').innerHTML = '';
  const q = question.toLowerCase();
  if (/^what('s| is) in /.test(q)) {
    const food = findFood(q);
    if (food) { CARD = compileFood(food); renderCard(question); return; }
  }
  const trio = [...new Set(q.includes('methylxanthine') ? ['caffeine', 'theophylline', 'theobromine'] : findSubstances(q))].filter(n => K.substances[n]);
  if (trio.length >= 2) {
    CARD = compileComparison(trio, findMeasure(q) || K.substances[trio[0]].expect.measure);
    renderCard(question);
    return;
  }
  if (!trio.length) {
    const food = !findSubstance(q) ? findFood(q) : null;
    if (food) { CARD = compileFood(food); renderCard(question); return; }
    const fb = findGene(q);
    if (fb) { CARD = compileGene(fb); renderCard(question); return; }
  }
  let server = null;
  if (Live.connected) server = await askp({ cmd: 'compile_card', question }, 75000);
  if (server && server.ok && server.data) {
    CARD = { ...server.data, feasible: true, matched: server.data.substance };
  } else {
    CARD = compileCard(question);
    if (CARD.feasible) CARD.badge = 'local';
  }
  renderCard(question);
}
function renderCard(question) {
  const el = $('card');
  if (CARD.mode === 'explain') return renderExplain();
  if (CARD.mode === 'compare') return renderComparison();
  if (!CARD.feasible) {
    const testable = Object.keys(K.substances).filter(n => K.substances[n].dosable).join(', ');
    el.innerHTML = `<span class="eyebrow">not testable yet</span>
      <h1>Cannot test this yet</h1>
      <p>${esc(CARD.substance ? `You asked about ${CARD.substance}.` : 'No known substance in the question.')}</p>
      <p class="no">No — ${esc(CARD.reason)}.</p>
      ${CARD.evidence && CARD.evidence.length ? `<h2>Papers (why we know the biology)</h2><ul class="tight">${CARD.evidence.map(e => `<li>${esc(e)} [cited in sim code]</li>`).join('')}</ul>` : ''}
      <h2>What would unblock it</h2><p class="dim">${esc(CARD.topic ? 'A new stimulus or wiring the sim does not have.' : 'New wiring the sim does not have (see reason above).')}</p>
      <p class="dim">Testable today: ${esc(testable)}.</p>`;
    setCaption('Not testable on our flies — yet.');
    setFeatured('Cannot test this yet', CARD.reason || '');
    return;
  }
  const c = CARD;
  const badge = c.badge === 'ai'
    ? 'Card source: written live by AI, checked against our substance and paper lists.'
    : 'Card source: local lists (AI unavailable — connect the lab with META_API_KEY for live cards).';
  const proc = (c.card.procedure && c.card.procedure.length ? c.card.procedure : [
    'Control box: restore to clean, run.',
    `Treatment box: restore, dose ${c.substance} 0.5, run.`,
    `Maxime and Hagrid run in both boxes. Control and treatment interleave, ${c.card.repeats} repeats each.`]);
  const sec = (c.card.secondary || c.secondary || []).map(b => K.measures[b]).filter(Boolean).join(' + ');
  el.innerHTML = `<span class="eyebrow">${esc(c.measure)} · run card</span>
    <h1>${esc(c.card.title)}</h1>
    <div class="pills"><span>${esc(c.substance)}</span><span>target: ${esc(K.measures[c.measure])}</span>${sec ? `<span>behaviour: ${esc(sec)}</span>` : ''}<span>${esc(c.kind)}</span></div>
    <p>${esc(c.card.hypothesis)} Mechanism: ${esc(c.mechanism)}.</p>
    ${c.note_extra ? `<p class="dim">${esc(c.note_extra)}</p>` : ''}
    <h2>Key Traits</h2>
    <div class="traits">
      <div class="trait"><svg class="ic"><use href="#i-flask"/></svg><span class="k">Control</span><span>clean box, both flies</span></div>
      <div class="trait"><svg class="ic"><use href="#i-fish"/></svg><span class="k">Treatment</span><span>${esc(c.substance)} 0.5, both flies</span></div>
      <div class="trait"><svg class="ic"><use href="#i-gauge"/></svg><span class="k">Effort</span><span>${c.card.repeats} repeats of ${c.card.steps} steps</span></div>
      <div class="trait"><svg class="ic"><use href="#i-globe"/></svg><span class="k">Prediction</span><span>treatment ${esc(PRED_WORD[c.expect.direction])} — ${esc(c.expect.why)}</span></div>
    </div>
    <h2>Procedure</h2><ul class="tight">${proc.map(p => `<li>${esc(p)}</li>`).join('')}</ul>
    ${c.evidence.length ? `<h2>Expected from</h2><ul class="tight">${c.evidence.map(e => `<li>${esc(e)} [cited in sim code]</li>`).join('')}</ul>`
      : `<h2>Expected from</h2><p class="dim">The sim's own mechanism only — no paper attached to this drug.</p>`}
    ${(() => { const t = ((typeof AFFINITY !== 'undefined' && AFFINITY) || {})[c.substance] || [];
      return t.length ? `<h2>Targets (GtoPdb)</h2><ul class="tight">${t.slice(0, 6).map(e =>
        `<li>${esc(e.target)}${e.gene ? ` (${esc(e.gene)})` : ''} — ${esc(e.action)}${e.affinity !== '' && e.affinity != null ? `, ${esc(String(e.affinity))} ${esc(e.unit)}` : ''}${e.species ? ` [${esc(e.species)}]` : ''}</li>`).join('')}</ul>` : ''; })()}
    ${(() => { const b = ((typeof BRIDGE !== 'undefined' && BRIDGE) || {});
      const rows = (b[c.substance] || []).filter(e => e.f && e.f.length)
        .sort((x, y) => (x.sp === 'Human' ? 0 : 1) - (y.sp === 'Human' ? 0 : 1)).slice(0, 4);
      if (!rows.length) return '';
      const sym = {};
      if (typeof GENES !== 'undefined' && GENES) for (const [fb, g] of Object.entries(GENES)) { if (g.s && !(g.s.toLowerCase() in sym)) sym[g.s.toLowerCase()] = g; }
      const simOf = f => { const g = sym[(f || '').toLowerCase()]; if (!g) return '';
        const s = [...new Set((g.e || []).flatMap(e => e.sim || []))];
        return s.length ? ` — reaches sim: ${s.join(', ')}` : ''; };
      return `<h2>Fly legs (Alliance)</h2><ul class="tight">${rows.map(e =>
        `<li>${esc(e.t)} → fly ${esc(e.f.join(', '))} (on in: ${esc(e.e.slice(0, 3).join(', ') || 'unlisted sites')}${esc(simOf(e.f[0]))})</li>`).join('')}</ul>`; })()}
    ${c.caveat ? `<p class="dim">Not modelled: ${esc(c.caveat)}.</p>` : ''}
    <p class="dim">${esc(badge)} ${(c.effect_confidence && c.mechanism_confidence)
      ? `Effect in real flies: ${esc(c.effect_confidence)}. Mechanism in our sim: ${esc(c.mechanism_confidence)}.`
      : `Confidence: ${esc(c.confidence)}.`}</p>
    ${(c.notes || []).map(n => `<p class="dim">Note: ${esc(n)}.</p>`).join('')}`;
  setCaption(`Treatment ${PRED_WORD[c.expect.direction]} vs control — run it to see.`);
  setFeatured(c.card.title, c.card.hypothesis);
  $('run').hidden = false;
  setRunEnabled(Live.connected);
  $('prog').textContent = Live.connected ? '' : 'needs the lab — start the server';
}

function renderExplain() {
  const c = CARD, el = $('card');
  const imgs = (c.stages || []).filter(s => IMG[s]).map(s =>
    `<div><h3>${esc(s)}</h3><img src="${IMG[s]}" alt="${esc(s)}" style="max-width:100%;border-radius:6px"></div>`).join('');
  el.innerHTML = `<span class="eyebrow">explainer · from references</span>
    <h1>${esc(c.title)}</h1>
    ${(c.paragraphs || []).map(p => `<p>${esc(p)}</p>`).join('')}
    ${imgs}
    ${(c.refs || []).length ? `<h2>From</h2><ul class="tight">${c.refs.map(r => `<li>${esc(r)}</li>`).join('')}</ul>` : ''}
    <p class="dim">Written live by AI from our references. Explained, not simulated — no experiment to run.</p>
    ${(c.notes || []).map(n => `<p class="dim">Note: ${esc(n)}.</p>`).join('')}`;
  setCaption(c.title);
  setFeatured(c.title, (c.paragraphs || [])[0] || '');
  $('run').hidden = true;
}

/* ---- run: control vs treatment, both sexes ---- */
function askp(obj, timeout = 30000) {
  return new Promise(resolve => {
    if (!Live.connected) return resolve(null);
    const id = Live.send(obj);
    if (!id) return resolve(null);
    let done = false;
    Live.pending[id] = m => { if (!done) { done = true; resolve(m); } };
    setTimeout(() => { if (!done) { done = true; delete Live.pending[id]; resolve(null); } }, timeout);
  });
}
async function runCard() {
  if (CARD && CARD.mode === 'compare') return runComparison();
  if (!CARD || !CARD.feasible || RUN) return;
  const quietMs = Live.lastState ? Date.now() - Live.lastState : 1e9;
  if (!Live.connected || quietMs > 6000) {
    $('prog').textContent = 'Lab is quiet — reconnecting. Wait for the green dot, then run again.';
    try { Live.ws && Live.ws.close(); } catch {}
    return;
  }
  setRunEnabled(false);
  $('out').innerHTML = '';
  RUN = { trace: [], groups: {} };
  $('prog').textContent = 'Writing the card…';
  try {
    const ack = await askp({ cmd: 'run_experiment', card: CARD.card });
    if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'no answer in 30s — check the server terminal for a traceback');
    const rec = await new Promise(resolve => {
      Live.pending[ack.data.id] = m => resolve(m.type === 'experiment_done' ? m.record : null);
      const iv = setInterval(() => {
        if (!Live.connected) { clearInterval(iv); delete Live.pending[ack.data.id]; resolve(null); }
        if (RUN && RUN.finished) clearInterval(iv);
      }, 700);
      setTimeout(() => { if (!RUN.finished) { delete Live.pending[ack.data.id]; resolve(null); } }, 600000);
    });
    RUN.finished = true;
    if (!rec) throw new Error(Live.connected ? 'run ended early' : 'lab dropped mid-run — stopping, not reading stale numbers');
    $('prog').textContent = 'done';
    renderResults(rec);
  } catch (err) {
    $('prog').textContent = 'Run failed: ' + err.message;
    RUN = null;
    setRunEnabled(Live.connected);
  }
}

/* ---- results: two tables + stages + match ---- */
const badge = v => `<span class="verdict ${v === 'SUPPORTED' ? 'v-sup' : v === 'NOT SUPPORTED' ? 'v-not' : v === 'MIXED' ? 'v-mix' : 'v-cant'}">${esc(v || '?')}</span>`;
function stageMeans(slot) {
  const acc = (RUN.groups || {})[slot] || {};
  const out = {};
  for (const st of K.stages) { const [m] = meanSD(acc[st] || []); out[st] = m; }
  return out;
}
function renderResults(rec) {
  const pf = (rec.details || {}).per_fly || {};
  const tbl = (box, col, mkey, vkey) => {
    const rows = FLIES.map(([name, cslot, tslot]) => {
      const d = ((pf[name] || [])[0] || {});
      const e = ((d.measures || {})[mkey]) || d;
      const vs = col === 'control' ? (e.control_values || []) : (e.values || []);
      const [m, s] = meanSD(vs);
      return `<tr><td>${esc(name)}</td><td>${m.toFixed(4)} ± ${s.toFixed(4)} (n=${vs.length})<br><span class="dim">runs: ${vs.map(v => (+v).toFixed(4)).join(', ') || '—'}</span></td>
        <td>${col === 'treatment' ? badge(vkey ? ((((pf[name] || [])[0] || {}).measures || {})[vkey] || {}).verdict : d.verdict) : '<span class="dim">baseline</span>'}</td></tr>`;
    }).join('');
    return `<table class="flies"><tr><th>fly</th><th>${esc(mkey)} — ${box} box</th><th>verdict</th></tr>${rows}</table>`;
  };
  const secs = (rec.card && rec.card.secondary) || CARD.secondary || [];
  const secTbls = secs.map(b =>
    `<h2>Behaviour — ${esc((K.measures || {})[b] || b)}</h2>${tbl('control', 'control', b, b)}${tbl('treatment', 'treatment', b, b)}`).join('');
  let stageHtml = '';
  const topImgs = [];
  for (const [name, cslot, tslot] of FLIES) {
    const c = stageMeans(cslot), t = stageMeans(tslot);
    const mx = Math.max(0.01, ...K.stages.map(s => Math.max(c[s], t[s])));
    const ups = K.stages.map(s => [s, (t[s] - c[s]) / Math.max(c[s], 1e-6)]).sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]));
    const big = ups[0];
    const tiny = Math.abs(big[1]) < 0.05;
    topImgs.push({ name, c, t });
    stageHtml += `<h3>${esc(name)} — stages (share firing, sampled live during the run)</h3>` + K.stages.map(s =>
      `<div class="stagebar"><span class="nm">${esc(s)}</span>
       <span class="bar"><i style="width:${(100 * c[s] / mx).toFixed(1)}%"></i></span>
       <span class="bar t"><i style="width:${(100 * t[s] / mx).toFixed(1)}%"></i></span></div>`).join('') +
      `<p class="dim">control (blue) vs treatment (teal). Biggest change: <b>${esc(big[0])}</b> (${big[1] >= 0 ? '+' : ''}${Math.round(100 * big[1])}%${tiny ? ' — no real change' : ''}).</p>`;
  }
  const movers = topImgs.map(({ name, c, t }) => {
    const ranked = K.stages.map(s => [s, (t[s] - c[s]) / Math.max(c[s], 1e-6)])
      .sort((a, b) => b[1] - a[1]).filter(([s]) => BINS[s]).slice(0, 3);
    const links = ranked.map(([s, rel]) =>
      `<a href="../library/#/${BINS[s][0]}">${esc(s)}</a> (${rel >= 0 ? '+' : ''}${Math.round(100 * rel)}%)`).join(' · ') ||
      'no mapped stage moved';
    return `<p class="dim">${esc(name)} in the Fly Atlas: ${links}.</p>`;
  }).join('');
  FLYVALS = topImgs.map(({ t }) => t);
  Stage.showFly(Stage.fly);
  setCaption(`${rec.verdict} — treatment ${PRED_WORD[CARD.expect.direction]} vs control.`);
  setFeatured(CARD.card.title, `Verdict: ${rec.verdict}.`);
  $('out').innerHTML = `<h2>Control box</h2><p class="dim">Untouched. This is what normal looks like for this run.</p>${tbl('control', 'control', CARD.measure)}
    <h2>Treatment box — ${esc(CARD.substance)} 0.5</h2>${tbl('treatment', 'treatment', CARD.measure)}${secTbls}<p>Overall: ${badge(rec.verdict)}</p><p><button class="go" id="watchBtn">Watch this run replay</button></p>
    <h2>Where it changed</h2>${stageHtml}${movers}
    <h2>Did it match?</h2>${matchHtml(rec)}`;
  document.getElementById('watchBtn').onclick = () => location.assign('watch/?id=' + encodeURIComponent(rec.id));
  RUN = null;
  setRunEnabled(Live.connected);
}
function matchHtml(rec) {
  const v = rec.verdict;
  const head = v === 'SUPPORTED'
    ? `<span class="yes">Match.</span> Predicted ${PRED_WORD[CARD.expect.direction]} — rule says ${esc(v)}.`
    : v === "CAN'T TELL YET"
      ? `<span class="verdict v-cant">Undecided.</span> The gap is smaller than the control box's own spread — more repeats would settle it.`
      : `<span class="no">Miss.</span> Predicted ${PRED_WORD[CARD.expect.direction]} — rule says ${esc(v)}.`;
  const story = (rec.explanation || []).map(p => `<p>${esc(p)}</p>`).join('');
  const pf = (rec.details || {}).per_fly || {};
  const sus = [...new Set(Object.keys(pf).flatMap(f => (((pf[f] || [])[0] || {}).suspects || [])))];
  return `<p>${head}</p>${story || `<p class="dim">No explanation on this record — re-run to get the pathway story.</p>`}
    ${sus.length ? `<p class="dim">Suspects: ${esc(sus.join('; '))}.</p>` : ''}
    <p class="dim">Seeds: ${rec.seeds ? esc(JSON.stringify(rec.seeds)) : 'runs not individually seeded'}. Record ${esc(rec.id || '?')}.</p>`;
}

/* ---- comparison: two or more substances, one measure ---- */
const COMPARE_WORDS = [' vs ', 'versus', 'compare', 'comparison', 'methylxanthine', 'strongest', 'stronger', 'weakest', 'difference between', 'rank', 'trio', 'all three'];
const FOLLOWUP_WORDS = ['why', 'how come', 'strongest', 'weakest', 'evidence', 'paper', 'papers', 'reference', 'limit', 'not modelled', 'not modeled', 'caveat', 'assum', 'run it', 'run them', 'again', 'what next', 'what to ask', 'explain', 'mechanism'];
let THREAD = null;   // comparison conversation: {members, measure, results, focus}
function findSubstances(q) {
  const lq = q.toLowerCase(), found = [];
  for (const [name, s] of Object.entries(K.substances)) {
    let at = -1;
    for (const a of [name, ...(s.aliases || [])]) {
      if (!a) continue;
      const rx = new RegExp('\\b' + a.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b');
      const m = lq.search(rx);
      if (m >= 0 && (at < 0 || m < at)) at = m;
    }
    if (at >= 0) found.push([at, name]);
  }
  return found.sort((x, y) => x[0] - y[0]).map(([, n]) => n);
}
function runnable(name) {
  const d = Live.hello && Live.hello.drugs;
  if (!d) return null;   // lab hello not seen yet — unknown
  return Object.prototype.hasOwnProperty.call(d, name);
}
function isFollowup(q) {
  if (!THREAD) return false;
  if (q.includes('methylxanthine')) return false;
  if (findSubstances(q).length >= 2) return false;
  if (findSubstances(q).length === 0) return true;
  return FOLLOWUP_WORDS.some(w => q.includes(w));
}
function compileComparison(names, measure) {
  const cards = names.map(n => {
    const s = K.substances[n];
    return {
      substance: n,
      card: {
        title: `${n} vs control (${measure})`,
        hypothesis: `${n} ${s.expect.direction === 'different' ? 'changes' : (s.expect.direction === 'higher' ? 'raises' : 'lowers')} ${K.measures[measure]}.`,
        control: { name: 'control box', setup: [{ cmd: 'restore' }] },
        treatments: [{ name: `${n} 0.5`, setup: [{ cmd: 'restore' }, { cmd: 'dose', drug: n, amount: 0.5 }] }],
        measure, prediction: s.expect.direction, repeats: 10, steps: 400, author: 'ask-page',
      },
    };
  });
  return { mode: 'compare', feasible: true, members: names, measure, cards, badge: 'local' };
}
function runState(n) {
  const s = K.substances[n];
  if (!s.dosable) return { runnable: false, why: `not dosable — ${s.not_modelled || s.confidence}` };
  const r = runnable(n);
  if (r === null) return { runnable: true, why: 'lab hello not seen yet — run to find out' };
  if (!r) return { runnable: false, why: 'needs patch 009 — the sim cannot dose this yet' };
  return { runnable: true, why: 'runs today' };
}
function renderComparison() {
  const el = $('card');
  THREAD = { members: CARD.members, measure: CARD.measure, results: (THREAD && THREAD.members.join() === CARD.members.join()) ? THREAD.results : {}, focus: null };
  const rows = CARD.cards.map(({ substance: n }) => {
    const s = K.substances[n], st = runState(n);
    return `<tr><td>${esc(n)}</td><td>${esc(s.kind)}</td><td>treatment ${esc(PRED_WORD[s.expect.direction])} — ${esc(s.expect.why)}</td><td>${st.runnable ? `<span class="yes">${esc(st.why)}</span>` : `<span class="no">${esc(st.why)}</span>`}</td></tr>`;
  }).join('');
  const ev = CARD.members.map(n => `<li><b>${esc(n)}</b>: ${(K.substances[n].evidence || []).map(esc).join('; ') || 'sim mechanism only — no paper attached'}</li>`).join('');
  const caveats = CARD.members.map(n => `<li><b>${esc(n)}</b> (${esc(K.substances[n].confidence)}): ${esc(K.substances[n].not_modelled || 'no caveat on file')}</li>`).join('');
  el.innerHTML = `<span class="eyebrow">${esc(CARD.measure)} · comparison card</span>
    <h1>${CARD.members.map(esc).join(' vs ')}</h1>
    <p>One run per member, same measure (${esc(K.measures[CARD.measure])}), same effort: 3 repeats of 400 steps, Maxime and Hagrid in both boxes.</p>
    <table class="flies"><tr><th>member</th><th>kind</th><th>prediction</th><th>can it run?</th></tr>${rows}</table>
    <h2>Expected from</h2><ul class="tight">${ev}</ul>
    <h2>Not modelled</h2><ul class="tight">${caveats}</ul>
    <p class="dim">Card source: local lists, one card per member. Confidence is per member, above.</p>`;
  setCaption('Comparison card — run it to rank the members.');
  setFeatured(CARD.members.join(' vs '), 'Same measure, same effort — the runs decide the ranking.');
  $('run').hidden = false;
  setRunEnabled(Live.connected);
  $('prog').textContent = Live.connected ? '' : 'needs the lab — start the server';
  const first = CARD.members[0], s0 = K.substances[first];
  const isTrio = CARD.members.length === 3 && ['caffeine', 'theophylline', 'theobromine'].every(m => CARD.members.includes(m));
  const whyChip = isTrio
    ? 'Why is theophylline expected strongest?'
    : `Why is ${first} expected ${PRED_WORD[s0.expect.direction]}?`;
  suggestChips(['Run the comparison', whyChip, 'Show the evidence', 'What is not modelled?']);
}
async function runOneCard(card) {
  const ack = await askp({ cmd: 'run_experiment', card });
  if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'no answer in 30s — check the server terminal for a traceback');
  return new Promise(resolve => {
    Live.pending[ack.data.id] = m => resolve(m.type === 'experiment_done' ? m.record : null);
    const iv = setInterval(() => {
      if (!Live.connected) { clearInterval(iv); delete Live.pending[ack.data.id]; resolve(null); }
      if (RUN && RUN.finished) clearInterval(iv);
    }, 700);
    setTimeout(() => { if (!RUN.finished) { delete Live.pending[ack.data.id]; resolve(null); } }, 600000);
  });
}
function memberGap(rec) {
  const pf = (rec.details || {}).per_fly || {};
  const gaps = Object.keys(pf).map(f => (((pf[f] || [])[0] || {}).gap) || 0);
  return gaps.reduce((a, b) => a + b, 0) / Math.max(1, gaps.length);
}
async function runComparison() {
  if (!CARD || CARD.mode !== 'compare' || RUN) return;
  const quietMs = Live.lastState ? Date.now() - Live.lastState : 1e9;
  if (!Live.connected || quietMs > 6000) {
    $('prog').textContent = 'Lab is quiet — reconnecting. Wait for the green dot, then run again.';
    try { Live.ws && Live.ws.close(); } catch {}
    return;
  }
  setRunEnabled(false);
  $('out').innerHTML = '';
  RUN = { trace: [], groups: {}, compare: true };
  for (let i = 0; i < CARD.cards.length; i++) {
    const { substance: n, card } = CARD.cards[i];
    const st = runState(n);
    if (!st.runnable) { THREAD.results[n] = { status: st.why }; continue; }
    $('prog').textContent = `Running ${n}… (${i + 1} of ${CARD.cards.length})`;
    try {
      const rec = await runOneCard(card);
      if (!rec) throw new Error(Live.connected ? 'run ended early' : 'lab dropped mid-run — stopping, not reading stale numbers');
      THREAD.results[n] = { status: 'done', rec, gap: memberGap(rec) };
    } catch (err) {
      THREAD.results[n] = { status: 'failed: ' + err.message };
    }
  }
  RUN.finished = true;
  RUN = null;
  $('prog').textContent = 'done';
  renderComparisonResults();
  setRunEnabled(Live.connected);
}
function rankMembers() {
  const moved = ([n, r]) => {   // movement in the predicted direction, not raw gap
    const d = ((K.substances[n] || {}).expect || {}).direction;
    if (d === 'lower') return -r.gap;
    if (d === 'different') return Math.abs(r.gap);
    return r.gap;
  };
  return Object.entries(THREAD.results || {})
    .filter(([, r]) => r.status === 'done')
    .sort((a, b) => moved(b) - moved(a));
}
function renderComparisonResults() {
  const ranked = rankMembers();
  const rows = CARD.members.map(n => {
    const r = THREAD.results[n] || { status: 'not run' };
    if (r.status !== 'done') return `<tr><td>${esc(n)}</td><td colspan="3"><span class="dim">${esc(r.status)}</span></td></tr>`;
    return `<tr><td>${esc(n)}</td><td>${r.gap >= 0 ? '+' : ''}${r.gap.toFixed(4)} ${esc(CARD.measure)}</td><td>${badge(r.rec.verdict)}</td><td><a href="watch/?id=${encodeURIComponent(r.rec.id)}">replay</a></td></tr>`;
  }).join('');
  $('out').innerHTML = `<h2>Ranking — ${esc(K.measures[CARD.measure])}</h2>
    <table class="flies"><tr><th>member</th><th>shift vs control</th><th>verdict</th><th>run</th></tr>${rows}</table>
    <h2>What this means</h2>${explainResults()}
    <p class="dim">Seeds and records live on each run's replay page. Unranked members never ran — they are not last, they are unknown.</p>`;
  setCaption(ranked.length ? `${ranked[0][0]} moved ${K.measures[CARD.measure]} most.` : 'No member produced numbers.');
  setFeatured(CARD.members.join(' vs '), ranked.length ? `Strongest shift: ${ranked[0][0]}.` : 'No runs completed.');
  if (ranked.length) { THREAD.focus = ranked[0][0]; }
  suggestChips([`Why did ${THREAD.focus || CARD.members[0]} come first?`, 'Show the evidence', 'What is not modelled?', 'What to ask next']);
}
function explainResults() {
  const ranked = rankMembers();
  const exp = CARD.members.map(n => {
    const s = K.substances[n];
    return `<b>${esc(n)}</b> (${esc(s.kind)}): ${esc(s.mechanism)}. Predicted treatment ${esc(PRED_WORD[s.expect.direction])} — ${esc(s.expect.why)}.`;
  }).join(' ');
  if (!ranked.length) return `<p>${exp}</p><p>No member has numbers yet — the ranking above says why each one is missing.</p>`;
  const [top, topR] = ranked[0];
  const order = ranked.map(([n, r]) => `${n} (${r.gap >= 0 ? '+' : ''}${r.gap.toFixed(4)})`).join(', then ');
  const pred = K.substances[top].expect.direction;
  const hit = pred === 'higher' ? topR.gap > 0 : pred === 'lower' ? topR.gap < 0 : true;
  return `<p>What we did: one control-vs-treatment run per member, same measure, same effort — the runs are linked in the ranking above.</p>
    <p>In plain words: ${exp}</p>
    <p>Measured order, strongest shift first: ${esc(order)}. ${hit ? `<span class="yes">Match for ${esc(top)}.</span> Predicted ${esc(PRED_WORD[pred])}, measured ${topR.gap >= 0 ? 'up' : 'down'} — rule says ${esc(topR.rec.verdict)}.` : `<span class="no">Miss for ${esc(top)}.</span> Predicted ${esc(PRED_WORD[pred])}, measured ${topR.gap >= 0 ? 'up' : 'down'} — rule says ${esc(topR.rec.verdict)}. The sim disagrees with the guess.`}</p>`;
}
function appendThread(question, html) {
  const t = $('thread');
  const div = document.createElement('div');
  div.className = 'qa';
  div.innerHTML = `<p class="qq">${esc(question)}</p><div>${html}</div>`;
  t.appendChild(div);
  div.scrollIntoView({ block: 'nearest' });
}
function suggestChips(items) {
  let box = $('chips');
  if (!box) { box = document.createElement('div'); box.id = 'chips'; box.className = 'chips'; $('thread').after(box); }
  box.innerHTML = `<span class="dim">What to ask next:</span> `;
  for (const it of items) {
    const b = document.createElement('button');
    b.textContent = it;
    b.onclick = () => { if (/^run the comparison/i.test(it)) { runComparison(); } else { $('q').value = it; showCard(it); } };
    box.appendChild(b);
  }
}
function answerFollowup(question) {
  const q = question.toLowerCase();
  const t = THREAD;
  const named = findSubstances(q).find(n => t.members.includes(n)) || null;
  const ranked = rankMembers();
  const focus = named || t.focus || (ranked[0] && ranked[0][0]) || t.members[0];
  t.focus = focus;
  const s = K.substances[focus];
  const r = (t.results || {})[focus];
  const needRun = 'I can only explain numbers already on screen — run the comparison first, then ask me.';
  let html;
  if (/strongest|highest|biggest|winner|first/.test(q)) {
    html = ranked.length
      ? `<p>Strongest shift on screen: <b>${esc(ranked[0][0])}</b> (${ranked[0][1].gap >= 0 ? '+' : ''}${ranked[0][1].gap.toFixed(4)} ${esc(t.measure)}). Full order: ${esc(ranked.map(([n, x]) => `${n} (${x.gap >= 0 ? '+' : ''}${x.gap.toFixed(4)})`).join(', then '))}.</p>`
      : `<p>${esc(needRun)}</p>`;
  } else if (/weakest|lowest|smallest|last/.test(q)) {
    html = ranked.length
      ? `<p>Weakest shift on screen: <b>${esc(ranked[ranked.length - 1][0])}</b>. (Members that never ran are unknown, not last.)</p>`
      : `<p>${esc(needRun)}</p>`;
  } else if (/evidence|paper|papers|reference|cite/.test(q)) {
    html = `<ul class="tight">${t.members.map(n => `<li><b>${esc(n)}</b>: ${((K.substances[n].evidence || []).map(esc).join('; ')) || 'sim mechanism only — no paper attached'}</li>`).join('')}</ul>`;
  } else if (/limit|not modelled|not modeled|caveat|assum/.test(q)) {
    html = `<ul class="tight">${t.members.map(n => `<li><b>${esc(n)}</b> (${esc(K.substances[n].confidence)}): ${esc(K.substances[n].not_modelled || 'no caveat on file')}</li>`).join('')}</ul>`;
  } else if (/run it|run them|run the|again|more repeats/.test(q)) {
    if (ranked.length) { html = `<p>The numbers above are the runs. Ask "why" about any member and I will explain from the results on screen.</p>`; }
    else if (Live.connected) { html = `<p>Running now — one run per member.</p>`; appendThread(question, html); runComparison(); suggestChips(['Why did it come out that way?', 'Show the evidence', 'What is not modelled?']); return; }
    else { html = `<p>Needs the lab — start the server and ask again.</p>`; }
  } else {
    const measured = r && r.status === 'done'
      ? ` Measured: ${r.gap >= 0 ? '+' : ''}${r.gap.toFixed(4)} ${esc(t.measure)} — rule says ${esc(r.rec.verdict)}.`
      : (r ? ` No numbers for ${esc(focus)}: ${esc(r.status)}.` : ` No numbers yet — ${esc(needRun)}`);
    html = `<p><b>${esc(focus)}</b> (${esc(s.kind)}): ${esc(s.mechanism)}. Predicted treatment ${esc(PRED_WORD[s.expect.direction])} — ${esc(s.expect.why)}.${measured}</p>`;
  }
  appendThread(question, html);
  suggestChips([`Why ${focus}?`, 'Who was strongest?', 'Show the evidence', 'What is not modelled?']);
}

/* ---- stage: one 3D brain, two flies ---- */
let FLYVALS = [{}, {}];
const Stage = {
  view: null, fly: 0,
  async init() {
    try {
      const { brain3D } = await import('./brain3d.js');
      this.view = await brain3D($('gl'), {}, { soma: Feather.mode() === 'dusk' ? 0x4a5578 : 0x8a8fa8, stage: $('stage') });
    } catch (err) {
      $('caption').textContent = '3D unavailable here — the numbers below carry it.';
    }
  },
  showFly(i) {
    this.fly = i;
    for (const n of [0, 1]) $(`tab${n}`).setAttribute('aria-selected', String(n === i));
    const st = $('stage');
    st.classList.add('switching');
    setTimeout(() => st.classList.remove('switching'), 350);
    if (this.view) this.view.setValues(FLYVALS[i]);
  },
};
function setCaption(t) { const c = $('caption'); if (c) c.textContent = t; }
function setFeatured(title, text) {
  if ($('featTitle')) $('featTitle').textContent = title;
  if ($('featText')) $('featText').textContent = text;
}
function syncZoomBtns() {
  if (!Stage.view) return;
  const z = Stage.view.zoom();
  $('zin').setAttribute('aria-pressed', String(z >= 1));
  $('zout').setAttribute('aria-pressed', String(z < 1));
  $('closeupBtn').setAttribute('aria-pressed', String(z > 2.2));
}
function initStage() {
  Stage.init();
  $('tab0').onclick = () => Stage.showFly(0);
  $('tab1').onclick = () => Stage.showFly(1);
  document.querySelector('.tabs').addEventListener('keydown', e => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') Stage.showFly(Stage.fly ? 0 : 1);
  });
  $('stage').addEventListener('keydown', e => {
    if (!Stage.view) return;
    if (e.key === 'ArrowLeft') Stage.view.queueYaw(0.18);
    else if (e.key === 'ArrowRight') Stage.view.queueYaw(-0.18);
    else if (e.key === '+' || e.key === '=') { Stage.view.zoomBy(1.2); syncZoomBtns(); }
    else if (e.key === '-' || e.key === '_') { Stage.view.zoomBy(1 / 1.2); syncZoomBtns(); }
    else if (e.key === '0') { Stage.view.reset(); syncZoomBtns(); }
    else return;
    e.preventDefault();
  });
  $('zin').onclick = () => { Stage.view && Stage.view.zoomBy(1.35); syncZoomBtns(); };
  $('zout').onclick = () => { Stage.view && Stage.view.zoomBy(1 / 1.35); syncZoomBtns(); };
  $('turnL').onclick = () => Stage.view && Stage.view.queueYaw(-Math.PI / 2);
  $('turnR').onclick = () => Stage.view && Stage.view.queueYaw(Math.PI / 2);
  $('turn360').onclick = () => Stage.view && Stage.view.queueYaw(Math.PI * 2);
  $('closeupBtn').onclick = () => {
    if (!Stage.view) return;
    const z = Stage.view.zoom();
    Stage.view.zoomBy(z > 2.2 ? 1 / z : 2.6 / z);
    syncZoomBtns();
  };
  $('expandBtn').onclick = () => {
    const hero = $('hero'), on = !hero.classList.contains('focus');
    hero.classList.toggle('focus', on);
    $('expandBtn').setAttribute('aria-pressed', String(on));
  };
  addEventListener('keydown', e => {
    if (e.key === 'Escape') { $('hero').classList.remove('focus'); $('expandBtn').setAttribute('aria-pressed', 'false'); }
  });
  Feather.topbar(document.querySelector('aside.side'), { brand: 'Ask the flies', base: '../', current: 'ask', extras: ['.featured'], light: false });
  Feather.init({ onLight: m => { if (Stage.view) Stage.view.setSoma(m === 'dusk' ? 0x4a5578 : 0x8a8fa8); } });
  const tip = $('partTip'), stageEl = $('stage');
  let tipTimer = 0, tap = null;
  const hideTip = () => { tip.hidden = true; };
  const showTip = (cx, cy, stage) => {
    const v = (FLYVALS[Stage.fly] || {})[stage];
    const who = ['Maxime', 'Hagrid'][Stage.fly];
    tip.textContent = v == null ? `${stage} (${who})` : `${stage} — treatment ${v.toFixed(2)}/s (${who})`;
    tip.hidden = false;
    const r = stageEl.getBoundingClientRect();
    tip.style.top = `${cy - r.top}px`;
    tip.style.left = '0px';
    const w = tip.offsetWidth;
    let x = cx - r.left + 14;
    if (x + w > r.width - 6) x = cx - r.left - 14 - w;
    tip.style.left = `${x}px`;
  };
  stageEl.addEventListener('pointermove', e => {
    if (e.pointerType === 'touch' || !Stage.view) return;
    if (e.buttons) { hideTip(); return; }
    const hit = Stage.view.hover(e.clientX, e.clientY);
    if (hit) showTip(e.clientX, e.clientY, hit);
    else hideTip();
  });
  stageEl.addEventListener('pointerleave', hideTip);
  stageEl.addEventListener('pointerdown', e => {
    tap = e.target.closest('button') ? null : { x: e.clientX, y: e.clientY, t: performance.now() };
    hideTip();
  });
  stageEl.addEventListener('pointerup', e => {
    if (tap && e.clientX !== undefined && Math.hypot(e.clientX - tap.x, e.clientY - tap.y) < 8 &&
        performance.now() - tap.t < 350 && Stage.view) {
      const hit = Stage.view.hover(e.clientX, e.clientY);
      if (hit) {
        showTip(e.clientX, e.clientY, hit);
        clearTimeout(tipTimer);
        tipTimer = setTimeout(hideTip, 1600);
      }
    }
    tap = null;
  });
}

/* ---- boot ---- */
fetch('knowledge.json').then(r => r.json()).then(j => { K = j; }).catch(() => {
  $('card').hidden = false;
  $('card').innerHTML = '<h2>Ask the flies</h2><p class="no">knowledge.json missing — serve the ask/ folder over http.</p>';
});
fetch('../anatomy/genes.json').then(r => r.json()).then(j => { GENES = j.genes; }).catch(() => { GENES = null; });
fetch('foods.json').then(r => r.json()).then(j => { FOODS = j; }).catch(() => { FOODS = null; });
fetch('affinity.json').then(r => r.json()).then(j => { AFFINITY = j; }).catch(() => { AFFINITY = null; });
fetch('../anatomy/drug_targets.json').then(r => r.json()).then(j => { BRIDGE = j.bridge; }).catch(() => { BRIDGE = null; });
$('ask').onclick = () => { const q = $('q').value.trim(); if (q && K) showCard(q); };
{ const q0 = new URLSearchParams(location.search).get('q');
  if (q0) { const iv = setInterval(() => { if (K) { clearInterval(iv); $('q').value = q0; showCard(q0); } }, 200); } }
$('q').addEventListener('keydown', e => { if (e.key === 'Enter') $('ask').click(); });
document.querySelectorAll('[data-q]').forEach(b => b.onclick = () => { $('q').value = b.dataset.q; if (K) showCard(b.dataset.q); });
$('runBtn').onclick = runCard;
$('runBtnM').onclick = runCard;
initStage();
Live.connect();
setInterval(() => Live.watchdog(), 2000);

