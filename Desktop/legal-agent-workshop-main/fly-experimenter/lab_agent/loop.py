"""The agentic loop: the model picks one tool per turn until it files a report."""
import json
import os
import time
import types

from agent_kit import client, progress, settings
from agent_kit.record import Record
from agent_kit.tools import registry

PLAYBOOK = os.path.join(os.path.dirname(__file__), "system_instructions.md")


def _complete(client_obj, model, messages, tries=3):
    last = None
    for _ in range(tries):
        try:
            return client_obj.complete(messages, registry.definitions())
        except Exception as e:
            last = e
            time.sleep(2)
    raise last


def _run_loop(store, run_id, messages, record, client_obj, model, lab, turns):
    ctx = types.SimpleNamespace(lab=lab, record=record, settings=None)
    for n in range(1, turns + 1):
        if n == turns:
            messages.append({"role": "user", "content": "Turn limit reached. File your report now with file_report."})
        try:
            reply = _complete(client_obj, model, messages)
        except Exception as e:
            store.save_turn(run_id, {"n": n, "error": f"{type(e).__name__}: {e}"})
            return {"run_id": run_id, "status": "paused", "turns": n, "note": "model failed"}
        messages.append(reply)
        calls = reply.get("tool_calls") or []
        if not calls:
            messages.append({"role": "user", "content": "Reply with exactly one tool call."})
            store.save_turn(run_id, {"n": n, "reply": reply.get("content", "")})
            store.save_messages(run_id, messages)
            continue
        call = calls[0]
        try:
            targs = json.loads(call["function"]["arguments"] or "{}")
        except ValueError:
            targs = None
        if targs is None:
            result = {"status": "error", "error": "tool arguments were not JSON"}
            name = call["function"]["name"]
        else:
            name = call["function"]["name"]
            result = registry.run(name, targs, ctx)
        messages.append({"role": "tool", "tool_call_id": call["id"],
                         "content": json.dumps(result)})
        store.save_turn(run_id, {"n": n, "tool": name, "args": targs, "result": result})
        store.save_record(run_id, record.to_dict())
        store.save_messages(run_id, messages)
        if result.get("status") == "over":
            return {"run_id": run_id, "status": "done", "turns": n}
    return {"run_id": run_id, "status": "paused", "turns": turns}


def run_agent(goal, turns=30, client_obj=None, lab=None, store=None, model=None):
    cfg = settings.load_settings()
    store = store or progress.RunStore()
    if lab is None:
        from agent_kit.lab import SyncLab
        lab = SyncLab(cfg["LAB_URL"])
        lab.connect()
        own_lab = True
    else:
        own_lab = False
    model = model or cfg["MODEL_API_MODEL"]
    if client_obj is None:
        if not cfg["MODEL_API_KEY"]:
            raise SystemExit("MODEL_API_KEY is empty: copy .env.example to .env and fill it")
        client_obj = client.ModelClient(cfg, model)
    with open(PLAYBOOK) as f:
        system = f.read()
    record = Record()
    run_id = store.new_run({"goal": goal, "turns": turns})
    try:
        return _run_loop(store, run_id, [{"role": "system", "content": system},
                                         {"role": "user", "content": goal}],
                         record, client_obj, model, lab, turns)
    finally:
        if own_lab:
            lab.close()


def resume_run(run_id, turns=15, client_obj=None, lab=None, store=None, model=None):
    cfg = settings.load_settings()
    store = store or progress.RunStore()
    saved = store.load(run_id)
    if lab is None:
        from agent_kit.lab import SyncLab
        lab = SyncLab(cfg["LAB_URL"])
        lab.connect()
        own_lab = True
    else:
        own_lab = False
    model = model or cfg["MODEL_API_MODEL"]
    if client_obj is None:
        if not cfg["MODEL_API_KEY"]:
            raise SystemExit("MODEL_API_KEY is empty: copy .env.example to .env and fill it")
        client_obj = client.ModelClient(cfg, model)
    try:
        return _run_loop(store, run_id, saved["messages"] or [], Record(saved["record"]),
                         client_obj, model, lab, turns)
    finally:
        if own_lab:
            lab.close()
