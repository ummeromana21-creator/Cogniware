"""Maintained check for the Experiment Atlas page. Run from this folder: python3 check.py"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.dirname(HERE)
FLY = os.path.expanduser("~/Desktop/Fly Brain")
fails = []


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (f" [{extra}]" if extra and not cond else ""))
    if not cond:
        fails.append(name)


# ---------- data.json ----------
dpath = os.path.join(HERE, "data.json")
check("data.json built", os.path.exists(dpath))
data = json.load(open(dpath)) if os.path.exists(dpath) else {"cards": []}
cards = data.get("cards", [])
check("cards non-empty", len(cards) > 40, len(cards))
check("generated stamp", bool(data.get("generated")), data.get("generated"))

kinds = {}
for c in cards:
    kinds[c["kind"]] = kinds.get(c["kind"], 0) + 1
check("counts match cards", data.get("counts", {}).get("kind") == kinds, kinds)
for k in ("drug", "model check", "comparison", "new sense", "mutant"):
    check(f"kind present: {k}", k in kinds)

# every card: sentence + real status + lab status from the fixed vocabularies
REALS = {"already known", "partly known", "not yet tested", "can't test yet"}
LABS = {"run", "ready", "needs_model", "cant_test"}
bad = [c["key"] for c in cards
       if not c.get("sentence") or c.get("real") not in REALS
       or c.get("lab", {}).get("status") not in LABS]
check("card schema + vocab", not bad, bad[:5])

# run records pinned to real notebook files (or declared non-notebook kinds)
nb_ids = {os.path.splitext(os.path.basename(f))[0]
          for f in glob.glob(os.path.join(FLY, "notebook", "*.json"))}
dangling = []
for c in cards:
    rec = c.get("lab", {}).get("record") or {}
    rid = rec.get("id")
    if rid and rid not in nb_ids:
        dangling.append(rid)
check("run records exist", not dangling, dangling[:3])

# run cards carry the record's written result, verbatim from the notebook
nostory, drift = [], []
for c in cards:
    rec = c.get("lab", {}).get("record") or {}
    rid = rec.get("id")
    if not rid:
        continue
    if not rec.get("story"):
        nostory.append(rid)
        continue
    src = json.load(open(os.path.join(FLY, "notebook", rid + ".json")))
    if rec["story"] != " ".join((src.get("analysis") or "").split()):
        drift.append(rid)
check("run stories present", not nostory, nostory[:3])
check("run stories verbatim", not drift, drift[:3])
appjs = open(os.path.join(HERE, "app.js")).read()
css = open(os.path.join(HERE, "style.css")).read()
check("story rendered", 'r.story' in appjs and '.story' in css)

# run cards carry a two-clause hypothesis (text + given + falsified)
nohyp = [c["key"] for c in cards if c.get("lab", {}).get("status") == "run"
         and not all((c.get("hypothesis") or {}).get(k) for k in ("text", "given", "falsified"))]
check("run hypotheses present", not nohyp, nohyp[:3])
RECON = "the card's rationale, not a sealed prediction"
check("hypothesis rendered", 'c.hypothesis' in appjs and '?hyp=' in appjs and RECON in appjs)

# notebook-backed run records carry the explained 5-row recipe
norecipe = [c["key"] for c in cards
            if (c.get("lab", {}).get("record") or {}).get("id")
            and not all(((c["lab"]["record"].get("recipe")) or {}).get(k)
                        for k in ("control", "treatment", "effort", "prediction", "measure"))]
check("run recipes present", not norecipe, norecipe[:3])
check("recipe rendered", 'r.recipe' in appjs and 'The run, row by row' in appjs)

# ready drug prefills: substance + behaviour keyword both parseable by Ask
kn = json.load(open(os.path.join(LAB, "ask", "knowledge.json")))
askjs = open(os.path.join(LAB, "ask", "app.js")).read()
mkeys = set(re.findall(r'"([a-z_]+)":\s*"[a-z_]+"', askjs.split("MEASURE_KEYS")[1].split("};")[0])) \
    if "MEASURE_KEYS" in askjs else set()
badq = []
for c in cards:
    q = (c.get("lab") or {}).get("ask")
    if not q:
        continue
    m = re.match(r"does (.+) change (\w+)\?$", q)
    if not m or m.group(1).replace(" ", "_") not in kn["substances"]:
        badq.append(q)
        continue
    if mkeys and m.group(2) not in mkeys:
        badq.append(q + " (measure keyword?)")
check("ask prefills parse", not badq, badq[:3])

# structures resolve; schematics present for non-drugs
miss_img = [c["key"] for c in cards if c.get("structure")
            and not os.path.exists(os.path.join(HERE, c["structure"]))]
check("structure files resolve", not miss_img, miss_img[:3])
nosch = [c["key"] for c in cards if c["kind"] != "drug" and "<svg" not in (c.get("schematic") or "")]
check("non-drugs have schematics", not nosch, nosch[:3])
noform = [c["key"] for c in cards if c.get("structure") and not c.get("formula")]
check("structured drugs carry formulas", not noform, noform[:3])
caf = next(c for c in cards if c["key"] == "drug:caffeine")
check("caffeine formula pinned", caf.get("formula") == "C8H10N4O2", caf.get("formula"))

# ---------- page shell ----------
html = open(os.path.join(HERE, "index.html")).read()
check("shared nav mount", 'id="navMount"' in html and "Feather.topbar" in html
      and "current: 'experiments'" in html)
for ref in ("style.css", "app.js", "../feather.css", "../feather.js"):
    check(f"ref {ref}", ref in html and os.path.exists(os.path.join(HERE, ref)))
check("fetch data.json", 'fetch("data.json")' in open(os.path.join(HERE, "app.js")).read())
check("counts header", 'id="counts"' in html)

js = open(os.path.join(HERE, "app.js")).read()
ids_html = set(re.findall(r'id="([^"]+)"', html))
used = set(re.findall(r'''(?:getElementById|\$\()\s*["']([^"']+)["']''', js))
check("js ids exist in html", used <= ids_html, sorted(used - ids_html)[:5])
check("no test hooks", "window.__" not in html and "window.__" not in js)
urls = sorted(set(re.findall(r'https?://[^"\' )]+', html + js)))
ext = [u for u in urls if "www.w3.org" not in u]
check("no external urls", not ext, ext[:3])

if shutil.which("node"):
    open("/tmp/expcheck.js", "w").write(js)
    r = subprocess.run(["node", "--check", "/tmp/expcheck.js"], capture_output=True)
    check("app.js syntax", r.returncode == 0, r.stderr.decode()[:200])
else:
    print("SKIP app.js syntax (no node)")

# ---------- ask waiting-room contract (runs survive page navigation) ----------
# The bench remembers the run id it waits for; on boot it renders the finished
# record or follows the live run. These pins keep that contract from rotting.
check("waitroom key set on ack", "pendSet({ id: ack.data.id" in askjs)
check("waitroom cleared on done", "function followDone(rec) {\n  pendClear();" in askjs)
check("waitroom follows pending", "followPending()" in askjs
      and "get_experiment" in askjs and "Finished while you were away" in askjs)
check("waitroom render null-safe", "recGroups(rec)" in askjs
      and "RG || (RUN && RUN.groups) || null" in askjs
      and "No stage data on this record" in askjs)
check("compare resume", "resumeCompare()" in askjs and "ask-compare" in askjs
      and "never re-run" in askjs)
libjs = open(os.path.join(LAB, "library", "app.js")).read()
check("atlas region ask-link", "renderRegion(" in askjs and "&region=" in libjs)
check("hypothesis ask-link", "renderHypothesis(" in askjs and "get('hyp')" in askjs
      and "answerHypFollowup(" in askjs and RECON in askjs
      and "CARD.mode === 'explain' && CARD.hyp" in askjs)
if shutil.which("node"):
    open("/tmp/askcheck.js", "w").write(askjs)
    r = subprocess.run(["node", "--check", "/tmp/askcheck.js"], capture_output=True)
    check("ask/app.js syntax", r.returncode == 0, r.stderr.decode()[:200])

print()
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"FAILURES: {fails}")
sys.exit(1 if fails else 0)
