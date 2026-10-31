"""Sexual-dimorphism wiring map: fru/dsx neurons per brain region (data only).

Join (read in place, never copied):
  Flywire labels.csv.gz             root_id -> free-text labels ('putative fru', ...)
  Flywire neuropil_synapse_table    root_id -> argmax neuropil by in+out synapses
  library/data.json                 region names + hubs
A neuron counts as fru/dsx when ANY label contains 'fru'/'dsx' (case-insensitive).
Region = the neuropil holding most of its synapses (L/R merged).

Output (this folder): sexdiff.json {region: {hub, n, fru, dsx}}.
Run:  cd metaverse-lab/library && python3 build_sexdiff.py && python3 check.py
"""
import csv
import gzip
import json
import os
from collections import Counter, defaultdict

DB = '/Users/robin/Desktop/Databases/Flywire '
HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    fru, dsx = set(), set()
    with gzip.open(os.path.join(DB, 'labels.csv.gz'), 'rt', newline='') as f:
        for r in csv.DictReader(f):
            lab = (r['label'] or '').lower()
            if 'fru' in lab:
                fru.add(r['root_id'])
            if 'dsx' in lab:
                dsx.add(r['root_id'])

    regions = json.load(open(os.path.join(HERE, 'data.json')))
    hub_of = {e['name']: e['hub'] for e in regions}

    def region_of(code):
        base = code[:-2] if code.endswith(('_L', '_R')) else code
        return base if base in hub_of else None

    n, nfru, ndsx = Counter(), Counter(), Counter()
    nunmapped = 0
    with gzip.open(os.path.join(DB, 'neuropil_synapse_table.csv.gz'), 'rt', newline='') as f:
        rd = csv.reader(f)
        head = next(rd)
        cols = []
        for i, h in enumerate(head):
            p = h.split(' ')
            if len(p) == 4 and p[1] == 'synapses' and p[2] == 'in':
                cols.append((i, p[3]))
        for row in rd:
            tot = defaultdict(int)
            for i, code in cols:
                try:
                    v = int(row[i] or 0)
                except ValueError:
                    continue
                if v:
                    tot[code] += v
            if not tot:
                continue
            reg = region_of(max(tot, key=tot.get))
            if reg is None:
                nunmapped += 1
                continue
            n[reg] += 1
            if row[0] in fru:
                nfru[reg] += 1
            if row[0] in dsx:
                ndsx[reg] += 1

    out = {reg: {'hub': hub_of[reg], 'n': n[reg], 'fru': nfru[reg], 'dsx': ndsx[reg]}
           for reg in sorted(n, key=lambda r: (-nfru[r], -n[r]))}
    with open(os.path.join(HERE, 'sexdiff.json'), 'w') as f:
        json.dump(out, f)
    print(json.dumps({'regions': len(out), 'neurons_assigned': sum(n.values()),
                      'fru_neurons': len(fru), 'dsx_neurons': len(dsx),
                      'fru_assigned': sum(nfru.values()), 'dsx_assigned': sum(ndsx.values()),
                      'unmapped_region': nunmapped}, indent=1))


if __name__ == '__main__':
    main()
