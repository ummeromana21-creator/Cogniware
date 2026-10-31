"""Build fly anatomy datasets from the held databases (read in place, never copied).

Outputs (this folder): genes.json, drug_targets.json. See README.md.
Run:  cd metaverse-lab/anatomy && python3 build.py
"""
import csv
import gc
import gzip
import json
import os
import re
import sys

DB = '/Users/robin/Desktop/Databases'
HERE = os.path.dirname(os.path.abspath(__file__))
KNOWLEDGE = os.path.join(HERE, '..', 'ask', 'knowledge.json')

# FBbt anatomy site -> sim group(s). Unmapped sites are kept with sim: [].
SITE_MAP = {
    'antennal lobe': ['antennal lobe PNs'],
    'mushroom body': ['mushroom body'],
    'mushroom body calyx': ['mushroom body'],
    'lateral horn': ['lateral horn'],
    'central complex': ['central complex'],
    'ellipsoid body': ['central complex'],
    'fan-shaped body': ['central complex'],
    'protocerebral bridge': ['central complex'],
    'noduli': ['central complex'],
    'lateral accessory lobe': ['central complex'],
    'optic lobe': ['optic lobes'],
    'medulla': ['optic lobes'],
    'lobula': ['optic lobes'],
    'lamina': ['optic lobes'],
    'ventral nerve cord': ['nerve cord'],
    'thoracic ganglion': ['nerve cord'],
    'brain': ['brain-wide'],
    'adult brain': ['brain-wide'],
    'eye': ['optic lobes'],
    'antenna': ['nose'],
    'antennal nerve': ['nose'],
    'leg': ['legs'],
    'descending neuron': ['descending neurons'],
}


def disp(d):
    return ((d or {}).get('displayText')) or ''


def load_json_gz(path):
    with gzip.open(path, 'rt') as f:
        return json.load(f)['data']


def main():
    stats = {}
    # ---- 1. FlyBase synonyms: FBgn -> symbol, synonyms ----
    genes = {}
    sym_path = os.path.join(DB, 'fb_synonym_fb_2026_03.tsv')
    with open(sym_path) as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            parts = line.rstrip('\n').split('\t')
            if len(parts) < 3:
                continue
            fb, _org, sym = parts[0], parts[1], parts[2]
            if not fb.startswith('FBgn'):
                continue
            syns = []
            for col in parts[4:6]:
                syns += [s.strip() for s in col.split(',') if s.strip()] if col else []
            g = genes.setdefault(fb, {'s': sym, 'syn': [], 'h': [], 'd': [],
                                      'c': [], 'e': []})
            g['s'] = sym
            g['syn'] = sorted(set(g['syn']) | set(syns))[:12]
    stats['fb_synonyms'] = len(genes)

    # ---- 2. Alliance genes (fly slice): full names ----
    print('step 2/7: alliance genes...', flush=True)
    recs = load_json_gz(os.path.join(DB, 'alliance', 'GENE_JSON_COMBINED.json.gz'))
    nfly = 0
    for r in recs:
        g0 = r.get('gene', r)                              # records are gene_summary wrappers
        if ((g0.get('taxon') or {}).get('name') or '') != 'Drosophila melanogaster':
            continue
        fb = (g0.get('primaryExternalId') or '').replace('FB:', '')
        if not fb.startswith('FBgn'):
            continue
        nfly += 1
        g = genes.setdefault(fb, {'s': disp(g0.get('geneSymbol')), 'syn': [],
                                    'h': [], 'd': [], 'c': [], 'e': []})
        name = disp(g0.get('geneFullName'))
        if name:
            g['n'] = name
        for s in (g0.get('geneSynonyms') or [])[:12]:
            t = disp(s)
            if t and t not in g['syn'] and len(g['syn']) < 12:
                g['syn'].append(t)
    del recs
    gc.collect()
    stats['alliance_fly_genes'] = nfly
    print(f"  fly genes: {nfly}", flush=True)
    if not nfly:
        sys.exit('FAIL-FAST: no fly genes parsed from GENE_JSON_COMBINED (schema drift?)')

    # ---- 3. Orthology: human best-score partners per fly gene ----
    print('step 3/7: orthology...', flush=True)
    recs = load_json_gz(os.path.join(DB, 'alliance', 'ORTHOLOGY-ALLIANCE_JSON_COMBINED.json.gz'))
    hum2fly = {}
    for r in recs:
        g = r.get('geneToGeneOrthologyGenerated', {})
        s, o = g.get('subjectGene') or {}, g.get('objectGene') or {}
        ts = ((s.get('taxon') or {}).get('name')) or ''
        to = ((o.get('taxon') or {}).get('name')) or ''
        fly, hum = None, None
        if 'melanogaster' in ts and 'sapiens' in to:
            fly, hum = s, o
        elif 'sapiens' in ts and 'melanogaster' in to:
            fly, hum = o, s
        else:
            continue
        if ((g.get('isBestScore') or {}).get('name')) != 'Yes':
            continue
        fb = (fly.get('primaryExternalId') or '').replace('FB:', '')
        hs = disp(hum.get('geneSymbol'))
        if not fb.startswith('FBgn') or not hs:
            continue
        genes.setdefault(fb, {'s': disp(fly.get('geneSymbol')), 'syn': [],
                          'h': [], 'd': [], 'c': [], 'e': []})
        if hs not in genes[fb]['h']:
            genes[fb]['h'].append(hs)
        hum2fly.setdefault(hs, []).append(fb)
    del recs
    gc.collect()
    stats['fly_genes_with_human_ortholog'] = sum(1 for g in genes.values() if g['h'])

    # ---- 4. Human disease -> fly genes via best orthologs (marked inferred) ----
    print('step 4/7: disease...', flush=True)
    recs = load_json_gz(os.path.join(DB, 'alliance', 'DISEASE-ALLIANCE_JSON_HUMAN.json.gz'))
    for r in recs:
        subj = r.get('subject') or {}
        obj = r.get('object') or {}
        hs = disp(subj.get('geneSymbol')) or (subj.get('primaryExternalId') or '').replace('HGNC:', '')
        dn = (obj.get('name')) or ''
        doid = (obj.get('curie')) or ''
        if not hs or not dn:
            continue
        for fb in hum2fly.get(hs, [])[:4]:
            if fb in genes and len(genes[fb]['d']) < 10:
                entry = {'d': dn, 'id': doid, 'via': hs}
                if entry not in genes[fb]['d']:
                    genes[fb]['d'].append(entry)
    del recs
    gc.collect()
    stats['fly_genes_with_disease'] = sum(1 for g in genes.values() if g['d'])

    # ---- 5. CTD chemical-gene, fly rows (taxon 7227) ----
    print('step 5/7: CTD chemical-gene...', flush=True)
    sym2fb = {}
    for fb, g in genes.items():
        sym2fb.setdefault(g['s'], fb)
    chem_gene = {}
    with open(os.path.join(DB, 'ctd', 'CTD_chem_gene_ixns.csv')) as f:
        for line in f:
            if line.startswith('#'):
                continue
            row = next(csv.reader([line]))
            if len(row) < 11 or row[7] != '7227':
                continue
            chem, sym, acts, pmid = row[0], row[3], row[9], row[10]
            fb = sym2fb.get(sym)
            if not fb:
                continue
            if len(genes[fb]['c']) < 12:
                entry = {'c': chem, 'a': acts, 'p': pmid}
                if entry not in genes[fb]['c']:
                    genes[fb]['c'].append(entry)
            chem_gene.setdefault(chem.lower(), []).append((fb, sym, acts))
    stats['fly_genes_with_chemicals'] = sum(1 for g in genes.values() if g['c'])

    # ---- 6. Alliance expression, fly slice: gene -> anatomy sites ----
    print('step 6/7: alliance expression...', flush=True)
    recs = load_json_gz(os.path.join(DB, 'alliance', 'EXPRESSION-ALLIANCE_JSON_COMBINED.json.gz'))
    unmapped = {}
    nfly_e = 0
    for r in recs:
        a = r.get('geneExpressionAnnotation', r)           # records are wrappers
        subj = a.get('expressionAnnotationSubject') or {}
        if ((subj.get('taxon') or {}).get('name') or '') != 'Drosophila melanogaster':
            continue
        fb = (subj.get('primaryExternalId') or '').replace('FB:', '')
        if not fb.startswith('FBgn') or fb not in genes:
            continue
        nfly_e += 1
        pat = a.get('expressionPattern') or {}
        where = (pat.get('whereExpressed') or {}).get('anatomicalStructure') or {}
        site = (where.get('name')) or ''
        curie = (where.get('curie')) or ''
        if not site:
            continue
        key = site.lower()
        sim = SITE_MAP.get(key, [])
        if not sim:
            unmapped[site] = unmapped.get(site, 0) + 1
        entry = {'site': site, 'fb': curie, 'sim': sim,
                 'stage': (a.get('whenExpressedStageName')) or '',
                 'assay': ((a.get('expressionAssayUsed') or {}).get('name')) or ''}
        if entry not in genes[fb]['e'] and len(genes[fb]['e']) < 16:
            genes[fb]['e'].append(entry)
    del recs
    gc.collect()
    stats['fly_genes_with_expression'] = sum(1 for g in genes.values() if g['e'])
    print(f"  fly annotations: {nfly_e}, genes with expression: {stats['fly_genes_with_expression']}", flush=True)
    if not nfly_e:
        sys.exit('FAIL-FAST: no fly expression parsed from EXPRESSION combined (schema drift?)')
    stats['top_unmapped_sites'] = sorted(unmapped.items(), key=lambda kv: -kv[1])[:10]

    # ---- 7. Drug-target bridge for the lab's substances ----
    print('step 7/7: drug-target bridge...', flush=True)
    gtop = os.path.join(DB, 'gtopdb')
    with open(os.path.join(gtop, 'ligands.csv')) as f:
        rd = csv.DictReader((l for l in f if not l.startswith('"#')))
        lig_lower = {}
        for r in rd:
            lid = r.get('Ligand ID')
            if not lid:
                continue
            names = [r.get('Name') or '']
            names += re.split(r'[|;]', r.get('Synonyms') or '')
            for nm in names:
                nm = nm.strip().lower()
                if nm:
                    lig_lower.setdefault(nm, []).append(lid)
    actions = {}
    import glob as _glob
    for path in _glob.glob(os.path.join(gtop, '*interactions.csv')):
        with open(path) as f:
            rd = csv.DictReader((l for l in f if not l.startswith('"#')))
            for r in rd:
                try:
                    lid = r.get('Ligand ID') or r.get('Ligand id')
                    tgt = r.get('Target Gene Symbol') or ''
                    act = r.get('Action') or ''
                    sp = r.get('Target Species') or ''
                except (AttributeError, KeyError):
                    continue
                if lid and tgt:
                    actions.setdefault(lid, []).append(
                        {'t': tgt, 'a': act, 'sp': sp})
    with open(KNOWLEDGE) as f:
        subs = json.load(f)['substances']
    bridge = {}
    for name, s in subs.items():
        hits = []
        for alias in [name, ' '.join(name.split('_'))] + (s.get('aliases') or []):
            for lid in lig_lower.get((alias or '').lower(), []):
                for a in actions.get(lid, [])[:6]:
                    fs = sorted({fb for hs in [a['t']]
                                 for fb in hum2fly.get(hs, [])[:3]})
                    sites = sorted({e['site'] for fb in fs
                                    for e in genes.get(fb, {}).get('e', [])})[:8]
                    hits.append({'t': a['t'], 'a': a['a'], 'sp': a['sp'],
                                 'f': [genes[fb]['s'] for fb in fs if fb in genes],
                                 'e': sites})
        if hits:
            seen, ded = set(), []
            for h in hits:
                k = (h['t'], h['a'])
                if k not in seen:
                    seen.add(k)
                    ded.append(h)
            bridge[name] = ded[:8]
    stats['substances_with_targets'] = len(bridge)

    # ---- outputs ----
    with open(os.path.join(HERE, 'genes.json'), 'w') as f:
        json.dump({'built': 'build.py', 'genes': genes}, f)
    with open(os.path.join(HERE, 'drug_targets.json'), 'w') as f:
        json.dump({'built': 'build.py', 'bridge': bridge,
                   'note': 'mammalian targets mapped to fly via best orthologs; expression is anatomy-level, literature-curated'}, f)
    stats['genes_total'] = len(genes)
    print(json.dumps(stats, indent=1))


if __name__ == '__main__':
    main()
