#!/usr/bin/env python3
"""Layer 3: screen-vs-record. What lab.html shows must equal the notebook.

No browser installed here, so this checks the full pipeline the screen
renders, end to end against the live server:
  1. lab.html serves over HTTP and contains the render code (renderRecord,
     fmtv, badge) -- the screen is this code.
  2. the websocket verbs the page uses (list_notebook, get_experiment)
     return entries whose id/title/verdict match the JSON on disk.
  3. the newest full record payload matches the JSON on disk exactly.
  4. the page's own fmtv()/badge() (extracted from the served HTML, run
     under node) format numbers and verdicts as the records require.

Needs the lab server up:  python3 server.py   (ws 8765 + http 8000)
"""
import asyncio
import glob
import json
import os
import re
import subprocess
import sys
import urllib.request

LAB = "/Users/robin/Desktop/Fly Brain"
HTTP = os.environ.get("LAB_URL", "http://localhost:8000/lab.html")
WS = os.environ.get("LAB_WS", "ws://localhost:8765")
R = {}


def ok(name, cond, note=""):
    R[name] = bool(cond)
    print(f"   {'PASS' if cond else 'FAIL'}  {name}  {note}")


def main():
    import websockets  # noqa: PLC0415

    recs = {}
    for p in glob.glob(os.path.join(LAB, "notebook", "*.json")):
        try:
            r = json.load(open(p))
        except json.JSONDecodeError:
            continue
        if isinstance(r, dict) and "id" in r and "card" in r:
            recs[r["id"]] = r
    ok("records_on_disk", len(recs) > 0, f"{len(recs)} records")

    with urllib.request.urlopen(HTTP, timeout=20) as h:
        html = h.read().decode("utf-8", "replace")
    ok("page_serves", "renderRecord" in html, f"{len(html) // 1024} KB")
    for fn in ("renderRecord", "renderList", "function fmtv", "function badge"):
        ok(f"screen_code_has_{fn.split()[-1]}", fn in html)

    async def rpc(cmd, **kw):
        async with websockets.connect(WS, max_size=2 ** 22) as ws:
            await ws.recv()  # hello
            await ws.send(json.dumps(dict(id="svr", cmd=cmd, **kw)))
            while True:
                m = json.loads(await ws.recv())
                if m.get("type") == "ack" and m.get("id") == "svr":
                    return m

    a = asyncio.run(rpc("list_notebook"))
    entries = (a.get("data") or {}).get("notebook", [])
    ok("list_notebook_ok", a.get("ok") and entries, f"{len(entries)} entries")
    missing = [e["id"] for e in entries if e["id"] not in recs]
    ok("every_entry_matches_a_record", not missing, f"unmatched: {missing[:3]}" if missing else "")
    bad = [e["id"] for e in entries if e["id"] in recs
           and (e.get("title") != recs[e["id"]]["card"]["title"]
                or (e.get("verdict") or None) != recs[e["id"]].get("verdict"))]
    ok("entry_title_and_verdict_match", not bad, f"mismatched: {bad[:3]}" if bad else "")

    newest = max(recs.values(), key=lambda r: r.get("date", ""))
    g = asyncio.run(rpc("get_experiment", exp=newest["id"]))
    payload = (g.get("data") or {}).get("record")
    ok("record_payload_matches_disk", payload == newest,
       "" if payload == newest else "payload differs from JSON on disk")

    # the page's own formatting functions, run under node
    mfmt = re.search(r"function fmtv\(x\) [^\n]*", html)
    mbadge = re.search(r"function badge\(v\) [^\n]*", html)
    ok("fmtv_badge_extractable", bool(mfmt and mbadge))
    if mfmt and mbadge:
        js = (mfmt.group(0) + "\n" + mbadge.group(0)
              + "\nconsole.log(JSON.stringify({"
              + "a: fmtv(0.07244), b: fmtv(12.3456), c: fmtv(123.456), d: fmtv(null),"
              + "v: ['SUPPORTED', 'NOT SUPPORTED', 'MIXED', \"CAN'T TELL YET\"].map(badge)"
              + "}));")
        out = subprocess.run(["node", "-e", js], capture_output=True, text=True, timeout=30)
        got = json.loads(out.stdout or "{}")
        ok("fmtv_rounds_like_records",
           got.get("a") == "0.072" and got.get("b") == "12.35"
           and got.get("c") == "123" and got.get("d") == "n/a", str(got))
        ok("badge_shows_verdict_text",
           all(v in got.get("v", [])[i] for i, v in
               enumerate(["SUPPORTED", "NOT SUPPORTED", "MIXED", "CAN'T TELL YET"])),
           "badge text carries the record verdict")

    print("\n==== SUMMARY ====")
    for k, v in R.items():
        print(f"   {k:34s} {'PASS' if v else 'FAIL'}")
    print("ALL PASS" if all(R.values()) else "NOT YET")
    return 0 if all(R.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
