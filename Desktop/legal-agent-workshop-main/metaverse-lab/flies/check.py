"""Maintained check for the Meet-the-flies page. Run from this folder: python3 check.py"""
import json, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
fails = []


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (f" [{extra}]" if extra and not cond else ""))
    if not cond:
        fails.append(name)


html = open(os.path.join(HERE, "index.html")).read()
mods = re.findall(r'<script type="module">(.*?)</script>', html, re.S)
check("one inline module", len(mods) == 1, len(mods))
js = mods[0] if mods else ""

# ids referenced from JS must exist in the static HTML or be rendered dynamically
ids_html = set(re.findall(r'id="([^"]+)"', html)) | {"closeup"}  # rendered dynamically
used = set(re.findall(r'''(?:getElementById|\$\()\s*["']([^"']+)["']''', js))
check("js ids exist in html", used <= ids_html, sorted(used - ids_html)[:5])

for ref in ("index.html", "../study.html", "../library/index.html",
            "../ask/index.html", "../vendor/three/three.module.js"):
    check(f"ref {ref}", os.path.exists(os.path.join(HERE, ref)))

# portal nav links resolve to real pages
for href in ("../study.html", "../library/", "../ask/", "./"):
    check(f"nav {href}", f'href="{href}"' in html)

# pinned content: atlas numbers + taxonomy + both flies
for pin in ("157,789", "166,700", "23.6 million", "89.9 million",
            "66 entries", "DROSOPHILIDAE", "Diptera", "Drosophila",
            "Maxime", "Hagrid", "Compound eye", "Haltere", "labelBtn",
            "partTip", "partAt", "intersectObjects"):
    check(f"content {pin}", pin in html)

check("importmap vendored three", '"three": "../vendor/three/three.module.js"' in html)
check("no test hooks", "window.__" not in html)
urls = sorted(set(re.findall(r'https?://[^"\' )]+', html)))
ext = [u for u in urls if not any(h in u for h in
       ("fonts.googleapis.com", "fonts.gstatic.com", "www.w3.org"))]
check("only font CDN external", not ext, ext[:3])
check("canvas + stage + sheet", all(x in html for x in ('id="gl"', 'id="stage"', 'id="detail"')))

if mods and shutil.which("node"):
    open("/tmp/fliescheck.mjs", "w").write(js)
    r = subprocess.run(["node", "--check", "/tmp/fliescheck.mjs"], capture_output=True)
    check("module syntax", r.returncode == 0, r.stderr.decode()[:200])
else:
    print("SKIP module syntax (no node)")

print()
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"FAILURES: {fails}")
sys.exit(1 if fails else 0)
