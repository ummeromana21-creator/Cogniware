#!/usr/bin/env python3
"""Layer 3: fresh-clone test. Rebuild the fly cache from the raw CSVs into a
clean temp dir (no committed files read) and require it to match the
committed cache array-for-array. If it doesn't, something hides outside the
raw data + code.

Covers the "rebuilds identically" half. The other half is documented, not
tested: raw CSVs are not redistributed (consortium terms), so a true fresh
machine must first export neurons + connections_princeton from codex.flywire.ai.

Run:  SUBJECT=banc python3 check_fresh_clone.py   (default)
      SUBJECT=malecns|flywire python3 check_fresh_clone.py
"""
import os
import sys
import tempfile

import numpy as np

sys.path.insert(0, "/Users/robin/Desktop/Fly Brain")

SUBJECT = os.environ.get("SUBJECT", "banc")
RAW_FLYWIRE = os.environ.get("BRAINRAW_FLYWIRE", "/Users/robin/Desktop/Flywire ")
R = {}


def ok(name, cond, note=""):
    R[name] = bool(cond)
    print(f"   {'PASS' if cond else 'FAIL'}  {name}  {note}")


def same(a, b):
    a, b = np.asanyarray(a), np.asanyarray(b)
    if a.shape != b.shape or a.dtype != b.dtype:
        return False
    if np.issubdtype(a.dtype, np.floating):
        return bool(np.array_equal(a, b, equal_nan=True))
    return bool((a == b).all())


with tempfile.TemporaryDirectory(prefix="freshclone_") as tmp:
    fresh = os.path.join(tmp, "fly.npz")
    if SUBJECT == "flywire":
        import flywire_brain as mod  # noqa: E402

        committed = mod.CACHE
        print(f"Rebuilding {SUBJECT} from {RAW_FLYWIRE} ...")
        mod.build(data_dir=RAW_FLYWIRE, cache=fresh, verbose=False)
    else:
        import cns_fly as mod  # noqa: E402

        committed = mod.SUBJECTS[SUBJECT]["cache"]
        mod.SUBJECTS[SUBJECT]["cache"] = fresh  # runtime redirect only; source untouched
        print(f"Rebuilding {SUBJECT} from raw ...")
        mod.build(SUBJECT, verbose=False)
    ok("committed_cache_exists", os.path.exists(committed), committed)
    ok("fresh_cache_built", os.path.exists(fresh))

    zc, zf = np.load(committed), np.load(fresh)
    ok("same_keys", set(zc.files) == set(zf.files),
       f"{len(zc.files)} keys" if set(zc.files) == set(zf.files)
       else f"only-committed={sorted(set(zc.files) - set(zf.files))} only-fresh={sorted(set(zf.files) - set(zc.files))}")
    diffs = [k for k in zc.files if k in zf and not same(zc[k], zf[k])]
    for k in ("n", "synapses"):
        if k in zc.files and k in zf:
            ok(f"same_{k}", int(zc[k]) == int(zf[k]), f"{int(zf[k]):,}")
    for k in ("data", "indices", "indptr"):
        ok(f"same_W_{k}", k not in diffs)
    for k in ("region_names", "region_bounds", "pool_names", "pool_bounds",
              "root_id", "primary_type", "side", "nt_type", "super_class",
              "cls", "neuropil"):
        if k in zc.files and k in zf:
            ok(f"same_{k}", k not in diffs)
    if "xyz" in zc.files and "xyz" in zf:
        # xyz is display-only (server.py rounds it to 1e-3 for the hologram;
        # step() never reads it) and carries float32 rounding noise ~2e-7
        # between builds, so near-equality at 1e-6 is the honest bar.
        near = np.allclose(zc["xyz"].astype(float), zf["xyz"].astype(float),
                           atol=1e-6, equal_nan=True)
        ok("same_xyz_display_only", near, "allclose atol=1e-6")
        if "xyz" in diffs and near:
            diffs.remove("xyz")
    ok("no_other_diffs", not [d for d in diffs],
       f"differing keys: {diffs}" if diffs else "every array identical")

print("\n==== SUMMARY ====")
for k, v in R.items():
    print(f"   {k:24s} {'PASS' if v else 'FAIL'}")
print("ALL PASS" if all(R.values()) else "NOT YET")
sys.exit(0 if all(R.values()) else 1)
