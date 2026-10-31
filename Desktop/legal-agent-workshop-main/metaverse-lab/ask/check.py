"""Ask page checks: single-question flow, methylxanthine comparison, explainer, follow-ups.

Run:  cd metaverse-lab/ask && python3 check.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f" — {detail}" if detail and not cond else ""))
    if not cond:
        fails.append(name)


APP = (HERE / "app.js").read_text()
HTML = (HERE / "index.html").read_text()
CSS = (HERE / "style.css").read_text()
K = json.loads((HERE / "knowledge.json").read_text())

# 1. JS parses (node is the project's JS oracle, same as duo/flies/library)
for f in ["app.js", "brain3d.js"]:
    r = subprocess.run(["node", "--check", str(HERE / f)], capture_output=True, text=True)
    check(f"{f} syntax", r.returncode == 0, r.stderr.strip()[:200])

# 2. Single-question flow intact (pre-existing pins — do not regress)
for pin in ["function compileCard(question)", "function runCard()",
            "function renderResults(rec)", "function renderExplain()",
            "cmd: 'run_experiment'", "cmd: 'compile_card'"]:
    check(f"single-flow pin: {pin[:40]}", pin in APP)
check("wss-aware lab link intact", "location.protocol === 'https:' ? 'wss' : 'ws'" in APP)

# 3. Methylxanthine data pins
MX = ["caffeine", "theophylline", "theobromine"]
subs = K["substances"]
check("methylxanthine trio present", all(m in subs for m in MX))
check("trio all dosable", all(subs[m].get("dosable") for m in MX))
check("trio all predict firing_all",
      all(subs[m]["expect"]["measure"] == "firing_all" for m in MX))
check("trio all predict higher",
      all(subs[m]["expect"]["direction"] == "higher" for m in MX))

# 4. Comparison mode pins
for pin in ["function findSubstances(q)", "COMPARE_WORDS", "function compileComparison(",
            "function renderComparison(", "function runComparison(",
            "hello.drugs", "methylxanthine"]:
    check(f"compare pin: {pin}", pin in APP or pin in HTML)
check("compare example button", "methylxanthine" in HTML.lower())

# 5. Conversational explainer + follow-up pins
for pin in ["function explainResults(", "function answerFollowup(", "THREAD",
            "id=\"thread\"", "suggest", "What to ask next"]:
    check(f"thread pin: {pin}", pin in APP or pin in HTML)
check("follow-up never invents numbers",
      "on screen" in APP or "from the results" in APP or "results above" in APP)

# 6. Honesty pins (comparison must carry the same guards as single cards)
check("comparison shows confidence", "confidence" in APP)
check("comparison shows unmodelled", "not_modelled" in APP or "Not modelled" in APP)
check("runnable-vs-patch signal", "patch 009" in APP)

# 7. Gene-query pins (anatomy catalog answers, explain-only, never runs)
for pin in ["function findGene(q)", "function compileGene(", "anatomy/genes.json"]:
    check(f"gene pin: {pin}", pin in APP)

# 8. Food-composition pins (FooDB + Duke's, explain-only, never runs)
for pin in ["function findFood(q)", "function compileFood(", "fetch('foods.json')"]:
    check(f"food pin: {pin}", pin in APP)
F = json.loads((HERE / "foods.json").read_text())
check("1948 foods", len(F) == 1948, len(F))
compounds = {e["substance"] for v in F.values() for e in v["items"]}
check("21 lab compounds in foods", len(compounds) == 21, len(compounds))
check("food items are lab substances", compounds <= set(subs))
check("food items have amount+unit+source",
      all(e.get("amount") and e.get("unit") and e.get("source") for v in F.values() for e in v["items"]))
cocoa = F.get("Milk, chocolate beverage, hot cocoa, homemade", {}).get("items", [])
tb = next((e for e in cocoa if e["substance"] == "theobromine"), {})
check("hot cocoa theobromine pinned", tb.get("amount") == 68.0 and tb.get("unit") == "mg/100 g", tb)
check("sleep measure wired", "sleep" in K["measures"] and "['sleep'" in APP)

# 9. Affinity pins (GtoPdb ligand -> target actions on single cards)
for pin in ["fetch('affinity.json')", "Targets (GtoPdb)"]:
    check(f"affinity pin: {pin}", pin in APP)
A = json.loads((HERE / "affinity.json").read_text())
check("24 substances with targets", len(A) == 24, len(A))
check("131 affinity rows", sum(len(v) for v in A.values()) == 131)
caf = [e for e in A.get("caffeine", []) if e["gene"] == "ADORA1"]
check("caffeine ADORA1 antagonist pinned",
      bool(caf) and caf[0]["action"] == "Antagonist" and caf[0]["primary"] is True, caf[:1])
check("affinity rows have target+action",
      all(e.get("target") and e.get("action") for v in A.values() for e in v))

check("cache-busted app", "app.js?v=" in HTML)

# 10. Receptor-map pins (drug -> mammalian target -> fly gene -> expression)
for pin in ["fetch('../anatomy/drug_targets.json')", "Fly legs (Alliance)"]:
    check(f"bridge pin: {pin}", pin in APP)
BR = json.loads((HERE / "../anatomy/drug_targets.json").read_text())["bridge"]
check("24 bridged substances", len(BR) == 24, len(BR))
check("caffeine bridged via AdoR",
      any("AdoR" in e.get("f", []) for e in BR.get("caffeine", [])))

# 11. Headless runtime smoke (boots app.js, runs questions through showCard)
r = subprocess.run(["node", "smoke.js"], capture_output=True, text=True, cwd=HERE)
tail = ((r.stdout or "") + (r.stderr or "")).strip().splitlines()[-4:]
check("ask smoke", r.returncode == 0, " | ".join(tail))

print()
if fails:
    print(f"RESULT: {len(fails)} FAILING")
    sys.exit(1)
print("RESULT: ALL CHECKS PASSED")
