/* Headless smoke for ask/app.js: boots the real script with stub DOM and runs
   questions through showCard + the compilers. Catches ReferenceErrors and
   wiring breaks that node --check cannot see. Run: node smoke.js (exit 1 on fail). */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const HERE = __dirname;

const els = {};
const el = () => new Proxy(function () {}, {
  get: (t, p) => (p === 'dataset' ? {} : (p in t ? t[p] : el())),
  set: (t, p, v) => { t[p] = v; return true; },
  apply: () => el(),
});
const store = {};
const sandbox = {
  console,
  URLSearchParams,
  URL,
  sessionStorage: {
    getItem: k => (k in store ? store[k] : null),
    setItem: (k, v) => { store[k] = String(v); },
    removeItem: k => { delete store[k]; },
  },
  document: { getElementById: id => (els[id] ??= el()), querySelector: () => el(), querySelectorAll: () => [],
    createElement: () => el(), createTextNode: () => el() },
  location: { protocol: 'http:', hostname: 'localhost', search: '' },
  addEventListener: () => {},
  setTimeout: () => 0,
  setInterval: () => 0,
  clearInterval: () => {},
  fetch: (url) => {
    const m = String(url).match(/^(library\/[A-Za-z]+\.json|experiments\/data\.json)$/);
    if (m) {
      try {
        const j = JSON.parse(fs.readFileSync(path.join(HERE, '..', m[0]), 'utf8'));
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(j) });
      } catch { /* fall through to 404 */ }
    }
    return Promise.resolve({ ok: false, status: 404, json: () => Promise.reject(new Error('404')) });
  },
  Stage: { init: () => {}, showFly: () => {}, fly: 0, view: null },
  Feather: { topbar: () => {}, init: () => {} },
};
vm.createContext(sandbox);
const src = fs.readFileSync(path.join(HERE, 'app.js'), 'utf8') + `
;globalThis.__t = { showCard, renderRegion, renderHypothesis, findFood, compileFood, findMeasure, findSubstance, compileCard, matchHtml, onState,
  followDone, renderResults, setDose, modelLine, offNote, recDose, poolCurve, curveSVG,
  getCARD: () => CARD, getRUN: () => RUN,
  setK: v => { K = v; }, setFOODS: v => { FOODS = v; }, setGENES: v => { GENES = v; },
  setBRIDGE: v => { BRIDGE = v; }, setAFFINITY: v => { AFFINITY = v; },
  setLive: v => { Live = v; }, getLive: () => Live, setCARD: v => { CARD = v; }, setRUN: v => { RUN = v; }, setTHREAD: v => { THREAD = v; },
  FOOD_STOP };`;
vm.runInContext(src, sandbox, { filename: 'app.js' });
const t = sandbox.__t;
t.setLive({ connected: false, hello: null, send: () => null, on: () => {} });
t.setK(JSON.parse(fs.readFileSync(path.join(HERE, 'knowledge.json'), 'utf8')));
t.setFOODS(JSON.parse(fs.readFileSync(path.join(HERE, 'foods.json'), 'utf8')));
t.setGENES(null);

const fails = [];
const check = (name, cond, extra = '') => {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (extra && !cond ? ' — ' + extra : ''));
  if (!cond) fails.push(name);
};

(async () => {
  await t.showCard('what is in chocolate');
  let c = t.getCARD();
  check('food question answers', c && c.mode === 'explain' && /chocolate/i.test(c.title), c && c.title);
  check('food answer names theobromine', c && c.paragraphs.join(' ').includes('theobromine'));

  await t.showCard('does caffeine keep flies awake');
  c = t.getCARD();
  check('caffeine card feasible', c && c.feasible === true && c.substance === 'caffeine');
  check('lab-set target wins (rule 1)', c && c.measure === 'firing_all', c && c.measure);
  check('asked measure kept as extra note', c && (c.note_extra || '').includes('sleep proxy'), c && c.note_extra);
  check('secondary + dual confidence', c && c.card.secondary.join() === 'activity' && !!c.effect_confidence && !!c.mechanism_confidence);
  check('repeats default 5 (quick mode)', c && c.card.repeats === 5, c && c.card.repeats);

  await t.showCard('does caffeine make flies walk');
  c = t.getCARD();
  check('walk question keeps lab measures', c && c.measure === 'firing_all' && c.card.secondary.join() === 'activity');

  await t.showCard('does deet keep flies from bananas');
  c = t.getCARD();
  check('deet card: nose + distance', c && c.measure === 'firing.nose' && c.card.secondary.join() === 'banana_dist,firing.taste neurons',
    c && `${c.measure}/${c.card.secondary}`);
  check('distance prediction flips (rule 1)', c && c.card.secondary_prediction.join() === 'higher,lower',
    c && c.card.secondary_prediction);

  await t.showCard('what does optic do (vision, motion)');
  c = t.getCARD();
  check('region question does not throw', !!c);

  await t.showCard('methylxanthine trio');
  c = t.getCARD();
  check('trio still compares', c && c.mode === 'compare', c && c.mode);

  t.setBRIDGE({ caffeine: [{ t: 'ADORA1', a: 'Antagonist', sp: 'Human', f: ['AdoR'], e: ['adult head'] }] });
  t.setGENES({ FBgn1: { s: 'AdoR', e: [{ site: 'adult head', sim: ['central'] }] } });
  t.setAFFINITY({ caffeine: [{ target: 'A1 receptor', gene: 'ADORA1', action: 'Antagonist', affinity: 4.35, unit: 'pKi', species: 'Human' }] });
  await t.showCard('does caffeine make flies walk');
  const html = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('targets block renders', html.includes('Targets (GtoPdb)') && html.includes('ADORA1'), html.slice(0, 120));
  check('fly legs render with sim reach', html.includes('Fly legs (Alliance)') && html.includes('AdoR') && html.includes('reaches sim: central'));
  check('dual confidence renders', html.includes('Effect in real flies:') && html.includes('Mechanism in our sim:'));
  check('effort shows 5x200', html.includes('5 repeats of 200 steps'));

  await t.showCard('does theophylline keep flies awake');
  const thHtml = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('theophylline engagement renders', thHtml.includes('second action joins') && thHtml.includes('PDE arm'));
  check('dose picker renders', thHtml.includes('data-dose="1.5"') && thHtml.includes('Standard 0.5'));
  check('curve button renders', thHtml.includes('curveBtn'));
  t.setDose(1.5);
  check('setDose threads treatments', t.getCARD().dose === 1.5 && t.getCARD().card.treatments[0].setup[1].amount === 1.5
    && t.getCARD().card.treatments[0].name === 'theophylline 1.5');
  check('modelLine dopamine exact', t.modelLine('dopamine', 0.5) === 'background excitatory drive on dopamine-receptor neurons ×1.61');
  check('modelLine theophylline exact', t.modelLine('theophylline', 0.5) === 'background excitatory drive ×1.23; excitatory transmission ×1.00; scales with glial tone (half rested, full tired)');
  check('recDose reads setup', t.recDose({ card: { treatments: [{ setup: [{ cmd: 'dose', drug: 'x', amount: 1.5 }] }] } }) === 1.5);
  check('curveSVG marks join', t.curveSVG('theophylline', 'm', [{ dose: 0.5, tm: 1, cm: 0.5, n: 4 }], 0.5, true).includes('~1.8 (prediction)'));
  const trioFix = { card: { treatments: [{ name: 'theophylline 0.5', setup: [{ cmd: 'dose', drug: 'theophylline', amount: 0.5 }] }, { name: 'caffeine 0.5', setup: [{ cmd: 'dose', drug: 'caffeine', amount: 0.5 }] }] },
    details: { per_fly: { Maxime: [
      { treatment: 'theophylline 0.5', measures: { firing_all: { values: [0.03], control_values: [0.01] } } },
      { treatment: 'caffeine 0.5', measures: { firing_all: { values: [0.02], control_values: [0.01] } } } ] } } };
  check('poolCurve splits trio members', (() => {
    const a = t.poolCurve([trioFix], 'firing_all', 'theophylline')[0.5];
    const b = t.poolCurve([trioFix], 'firing_all', 'caffeine')[0.5];
    return a && a.t[0] === 0.03 && b && b.t[0] === 0.02;
  })());

  t.setTHREAD(null);  // fresh question, not a comparison followup
  await t.showCard('does light reach the eye');
  check('light eye card feasible', t.getCARD() && t.getCARD().feasible === true && t.getCARD().substance === 'light');
  const liHtml = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('light procedure is stimulus', liHtml.includes('stimulus light level 1') && !liHtml.includes('dose light'));
  check('light hides picker', !liHtml.includes('data-dose=') && !liHtml.includes('curveBtn'));
  check('light facts render', liHtml.includes('1,849 banc') && liHtml.includes('asymmetry is the dataset'));
  await t.showCard('does light move the visual targets');
  check('light targets card', t.getCARD() && t.getCARD().stimulus === 'targets' && t.getCARD().measure === 'firing.visual targets');
  const tgHtml = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('targets awake floor stated', tgHtml.includes('awake floor for target assays') && tgHtml.includes('data-quality flag'));
  await t.showCard('does light ice cream keep flies awake');
  check('light food stays food', t.getCARD() && t.getCARD().mode === 'explain');

  await t.showCard('are ninaE nulls blind');
  check('ninaE mutant card', t.getCARD() && t.getCARD().mutant === 'ninaE_ko' && t.getCARD().measure === 'firing.photoreceptors');
  const muHtml = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('mutant procedure raises her', muHtml.includes('raise her as a ninaE_ko mutant') && muHtml.includes('stimulus light level 1'));
  check('mutant hides picker', !muHtml.includes('data-dose=') && !muHtml.includes('curveBtn'));
  check('mutant facts render', muHtml.includes('disinhibition') && muHtml.includes('Alliance PHENOTYPE'));
  await t.showCard('are Orco knockouts anosmic');
  check('Orco mutant card', t.getCARD() && t.getCARD().mutant === 'Orco_ko');
  await t.showCard('what does the para null do');
  check('para mutant card', t.getCARD() && t.getCARD().mutant === 'para_ko' && t.getCARD().measure === 'firing_all');
  await t.showCard('what does Orco do');
  check('bare gene stays off mutant path', t.getCARD() && !t.getCARD().mutant);
  await t.showCard('can Gr66a nulls taste bitter');
  check('Gr66a mutant card', t.getCARD() && t.getCARD().mutant === 'Gr66a_ko' && t.getCARD().measure === 'firing.taste neurons');
  const grHtml = (() => { try { return String(vm.runInContext("document.getElementById('card').innerHTML", sandbox)); } catch { return ''; } })();
  check('Gr66a procedure is preset+bitter', grHtml.includes('set I_taste 2') && grHtml.includes('stimulus bitter level 1') && grHtml.includes('raise her as a Gr66a_ko mutant'));
  check('Gr66a facts render', grHtml.includes('I_taste 2.0 (pinned') && grHtml.includes('+299%'));
  await t.showCard('does deet keep flies from bananas');
  const dc = t.getCARD();
  check('deet taste secondary', dc && dc.secondary && dc.secondary.includes('firing.taste neurons')
    && dc.card.secondary_prediction[1] === 'lower');
  check('deet I_taste both boxes', dc && dc.card.control.setup.some(a => a.key === 'I_taste' && a.value === 2.0)
    && dc.card.treatments[0].setup.some(a => a.key === 'I_taste' && a.value === 2.0));

  t.onState({ experiment: { id: 'e9', condition: 'caffeine 0.5', repeat: 8, n_rep: 10, progress: 0.71 },
    flies: {}, focus: 'control:banc', sps: 28 });
  check('reattach to running experiment', t.getRUN() && t.getRUN().followed === true);
  const prog = (() => { try { return String(vm.runInContext("document.getElementById('prog').textContent", sandbox)); } catch { return ''; } })();
  check('reattach shows progress', prog.includes('repeat 8 of 10'));

  const mh = t.matchHtml({ verdict: "CAN'T TELL YET", explanation: ['Para one here.', 'Para two here.'],
    details: { per_fly: { 'Maxime (female)': [{ suspects: ['too few repeats (9 per box)'] }] } },
    seeds: [{ pad: 'x'.repeat(300) }], id: 'seedrec9' });
  check('match renders explanation', mh.includes('Para one here.') && mh.includes('Para two here.'));
  check('match renders suspects', mh.includes('Suspects:') && mh.includes('too few repeats'));
  check('seeds untruncated (rule 9)', mh.includes('x'.repeat(300)), mh.length);
  check('no sim-has-numbers (rule 3)', !mh.includes('sim has the numbers'));
  check('no lights-up (rule 5)', !mh.includes('Lights up') && !mh.includes('lit up'));

  // cross-page return: stored run context restores card + question + run section
  const dom = (id, prop, v) => {
    try {
      if (v === undefined) return vm.runInContext(`document.getElementById('${id}').${prop}`, sandbox);
      vm.runInContext(`document.getElementById('${id}').${prop} = ${JSON.stringify(v)}`, sandbox);
    } catch { return undefined; }
  };
  await t.showCard('does caffeine keep flies awake');
  const savedCard = JSON.parse(JSON.stringify(t.getCARD()));
  store['ask-waiting'] = JSON.stringify({ id: 'e9', q: 'does caffeine keep flies awake', card: savedCard });
  t.setCARD(null);
  t.setRUN(null);
  dom('q', 'value', '');
  t.onState({ experiment: { id: 'e9', condition: 'caffeine 0.5', repeat: 3, n_rep: 10, progress: 0.27 },
    flies: {}, focus: 'control:banc', sps: 28 });
  check('rejoin restores followed run', t.getRUN() && t.getRUN().followed === true);
  check('rejoin restores stored card', t.getCARD() && t.getCARD().substance === 'caffeine');
  check('rejoin restores question', dom('q', 'value') === 'does caffeine keep flies awake', dom('q', 'value'));
  check('rejoin unhides run section', dom('run', 'hidden') === false);
  check('rejoin renders card', String(dom('card', 'innerHTML') || '').includes('caffeine'));
  // stale key (different live run) restores nothing but still unhides + follows
  t.setCARD(null);
  t.setRUN(null);
  store['ask-waiting'] = JSON.stringify({ id: 'other', q: 'zzz', card: savedCard });
  t.onState({ experiment: { id: 'e9', condition: 'caffeine 0.5', repeat: 3, n_rep: 10, progress: 0.27 },
    flies: {}, focus: 'control:banc', sps: 28 });
  check('stale key keeps bench clean', t.getCARD() === null && t.getRUN().followed === true);
  delete store['ask-waiting'];

  // finished-while-away renders the card too, not just results
  t.setCARD(null);
  t.setRUN(null);
  dom('q', 'value', 'does caffeine keep flies awake');
  const rec = { id: 'e9', verdict: 'SUPPORTED', explanation: ['It worked.'],
    card: { title: 'caffeine vs control', treatments: [{ name: 'caffeine 0.5' }],
      measure: 'firing_all', secondary: [], prediction: 'higher' },
    details: { per_fly: { 'Maxime (female)': [{ verdict: 'SUPPORTED',
      measures: { firing_all: { control_values: [1, 1.1], values: [2, 2.1], verdict: 'SUPPORTED' } } }],
      'Hagrid (male)': [{ verdict: 'SUPPORTED',
        measures: { firing_all: { control_values: [1, 1.1], values: [2, 2.1], verdict: 'SUPPORTED' } } }] } } };
  t.followDone(rec);
  check('followDone builds card', t.getCARD() && t.getCARD().substance === 'caffeine');
  check('followDone renders card', String(dom('card', 'innerHTML') || '').includes('caffeine'));
  check('followDone renders results', String(dom('out', 'innerHTML') || '').includes('Where it changed'));
  check('followDone clears run', t.getRUN() === null);

  // region routing: Atlas "what does X do" answers from atlas files, never food/AI
  t.setTHREAD(null);
  check('male stops food routing',
    t.findFood('what does me_r (male optic) do (male subset · optic-right)') === null,
    t.findFood('what does me_r (male optic) do (male subset · optic-right)'));
  await t.showCard('what does optic do (vision, motion)');
  check('optic answers as region', t.getCARD() && t.getCARD().region === 'optic' && t.getCARD().mode === 'explain',
    t.getCARD() && t.getCARD().title);
  t.setTHREAD(null);
  await t.showCard('what does ME_R (male optic) do (male subset · optic-right)');
  check('male subset answers as region', t.getCARD() && t.getCARD().region === 'ME_R (male optic)',
    t.getCARD() && t.getCARD().title);
  t.setTHREAD(null);
  await t.renderRegion('3057', 'subset link', 'subset');
  check('subset row+level dispatch', t.getCARD() && t.getCARD().region === 'ME_R (male optic)');
  await t.renderRegion('4', 'hub link', 'hub');
  check('hub row+level dispatch', t.getCARD() && t.getCARD().region === 'optic');
  await t.renderRegion('4000', 'sense link', 'sense');
  check('sense row+level dispatch', t.getCARD() && t.getCARD().region === 'photoreceptors');
  t.setTHREAD(null);
  await t.showCard('what does taste do (taste)');
  check('taste answers as region', t.getCARD() && t.getCARD().region === 'taste',
    t.getCARD() && t.getCARD().title);
  t.setTHREAD(null);
  // freeze regressions: known substances never wait on the server
  t.setTHREAD(null);
  await t.showCard('how does caffeine affect Maxime and Hagrid');
  check('both-flies caffeine card feasible', t.getCARD() && t.getCARD().feasible === true &&
    t.getCARD().substance === 'caffeine', t.getCARD() && t.getCARD().title);
  let calls = 0;
  t.setLive({ connected: true, hello: null, pending: {}, send: () => { calls++; return 'hung'; }, on: () => {} });
  t.setTHREAD(null);
  await t.showCard('how does caffeine affect Maxime and Hagrid');
  check('known substance skips silent server', calls === 0 && t.getCARD() && t.getCARD().feasible === true,
    `calls=${calls}`);
  check('local badge set', t.getCARD() && t.getCARD().badge === 'local');
  check('trait glosses render', /unitless knob/.test(els.card.innerHTML || '') &&
    /rule 2 needs at least 5/.test(els.card.innerHTML || '') &&
    /measured against these two/.test(els.card.innerHTML || ''));
  t.setTHREAD(null);
  const LV = t.getLive ? t.getLive() : null;
  const p = t.showCard('is zxqvblorp testable');
  await new Promise(r => setTimeout(r, 50));
  check('unknown question consults server', calls === 1, `calls=${calls}`);
  if (LV && LV.pending && LV.pending.hung) LV.pending.hung({ ok: false });
  await p;
  check('server refusal falls back local', t.getCARD() && t.getCARD().feasible === false);
  t.setLive({ connected: false, hello: null, pending: {}, send: () => null, on: () => {} });
  await t.renderRegion('XYZ-nope', 'missing entry', '');
  check('unknown name falls back to plain card', t.getCARD() && !t.getCARD().region);
  check('stopwords remove no food reach', (() => {
    const F = JSON.parse(fs.readFileSync(path.join(HERE, 'foods.json'), 'utf8'));
    const old = new Set(['what', 'whats', 'does', 'do', 'are', 'is', 'in', 'the', 'and',
      'food', 'foods', 'contain', 'contains', 'dose', 'a', 'an', 'of', 'any']);
    const reach = stop => Object.keys(F).filter(n =>
      n.split(',')[0].toLowerCase().split(/[^a-z]+/).some(w => w.length > 3 && !stop.has(w)));
    const a = reach(old), b = reach(t.FOOD_STOP);
    return a.length === b.length && a.every(n => b.includes(n));
  })());

  // hypothesis explainer (?hyp=): content renders AND the card unhides
  els.card.hidden = true;   // fresh page carries <section id="card" hidden>
  await t.renderHypothesis('drug:theophylline');
  check('hypothesis explainer unhides card', els.card.hidden === false);
  check('hypothesis explainer content', /Would fail if/.test(els.card.innerHTML || ''));

  console.log(fails.length ? `SMOKE: ${fails.length} FAILING` : 'SMOKE: ALL PASS');
  process.exit(fails.length ? 1 : 0);
})().catch(e => { console.log('SMOKE THREW: ' + (e && e.stack || e)); process.exit(1); });
