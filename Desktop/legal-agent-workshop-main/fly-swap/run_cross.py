#!/usr/bin/env python3
"""run_cross.py -- the wiring x tuning cross experiment, headless.

Four specimens, identical trials, real numbers: Maxime, Hagrid, and the two
crosses (each wiring on the other's background-drive tuning). Uses the same
engine (cns_fly), trial meter (pharmacology.TrialMeter) and step order as the
live lab's run_trial, so the numbers are directly comparable.

Copy into the Fly Brain folder and run:
    python3 run_cross.py [--repeats 3] [--steps 400] [--out cross_results.json]
"""
import argparse
import datetime
import json
import time

import numpy as np

import fly
import cns_fly
import pharmacology as ph

SPECIMENS = {
    'maxime':  dict(label='Maxime (banc wiring, I_bg 1.6)', subject='banc', params={}),
    'hagrid':  dict(label='Hagrid (malecns wiring, I_bg 1.5)', subject='malecns', params={}),
    'cross_a': dict(label='Cross-A (banc wiring, I_bg 1.5)', subject='banc', params={'I_bg': 1.5}),
    'cross_b': dict(label='Cross-B (malecns wiring, I_bg 1.6)', subject='malecns', params={'I_bg': 1.6}),
}
SETTLE = 30  # unmetered settle steps before each run, like the lab's experiments


def one_run(spec, steps, seed):
    """One fresh specimen, one trial. Mirrors Sim._tick + trial metering."""
    brain = cns_fly.CNSFly(spec['subject'], seed=seed, params=spec['params'])
    body = fly.FlyBody()
    world = fly.World(seed=seed)
    world.randomize_sugar()
    pharm = ph.Pharmacology(brain)
    for _ in range(SETTLE):
        sensory, _ = world.get_sensory(body.x, body.y, body.angle)
        motor, _ = brain.step(sensory)
        pharm.step()
        body.move(motor)
    meter = ph.TrialMeter(brain, body)
    caught = 0
    for _ in range(steps):
        sensory, dist = world.get_sensory(body.x, body.y, body.angle)
        caught_now = dist < world.catch_radius
        if caught_now:
            caught += 1
            world.randomize_sugar()
        motor, spiked = brain.step(sensory)
        pharm.step()
        prev = (body.x, body.y)
        body.move(motor)
        meter.update(spiked, motor, body, prev, caught_now)
    out = meter.result(brain, pharm)
    out['neurons'] = int(brain.n)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--repeats', type=int, default=3)
    ap.add_argument('--steps', type=int, default=400)
    ap.add_argument('--out', default='cross_results.json')
    args = ap.parse_args()
    t0 = time.time()
    log = {'procedure': {'specimens': {k: {'subject': v['subject'], 'params': v['params']} for k, v in SPECIMENS.items()},
                         'settle_steps': SETTLE, 'steps': args.steps, 'repeats': args.repeats},
           'runs': []}
    for rep in range(args.repeats):
        for key, spec in SPECIMENS.items():
            seed = 1000 + rep * 10 + list(SPECIMENS).index(key)
            m = one_run(spec, args.steps, seed=seed)
            m['tortuosity'] = m['tortuosity']  # None stays None in JSON
            log['runs'].append({'repeat': rep, 'specimen': key, 'seed': seed, 'metrics': m})
            print(f"rep {rep} {key}: activity={m['activity']:.4f} firing={m['firing_all']:.4f} "
                  f"bananas={m['bananas']} tort={m['tortuosity']}", flush=True)
    for k in ('firing', 'drugs'):
        for r in log['runs']:
            r['metrics'].pop(k, None)  # bulky per-group maps; scalars stay
    log['elapsed_s'] = round(time.time() - t0, 1)
    log['date'] = datetime.date.today().isoformat()
    import experiments as EX
    by = {}
    for r in log['runs']:
        by.setdefault(r['specimen'], []).append({'firing_all': r['metrics']['firing_all']})
    log['verdicts'] = {}
    for name, ctrl, treat in [('cross_a_vs_maxime', 'maxime', 'cross_a'), ('cross_b_vs_hagrid', 'hagrid', 'cross_b')]:
        mini = {'measure': 'firing_all', 'prediction': 'different',
                'control': {'name': ctrl}, 'treatments': [{'name': treat}]}
        txt, v, _det = EX.analyse_one(mini, {ctrl: by[ctrl], treat: by[treat]})
        log['verdicts'][name] = {'verdict': v, 'analysis': txt, 'prediction': 'different'}
    with open(args.out, 'w') as f:
        json.dump(log, f, indent=1)
    print(f'wrote {args.out} in {log["elapsed_s"]}s')


if __name__ == '__main__':
    main()
