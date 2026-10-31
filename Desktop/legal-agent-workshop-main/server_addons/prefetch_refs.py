"""Prefetch reference records for the ask page. Run once (needs internet):

    python3 prefetch_refs.py [knowledge.json] [outdir]

Stores one verbatim JSON file per dosable substance. The AI answers strictly
from these files plus knowledge.json; rerun anytime to refresh dates.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import references

KNOW = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..', 'metaverse-lab', 'ask', 'knowledge.json')
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..', 'reference_cache')

with open(KNOW) as f:
    knowledge = json.load(f)
names = [n for n, s in knowledge['substances'].items() if s.get('dosable')]
status = references.prefetch(names, OUT)
for n, st in status.items():
    print(f'{n}: {st}')
print(f'{sum(1 for s in status.values() if s == "ok")}/{len(status)} cached in {OUT}')
