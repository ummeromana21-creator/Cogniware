"""Inter-region wiring pathways for the Atlas 3D view (read sources in place).

Method (same recipe as build_sides.py, whose code->region maps are reused):
  1. Home region per neuron = modal neuropil across its BANC (female) /
     Male CNS (male) connection rows, mapped to Atlas region names.
  2. Pathway A->B = synapses of connections with home(pre)=A, home(post)=B.
Self-loops are kept in the data; the 3D view draws only A!=B pairs.

Output (this folder): pathways.json [{a, b, f, m}] top 200 by f+m.
Run:  cd metaverse-lab/library && python3 build_pathways.py && python3 check.py
"""
import csv
import gzip
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_sides as BS

DB = '/Users/robin/Desktop/Databases'
HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    regions = json.load(open(os.path.join(HERE, 'data.json')))
    names = {e['name'] for e in regions if e['level'] == 'neuropil'}

    home = {}
    for label, fname, mapfn in (
            ('female', 'BANC/connections_princeton.csv.gz', BS.fmap),
            ('male', 'Male CNS/connections_princeton.csv.gz', BS.mmap)):
        votes = defaultdict(Counter)
        with gzip.open(os.path.join(DB, fname), 'rt', newline='') as f:
            for r in csv.DictReader(f):
                nm = mapfn(r['neuropil'], names)
                if nm is None:
                    continue
                votes[r['pre_root_id']][nm] += 1
                votes[r['post_root_id']][nm] += 1
        home[label] = {root: cnt.most_common(1)[0][0] for root, cnt in votes.items()}
        print(f'{label}: {len(home[label])} neurons homed', flush=True)

    edges = Counter()
    for label, fname in (('female', 'BANC/connections_princeton.csv.gz'),
                         ('male', 'Male CNS/connections_princeton.csv.gz')):
        h = home[label]
        with gzip.open(os.path.join(DB, fname), 'rt', newline='') as f:
            for r in csv.DictReader(f):
                a, b = h.get(r['pre_root_id']), h.get(r['post_root_id'])
                if a is None or b is None:
                    continue
                edges[(label, a, b)] += int(r['syn_count'] or 0)

    pairs = defaultdict(lambda: [0, 0])
    for (label, a, b), v in edges.items():
        pairs[(a, b)][0 if label == 'female' else 1] += v
    rows = [{'a': a, 'b': b, 'f': f, 'm': m} for (a, b), (f, m) in pairs.items()]
    rows.sort(key=lambda e: -(e['f'] + e['m']))
    with open(os.path.join(HERE, 'pathways.json'), 'w') as f:
        json.dump(rows[:200], f)
    top = rows[0]
    print(json.dumps({'pairs': len(rows), 'kept': min(200, len(rows)),
                      'top': [top['a'], top['b'], top['f'] + top['m']]}, indent=1))


if __name__ == '__main__':
    main()
