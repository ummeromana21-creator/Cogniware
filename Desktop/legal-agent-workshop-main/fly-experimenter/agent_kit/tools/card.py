"""File an experiment card: control vs treatments, measure, prediction."""
NAME = "propose_experiment"
DESCRIPTION = ("File an experiment card: title, hypothesis, control and treatment "
               "step lists (generic verbs only), measure, prediction "
               "(higher/lower/different), repeats, steps. Returns the card id.")
PARAMETERS = {
    "type": "object",
    "properties": {
        "title": {"type": "string"}, "hypothesis": {"type": "string"},
        "description": {"type": "string"},
        "control": {"type": "array", "items": {"type": "object"}},
        "treatments": {"type": "array", "items": {"type": "object"}},
        "measure": {"type": "string",
                    "description": "activity, firing, firing.<group>, tortuosity, "
                                   "mean_abs_turn, synchrony, bananas, legs.<i>"},
        "prediction": {"type": "string", "enum": ["higher", "lower", "different"]},
        "source": {"type": "object", "description": "optional cited claim {citation, claim, direction}",
                   "properties": {"citation": {"type": "string"}, "claim": {"type": "string"},
                                  "direction": {"type": "string",
                                                "enum": ["higher", "lower", "different"]}}},
        "repeats": {"type": "integer"}, "steps": {"type": "integer"},
    },
    "required": ["title", "control", "treatments", "measure", "prediction"],
    "additionalProperties": False,
}


def run(args, ctx):
    card = dict(args, author="agent")
    if "description" not in card:
        card["description"] = card.get("hypothesis", "")
    ack = ctx.lab.call({"cmd": "propose_experiment", "card": card})
    if not ack.get("ok"):
        return {"status": "error", "error": ack.get("error", "card refused")}
    data = ack.get("data") or {}
    ctx.record.set_card({"id": data.get("id"), "title": card["title"]})
    return {"status": "ok", "id": data.get("id"), "steps": data.get("steps")}
