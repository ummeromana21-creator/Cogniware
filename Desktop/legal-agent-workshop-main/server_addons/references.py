"""references.py - verbatim reference cache for the ask page.

Each substance gets one JSON file with retrieved records, stored exactly as
the source returned them plus source/url/date lines. Nothing here is
hand-written and nothing is generated: the AI answers strictly from these
records plus knowledge.json, and the page cites them.

Sources wired now: PubChem (no key). CompTox and CTD are later: CompTox needs
a free user key, CTD has no per-chemical REST.
"""
import json
import os
import urllib.parse
import urllib.request

SOURCES = ('pubchem',)
TIMEOUT = 15


def cache_path(outdir, substance):
    safe = ''.join(c if c.isalnum() else '_' for c in substance.lower())
    return os.path.join(outdir, safe + '.json')


def lookup(substance, outdir):
    """Cached record for a substance, or None when never prefetched."""
    try:
        with open(cache_path(outdir, substance)) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def _get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'flylab/1.0'})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        return json.loads(r.read().decode('utf-8', 'replace'))


def fetch_pubchem(name):
    """Compound facts + description + assay summary, each best-effort."""
    base = 'https://pubchem.ncbi.nlm.nih.gov/rest/pug'
    q = urllib.parse.quote(name)
    rec = {'source': 'pubchem', 'query': name, 'parts': {}}
    for part, url in [
        ('compound', f'{base}/compound/name/{q}/property/Title,MolecularFormula,IUPACName,CanonicalSMILES/JSON'),
        ('description', f'{base}/compound/name/{q}/description/JSON'),
        ('assays', f'{base}/compound/name/{q}/assaysummary/JSON'),
    ]:
        try:
            rec['parts'][part] = {'url': url, 'data': _get(url)}
        except Exception as e:  # network or unknown name: recorded, not fatal
            rec['parts'][part] = {'url': url, 'error': f'{type(e).__name__}: {e}'[:200]}
    return rec


def fetch_all(name):
    return {'pubchem': fetch_pubchem(name)}


def prefetch(names, outdir):
    """Fetch and store one cache file per name. Returns {name: 'ok'|'empty'}."""
    import datetime
    os.makedirs(outdir, exist_ok=True)
    status = {}
    for name in names:
        rec = {'substance': name,
               'retrieved': datetime.date.today().isoformat(),
               'records': fetch_all(name)}
        ok = any('data' in p for p in rec['records']['pubchem']['parts'].values())
        with open(cache_path(outdir, name), 'w') as f:
            json.dump(rec, f, indent=1)
        status[name] = 'ok' if ok else 'empty'
    return status
