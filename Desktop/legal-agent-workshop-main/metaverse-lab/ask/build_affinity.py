"""Ligand -> target actions for lab substances from GtoPdb (read in place).

Input:  ~/Desktop/Databases/gtopdb/interactions.csv (24,968 rows): ligand,
        target (+ gene symbol, species), interaction type/action, affinity
        (median/low/high + units), primary-target flag, PubMed IDs.
Only ligands matching ask/knowledge.json substances (+ aliases) are kept.

Output (ask/): affinity.json {substance: [{target, gene, species, action,
type, primary, affinity, unit, pubmed}]} sorted human-first, primary-first.
Run:  cd metaverse-lab/ask && python3 build_affinity.py && python3 check.py
"""
import csv
import json
import os
import re

DB = '/Users/robin/Desktop/Databases/gtopdb'
HERE = os.path.dirname(os.path.abspath(__file__))


def norm(s):
    return re.sub(r'\s+', ' ', (s or '').strip().lower())


def main():
    subs = json.load(open(os.path.join(HERE, 'knowledge.json')))['substances']
    lab = {}
    for name, s in subs.items():
        lab[norm(name)] = name
        for a in s.get('aliases') or []:
            lab.setdefault(norm(a), name)

    out = {}
    with open(os.path.join(DB, 'interactions.csv'), newline='') as f:
        for row in csv.DictReader((ln for ln in f if not ln.startswith('"#'))):
            hit = lab.get(norm(row.get('Ligand')))
            if not hit:
                continue
            aff = (row.get('Affinity Median') or row.get('Affinity Low')
                   or row.get('Affinity High') or '').strip()
            try:
                aff_n = float(aff)
            except ValueError:
                aff_n = None
            entry = {'target': re.sub(r'<[^>]+>', '', row.get('Target') or '').strip(),
                     'gene': (row.get('Target Gene Symbol') or '').strip(),
                     'species': (row.get('Target Species') or '').strip(),
                     'action': (row.get('Action') or row.get('Type') or '').strip(),
                     'primary': (row.get('Primary Target') or '').strip().lower() == 'true',
                     'affinity': aff_n if aff_n is not None else aff,
                     'unit': (row.get('Affinity Units') or '').strip(),
                     'pubmed': (row.get('PubMed ID') or '').strip()}
            items = out.setdefault(hit, [])
            if entry not in items:
                items.append(entry)
    for items in out.values():
        items.sort(key=lambda e: (e['species'].lower() != 'human', not e['primary'],
                                  e['target']))  # human primary targets first
        del items[24:]
    with open(os.path.join(HERE, 'affinity.json'), 'w') as f:
        json.dump(out, f)
    print(json.dumps({'substances': len(out),
                      'rows': sum(len(v) for v in out.values())}, indent=1))


if __name__ == '__main__':
    main()
