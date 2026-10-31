"""Run one measurement trial and wait for its per-fly metrics."""
NAME = "run_trial"
DESCRIPTION = ("Run one measurement trial of N steps and wait for the result: "
               "activity, firing and bananas per fly in each box. No drugs change.")
PARAMETERS = {
    "type": "object",
    "properties": {
        "steps": {"type": "integer", "description": "measurement steps, default 400"},
        "label": {"type": "string", "description": "short label for the trial"},
    },
    "additionalProperties": False,
}


def run(args, ctx):
    steps = args.get("steps", 400)
    label = args.get("label", "trial")
    ack = ctx.lab.call({"cmd": "run_trial", "steps": steps, "label": label})
    if not ack.get("ok"):
        return {"status": "error", "error": ack.get("error", "trial refused")}
    res = ctx.lab.wait_for(
        lambda m: m.get("type") == "result" and m.get("id") == ack["id"],
        timeout=90 + steps // 2)
    flies = {}
    for slot, v in (res.get("per_fly") or {}).items():
        flies[slot] = {"activity": round(v.get("activity", 0), 4),
                       "firing": round(v.get("firing_all", 0), 4),
                       "bananas": v.get("bananas", 0)}
    ctx.record.add_trial({"label": label, "steps": steps, "flies": flies})
    return {"status": "ok", "label": label, "flies": flies}
