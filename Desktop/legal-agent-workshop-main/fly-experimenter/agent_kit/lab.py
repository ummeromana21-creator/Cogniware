"""Synchronous client for the fly lab's WebSocket protocol (stdlib until used)."""
import collections
import json


class LabError(Exception):
    pass


class SyncLab:
    def __init__(self, url):
        self.url = url
        self.ws = None
        self.hello = None
        self._state = None
        self._inbox = collections.deque()
        self._cmd_id = 0

    def connect(self, timeout=10):
        from websockets.sync.client import connect
        try:
            self.ws = connect(self.url, max_size=2 ** 20, open_timeout=timeout)
        except Exception as e:
            raise LabError(f"cannot reach lab at {self.url}: {e}")
        try:
            hello = json.loads(self.ws.recv(timeout=timeout))
        except Exception as e:
            raise LabError(f"no hello from lab: {e}")
        if hello.get("type") != "hello":
            raise LabError(f"expected hello, got {hello.get('type')}")
        self.hello = hello
        return hello

    def _read(self, timeout):
        try:
            return json.loads(self.ws.recv(timeout=timeout))
        except TimeoutError:
            return None

    def _pump(self, until, timeout):
        import time
        end = time.monotonic() + timeout
        while True:
            m = self._read(max(0.0, end - time.monotonic()))
            if m is None:
                return None
            t = m.get("type")
            if t == "state":
                self._state = m
            elif t in ("result", "experiment_done"):
                self._inbox.append(m)
            hit = until(m)
            if hit is not None:
                return hit

    def call(self, cmd, timeout=15):
        if self.ws is None:
            raise LabError("not connected")
        self._cmd_id += 1
        cmd = dict(cmd, id=f"a{self._cmd_id}")
        self.ws.send(json.dumps(cmd))
        ack = self._pump(lambda m: m if m.get("type") == "ack" and m.get("id") == cmd["id"] else None,
                         timeout)
        if ack is None:
            raise LabError(f"no ack for {cmd.get('cmd')}")
        return ack

    def state(self, timeout=5):
        if self.ws is None:
            raise LabError("not connected")
        self._pump(lambda m: None, 0.5 if self._state else timeout)
        return self._state

    def wait_for(self, pred, timeout=300):
        for m in list(self._inbox):
            if pred(m):
                self._inbox.remove(m)
                return m
        found = self._pump(lambda m: m if pred(m) else None, timeout)
        if found is None:
            raise LabError("timed out waiting for lab message")
        try:
            self._inbox.remove(found)
        except ValueError:
            pass
        return found

    def close(self):
        try:
            if self.ws is not None:
                self.ws.close()
        finally:
            self.ws = None
