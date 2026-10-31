#!/usr/bin/env python3
"""check_figures.py - referee for compute/figures.py output.

Checks that every figure exists and its label block is complete. No numbers
are typed in as expected results.

Copy to the Fly Brain folder and run:
    python3 check_figures.py [--out compute/out] [--subject banc]
"""
import argparse
import json
import os
import sys

REQUIRED_KEYS = ['title', 'simulated', 'git_commit', 'dataset', 'date', 'seed', 'seeds', 'params', 'xlabel', 'dose']

PER_SUBJECT = ['raster', 'rates', 'waterfall', 'regime', 'vtrace', 'ringbump']
SHARED = ['dose-response', 'dose-caffeine', 'dose-ethanol', 'dose-isoflurane', 'timecourse']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default='compute/out')
    ap.add_argument('--subject', default='banc')
    a = ap.parse_args()
    names = [f'{n}-{a.subject}' for n in PER_SUBJECT] + SHARED
    fails = []
    for n in names:
        png = os.path.join(a.out, n + '.png')
        js = os.path.join(a.out, n + '.json')
        if not os.path.exists(png):
            fails.append(f'{n}: missing PNG')
            continue
        if not os.path.exists(js):
            fails.append(f'{n}: missing sidecar JSON')
            continue
        try:
            meta = json.load(open(js))
        except Exception as e:
            fails.append(f'{n}: bad JSON ({e})')
            continue
        for k in REQUIRED_KEYS:
            if k not in meta or meta[k] in (None, '', 'unknown'):
                fails.append(f'{n}: label block missing/empty {k!r}')
        if 'simulated' not in str(meta.get('title', '')).lower():
            fails.append(f'{n}: title lacks "simulated"')
    if fails:
        print('\n'.join(f'FAIL {f}' for f in fails))
        sys.exit(1)
    print(f'all {len(names)} figures exist with complete label blocks: PASS')


if __name__ == '__main__':
    main()
