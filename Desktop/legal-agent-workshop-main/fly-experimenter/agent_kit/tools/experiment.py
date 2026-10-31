"""Run a filed experiment card and wait for its verdict."""
NAME = "run_experiment"
DESCRIPTION = ("Run a filed experiment card by id and wait for completion: "
               "verdict plus per-fly verdicts. This takes minutes.")
PARAMETERS = {
    "type": "object",
    "properties": {"id": {"type": "string", "description": "experiment card id"}},
    "required": ["id"], "additionalProperties": False,
}


def run(args, ctx):
    ack = ctx.lab.call({"cmd": "run_experiment", "id": args["id"]})
    if not ack.get("ok"):
        return {"status": "error", "error": ack.get("error", "run refused")}
    rid = (ack.get("data") or {}).get("id")
    done = ctx.lab.wait_for(
        lambda m: m.get("type") == "experiment_done" and (rid is None or m.get("id") == rid),
        timeout=1200)
    rec = done.get("record") or {}
    ctx.record.add_trial({"label": f"experiment {args['id']}", "verdict": rec.get("verdict")})
    return {"status": "ok", "id": args["id"], "verdict": rec.get("verdict"),
            "flies": rec.get("verdicts_by_fly", {})}
