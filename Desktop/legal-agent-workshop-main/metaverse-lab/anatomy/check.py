"""Anatomy dataset checks: gene catalog + drug-target bridge.

Build first:  cd metaverse-lab/anatomy && python3 build.py && python3 check.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f" — {detail}" if detail and not cond else ""))
    if not cond:
        fails.append(name)


def load(name):
    with open(os.path.join(HERE, name)) as f:
        return json.load(f)


G = load('genes.json')['genes']
B = load('drug_targets.json')['bridge']

# 1. catalog shape
check("genes catalog non-trivial", len(G) > 5000, f"n={len(G)}")
bad_keys = [k for k in G if not re.fullmatch(r'FBgn\d+', k)]
check("all keys are FBgn ids", not bad_keys, str(bad_keys[:3]))
no_sym = [k for k, g in G.items() if not g.get('s')]
check("every gene has a symbol", not no_sym, str(no_sym[:3]))
for field in ['syn', 'h', 'd', 'c', 'e']:
    missing = [k for k, g in G.items() if field not in g]
    check(f"field '{field}' universal", not missing, str(missing[:3]))

# 2. enrichment coverage (legs must have landed, not just the catalog)
n_h = sum(1 for g in G.values() if g['h'])
n_d = sum(1 for g in G.values() if g['d'])
n_c = sum(1 for g in G.values() if g['c'])
n_e = sum(1 for g in G.values() if g['e'])
check("ortholog leg landed", n_h > 5000, f"n={n_h}")
check("disease leg landed", n_d > 100, f"n={n_d}")
check("chemical leg landed", n_c > 100, f"n={n_c}")
check("expression leg landed", n_e > 100, f"n={n_e}")

# 3. expression entries carry anatomy + sim mapping
es = [e for g in G.values() for e in g['e']]
check("expression sites named", all(e.get('site') for e in es))
mapped = [e for e in es if e.get('sim')]
check("some sites map to sim groups", len(mapped) > 50, f"n={len(mapped)}")

# 4. drug-target bridge
with open(os.path.join(HERE, '..', 'ask', 'knowledge.json')) as f:
    subs = json.load(f)['substances']
check("bridge covers lab substances", len(B) >= 5, f"n={len(B)}")
check("caffeine bridged", 'caffeine' in B)
for name, hits in B.items():
    if name not in subs:
        check(f"bridge member '{name}' is a lab substance", False)
        break
else:
    check("all bridge members are lab substances", True)
for hits in B.values():
    for h in hits:
        if not all(k in h for k in ['t', 'a', 'f', 'e']):
            check("bridge hits carry t/a/f/e", False, str(h)[:150])
            break
    else:
        continue
    break
else:
    check("bridge hits carry t/a/f/e", True)

# 5. payload sizes stay page-friendly
for name, cap in [('genes.json', 30 << 20), ('drug_targets.json', 1 << 20)]:
    size = os.path.getsize(os.path.join(HERE, name))
    check(f"{name} under {cap >> 20 or 1}MB", size < cap, f"{size} bytes")

print()
if fails:
    print(f"RESULT: {len(fails)} FAILING")
    sys.exit(1)
print("RESULT: ALL CHECKS PASSED")
