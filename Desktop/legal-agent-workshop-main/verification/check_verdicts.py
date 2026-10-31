#!/usr/bin/env python3
"""Layer 3: verdict-rule tests. Feeds analyse() hand-made numbers with known
answers. No server, no randomness: imports experiments.py read-only.

Covers: clear win -> SUPPORTED; wrong direction -> NOT SUPPORTED; runs
disagree -> NOT SUPPORTED; tiny gap -> CAN'T TELL YET; sexes disagree ->
MIXED; agreement paths; 'different' prediction; missing data; multi-treatment.
"""
import sys

sys.path.insert(0, "/Users/robin/Desktop/Fly Brain")
from experiments import analyse  # noqa: E402

R = {}


def ok(name, cond, note=""):
    R[name] = bool(cond)
    print(f"   {'PASS' if cond else 'FAIL'}  {name}  {note}")


def card(prediction, treatments=("drug 1.0",), measure="activity"):
    return {
        "title": "t", "hypothesis": "h", "description": "d",
        "control": {"name": "sober", "setup": []},
        "treatments": [{"name": t, "setup": []} for t in treatments],
        "measure": measure, "prediction": prediction,
        "repeats": 5, "steps": 10, "author": "check",
    }


def runs(vals):
    return [{"activity": v} for v in vals]


CTRL = [10.0, 11.0, 9.0, 10.0, 11.0]  # mean 10.2
WIN = [20.0, 21.0, 19.0, 20.0, 21.0]  # clear higher win
WRONG = [0.0, 1.0, -1.0, 0.0, 1.0]  # big gap, wrong way for 'higher'
SPLIT = [12.0, 8.0, 12.0, 8.0, 8.0]  # 2/5 hits: runs disagree
TINY = [10.5, 10.4, 10.6, 10.5, 10.4]  # gap < wobble
ALIKE = [10.1, 9.9, 10.0, 10.2, 9.8]  # boxes look alike

# 1-4: single fly, prediction 'higher'
_, v, _ = analyse(card("higher"), {"sober": runs(CTRL), "drug 1.0": runs(WIN)})
ok("clear_win_supported", v == "SUPPORTED", v)
_, v, _ = analyse(card("higher"), {"sober": runs(CTRL), "drug 1.0": runs(WRONG)})
ok("wrong_direction_not_supported", v == "NOT SUPPORTED", v)
_, v, _ = analyse(card("higher"), {"sober": runs(CTRL), "drug 1.0": runs(SPLIT)})
ok("runs_disagree_not_supported", v == "NOT SUPPORTED", v)
_, v, _ = analyse(card("higher"), {"sober": runs(CTRL), "drug 1.0": runs(TINY)})
ok("tiny_gap_cant_tell", v == "CAN'T TELL YET", v)

# 5-6: prediction 'lower' mirrors
_, v, _ = analyse(card("lower"), {"sober": runs(CTRL), "drug 1.0": runs(WRONG)})
ok("lower_win_supported", v == "SUPPORTED", v)
_, v, _ = analyse(card("lower"), {"sober": runs(CTRL), "drug 1.0": runs(WIN)})
ok("lower_wrong_way_not_supported", v == "NOT SUPPORTED", v)

# 7-8: prediction 'different'
_, v, _ = analyse(card("different"), {"sober": runs(CTRL), "drug 1.0": runs(ALIKE)})
ok("different_alike_not_supported", v == "NOT SUPPORTED", v)
_, v, _ = analyse(card("different"), {"sober": runs(CTRL), "drug 1.0": runs(WIN)})
ok("different_clear_gap_supported", v == "SUPPORTED", v)

# 9-10: missing data
_, v, _ = analyse(card("higher"), {"sober": runs(CTRL), "drug 1.0": runs([None] * 5)})
ok("no_treatment_measure_cant_tell", v == "CAN'T TELL YET", v)
_, v, _ = analyse(card("higher"), {"sober": runs([None] * 5), "drug 1.0": runs(WIN)})
ok("no_control_measure_cant_tell", v == "CAN'T TELL YET", v)

# 11: multi-treatment split -> MIXED (one fly, two treatments)
_, v, det = analyse(card("higher", ("good 1.0", "bad 1.0")),
                    {"sober": runs(CTRL), "good 1.0": runs(WIN), "bad 1.0": runs(WRONG)})
ok("multi_treatment_split_mixed", v == "MIXED", v)

# 12-15: two sexes
def twofly(female_t, male_t, pred="higher"):
    return {"sober": {"Maxime": runs(CTRL), "Hagrid": runs(CTRL)},
            "drug 1.0": {"Maxime": runs(female_t), "Hagrid": runs(male_t)}}

_, v, det = analyse(card("higher"), twofly(WIN, WRONG))
ok("sexes_disagree_mixed", v == "MIXED" and det["verdicts"] == {"Maxime": "SUPPORTED", "Hagrid": "NOT SUPPORTED"},
   f"{v} {det['verdicts']}")
_, v, _ = analyse(card("higher"), twofly(WIN, WIN))
ok("sexes_agree_supported", v == "SUPPORTED", v)
_, v, _ = analyse(card("higher"), twofly(WRONG, WRONG))
ok("sexes_agree_not_supported", v == "NOT SUPPORTED", v)
_, v, _ = analyse(card("higher"), twofly(TINY, TINY))
ok("sexes_agree_cant_tell", v == "CAN'T TELL YET", v)

print("\n==== SUMMARY ====")
for k, v in R.items():
    print(f"   {k:34s} {'PASS' if v else 'FAIL'}")
print("ALL PASS" if all(R.values()) else "NOT YET")
sys.exit(0 if all(R.values()) else 1)
