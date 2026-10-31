"""Read the notebook: list experiments or fetch one record."""
NAME = "notebook"
DESCRIPTION = ("Read the lab notebook: list experiment cards with status and "
               "verdict, or fetch one record with its markdown table.")
PARAMETERS = {
    "type": "object",
    "properties": {"op": {"type": "string", "enum": ["list", "get"]},
                   "id": {"type": "string", "description": "experiment id for get"}},
    "required": ["op"], "additionalProperties": False,
}


def run(args, ctx):
    if args["op"] == "list":
        ack = ctx.lab.call({"cmd": "list_notebook"})
        if not ack.get("ok"):
            return {"status": "error", "error": "notebook refused"}
        return {"status": "ok", "experiments": (ack.get("data") or [])[:20]}
    if not args.get("id"):
        return {"status": "error", "error": "get needs an id"}
    ack = ctx.lab.call({"cmd": "get_experiment", "id": args["id"]})
    if not ack.get("ok"):
        return {"status": "error", "error": ack.get("error", "unknown experiment")}
    data = ack.get("data") or {}
    return {"status": "ok", "record": data.get("record"),
            "markdown": (data.get("markdown") or "")[:3000]}
