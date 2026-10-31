"""Tool tests against FakeLab. No server, no model key."""
import json
import types
import unittest

from agent_kit.record import Record
from agent_kit.tools import registry
from tests.fake_lab import FakeLab, SLOTS


def ctx(lab=None):
    return types.SimpleNamespace(lab=lab or FakeLab(), record=Record(), settings={})


class ToolsTest(unittest.TestCase):
    def test_observe_summarizes(self):
        r = registry.run("observe", {}, ctx())
        self.assertEqual(r["status"], "ok")
        self.assertEqual(set(r["flies"]), set(SLOTS))
        self.assertNotIn("fired", json.dumps(r))

    def test_act_dose(self):
        c = ctx()
        r = registry.run("act", {"action": "dose", "drug": "caffeine",
                                 "amount": 0.5, "target": "treatment"}, c)
        self.assertEqual(r["status"], "ok")
        self.assertEqual(c.lab.commands[-1]["cmd"], "dose")

    def test_act_missing_arg(self):
        r = registry.run("act", {"action": "dose"}, ctx())
        self.assertEqual(r["status"], "error")

    def test_trial(self):
        c = ctx()
        r = registry.run("run_trial", {"steps": 100, "label": "p"}, c)
        self.assertEqual(r["status"], "ok")
        self.assertEqual(set(r["flies"]), set(SLOTS))
        self.assertEqual(len(c.record.trials), 1)

    def test_card(self):
        c = ctx()
        r = registry.run("propose_experiment",
                         {"title": "t", "control": [], "treatments": [{"dose": 1}],
                          "measure": "activity", "prediction": "higher"}, c)
        self.assertEqual(r["status"], "ok")
        self.assertEqual(r["id"], "e1")
        self.assertEqual(c.record.card["id"], "e1")

    def test_experiment(self):
        r = registry.run("run_experiment", {"id": "e1"}, ctx())
        self.assertEqual(r["status"], "ok")
        self.assertEqual(r["verdict"], "SUPPORTED")

    def test_notebook(self):
        c = ctx()
        self.assertEqual(registry.run("notebook", {"op": "list"}, c)["status"], "ok")
        self.assertEqual(registry.run("notebook", {"op": "get", "id": "e1"}, c)["status"], "ok")
        self.assertEqual(registry.run("notebook", {"op": "get"}, c)["status"], "error")

    def test_lookup(self):
        r = registry.run("lookup_substance", {"name": "caffeine"}, ctx())
        self.assertEqual(r["status"], "ok")
        self.assertEqual(r["match"], "caffeine")
        self.assertEqual(r["mechanism"], "test")
        self.assertEqual(r["confidence"], "high")

    def test_report_ends_run(self):
        c = ctx()
        r = registry.run("file_report", {"findings": ["a"], "conclusion": "b"}, c)
        self.assertEqual(r["status"], "over")
        self.assertEqual(c.record.report["conclusion"], "b")

    def test_unknown_tool(self):
        self.assertEqual(registry.run("nope", {}, ctx())["status"], "error")

    def test_tool_exception_is_error(self):
        class Broken:
            def state(self, timeout=5):
                raise RuntimeError("boom")
        r = registry.run("observe", {}, ctx(lab=Broken()))
        self.assertEqual(r["status"], "error")
        self.assertIn("RuntimeError", r["error"])

    def test_definitions_shape(self):
        for d in registry.definitions():
            self.assertEqual(d["type"], "function")
            self.assertIn("name", d["function"])
            self.assertIn("parameters", d["function"])


if __name__ == "__main__":
    unittest.main()
