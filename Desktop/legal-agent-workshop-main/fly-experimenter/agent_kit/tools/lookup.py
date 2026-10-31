"""Look up a substance: library mechanism, confidence, web sources."""
NAME = "lookup_substance"
DESCRIPTION = ("Look up a substance by free text: library mechanism and "
               "confidence when curated, plus web source titles.")
PARAMETERS = {
    "type": "object",
    "properties": {"name": {"type": "string", "description": "drug or chemical name"}},
    "required": ["name"], "additionalProperties": False,
}


def run(args, ctx):
    ack = ctx.lab.call({"cmd": "lookup", "name": args["name"]})
    if not ack.get("ok"):
        return {"status": "error", "error": "lookup refused"}
    data = ack.get("data") or {}
    lib = data.get("library") or {}
    match = data.get("match")
    name = match.get("name") if isinstance(match, dict) else match
    return {"status": "ok", "match": name, "known": data.get("known"),
            "mechanism": lib.get("mechanism") or (match or {}).get("mechanism")
            if isinstance(match, dict) else lib.get("mechanism"),
            "confidence": lib.get("confidence") or (match or {}).get("confidence")
            if isinstance(match, dict) else lib.get("confidence"),
            "not_modelled": lib.get("not_modelled"),
            "sources": [s.get("title") for s in (data.get("sources") or [])][:5]}
