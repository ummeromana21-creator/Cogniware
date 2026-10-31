"""Build Atlas L/R-side cards and male-subset cards from the held connectomes.

Same recipe as the 66 region rows (verified: this script reproduces all 58
neuropil fe/me/fs/ms exactly before writing anything):
  female counts = BANC connections_princeton.csv.gz grouped by neuropil code
  male counts   = Male CNS connections_princeton.csv.gz grouped by code

Outputs (this folder):
  sides.json   — one row per side (name NP_L/NP_R, level "side") for every
                 neuropil with side-resolved codes in either sex. A side value
                 is null when that sex cannot be split (unpaired codes), with
                 the reason in desc; nothing is estimated.
  subsets.json — one row per neuropil code (level "subset", male-only) from the
                 two formerly excluded files: Male Fly Connectome (VNC-only)
                 and Male Fly Optic Nerve (optic-right).

Run:  cd metaverse-lab/library && python3 build_sides.py && python3 check.py
Takes minutes (streams ~26M connection rows); sources read in place.
"""
import csv
import gzip
import json
import os
import re
import sys
from collections import Counter, defaultdict

DB = '/Users/robin/Desktop/Databases'
HERE = os.path.dirname(os.path.abspath(__file__))
THORACIC = {'1': 'ProNm', '2': 'MesoNm', '3': 'MetaNm'}
NERVE_M = {'ADMN_L', 'ADMN_R', 'AbN1_R', 'AbN2_L', 'AbN2_R', 'AbN3_L',
           'AbN3_R', 'AbN4_L', 'AbN4_R', 'AbNT_L', 'AbNT_R', 'DMetaN_L',
           'DMetaN_R', 'DProN_L', 'DProN_R', 'MesoAN_L', 'MesoAN_R',
           'MesoLN_L', 'MesoLN_R', 'MetaLN_L', 'MetaLN_R', 'PDMN_L',
           'PDMN_R', 'ProAN_L', 'ProAN_R', 'ProLN_L', 'ProLN_R', 'PrN_L',
           'VProN_L', 'VProN_R', 'CvN_R'}


def strip_side(code):
    return code[:-2] if code.endswith(('_L', '_R')) else code


def side_of(code):
    return code[-1] if code.endswith(('_L', '_R')) else None


def fmap(code, names):
    """BANC code -> region name."""
    if code in ('Xnerve', 'cervical_connective', 'Tct'):
        return 'nerves'
    if code == 'ANm':
        return 'Abdomen'
    if code in ('ABDNM_L', 'ABDNM_R'):
        return 'Abdomen'
    m = re.fullmatch(r'VNC_(?:T\d_)?([A-Za-z]+)_([LR])', code)
    if m:
        return m.group(1)
    m = re.fullmatch(r'([A-Za-z]+)_UTct_T\d_([LR])', code)
    if m:
        return m.group(1)
    m = re.fullmatch(r'LegNp_T([123])_([LR])', code)
    if m:
        return THORACIC[m.group(1)]
    base = strip_side(code)
    return base if base in names else None


def mmap(code, names):
    """Male CNS code -> region name."""
    if code == 'ANm':
        return 'Abdomen'
    if code in NERVE_M or code in ('AB_L', 'AB_R'):
        return 'nerves'
    if code in ('UNASGD', 'CV_UNASGD'):
        return 'VNC_UNASGD'
    m = re.fullmatch(r'([A-Za-z]+)_UTct_T\d_([LR])', code)
    if m:
        return m.group(1)
    if re.fullmatch(r'mVAC_T\d_[LR]', code):
        return 'mVAC'
    m = re.fullmatch(r'LegNp_T([123])_([LR])', code)
    if m:
        return THORACIC[m.group(1)]
    base = strip_side(code)
    return base if base in names else None


def scan(path, mapfn, names):
    syn = Counter()
    rows = Counter()
    unmapped = set()
    with gzip.open(path, 'rt', newline='') as f:
        for r in csv.DictReader(f):
            nm = mapfn(r['neuropil'], names)
            if nm is None:
                unmapped.add(r['neuropil'])
                continue
            key = (nm, side_of(r['neuropil']))
            syn[key] += int(r['syn_count'] or 0)
            rows[key] += 1
    return syn, rows, unmapped


def ratio(fs, ms):
    if fs and ms:
        return round(ms / fs, 2)
    if ms:
        return 'M-only'
    if fs:
        return 'F-only'
    return None


def main():
    regions = json.load(open(os.path.join(HERE, 'data.json')))
    neu = [e for e in regions if e['level'] == 'neuropil']
    names = {e['name'] for e in neu}
    by_name = {e['name']: e for e in neu}

    fsyn, frows, fun = scan(os.path.join(DB, 'BANC', 'connections_princeton.csv.gz'), fmap, names)
    msyn, mrows, mun = scan(os.path.join(DB, 'Male CNS', 'connections_princeton.csv.gz'), mmap, names)
    if fun or mun:
        print(f'UNMAPPED codes F={sorted(fun)} M={sorted(mun)}')
        sys.exit(1)

    # ---- verify: reproduce every published neuropil count exactly ----
    bad = []
    for e in neu:
        n = e['name']
        fs = sum(v for (nm, _s), v in fsyn.items() if nm == n)
        fe = sum(v for (nm, _s), v in frows.items() if nm == n)
        ms = sum(v for (nm, _s), v in msyn.items() if nm == n)
        me = sum(v for (nm, _s), v in mrows.items() if nm == n)
        if (fs, fe, ms, me) != (e['fs'], e['fe'], e['ms'], e['me']):
            bad.append(n)
    if bad:
        print(f'METHOD MISMATCH on {len(bad)} regions: {bad[:8]} — refusing to write')
        sys.exit(1)
    print(f'verified: reproduced 58/58 neuropil counts exactly')

    # ---- sides ----
    sides = []
    for e in sorted(neu, key=lambda x: x['row']):
        n = e['name']
        f_sided = fsyn.get((n, 'L'), 0) + fsyn.get((n, 'R'), 0)
        f_any = f_sided + fsyn.get((n, None), 0)
        m_sided = msyn.get((n, 'L'), 0) + msyn.get((n, 'R'), 0)
        m_any = m_sided + msyn.get((n, None), 0)
        if not (f_sided or m_sided):
            continue  # unpaired-only region: no sides exist
        for i, s in enumerate(('L', 'R')):
            fs = fsyn.get((n, s), 0) if f_sided else None
            ms = msyn.get((n, s), 0) if m_sided else None
            fe = frows.get((n, s), 0) if f_sided else None
            me = mrows.get((n, s), 0) if m_sided else None
            notes = []
            if f_any and not f_sided:
                notes.append('female side split unavailable (female file tags this region unpaired)')
            if m_any and not m_sided:
                notes.append('male side split unavailable (male file tags this region unpaired)')
            for tag, un in (('female', fsyn.get((n, None), 0)),
                            ('male', msyn.get((n, None), 0))):
                if un:
                    notes.append(f'plus {un:,} unpaired {tag} synapses not assigned to a side')
            parts = f'{(fs or 0):,} female + {(ms or 0):,} male'
            desc = (f"{'Left' if s == 'L' else 'Right'} half of the {n} region "
                    f"({e['func']}): {parts} synapses. " + ' '.join(notes)).strip()
            sides.append({
                'row': 2000 + len(sides),
                'level': 'side', 'hub': e['hub'], 'name': f'{n}_{s}',
                'base': n, 'fn': None, 'mn': None, 'fe': fe, 'me': me,
                'fs': fs, 'ms': ms, 'ratio': ratio(fs or 0, ms or 0),
                'func': f"{e['func']} ({'left' if s == 'L' else 'right'})",
                'desc': desc, 'img': e['img'],
            })
    with open(os.path.join(HERE, 'sides.json'), 'w') as f:
        json.dump(sides, f)

    # ---- subsets (male-only files) ----
    subsets = []
    for fname, label, tag, img in (
            ('Male Fly Connectome/connections_princeton.csv.gz',
             'Male Fly Connectome (VNC-only)', 'male VNC', 'img/row10_VNC.jpg'),
            ('Male Fly Optic Nerve/connections_princeton.csv.gz',
             'Male Fly Optic Nerve (optic-right)', 'male optic', 'img/row04_optic.jpg')):
        syn = Counter()
        rows = Counter()
        with gzip.open(os.path.join(DB, fname), 'rt', newline='') as f:
            for r in csv.DictReader(f):
                syn[r['neuropil']] += int(r['syn_count'] or 0)
                rows[r['neuropil']] += 1
        for code in sorted(syn, key=lambda c: -syn[c]):
            subsets.append({
                'row': 3000 + len(subsets),
                'level': 'subset', 'hub': 'subsets',
                'name': f'{code} ({tag})', 'code': code,
                'fn': None, 'mn': None, 'fe': None, 'me': rows[code],
                'fs': None, 'ms': syn[code], 'ratio': 'M-only',
                'func': f'male subset · {label.split("(")[1][:-1]}',
                'desc': (f'{label}: {rows[code]:,} connections, {syn[code]:,} male '
                         f'synapses tagged {code}. Male-only file; no female counterpart.'),
                'img': img,
            })
    with open(os.path.join(HERE, 'subsets.json'), 'w') as f:
        json.dump(subsets, f)
    print(json.dumps({'sides': len(sides), 'subsets': len(subsets),
                      'regions_split': len({s["base"] for s in sides})}, indent=1))


if __name__ == '__main__':
    main()
