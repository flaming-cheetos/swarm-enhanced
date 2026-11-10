#!/usr/bin/env python3
"""
Capture harness for the Enhanced Swarm web panel.

NOT the real server. This reproduces the auth/transport contract that B1 put in
SwarmWebServer/SwarmWebHandler so the extracted panel can be rendered and its
controls exercised without launching Minecraft:

  GET /            token in ?token= or cookie  -> 200 + Set-Cookie + CSP
  GET /app.js      cookie required             -> 200
  POST /api/*      cookie required, Origin checked, always {"ok":true}
  GET /ws          cookie required, cookie must survive the WS handshake

Only the game data is mocked. Markup, CSS and app.js are the extracted source.
Every POST/handshake is appended to requests.jsonl with a timestamp.
"""
import base64
import hashlib
import json
import pathlib
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ROOT = pathlib.Path(__file__).resolve().parent.parent
HERE = pathlib.Path(__file__).resolve().parent
REQ_LOG = HERE / "requests.jsonl"
TOKEN = "b1-test-token"
HOST = "127.0.0.1"
PORT = 8791
GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
CSP = (
    "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
    "img-src 'self' data:; connect-src 'self' ws://127.0.0.1:8791; "
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
)

_lock = threading.Lock()


def record(event):
    event["ts"] = time.strftime("%H:%M:%S")
    with _lock:
        with REQ_LOG.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event) + "\n")


def state():
    bots = [
        {
            "username": "Miner01", "nickname": "Iron Goblin", "online": True,
            "health": 20.0, "food": 18, "x": 112, "y": 64, "z": -308,
            "task": "Gathering iron_ore", "progress": "37/64", "eta": "2m 10s",
            "tier": "IRON", "state": "GATHERING", "halted": False,
        },
        {
            "username": "Builder02", "nickname": "Bob", "online": True,
            "health": 14.5, "food": 9, "x": 98, "y": 71, "z": -301,
            "task": "Placing cobblestone", "progress": "12/32", "eta": "",
            "tier": "STONE", "state": "BUILDING", "halted": True,
        },
        {
            "username": "Farm03", "nickname": "Farm03", "online": False,
            "health": 0, "food": 0, "x": 0, "y": 0, "z": 0,
            "task": "Offline", "progress": "", "eta": "",
            "tier": "WOOD", "state": "OFFLINE", "halted": False,
        },
    ]
    tasks = [
        {"id": "0f7c5ae2-1111-4a11-9a11-111111111111", "type": "GATHER",
         "item": "iron_ore", "quantity": 64, "progress": "37/64",
         "status": "ACTIVE", "reason": "", "bot": "Miner01"},
        {"id": "0f7c5ae2-2222-4a22-9a22-222222222222", "type": "CRAFT",
         "item": "iron_pickaxe", "quantity": 2, "progress": "0/2",
         "status": "PENDING", "reason": "", "bot": "any"},
        {"id": "0f7c5ae2-3333-4a33-9a33-333333333333", "type": "BUILD",
         "item": "cobblestone", "quantity": 32, "progress": "12/32",
         "status": "BLOCKED", "reason": "waiting for smelt to finish",
         "bot": "Builder02"},
        {"id": "0f7c5ae2-4444-4a44-9a44-444444444444", "type": "SMELT",
         "item": "iron_ingot", "quantity": 16, "progress": "16/16",
         "status": "COMPLETE", "reason": "", "bot": "Miner01"},
        {"id": "0f7c5ae2-5555-4a55-9a55-555555555555", "type": "SCAN_CHESTS",
         "item": "", "quantity": 1, "progress": "0/1",
         "status": "CANCELLED", "reason": "host cancelled task",
         "bot": "any"},
        {"id": "0f7c5ae2-6666-4a66-9a66-666666666666", "type": "GATHER",
         "item": "coal", "quantity": 64, "progress": "0/64",
         "status": "FAILED", "reason": "no known source", "bot": "any"},
    ]
    return {
        "type": "state", "modVersion": "0.1.0",
        "buildCommit": "c366290305bf", "hostName": "rdh",
        "connected": 3, "bots": bots, "tasks": tasks,
    }


LOGS = [
    {"type": "log", "time": "04:03:11", "level": "INFO",
     "msg": "Web panel: http://127.0.0.1:8888/?token=<redacted>"},
    {"type": "log", "time": "04:03:14", "level": "INFO",
     "msg": "Task added: iron_ore x64 \u2192 any"},
    {"type": "log", "time": "04:04:02", "level": "WARN",
     "msg": "Path C:\\Users\\rdh\\multi\"line\nsecond line\ttabbed"},
    {"type": "log", "time": "04:04:19", "level": "ERROR",
     "msg": "API error on /api/task/add: For input string: \"sixty-four\""},
    {"type": "log", "time": "04:05:01", "level": "INFO",
     "msg": "resume received for Builder02"},
    {"type": "log", "time": "04:05:44", "level": "WARN",
     "msg": "Worker not found: Farm03"},
]


def frame(payload: bytes, opcode: int = 0x1) -> bytes:
    header = bytes([0x80 | opcode])
    n = len(payload)
    if n < 126:
        header += bytes([n])
    elif n < 65536:
        header += bytes([126]) + struct.pack(">H", n)
    else:
        header += bytes([127]) + struct.pack(">Q", n)
    return header + payload


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _cookie_token(self):
        raw = self.headers.get("Cookie", "")
        for part in raw.split(";"):
            part = part.strip()
            if part.startswith("swarm_token="):
                return part.split("=", 1)[1]
        return None

    def _authorized(self, query):
        return (
            self.headers.get("X-Swarm-Token") == TOKEN
            or query.get("token", [None])[0] == TOKEN
            or self._cookie_token() == TOKEN
        )

    def _origin_allowed(self):
        origin = self.headers.get("Origin")
        if origin is None:
            return True
        host = self.headers.get("Host", "")
        return origin.lower() == ("http://" + host).lower()

    def _send(self, code, content_type, body, extra=None):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        path = parsed.path
        # /ws authenticates by cookie only in a browser; keep /__requests open
        # to inspection with the same cookie or the token query.
        if path == "/__requests":
            rows = REQ_LOG.read_text(encoding="utf-8") if REQ_LOG.exists() else ""
            self._send(200, "text/plain; charset=UTF-8", rows)
            return
        if not self._authorized(query):
            record({"event": "401", "path": path, "has_cookie": bool(self._cookie_token())})
            self._send(401, "text/plain", "Unauthorized")
            return

        if path == "/":
            record({"event": "get", "path": "/", "status": 200,
                    "has_cookie": bool(self._cookie_token()),
                    "via_query": query.get("token", [None])[0] == TOKEN})
            html = (ROOT / "index.html").read_text(encoding="utf-8")
            self._send(200, "text/html; charset=UTF-8", html, {
                "Set-Cookie": "swarm_token=" + TOKEN + "; Path=/; HttpOnly; SameSite=Strict",
                "Content-Security-Policy": CSP,
            })
        elif path == "/app.js":
            record({"event": "get", "path": "/app.js", "status": 200,
                    "has_cookie": bool(self._cookie_token())})
            js = (ROOT / "app.js").read_text(encoding="utf-8")
            self._send(200, "application/javascript; charset=UTF-8", js)
        elif path == "/ws":
            self._websocket()
        else:
            record({"event": "get", "path": path, "status": 404})
            self._send(404, "text/plain", "Not found")

    def do_POST(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        length = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(length).decode("utf-8", "replace")
        if not self._authorized(query):
            record({"event": "401", "path": parsed.path, "body": body})
            self._send(401, "text/plain", "Unauthorized")
            return
        if not self._origin_allowed():
            record({"event": "403", "path": parsed.path, "body": body,
                    "origin": self.headers.get("Origin")})
            self._send(403, "text/plain", "Origin not allowed")
            return
        record({"event": "post", "path": parsed.path, "body": body,
                "has_cookie": bool(self._cookie_token())})
        self._send(200, "application/json", '{"ok":true}')

    def _websocket(self):
        key = self.headers.get("Sec-WebSocket-Key")
        if self.headers.get("Upgrade", "").lower() != "websocket" or not key:
            self._send(400, "text/plain", "Bad websocket")
            return
        accept = base64.b64encode(
            hashlib.sha1((key + GUID).encode("ascii")).digest()
        ).decode("ascii")
        self.send_response(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.wfile.flush()
        record({"event": "ws_open", "path": "/ws",
                "has_cookie": bool(self._cookie_token()),
                "origin": self.headers.get("Origin", "")})
        try:
            self.wfile.write(frame(json.dumps(state()).encode("utf-8")))
            self.wfile.write(frame(json.dumps(
                {"type": "log_history", "logs": LOGS}).encode("utf-8")))
            self.wfile.flush()
            for _ in range(600):
                time.sleep(2)
                self.wfile.write(frame(json.dumps(state()).encode("utf-8")))
                self.wfile.flush()
        except Exception:
            return


def main():
    if REQ_LOG.exists():
        REQ_LOG.unlink()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    print(f"capture harness on http://{HOST}:{PORT}/?token={TOKEN}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
