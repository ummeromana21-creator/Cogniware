"""Tool registry: definitions for the model, dispatch for the loop."""
from agent_kit.tools import act, card, experiment, lookup, notebook, observe, report, trial

TOOLS = [observe, act, trial, card, experiment, notebook, lookup, report]


def definitions():
    return [{"type": "function",
             "function": {"name": t.NAME, "description": t.DESCRIPTION,
                          "parameters": t.PARAMETERS}} for t in TOOLS]


def run(name, args, ctx):
    for t in TOOLS:
        if t.NAME == name:
            try:
                return t.run(args or {}, ctx)
            except Exception as e:
                return {"status": "error", "error": f"{type(e).__name__}: {e}"}
    return {"status": "error", "error": f"unknown tool {name}"}
