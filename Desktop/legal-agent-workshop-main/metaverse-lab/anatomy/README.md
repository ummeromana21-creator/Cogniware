# Fly anatomy datasets

Gene catalog, coarse expression maps, and drug-target bridges for Maxime and
Hagrid — built from the held databases, not hand-written. Every row keeps its
source so the pages can cite it.

## Sources (read in place, never copied)

- `~/Desktop/Databases/fb_synonym_fb_2026_03.tsv` — FlyBase symbol/synonym map
- `~/Desktop/Databases/alliance/` — genes, orthology, disease (FB), expression
- `~/Desktop/Databases/ctd/CTD_chem_gene_ixns.csv` — chemical–gene rows
- `~/Desktop/Databases/gtopdb/` — ligand actions + target map
- `../ask/knowledge.json` — the lab's substance list (what to bridge)

## Outputs (built, committed)

- `genes.json` — fly genes: symbol, synonyms, human orthologs, disease links,
  chemicals, expression sites. Slim by design (see sizes in check.py).
- `drug_targets.json` — substance → mammalian targets → fly orthologs →
  expression sites, each leg cited.

## Rebuild

```bash
cd metaverse-lab/anatomy && python3 build.py && python3 check.py
```

Builds stream the big files; nothing is extracted. Disk stays untouched
apart from the two JSON outputs.
