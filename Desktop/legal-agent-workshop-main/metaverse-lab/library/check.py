"""Maintained check for the region library page. Run from this folder: python3 check.py"""
import json, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
fails = []


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (f" [{extra}]" if extra and not cond else ""))
    if not cond:
        fails.append(name)


d = json.load(open(os.path.join(HERE, "data.json")))
check("66 entries", len(d) == 66, len(d))
req = {"row", "level", "hub", "name", "fn", "mn", "fe", "me", "fs", "ms",
       "ratio", "func", "desc", "img"}
check("entry schema", all(set(e) == req for e in d))
check("hub F syn total", sum(e["fs"] or 0 for e in d if e["level"] == "hub") == 23556214)
check("hub M syn total", sum(e["ms"] or 0 for e in d if e["level"] == "hub") == 89860280)
missing = [e["img"] for e in d if not os.path.exists(os.path.join(HERE, e["img"]))]
check("card images resolve", not missing, missing[:3])
need = {e["name"] for e in d if e["level"] == "neuropil"
        and e["hub"] not in ("VNC", "unassigned", "nowire")}
idx = json.load(open(os.path.join(HERE, "points", "index.json")))
check("points cover brain neuropils", need == set(idx["regions"]),
      sorted(need ^ set(idx["regions"]))[:5])
nobin = [r for r in idx["regions"] if not os.path.exists(os.path.join(HERE, "points", f"{r}.bin"))]
check("region bins present", not nobin, nobin[:3])
for f in ("points/somas.bin", "points/classes.bin"):
    check(f"{f} present", os.path.exists(os.path.join(HERE, f)))

ct = json.load(open(os.path.join(HERE, "celltypes.json")))
check("236 cell types", len(ct) == 236, len(ct))
creq = {"row", "level", "hub", "name", "cls", "sub", "flow", "fn", "mn",
        "fe", "me", "fs", "ms", "ratio", "top", "func", "desc", "img"}
check("celltype schema", all(set(e) == creq for e in ct))
check("celltype levels", all(e["level"] == "celltype" and e["hub"] == "celltypes" for e in ct))
check("celltype names unique", len({e["name"] for e in ct}) == len(ct))
check("celltype neurons covered", sum(e["fn"] for e in ct) == 102490, sum(e["fn"] for e in ct))
r1 = next(e for e in ct if e["name"] == "R1-6")
check("R1-6 pinned", r1["fn"] == 8456 and r1["fs"] == 200627 and r1["top"] == ["LA"], r1["fn"])
check("celltype sorted by neurons", all(ct[i]["fn"] >= ct[i + 1]["fn"] for i in range(len(ct) - 1)))
check("celltype images resolve", all(os.path.exists(os.path.join(HERE, e["img"])) for e in ct))
check("celltype descs non-empty", all(e["desc"] and e["func"] for e in ct))

sd = json.load(open(os.path.join(HERE, "sides.json")))
ss = json.load(open(os.path.join(HERE, "subsets.json")))
check("94 sides", len(sd) == 94, len(sd))
check("47 regions split", len({e["base"] for e in sd}) == 47)
check("65 subsets", len(ss) == 65, len(ss))
mel = next(e for e in sd if e["name"] == "ME_L")
mer = next(e for e in sd if e["name"] == "ME_R")
check("ME_L pinned", mel["fs"] == 1672003 and mel["ms"] == 8358660, (mel["fs"], mel["ms"]))
check("ME_R pinned", mer["fs"] == 2471832 and mer["ms"] == 10323123, (mer["fs"], mer["ms"]))
check("ME sides sum to parent", mel["fs"] + mer["fs"] == 4143835 and mel["ms"] + mer["ms"] == 18681783)
sub_me = next(e for e in ss if e["name"] == "ME_R (male optic)")
check("subset ME_R pinned", sub_me["ms"] == 15267641, sub_me["ms"])
allnames = [e["name"] for e in d + ct + sd + ss]
dupes = [n for n in set(allnames) if allnames.count(n) > 1]
# GNG hub + GNG neuropil share a name in the original 66 rows (pre-existing);
# every new card file must add zero further collisions.
check("names unique across all card files", dupes == ["GNG"], dupes)
sreq = {"row", "level", "hub", "name", "base", "fn", "mn", "fe", "me",
        "fs", "ms", "ratio", "func", "desc", "img"}
check("side schema", all(set(e) == sreq for e in sd))
check("side levels", all(e["level"] == "side" for e in sd))
check("side names sided", all(e["name"].endswith(("_L", "_R")) for e in sd))
nameset = {x["name"] for x in sd}
check("side pairs complete", all((e["name"][:-2] + ("_L" if e["name"].endswith("_R") else "_R")) in nameset for e in sd))
dbyname = {e["name"]: e for e in d}
check("side bases are neuropils", all(e["base"] in dbyname and dbyname[e["base"]]["level"] == "neuropil" for e in sd))
check("side hubs match parent", all(e["hub"] == dbyname[e["base"]]["hub"] for e in sd))
check("side images match parent", all(e["img"] == dbyname[e["base"]]["img"] for e in sd))
sums = {}
for e in sd:
    a, b = sums.get(e["base"], (0, 0))
    sums[e["base"]] = (a + (e["fs"] or 0), b + (e["ms"] or 0))
ok = True
for base, (fs, ms) in sums.items():
    p = dbyname[base]
    if fs > (p["fs"] or 0) or ms > (p["ms"] or 0):
        ok = False
check("side sums within parent", ok)
need_note = [b for b, (fs, ms) in sums.items()
             if fs < (dbyname[b]["fs"] or 0) or ms < (dbyname[b]["ms"] or 0)]
noted = all(any("unpaired" in x["desc"] or "unavailable" in x["desc"] for x in sd if x["base"] == b) for b in need_note)
check("side shortfalls explained", noted, [b for b in need_note if not any("unpaired" in x["desc"] or "unavailable" in x["desc"] for x in sd if x["base"] == b)][:3])
ureq = {"row", "level", "hub", "name", "code", "fn", "mn", "fe", "me",
        "fs", "ms", "ratio", "func", "desc", "img"}
check("subset schema", all(set(e) == ureq for e in ss))
check("subset levels", all(e["level"] == "subset" and e["hub"] == "subsets" for e in ss))
check("subset male-only", all(e["fs"] is None and e["ms"] and e["ratio"] == "M-only" for e in ss))
check("subset names unique", len({e["name"] for e in ss}) == len(ss))
check("subset images resolve", all(os.path.exists(os.path.join(HERE, e["img"])) for e in ss))

sxd = json.load(open(os.path.join(HERE, "sexdiff.json")))
check("42 sexdiff regions", len(sxd) == 42, len(sxd))
check("sexdiff schema", all(set(v) == {"hub", "n", "fru", "dsx"} for v in sxd.values()))
check("sexdiff neurons assigned", sum(v["n"] for v in sxd.values()) == 133905)
check("sexdiff fru total", sum(v["fru"] for v in sxd.values()) == 1140)
check("sexdiff dsx total", sum(v["dsx"] for v in sxd.values()) == 120)
check("SMP top fru region", sxd["SMP"]["fru"] == 196 and sxd["SMP"]["hub"] == "lateral")
check("sexdiff regions are atlas regions",
      set(sxd) <= {e["name"] for e in d})

pw = json.load(open(os.path.join(HERE, "pathways.json")))
check("200 pathways", len(pw) == 200, len(pw))
check("pathway schema", all(set(e) == {"a", "b", "f", "m"} for e in pw))
check("pathways sorted desc", all(pw[i]["f"] + pw[i]["m"] >= pw[i + 1]["f"] + pw[i + 1]["m"] for i in range(len(pw) - 1)))
check("top pathway ME self-loop", pw[0]["a"] == "ME" and pw[0]["b"] == "ME" and pw[0]["f"] + pw[0]["m"] == 20474216)
top_ns = next(e for e in pw if e["a"] != e["b"])
check("top link ME->LO", (top_ns["a"], top_ns["b"]) == ("ME", "LO") and top_ns["f"] == 524087 and top_ns["m"] == 3375625)
check("pathway endpoints are atlas regions",
      {e["a"] for e in pw} | {e["b"] for e in pw} <= {e["name"] for e in d})

html = open(os.path.join(HERE, "index.html")).read()
js = open(os.path.join(HERE, "app.js")).read()
ids_html = set(re.findall(r'id="([^"]+)"', html)) | {"closeup"}  # rendered dynamically
used = set(re.findall(r'(?:getElementById|\$\()\s*["\']([^"\']+)["\']', js))
check("js ids exist in html", used <= ids_html, sorted(used - ids_html)[:5])
for ref in ("style.css", "app.js", "data.json", "meta.json", "celltypes.json",
            "sides.json", "subsets.json", "sexdiff.json", "pathways.json",
            "../study.html", "../vendor/three/three.module.js"):
    check(f"ref {ref}", os.path.exists(os.path.join(HERE, ref)))
check("celltypes hub chip", '"celltypes"' in js)
check("celltypes fetch", 'fetch("celltypes.json")' in js)
check("celltypes level option", 'value="celltype"' in html)
check("celltypes fallback caption", "per-type 3D positions not built yet" in js)
check("subsets hub chip", '"subsets"' in js)
check("sides fetch", 'fetch("sides.json")' in js)
check("subsets fetch", 'fetch("subsets.json")' in js)
check("side level option", 'value="side"' in html)
check("subset level option", 'value="subset"' in html)
check("side caption", "render shows the whole region" in js)
check("subset caption", "Male-only subset file" in js)
check("side display names", r'/^(.+)_([LR])$/' in js)
check("pathways fetch", 'fetch("pathways.json")' in js)
check("pathways toggle", 'id="pathways"' in js)
check("pathways lines", "LineSegments" in js)
check("cache-busted assets", "style.css?v=" in html and "app.js?v=" in html)
check("citation footer", "doi:10.13140" in html)
css = open(os.path.join(HERE, "style.css")).read()
check("loader hidden rule", ".loader[hidden]" in css)
check("tooltip element", 'id="partTip"' in html)
check("tooltip hidden rule", ".parttip[hidden]" in css)
m = re.search(r"const EXP = \{(.*?)\};", js, re.S)
keys = set(re.findall(r"([A-Za-z_]\w*): \"", m.group(1))) if m else set()
plain = {"lateral", "nowire", "olfactory", "optic", "unassigned", "Abdomen", "nerves"}
need = {e["name"] for e in d} - plain
check("expansions cover coded names", need == keys, sorted(need ^ keys)[:5])
for pin in ("Inframedial bridge", "Lower tectulum", "Cantle",
            "Medial ventral association center", "Antennal mechanosensory and motor center"):
    check(f"expansion {pin}", pin in js)
check("no stale .Detail class selectors", ".Detail" not in css)

if shutil.which("node"):
    # --check needs an ESM suffix for module syntax; copy aside
    shutil.copyfile(os.path.join(HERE, "app.js"), "/tmp/libcheck.mjs")
    r = subprocess.run(["node", "--check", "/tmp/libcheck.mjs"], capture_output=True)
    check("app.js syntax", r.returncode == 0, r.stderr.decode()[:200])
else:
    print("SKIP app.js syntax (no node)")

print()
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"FAILURES: {fails}")
sys.exit(1 if fails else 0)
