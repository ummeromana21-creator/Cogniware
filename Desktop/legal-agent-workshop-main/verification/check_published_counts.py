#!/usr/bin/env python3
"""Layer 2 referee: our Codex exports vs the consortia's published numbers.

Reads the RAW CSVs (same files check_provenance.py uses) and compares ten
quantities against published values with sources. Modes:
  exact    ours must equal published, else FAIL
  within   ours must fall in published approximate range, else FAIL
  reported ours vs published printed for the record, never fails
Exit 0 = all exact/within pass. Reported lines document known differences
(thresholded exports, unpinned snapshot versions) with follow-ups.
"""
import csv
import gzip
import os
import sys

RAW = {
    "banc": os.environ.get("BRAINRAW_BANC", "/Users/robin/Desktop/BANC"),
    "malecns": os.environ.get("BRAINRAW_MALECNS", "/Users/robin/Desktop/Male CNS"),
    "flywire": os.environ.get("BRAINRAW_FLYWIRE", "/Users/robin/Desktop/Flywire "),
}
NOT_NEURON = {"glia", "trachea", "not_a_neuron"}


def scan_banc_malecns(d):
    n = dn = pc1 = syn = 0
    with gzip.open(os.path.join(d, "neurons.csv.gz"), "rt") as f:
        for r in csv.DictReader(f):
            if (r.get("Super Class") or "") in NOT_NEURON:
                continue
            n += 1
            t = r.get("Primary Cell Type") or ""
            if t.startswith("DN"):
                dn += 1
            if t.startswith("pC1"):
                pc1 += 1
    with gzip.open(os.path.join(d, "connections_princeton.csv.gz"), "rt") as f:
        for r in csv.DictReader(f):
            syn += int(r["syn_count"])
    return {"neurons": n, "dn": dn, "pc1": pc1, "synapses": syn}


def scan_flywire(d):
    import pandas as pd  # noqa: PLC0415 (heavy dep, flywire only)

    neu = pd.read_csv(os.path.join(d, "neurons.csv.gz"), usecols=["root_id"])
    typ = pd.read_csv(
        os.path.join(d, "consolidated_cell_types.csv.gz"),
        usecols=["root_id", "primary_type"],
    ).fillna("")
    m = neu.merge(typ, on="root_id", how="left").fillna("")
    t = m["primary_type"]
    return {
        "neurons": len(neu),
        "dn": int(t.str.startswith("DN").sum()),
        "pc1": int(t.str.startswith("pC1").sum()),
    }


def main():
    ours = {
        "banc": scan_banc_malecns(RAW["banc"]),
        "malecns": scan_banc_malecns(RAW["malecns"]),
        "flywire": scan_flywire(RAW["flywire"]),
    }
    checks = [
        # (label, ours, mode, expected, source/note)
        ("flywire neurons", ours["flywire"]["neurons"], "exact", 139255,
         "Dorkenwald et al., Nature 634:124-138 (2024), doi:10.1038/s41586-024-07558-y; FlyWire v783"),
        ("male-cns neurons", ours["malecns"]["neurons"], "exact", 166700,
         "Male CNS v1.0, male-cns.janelia.org (Janelia/LMB/MRC); Cell papers Sep 2026"),
        ("banc neurons", ours["banc"]["neurons"], "reported", "175401 (v888, glia dropped) / ~188k headline",
         "Bates et al., Nature 656:957-970 (2026). FOLLOW-UP: our Codex Princeton export (Oct 28 2026 "
         "download) predates/differs from v888; pin export snapshot version."),
        ("banc DN types", ours["banc"]["dn"], "within", (1200, 1400),
         "~1,300 DNs: Braun et al., Nature 2024, doi:10.1038/s41586-024-07523-9; "
         "Cheong et al. 2024 (MANC 1,328); Stürner et al., Nature 2025, "
         "doi:10.1038/s41586-025-08925-z (female)"),
        ("male-cns DN types", ours["malecns"]["dn"], "within", (1200, 1400),
         "same as above"),
        ("flywire DN types", ours["flywire"]["dn"], "within", (1200, 1400),
         "same as above"),
        ("male-cns pC1 (male-specific)", ours["malecns"]["pc1"], "reported", "~150",
         "Male CNS cell-type annotation. FOLLOW-UP: primary citation needed (best so far: project "
         "write-up, not peer-reviewed); female exports have 10 each (ours: banc %d, flywire %d)."
         % (ours["banc"]["pc1"], ours["flywire"]["pc1"])),
        ("male-cns synapses", ours["malecns"]["synapses"], "reported", "~124M (v1.0)",
         "Thresholded Princeton export keeps a subset; differs by design. Male export has no "
         "glia rows, so this matches provenance exactly."),
        ("banc synapses", ours["banc"]["synapses"], "reported", "~199M predicted (paper)",
         "Thresholded Princeton export keeps a subset; differs by design. Raw-file sum incl. "
         "glia-touching edges (provenance excludes them: 23,553,107)."),
    ]
    fails = 0
    for label, got, mode, exp, src in checks:
        if mode == "exact":
            ok = got == exp
            status = "MATCH" if ok else "MISMATCH"
        elif mode == "within":
            ok = exp[0] <= got <= exp[1]
            status = "IN-RANGE" if ok else "OUT-OF-RANGE"
        else:
            ok, status = True, "REPORTED"
        if not ok:
            fails += 1
        print(f"[{status}] {label}: ours={got} published={exp}\n        src: {src}")
    print("RESULT:", "ALL PASS" if not fails else f"{fails} FAILURES")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
