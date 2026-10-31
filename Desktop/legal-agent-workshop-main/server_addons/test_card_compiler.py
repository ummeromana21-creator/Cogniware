"""Focused tests for card_compiler validation. No network, no API key.

Run:  python3 test_card_compiler.py   (from this directory)
"""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import card_compiler as cc

KNOW = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                   '..', 'metaverse-lab', 'ask', 'knowledge.json')))
EV = KNOW['substances']['deet']['evidence'][0]  # filed line to quote


def run_spec(**kw):
    spec = {'mode': 'run', 'substance': 'deet', 'measure': 'bananas',
            'hypothesis': 'deet lowers bananas found.', 'prediction': 'lower',
            'procedure': ['Restore both boxes.', 'Dose deet 0.5.'],
            'expected_from': [EV], 'caveat': None}
    spec.update(kw)
    return spec


class ValidateRun(unittest.TestCase):
    def test_valid_card_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            card, problems = cc.validate_run(run_spec(), KNOW, tmp)
        self.assertEqual(problems, [])
        self.assertEqual(card['substance'], 'deet')
        self.assertEqual(card['card']['treatments'][0]['setup'][1]['drug'], 'deet')
        self.assertEqual(card['evidence'], [EV])

    def test_unknown_substance_rejected(self):
        card, problems = cc.validate_run(run_spec(substance='unobtanium'), KNOW, '/none')
        self.assertIsNone(card)
        self.assertTrue(problems)

    def test_nondosable_rejected(self):
        card, problems = cc.validate_run(run_spec(substance='salt'), KNOW, '/none')
        self.assertIsNone(card)
        self.assertIn('not dosable', problems[0])

    def test_invented_citation_dropped(self):
        spec = run_spec(expected_from=['Smith et al. 2099 — deet cures everything'])
        with tempfile.TemporaryDirectory() as tmp:
            card, problems = cc.validate_run(spec, KNOW, tmp)
        self.assertEqual(problems, [])
        self.assertEqual(card['evidence'], [])
        self.assertTrue(any('unfiled' in n for n in card['notes']))

    def test_unknown_measure_falls_back(self):
        with tempfile.TemporaryDirectory() as tmp:
            card, _ = cc.validate_run(run_spec(measure='telepathy'), KNOW, tmp)
        self.assertEqual(card['measure'], 'bananas')


class ExplainAndRefuse(unittest.TestCase):
    def test_explain_keeps_filed_refs_and_stages(self):
        spec = {'mode': 'explain', 'title': 'Smell path', 'paragraphs': ['You smell with your nose.'],
                'stages': ['nose', 'mushroom body', 'teleporter'], 'refs': [EV], 'substance': 'deet'}
        with tempfile.TemporaryDirectory() as tmp:
            card, _ = cc.validate_explain(spec, KNOW, tmp)
        self.assertEqual(card['stages'], ['nose', 'mushroom body'])
        self.assertEqual(card['refs'], [EV])

    def test_compile_refuse_raises(self):
        with self.assertRaises(ValueError):
            cc.compile_question('x', KNOW, '/none', llm_call=lambda s, u: '{"mode":"refuse","reason":"nope"}')

    def test_empty_reply_raises_cleanly(self):
        for bad in (None, '', 123):
            with self.assertRaises(ValueError):
                cc.parse_reply(bad)

    def test_compile_run_end_to_end_with_stub(self):
        calls = []
        def stub(s, u):
            calls.append(u)
            if len(calls) == 1:
                return json.dumps({'mode': 'run', 'substance': 'deet', 'measure': 'bananas'})
            return 'Here is your card: ' + json.dumps(run_spec()) + ' done.'
        with tempfile.TemporaryDirectory() as tmp:
            card = cc.compile_question('does deet stop bananas', KNOW, tmp, llm_call=stub)
        self.assertEqual(len(calls), 2)
        self.assertEqual(card['badge'], 'ai')
        self.assertEqual(card['card']['measure'], 'bananas')

    def test_routing_rejects_unknown_substance_early(self):
        calls = []
        def stub(s, u):
            calls.append(u)
            return json.dumps({'mode': 'run', 'substance': 'unobtanium'})
        with self.assertRaises(ValueError):
            cc.compile_question('x', KNOW, '/none', llm_call=stub)
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main(verbosity=1)
