"""Ask bench checks: single-question flow, methylxanthine comparison, explainer, follow-ups.

The bench lives in study.html now (ask/ keeps app.js, data and watch/).
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
HTML = (HERE / "../study.html").read_text()
STUDY = (HERE / "../study.js").read_text()
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
check("wss-aware lab link intact", "location.protocol === 'https:' ? 'wss' : 'ws'" in STUDY)
for pin in ["controlGloss", "treatGloss", "effortGloss", "doseGloss",
            "Every result is measured against these two",
            "a unitless knob on the sim, not milligrams",
            "rule 2 needs at least 5 per side"]:
    check(f"trait gloss pin: {pin[:40]}", pin in APP)

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
check("compare example button", "methylxanthine" in APP.lower())

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
for pin in ["function findFood(q)", "function compileFood(", "fetch('ask/foods.json')"]:
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
for pin in ["fetch('ask/affinity.json')", "Targets (GtoPdb)"]:
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
for pin in ["fetch('anatomy/drug_targets.json')", "Fly legs (Alliance)"]:
    check(f"bridge pin: {pin}", pin in APP)
BR = json.loads((HERE / "../anatomy/drug_targets.json").read_text())["bridge"]
check("24 bridged substances", len(BR) == 24, len(BR))
check("caffeine bridged via AdoR",
      any("AdoR" in e.get("f", []) for e in BR.get("caffeine", [])))

# 11. Headless runtime smoke (boots app.js, runs questions through showCard)
r = subprocess.run(["node", "smoke.js"], capture_output=True, text=True, cwd=HERE)
tail = ((r.stdout or "") + (r.stderr or "")).strip().splitlines()[-4:]
check("ask smoke", r.returncode == 0, " | ".join(tail))

# 12. Study-merge pins (bench mounts in study.html, one socket for the page)
check("no duplicate ask page", not (HERE / "index.html").exists())
for pin in ["globalThis.AskBench", "function mountBench(", "function initOnce(",
            "function askBenchHTML(", 'id="askBench"']:
    check(f"bench pin: {pin}", pin in APP)
check("single socket (bench owns none)", "new WebSocket" not in APP)
check("study publishes its Live", "globalThis.StudyLive" in STUDY)
check("ask is a Study bench", "id: 'ask'" in STUDY and "AskBench.mount" in STUDY)
check("ask bench lands first", "Labs.open('ask')" in STUDY)
check("study loads bench script+style",
      "ask/app.js?v=" in HTML and "ask/style.css" in HTML)
for rid in ["brainWrap", "tab0", "tab1", "stage", "caption", "benchTabs"]:
    check(f"rail pin: {rid}", f'id="{rid}"' in HTML)

# 13. Cross-page return pins (run context survives navigation via sessionStorage)
check("run context stored at ack", "card: CARD" in APP and "pendSet({ id:" in APP)
check("rejoin restores context", "restoreRunContext(s.experiment.id)" in APP)
check("restore needs id match", "p.id !== liveId" in APP)
check("failed runs clear key", "don't haunt the next visit" in APP)
check("finished renders card too", "renderCard(($('q')" in APP)
_dop = K["substances"]["dopamine"]
check("dopamine postsynaptic target", _dop["target"] == "firing.dopamine-receptor neurons"
      and _dop["target_prediction"] == "higher" and _dop["expect"]["measure"] == _dop["target"]
      and _dop["expect"]["direction"] == "higher")
check("dopamine receptor measure on file", "firing.dopamine-receptor neurons" in K["measures"])
check("acts_where on file", all("acts_where" in K["substances"][n] and "receptors" in K["substances"][n]
      for n in ("dopamine", "caffeine", "theophylline", "theobromine")))
check("where-it-acts card line", "Where it acts:" in APP and "acts_where: s.acts_where" in APP)
check("offline procedure notes", "background excitatory drive on dopamine-receptor neurons ${X2" in APP
      and "scales with glial tone (half rested, full tired)" in APP)
check("dose model exact", "hill(d, 0.4, 2)" in APP and "0.25 * hill(d, 0.1, 1.5)" in APP
      and "0.5 * hill(d, 3.1, 4)" in APP and "excitatory transmission ${X2" in APP
      and "PDE_JOIN = 1.79" in APP and "dose_model !== 'chembl-v1'" in APP
      and "weight_model !== 'norm-v1'" in APP)
check("vision counts recalibrated", "2,244 banc, 2,821 malecns" in K["stimuli"]["light"]["assays"]["targets"]["facts"])
check("chembl evidence", any("8.5" in e for e in K["substances"]["theophylline"]["evidence"])
      and any("32.5" in e for e in K["substances"]["caffeine"]["evidence"])
      and any("105" in e for e in K["substances"]["theobromine"]["evidence"]))
check("theophylline two-arm sync", "phosphodiesterase at higher doses" in K["substances"]["theophylline"]["mechanism"]
      and "PDE EC50" in K["substances"]["theophylline"]["mechanism_confidence"])
check("dose picker", 'data-dose="${d}"' in APP and "[0.25, 0.5, 1.5]" in APP
      and "DOSES.includes(d)" in APP and "CARD.dose = d" in APP)
check("dose threaded", "amount: DOSE_DEFAULT" in APP and "dose ${c.substance} ${dose}" in APP
      and "recDose(rec)" in APP)
check("engagement annotation", "second action joins" in APP and "hill(dose, 3.1, 4)" in APP
      and "hill(dose, 0.1, 1.5)" in APP)
check("dose curve", "list_notebook" in APP and "Dose–response" in APP and 'id="curve"' in APP
      and "runSweep" in APP and ".doseseg" in CSS)
check("pooling matches member entries", "poolCurve(recs, measure, sub)" in APP
      and "one entry per member" in APP)
check("vision measures on file", "firing.photoreceptors" in K["measures"]
      and "firing.visual targets" in K["measures"])
check("vision stimulus entry", "light" in K["stimuli"] and "eye" in K["stimuli"]["light"]["assays"]
      and "targets" in K["stimuli"]["light"]["assays"])
check("light card template", "compileStimulus" in APP and "findStimulus" in APP
      and "stimulus ${a.channel} level ${a.level}" in APP)
check("stimulus offline lines", "set ${a.key} ${a.value}" in APP and "awake floor for target assays" in APP
      and K["stimuli"]["light"]["assays"]["eye"]["setup"][0] == {"cmd": "set_param", "key": "I_vis", "value": 2.0})
check("dose UI gated to dosable", "c.dosable === false" in APP and "treatLabel(rec" in APP)
check("mutants on file", all(n in K["mutants"] for n in ("ninaE_ko", "Orco_ko", "para_ko", "Gr66a_ko"))
      and K["mutants"]["para_ko"]["measure"] == "firing_all"
      and K["mutants"]["Gr66a_ko"]["measure"] == "firing.taste neurons"
      and "firing.taste neurons" in K["measures"])
check("deet contact route", "bitter-taste neurons on contact" in K["substances"]["deet"]["mechanism"]
      and "assumption S3" in K["substances"]["deet"]["not_modelled"]
      and "contact route, S3" in APP)
check("deet taste secondary", K["substances"]["deet"]["behavior"] == ["banana_dist", "firing.taste neurons"]
      and "sub.name === 'deet'" in APP and "key: 'I_taste', value: 2.0" in APP)
check("mutant card template", "compileMutant" in APP and "findMutant" in APP
      and "raise her as a ${a.name} mutant" in APP)
check("knowledge fetch versioned", "knowledge.json?v=" in APP)
check("region subset file", "library/subsets.json" in APP)
check("region sense file", "library/sensegroups.json" in APP)
check("dose row icon", '#i-drop' in APP and 'id="i-drop"' in HTML)
check("dose buttons horizontal", ".doseseg { display:flex" in CSS and 'aria-pressed="true"' in CSS)
check("local-first substances", "trio.length === 1 ? compileCard" in APP)
check("ai wait capped", "askp({ cmd: 'compile_card', question }, 30000)" in APP)
check("showcard guarded", "Could not write this card" in APP)
SJ = (HERE / ".." / "study.js").read_text()
check("send race safe", "dropped between check and send" in SJ)
check("waiters flushed on close", "never strand a waiter on a dead socket" in SJ)
check("region name routing", "/^what does (.+?) do" in APP and "triedRegion" in APP)
check("region fallback guarded", "showCard(question, true)" in APP)
check("food stops atlas words", "'male', 'female', 'left', 'right', 'subset'" in APP)

print()
if fails:
    print(f"RESULT: {len(fails)} FAILING")
    sys.exit(1)
print("RESULT: ALL CHECKS PASSED")
