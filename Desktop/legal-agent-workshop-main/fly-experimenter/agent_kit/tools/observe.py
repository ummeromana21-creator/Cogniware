"""Read the latest lab snapshot: step, rate, trial, per-fly firing and drugs."""
NAME = "observe"
DESCRIPTION = ("Read the latest lab snapshot: step, steps/s, whether a trial is "
               "running, and per fly position, firing percent, drugs on board, "
               "bananas caught. No side effects.")
PARAMETERS = {"type": "object", "properties": {}, "additionalProperties": False}


def run(args, ctx):
    st = ctx.lab.state()
    if st is None:
        return {"status": "error", "error": "no state received from the lab yet"}
    flies = {}
    for slot, fs in (st.get("flies") or {}).items():
        fly = fs.get("fly") or {}
        rates = fs.get("rates") or {}
        flies[slot] = {
            "label": fs.get("label", slot),
            "x": round(fly.get("x", 0), 2),
            "y": round(fly.get("y", 0), 2),
            "firing_pct": round(100 * (rates.get("all") or 0), 2),
            "drugs": fs.get("drugs") or {},
            "caught": fs.get("caught", 0),
        }
    trial = st.get("trial")
    return {"status": "ok", "t": st.get("t"), "sps": st.get("sps"),
            "trial": None if trial is None else {
                "label": trial.get("label"), "progress": round(trial.get("progress") or 0, 2)},
            "flies": flies}
