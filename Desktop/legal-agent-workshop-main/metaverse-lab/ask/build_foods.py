"""Build food -> lab-compound tables from the held food databases (read in place).

Inputs:
  ~/Desktop/Databases/foodb/foodb_2020_04_07_json.zip :: Content.json (3.5 GB
      JSON-lines; streamed, never extracted): food x compound amounts.
  ~/Desktop/Databases/duke/Duke-Source-CSV/{FARMACY_NEW,COMMON_NAMES}.csv:
      plant x phytochemical amounts (Duke's units, usually ppm).

Only compounds the lab can dose (ask/knowledge.json substances + aliases) are
kept, so every row answers "what in this food reaches the fly, and how much".
Amounts stay in their source units; ranks compare within one food and one unit.

Output (ask/): foods.json {food: {items: [{substance, amount, unit, source}],
note}} sorted by amount desc.
Run:  cd metaverse-lab/ask && python3 build_foods.py && python3 check.py
"""
import csv
import json
import os
import re
import zipfile

DB = '/Users/robin/Desktop/Databases'
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
    # chemical-name variants the databases use for lab substances
    lab.update({k: v for k, v in
                [('1,8-cineole', 'eucalyptol')] if v in subs})

    foods = {}

    def add(food, substance, amount, unit, source):
        if amount is None or (isinstance(amount, float) and amount <= 0):
            return
        foods.setdefault(food, []).append(
            {'substance': substance, 'amount': amount, 'unit': unit, 'source': source})

    # ---- FooDB (stream the 3.5 GB file inside the zip) ----
    n_foodb = 0
    z = zipfile.ZipFile(os.path.join(DB, 'foodb', 'foodb_2020_04_07_json.zip'))
    with z.open('foodb_2020_04_07_json/Content.json') as f:
        for line in f:
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if r.get('source_type') != 'Compound':
                continue
            hit = lab.get(norm(r.get('orig_source_name')))
            if not hit:
                continue
            try:
                amt = float(r.get('standard_content') or 'nan')
            except ValueError:
                continue
            if not amt > 0:
                continue
            food = (r.get('orig_food_common_name') or 'unknown food').strip()
            part = (r.get('orig_food_part') or '').strip()
            if part:
                food = f'{food} ({part.lower()})'
            add(food, hit, amt, r.get('orig_unit') or 'unspecified',
                f"FooDB/{r.get('citation') or 'unknown'}")
            n_foodb += 1

    # ---- Duke's ----
    names = {}
    with open(os.path.join(DB, 'duke', 'Duke-Source-CSV', 'COMMON_NAMES.csv'), newline='') as f:
        for r in csv.DictReader(f):
            names[r['FNFNUM']] = r['CNNAM']
    n_duke = 0
    with open(os.path.join(DB, 'duke', 'Duke-Source-CSV', 'FARMACY_NEW.csv'), newline='') as f:
        for r in csv.DictReader(f):
            hit = lab.get(norm(r.get('CHEM')))
            if not hit:
                continue
            lo = r.get('AMT_LO') or r.get('AMT_OR_LO') or ''
            hi = r.get('AMT_HI') or r.get('AMT_OR_HI') or r.get('AMT_ULHI') or ''
            try:
                amt = float(hi or lo or 'nan')
            except ValueError:
                continue
            if not amt > 0:
                continue
            plant = names.get(r['FNFNUM'], f"FNFNUM {r['FNFNUM']}")
            add(f"{plant} (Duke's)", hit, amt,
                r.get('QUANT_UNIT') or "unspecified (Duke's units)", "Duke's FARMACY")
            n_duke += 1

    out = {}
    for food, items in foods.items():
        items.sort(key=lambda e: -(e['amount'] if isinstance(e['amount'], float) else 0))
        units = {e['unit'] for e in items}
        out[food] = {'items': items[:12],
                     'note': 'ranked by amount' if len(units) == 1
                     else 'amounts use mixed units; ranks are indicative only'}
    with open(os.path.join(HERE, 'foods.json'), 'w') as f:
        json.dump(out, f)
    n_lab = len({e['substance'] for v in out.values() for e in v['items']})
    print(json.dumps({'foods': len(out), 'lab_compounds_hit': n_lab,
                      'foodb_rows': n_foodb, 'duke_rows': n_duke}, indent=1))


if __name__ == '__main__':
    main()
