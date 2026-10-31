"""CLI: python -m lab_agent "goal" [--turns N] [--resume RUN_ID] [--lab URL] [--model M]."""
import argparse
import json

from lab_agent import loop


def main(argv=None):
    ap = argparse.ArgumentParser(description="Fly experimenter agent")
    ap.add_argument("goal", nargs="?", default="", help="the question to answer")
    ap.add_argument("--turns", type=int, default=30)
    ap.add_argument("--resume", default=None, help="run id to continue")
    ap.add_argument("--lab", default=None, help="lab WebSocket URL")
    ap.add_argument("--model", default=None, help="model name override")
    a = ap.parse_args(argv)
    if a.resume:
        out = loop.resume_run(a.resume, turns=a.turns, model=a.model)
    else:
        if not a.goal:
            ap.error("give a goal, or --resume RUN_ID")
        lab = None
        if a.lab:
            from agent_kit.lab import SyncLab
            lab = SyncLab(a.lab)
            lab.connect()
        out = loop.run_agent(a.goal, turns=a.turns, lab=lab, model=a.model)
    print(json.dumps(out))


if __name__ == "__main__":
    main()
