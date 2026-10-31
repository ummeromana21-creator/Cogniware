# Claims audit + citation audit (2026-10-31, pine-volans)

Scope: every number / factual claim in `Fly Brain/README.md`, `validation.md`
(scope section + results), `comparative/FINDINGS.md`, `lab.html` display
strings, notebook records referenced, and the portal pages owned by this
session (`metaverse-lab/flies/`, `metaverse-lab/library/`).

Legend: ✓ traced to a pointer · ⚠ needs a pointer/qualifier · ✗ no pointer found.

## Claims

| # | Claim | Pointer | Status |
|---|-------|---------|--------|
| 1 | Maxime 157,789 neurons (README) | raw rows minus 473 glia/trachea; `check_provenance.py` PASS | ✓ |
| 2 | Hagrid 166,700 neurons (README) | raw rows; `check_published_counts.py` MATCH | ✓ |
| 3 | 391 / 381 leg motor neurons | counted from built flies (`motor_L`+`motor_R`) | ✓ |
| 4 | FlyWire v783 brain-only Maxime | `flywire_brain.py` + `flywire/maxime_v783.npz` | ✓ |
| 5 | Provenance paragraph (README) | `check_provenance.py` PASS banc+malecns; flywire leg has a raw-path bug (open) | ✓/⚠ |
| 6 | ORN 1.3× own-side assumption | `ORN_IPSI_BIAS` in `cns_fly.py:33`, `flywire_brain.py:37` | ✓ (see citation note G1) |
| 7 | Confidence labels + sources (substances) | `check_drugs.py` TEST 8 `library_handles_declared` | ✓ |
| 8 | Built caches at `data/banc/fly.npz` etc. | files exist | ✓ |
| 9 | check_fly steering FAILS (README:54) | reruns banc/malecns/flywire confirm; matches P7 | ✓ |
| 10 | check_drugs "11 tests each" | 11 `verdict()` calls | ✓ |
| 11 | Verdict-rule plain sentence (README:92-96) | `analyse_one` + `verification/check_verdicts.py` 15/15 | ✓ |
| 12 | TTX / tetanus / isoflurane / caffeine results | validation P1–P3, P5; `check_drugs` TEST 4,5,7; notebook records | ✓ |
| 13 | "picrotoxin raises firing ~4× on the FlyWire brain" (README) | `check_drugs.py` TEST 6, `BRAIN=flywire`: 9.77% vs 2.34% (=4.17×), synchrony 0. (First audit pass missed this pointer — corrected.) No conflict with P4: P4's +22% is a different setup; both show raised firing without bursting | ✓ FIXED (pointer added) |
| 14 | P7 negative (smell lateralisation lost) | `check_fly` FAIL + validation P7 + `stage_map.py` exists | ✓ |
| 15 | Sex-correction numbers (~60%, medians 206/50, 540/167/787, 300 budget, 34%/10%) | validation.md:257-263; FINDINGS:20,23 | ✓ |
| 16 | "Caffeine 0.5 +38% F / +42% M, SUPPORTED" (README) | record `20261030_164206_caffeine-vs-control-firing-all`: Maxime +37.9%, Hagrid +41.8%, SUPPORTED in each (recomputed from the JSON) | ✓ FIXED (was unsourced 0.8/+61/+70; now recites the record) |
| 17 | FINDINGS numbers (883/1343/409, 0.91/0.75/0.71, 26%, 3–4%) | FINDINGS.md:16,18,21; scripts named :26 (not rerun) | ✓ |
| 18 | "DEET cuts receptor-neuron firing by 56–80% at full dose" (README) | `check_drugs.py` TEST 9: FlyWire 8.28→1.62% (80%), BANC 12.86→5.14% (60%), Male CNS 14.63→6.46% (56%) | ✓ FIXED (was unsourced ~75%; now measured per brain) |
| 19 | Amphetamine acts only via dopamine neurons | `check_drugs` TEST 10 | ✓ |
| 20 | Data credits; raw CSVs not redistributed | `git ls-files` shows no CSV | ✓ |
| 21 | FlyWire 139,255 (lab.html) | `check_published_counts.py` MATCH | ✓ |
| 22 | FlyWire 50.7M synapses (validation) | thresholded export sums to 50,666,648. Qualifier added: "(≥5-synapse Princeton export; 54.5 M in the paper)" | ✓ FIXED |
| 23 | Male 6.2M / BANC 3.0M connections (validation:13-16) | built W nnz 6,242,118 / 3,036,600 | ✓ |
| 24 | Ethanol monotonic, GABA-only (README:132) | validation P6; ethanol notebook record present (numbers not rechecked) | ✓ partial |
| 25 | Portal (mine): 157,789 / 166,700 / 23.6M / 89.9M / 3.0M / 66 / 8 / 58 | raw sums; built nnz; `data.json` level counts | ✓ fixed this session |
| 26 | Portal (mine): body ≈2.5mm F / 2.0mm M | females ~2.5mm per species references (e.g. Drosophila melanogaster species account); males "slightly smaller" — 2.0mm consistent (secondary source) | ✓ audited |
| 27 | "Every claim backed by a referee" (README:29) | #13, #16, #18 all fixed with pointers this session | ✓ holds |

Reconciled (looked wrong, checked out): FINDINGS pC1 "Maxime 12" = 10 pC1 + 2 P1
in raw (family query); FINDINGS DN ≈1,310 = central-only scope vs 1,331–1,342
whole-export; `cns_fly.py` header "158,262 neurons" counts raw rows incl. glia
while README counts 157,789 sim neurons — suggest aligning the header.

## Citations

| Paper | Cited for | Status |
|-------|-----------|--------|
| Dorkenwald et al., Nature 634:124-138 (2024), doi:10.1038/s41586-024-07558-y | FlyWire v783, 139,255 neurons | ✓ verified |
| Schlegel et al., Nature (2024), doi:10.1038/s41586-024-07686-5 | FlyWire 8,453 cell-type annotations | ✓ verified |
| Bates et al., Nature 656:957-970 (2026) | BANC connectome | ✓ headline; export-version detail open |
| Berg et al., Cell 189(18) (2026), doi:10.1016/j.cell.2026.08.015 | Male CNS v1.0, 166,700 | ⚠ found via secondary source; open before submission |
| Gaudry et al., Nature 493:424-428 (2013) | ORN ~40% more transmitter ipsilaterally | ✓ paper · ⚠ G1: code implements ipsi×1.3/contra×0.7 = 86% asymmetry, stronger than cited; needs justification or fix |
| Shiu et al., Nature 634:210-219 (2024), doi:10.1038/s41586-024-07763-9 | LIF model, transmitter sign convention | ✓ verified; lab's g_exc 0.3 vs 0.275 documented in code |
| Eckstein et al., Cell 187:2574-2594 (2024), doi:10.1016/j.cell.2024.03.016 | Transmitter prediction 87/94/91% | ✓ verified |
| Braun et al., Nature (2024), doi:10.1038/s41586-024-07523-9 | ~1,300 DNs | ✓ verified |
| Cheong et al. (2024) | MANC 1,328 DNs | ✓ verified |
| Stürner et al., Nature (2025), doi:10.1038/s41586-025-08925-z | female DN comparison | ✓ verified (was "2024" in my check; fixed) |
| Ito et al. 2014; Court et al. 2020 | brain/VNC region expansions (portal) | ✓ verified earlier |
| bioRxiv 10.1101/2025.10.09.680999 | 1,427 male-specific neurons | ⚠ not opened |
| Wolf/Rodan/Tsai/Heberlein 2002; Shaw 2000; Hendricks 2000; Sweeney 1995; Parr 2001 | drug-behaviour literature (P1-P6) | ⚠ not opened; standard papers, PubMed-accessible |
| Hallem & Carlson 2006 | banana-glomerulus set (flywire_brain.py:31) | ⚠ not opened; landmark OR map, plausible |

## Fixes applied this session (own files)

- `metaverse-lab/flies/index.html`: 158,262 → 157,789 neurons; 4.0M → 3.0M
  connections (lab convention = built edges); `check.py` pin updated; ALL PASS.
- `verification/check_published_counts.py`: Stürner citation corrected to 2025.

## Fixes applied 2026-10-31 (user-authorized; files outside workspace edited via
approved script `/tmp/apply_audit_fixes.py`, each pattern matched exactly once)

1. README picrotoxin — kept, with pointer: TEST 6 `BRAIN=flywire` gives
   9.77% vs 2.34% (=4.17×), synchrony 0. (BANC 2.9×, Male CNS 4.8× same test.)
2. README caffeine — now recites the 0.5 record (+38%/+42%, SUPPORTED each).
3. README DEET — now 56–80% with per-brain TEST 9 numbers.
4. G1 Gaudry precision — documented in `cns_fly.py:33`, `flywire_brain.py:35-37`,
   and validation.md scope. OPEN DECISION (not taken): rescaling 1.3/0.7 toward
   the cited 1.4 ratio would change W, stale all three caches, and shift every
   validation number — a model change for the owner, not a text fix.
5. (minor) validation "50.7M" qualified; `cns_fly.py` header now 157,789.
6. (minor, informational) fresh-clone rebuilds match the committed caches
   array-for-array except `xyz`, which carries ~2e-7 float32 rounding noise
   between builds. Harmless: `step()` never reads `xyz`; the server rounds it
   to 1e-3 for the hologram. `check_fresh_clone.py` asserts exact equality on
   all science arrays and allclose (atol=1e-6) on `xyz`.
