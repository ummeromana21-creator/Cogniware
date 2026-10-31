"""Review a finished run: goal, turns, tools used, trials, report."""
import sys

from agent_kit.progress import RunStore


def main(argv):
    if len(argv) != 2:
        print("usage: ./helper review RUN_ID")
        return 1
    runs = RunStore()
    try:
        saved = runs.load(argv[1])
    except FileNotFoundError:
        print(f"no such run: {argv[1]}")
        return 1
    print(f"run {argv[1]}: {saved['meta'].get('goal', '')}")
    tools = [t.get("tool", "?") for t in saved["turns"]]
    print(f"turns: {len(saved['turns'])} ({', '.join(tools)})")
    rec = saved["record"]
    for t in rec.get("trials", []):
        print(f"trial: {t.get('label')} -> {t.get('verdict', 'measured')}")
    rep = rec.get("report")
    if rep:
        for f in rep.get("findings", []):
            print(f"finding: {f}")
        print(f"conclusion: {rep.get('conclusion')}")
    else:
        print("no report filed (run paused)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
