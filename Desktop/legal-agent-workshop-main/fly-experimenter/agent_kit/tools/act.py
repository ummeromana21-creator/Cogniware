"""Change the lab: dose, washout, restore and other single-verb actions."""
NAME = "act"
DESCRIPTION = ("Change the lab with one verb: dose (drug, amount, target), washout, "
               "restore, reset_fly, scramble, set_speed, set_param, focus. Targets: "
               "treatment, control, all, or one slot such as treatment:banc.")
PARAMETERS = {
    "type": "object",
    "properties": {
        "action": {"type": "string", "enum": ["dose", "washout", "restore", "reset_fly",
                                             "scramble", "set_speed", "set_param", "focus"]},
        "target": {"type": "string", "description": "treatment, control, all, or a slot"},
        "drug": {"type": "string"}, "amount": {"type": "number"},
        "sps": {"type": "number"}, "key": {"type": "string"},
        "value": {}, "slot": {"type": "string"},
    },
    "required": ["action"], "additionalProperties": False,
}
_NEEDS = {"dose": ("drug", "amount"), "set_speed": ("sps",), "set_param": ("key", "value"),
          "focus": ("slot",)}


def run(args, ctx):
    action = args.get("action")
    missing = [p for p in _NEEDS.get(action, ()) if args.get(p) is None]
    if missing:
        return {"status": "error", "error": f"{action} needs {', '.join(missing)}"}
    cmd = {"cmd": action}
    if action == "focus":
        cmd["slot"] = args["slot"]
    else:
        cmd["target"] = args.get("target", "treatment")
    for p in ("drug", "amount", "sps", "key", "value"):
        if args.get(p) is not None:
            cmd[p] = args[p]
    ack = ctx.lab.call(cmd)
    if not ack.get("ok"):
        return {"status": "error", "error": ack.get("error", "lab refused")}
    return {"status": "ok", "did": cmd}
