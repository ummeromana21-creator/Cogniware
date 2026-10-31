"""Loop tests: complete, resume, and nudge. No server, no model key."""
import tempfile
import unittest

from agent_kit.progress import RunStore
from lab_agent import loop
from tests.fake_lab import FakeLab
from tests.fake_model import FakeModel, call


def script_done():
    return [call("c1", "observe", {}),
            call("c2", "run_trial", {"steps": 100, "label": "probe"}),
            call("c3", "file_report", {"findings": ["firing 1.5%"], "conclusion": "done"})]


class LoopTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = RunStore(root=self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_run_completes(self):
        out = loop.run_agent("probe the lab", turns=5,
                             client_obj=FakeModel(script_done()),
                             lab=FakeLab(), store=self.store)
        self.assertEqual(out["status"], "done")
        saved = self.store.load(out["run_id"])
        self.assertEqual(len(saved["turns"]), 3)
        self.assertEqual(saved["record"]["report"]["conclusion"], "done")
        self.assertEqual(len(saved["record"]["trials"]), 1)
        self.assertTrue(saved["messages"])

    def test_resume(self):
        model = FakeModel(script_done())
        lab = FakeLab()
        first = loop.run_agent("probe the lab", turns=1, client_obj=model,
                               lab=lab, store=self.store)
        self.assertEqual(first["status"], "paused")
        second = loop.resume_run(first["run_id"], turns=5, client_obj=model,
                                 lab=lab, store=self.store)
        self.assertEqual(second["status"], "done")
        self.assertEqual(len(self.store.load(first["run_id"])["turns"]), 3)

    def test_plain_text_gets_nudged(self):
        model = FakeModel([{"role": "assistant", "content": "thinking out loud"},
                           call("c9", "file_report", {"findings": [], "conclusion": "ok"})])
        out = loop.run_agent("q", turns=3, client_obj=model,
                             lab=FakeLab(), store=self.store)
        self.assertEqual(out["status"], "done")


if __name__ == "__main__":
    unittest.main()
