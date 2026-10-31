"""Scripted stand-in for the model: pops canned assistant messages."""


def call(mid, name, args):
    import json
    return {"role": "assistant", "content": "",
            "tool_calls": [{"id": mid, "type": "function",
                            "function": {"name": name, "arguments": json.dumps(args)}}]}


class FakeModel:
    def __init__(self, script):
        self.script = list(script)
        self.calls = 0

    def complete(self, messages, tools):
        self.calls += 1
        if self.script:
            return self.script.pop(0)
        return call("zz", "file_report",
                    {"findings": ["out of script"], "conclusion": "stopped"})
