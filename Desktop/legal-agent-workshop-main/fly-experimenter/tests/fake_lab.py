"""Scripted stand-in for SyncLab: canned hello, states, acks and results."""
from agent_kit.lab import LabError

SLOTS = ["control:banc", "control:malecns", "treatment:banc", "treatment:malecns"]


def _fly(label, firing=0.015):
    return {"label": label, "fly": {"x": 1.0, "y": 2.0, "angle": 0.5},
            "rates": {"all": firing}, "drugs": {}, "caught": 0,
            "banana": {"x": 5.0, "y": 0.0}}


class FakeLab:
    def __init__(self, states=None):
        self.hello = {"type": "hello", "mode": "live-test", "t": 100, "sps": 30,
                      "flies": {s: {"label": s} for s in SLOTS},
                      "drugs": {"caffeine": {}, "picrotoxin": {}}}
        self._states = list(states or [])
        self._state = {"type": "state", "t": 100, "sps": 30, "trial": None,
                       "flies": {s: _fly(s) for s in SLOTS}}
        self.inbox = []
        self.commands = []
        self.connected = False

    def connect(self, timeout=10):
        self.connected = True
        return self.hello

    def state(self, timeout=5):
        if self._states:
            self._state = self._states.pop(0)
        return self._state

    def call(self, cmd, timeout=15):
        self.commands.append(cmd)
        cid = f"a{len(self.commands)}"
        c = cmd.get("cmd")
        if c == "run_trial":
            self.inbox.append(
                {"type": "result", "id": cid, "label": cmd.get("label", "t"),
                 "per_fly": {s: {"activity": 0.07, "firing_all": 0.015, "bananas": 1}
                             for s in SLOTS}})
            return {"type": "ack", "id": cid, "ok": True, "data": {}}
        if c == "propose_experiment":
            return {"type": "ack", "id": cid, "ok": True, "data": {"id": "e1", "steps": 6}}
        if c == "run_experiment":
            self.inbox.append({"type": "experiment_done", "id": "r1",
                               "record": {"verdict": "SUPPORTED",
                                          "verdicts_by_fly": {"Maxime": "SUPPORTED"}}})
            return {"type": "ack", "id": cid, "ok": True, "data": {"id": "r1"}}
        if c == "lookup":
            return {"type": "ack", "id": cid, "ok": True,
                    "data": {"match": {"name": "caffeine", "confidence": "high"},
                             "known": True, "library": {"mechanism": "test"},
                             "sources": [{"title": "s1"}]}}
        if c == "list_notebook":
            return {"type": "ack", "id": cid, "ok": True,
                    "data": [{"id": "e1", "title": "t", "status": "done"}]}
        if c == "get_experiment":
            return {"type": "ack", "id": cid, "ok": True,
                    "data": {"record": {"id": "e1"}, "markdown": "# e1"}}
        if c in ("dose", "washout", "restore", "reset_fly", "scramble",
                 "set_speed", "set_param", "focus"):
            return {"type": "ack", "id": cid, "ok": True, "data": {}}
        return {"type": "ack", "id": cid, "ok": False, "error": f"no such command: {c}"}

    def wait_for(self, pred, timeout=300):
        for m in list(self.inbox):
            if pred(m):
                self.inbox.remove(m)
                return m
        raise LabError("timed out waiting for lab message")

    def close(self):
        self.connected = False
