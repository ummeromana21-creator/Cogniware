# Fly Brain patches + agent install (week plan: Must + Should)

All files were built and verified outside the lab folder. Nothing here has
touched `Fly Brain/` yet — these commands do that.

## 1. Apply the patches (order-independent, 001 first is fine)

```bash
cd "/Users/robin/Desktop/Fly Brain"
patch -p1 < /Users/robin/Desktop/legal-agent-workshop-main/fly-patches/001-source-field.patch
patch -p1 < /Users/robin/Desktop/legal-agent-workshop-main/fly-patches/002-delta-hologram.patch
patch -p1 < /Users/robin/Desktop/legal-agent-workshop-main/fly-patches/003-recorder.patch
```

- `001`: optional `source` {citation, claim, direction} on experiment cards;
  "Matches the cited claim" line in analysis, notebook markdown, and the card
  view; form fields; agent prompt. No server change (cards pass through).
- `002`: server ships group firing for all four flies; signal screen gets a
  delta mode (treatment−control per stage, per sex) on the `D` key; protocol
  doc line. Fake/old servers show a guard message instead.
- `003`: recorder — trial results and experiment runs carry `rec` (bit-packed
  spikes for the hologram sample + firing per group per window; focus fly
  only). New `recorder.py`; `rec_window` arg on run_trial / card field.

## 2. Install the agent (Must)

```bash
cp -R /Users/robin/Desktop/legal-agent-workshop-main/fly-experimenter "/Users/robin/Desktop/Fly Brain/experimenter"
cd "/Users/robin/Desktop/Fly Brain/experimenter"
cp .env.example .env   # then put MODEL_API_KEY in .env (never commit it)
./helper setup
./helper test          # offline suite, needs neither server nor key
```

## 3. Verify (5 minutes)

```bash
cd "/Users/robin/Desktop/Fly Brain"
python3 check_experiments.py     # cards/analysis still green after 001
```

- Open the lab, press `D` twice: live signal view, then delta view. Dose the
  treatment box and watch delta bars move.
- Write a card with a citation, run it, open the notebook: the matches-claim
  line should sit under the verdict.
- With the lab running and `.env` filled:
  `./helper run "does caffeine 0.5 make the flies walk more?"` (from
  `experimenter/`), then `./helper review <RUN_ID>`.

## 004 — per-repeat seeds (`004-seed-runs.patch`)
Gives every experiment run its own recorded seed: `seed_base` on the card
(random unless pinned), brain + world RNGs reseeded at each run's setup, and
`seeds` (per run: condition, repeat, per-fly seeds) saved into the record.
Apply from the Fly Brain folder, then restart the server:
`patch -p1 < /Users/robin/Desktop/legal-agent-workshop-main/fly-patches/004-seed-runs.patch`
