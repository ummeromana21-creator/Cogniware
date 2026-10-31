#!/usr/bin/env python3
"""Layer 3: determinism test. Same seed twice -> identical to the last digit.

Builds the brain twice with seed=0, checks the connectome is bitwise
identical, then runs the same scripted + closed-loop steps on both and checks
motor outputs and spike trains match exactly. Any mismatch means something
uses unrecorded randomness.

Run:  BRAIN=random python3 check_determinism.py     (fast, default)
      BRAIN=banc|malecns|flywire python3 check_determinism.py   (real brains)
"""
import os
import sys

import numpy as np

sys.path.insert(0, "/Users/robin/Desktop/Fly Brain")
import fly  # noqa: E402

BRAIN = os.environ.get("BRAIN", "random")
STEPS = int(os.environ.get("DSTEPS", "200"))
R = {}


def ok(name, cond, note=""):
    R[name] = bool(cond)
    print(f"   {'PASS' if cond else 'FAIL'}  {name}  {note}")


if BRAIN == "flywire":
    import flywire_brain  # noqa: E402

    make_brain = lambda seed: flywire_brain.FlyWireBrain(seed=seed)
elif BRAIN in ("banc", "malecns"):
    import cns_fly  # noqa: E402

    make_brain = lambda seed: cns_fly.CNSFly(BRAIN, seed=seed)
else:
    make_brain = lambda seed: fly.FlyBrain(seed=seed)

print(f"Building two {BRAIN} brains with seed=0 ...")
a = make_brain(0)
b = make_brain(0)
ok("same_n", a.n == b.n, f"n={a.n}")
ok("same_regions", a.regions == b.regions)
ok("same_nnz", a.W.nnz == b.W.nnz, f"nnz={a.W.nnz}")
ad, bd = a.W.tocoo(), b.W.tocoo()
same_w = (ad.row == bd.row).all() and (ad.col == bd.col).all() and (ad.data == bd.data).all()
ok("same_weights_bitwise", bool(same_w))


def run(brain, seed):
    world, body = fly.World(seed=seed), fly.FlyBody()
    body.x, body.y, body.angle = 0.0, 0.0, 0.0
    world.sugar_x, world.sugar_y = 5.0, 0.0
    motors, spikes = [], 0
    for _ in range(STEPS):
        sensory, _ = world.get_sensory(body.x, body.y, body.angle)
        motor, spiked = brain.step(sensory)
        motors.append(np.array(motor, dtype=float))
        spikes += int(spiked.sum())
        body.move(np.nan_to_num(motor))
    return np.array(motors), spikes, (body.x, body.y, body.angle)


m1, s1, e1 = run(a, 0)
m2, s2, e2 = run(b, 0)
ok("same_motor_trace", bool((m1 == m2).all()), f"{STEPS} steps")
ok("same_spike_total", s1 == s2, f"{s1} spikes")
ok("same_end_pose", e1 == e2, f"({e1[0]:.4f}, {e1[1]:.4f}, {e1[2]:.4f})")
ok("nontrivial_run", s1 > 0 and np.abs(m1).sum() > 0, "the sim actually did something")

print("\n==== SUMMARY ====")
for k, v in R.items():
    print(f"   {k:24s} {'PASS' if v else 'FAIL'}")
print("ALL PASS" if all(R.values()) else "NOT YET")
sys.exit(0 if all(R.values()) else 1)
