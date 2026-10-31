"""Run progress on disk: one folder per run, turns plus record plus messages."""
import datetime
import json
import os


class RunStore:
    def __init__(self, root="workspace"):
        self.root = root

    def new_run(self, meta):
        rid = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        path = os.path.join(self.root, "runs", rid)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, "meta.json"), "w") as f:
            json.dump(meta, f)
        return rid

    def _path(self, run_id, name):
        return os.path.join(self.root, "runs", run_id, name)

    def save_turn(self, run_id, turn):
        with open(self._path(run_id, "turns.jsonl"), "a") as f:
            f.write(json.dumps(turn) + "\n")

    def save_record(self, run_id, record):
        with open(self._path(run_id, "record.json"), "w") as f:
            json.dump(record, f)

    def save_messages(self, run_id, messages):
        with open(self._path(run_id, "messages.json"), "w") as f:
            json.dump(messages, f)

    def load(self, run_id):
        def read(name, default):
            try:
                with open(self._path(run_id, name)) as f:
                    return json.load(f)
            except FileNotFoundError:
                return default
        with open(self._path(run_id, "turns.jsonl")) as f:
            turns = [json.loads(line) for line in f if line.strip()]
        return {"meta": read("meta.json", {}), "turns": turns,
                "record": read("record.json", {}), "messages": read("messages.json", [])}

    def list_runs(self):
        base = os.path.join(self.root, "runs")
        if not os.path.isdir(base):
            return []
        return sorted(d for d in os.listdir(base)
                      if os.path.isfile(os.path.join(base, d, "meta.json")))
