"""Build the Experiment Atlas data (metaverse-lab/experiments/data.json + img/*.svg).

Sources (read, never edited here):
  Fly Brain/substances.py ........ 30 library drugs + standing target/behavior rules
  metaverse-lab/ask/knowledge.json  the 7 classic drugs' rules + confidences
  Fly Brain/validation.md ........ P1-P7 claims + recorded results (hand-mapped below)
  comparative/FINDINGS.md ........ 4 comparison findings (hand-mapped below)
  Fly Brain/mutants.py ........... 3 mutant cards
  brief 2026-10-31 ............... 3 new-sense cards (user-specified; citations flagged)
  Fly Brain/notebook/*.json ...... run verdicts (newest record per experiment)
  Databases/gtopdb/all_ligands.sdf  2D structures (RDKit); PubChem SMILES fallback

Run:  cd metaverse-lab/experiments && ~/Desktop/"Fly Brain"/.venv/bin/python build.py
      (MUST use the project venv: system python3 lacks RDKit, and rebuilding
      with it silently drops all 36 structures + formulas from data.json)

Nothing is invented: prose sources are transcribed once into the tables below
(the user reviews them); everything else is computed from files.
"""
import glob
import json
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.expanduser('~/Desktop/legal-agent-workshop-main/metaverse-lab')
FLY = os.path.expanduser('~/Desktop/Fly Brain')
DB = os.path.expanduser('~/Desktop/Databases')
sys.path.insert(0, FLY)

import substances as S
import mutants as M

# =============================================================================
# PHRASING TABLES (fixed word maps, reviewed with the rest of this file)
# =============================================================================
SITE_WORDS = {  # standing target -> plain words for "where it acts"
    'firing_all': 'the whole brain', 'firing.nose': 'the smell centre',
    'firing_smell': 'the smell centre', 'brain-wide': 'the whole brain',
    'muscle': 'the leg muscles', 'legs only. firing stays': 'only the legs (brain keeps firing)',
}
MEASURE_WORDS = {  # behaviour measure -> plain words for "what to measure"
    'activity': 'walking', 'banana_dist': 'banana finding', 'sleep': 'sleep',
    'synchrony': 'seizure-like bursting', 'bananas': 'bananas collected',
    'firing_all': 'brain firing', 'banana_near': 'time near the banana',
}
BEHAVIOR_KEYWORDS = {  # behaviour -> one MEASURE_KEYS word for the Ask prefill
    'activity': 'walk', 'banana_dist': 'near', 'sleep': 'sleep',
    'synchrony': 'seizure', 'bananas': 'collect', 'firing_all': 'firing',
}

# =============================================================================
# NOTEBOOK (newest record per experiment)
# =============================================================================
def load_notebook():
    recs = []
    for f in glob.glob(os.path.join(FLY, 'notebook', '*.json')):
        if os.path.basename(f) in ('index.json', 'lookups.json', 'robots.json'):
            continue
        try:
            recs.append(json.load(open(f)))
        except (json.JSONDecodeError, OSError):
            continue
    return recs

def newest_for(recs, drug=None, measure=None):
    """Newest notebook record whose treatment card involves drug+measure."""
    cands = []
    for r in recs:
        card = r.get('card') or {}
        treat = json.dumps(card.get('treatments', []))
        if drug and drug not in treat:
            continue
        if measure and card.get('measure') != measure and measure not in treat:
            continue
        cands.append(r)
    cands.sort(key=lambda r: (r.get('date', ''), r.get('id', '')), reverse=True)
    return cands[0] if cands else None

# =============================================================================
# DRUG STATUS RULES (pure functions of substances.py fields — no judgement)
# =============================================================================
def lib_real_status(key, lib):
    """Real-fly status from LIBRARY confidence + filed evidence."""
    e = lib.get(key, {})
    conf = e.get('confidence', '')
    ev = [x for x in e.get('evidence', []) if not x.startswith('Real-fly:')]
    src = ev[0] if ev else ('confidence note only' if conf else 'no paper on file')
    if conf == 'not testable yet':
        why = e.get('not_modelled') or 'no dose handle in the sim'
        return 'can\'t test yet', why
    if conf == 'established in Drosophila':
        return 'already known', src
    if conf in ('insect evidence', 'inferred', 'established in mammals'):
        return 'partly known', src
    if conf:
        return 'partly known', src + ' (%s)' % conf
    return 'not yet tested', 'no paper on file'

def classic_real_status(entry):
    """Real-fly status for the 7 knowledge.json-only classics."""
    ev = entry.get('evidence', []) or []
    conf = entry.get('effect_confidence', '') or entry.get('mechanism_confidence', '')
    src = ev[0] if ev else 'no paper on file'
    if not ev:
        return 'not yet tested', 'no paper on file' + (' (%s)' % conf if conf else '')
    text = ' '.join(ev).lower()
    if 'drosophila' in text or ' fly ' in text or 'flies' in text:
        return 'already known', src
    return 'partly known', src

def ask_prefill(name, behavior):
    kw = BEHAVIOR_KEYWORDS.get(behavior)
    if not kw:
        return None
    return 'does %s change %s?' % (name.replace('_', ' '), kw)

# =============================================================================
# HAND TABLES (transcribed from prose sources; user-reviewed, never invented)
# real: already known | partly known | not yet tested | can't test yet
# =============================================================================
P_TABLE = [
    dict(key='P1', off_record=True, title='P1 — TTX silences brain and body',
         claim='Tetrodotoxin blocks spikes: firing falls to ~0 and the fly stops.',
         site='whole-brain firing', measure='firing_all', expect='lower',
         drug='ttx', real='partly known', real_source='textbook pharmacology — no fly paper cited',
         recorded='firing 4.91% → 0.18%, activity 0.089 → 0.045. Matches.'),
    dict(key='P2', title='P2 — Tetanus: brain lit, body frozen',
         claim='Tetanus toxin blocks transmitter release: firing stays, movement goes to ~0.',
         site='leg muscle input', measure='activity', expect='lower',
         drug='tetanus_toxin', real='already known', real_source='Sweeney et al. 1995, Neuron (Drosophila)',
         recorded='firing 4.91% → 4.72%, activity 0.089 → 0.003. Matches.'),
    dict(key='P3', off_record=True, title='P3 — Anaesthesia is reversible',
         claim='Isoflurane sedates dose-dependently and washes out again.',
         site='whole-brain firing', measure='activity', expect='lower',
         drug='isoflurane', real='already known', real_source='fly anaesthesia literature (see validation.md)',
         recorded='activity 0.089 → 0.050 → 0.087. Reversibility matches.'),
    dict(key='P4', off_record=True, title='P4 — Picrotoxin: the seizure that wasn\u2019t',
         claim='Removing inhibition should cause synchronous bursting. Firing rises — bursting never comes.',
         site='whole-brain firing', measure='firing_all', expect='higher',
         drug='picrotoxin', real='already known', real_source='Drosophila seizure-model literature',
         recorded='Firing +22% but synchrony ~0. Honest negative; needs the P4b model change.'),
    dict(key='P5', title='P5 — Caffeine raises activity',
         claim='Caffeine raises locomotion, monotonically with dose.',
         site='whole-brain firing', measure='activity', expect='higher',
         drug='caffeine', real='already known', real_source='Shaw et al. 2000, Science; Hendricks et al. 2000, Neuron',
         recorded='Monotonic 0.088 → 0.117, washout restores. Matches (weak test).'),
    dict(key='P6', title='P6 — Ethanol: the missing buzz',
         claim='Real flies go hyperactive on low ethanol; the single-mechanism model only sedates.',
         site='whole-brain firing', measure='activity', expect='lower',
         drug='ethanol', real='already known', real_source='Wolf et al. 2002, J Neurosci; Parr et al. 2001',
         recorded='Monotonic decrease. Expected mismatch; needs the P6b model change.'),
    dict(key='P7', title='P7 — Where the smell signal dies',
         claim='With real wiring, the left/right smell difference dies at the first synapse (PN saturation).',
         site='olfactory path L/R', measure='lateralisation index', expect='lower',
         drug=None, real='already known', real_source='Gaudry et al. 2013, Nature (fly ORN→PN)',
         recorded=('ORNs +0.58, everything downstream ~0. Honest negative (stage_map.py). '
                   'Nov 1 recalibration: bilateral (legs ipsi==contra exactly), klinotaxis (40-run '
                   "CAN'T TELL), and wind lateralization all rejected — full autopsy in battery P7.")),
    dict(key='P4b', title='P4b — Bursting with adaptation (needs model change)',
         claim='FOLLOW-UP (not run): adaptation + a balanced recurrent pool should seize under picrotoxin.',
         site='whole-brain synchrony', measure='synchrony', expect='higher',
         drug='picrotoxin', real='already known', real_source='Drosophila seizure-model literature',
         recorded='Not attempted within the hackathon window.', needs_model=True,
         model_note='Two populations: feed-forward sensorimotor path + balanced recurrent pool with adaptation.'),
    dict(key='P6b', title='P6b — Biphasic ethanol (needs model change)',
         claim='FOLLOW-UP (not run): a global excitability gain (octopamine/dopamine tone) should restore the low-dose buzz.',
         site='whole-brain firing', measure='activity', expect='higher',
         drug='ethanol', real='already known', real_source='Wolf et al. 2002 (modulatory dependence)',
         recorded='To be added only after the monotonic result stands.', needs_model=True,
         model_note='One global gain raised at low ethanol concentration, labelled a second hypothesis.'),
]

C_TABLE = [
    dict(key='C1', title='C1 — Same parts?',
         claim='Central-brain cell counts agree within 2% across all three connectomes; naming is the biggest noise source.',
         site='central-brain cell census', measure='cell counts by type', expect='same',
         real='already known', real_source='FINDINGS.md §1 (Oct 30, 2026)',
         recorded='57% of 2,909 shared types match in all three; 29% the two females disagree (mapping).'),
    dict(key='C2', title='C2 — Male-only cells?',
         claim='P1 courtship cluster and male mAL types appear in Hagrid as the literature says.',
         site='sex-specific cell types', measure='cells per type', expect='higher',
         expect_text='more cells per type in the male',
         real='already known', real_source='FINDINGS.md §2 + male-CNS literature',
         recorded='P1: 10/12/156 (FlyWire/Maxime/Hagrid). Direction matches.'),
    dict(key='C3', title='C3 — Left vs right',
         claim='Hagrid\u2019s left antenna is under-mapped; Maxime has her own lopsided types. Both are data gaps, not biology.',
         site='left/right cell counts', measure='L/R symmetry', expect='same',
         real='not yet tested', real_source='FINDINGS.md §3 — mapping gaps, no literature claim',
         recorded='Hagrid smell receptors 883 L / 1343 R; Maxime 26% of types lopsided >30%.'),
    dict(key='C4', title='C4 — Connection strength',
         claim='Synapse counts differ by machine; compared as shares, FlyWire–Hagrid agree best (0.91). Raw counts must never be compared.',
         site='synapses per central neuron', measure='input-share similarity', expect='same',
         real='not yet tested', real_source='FINDINGS.md §4 — methodological finding',
         recorded='540/167/787 synapses per neuron by machine; shares similar at 0.71–0.91.'),
]

# New senses: user-specified 2026-10-31 (NOT validation.md S1–S3, which are
# different modelling notes). Each says the sense exists but is off by default.
SENSES = [
    dict(key='S-co2', title='CO2 avoidance',
         claim='A CO2 puff should drive avoidance. Known in flies; the channel exists but is switched off by default.',
         site='CO2 channel', measure='avoidance', expect='response',
         real='already known', real_source='lab brief 2026-10-31 — needs a filed citation',
         verb='stimulus', channel='co2'),
    dict(key='S-wind', title='Wind-guided smell navigation',
         claim=('P7\u2019s second hypothesis: with wind, smell navigation should work. Known in flies; off by default. '
                'Sim update Nov 1: the model rejects all three follow-ups (bilateral, klinotaxis, wind lateralization) '
                '\u2014 P7 stays a negative result; real flies still do it.'),
         site='wind + smell combined', measure='bananas found upwind', expect='higher',
         real='already known', real_source='Álvarez-Salvado et al. 2018, eLife (wind-guided odour navigation)',
         verb='stimulus', channel='wind'),
    dict(key='S-taste', title='Sugar-taste neurons switched on',
         claim='Switching sugar-taste neurons on should light up their downstream cells. Known; off by default.',
         site='gustatory pool', measure='downstream firing', expect='higher',
         real='already known', real_source='Shiu et al. 2024 (per brief — verify exact claim)',
         verb='stimulus', channel='bitter'),
]

# =============================================================================
# HYPOTHESES (assistant draft 2026-11-02, user to review; run cards only)
# Two-clause form: the claim in the sim, what it rests on (encoded), and what
# would fail it. Grounded only in each card's own sentence/mechanism/claim —
# never in the recorded outcome. Reconstructed post-run by necessity: the UI
# labels them rationale, not sealed predictions.
# =============================================================================
HYPOTHESES = {
    'drug:amphetamine': dict(
        text='In the 2×2 sim, amphetamine changes walking and dopamine-neuron firing vs control.',
        given='it is encoded as reversed dopamine transport plus emptied dopamine vesicles, so dopamine release rises; the octopamine-arm effect is weaker.',
        falsified='both measures stay within the control wobble.'),
    'drug:aldicarb': dict(
        text='In the 2×2 sim (Maxime and Hagrid, control vs treatment boxes), aldicarb raises firing in acetylcholine neurons vs control, and walking changes with it.',
        given='aldicarb is encoded as prolonged acetylcholine action at excitatory synapses; no enzyme molecule and no clearance kinetics are modelled.',
        falsified='cholinergic firing stays within the control wobble.'),
    'drug:bupropion': dict(
        text='In the 2×2 sim, bupropion raises firing in dopamine neurons vs control.',
        given='it is encoded as blocked dopamine reuptake, so released dopamine acts longer; the nicotinic arm is noted, not modelled.',
        falsified='dopamine-neuron firing stays within the control wobble.'),
    'drug:caffeine': dict(
        text='In the 2×2 sim, caffeine raises whole-brain firing and walking vs control, monotonically with dose.',
        given='caffeine is encoded as a uniform lift of background drive (up to +25% at full Hill engagement, ec50 0.4); the AdoR tag is annotation-only, so every neuron is lifted equally.',
        falsified='the treatment gap is flat or reversed across doses.'),
    'drug:cocaine': dict(
        text='In the 2×2 sim, cocaine changes walking and dopamine-neuron firing vs control.',
        given='it is encoded as blocked dopamine reuptake, so released dopamine stays in the synapse longer.',
        falsified='both measures stay within the control wobble.'),
    'drug:deet': dict(
        text='In the 2×2 sim, DEET quiets nose firing and pushes banana-finding down vs control.',
        given='it is encoded as dulled Orco-dependent smell-receptor responses to attractive odours.',
        falsified='nose firing and banana distance both stay within the control wobble.'),
    'drug:ethanol': dict(
        text='In the 2×2 sim, ethanol lowers walking vs control — sedation only, at every dose.',
        given='it is encoded as potentiated GABA inhibition with depressed excitation and added membrane noise; there is no low-dose arousal arm.',
        falsified='any dose raises walking above control, or the sedation gap falls inside the control wobble.'),
    'drug:tetanus_toxin': dict(
        text='In the 2×2 sim, tetanus toxin collapses walking toward zero while whole-brain firing stays near control.',
        given='it is encoded as silenced transmitter release at the muscle: motor neurons fire, the muscle hears nothing.',
        falsified='walking survives, or brain firing collapses with it.'),
    'drug:theobromine': dict(
        text='In the 2×2 sim, theobromine raises whole-brain firing and walking vs control, less than caffeine at the same dose.',
        given="it is encoded as a uniform background-drive lift with the trio's weakest potency (ec50 1.3, ChEMBL-anchored against caffeine's 0.4).",
        falsified="its gap matches or beats caffeine's, or vanishes into the control wobble."),
    'drug:theophylline': dict(
        text='In the 2×2 sim, theophylline raises whole-brain firing and walking vs control, more than caffeine at the same dose.',
        given="it is encoded as a uniform background-drive lift with the trio's strongest potency (ec50 0.1, ChEMBL-anchored) plus a global high-dose PDE gain arm (ec50 3.1).",
        falsified='its gap is smaller than caffeine\u2019s at Standard dose.'),
    'check:P1': dict(
        text='In the sim, TTX drives whole-brain firing toward zero and stops walking.',
        given='TTX is encoded as blocked spiking.',
        falsified='firing stays above 1%, or walking persists.'),
    'check:P2': dict(
        text='In the sim, tetanus toxin leaves whole-brain firing near control while walking falls toward zero.',
        given='it is encoded as blocked transmitter release onto muscle.',
        falsified='firing collapses, or walking survives.'),
    'check:P3': dict(
        text='In the sim, isoflurane lowers walking dose-dependently, and washout restores it.',
        given='it is encoded as potentiated inhibition plus reduced background drive, with a finite half-life.',
        falsified='the effect is flat across doses, or washout fails to restore walking.'),
    'check:P4': dict(
        text='In the sim, picrotoxin raises whole-brain firing and produces synchronous bursting.',
        given='it is encoded as removed inhibition.',
        falsified='synchrony stays near zero.'),
    'check:P5': dict(
        text='In the sim, caffeine raises walking monotonically with dose, and washout restores baseline.',
        given='the uniform background-drive encoding: the direction nearly follows by construction, so this is a weak test.',
        falsified='the dose curve is flat, non-monotonic, or irreversible.'),
    'check:P6': dict(
        text='In the sim, ethanol only sedates: walking falls monotonically with dose, with no low-dose rise.',
        given='the single-mechanism GABA encoding with no arousal arm.',
        falsified='any low dose raises walking above control.'),
    'check:P7': dict(
        text='In the sim with real wiring, a one-sided banana smell keeps a left/right difference at the nose but loses it by the first downstream relay.',
        given='ORN→PN convergence with saturating responses.',
        falsified='downstream stages keep a lateralisation index clearly above zero.'),
    'comparison:C1': dict(
        text='Across the three connectome brains, central-brain cell counts agree within 2%.',
        given='counts are compared per shared cell type, with naming differences as the expected noise.',
        falsified='the central-brain totals differ by more than 2%.'),
    'comparison:C2': dict(
        text='Hagrid carries the male-specific courtship cells (P1 cluster, male mAL types) nearly absent in the females.',
        given='published male-CNS anatomy.',
        falsified='P1-family counts are comparable across all three brains.'),
    'comparison:C3': dict(
        text='Left/right count asymmetries are mapping gaps, not biology.',
        given='symmetric organs should map symmetric counts.',
        falsified='an asymmetry matches a known biological asymmetry.'),
    'comparison:C4': dict(
        text='Raw synapses-per-neuron differ by machine, but input-share profiles agree, FlyWire–Hagrid best.',
        given='each dataset counts synapses differently, so only shares are comparable.',
        falsified='share-similarity is no better than raw-count similarity.'),
    'sense:S-co2': dict(
        text='In the 2×2 sim with the CO2 channel switched on, a CO2 puff drives avoidance vs control.',
        given='a dedicated CO2 channel exists but is off by default.',
        falsified='CO2-side occupancy matches the control side.'),
    'sense:S-wind': dict(
        text='In the 2×2 sim with wind switched on, a banana smell plus wind steers the fly upwind toward the banana.',
        given='wind-gated odour navigation — P7\u2019s second hypothesis.',
        falsified='upwind banana-finding matches the no-wind control.'),
    'sense:S-taste': dict(
        text='In the 2×2 sim with sugar-taste neurons switched on, their downstream cells light up above control.',
        given='the gustatory pool exists but is off by default.',
        falsified='downstream firing matches control.'),
    'mutant:DAT_null': dict(
        text='In the 2×2 sim, DAT-null flies show raised dopamine-neuron firing vs control.',
        given='no reuptake is read as 1.5× dopamine release gain; sleep and ethanol phenotypes can only emerge via the network.',
        falsified='dopamine firing matches control.'),
    'mutant:Gr5a_ko': dict(
        text='In the 2×2 sim, Gr5a-null flies lose sweet responses while bitter responses stay.',
        given='the null is read as sweet_gain 0 with the bitter arm kept, because the sim pools all gustatory neurons.',
        falsified='bitter responses also vanish, or sweet responses survive.'),
    'mutant:Gr63a_ko': dict(
        text='In the 2×2 sim, Gr63a-null flies lose CO2 responses vs control.',
        given='the null is read as silenced CO2 ORNs (Gr21a/Gr63a complex, glomerulus V).',
        falsified='CO2 responses survive.'),
    'mutant:Gr66a_ko': dict(
        text='In the 2×2 sim, Gr66a-null flies lose bitter responses while sweet responses stay.',
        given='the null is read as bitter_gain 0 with the sweet arm kept, because the sim pools all gustatory neurons.',
        falsified='sweet responses also vanish, or bitter responses survive.'),
    'mutant:Orco_ko': dict(
        text='In the 2×2 sim, Orco-null flies lose banana-smell responses vs control.',
        given='the null is read as silenced nose ORNs, covering only the simulable banana-ORN subset.',
        falsified='banana responses survive.'),
    'mutant:Rdl': dict(
        text='In the 2×2 sim, Rdl-null flies show raised whole-brain firing vs control.',
        given='the null is read as half inhibitory gain; seizures themselves are not modelled.',
        falsified='firing matches control.'),
    'mutant:ninaE_ko': dict(
        text='In the 2×2 sim, ninaE-null flies lose light responses while the rest of the visual system stays intact.',
        given='the null is read as silenced photoreceptors (R1–R6 rhodopsin).',
        falsified='light responses survive.'),
    'mutant:nompC_ko': dict(
        text='In the 2×2 sim, nompC-null flies lose wind responses vs control.',
        given='the null is read as silenced wind sensors; hearing and coordination are not modelled.',
        falsified='wind responses survive.'),
    'mutant:para_ko': dict(
        text='In the 2×2 sim, para-null flies go fully silent: no firing, no walking.',
        given='a null para cannot spike anywhere.',
        falsified='any firing or walking survives.'),
    'mutant:para_ts': dict(
        text='In the 2×2 sim at the restrictive temperature, para flies show lowered firing and walking vs control.',
        given='the hypomorph is read as a +3 mV spike threshold; paralysis itself is not modelled.',
        falsified='firing matches control.'),
    'mutant:park': dict(
        text='In the 2×2 sim, park-null flies show roughly halved dopamine-neuron firing vs control.',
        given='the null is read as a deterministic first-half lesion of the dopamine group — which half is arbitrary and stated; no synuclein, mitochondria or tremor modelled.',
        falsified='dopamine firing matches control.'),
    'mutant:per01': dict(
        text='In the 2×2 sim, per01 flies lose clock-driven firing modulation (no daylight lift) vs control.',
        given='the null forces clock modulation amplitude to zero on any background.',
        falsified='day/night modulation survives.'),
}

# =============================================================================
# PREDICTION SENTENCES (template + standing rules only)
# =============================================================================
def site_words(target):
    if target in SITE_WORDS:
        return SITE_WORDS[target]
    if (target or '').startswith('firing.'):
        return 'firing in ' + target[len('firing.'):]
    return target or '?'

def as_list(v):
    return list(v) if isinstance(v, (list, tuple)) else ([v] if v else [])

def drug_sentence(target, behavior, direction):
    site = site_words(target)
    what = MEASURE_WORDS.get(behavior, (behavior or '?').replace('_', ' '))
    if direction == 'higher':
        return 'Acts on %s → expect more %s.' % (site, what)
    if direction == 'lower':
        return 'Acts on %s → expect less %s.' % (site, what)
    return 'Acts on %s → %s changes.' % (site, what)

def plain_sentence(site, measure, expect, expect_text=None):
    if expect_text:
        return 'Acts on %s → expect %s.' % (site.lower(), expect_text)
    what = MEASURE_WORDS.get(measure, measure)
    arrow = {'higher': 'more', 'lower': 'less', 'same': 'no change in'}.get(expect, expect or 'a change in')
    return 'Acts on %s → expect %s %s.' % (site.lower(), arrow, what)

# =============================================================================
# SCHEMATIC SVG (site → measurement; offline, dependency-free)
# =============================================================================
def schematic(site, measure):
    def words(s, cx):
        # Word-wrap to at most 2 lines of ~15 chars; never overflows the box.
        bits, lines, cur = (s or '?').split(), [], ''
        for b in bits:
            trial = (cur + ' ' + b).strip()
            if len(trial) > 15 and cur:
                lines.append(cur)
                cur = b
            else:
                cur = trial
        if cur:
            lines.append(cur)
        lines = lines[:2]
        if len(' '.join(bits)) > len(' '.join(lines)):
            lines[-1] = lines[-1][:13] + '…'
        ys = [52] if len(lines) == 1 else [46, 60]
        return ''.join('<text x="%d" y="%d" text-anchor="middle" class="sch-word">%s</text>' % (
            cx, y, esc(ln)) for ln, y in zip(lines, ys))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 88" role="img">'
        '<rect x="4" y="12" width="112" height="64" rx="8" class="sch-box"/>'
        '<text x="60" y="31" text-anchor="middle" class="sch-label">SITE</text>'
        '%s'
        '<path d="M120 44 h16 m-6 -5 6 5 -6 5" class="sch-arrow"/>'
        '<rect x="140" y="12" width="116" height="64" rx="8" class="sch-box"/>'
        '<text x="198" y="31" text-anchor="middle" class="sch-label">MEASURE</text>'
        '%s'
        '</svg>' % (words(site, 60), words(measure, 198))
    )

def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

# =============================================================================
# CARD BUILDERS
# =============================================================================
def drug_cards(lib, cur, classics, recs):
    cards = []
    for key in sorted(set(lib) | set(classics)):
        in_lib = key in lib
        rule = cur.get(key) or classics.get(key) or {}
        target = rule.get('target', '')
        behaviors = as_list(rule.get('behavior'))
        primary = behaviors[0] if behaviors else ''
        exp = rule.get('expect') or {}
        direction = (exp.get('direction') if isinstance(exp, dict) else None) \
            or rule.get('target_prediction')
        name = key.replace('_', ' ')
        if in_lib:
            real, src = lib_real_status(key, lib)
        else:
            real, src = classic_real_status(rule)
        src_entry = lib.get(key) or classics.get(key) or {}
        card = dict(key='drug:' + key, kind='drug', title=name.title(),
                    target=target, behavior=behaviors,
                    sentence=(drug_sentence(target, primary, direction)
                              if (target or behaviors)
                              else 'No standing rule on file — target unknown.'),
                    mechanism=src_entry.get('mechanism', ''),
                    drug_class=src_entry.get('drug_class', '') or src_entry.get('kind', ''),
                    evidence=[e for e in src_entry.get('evidence', [])
                              if not e.startswith('Real-fly:')],
                    real=real, real_source=src)
        if real == "can't test yet":
            card['lab'] = dict(status='cant_test', why=src)
        else:
            rec = newest_for(recs, drug=key)
            if rec:
                card['lab'] = dict(status='run', record=record_ref(rec))
            else:
                card['lab'] = dict(status='ready', ask=ask_prefill(key, primary))
        cards.append(card)
    return cards

def record_ref(rec):
    card = rec.get('card') or {}
    words = rec.get('analysis') or ''
    words = ' '.join(words.split()) if isinstance(words, str) else ''
    return dict(id=rec.get('id'), title=card.get('title', rec.get('id')),
                verdict=rec.get('verdict'), date=rec.get('date'),
                measure=card.get('measure'), runs=len(rec.get('results', []) or []),
                story=words, recipe=record_recipe(rec))

# The run, explained row by row: fixed templates over the notebook record's own
# fields (control/treatment setups, repeats, steps, prediction, hypothesis,
# measure). Nothing invented; the "rule 2" line transcribes experiments.py.
DOSE_WORDS = {0.25: 'Low', 0.5: 'Standard', 1.5: 'High'}
PRED_WORD = {'higher': 'up', 'lower': 'down', 'different': 'different'}

def setup_words(setup):
    bits = []
    for a in setup or []:
        cmd = a.get('cmd')
        if cmd == 'restore':
            bits.append('restore to clean')
        elif cmd == 'dose':
            bits.append('dose %s %s' % (a.get('drug'), a.get('amount')))
        elif cmd == 'set_param':
            bits.append('set %s %s' % (a.get('key'), a.get('value')))
        elif cmd == 'stimulus':
            bits.append('stimulus %s level %s' % (a.get('channel'), a.get('level')))
        elif cmd == 'mutant':
            bits.append('raised as %s mutants' % a.get('name'))
        elif cmd == 'set_exposure':
            bits.append('%s exposure %s days' % (a.get('drug'), a.get('days')))
        else:
            bits.append(str(cmd))
    return bits

def record_recipe(rec):
    card = rec.get('card') or {}
    ctrl = card.get('control') or {}
    treat = (card.get('treatments') or [{}])[0]
    cbits = [w for a in (ctrl.get('setup') or []) if a.get('cmd') != 'restore'
             for w in setup_words([a])]
    control = '%s: both flies, %s. Every result is measured against these two.' % (
        ctrl.get('name') or 'control box', ', '.join(cbits) if cbits else 'nothing added')
    dose = next((a for a in (treat.get('setup') or []) if a.get('cmd') == 'dose'), None)
    mut = next((a for a in (treat.get('setup') or []) if a.get('cmd') == 'mutant'), None)
    led = None
    if dose:
        amt = dose.get('amount')
        word = DOSE_WORDS.get(amt)
        treatment = '%s at dose %s, both flies (a unitless knob on the sim, not milligrams)' % (
            (dose.get('drug') or '').replace('_', ' '),
            ('%s %s' % (word, amt)) if word else amt)
        led = 'dose'
    elif mut:
        treatment = 'raised as %s mutants, both flies' % mut.get('name')
        led = 'mutant'
    else:
        treatment = '%s, both flies' % (treat.get('name') or 'treatment box')
    extra = [w for a in (treat.get('setup') or [])
             if a.get('cmd') not in ('restore', led) for w in setup_words([a])]
    if extra:
        treatment += ' — with %s' % ', '.join(extra)
    effort = '%s repeats of %s steps — how much evidence the run collects (rule 2 needs at least 5 per side for a verdict).' % (
        card.get('repeats', '?'), card.get('steps', '?'))
    pred, hyp = card.get('prediction'), (card.get('hypothesis') or '').strip()
    if pred and hyp:
        prediction = 'treatment %s — %s' % (PRED_WORD.get(pred, pred), hyp)
    elif hyp:
        prediction = hyp
    elif pred:
        prediction = 'treatment %s' % PRED_WORD.get(pred, pred)
    else:
        prediction = 'no prediction on file'
    measure = site_words(card.get('measure'))
    secs = as_list(card.get('secondary'))
    if secs:
        sps = as_list(card.get('secondary_prediction'))
        bits = []
        for i, s in enumerate(secs):
            b = MEASURE_WORDS.get(s, (s or '').replace('_', ' '))
            d = sps[i] if i < len(sps) else None
            if d:
                b += ' (expects %s)' % PRED_WORD.get(d, d)
            bits.append(b)
        measure += '; also watches %s' % ', '.join(bits)
    return dict(control=control, treatment=treatment, effort=effort,
                prediction=prediction, measure=measure)

def p_cards(recs, behaviors):
    cards = []
    for p in P_TABLE:
        card = dict(key='check:' + p['key'], kind='model check', title=p['title'],
                    sentence=plain_sentence(p['site'], p['measure'], p['expect']),
                    claim=p['claim'], recorded=p['recorded'],
                    real=p['real'], real_source=p['real_source'],
                    schematic=schematic(p['site'], p['measure']))
        if p.get('needs_model'):
            card['lab'] = dict(status='needs_model', why=p['model_note'])
        elif p['key'] == 'P7':
            card['lab'] = dict(status='run', record=dict(
                kind='analysis', id=None, title='stage_map.py lateralisation analysis',
                verdict='recorded negative', date=None,
                measure=p['measure'], runs=0,
                note='Not a notebook run; result stands in validation.md P7.'))
        else:
            rec = newest_for(recs, drug=p['drug'])
            if rec:
                card['lab'] = dict(status='run', record=record_ref(rec))
            elif p.get('off_record'):
                card['lab'] = dict(status='run', record=dict(
                    kind='validation', id=None, title='validation.md ' + p['key'],
                    verdict='result stands', date=None,
                    measure=p['measure'], runs=0,
                    note='Ran during validation; no notebook record was saved.'))
            else:
                beh = (behaviors.get(p['drug']) or [None])[0]
                card['lab'] = dict(status='ready', ask=ask_prefill(p['drug'], beh))
        cards.append(card)
    return cards

def c_cards():
    return [dict(key='comparison:' + c['key'], kind='comparison', title=c['title'],
                 sentence=plain_sentence(c['site'], c['measure'], c['expect'],
                                         c.get('expect_text')),
                 claim=c['claim'], recorded=c['recorded'],
                 real=c['real'], real_source=c['real_source'],
                 schematic=schematic(c['site'], c['measure']),
                 lab=dict(status='run', record=dict(
                     kind='finding', id=None, title='comparative/FINDINGS.md §' + c['key'][1],
                     verdict='finding recorded', date='2026-10-30',
                     measure=c['measure'], runs=0,
                     note='Connectome analysis, not a sim run.')))
            for c in C_TABLE]

def sense_cards(recs):
    # Ask the flies cannot prefill stimulus verbs yet, so these carry a bench
    # note instead of an Ask link.
    cards = []
    for s in SENSES:
        card = dict(key='sense:' + s['key'], kind='new sense', title=s['title'],
                    sentence=plain_sentence(s['site'], s['measure'], s['expect']),
                    claim=s['claim'], real=s['real'], real_source=s['real_source'],
                    schematic=schematic(s['site'], s['measure']))
        rec = newest_for(recs, measure=s['channel'])
        if rec:
            card['lab'] = dict(status='run', record=record_ref(rec))
        else:
            card['lab'] = dict(status='ready', ask=None,
                               note='Run from the lab bench (stimulus verb) — Ask can\'t prefill senses yet.')
        cards.append(card)
    return cards

def mutant_cards(recs):
    # Ask the flies cannot prefill mutant verbs yet, so these carry a bench
    # note instead of an Ask link.
    cards = []
    for key in sorted(M.MUTANTS):
        m = M.MUTANTS[key]
        card = dict(key='mutant:' + key, kind='mutant',
                    title=m.get('title', key),
                    sentence='Perturbs gene %s (%s) → run it and watch brain firing and walking.' % (
                        m.get('gene', '?'), m.get('fbgn', '?')),
                    claim=m.get('note', ''),
                    real='already known',
                    real_source='FlyBase %s — classic mutant (paper to file)' % m.get('fbgn', '?'),
                    schematic=schematic('gene ' + str(m.get('gene', '?')), 'firing + walking'))
        rec = newest_for(recs, drug=key)
        if rec:
            card['lab'] = dict(status='run', record=record_ref(rec))
        else:
            card['lab'] = dict(status='ready', ask=None,
                               note='Run from the lab bench (mutant verb) — Ask can\'t prefill mutants yet.')
        cards.append(card)
    return cards

# =============================================================================
# STRUCTURES (GtoPdb SDF -> PubChem SMILES -> "structure not found")
# =============================================================================
SDF = os.path.join(DB, 'gtopdb', 'all_ligands.sdf')

def sdf_index(wanted):
    """Scan the SDF once; return {drug_key: molblock} for exact name hits."""
    try:
        from rdkit import Chem
    except ImportError:
        print('RDKit not installed: skipping all structures.')
        return {}
    try:
        suppl = Chem.SDMolSupplier(SDF, removeHs=False)
    except Exception as e:
        print('SDF unreadable (%s): skipping structures.' % e)
        return {}
    alias_of = {}
    for key, names in wanted.items():
        for n in names:
            alias_of[n.lower()] = key
    found = {}
    for mol in suppl:
        if mol is None:
            continue
        try:
            props = mol.GetPropsAsDict()
        except Exception:
            continue
        name = str(props.get('Name', '') or '')
        short = name.split(' : ')[-1].strip().lower()
        key = alias_of.get(short)
        if key and key not in found:
            found[key] = Chem.MolToMolBlock(mol)
        if len(found) == len(wanted):
            break
    return found

def lookup_smiles():
    """SMILES from the existing PubChem cache + our own fetched file."""
    out = {}
    for path in (os.path.join(FLY, 'notebook', 'lookups.json'),
                 os.path.join(HERE, 'fetched_smiles.json')):
        try:
            cache = json.load(open(path))
        except (OSError, ValueError):
            continue
        for k, v in cache.items():
            if not k.startswith('pubchem:'):
                continue
            smi = extract_smiles(v)
            if smi:
                out[k[len('pubchem:'):].lower()] = smi
    return out

def extract_smiles(v):
    if isinstance(v, str):
        return v if looks_like_smiles(v) else None
    if isinstance(v, dict):
        for k in ('smiles', 'SMILES', 'canonical_smiles', 'CanonicalSMILES',
                  'connectivity_smiles', 'isomeric_smiles'):
            if isinstance(v.get(k), str) and looks_like_smiles(v[k]):
                return v[k]
        for sub in v.values():
            smi = extract_smiles(sub)
            if smi:
                return smi
    if isinstance(v, list):
        for sub in v:
            smi = extract_smiles(sub)
            if smi:
                return smi
    return None

def looks_like_smiles(s):
    return bool(s) and len(s) < 2000 and re.search(r'[A-Za-z]', s) and '\n' not in s

def fetch_pubchem(name):
    url = ('https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/%s'
           '/property/CanonicalSMILES/JSON' % urllib.parse.quote(name))
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'FlyLab-Atlas/1.0'})
        res = urllib.request.urlopen(req, timeout=10)
        data = json.load(res)
        props = data['PropertyTable']['Properties']
        if not props:
            return None
        for k in ('ConnectivitySMILES', 'CanonicalSMILES', 'IsomericSMILES'):
            if props[0].get(k):
                return props[0][k]
        return None
    except Exception:
        return None

def mol_to_svg(mol):
    from rdkit import Chem
    from rdkit.Chem.Draw import rdMolDraw2D
    try:
        Chem.SanitizeMol(mol)
    except Exception:
        pass
    # Always lay out fresh 2D coords: SDF conformers can be degenerate.
    from rdkit.Chem import AllChem
    try:
        AllChem.Compute2DCoords(mol)
    except Exception:
        if not mol.GetNumConformers():
            return None
    try:
        d = rdMolDraw2D.MolDraw2DSVG(240, 180)
        d.DrawMolecule(mol)
        d.FinishDrawing()
        return d.GetDrawingText()
    except Exception:
        return None

# PubChem name overrides: the file key is ambiguous or unsearchable there.
PUBCHEM_NAMES = {'pmd': 'p-menthane-3,8-diol'}

def resolve_structures(cards, lib, classics, fetch):
    from rdkit import Chem  # noqa  (ImportError handled by caller)
    wanted = {}
    for c in cards:
        if c['kind'] != 'drug':
            continue
        key = c['key'].split(':', 1)[1]
        names = [key, key.replace('_', ' ')]
        names += (lib.get(key) or {}).get('aliases', [])
        names += (classics.get(key) or {}).get('aliases', [])
        wanted[key] = names
    blocks = sdf_index(wanted)
    cache = lookup_smiles()
    fetched = {}
    try:
        fetched = json.load(open(os.path.join(HERE, 'fetched_smiles.json')))
    except (OSError, ValueError):
        pass
    imgdir = os.path.join(HERE, 'img')
    os.makedirs(imgdir, exist_ok=True)
    report = dict(sdf=sorted(blocks), pubchem=[], missing=[])
    for c in cards:
        if c['kind'] != 'drug':
            continue
        key = c['key'].split(':', 1)[1]
        mol = None
        if key in blocks:
            try:
                mol = Chem.MolFromMolBlock(blocks[key])
            except Exception:
                mol = None
        if mol is None:
            smi = None
            for n in wanted[key]:
                if n.lower() in cache:
                    smi = cache[n.lower()]
                    break
            if smi is None and fetch:
                tries = [PUBCHEM_NAMES.get(key, key.replace('_', ' '))] + wanted[key]
                for t in tries:
                    smi = fetch_pubchem(t)
                    if smi:
                        break
                if smi:
                    fetched['pubchem:' + key] = smi
                    cache[key.lower()] = smi
            if smi:
                try:
                    mol = Chem.MolFromSmiles(smi)
                except Exception:
                    mol = None
            if mol is not None:
                report['pubchem'].append(key)
        if mol is not None:
            svg = mol_to_svg(mol)
            if svg:
                path = os.path.join(imgdir, key + '.svg')
                open(path, 'w').write(svg)
                c['structure'] = 'img/%s.svg' % key
                try:
                    from rdkit.Chem import rdMolDescriptors
                    c['formula'] = rdMolDescriptors.CalcMolFormula(mol)
                except Exception:
                    c['formula'] = None
                continue
        c['structure'] = None
        c['formula'] = None
        report['missing'].append(key)
    if fetch and fetched:
        json.dump(fetched, open(os.path.join(HERE, 'fetched_smiles.json'), 'w'), indent=1)
    return report

# =============================================================================
# MAIN
# =============================================================================
def main():
    fetch = '--no-fetch' not in sys.argv
    kn = json.load(open(os.path.join(LAB, 'ask', 'knowledge.json')))
    lib, cur = S.LIBRARY, S.CURATION
    classics = {k: v for k, v in kn['substances'].items() if k not in lib}
    recs = load_notebook()
    behaviors = {}
    for k in set(cur) | set(classics):
        rule = cur.get(k) or classics.get(k) or {}
        behaviors[k] = as_list(rule.get('behavior'))
    cards = (drug_cards(lib, cur, classics, recs) + p_cards(recs, behaviors)
             + c_cards() + sense_cards(recs) + mutant_cards(recs))
    for c in cards:
        h = HYPOTHESES.get(c['key'])
        if h:
            c['hypothesis'] = h
    try:
        srep = resolve_structures(cards, lib, classics, fetch)
    except ImportError:
        for c in cards:
            if c['kind'] == 'drug':
                c['structure'] = None
        srep = dict(sdf=[], pubchem=[], missing='rdkit missing')
    import datetime
    data = dict(generated=datetime.date.today().isoformat(),
                counts=count_cards(cards), cards=cards)
    json.dump(data, open(os.path.join(HERE, 'data.json'), 'w'), indent=1)
    print('cards: %d  (drugs %d, checks %d, comparisons %d, senses %d, mutants %d)' % (
        len(cards), *[data['counts']['kind'].get(k, 0) for k in
                      ('drug', 'model check', 'comparison', 'new sense', 'mutant')]))
    print('lab:', data['counts']['lab'])
    print('real:', data['counts']['real'])
    print('structures: %d sdf, %d pubchem, missing %s' % (
        len(srep['sdf']), len(srep['pubchem']), srep['missing']))

def count_cards(cards):
    kinds, labs, reals = {}, {}, {}
    for c in cards:
        kinds[c['kind']] = kinds.get(c['kind'], 0) + 1
        labs[c['lab']['status']] = labs.get(c['lab']['status'], 0) + 1
        reals[c['real']] = reals.get(c['real'], 0) + 1
    return dict(kind=kinds, lab=labs, real=reals)

if __name__ == '__main__':
    main()
