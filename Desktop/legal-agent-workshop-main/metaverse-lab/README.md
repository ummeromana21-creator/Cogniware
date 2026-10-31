# Metaverse Fly Lab

A brand-new 3D lab for the validated connectome flies: **Maxime** (female,
BANC) and **Hagrid** (male, Male CNS) live together in a glass fly jar on the
bench. Positions, headings and firing rates stream live from the fly-lab
server over its WebSocket protocol; with no server it wanders ambiently so
the scene is never frozen. Desktop orbit + VR via WebXR.

No build step. Three.js r160 is vendored (`vendor/`), so the page is
self-contained apart from the live data feed.

## Run it

In one terminal, the fly lab (from the Fly Brain folder):

```bash
python3 server.py
```

In another, serve this folder (any free port; the page finds the lab's
WebSocket on its own):

```bash
cd metaverse-lab
python3 -m http.server 8123
```

Then open http://localhost:8123/lab.html in a browser. Drag to orbit,
scroll to zoom. On a headset, press VR.

## Fly Study (side-view lab + 3D dock)

`study.html` (+ `study.css`, `study.js`) is the screen-first build: nine
lab pages behind a toggle list, with the CONTROL + VARIABLE jars always
visible in a 3D observation dock. Same servers as above, then open
http://localhost:8123/study.html (serve over http — modules don't load
from file://). Click a lab or press 1–9. Requires the lab on
ws://localhost:8765. Lab order: computational, comparative,
connectomics/neuroanatomy, neuroinformatics, electrophysiology,
neuropharmacology, pharmacokinetics, circuit & systems, behaviour.

The computational lab does one experiment at a time: a "Before you start"
intro, an always-visible status bar (live/offline, step, focus fly,
nose→brain→legs ticker, Heal, Download report), an experiment list with a
status chip per item, one experiment screen at a time (Question, Setup,
Predict, Run, Result, So what, Footer), and a Results board generated
from the records. Each screen replays its recording (date + seeds shown)
or runs the card live against the lab; predictions lock before running.
A talking-Muse tour (browser voice; drop studio mp3s in `audio/` as
`comp-chN.mp3` and flip `NARR_AUDIO` in study.js) walks the list in
order, running short cards live and replaying long ones with a
"recorded earlier" line. Every number is mean ± wobble over ≥3 runs with
runs visible; verdicts come from the lab's own rule.

Figures are generated offline (same engine, headless). Copy `compute/`
into Fly Brain, then:

```bash
python3 -m compute.figures all --out metaverse-lab/fig
python3 check_figures.py --out metaverse-lab/fig   # referee: existence + label blocks
```

A genuine figure set ships in `fig/` (check each label line for its
commit/seed/params); regenerate after any sim change with the command
printed under each figure. The brain-swap section reads the shipped
`cross_results.json` (genuine 3×400); regenerate with `run_cross.py` (see
`../fly-swap/run_cross.py`). Trials/experiments can record spikes +
group windows once patch `003-recorder` is applied (`rec_window` arg).
Shipped `rec/rec-*.json` recordings carry per-repeat seeds; live repeats
need patch `004-seed-runs` on the server (without it the footer says so).

## Experiments

The left jar is the CONTROL (never dosed); the right jar is the VARIABLE.
The Experiment panel acts on the variable jar only: pick a drug and amount,
Dose, then Run trial. Results compare every fly control-vs-variable
(activity, firing, bananas). Washout clears drugs; Restore resets the
variable flies.

## Files

- `lab.html` — the whole lab: room, bench, jar, flies, live link, HUD, VR.
- `vendor/three/` — three.js r160 + OrbitControls + VRButton (pinned local).
