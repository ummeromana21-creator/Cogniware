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
  'antennal lobe PNs': 'library/img/row42_AL.jpg',
  'lateral horn': 'library/img/row43_LH.jpg',
  'mushroom body': 'library/img/row44_MB_CA.jpg',
  'central complex': 'library/img/row57_CX.jpg',
  'nerve cord': 'library/img/row10_VNC.jpg',
};
const BINS = {  // sim stage -> atlas region bins (also Fly Atlas entry names)
  'antennal lobe PNs': ['AL'], 'lateral horn': ['LH'],
  'mushroom body': ['MB_CA', 'MB_ML', 'MB_PED', 'MB_VL'],
  'central complex': ['EB', 'FB', 'PB', 'NO', 'LAL'],
  'optic lobes': ['ME', 'LO', 'LOP', 'AME', 'LA'],
};
const PRED_WORD = { higher: 'up', lower: 'down', different: 'different' };
// Dose model, evaluated exactly like the sim (substances.hill + pharmacology DRUGS).
const hill = (c, ec50, n) => c > 0 ? Math.pow(c, n) / (Math.pow(ec50, n) + Math.pow(c, n)) : 0;
const X2 = v => '×' + v.toFixed(2);
const DOSES = [0.25, 0.5, 1.5];           // picker: Low / Standard / High
const DOSE_DEFAULT = 0.5;                  // Standard matches notebook history
const CURVE_DOSES = [0.25, 0.5, 0.75, 1.0, 1.5, 2.0];
const PDE_JOIN = 1.79;  // pure-hill 10% crossing, bit-identical to TEST 16
const REPARAM = new Set(['theophylline', 'theobromine']);  // records without the chembl-v1 stamp used the old model
function modelLine(sub, d) {
  if (sub === 'dopamine') return `background excitatory drive on dopamine-receptor neurons ${X2(1 + hill(d, 0.4, 2))}`;
  if (sub === 'theophylline') return `background excitatory drive ${X2(1 + 0.25 * hill(d, 0.1, 1.5))}; excitatory transmission ${X2(1 + 0.5 * hill(d, 3.1, 4))}; scales with glial tone (half rested, full tired)`;
  return null;
}
const OFFTONE = '; scales with glial tone (half rested, full tired)';
function offNote(sub, d) {
  const ml = modelLine(sub, d);
  if (ml) return ' — ' + ml;
  if (sub === 'caffeine' || sub === 'theobromine') return OFFTONE;
  if (sub === 'deet') return ` — bitter co-located on the banana at ${Math.min(1, d)} (contact route, S3)`;
  return '';
}
function recDose(rec) {
  const setup = (((rec.card || {}).treatments || [])[0] || {}).setup || [];
  const dose = setup.find(a => a.cmd === 'dose');
  if (dose && typeof dose.amount === 'number') return dose.amount;
  const m = String((((rec.card || {}).treatments || [])[0] || {}).name || '').match(/([\d.]+)\s*$/);
  return m ? parseFloat(m[1]) : DOSE_DEFAULT;
}
function treatLabel(rec, sub) {
  const setup = (((rec.card || {}).treatments || [])[0] || {}).setup || [];
  const dose = setup.find(a => a.cmd === 'dose');
  if (dose && typeof dose.amount === 'number') return `${sub} ${dose.amount}`;
  return (((rec.card || {}).treatments || [])[0] || {}).name || sub;
}
const FLIES = [['Maxime (female)', 'control:banc', 'treatment:banc'], ['Hagrid (male)', 'control:malecns', 'treatment:malecns']];

/* ---- lab link: the bench borrows Study's socket (one connection per page) ----
   Study publishes its Live layer as globalThis.StudyLive before this boots. */
let Live = null;
let lastStateTs = 0;   // ms of the last state seen (freshness for the run guards)
function setRunEnabled(on) {
  for (const id of ['runBtn', 'runBtnM']) { const b = $(id); if (b) b.disabled = !on; }
}
/* Bench run-wait: Study routes experiment_done/cancelled through events, not
   pending callbacks, so a run waits on a filtered subscription (same id match).
   Stale subscriptions (from an earlier run) no-op on the RUN token. */
function waitDone(eid) {
  return new Promise(resolve => {
    const tok = RUN;
    let settled = false;
    const settle = rec => {
      if (settled || RUN !== tok) return;
      settled = true;
      clearInterval(iv);
      resolve(rec);
    };
    Live.on('experiment_done', m => { if (m.id === eid) settle(m.record || null); });
    Live.on('experiment_cancelled', m => { if (m.id === eid) settle(null); });
    const iv = setInterval(() => {
      if (!Live.connected) settle(null);
      if (RUN && RUN.finished) clearInterval(iv);
    }, 700);
    setTimeout(() => { if (RUN === tok && !(RUN && RUN.finished)) settle(null); }, 600000);
  });
}
function benchProg(text) {
  const p = $('prog');
  if (p) p.textContent = text;
}
function setOut(html) {
  const o = $('out');
  if (o) o.innerHTML = html;
  const cv = $('curve');
  if (cv) cv.innerHTML = '';
  return o;
}
function onDoneFollow(m) {
  if (RUN && RUN.followed && m.record) followDone(m.record);
  else if (RUN && RUN.followed) { RUN = null; setRunEnabled(Live.connected); benchProg('The run ended elsewhere.'); }
}
function onCancelledFollow(m) {
  if (RUN && RUN.followed) { RUN = null; setRunEnabled(Live.connected); benchProg('The run ended elsewhere.'); }
}
/* Rebuild what page navigation wiped: run section, question, and the exact card
   the run started with (stored at ack — never recompiled, since compile could
   differ and showCard wipes RUN). Only when the stored id matches the live
   run; a question the user already typed always wins. */
function restoreRunContext(liveId) {
  const run = $('run');
  if (run) run.hidden = false;
  const p = pendGet();
  if (!p || p.id !== liveId) return;
  if (p.q && $('q') && !$('q').value) $('q').value = p.q;
  if (!CARD && p.card && $('card')) {
    try { CARD = p.card; renderCard(p.q || ''); } catch { CARD = null; }
  }
}
function onState(s) {
  if (!K) return;   // knowledge still loading — the next state rejoins
  lastStateTs = Date.now();
  if (!RUN) {
    // Runs live on the server, not the page: rejoin one started elsewhere (or before navigating away).
    if (!s.experiment) return;
    RUN = { trace: [], groups: {}, followed: true, id: s.experiment.id };
    setRunEnabled(false);
    restoreRunContext(s.experiment.id);
    benchProg('Rejoined a running experiment…');
  }
  const x = s.experiment;
  if (x) benchProg(`${x.condition || ''} · repeat ${x.repeat || '?'} of ${x.n_rep || '?'} · ${Math.round(100 * (x.progress || 0))}%`);
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
  if (!cv || !RUN || RUN.trace.length < 2) return;
  cv.style.display = 'block';
  const ctx = cv.getContext('2d'), W = cv.width, H = cv.height, tr = RUN.trace;
  const mx = Math.max(0.1, ...tr);
  const css = getComputedStyle(document.documentElement);
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
const FOOD_STOP = new Set(['what', 'whats', 'does', 'do', 'are', 'is', 'in', 'the', 'and', 'food', 'foods', 'contain', 'contains', 'dose', 'a', 'an', 'of', 'any',
  // Atlas vocabulary leaking from region questions must never route to foods:
  // "male" once sent a male-subset card to Male Fern (Duke's).
  'male', 'female', 'left', 'right', 'subset', 'subsets']);
function findFood(q) {
  if (!FOODS) return null;
  // Head words only ("Asparagus" in "Asparagus, all types, raw"), whole-word:
  // qualifier words like "types"/"raw" must never match ("type" once routed a
  // brain region to asparagus).
  const words = q.toLowerCase().split(/[^a-z]+/).filter(w => w.length > 3 && !FOOD_STOP.has(w));
  let best = null;
  for (const name of Object.keys(FOODS)) {
    const head = name.split(',')[0].toLowerCase().split(/[^a-z]+/).filter(w => w.length > 3);
    const hits = words.filter(w => head.includes(w)).length;
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
/* Atlas "ask about this region" links carry an explicit region id, so region
   questions never fall into the food matcher (11/66 did). Pure file readout. */
async function renderRegion(rowId, question, level) {
  if (question && $('q')) $('q').value = question;
  setOut(''); benchProg('Looking up the atlas entry…');
  try {
    // Row ranges are disjoint (regions 4–69, cell types 1000+, sides 2000+,
    // male subsets 3000+, sense groups 4000+), so infer the file when an older
    // link omits the level marker. A non-numeric id is an Atlas name: search
    // every file.
    const want = String(rowId).trim();
    const numeric = /^\d+$/.test(want);
    const n = numeric ? parseInt(want, 10) : -1;
    const lv = level || (n >= 4000 ? 'sense' : n >= 3000 ? 'subset' : n >= 2000 ? 'side' : n >= 1000 ? 'celltype' : '');
    const files = lv === 'celltype' ? ['library/celltypes.json']
      : lv === 'side' ? ['library/sides.json']
      : lv === 'subset' ? ['library/subsets.json']
      : lv === 'sense' ? ['library/sensegroups.json']
      : numeric ? ['library/data.json']
      : ['library/data.json', 'library/celltypes.json', 'library/sides.json', 'library/subsets.json', 'library/sensegroups.json'];
    let e = null, file = files[0];
    for (const f of files) {
      const res = await fetch(f);
      if (!res.ok) continue;
      const entries = await res.json();
      e = (entries || []).find(x => String(x.row) === want || (x.name || '').toLowerCase() === want.toLowerCase());
      if (e) { file = f; break; }
    }
    if (!e) throw new Error('entry not in the atlas');
    const kind = file.includes('celltypes') ? 'fly cell type'
      : file.includes('sides') ? 'brain side'
      : file.includes('subsets') ? 'male subset'
      : file.includes('sensegroups') ? 'sense group' : 'fly brain region';
    const cls = [e.cls, e.sub, e.flow].filter(Boolean).join(' · ');
    CARD = { mode: 'explain', feasible: false, region: e.name,
      title: `${e.name} — ${kind}`,
      paragraphs: [`Does: ${e.func || 'function not on file'}.`, e.desc || 'No description on file.'],
      refs: [`Fly Atlas (${file})`, 'Dorkenwald et al. 2024, Nature (wiring)'],
      notes: [`Atlas hub: ${e.hub || '?'}, level: ${e.level || '?'}`].concat(cls ? [`Class: ${cls}`] : []),
      stages: (typeof IMG !== 'undefined' && IMG[e.name]) ? [e.name] : [] };
    benchProg('done');
    renderCard(question);
  } catch (err) {
    benchProg('Atlas lookup failed — asking as a plain question.');
    showCard(question, true);
  }
}
/* Atlas "ask about this hypothesis" links carry an experiment card key (?hyp=).
   The explainer is compiled from the card's own data — nothing invented. */
async function renderHypothesis(key) {
  setOut(''); benchProg('Reading the experiment card…');
  try {
    const res = await fetch('experiments/data.json');
    if (!res.ok) throw new Error('atlas data missing');
    const data = await res.json();
    const c = (data.cards || []).find(x => x.key === key);
    if (!c || !c.hypothesis) throw new Error('no hypothesis on that card');
    const h = c.hypothesis, r = (c.lab && c.lab.record) || {};
    CARD = { mode: 'explain', feasible: false,
      hyp: { key: c.key, title: c.title, text: h.text, given: h.given, falsified: h.falsified,
             verdict: r.verdict || '', record: r.title || '' },
      title: `${c.title} — hypothesis`,
      paragraphs: [
        h.text,
        `Rests on: ${h.given}`,
        `Would fail if: ${h.falsified}`,
        r.verdict ? `Latest run: ${r.verdict} — ${r.title || ''}.` : 'Not run yet.'],
      refs: (c.real_source ? [`Real flies (${c.real}): ${c.real_source}`] : [])
        .concat(c.evidence || []).concat([`Experiment Atlas card ${c.key}`]),
      notes: ["Reconstructed from the standing rules after the run — the card's rationale, not a sealed prediction"] };
    benchProg('done');
    renderCard(c.title + ' hypothesis');
    suggestChips(['Why this prediction?', 'Show the evidence', 'What would disprove it?']);
  } catch (err) {
    benchProg('Hypothesis lookup failed — ' + ((err && err.message) || err));
  }
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
function findStimulus(q) {
  if (!K.stimuli || !/\blight\b/.test(q)) return null;
  if (!/(photoreceptor|visual|\beye\b|retina|\bsee\b|vision|optic)/.test(q)) return null;
  return /target/.test(q) ? 'targets' : 'eye';
}
function findMutant(q) {
  if (!K.mutants) return null;
  if (!/(mutant|null|knockout|\bko\b)/.test(q)) return null;
  if (q.includes('ninae')) return 'ninaE_ko';
  if (/\borco\b/.test(q)) return 'Orco_ko';
  if (/\bpara\b/.test(q)) return 'para_ko';
  if (q.includes('gr66a')) return 'Gr66a_ko';
  return null;
}
function compileMutant(name) {
  const m = K.mutants[name];
  const card = {
    title: m.title, hypothesis: m.hypothesis,
    control: { name: 'control box', setup: [{ cmd: 'restore' }] },
    treatments: [{ name: m.treatment_name, setup: m.setup }],
    measure: m.measure, prediction: m.prediction,
    secondary: m.secondary || [], secondary_prediction: m.secondary_prediction || [],
    repeats: 5, steps: 200, author: 'ask-page',  // quick mode (for now)
  };
  return { feasible: true, substance: name, dosable: false, mutant: name,
    matched: null, measure: m.measure, prediction: m.prediction, secondary: card.secondary, card,
    expect: { direction: m.prediction, measure: m.measure, why: m.why },
    evidence: m.evidence, caveat: m.not_modelled, confidence: m.confidence,
    effect_confidence: m.effect_confidence, mechanism_confidence: m.mechanism_confidence,
    mechanism: m.mechanism, kind: m.kind, facts: m.facts, badge: 'local' };
}
function compileStimulus(assay) {
  const L = K.stimuli.light, a = L.assays[assay];
  const card = {
    title: a.title, hypothesis: a.hypothesis,
    control: { name: 'control box', setup: [{ cmd: 'restore' }] },
    treatments: [{ name: a.treatment_name, setup: a.setup }],
    measure: a.measure, prediction: a.prediction,
    secondary: a.secondary || [], secondary_prediction: a.secondary_prediction || [],
    repeats: 5, steps: 200, author: 'ask-page',  // quick mode (for now)
  };
  return { feasible: true, substance: 'light', dosable: false, stimulus: assay,
    matched: null, measure: a.measure, prediction: a.prediction, secondary: card.secondary, card,
    expect: { direction: a.prediction, measure: a.measure, why: a.why },
    evidence: L.evidence, caveat: L.not_modelled, confidence: L.confidence,
    effect_confidence: L.effect_confidence, mechanism_confidence: L.mechanism_confidence,
    mechanism: L.mechanism, kind: L.kind, facts: a.facts, badge: 'local' };
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
    treatments: [{ name: `${sub.name} ${DOSE_DEFAULT}`, setup: [{ cmd: 'restore' }, { cmd: 'dose', drug: sub.name, amount: DOSE_DEFAULT }] }],
    measure, prediction, secondary, secondary_prediction: secondary.map(b =>
      b === 'banana_dist' ? { higher: 'lower', lower: 'higher', different: 'different' }[s.expect.direction] : s.expect.direction),
    repeats: 5, steps: 200, author: 'ask-page',  // quick mode (for now): ~40s a card, verdicts still valid (rule 2 needs 5)
  };
  if (sub.name === 'deet') {  // S3 contact route: taste current in BOTH boxes or the channel confounds
    card.control.setup.push({ cmd: 'set_param', key: 'I_taste', value: 2.0 });
    card.treatments[0].setup.splice(1, 0, { cmd: 'set_param', key: 'I_taste', value: 2.0 });
  }
  return { feasible: true, substance: sub.name, matched: sub.alias, measure, prediction, secondary, card,
    dose: DOSE_DEFAULT, dosable: true,
    expect: s.expect, evidence: s.evidence, caveat: s.not_modelled, confidence: s.confidence,
    effect_confidence: s.effect_confidence, mechanism_confidence: s.mechanism_confidence,
    mechanism: s.mechanism, kind: s.kind, drug_class: s.drug_class, note_extra,
    acts_where: s.acts_where || null };
}
async function showCard(question, triedRegion = false) {
  try {
    await showCardInner(question, triedRegion);
  } catch (err) {
    CARD = null;
    $('run').hidden = true;
    benchProg('Could not write this card: ' + (err && err.message ? err.message : err));
  }
}
async function showCardInner(question, triedRegion = false) {
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
    if (!triedRegion) {  // Atlas phrasing ("what does X do") answers from the atlas by name,
      const rm = question.match(/^what does (.+?) do(?:[\s(?.]|$)/i);  // even with no ?region= link
      if (rm && rm[1].trim().length >= 2) { await renderRegion(rm[1].trim(), question, ''); return; }
    }
    const stim = findStimulus(q);  // before food: "light" is also a food word (light ice cream)
    if (stim) { CARD = compileStimulus(stim); renderCard(question); return; }
    const mut = findMutant(q);  // before gene: explicit mutant intent runs an experiment, not an info card
    if (mut) { CARD = compileMutant(mut); renderCard(question); return; }
    const food = !findSubstance(q) ? findFood(q) : null;
    if (food) { CARD = compileFood(food); renderCard(question); return; }
    const fb = findGene(q);
    if (fb) { CARD = compileGene(fb); renderCard(question); return; }
  }
  // Known substance: answer from the local compiler instantly — never park a
  // runnable card behind a slow AI call (a stuck compile used to freeze Ask
  // for the full server timeout with a dead Run button).
  const local = trio.length === 1 ? compileCard(question) : null;
  if (local && local.feasible) {
    CARD = local;
    CARD.badge = 'local';
    renderCard(question);
    return;
  }
  let server = null;
  if (Live.connected) server = await askp({ cmd: 'compile_card', question }, 30000);
  if (server && server.ok && server.data) {
    CARD = { ...server.data, feasible: true, matched: server.data.substance };
  } else {
    CARD = local || compileCard(question);
    if (CARD.feasible) CARD.badge = 'local';
  }
  renderCard(question);
}
function renderCard(question) {
  const el = $('card');
  const cv0 = $('curve');
  if (cv0) cv0.innerHTML = '';
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
  const dose = (typeof c.dose === 'number') ? c.dose : DOSE_DEFAULT;
  const stimWords = a => a.cmd === 'set_param' ? `set ${a.key} ${a.value}`
    : a.cmd === 'stimulus' ? `stimulus ${a.channel} level ${a.level}`
    : a.cmd === 'mutant' ? `raise her as a ${a.name} mutant (${((K.mutants || {})[a.name] || {}).title || 'lesion on file'})` : a.cmd;
  const stimSetup = ((c.card.treatments || [])[0] || {}).setup || [];
  const stimLine = `Treatment box: restore, ${stimSetup.filter(a => a.cmd !== 'restore').map(stimWords).join(', ')}, run.` +
    (stimSetup.some(a => a.cmd === 'set_param' && a.key === 'I_bg') ? ' (I_bg 2.2 is the raised awake floor for target assays.)' : '');
  const proc = (c.card.procedure && c.card.procedure.length ? c.card.procedure : (c.stimulus || c.mutant) ? [
    'Control box: restore to clean, run.',
    stimLine,
    `Maxime and Hagrid run in both boxes. Control and treatment interleave, ${c.card.repeats} repeats each.`] : [
    c.substance === 'deet' ? 'Control box: restore to clean, set I_taste 2.0, run.' : 'Control box: restore to clean, run.',
    c.substance === 'deet'
      ? `Treatment box: restore, set I_taste 2.0, dose deet ${dose}, run${offNote(c.substance, dose)}.`
      : `Treatment box: restore, dose ${c.substance} ${dose}, run${offNote(c.substance, dose)}.`,
    `Maxime and Hagrid run in both boxes. Control and treatment interleave, ${c.card.repeats} repeats each.`]);
  const DOSE_NAMES = { 0.25: 'Low', 0.5: 'Standard', 1.5: 'High' };
  const engage = c.substance === 'theophylline'
    ? `<p class="dim">Adenosine arm ${Math.round(100 * hill(dose, 0.1, 1.5))}% engaged; PDE arm ${Math.round(100 * hill(dose, 3.1, 4))}% engaged at dose ${dose}. Above ~1.8 the second action joins (prediction).</p>` : '';
  const ctrlExtra = (((c.card.control || {}).setup) || []).filter(a => a.cmd !== 'restore').map(stimWords).join(', ');
  const treatExtra = stimSetup.filter(a => a.cmd !== 'restore' && a.cmd !== 'dose').map(stimWords).join(', ');
  const doseWord = DOSE_NAMES[dose];
  const controlGloss = `The comparison group: Maxime and Hagrid in a clean box${ctrlExtra ? ', with ' + ctrlExtra : ', nothing added'}. Every result is measured against these two.`;
  const treatGloss = c.dosable === false
    ? (treatExtra || 'How the treatment box differs — see Procedure below.')
    : `${doseWord ? doseWord + ' ' + dose : 'dose ' + dose} — a unitless knob on the sim, not milligrams${treatExtra ? '; with ' + treatExtra : ''}.`;
  const effortGloss = 'How much evidence the run collects (rule 2 needs at least 5 per side for a verdict).';
  const doseGloss = 'Re-run weaker or stronger to see if the effect grows with dose.';
  const sec = (c.card.secondary || c.secondary || []).map(b => K.measures[b]).filter(Boolean).join(' + ');
  el.innerHTML = `<span class="eyebrow">${esc(c.measure)} · run card</span>
    <h1>${esc(c.card.title)}</h1>
    <div class="pills"><span>${esc(c.substance)}</span><span>target: ${esc(K.measures[c.measure])}</span>${sec ? `<span>behaviour: ${esc(sec)}</span>` : ''}<span>${esc(c.kind)}</span></div>
    <p>${esc(c.card.hypothesis)} Mechanism: ${esc(c.mechanism)}.</p>
    ${c.acts_where ? `<p>Where it acts: ${esc(c.acts_where)}</p>` : ''}
    ${c.facts ? `<p class="dim">${esc(c.facts)}</p>` : ''}
    ${c.note_extra ? `<p class="dim">${esc(c.note_extra)}</p>` : ''}
    <h2>Key Traits</h2>
    <div class="traits">
      <div class="trait"><svg class="ic"><use href="#i-flask"/></svg><span class="k">Control</span><span>clean box, both flies<br><small class="dim">${esc(controlGloss)}</small></span></div>
      <div class="trait"><svg class="ic"><use href="#i-fish"/></svg><span class="k">Treatment</span><span>${c.dosable === false ? esc(((c.card.treatments || [])[0] || {}).name || c.substance) : `${esc(c.substance)} ${dose}`}, both flies<br><small class="dim">${esc(treatGloss)}</small></span></div>
      <div class="trait"><svg class="ic"><use href="#i-gauge"/></svg><span class="k">Effort</span><span>${c.card.repeats} repeats of ${c.card.steps} steps<br><small class="dim">${esc(effortGloss)}</small></span></div>
      <div class="trait"><svg class="ic"><use href="#i-globe"/></svg><span class="k">Prediction</span><span>treatment ${esc(PRED_WORD[c.expect.direction])} — ${esc(c.expect.why)}</span></div>
      ${c.dosable === false ? '' : `<div class="trait"><svg class="ic"><use href="#i-drop"/></svg><span class="k">Dose</span><span><span class="doseseg" role="group" aria-label="Dose">${DOSES.map(d =>
        `<button data-dose="${d}" aria-pressed="${d === dose}">${DOSE_NAMES[d]} ${d}</button>`).join('')}</span><br><small class="dim">${esc(doseGloss)}</small></span></div>`}
    </div>
    ${engage}
    <h2>Procedure</h2><ul class="tight">${proc.map(p => `<li>${esc(p)}</li>`).join('')}</ul>
    ${c.dosable === false ? '' : '<p><button class="go" id="curveBtn">Dose–response</button></p>'}
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
  el.querySelectorAll('[data-dose]').forEach(b => b.onclick = () => setDose(parseFloat(b.dataset.dose)));
  const cb = el.querySelector('#curveBtn');
  if (cb) cb.onclick = plotDoseCurve;
}
function setDose(d) {
  if (!CARD || !CARD.feasible || CARD.dosable === false || !DOSES.includes(d)) return;
  CARD.dose = d;
  const t = (CARD.card.treatments || [])[0];
  if (t) {
    t.name = `${CARD.substance} ${d}`;
    const setup = (t.setup || []).find(a => a.cmd === 'dose');
    if (setup) setup.amount = d;
  }
  try { renderCard(($('q') || {}).value || ''); } catch {}
}

/* ---- dose–response: pool notebook records for this drug + measure, plot vs dose ---- */
function poolCurve(recs, measure, sub) {
  const byDose = {};
  for (const rec of recs) {
    const pf = (rec.details || {}).per_fly || {};
    const single = (rec.card.treatments || []).length <= 1;
    for (const f of Object.keys(pf)) {
      for (const e of (pf[f] || [])) {
        const m = ((e || {}).measures || {})[measure];
        if (!m) continue;
        // Match the entry to this drug: comparison records carry one entry per member.
        let dk = null;
        const mm = String((e || {}).treatment || '').match(/^(.+?)\s+([\d.]+)\s*$/);
        if (mm && mm[1].replace(/ /g, '_') === sub) dk = Math.round(parseFloat(mm[2]) * 1000) / 1000;
        else if (!mm && single) dk = Math.round(recDose(rec) * 1000) / 1000;
        else continue;
        const slot = (byDose[dk] = byDose[dk] || { t: [], c: [] });
        const tv = (m.values || []).filter(v => typeof v === 'number');
        const cv = (m.control_values || []).filter(v => typeof v === 'number');
        if (tv.length) slot.t.push(...tv);
        else if (typeof m.treatment_mean === 'number') slot.t.push(m.treatment_mean);
        if (cv.length) slot.c.push(...cv);
        else if (typeof m.control_mean === 'number') slot.c.push(m.control_mean);
      }
    }
  }
  return byDose;
}
const mean = vs => vs.reduce((a, b) => a + b, 0) / Math.max(1, vs.length);
function curveSVG(sub, measureName, pts, controlMean, withMarker) {
  const W = 600, H = 300, L = 64, R = 16, T = 16, B = 44;
  const all = pts.flatMap(p => [p.tm, p.cm]).concat([controlMean]).filter(v => typeof v === 'number');
  let lo = Math.min(...all), hi = Math.max(...all);
  if (hi - lo < 1e-9) { hi = lo + 1; lo = Math.max(0, lo - 0.1); }
  const pad = (hi - lo) * 0.15; lo = Math.max(0, lo - pad); hi += pad;
  const X = d => L + (d / 2) * (W - L - R);
  const Y = v => T + (1 - (v - lo) / (hi - lo)) * (H - T - B);
  const f3 = v => (Math.round(v * 1000) / 1000).toString();
  let s = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="dose response curve">`;
  s += `<rect x="0" y="0" width="${W}" height="${H}" fill="none"/>`;
  for (let i = 0; i <= 4; i++) {
    const v = lo + (hi - lo) * i / 4, y = Y(v);
    s += `<line x1="${L}" y1="${y}" x2="${W - R}" y2="${y}" stroke="#e7e0d1" stroke-width="1"/>`;
    s += `<text x="${L - 8}" y="${y + 4}" text-anchor="end" font-size="11" fill="#6d685d">${f3(v)}</text>`;
  }
  for (const d of CURVE_DOSES) {
    const x = X(d);
    s += `<line x1="${x}" y1="${H - B}" x2="${x}" y2="${H - B + 5}" stroke="#6d685d" stroke-width="1"/>`;
    s += `<text x="${x}" y="${H - B + 20}" text-anchor="middle" font-size="11" fill="#6d685d">${d}</text>`;
  }
  s += `<line x1="${L}" y1="${T}" x2="${L}" y2="${H - B}" stroke="#1b1a17" stroke-width="1"/>`;
  s += `<line x1="${L}" y1="${H - B}" x2="${W - R}" y2="${H - B}" stroke="#1b1a17" stroke-width="1"/>`;
  s += `<text x="${(L + W - R) / 2}" y="${H - 4}" text-anchor="middle" font-size="12" fill="#1b1a17">dose (units 0–2)</text>`;
  const cy = Y(controlMean);
  s += `<line x1="${L}" y1="${cy}" x2="${W - R}" y2="${cy}" stroke="#a8a090" stroke-width="1.5" stroke-dasharray="6 4"/>`;
  s += `<text x="${W - R}" y="${cy - 6}" text-anchor="end" font-size="11" fill="#6d685d">control ${f3(controlMean)}</text>`;
  if (withMarker) {
    const mx = X(PDE_JOIN);
    s += `<line x1="${mx}" y1="${T}" x2="${mx}" y2="${H - B}" stroke="#b45309" stroke-width="1.5" stroke-dasharray="3 3"/>`;
    s += `<text x="${mx + 6}" y="${T + 14}" font-size="11" fill="#b45309">second action joins ~1.8 (prediction)</text>`;
  }
  const line = pts.map(p => `${X(p.dose)},${Y(p.tm)}`).join(' ');
  if (pts.length > 1) s += `<polyline points="${line}" fill="none" stroke="#0f766e" stroke-width="2"/>`;
  for (const p of pts) {
    s += `<circle cx="${X(p.dose)}" cy="${Y(p.tm)}" r="5" fill="#0f766e"/>`;
    s += `<text x="${X(p.dose)}" y="${Y(p.tm) - 10}" text-anchor="middle" font-size="11" fill="#1b1a17">n=${p.n}</text>`;
  }
  s += `<text x="${L}" y="${T - 2}" font-size="12" fill="#1b1a17">${esc(sub)} dose–response: ${esc(measureName)}</text></svg>`;
  return s;
}
async function plotDoseCurve() {
  if (!CARD || !CARD.feasible || CARD.mode === 'compare') return;
  const cv = $('curve');
  if (!cv) return;
  if (!Live.connected) { cv.innerHTML = '<p class="no">needs the lab — start the server</p>'; return; }
  const sub = CARD.substance, measure = CARD.measure;
  cv.innerHTML = '<p class="dim">Pooling notebook records…</p>';
  const status = () => cv.querySelector('p');
  try {
    const list = await askp({ cmd: 'list_notebook' }, 30000);
    const items = ((list && list.notebook) || []);
    const slug = sub.toLowerCase().replace(/[ _]+/g, '-');
    const cands = items.filter(r => String(r.title || '').toLowerCase().includes(slug)).slice(-40);
    const recs = [];
    let excluded = 0;
    for (let i = 0; i < cands.length; i++) {
      const st = status(); if (st) st.textContent = `Pooling notebook records… ${i + 1}/${cands.length}`;
      const a = await askp({ cmd: 'get_experiment', exp: cands[i].id }, 30000);
      const rec = a && a.ok && a.data && a.data.record;
      if (!rec || !rec.card || rec.card.measure !== measure) continue;
      const dosed = (rec.card.treatments || []).some(t => ((t.setup || []).find(x => x.cmd === 'dose') || {}).drug === sub);
      if (!dosed) continue;
      if (rec.weight_model !== 'norm-v1') { excluded++; continue; }  // raw-weights era (pre-recalibration)
      if (REPARAM.has(sub) && rec.dose_model !== 'chembl-v1') { excluded++; continue; }
      recs.push(rec);
    }
    if (!recs.length) {
      cv.innerHTML = `<p class="dim">No ${esc(sub)} records on ${esc(K.measures[measure])} yet` +
        (excluded ? ` under the current dose model (${excluded} older record${excluded === 1 ? '' : 's'} excluded)` : '') +
        ` — run the card, then plot again.</p>`;
      return;
    }
    const byDose = poolCurve(recs, measure, sub);
    const pts = Object.keys(byDose).map(Number).sort((a, b) => a - b)
      .filter(d => byDose[d].t.length)
      .map(d => ({ dose: d, tm: mean(byDose[d].t), cm: mean(byDose[d].c), n: byDose[d].t.length }));
    const allC = Object.values(byDose).flatMap(s => s.c);
    const controlMean = mean(allC);
    const have = new Set(pts.map(p => p.dose));
    const missing = CURVE_DOSES.filter(d => !have.has(d));
    cv.innerHTML = curveSVG(sub, K.measures[measure], pts, controlMean, sub === 'theophylline') +
      `<p class="dim">Pooled from ${recs.length} record${recs.length === 1 ? '' : 's'} (treatment means, both flies).` +
      (excluded ? ` ${excluded} older record${excluded === 1 ? '' : 's'} under a previous model excluded — re-run to refill.` : '') +
      (missing.length ? ` No runs yet at ${missing.join(', ')}.</p>
      <p><button class="go" id="sweepBtn">Run ${missing.length} missing dose${missing.length === 1 ? '' : 's'}</button></p>` : '</p>');
    const sb = cv.querySelector('#sweepBtn');
    if (sb) sb.onclick = () => runSweep(missing);
  } catch (err) {
    cv.innerHTML = `<p class="no">Could not pool records: ${esc(err.message || err)}</p>`;
  }
}
async function runSweep(doses) {
  if (RUN) { benchProg('An experiment is already running — wait for it, then sweep.'); return; }
  if (!CARD || !CARD.feasible || CARD.mode === 'compare' || !Live.connected) return;
  RUN = { trace: [], groups: {}, sweep: true };
  setRunEnabled(false);
  try {
    for (let i = 0; i < doses.length; i++) {
      benchProg(`Dose sweep ${i + 1}/${doses.length}: dose ${doses[i]}…`);
      const card = JSON.parse(JSON.stringify(CARD.card));
      const keep = ((((CARD.card.treatments || [])[0] || {}).setup) || [])
        .filter(a => !(a.cmd === 'dose' && a.drug === CARD.substance));  // preserve set_param etc (deet I_taste)
      card.treatments = [{ name: `${CARD.substance} ${doses[i]}`,
        setup: [...keep, { cmd: 'dose', drug: CARD.substance, amount: doses[i] }] }];
      const ack = await askp({ cmd: 'run_experiment', card }, 30000);
      if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'no answer in 30s');
      const rec = await waitDone(ack.data.id);
      if (!rec) throw new Error('run ended early at dose ' + doses[i]);
    }
    benchProg('Sweep done — pooling.');
    await plotDoseCurve();
    benchProg('done');
  } catch (err) {
    benchProg('Sweep stopped: ' + (err.message || err));
  }
  RUN = null;
  setRunEnabled(Live.connected);
}

function renderExplain() {
  const c = CARD, el = $('card');
  el.hidden = false;   // direct renders (?hyp=, ?region=) bypass showCardInner's unhide
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
  if (RUN && RUN.followed) { benchProg('An experiment is already running — following it.'); return; }
  if (!CARD || !CARD.feasible || RUN) return;
  const quietMs = lastStateTs ? Date.now() - lastStateTs : 1e9;
  if (!Live.connected || quietMs > 6000) {
    benchProg('Lab is quiet — reconnecting. Wait for the green dot, then run again.');
    try { Live.ws && Live.ws.close(); } catch {}
    return;
  }
  setRunEnabled(false);
  setOut('');
  RUN = { trace: [], groups: {} };
  benchProg('Writing the card…');
  try {
    const ack = await askp({ cmd: 'run_experiment', card: CARD.card });
    if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'no answer in 30s — check the server terminal for a traceback');
    pendSet({ id: ack.data.id, q: $('q') ? $('q').value : '', card: CARD });  // waiting room: survive navigation
    const rec = await waitDone(ack.data.id);
    RUN.finished = true;
    if (!rec) throw new Error(Live.connected ? 'run ended early' : 'lab dropped mid-run — stopping, not reading stale numbers');
    benchProg('done');
    renderResults(rec);
  } catch (err) {
    benchProg('Run failed: ' + err.message);
    RUN = null;
    pendClear();   // failed runs leave nothing to rejoin — don't haunt the next visit
    setRunEnabled(Live.connected);
  }
}

/* ---- waiting room: a run outlives page navigation, so remember what we wait for ----
   session key, set on run ack, cleared when the record lands. On boot, a pending
   key either renders the finished record ("finished while you were away") or
   follows the still-running experiment to its done event. */
const WAIT_KEY = 'ask-waiting';
function pendGet() { try { return JSON.parse(sessionStorage.getItem(WAIT_KEY) || 'null'); } catch { return null; } }
function pendSet(v) { try { sessionStorage.setItem(WAIT_KEY, JSON.stringify(v)); } catch {} }
function pendClear() { try { sessionStorage.removeItem(WAIT_KEY); } catch {} }
function followPending() {
  const c = cmpGet();
  if (c && c.members && !RUN) { resumeCompare(); return; }  // unfinished trio wins over everything
  const p = pendGet();
  if (!p || !p.id || RUN) return;
  benchProg('Looking up your run…');
  askp({ cmd: 'get_experiment', exp: p.id }).then(ack => {
    if (ack && ack.ok && ack.data && ack.data.record) {
      try {
        if (p.q && $('q')) $('q').value = p.q;
        followDone(ack.data.record);
        benchProg('Finished while you were away — record below.');
      } catch (err) {
        benchProg('Found your finished run but could not show it: ' + (err.message || err));
      }
      pendClear();
      return;
    }
    // No record yet: the run is still going (live rejoin shows it), or gone.
    benchProg('Rejoining your run…');
    let settled = false;
    const done = ok => {
      if (settled) return; settled = true; pendClear();
      if (!ok) benchProg('That run is gone — the lab must have restarted. Run again.');
    };
    Live.on('experiment_done', m => {
      if (m.id !== p.id || settled) return;
      try {
        if (p.q && $('q')) $('q').value = p.q;
        followDone(m.record);
        benchProg('Finished while you were away — record below.');
      } catch (err) {
        benchProg('That run finished but could not be shown: ' + (err.message || err));
      }
      settled = true; pendClear();
    });
    Live.on('experiment_cancelled', m => { if (m.id === p.id) done(false); });
    setTimeout(() => {
      if (settled) return;
      const x = Live.state && Live.state.experiment;
      if (!x || x.id !== p.id) {
        askp({ cmd: 'get_experiment', exp: p.id }).then(a2 => {
          if (settled) return;
          if (a2 && a2.ok && a2.data && a2.data.record) {
            try {
              if (p.q && $('q')) $('q').value = p.q;
              followDone(a2.data.record);
              benchProg('Finished while you were away — record below.');
            } catch (err) {
              benchProg('That run finished but could not be shown: ' + (err.message || err));
            }
          } else done(false);
          settled = true; pendClear();
        }).catch(() => done(false));
      }
    }, 10000);
  }).catch(() => {});
}

/* ---- results for a run joined mid-flight: rebuild the card shell from the record ---- */
function followDone(rec) {
  pendClear();
  if (!CARD) {
    const c = rec.card || {};
    const tname = (((c.treatments || [])[0] || {}).name || '').split(' ')[0];
    CARD = { card: c, measure: c.measure, substance: tname, secondary: c.secondary || [],
      expect: { direction: c.prediction || 'higher' }, feasible: true, followed: true,
      dosable: !!(K.substances[tname] && K.substances[tname].dosable) };
    if ($('card')) { try { renderCard(($('q') || {}).value || ''); } catch {} }
  }
  renderResults(rec);
  benchProg('done');
  RUN = null;
  setRunEnabled(Live.connected);
}

/* ---- results: two tables + stages + match ---- */
const badge = v => `<span class="verdict ${v === 'SUPPORTED' ? 'v-sup' : v === 'NOT SUPPORTED' ? 'v-not' : v === 'MIXED' ? 'v-mix' : 'v-cant'}">${esc(v || '?')}</span>`;
/* Stage means from the record itself (preferred: complete even when the run
   wasn't watched live). Source 1: pathway rows; source 2: mean over run firing
   shares. One source per render — shares and live firing/s never mix. */
function recGroups(rec) {
  const pf = (rec.details || {}).per_fly || {};
  const out = {};
  let found = false;
  for (const [name, cslot, tslot] of FLIES) {
    const rows = ((((pf[name] || [])[0] || {}).pathway) || []).filter(r => r && r.stage != null);
    if (!rows.length) continue;
    found = true;
    out[cslot] = {}; out[tslot] = {};
    for (const r of rows) {
      out[cslot][r.stage] = [typeof r.control_mean === 'number' ? r.control_mean : 0];
      out[tslot][r.stage] = [typeof r.treatment_mean === 'number' ? r.treatment_mean : 0];
    }
  }
  if (found) return out;
  const res = rec.results || {};
  const ctrl = rec.card && rec.card.control && rec.card.control.name;
  const tre = ((rec.card && rec.card.treatments) || [])[0];
  if (ctrl && tre && res[ctrl] && res[tre.name]) {
    for (const [name, cslot, tslot] of FLIES) {
      const cr = res[ctrl][name] || [], tr = res[tre.name][name] || [];
      if (!cr.length || !tr.length) continue;
      const firing = run => (run.metrics && run.metrics.firing) || run.firing || {};
      const stages = new Set();
      for (const run of cr.concat(tr)) for (const s of Object.keys(firing(run))) stages.add(s);
      if (!stages.size) continue;
      found = true;
      out[cslot] = {}; out[tslot] = {};
      for (const s of stages) {
        const mean = runs => {
          const vs = runs.map(run => firing(run)[s]).filter(v => typeof v === 'number');
          return [vs.reduce((a, b) => a + b, 0) / Math.max(1, vs.length)];
        };
        out[cslot][s] = mean(cr); out[tslot][s] = mean(tr);
      }
    }
  }
  return found ? out : null;
}
function stageMeans(slot, groups) {
  const acc = ((groups) || {})[slot] || {};
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
  const RG = recGroups(rec);                       // record first: complete even unwatched
  const G = RG || (RUN && RUN.groups) || null;     // else live samples, else nothing
  for (const [name, cslot, tslot] of FLIES) {
    const c = stageMeans(cslot, G), t = stageMeans(tslot, G);
    const mx = Math.max(0.01, ...K.stages.map(s => Math.max(c[s], t[s])));
    const ups = K.stages.map(s => [s, (t[s] - c[s]) / Math.max(c[s], 1e-6)]).sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]));
    const big = ups[0];
    const tiny = Math.abs(big[1]) < 0.05;
    topImgs.push({ name, c, t });
    stageHtml += `<h3>${esc(name)} — stages (share firing)</h3>` + K.stages.map(s =>
      `<div class="stagebar"><span class="nm">${esc(s)}</span>
       <span class="bar"><i style="width:${(100 * c[s] / mx).toFixed(1)}%"></i></span>
       <span class="bar t"><i style="width:${(100 * t[s] / mx).toFixed(1)}%"></i></span></div>`).join('') +
      ((RG || (RUN && !RUN.followed))
        ? `<p class="dim">control (blue) vs treatment (teal). Biggest change: <b>${esc(big[0])}</b> (${big[1] >= 0 ? '+' : ''}${Math.round(100 * big[1])}%${tiny ? ' — no real change' : ''}).</p>`
        : (!RUN
        ? `<p class="dim">control (blue) vs treatment (teal). No stage data on this record — verdict and tables above are complete.</p>`
        : `<p class="dim">control (blue) vs treatment (teal). Joined mid-run — stage sampling started when you returned, so these bars are partial.</p>`));
  }
  const movers = topImgs.map(({ name, c, t }) => {
    const ranked = K.stages.map(s => [s, (t[s] - c[s]) / Math.max(c[s], 1e-6)])
      .sort((a, b) => b[1] - a[1]).filter(([s]) => BINS[s]).slice(0, 3);
    const links = ranked.map(([s, rel]) =>
      `<a href="library/#/${BINS[s][0]}">${esc(s)}</a> (${rel >= 0 ? '+' : ''}${Math.round(100 * rel)}%)`).join(' · ') ||
      'no mapped stage moved';
    return `<p class="dim">${esc(name)} in the Fly Atlas: ${links}.</p>`;
  }).join('');
  FLYVALS = topImgs.map(({ t }) => t);
  Stage.showFly(Stage.fly);
  setCaption(`${rec.verdict} — treatment ${PRED_WORD[CARD.expect.direction]} vs control.`);
  setFeatured(CARD.card.title, `Verdict: ${rec.verdict}.`);
  setOut(`<h2>Control box</h2><p class="dim">Untouched. This is what normal looks like for this run.</p>${tbl('control', 'control', CARD.measure)}
    <h2>Treatment box — ${esc(treatLabel(rec, CARD.substance))}</h2>${tbl('treatment', 'treatment', CARD.measure)}${secTbls}<p>Overall: ${badge(rec.verdict)}</p><p><button class="go" id="watchBtn">Watch this run replay</button></p>
    <h2>Where it changed</h2>${stageHtml}${movers}
    <h2>Did it match?</h2>${matchHtml(rec)}`);
  const wb = document.getElementById('watchBtn');
  if (wb) wb.onclick = () => location.assign('ask/watch/?id=' + encodeURIComponent(rec.id));
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
  const seedTxt = (() => {  // seeds stay full in the record; the page shows only the shape
    const runs = Array.isArray(rec.seeds) ? rec.seeds : [];
    if (!runs.length) return 'runs not individually seeded';
    const nums = runs.flatMap(r => Object.values((r && r.seeds) || {})).filter(v => typeof v === 'number');
    if (!nums.length) return `${runs.length} runs seeded`;
    const boxes = Object.keys((runs[0] && runs[0].seeds) || {}).length;
    return `${runs.length} runs × ${boxes} boxes, seeds ${Math.min(...nums)}–${Math.max(...nums)}`;
  })();
  return `<p>${head}</p>${story || `<p class="dim">No explanation on this record — re-run to get the pathway story.</p>`}
    ${sus.length ? `<p class="dim">Suspects: ${esc(sus.join('; '))}.</p>` : ''}
    <details class="dim"><summary>Seeds: ${esc(seedTxt)}. Record ${esc(rec.id || '?')}.</summary><p>${rec.seeds ? esc(JSON.stringify(rec.seeds)) : ''}</p></details>`;
}

/* ---- comparison: two or more substances, one measure ---- */
const COMPARE_WORDS = [' vs ', 'versus', 'compare', 'comparison', 'methylxanthine', 'strongest', 'stronger', 'weakest', 'difference between', 'rank', 'trio', 'all three'];
const FOLLOWUP_WORDS = ['why', 'how come', 'strongest', 'weakest', 'evidence', 'paper', 'papers', 'reference', 'limit', 'not modelled', 'not modeled', 'caveat', 'assum', 'run it', 'run them', 'again', 'what next', 'what to ask', 'explain', 'mechanism', 'disprov', 'falsif'];
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
  if (!THREAD) return !!(CARD && CARD.mode === 'explain' && CARD.hyp && FOLLOWUP_WORDS.some(w => q.includes(w)));
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
        measure, prediction: s.expect.direction, repeats: 5, steps: 200, author: 'ask-page',  // quick mode (for now)
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
    <p>One run per member, same measure (${esc(K.measures[CARD.measure])}), same effort: 5 repeats of 200 steps, Maxime and Hagrid in both boxes.</p>
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
async function runOneCard(card, onAck) {
  const ack = await askp({ cmd: 'run_experiment', card });
  if (!ack || !ack.ok) throw new Error((ack && ack.error) || 'no answer in 30s — check the server terminal for a traceback');
  if (onAck) onAck(ack.data.id);
  return waitDone(ack.data.id);
}

/* ---- comparison resume: a trio is several server runs driven by one page loop.
   The loop dies on navigation, so persist member state; on boot, adopt finished
   members (fetch their records), wait out any in-flight member, run the rest. */
const CMP_KEY = 'ask-compare';
function cmpGet() { try { return JSON.parse(sessionStorage.getItem(CMP_KEY) || 'null'); } catch { return null; } }
function cmpSet(v) { try { sessionStorage.setItem(CMP_KEY, JSON.stringify(v)); } catch {} }
function cmpClear() { try { sessionStorage.removeItem(CMP_KEY); } catch {} }
async function resumeCompare() {
  const s = cmpGet();
  if (!s || !s.members || RUN) return;
  benchProg('Resuming your comparison…');
  CARD = { mode: 'compare', members: s.members, measure: s.measure,
    cards: s.members.map(n => ({ substance: n, card: (s.cards || {})[n] })), feasible: true };
  if (s.q && $('q')) $('q').value = s.q;
  THREAD = { members: s.members, measure: s.measure, results: {}, focus: null };
  // Adopt finished members (records live on the server, keyed by run id).
  for (const n of s.members) {
    const st = (s.results || {})[n];
    if (st && st.status === 'done' && st.id) {
      try {
        const a = await askp({ cmd: 'get_experiment', exp: st.id });
        if (a && a.ok && a.data && a.data.record) {
          THREAD.results[n] = { status: 'done', rec: a.data.record, gap: memberGap(a.data.record) };
          continue;
        }
      } catch {}
    }
    if (st && st.status && st.status !== 'done' && st.status !== 'pending') THREAD.results[n] = { status: st.status };
  }
  // An in-flight member at navigation time: it finishes server-side — wait for it, never re-run.
  if (s.current && s.current.eid && !THREAD.results[s.current.name]) {
    benchProg(`Rejoining ${s.current.name}…`);
    try {
      const a = await askp({ cmd: 'get_experiment', exp: s.current.eid });
      let rec = (a && a.ok && a.data && a.data.record) || null;
      if (!rec) rec = await waitDone(s.current.eid);
      if (rec) THREAD.results[s.current.name] = { status: 'done', rec, gap: memberGap(rec) };
    } catch {}
    s.current = null; cmpSet(s);
  }
  await runComparison();
}
function memberGap(rec) {
  const pf = (rec.details || {}).per_fly || {};
  const gaps = Object.keys(pf).map(f => (((pf[f] || [])[0] || {}).gap) || 0);
  return gaps.reduce((a, b) => a + b, 0) / Math.max(1, gaps.length);
}
async function runComparison() {
  if (!CARD || CARD.mode !== 'compare' || RUN) return;
  const quietMs = lastStateTs ? Date.now() - lastStateTs : 1e9;
  if (!Live.connected || quietMs > 6000) {
    benchProg('Lab is quiet — reconnecting. Wait for the green dot, then run again.');
    try { Live.ws && Live.ws.close(); } catch {}
    return;
  }
  setRunEnabled(false);
  setOut('');
  RUN = { trace: [], groups: {}, compare: true };
  const cards = {};
  for (const { substance: n, card } of CARD.cards) cards[n] = card;
  const save = (results, current) => cmpSet({ members: CARD.members, measure: CARD.measure,
    cards, q: $('q') ? $('q').value : '', results, current: current || null });
  const saved = {};
  for (const n of CARD.members) {
    const r = (THREAD.results || {})[n];
    saved[n] = r && r.status === 'done' && r.rec ? { status: 'done', id: r.rec.id } : { status: (r && r.status) || 'pending' };
  }
  save(saved, null);
  for (let i = 0; i < CARD.cards.length; i++) {
    const { substance: n, card } = CARD.cards[i];
    if ((THREAD.results[n] || {}).status === 'done') continue;  // adopted by resumeCompare — never re-run
    const st = runState(n);
    if (!st.runnable) { THREAD.results[n] = { status: st.why }; saved[n] = { status: st.why }; save(saved, null); continue; }
    benchProg(`Running ${n}… (${i + 1} of ${CARD.cards.length})`);
    try {
      const rec = await runOneCard(card, eid => save(saved, { name: n, eid }));
      if (!rec) throw new Error(Live.connected ? 'run ended early' : 'lab dropped mid-run — stopping, not reading stale numbers');
      THREAD.results[n] = { status: 'done', rec, gap: memberGap(rec) };
      saved[n] = { status: 'done', id: rec.id }; save(saved, null);
    } catch (err) {
      THREAD.results[n] = { status: 'failed: ' + err.message };
      saved[n] = { status: THREAD.results[n].status }; save(saved, null);
    }
  }
  RUN.finished = true;
  RUN = null;
  cmpClear();
  benchProg('done');
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
    return `<tr><td>${esc(n)}</td><td>${r.gap >= 0 ? '+' : ''}${r.gap.toFixed(4)} ${esc(CARD.measure)}</td><td>${badge(r.rec.verdict)}</td><td><a href="ask/watch/?id=${encodeURIComponent(r.rec.id)}">replay</a></td></tr>`;
  }).join('');
  setOut(`<h2>Ranking — ${esc(K.measures[CARD.measure])}</h2>
    <table class="flies"><tr><th>member</th><th>shift vs control</th><th>verdict</th><th>run</th></tr>${rows}</table>
    <h2>What this means</h2>${explainResults()}
    <p class="dim">Seeds and records live on each run's replay page. Unranked members never ran — they are not last, they are unknown.</p>`);
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
  if (!t) return;
  const div = document.createElement('div');
  div.className = 'qa';
  div.innerHTML = `<p class="qq">${esc(question)}</p><div>${html}</div>`;
  t.appendChild(div);
  div.scrollIntoView({ block: 'nearest' });
}
function suggestChips(items) {
  let box = $('chips');
  if (!box) {
    const th = $('thread');
    if (!th) return;
    box = document.createElement('div'); box.id = 'chips'; box.className = 'chips'; th.after(box);
  }
  box.innerHTML = `<span class="dim">What to ask next:</span> `;
  for (const it of items) {
    const b = document.createElement('button');
    b.textContent = it;
    b.onclick = () => { if (/^run the comparison/i.test(it)) { runComparison(); } else { $('q').value = it; showCard(it); } };
    box.appendChild(b);
  }
}
function answerHypFollowup(question) {
  const q = question.toLowerCase(), h = CARD.hyp;
  let html;
  if (/evidence|paper|papers|reference|cite/.test(q)) {
    html = `<ul class="tight">${(CARD.refs || []).map(r => `<li>${esc(r)}</li>`).join('') || '<li>No references on file.</li>'}</ul>`;
  } else if (/disprov|falsif|fail|wrong|test/.test(q)) {
    html = `<p>It would fail if: ${esc(h.falsified)}</p>`
      + (h.verdict ? `<p>Latest run says: <b>${esc(h.verdict)}</b> — ${esc(h.record)}.</p>` : '');
  } else if (/run it|run them|run the|again|replay/.test(q)) {
    html = `<p><a href="experiments/#/c/${encodeURIComponent(h.key)}">Open the ${esc(h.title)} card in the Experiment Atlas →</a> — replay the run or run it again from there.</p>`;
  } else {
    html = `<p>${esc(h.text)}</p><p class="dim">Rests on: ${esc(h.given)}</p>`;
  }
  appendThread(question, html);
  suggestChips(['Why this prediction?', 'Show the evidence', 'What would disprove it?']);
}
function answerFollowup(question) {
  if (!THREAD && CARD && CARD.mode === 'explain' && CARD.hyp) return answerHypFollowup(question);
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
      const { brain3D } = await import('./brain3d.js');   // import() resolves vs the script URL (ask/), not the page
      this.view = await brain3D($('gl'), {}, { soma: Feather.mode() === 'dusk' ? 0x4a5578 : 0x8a8fa8, stage: $('stage') });
    } catch (err) {
      console.error('[ask] 3D brain failed:', err);
      let extra = '';
      try {
        const probe = async u => {
          try { const r = await fetch(u); return u + ' ' + r.status; }
          catch { return u + ' fetch-failed'; }
        };
        extra = ' [' + (await probe('./ask/brain3d.js')) + '; ' + (await probe('./vendor/three/three.module.js')) + ']';
      } catch {}
      $('caption').textContent = '3D unavailable here (' + ((err && err.message) || err) + ')' + extra + ' — the numbers below carry it.';
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
  const set = (id, v) => { const b = $(id); if (b) b.setAttribute('aria-pressed', String(v)); };
  set('zin', z >= 1);
  set('zout', z < 1);
  set('closeupBtn', z > 2.2);
}
/* Brains rail: static markup in study.html (#brainWrap), wired once at boot. */
function initRail() {
  Stage.init();
  $('tab0').onclick = () => Stage.showFly(0);
  $('tab1').onclick = () => Stage.showFly(1);
  document.getElementById('brainWrap').querySelector('.tabs').addEventListener('keydown', e => {
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
  /* closeupBtn lives in the bench and is wired when the bench mounts.
     Study owns the top bar and light toggle; the soma follows page mode via initOnce. */
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

/* ---- bench: template + mount + once-only boot ----
   Study renders this bench into #page and stashes the node (with its handlers
   and thread) when another bench opens. The brains rail is static markup. */
function askBenchHTML() {
  return `<section class="info" id="askBench">
    <div class="qrow">
      <input id="q" type="text" autocomplete="off" placeholder="e.g. how does caffeine affect Maxime and Hagrid?">
      <button id="ask" class="go">Ask</button>
    </div>
    <div class="examples">
      <button data-q="how does caffeine affect Maxime and Hagrid">caffeine</button>
      <button data-q="does DEET stop the flies finding bananas">DEET</button>
      <button data-q="what does tetanus toxin do to walking">tetanus + walking</button>
      <button data-q="compare caffeine, theophylline and theobromine">methylxanthine trio</button>
    </div>
    <div class="toprow">
      <button id="runBtn" class="go" disabled>Run the experiment</button>
    </div>
    <div class="detail"><article aria-live="polite">
      <section id="card" hidden></section>
      <section id="run" hidden>
        <span id="prog" class="prog"></span>
        <canvas id="trace" width="600" height="90" style="display:none"></canvas>
      </section>
      <section id="out"></section>
      <section id="curve"></section>
      <section id="thread" aria-live="polite"></section>
      <button class="closeupbtn" id="closeupBtn" aria-pressed="false">
        <span><span class="t">Close-up Detail</span><span class="d">Zoom into the active brain</span></span>
        <svg class="ic"><use href="#i-chev"/></svg>
      </button>
      <p class="dim" style="margin-top:12px">Cards are written live by AI and checked against the lab's substance and paper lists — or compiled locally when the AI is unavailable. All flies are simulated. Doses are unitless knob positions, never advice for any living thing.</p>
    </article></div>
    <div class="runbar"><button id="runBtnM" class="go" disabled>Run the experiment</button></div>
  </section>`;
}
let benchNode = null;
function mountBench(page) {
  if (benchNode) { page.appendChild(benchNode); return benchNode; }
  const wrap = document.createElement('div');
  wrap.innerHTML = askBenchHTML();
  benchNode = wrap.firstElementChild;
  page.appendChild(benchNode);
  $('ask').onclick = () => { const q = $('q').value.trim(); if (q && K) showCard(q); };
  $('q').addEventListener('keydown', e => { if (e.key === 'Enter') $('ask').click(); });
  benchNode.querySelectorAll('[data-q]').forEach(b => b.onclick = () => { $('q').value = b.dataset.q; if (K) showCard(b.dataset.q); });
  $('runBtn').onclick = runCard;
  $('runBtnM').onclick = runCard;
  $('closeupBtn').onclick = () => {
    if (!Stage.view) return;
    const z = Stage.view.zoom();
    Stage.view.zoomBy(z > 2.2 ? 1 / z : 2.6 / z);
    syncZoomBtns();
  };
  const q0 = new URLSearchParams(location.search).get('q');
  const r0 = new URLSearchParams(location.search).get('region');
  const l0 = new URLSearchParams(location.search).get('level');
  const h0 = new URLSearchParams(location.search).get('hyp');
  if (r0) { const iv = setInterval(() => { if (K) { clearInterval(iv); renderRegion(r0, q0 || '', l0 || ''); } }, 200); }
  else if (h0) { const iv = setInterval(() => { if (K) { clearInterval(iv); renderHypothesis(h0); } }, 200); }
  else if (q0) { const iv = setInterval(() => { if (K) { clearInterval(iv); $('q').value = q0; showCard(q0); } }, 200); }
  // Waiting room runs with or without ?q=: back-button/history returns carry ?q= too.
  // A pending run always wins over the prefilled card; with no pending key this no-ops.
  { const iv = setInterval(() => { if (K && Live && Live.connected) { clearInterval(iv); followPending(); } }, 300); }
  return benchNode;
}
let booted = false;
function initOnce() {
  if (booted) return;
  booted = true;
  Live = globalThis.StudyLive || null;
  if (Live) {
    Live.on('state', onState);
    Live.on('experiment_done', onDoneFollow);
    Live.on('experiment_cancelled', onCancelledFollow);
  }
  fetch('ask/knowledge.json?v=1').then(r => r.json()).then(j => { K = j; }).catch(() => {
    const c = $('card');
    if (c) { c.hidden = false; c.innerHTML = '<h2>Ask the flies</h2><p class="no">knowledge.json missing — serve the lab folder over http.</p>'; }
  });
  fetch('anatomy/genes.json').then(r => r.json()).then(j => { GENES = j.genes; }).catch(() => { GENES = null; });
  fetch('ask/foods.json').then(r => r.json()).then(j => { FOODS = j; }).catch(() => { FOODS = null; });
  fetch('ask/affinity.json').then(r => r.json()).then(j => { AFFINITY = j; }).catch(() => { AFFINITY = null; });
  fetch('anatomy/drug_targets.json').then(r => r.json()).then(j => { BRIDGE = j.bridge; }).catch(() => { BRIDGE = null; });
  initRail();
  Feather.init({ onLight: m => { if (Stage.view) Stage.view.setSoma(m === 'dusk' ? 0x4a5578 : 0x8a8fa8); } });
}
globalThis.AskBench = { mount: mountBench, initOnce };

