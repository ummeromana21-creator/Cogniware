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
const sandbox = {
  console,
  URLSearchParams,
  URL,
  document: { getElementById: id => (els[id] ??= el()), querySelector: () => el(), querySelectorAll: () => [],
    createElement: () => el(), createTextNode: () => el() },
  location: { protocol: 'http:', hostname: 'localhost', search: '' },
  addEventListener: () => {},
  setTimeout: () => 0,
  setInterval: () => 0,
  clearInterval: () => {},
  fetch: () => new Promise(() => {}),
  Stage: { init: () => {}, showFly: () => {}, fly: 0, view: null },
  Feather: { topbar: () => {}, init: () => {} },
};
vm.createContext(sandbox);
const src = fs.readFileSync(path.join(HERE, 'app.js'), 'utf8') + `
;globalThis.__t = { showCard, findFood, compileFood, findMeasure, findSubstance, compileCard, matchHtml,
  getCARD: () => CARD, setK: v => { K = v; }, setFOODS: v => { FOODS = v; }, setGENES: v => { GENES = v; },
  setBRIDGE: v => { BRIDGE = v; }, setAFFINITY: v => { AFFINITY = v; } };`;
vm.runInContext(src, sandbox, { filename: 'app.js' });
const t = sandbox.__t;
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
  check('repeats default 10', c && c.card.repeats === 10, c && c.card.repeats);

  await t.showCard('does caffeine make flies walk');
  c = t.getCARD();
  check('walk question keeps lab measures', c && c.measure === 'firing_all' && c.card.secondary.join() === 'activity');

  await t.showCard('does deet keep flies from bananas');
  c = t.getCARD();
  check('deet card: nose + distance', c && c.measure === 'firing.nose' && c.card.secondary.join() === 'banana_dist',
    c && `${c.measure}/${c.card.secondary}`);
  check('distance prediction flips (rule 1)', c && c.card.secondary_prediction.join() === 'higher',
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
  check('effort shows 10x400', html.includes('10 repeats of 400 steps'));

  const mh = t.matchHtml({ verdict: "CAN'T TELL YET", explanation: ['Para one here.', 'Para two here.'],
    details: { per_fly: { 'Maxime (female)': [{ suspects: ['too few repeats (9 per box)'] }] } },
    seeds: [{ pad: 'x'.repeat(300) }], id: 'seedrec9' });
  check('match renders explanation', mh.includes('Para one here.') && mh.includes('Para two here.'));
  check('match renders suspects', mh.includes('Suspects:') && mh.includes('too few repeats'));
  check('seeds untruncated (rule 9)', mh.includes('x'.repeat(300)), mh.length);
  check('no sim-has-numbers (rule 3)', !mh.includes('sim has the numbers'));
  check('no lights-up (rule 5)', !mh.includes('Lights up') && !mh.includes('lit up'));

  console.log(fails.length ? `SMOKE: ${fails.length} FAILING` : 'SMOKE: ALL PASS');
  process.exit(fails.length ? 1 : 0);
})().catch(e => { console.log('SMOKE THREW: ' + (e && e.stack || e)); process.exit(1); });
