# The Virtual Fly Lab — A Lab Book

*Written for the curious: middle-school students, PhD candidates, and professors alike. If you can follow a story, you can follow this book.*

---

## Chapter 1. What This Lab Is

This is a neuroscience laboratory that lives entirely inside a computer. There are no living animals here. Our subjects are two simulated fruit flies — virtual insects whose brains are wired from real connectome data, and whose bodies walk around small virtual boxes hunting virtual bananas.

The lab exists to answer one kind of question: **what happens inside a fly's brain when something changes?** That something can be a chemical, a silenced brain region, a swapped wiring diagram, or a different setting on the neurons themselves. Every answer comes from a real run of the simulation — never from a prediction alone — and every answer is checked against published research wherever such research exists.

Three rules govern everything we do:

1. **Flies are always observable.** No matter which room you visit, the flies are on screen, alive in their boxes, doing whatever the current experiment asks of them.
2. **Honesty before beauty.** Every number comes from at least three repeated runs and is shown as a mean with its wobble. Predictions are locked before results arrive. When the simulation cannot do something, we say so on the page — including experiments we expect to fail.
3. **One idea per sentence.** The lab speaks conversationally. Every technical word gets an everyday picture the first time it appears, and every display says whether it is LIVE (driven by running data) or an ILLUSTRATION (a drawing to help you think).

---

## Chapter 2. Meet the Flies

### Maxime — the female

Maxime is built from the BANC connectome (Brain And Nerve Cord), a FlyWire Codex export of a female fly. She contains **157,789 neurons, 3,036,600 connections, and 23,553,107 synapses**. Her smell sensors number 212 on the left and 184 on the right (banana-sensing smell neurons), and her six leg motor pools hold 69, 63, 63, 70, 63, and 63 motor neurons.

### Hagrid — the male

Hagrid is built from the Male CNS connectome (Janelia FlyEM), a Codex export of a male fly. He contains **166,700 neurons, 6,242,118 connections, and 89,860,280 synapses**. His smell sensors number 148 on the left and 176 on the right, with leg motor pools of 68, 58, 66, 67, 58, and 64.

### FlyWire Maxime — the reference

A third brain, the original FlyWire Maxime (139,255 mapped cell bodies), serves as a validated reference. It is the brain behind the Fly Atlas's glowing region images.

### How we know the wiring is right

Two independent audits check the flies. The **provenance audit** verifies neuron counts, transmitters, cell types, and synapse totals against the Codex exports — all pass on all three brains. The **wiring audit** checks that specific circuits land where biology says they should: the leg motor pools land exactly (391 of 391 on one side, 381 of 381 on the other), and the banana-smell path from nose neurons (ORN_V) to the smell-processing ball (the V glomerulus) is confirmed with data from both sides of the brain.

Think of it like checking a wiring diagram against the actual machine: we counted every wire, and then we traced the important ones by hand.

---

## Chapter 3. The Engine Room

The simulation is a small city of programs. Here is what each district does, in plain language.

### The lab server (`server.py`)

The beating heart. It advances every fly's brain about 28 steps per second, moves the flies around their boxes, and talks to the outside world over a WebSocket (port 8765) and a file server (port 8000 for the classic lab page). A second file server (port 8123) serves the study pages. The lab holds **four flies**: a female and a male in a control box, and a female and a male in a treatment box. Control flies are never touched; treatment flies receive whatever the experiment prescribes.

### The protocol (`protocol.md`)

The shared language between the pages and the lab. Its verbs read like lab instructions: `restore` (heal a box), `dose` and `washout` (give and remove a chemical), `run_trial` and `run_experiment` (run things), `propose_experiment` (suggest one), `compile_card` (turn a question into a plan), `focus` (choose which fly to watch), `get_experiment` and `list_notebook` (read the records), and `log` (write notes). Every run is saved on disk as a record with its own id and seeds.

### The experiment engine (`experiments.py`, `groups.py`, `substances.py`, `stage_map.py`)

- `groups.py` defines the brain's neighborhoods — nose, antennal lobe, lateral horn, mushroom body, central complex, optic lobes, descending neurons, nerve cord, legs, and the modulator groups (dopamine, octopamine, serotonin, GABA, acetylcholine, glutamate).
- `stage_map.py` defines the signal's journey: the propagation waterfall from smell to legs.
- `substances.py` defines the chemical shelf: caffeine, ethanol, DEET, tetanus toxin, isoflurane, and friends — each as a mechanism handle (a knob that turns a specific part of the simulation up or down), with doses as unitless knob positions, never real-world amounts.
- `experiments.py` defines the experiment cards and the verdict rules: SUPPORTED, NOT SUPPORTED, MIXED, or CANNOT TELL.

### The recorder (`recorder.py`, patch 003)

The lab's notebook-keeper. During a run it saves spikes per step for the sampled neurons (bit-packed to stay small) and firing per group per time window. This is what the replay player reads back: every glow you see in a replay is decoded from a record, never painted on.

### The narrator (`narrator.py`)

A Muse Spark-powered voice that explains what is happening, plus a local fallback that works with no network. Token budgets keep it fast and cheap (patch 006), and a guard keeps it honest (patch 007): it may only speak from numbers that just arrived, never from a pre-written script.

### The card compiler (`server_addons/card_compiler.py`, patch 005 + 008)

When you ask a question, this turns it into an experiment card: hypothesis, procedure, predicted result. It works in two stages — first it routes the question (is this about a drug? anatomy? behavior?), then it writes the card — and a validator checks every card against the lab's real substance list, measurable quantities, and filed evidence. If the AI is unreachable, a clearly badged local fallback compiles the card instead.

### The agent scaffold (`lab_agent/`, `agent_kit/`, `verification/`)

Built in the shape of Meta's legal-agent workshop: an experimenter agent that works the lab over the WebSocket protocol, one file per tool, with a verification layer and runs saved on disk. The model chooses every tool call; nothing experiment-specific is hard-coded.

---

## Chapter 4. The Nine Laboratories

The study building has nine rooms, each its own page, each with the flies visible. They are ordered by how much of the lab they need: the first two are the heart of the project.

1. **Computational neuroscience** — the flagship teaching lab. A narrated, one-experiment-at-a-time story: one neuron, one step, baseline brain, surgeon, drugs, signal path, regime map, wiring-or-settings cross, honest learnings, downloadable report. Every experiment screen has the same seven parts (Question, Setup, Predict, Run, Result, So what, Footer), every number is mean ± wobble over individually seeded repeats, and the tour guide never talks over a running procedure.
2. **Comparative neuroscience** — male versus female, side by side. This is where the six-connection question lives (see Chapter 7), and where the crossed specimens — Cross-A (her wiring, his settings) and Cross-B (his wiring, her settings) — let us ask whether a difference comes from wiring or from tuning.
3. **Connectomics / neuroanatomy** — the wiring diagrams themselves: counts, cell types, transmitters, and the paths that the audits verified.
4. **Neuroinformatics** — the data shelves: what we hold, where it came from, and how to query it.
5. **Electrophysiology** — spikes and voltages: single-neuron traces, rasters sorted by stage, population rate traces with dose and washout bands.
6. **Neuropharmacology** — dosing experiments with the chemical shelf, always control versus treatment, both sexes.
7. **Pharmacokinetics** — drug level over time: what goes in, what washes out, and what the model still assumes (instant, complete washout — see Chapter 7).
8. **Circuit & systems** — the signal's journey through stages, the propagation waterfall, and the P7 finding: in all three flies, the left/right smell signal is lost at the first synapse, so no fly steers by smell (yet).
9. **Behaviour & ethology** — walking, banana-finding, and the honest failures: avoidance walking is pre-registered as a test we expect to FAIL until wind navigation exists.

### The four screens you will actually visit

Most visitors enter through four pages, all in one design family (Chapter 6):

- **Meet the flies** — the hub. Portraits, counts, and doors to everything else.
- **Study** — the nine laboratories, starting with the computational story.
- **Fly Atlas** — 66 brain regions as glowing soma maps (one dot per cell body, at its real position), each with a 3D viewer, key numbers, and a link to ask about that region.
- **Ask the flies** — a chat box. Ask anything testable ("how does caffeine affect Maxime and Hagrid?"), read the compiled card, run the experiment live on control and treatment boxes, and get a verdict against the prediction — then replay the run as a glowing 3D brain with raster and bars in the **Watch** player.

---

## Chapter 5. The Honesty Machinery

This chapter is the lab's conscience. Other virtual labs show you a movie; this one shows you its working.

- **Repeats, not single runs.** Every repeat gets its own seed, listed in the footer. No number on any page comes from a single run. (We once found a ±0.0001 wobble that meant the repeats had secretly shared a seed — the footer now makes that impossible to hide.)
- **Predict before you click.** On every experiment, you pick ↑, ↓, or no change before running. Your prediction sits beside the outcome on the result card, and both are logged.
- **Verdicts from rules, not vibes.** `experiments.py` computes SUPPORTED / NOT SUPPORTED / MIXED / CANNOT TELL from the numbers. The "matches the cited claim" line compares against the sim mechanism, and says plainly when no paper is attached.
- **Swapped numbers, honestly.** In the brain-swap experiments, the numbers come from actually swapped wiring matrices run through the same steps — never from typing in "what we expect."
- **Pre-registered failures.** The CO₂ avoidance test and the compass check sit on a "Not done properly yet" list with the reason written beside each. A failed test we predicted is a result, not an embarrassment.
- **Label blocks on every figure.** Git commit, wiring dataset and version, seed, parameters, "steps" on the time axis, unitless dose, and "simulated" in the title. Figures are generated offline by scripts beside the page, and the regenerate command is printed underneath.
- **Recorded earlier, said aloud.** Long experiments (dose sweeps, regime maps) replay saved results, and the narrator says "recorded earlier on ⟨date⟩, seeds ⟨list⟩" out loud. If the lab is offline, the whole tour runs on saved records and says so in its first sentence.

### The P7 finding, in one paragraph

In all three brains, the signal from the left and right smell sensors merges at the very first synapse. That means no fly in this lab can steer by smell — the information needed to turn toward an odor is gone before the brain could use it. This single fact shapes the whole research program: CO₂ activation maps are real today, high-dose sedation is modeled through existing anaesthetic handles (labeled as assumption, with confidence and source), and smell-guided walking waits on a future wind-navigation system.

---

## Chapter 6. The Look of the Lab

Every page wears the same uniform: the **Feather Atlas** design system. Cream paper backgrounds, sage-green accents, deep teal for live data, Newsreader serif for headings, Nunito Sans for everything else — and a dusk mode that follows you from page to page, remembered across visits.

Under the uniform sits a shared skeleton, two small files every page includes:

- `feather.css` — the tokens (day and dusk palettes), the top bar, buttons, badges, and verdict pills.
- `feather.js` — the builder that assembles the hub bar (brand, four doors, sun button), remembers your light choice, and adopts each page's own widgets into the bar.

Consistency here means the chrome matches, not that every room is shaped the same. Ask keeps its ask-and-watch hero; Study keeps its reading column beside the live observation dock; the Atlas keeps its card grid and 3D detail; Watch keeps its player. Same family, different jobs.

---

## Chapter 7. The Data Shelves

This is the heart of the book for the data-minded: everything the lab stands on, shelf by shelf.

### Shelf 1 — The brains (wired in, running today)

- **FlyWire Codex exports** — the source of all three brains: BANC (female), Male CNS, and the FlyWire reference. Neuron positions, cell types, connection points, transmitters. *Held in the lab's fly files; the audits in Chapter 2 check against these.*
- **FlyWire region renders** — the glowing soma maps: 139,255 cell bodies as dots at real positions, colored by kind, with region connection clouds on top. *Held in the atlas image folder; the "region_images" recipe documents every step.*

### Shelf 2 — Chemistry and pharmacology (held, being wired)

- **PubChem** — compound identities and properties, queried live over its REST API. *Already wired into the reference system.*
- **ChEBI** — the "roles" classification: what kind of thing a compound is, biologically. *Queried live.*
- **ChEMBL v37** — targets and affinities with species: which protein, how strongly, in what organism. Full database dump held (2.1 GB MySQL plus structures, fingerprints, and sequences), with the join map extracted and held: activities → assays (species) + compounds (structures) + targets (species). *Held in Downloads; join reference held with the lab notes.*
- **IUPHAR / Guide to PHARMACOLOGY** — the action vocabulary: agonist, antagonist, enhancer. Full CSV set held: ligands, targets and families, all interaction splits, approved drugs, endogenous ligands. *Held in Downloads.*
- **CTD (Comparative Toxicogenomics Database)** — chemical–gene interactions mined from the literature. Full set held: chemical–gene, chemicals, genes, diseases, pathways, anatomy, exposures. *Held in Downloads.*
- **STITCH** — chemical–protein interaction scores per species. *Wanted: the Drosophila (taxon 7227) files; not yet downloaded.*
- **FooDB** — foods to compounds with amounts. *Held in Downloads (2020 JSON).*
- **Dr. Duke's Phytochemical Database (USDA)** — plants to constituents. *Held in Downloads (source CSVs).*

### Shelf 3 — Genes, orthologs, and disease (held, being wired)

- **FlyBase genome r6.69** — the full fly genome release: sequences, annotations, Chado XML. *Held in Downloads. Still wanted: the precomputed tables (interactions, synonyms, GO annotations) — the bulk page is bot-walled against machines, so these need a human browser.*
- **Fly anatomy ontology (FBbt)** — 30,035 anatomical terms and 155,425 relationships in obographs JSON. *Held with the lab notes (78 MB).*
- **Alliance of Genome Resources** — orthology, disease, phenotype, expression, and interaction files across species, including the combined orthology map our DIOPT substitute. *Held in Downloads.*
- **DIOPT** — the fly ortholog finder. Query-only, no bulk files; the Alliance orthology table covers its role. *Used through the web.*
- **FlyBase chemical reports** — fly genes and phenotypes tagged with chemicals. *Reachable per-chemical; bulk needs the human browser.*

### Shelf 4 — The future shelves (scouted, not yet pulled)

- **FlyCellAtlas / single-cell expression atlases** — which receptor genes each cell type expresses. This is the key to real, checkable receptor maps (Chapter 8).
- **FlyWire Codex transmitter predictions** — per-neuron transmitter calls to sharpen the sim's chemistry.

### A note on provenance

Every shelf item keeps its source URL, version, and access date with the lab notes. Anything quoted from a paper carries the exact claim and is marked "verified by Romana: no" until a human checks it. The six-connection male/female difference and the CO₂/ethanol details currently carry that mark.

---

## Chapter 8. Open Questions and Honest Limits

A textbook that hides what it doesn't know is a storybook. Here is what we don't know.

1. **The six synapses.** There is believed to be a difference of about six synaptic connections between the male and female fly in a key circuit. Looking it up properly — which circuit, which paper, which six — is outstanding.
2. **Receptor maps.** Today, a drug in the sim turns a mechanism handle: caffeine raises excitability everywhere. Real drugs bind specific receptors on specific cells. Real maps need single-cell expression data and are a genuine project, not a tweak.
3. **Bath and puff.** Real pharmacology has two delivery styles: bathing tissue in a drug, or puffing it at one spot. The sim has one global knob. A bath-then-puff field simulation — drug diffusing through space, cells responding by their receptors — is designed but parked.
4. **Pharmacokinetics.** Real flies metabolize and clear drugs over hours; ours wash out instantly and completely. The held CTD and ChEMBL data can ground the first real clearance curves.
5. **Wind navigation.** Until the sim gives flies a way to steer by smell (P7) and wind, avoidance walking stays on the expected-to-fail list.
6. **Sleep and dopamine.** Real caffeine hits sleep and dopamine circuits the sim lacks. The lab has no sleep measure at all — the closest cousin is distance walked.
7. **SAM, dropped.** We once considered having vision AI read brain pictures to find what lit up. The lab computes that from firing per group instead — simpler, exact, and honest. Dropped for this build.

---

## Chapter 9. Where This Can Go

Given the shelves above, here is the research program in order — each step feeds the next.

**First, ground the chemistry.** Wire ChEMBL affinities and IUPHAR actions into the card compiler's validator, so "caffeine does X" is checked against measured targets, not just the sim's handles. Add CTD chemical–gene links as the literature leg. This turns every drug card from plausible to evidence-backed.

**Second, build the receptor maps.** Join FlyCellAtlas expression onto the sim's neurons: which cells carry the caffeine receptor, the GABA receptor, the smell receptors. Then re-run the classic questions — why does DEET keep flies away? what happens if we block the smell receptors? — as anatomy experiments, with the lit regions predicted from real receptor distributions.

**Third, simulate the biology.** With receptors placed, build the bath-then-puff field: drug molecules diffusing through the neuropil, binding where receptors live, unbinding over time. Watch it live in the Watch player — the experiment happening in front of you, numbers emerging from mechanism rather than knobs.

**Fourth, close the loop to humans.** FlyBase orthologs plus Alliance disease files let a result travel: this fly circuit → these human orthologs → these disease associations. Never medical advice — the lab studies flies — but a genuine comparative-neuroscience story, which is exactly what Room 2 exists for.

**Fifth, the CO₂ flagship.** One chemical, done completely: the V-glomerulus activation map (real today), the aversion-to-anesthesia arc across behavior notes and dose-response (needs the sedation handles plus clearance curves), and the avoidance-walking test graduating from expected-to-fail once wind navigation lands. CO₂ becomes the template — curation format, behavior-note format, activation-report format, validation bar — and then the template scales to dozens of chemicals.

**Finally, the predictive model.** With chemistry grounded, receptors mapped, and dozens of templated chemicals run, the lab can do what it was always for: take a question nobody has run yet, compile the card, simulate the experiment on Maxime and Hagrid, and report back with a verdict, a replay, and the papers it agrees with — or an honest account of why it doesn't.

---

---

## Chapter 10. Second Edition Addendum (31 October 2026)

What the first edition listed as open, and what is true now:

**Wind navigation landed (Q5).** Flies steer by wind direction and speed through a dedicated channel; the avoidance-walking test still needs its live re-run to graduate, but the missing piece — wind in the world — is in.

**Sleep exists (Q6).** A `sleep` measure scores brain-quiet (<1% firing) per trial; sedatives raise it, caffeine lowers it. It is an activity proxy, not EEG — the book's honesty rule stands.

**Clearance curves are real (Q4).** Every drug now decays by its half-life across steps; melatonin, caffeine, and the rest wash out on curves, not instantly. The CTD/ChEMBL grounding of the exact half-lives is still future work.

**Chemistry is half-grounded (program step 1).** GtoPdb affinities and actions (131 target rows across 24 substances) now print on every Ask card, and 1,948 food-composition entries answer "what's in X". ChEMBL itself was parked (5.7 GB stand-up); GtoPdb covers the curated affinities.

**Receptor maps, version 0 (Q2, program step 2).** Alliance expression data (394,194 annotations, 9,660 genes) now drives per-card "fly legs": human drug target → fly ortholog → where it is expressed → which sim region it reaches. Single-cell FlyCellAtlas placement is still the genuine project; this is anatomy, not cells.

**Three mutants, one clock.** `para_ts`, `DAT_null`, and `per01` ship as parameter presets with explicit proxy statements; an opt-in circadian gain (default off) modulates arousal by zeitgeber time.

**The Atlas tripled.** 236 cell-type cards, 94 left/right side cards, 65 male-subset cards, 42 sex-difference entries, and a 200-pair pathway overlay joined the original 66 regions — all counts pinned against the connectome exports.

Still genuinely open: the six synapses (Q1), bath-and-puff fields (Q3), single-cell receptor placement, and the predictive-model finale. The CO₂ flagship (step 5) is one live run from graduating.

*End of the lab book — second edition. The flies have new senses, the shelves have readers, and the next chapter is still written one experiment at a time.*

