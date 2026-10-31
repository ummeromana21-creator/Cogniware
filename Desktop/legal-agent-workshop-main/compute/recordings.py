#!/usr/bin/env python3
"""compute/recordings.py - headless experiment records for the Lab 1 screens.

Runs the same engine as the lab through real experiment cards (validated by
experiments.py), analyses with experiments.analyse, and writes record JSONs
shaped EXACTLY like the lab's experiment_done records, so the page renders
replays and live runs with one renderer.

Every run gets its own seed; seeds are stored per run in the record.

Usage:
    python3 -m compute.recordings all --out metaverse-lab/rec --lab "/Users/.../Fly Brain"
"""
import argparse
import datetime
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

CARDS = {
    'quiet': {
        'title': 'Quiet brain',
        'hypothesis': 'Turning down background drive lowers whole-brain firing.',
        'control': {'name': 'control box', 'setup': [{'cmd': 'restore'}]},
        'treatments': [{'name': 'quiet', 'setup': [{'cmd': 'restore'}, {'cmd': 'set_param', 'key': 'I_bg', 'value': 0.8}]}],
        'measure': 'firing_all', 'prediction': 'lower', 'repeats': 3, 'steps': 400,
    },
    'noisy': {
        'title': 'Noisy brain',
        'hypothesis': 'Noisier steps change whole-brain firing.',
        'control': {'name': 'control box', 'setup': [{'cmd': 'restore'}]},
        'treatments': [{'name': 'noisy', 'setup': [{'cmd': 'restore'}, {'cmd': 'set_param', 'key': 'noise', 'value': 2.0}]}],
        'measure': 'firing_all', 'prediction': 'different', 'repeats': 3, 'steps': 400,
    },
    'scramble': {
        'title': 'Scramble wiring',
        'hypothesis': 'Shuffled wiring changes whole-brain firing.',
        'control': {'name': 'control box', 'setup': [{'cmd': 'restore'}]},
        'treatments': [{'name': 'scrambled', 'setup': [{'cmd': 'scramble'}]}],
        'measure': 'firing_all', 'prediction': 'different', 'repeats': 3, 'steps': 400,
    },
    'p7': {
        'title': 'Smell path (P7)',
        'hypothesis': 'Flies steer toward the banana by smell, so the treatment box catches differently.',
        'control': {'name': 'control box', 'setup': [{'cmd': 'restore'}]},
        'treatments': [{'name': 'treatment box', 'setup': [{'cmd': 'restore'}]}],
        'measure': 'bananas', 'prediction': 'different', 'repeats': 3, 'steps': 400,
    },
}

LABELS = {'banc': 'Maxime (female)', 'malecns': 'Hagrid (male)'}
SEED_BASE = {'quiet': 2000, 'noisy': 2100, 'scramble': 2200, 'p7': 2300}


def apply_setup(brain, pharm, setup):
    for a in setup:
        c = a['cmd']
        if c == 'restore':
            brain.restore()
            pharm.reset()
        elif c == 'set_param':
            brain.params[a['key']] = float(a['value'])
        elif c == 'dose':
            pharm.dose(a['drug'], float(a['amount']))
        elif c == 'washout':
            pharm.washout()
        elif c == 'scramble':
            brain.scramble()
        else:
            raise ValueError(f'setup verb {c!r} not supported headless')


def one_run(subject, setup, steps, seed):
    import cns_fly
    import fly
    import pharmacology as ph
    brain = cns_fly.CNSFly(subject, seed=seed)
    body = fly.FlyBody()
    world = fly.World(seed=seed)
    world.randomize_sugar()
    pharm = ph.Pharmacology(brain)
    apply_setup(brain, pharm, setup)
    for _ in range(30):
        sensory, dist = world.get_sensory(body.x, body.y, body.angle)
        motor, spiked = brain.step(sensory)
        pharm.step()
        body.move(motor)
    meter = ph.TrialMeter(brain, body)
    caught = False
    for _ in range(steps):
        sensory, dist = world.get_sensory(body.x, body.y, body.angle)
        now = dist < world.catch_radius
        motor, spiked = brain.step(sensory)
        pharm.step()
        prev = (body.x, body.y)
        body.move(motor)
        meter.update(spiked, motor, body, prev, now)
        caught = caught or now
    m = meter.result(brain, pharm)
    m.pop('firing', None)
    m.pop('drugs', None)
    return m


def record_card(name, out, lab):
    import experiments as EX
    import pharmacology as ph
    import cns_fly
    from figures import git_commit, cache_hash
    tmp = cns_fly.CNSFly('banc', seed=0)
    params = {k: float(tmp.params[k]) for k in sorted(tmp.params)}
    card, err = EX.validate(dict(CARDS[name]), ph.DRUGS, params)
    if err:
        sys.exit(f'card {name}: {err}')
    conds = [(card['control']['name'], card['control']['setup'])]
    conds += [(t['name'], t['setup']) for t in card['treatments']]
    results = {c: {lb: [] for lb in LABELS.values()} for c, _ in conds}
    seeds = {}
    idx = 0
    for cond, setup in conds:
        for subject, lb in LABELS.items():
            for rep in range(card['repeats']):
                seed = SEED_BASE[name] + idx
                idx += 1
                m = one_run(subject, setup, card['steps'], seed)
                results[cond][lb].append(m)
                seeds[f'{cond} / {lb} / run {rep + 1}'] = seed
                print(f'{name} {cond} {subject} rep {rep}: {card["measure"]}={m[card["measure"]]}', flush=True)
    analysis, verdict, details = EX.analyse(card, results)
    rec = {'id': f'exp-{name}-rec-{datetime.date.today().isoformat()}', 'card': card,
           'results': results, 'analysis': analysis, 'verdict': verdict, 'details': details,
           'date': datetime.date.today().isoformat(), 'seeds': seeds, 'simulated': True,
           'label': {'git_commit': git_commit(lab),
                     'dataset': f"banc sha1:{cache_hash(lab, 'banc')}+malecns sha1:{cache_hash(lab, 'malecns')}"},
           'subject': 'Maxime (female) + Hagrid (male) in control and treatment boxes'}
    path = os.path.join(out, f'rec-{name}.json')
    with open(path, 'w') as f:
        json.dump(rec, f, indent=1)
    print(f'wrote {path} -> {verdict}')
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('which', choices=sorted(CARDS) + ['all'])
    ap.add_argument('--out', default='rec')
    ap.add_argument('--lab', default=None)
    a = ap.parse_args()
    from figures import lab_path
    lab = a.lab or lab_path(a)
    sys.path.insert(0, lab)
    os.makedirs(a.out, exist_ok=True)
    names = sorted(CARDS) if a.which == 'all' else [a.which]
    for n in names:
        record_card(n, a.out, lab)


if __name__ == '__main__':
    main()
