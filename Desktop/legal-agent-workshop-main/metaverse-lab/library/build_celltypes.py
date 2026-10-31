"""Build Atlas cell-type cards from the held FlyWire tables (read in place, never copied).

Inputs (~/Desktop/Databases/Flywire /):
  consolidated_cell_types.csv.gz  root_id -> primary_type
  classification.csv.gz           root_id -> flow, class, sub_class, side
  neurons.csv.gz                  root_id -> predicted nt_type
  neuropil_synapse_table.csv.gz   root_id -> per-neuropil in/out synapses

Output (this folder): celltypes.json — one row per primary type with >=50
neurons, shaped as a superset of the data.json region rows so the Atlas grid,
filters, sort and detail view reuse them unchanged (level "celltype",
hub "celltypes"). FAFB is a female brain: fn/fs filled, mn/ms null.

Run:  cd metaverse-lab/library && python3 build_celltypes.py && python3 check.py
"""
import csv
import gzip
import json
import os
from collections import Counter, defaultdict

DB = '/Users/robin/Desktop/Databases/Flywire '
HERE = os.path.dirname(os.path.abspath(__file__))
MIN_NEURONS = 50


def open_gz(name):
    return gzip.open(os.path.join(DB, name), 'rt', newline='')


def main():
    ptype, cls, sub, flow, side, nt = {}, {}, {}, {}, {}, {}
    with open_gz('consolidated_cell_types.csv.gz') as f:
        for r in csv.DictReader(f):
            if r['primary_type']:
                ptype[r['root_id']] = r['primary_type']
    with open_gz('classification.csv.gz') as f:
        for r in csv.DictReader(f):
            i = r['root_id']
            cls[i], sub[i], flow[i], side[i] = (r['class'], r['sub_class'],
                                               r['flow'], r['side'])
    with open_gz('neurons.csv.gz') as f:
        for r in csv.DictReader(f):
            if r['nt_type']:
                nt[r['root_id']] = r['nt_type']

    counts = Counter(ptype.values())
    keep = {t for t, n in counts.items() if n >= MIN_NEURONS}

    # per-type accumulators (only kept types)
    n = Counter()
    nside = defaultdict(Counter)
    nts = defaultdict(Counter)
    syn = Counter()
    neu = defaultdict(lambda: defaultdict(int))
    meta = {}

    with open_gz('neuropil_synapse_table.csv.gz') as f:
        rd = csv.reader(f)
        head = next(rd)
        # column -> (neuropil code w/o side, in|out) for "X synapses in NP_S" cols
        colmap = {}
        for i, h in enumerate(head):
            parts = h.split(' ')
            if len(parts) == 4 and parts[1] == 'synapses' and parts[2] == 'in':
                code = parts[3]
                base = code[:-2] if code.endswith(('_L', '_R')) else code
                colmap[i] = (base, parts[0])  # input|output
        try:
            i_in, i_out = head.index('input synapses'), head.index('output synapses')
        except ValueError:
            i_in = i_out = None
        for row in rd:
            t = ptype.get(row[0])
            if t not in keep:
                continue
            n[t] += 1
            if row[0] in meta:
                pass
            else:
                meta[t] = (cls.get(row[0], ''), sub.get(row[0], ''),
                             flow.get(row[0], ''))
            nside[t][side.get(row[0], '')] += 1
            if row[0] in nt:
                nts[t][nt[row[0]]] += 1
            if i_in is not None:
                try:
                    syn[t] += (int(row[i_in] or 0) + int(row[i_out] or 0))
                except ValueError:
                    pass
            for i, (base, _io) in colmap.items():
                try:
                    v = int(row[i] or 0)
                except ValueError:
                    continue
                if v:
                    neu[t][base] += v

    rows = []
    for j, t in enumerate(sorted(keep, key=lambda t: -counts[t])):
        c, s, fl = meta.get(t, ('', '', ''))
        label = (s or c).replace('_', ' ') or 'unclassified'
        nt_mode, nt_n = nts[t].most_common(1)[0] if nts[t] else ('', 0)
        top = [k for k, _ in sorted(neu[t].items(), key=lambda kv: -kv[1])[:3]]
        lr = f"{nside[t].get('right', 0)}/{nside[t].get('left', 0)} R/L" \
            if (nside[t].get('right', 0) or nside[t].get('left', 0)) else 'side unlisted'
        bits = [f"{counts[t]:,} neurons ({lr})", label]
        if top:
            bits.append('loudest in ' + ', '.join(top))
        if nt_mode:
            bits.append(f"transmitter {nt_mode} (predicted, "
                        f"{nt_n / max(1, sum(nts[t].values())):.0%})")
        rows.append({
            'row': 1000 + j,
            'level': 'celltype',
            'hub': 'celltypes',
            'name': t,
            'cls': c, 'sub': s, 'flow': fl,
            'fn': counts[t], 'mn': None, 'fe': None, 'me': None,
            'fs': syn[t], 'ms': None, 'ratio': None,
            'top': top,
            'func': f"cell type · {label}",
            'desc': ' · '.join(bits) + '.',
            'img': 'img/celltype.svg',
        })
    with open(os.path.join(HERE, 'celltypes.json'), 'w') as f:
        json.dump(rows, f)
    print(json.dumps({'celltypes': len(rows),
                      'neurons_covered': sum(counts[t] for t in keep)}, indent=1))


if __name__ == '__main__':
    main()
