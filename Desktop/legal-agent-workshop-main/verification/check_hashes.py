"""Verify lab data files against committed SHA-256 sums. Run from repo root: python3 verification/check_hashes.py"""
import hashlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SUMS = os.path.join(HERE, "SHA256SUMS")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ok, changed, missing, total = 0, [], [], 0
    for line in open(SUMS):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        total += 1
        want, _, path = line.partition("  ")
        if not os.path.isabs(path):
            path = os.path.join(os.path.dirname(HERE), path)
        if not os.path.exists(path):
            missing.append(path)
        elif sha256(path) == want:
            ok += 1
        else:
            changed.append(path)
    print(f"checked: {total}, ok: {ok}, changed: {len(changed)}, missing: {len(missing)}")
    for p in changed[:10]:
        print("CHANGED", p)
    for p in missing[:10]:
        print("MISSING", p)
    print("RESULT:", "ALL CHECKS PASSED" if not changed and not missing else "FAILURES PRESENT")
    return 1 if changed or missing else 0


if __name__ == "__main__":
    sys.exit(main())
