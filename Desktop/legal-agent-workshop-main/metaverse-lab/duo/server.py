#!/usr/bin/env python3
"""Duo auth server — real server-side accounts for the Hagrid & Maxime page.

Run from this folder (no dependencies, stdlib only):
    python3 server.py [port]        # default 8123
Then open http://localhost:8123/duo/ in a browser.

The server serves the whole metaverse-lab tree (so every lab link works)
plus a same-origin JSON API:

    POST /api/signup   {email, password, first_name, last_name} -> {user}
    POST /api/login    {email, password} -> {user}
    POST /api/logout   -> {ok: true}
    GET  /api/me       -> {user} or 401 {user: null}
    GET  /api/config   -> {server: true, providers: {google, meta, apple}}
    GET  /api/oauth/<provider>/start     -> 302 to provider (or 501 JSON)
    GET  /api/oauth/<provider>/callback  -> 302 to /duo/ (sets session cookie)

Passwords are scrypt-hashed (per-user salt); sessions are random tokens in
SQLite, carried in an HttpOnly SameSite cookie. Email/password rules mirror
the page's own checklist. OAuth providers activate when their env vars are
set (see AUTH.md); otherwise the page keeps its clearly-labelled demo mode.
"""
import base64
import hashlib
import hmac
import json
import mimetypes
import os
import re
import secrets
import sqlite3
import sys
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # serve the whole metaverse-lab tree
DB_PATH = os.environ.get("DUO_DB", os.path.join(HERE, "duo-auth.sqlite3"))
SESSION_DAYS = 30

EMAIL_RE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]{2,}$")
PASSWORD_RULES = [
    ("len", "At least 8 characters", "8+ characters",
     lambda p: len(p) >= 8),
    ("upper", "An uppercase letter (A–Z)", "an uppercase letter",
     lambda p: bool(re.search(r"[A-Z]", p))),
    ("lower", "A lowercase letter (a–z)", "a lowercase letter",
     lambda p: bool(re.search(r"[a-z]", p))),
    ("digit", "A number (0–9)", "a number",
     lambda p: bool(re.search(r"[0-9]", p))),
    ("sym", "A symbol (!@#$…)", "a symbol",
     lambda p: bool(re.search(r"[^A-Za-z0-9]", p))),
]

SCRYPT_N, SCRYPT_R, SCRYPT_P, DKLEN = 2 ** 14, 8, 1, 32


class AuthError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


# ---------- secrets / config ----------

def load_secret():
    env = os.environ.get("DUO_SECRET", "").strip()
    if env:
        return env.encode()
    path = os.path.join(HERE, ".duo-secret")
    if os.path.exists(path):
        with open(path, "rb") as f:
            return f.read().strip()
    secret = secrets.token_bytes(32)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(base64.urlsafe_b64encode(secret))
    print("NOTE: generated a new server secret at .duo-secret "
          "(sessions reset if it is deleted).", flush=True)
    return secret


SECRET = None  # set in main(); tests may set directly


def secret():
    global SECRET
    if SECRET is None:
        SECRET = load_secret()
    return SECRET


OAUTH = {
    "google": {
        "env": ("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET"),
        "auth": "https://accounts.google.com/o/oauth2/v2/auth",
        "scope": "openid email profile",
    },
    "meta": {
        "env": ("META_APP_ID", "META_APP_SECRET"),
        "auth": "https://www.facebook.com/v20.0/dialog/oauth",
        "scope": "public_profile,email",
    },
    "apple": {
        "env": ("APPLE_CLIENT_ID", "APPLE_CLIENT_SECRET"),
        "auth": "https://appleid.apple.com/auth/authorize",
        "scope": "name email",
    },
}


def provider_configured(provider):
    cid, csec = (os.environ.get(k, "").strip() for k in OAUTH[provider]["env"])
    return bool(cid and csec), cid, csec


# ---------- validators / password hashing ----------

def valid_email(email):
    return bool(EMAIL_RE.match(str(email or "").strip()))


def password_status(pw):
    pw = pw or ""
    return [{"id": i, "label": label, "short": short, "ok": bool(test(pw))}
            for i, label, short, test in PASSWORD_RULES]


def assert_password(pw):
    missing = [r for r in password_status(pw) if not r["ok"]]
    if missing:
        raise AuthError("Password needs " +
                        ", ".join(r["short"] for r in missing) + ".")


def hash_password(pw):
    """Tagged `algo$params$salt$hash` string. Prefers scrypt; falls back to
    PBKDF2-HMAC-SHA256 (600k rounds) where the platform lacks scrypt
    (e.g. macOS LibreSSL builds)."""
    salt = secrets.token_bytes(16).hex()
    if hasattr(hashlib, "scrypt"):
        dk = hashlib.scrypt(pw.encode(), salt=bytes.fromhex(salt),
                            n=SCRYPT_N, r=SCRYPT_R, p=SCRYPT_P,
                            dklen=DKLEN).hex()
        return f"scrypt${SCRYPT_N}${SCRYPT_R}${SCRYPT_P}${salt}${dk}"
    dk = hashlib.pbkdf2_hmac("sha256", pw.encode(), bytes.fromhex(salt),
                             600_000).hex()
    return f"pbkdf2$600000${salt}${dk}"


def verify_password(pw, stored):
    try:
        parts = (stored or "").split("$")
        if parts[0] == "scrypt" and hasattr(hashlib, "scrypt"):
            _a, n, r, p, salt, want = parts
            dk = hashlib.scrypt(pw.encode(), salt=bytes.fromhex(salt),
                                n=int(n), r=int(r), p=int(p),
                                dklen=len(bytes.fromhex(want))).hex()
            return hmac.compare_digest(dk, want)
        if parts[0] == "pbkdf2":
            _a, rounds, salt, want = parts
            dk = hashlib.pbkdf2_hmac("sha256", pw.encode(),
                                     bytes.fromhex(salt), int(rounds)).hex()
            return hmac.compare_digest(dk, want)
    except (ValueError, TypeError, IndexError):
        pass
    return False


# ---------- database ----------

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  pw_salt TEXT,
  pw_hash TEXT,
  provider TEXT NOT NULL DEFAULT 'email',
  provider_sub TEXT,
  created INTEGER NOT NULL,
  UNIQUE (provider, provider_sub)
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exp INTEGER NOT NULL,
  created INTEGER NOT NULL,
  ip TEXT,
  ua TEXT
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE TABLE IF NOT EXISTS reset_tokens (
  token_hash TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exp INTEGER NOT NULL,
  used INTEGER NOT NULL DEFAULT 0,
  created INTEGER NOT NULL
);
"""


def db():
    con = sqlite3.connect(DB_PATH)
    con.execute("PRAGMA foreign_keys = ON")
    con.executescript(SCHEMA)
    return con


def public_user(row):
    return {"email": row[0], "name": f"{row[1]} {row[2]}".strip(),
            "provider": row[3]}


def create_email_user(email, password, first_name, last_name):
    email = email.strip().lower()
    first_name, last_name = first_name.strip(), last_name.strip()
    if not valid_email(email):
        raise AuthError("Enter a valid email address.")
    if not first_name or not last_name:
        raise AuthError("Enter your first and last name.")
    assert_password(password)
    pw_hash = hash_password(password)  # tagged algo string; pw_salt unused
    con = db()
    try:
        con.execute(
            "INSERT INTO users (email, first_name, last_name, pw_hash,"
            " provider, created) VALUES (?,?,?,?,'email',?)",
            (email, first_name, last_name, pw_hash, int(time.time())))
        con.commit()
    except sqlite3.IntegrityError:
        raise AuthError("That email already has an account — sign in instead.")
    finally:
        con.close()
    row = get_user_by_email(email)
    return {"email": row[0], "name": f"{row[1]} {row[2]}".strip(),
            "first_name": row[1], "last_name": row[2], "provider": row[3]}


def get_user_by_email(email):
    con = db()
    try:
        row = con.execute(
            "SELECT email, first_name, last_name, provider, pw_salt, pw_hash"
            " FROM users WHERE email = ?", (email.strip().lower(),)).fetchone()
    finally:
        con.close()
    return row


def login_email_user(email, password):
    row = get_user_by_email(email)
    if not row or not row[5]:
        raise AuthError("No account for that email — create one first.", 401)
    if row[3] != "email":
        raise AuthError(f"That email signed up with {row[3]} — use that button.",
                        401)
    if not verify_password(password or "", row[5]):
        raise AuthError("Wrong password — try again.", 401)
    return {"email": row[0], "name": f"{row[1]} {row[2]}".strip(),
            "first_name": row[1], "last_name": row[2], "provider": row[3]}


def upsert_provider_user(provider, sub, email, first_name, last_name):
    email = (email or f"{provider}.{sub}@no-email.local").strip().lower()
    first_name = (first_name or "").strip() or email.split("@")[0]
    last_name = (last_name or "").strip()
    now = int(time.time())
    con = db()
    try:
        row = con.execute(
            "SELECT email, first_name, last_name, provider FROM users"
            " WHERE provider = ? AND provider_sub = ?", (provider, sub)).fetchone()
        if not row:
            same_email = con.execute(
                "SELECT id FROM users WHERE email = ?", (email,)).fetchone()
            if same_email:
                # Link: provider-verified email matches an existing account.
                con.execute(
                    "UPDATE users SET provider = ?, provider_sub = ? WHERE id = ?",
                    (provider, sub, same_email[0]))
            else:
                con.execute(
                    "INSERT INTO users (email, first_name, last_name, provider,"
                    " provider_sub, created) VALUES (?,?,?,?,?,?)",
                    (email, first_name, last_name, provider, sub, now))
            con.commit()
            row = con.execute(
                "SELECT email, first_name, last_name, provider FROM users"
                " WHERE provider = ? AND provider_sub = ?", (provider, sub)).fetchone()
    finally:
        con.close()
    return public_user(row)


def user_id_for_email(email):
    con = db()
    try:
        row = con.execute("SELECT id FROM users WHERE email = ?",
                          (email,)).fetchone()
    finally:
        con.close()
    return row[0] if row else None


def create_session(user_email, ip="", ua=""):
    uid = user_id_for_email(user_email)
    token = secrets.token_urlsafe(32)
    th = hashlib.sha256(token.encode()).hexdigest()
    exp = int(time.time()) + SESSION_DAYS * 86400
    con = db()
    try:
        con.execute("DELETE FROM sessions WHERE exp < ?", (int(time.time()),))
        con.execute("INSERT INTO sessions (token_hash, user_id, exp, created, ip, ua)"
                    " VALUES (?,?,?,?,?,?)", (th, uid, exp, int(time.time()), ip, ua))
        con.commit()
    finally:
        con.close()
    return token, exp


def session_user(token):
    if not token:
        return None
    th = hashlib.sha256(token.encode()).hexdigest()
    con = db()
    try:
        row = con.execute(
            "SELECT u.email, u.first_name, u.last_name, u.provider,"
            " u.created, s.exp"
            " FROM sessions s JOIN users u ON u.id = s.user_id"
            " WHERE s.token_hash = ?", (th,)).fetchone()
        if not row:
            return None
        if row[5] < int(time.time()):
            con.execute("DELETE FROM sessions WHERE token_hash = ?", (th,))
            con.commit()
            return None
    finally:
        con.close()
    return {"email": row[0], "name": f"{row[1]} {row[2]}".strip(),
            "first_name": row[1], "last_name": row[2], "provider": row[3],
            "created": row[4]}


def destroy_session(token):
    if not token:
        return
    th = hashlib.sha256(token.encode()).hexdigest()
    con = db()
    try:
        con.execute("DELETE FROM sessions WHERE token_hash = ?", (th,))
        con.commit()
    finally:
        con.close()


# ---------- OAuth state tokens (stateless, HMAC-signed) ----------

def sign_state(provider):
    payload = json.dumps({"p": provider, "n": secrets.token_hex(8),
                          "exp": int(time.time()) + 600}).encode()
    b = base64.urlsafe_b64encode(payload).decode()
    sig = hmac.new(secret(), b.encode(), hashlib.sha256).hexdigest()
    return f"{b}.{sig}"


def verify_state(token, provider):
    try:
        b, sig = token.split(".", 1)
        good = hmac.new(secret(), b.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(good, sig):
            return False
        payload = json.loads(base64.urlsafe_b64decode(b.encode()))
        return payload.get("p") == provider and payload.get("exp", 0) > time.time()
    except (ValueError, TypeError):
        return False


# ---------- tiny HTTP helpers ----------

def http_post_form(url, fields, timeout=12):
    data = urllib.parse.urlencode(fields).encode()
    req = urllib.request.Request(url, data=data,
                                 headers={"Content-Type":
                                          "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def http_get_json(url, headers=None, timeout=12):
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


# ---------- password change / reset ----------

def change_email_password(email, new_password):
    row = get_user_by_email(email)
    if not row or not row[5]:
        raise AuthError("No password on this account.", 400)
    if row[3] != "email":
        raise AuthError(f"That account uses {row[3]} sign-in — "
                        "there is no lab password to change.", 400)
    assert_password(new_password)
    con = db()
    try:
        con.execute("UPDATE users SET pw_hash = ? WHERE email = ?",
                    (hash_password(new_password), email.strip().lower()))
        con.commit()
    finally:
        con.close()


def send_reset_email(to_email, link):
    """Returns True if an email was actually sent (SMTP configured)."""
    host = os.environ.get("DUO_SMTP_HOST", "").strip()
    if not host:
        return False
    import smtplib
    from email.message import EmailMessage
    port = int(os.environ.get("DUO_SMTP_PORT", "587"))
    user = os.environ.get("DUO_SMTP_USER", "")
    sender = os.environ.get("DUO_SMTP_FROM", user)
    msg = EmailMessage()
    msg["Subject"] = "Fly Lab password reset"
    msg["From"] = sender
    msg["To"] = to_email
    msg.set_content(
        "Someone asked to reset the password for this Fly Lab account.\n\n"
        f"Set a new one here (30 minutes, single use):\n{link}\n\n"
        "If that was not you, ignore this email — the password is unchanged.")
    if os.environ.get("DUO_SMTP_SSL") == "1":
        conn = smtplib.SMTP_SSL(host, port, timeout=15)
    else:
        conn = smtplib.SMTP(host, port, timeout=15)
    with conn as s:
        if os.environ.get("DUO_SMTP_SSL") != "1":
            s.starttls()
        if user:
            s.login(user, os.environ.get("DUO_SMTP_PASS", ""))
        s.send_message(msg)
    return True


def request_password_reset(email, base_url):
    """Always succeeds silently (no account enumeration). Returns True when
    a token was issued (email account exists); without SMTP the link is
    printed to the server log as a workshop fallback (see AUTH.md)."""
    row = get_user_by_email(email)
    if not row or row[3] != "email":
        return False
    uid = user_id_for_email(row[0])
    token = secrets.token_urlsafe(32)
    th = hashlib.sha256(token.encode()).hexdigest()
    con = db()
    try:
        con.execute("DELETE FROM reset_tokens WHERE exp < ?",
                    (int(time.time()),))
        con.execute("INSERT INTO reset_tokens (token_hash, user_id, exp, created)"
                    " VALUES (?,?,?,?)",
                    (th, uid, int(time.time()) + 1800, int(time.time())))
        con.commit()
    finally:
        con.close()
    link = f"{base_url}/duo/reset.html?token={token}"
    try:
        sent = send_reset_email(row[0], link)
    except Exception as e:  # SMTP misconfig must not reveal the account
        sys.stderr.write(f"reset email failed for {row[0]}: {e}\n")
        sent = False
    if not sent:
        sys.stderr.write(f"RESET LINK for {row[0]} (no SMTP; see AUTH.md):\n"
                         f"  {link}\n")
    return True


def redeem_password_reset(token, new_password):
    th = hashlib.sha256((token or "").encode()).hexdigest()
    con = db()
    try:
        row = con.execute(
            "SELECT user_id, exp, used FROM reset_tokens WHERE token_hash = ?",
            (th,)).fetchone()
        if not row or row[2] or row[1] < int(time.time()):
            raise AuthError("This reset link is invalid or expired.", 400)
        assert_password(new_password)
        con.execute("UPDATE users SET pw_hash = ? WHERE id = ?",
                    (hash_password(new_password), row[0]))
        con.execute("UPDATE reset_tokens SET used = 1 WHERE token_hash = ?",
                    (th,))
        con.execute("DELETE FROM sessions WHERE user_id = ?", (row[0],))
        con.commit()
    finally:
        con.close()


# ---------- rate limiting (in-memory, per IP + route) ----------

ATTEMPTS = {}
RATE_WINDOW, RATE_MAX = 600, 20


def rate_ok(key):
    now = time.time()
    hits = [t for t in ATTEMPTS.get(key, []) if now - t < RATE_WINDOW]
    hits.append(now)
    ATTEMPTS[key] = hits
    return len(hits) <= RATE_MAX


# ---------- request handler ----------

class Handler(BaseHTTPRequestHandler):
    server_version = "DuoAuth/1"

    def log_message(self, fmt, *args):  # quieter logs
        sys.stderr.write(f"{self.address_string()} {fmt % args}\n")

    # -- helpers --
    def send_json(self, obj, status=200, extra_headers=None):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra_headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        try:
            n = int(self.headers.get("Content-Length", 0))
        except ValueError:
            n = 0
        if n <= 0 or n > 1_000_000:
            raise AuthError("Empty or oversized request.")
        try:
            return json.loads(self.rfile.read(n).decode())
        except (ValueError, UnicodeDecodeError):
            raise AuthError("Invalid JSON body.")

    def cookies(self):
        out = {}
        for part in (self.headers.get("Cookie") or "").split(";"):
            if "=" in part:
                k, v = part.strip().split("=", 1)
                out[k] = v
        return out

    def session_cookie(self, token, exp):
        cookie = (f"duo_session={token}; HttpOnly; SameSite=Lax; Path=/;"
                  f" Max-Age={SESSION_DAYS * 86400}")
        if os.environ.get("DUO_HTTPS") == "1":
            cookie += "; Secure"
        return {"Set-Cookie": cookie}

    def clear_cookie(self):
        return {"Set-Cookie": "duo_session=; HttpOnly; SameSite=Lax; Path=/;"
                             " Max-Age=0"}

    def current_user(self):
        return session_user(self.cookies().get("duo_session"))

    def base_url(self):
        host = self.headers.get("Host", "localhost:8123")
        scheme = "https" if os.environ.get("DUO_HTTPS") == "1" else "http"
        return f"{scheme}://{host}"

    # -- API routes --
    def route_api(self, method, path):
        if method == "GET" and path == "/api/me":
            user = self.current_user()
            if user:
                self.send_json({"user": user})
            else:
                self.send_json({"user": None}, status=401)
            return True
        if method == "GET" and path == "/api/config":
            self.send_json({"server": True, "providers": {
                p: provider_configured(p)[0] for p in OAUTH}})
            return True
        if method == "POST" and path in ("/api/signup", "/api/login"):
            if not rate_ok((self.address_string(), path)):
                self.send_json({"error": "Too many attempts — wait a few minutes."},
                               status=429)
                return True
            try:
                body = self.read_json()
                if path == "/api/signup":
                    user = create_email_user(
                        body.get("email", ""), body.get("password", ""),
                        body.get("first_name", ""), body.get("last_name", ""))
                else:
                    user = login_email_user(body.get("email", ""),
                                            body.get("password", ""))
            except AuthError as e:
                self.send_json({"error": str(e)}, status=e.status)
                return True
            token, _exp = create_session(
                user["email"], ip=self.address_string(),
                ua=self.headers.get("User-Agent", "")[:200])
            self.send_json({"user": user},
                           extra_headers=self.session_cookie(token, _exp))
            return True
        if method == "POST" and path == "/api/logout":
            destroy_session(self.cookies().get("duo_session"))
            self.send_json({"ok": True}, extra_headers=self.clear_cookie())
            return True
        if method == "POST" and path == "/api/change-password":
            user = self.current_user()
            if not user:
                self.send_json({"error": "Sign in first."}, status=401)
                return True
            try:
                body = self.read_json()
                change_email_password(user["email"],
                                      body.get("new_password", ""))
            except AuthError as e:
                self.send_json({"error": str(e)}, status=e.status)
                return True
            self.send_json({"ok": True})
            return True
        if method == "POST" and path == "/api/forgot-password":
            if not rate_ok((self.address_string(), path)):
                self.send_json({"error": "Too many attempts — wait a few minutes."},
                               status=429)
                return True
            try:
                body = self.read_json()
            except AuthError as e:
                self.send_json({"error": str(e)}, status=e.status)
                return True
            request_password_reset(body.get("email", ""), self.base_url())
            self.send_json({"ok": True})  # always: no enumeration
            return True
        if method == "POST" and path == "/api/reset-password":
            try:
                body = self.read_json()
                redeem_password_reset(body.get("token", ""),
                                      body.get("new_password", ""))
            except AuthError as e:
                self.send_json({"error": str(e)}, status=e.status)
                return True
            self.send_json({"ok": True})
            return True
        m = re.match(r"^/api/oauth/(\w+)/(start|callback)$", path)
        if method == "GET" and m:
            return self.route_oauth(m.group(1), m.group(2))
        return False

    # -- OAuth (authorization-code flow, server-side exchange) --
    def route_oauth(self, provider, stage):
        if provider not in OAUTH:
            self.send_json({"error": "Unknown provider."}, status=404)
            return True
        if stage == "start":
            ok, cid, _csec = provider_configured(provider)
            if not ok:
                self.send_json({"error": f"{provider} is not configured — set "
                                f"{OAUTH[provider]['env'][0]} and "
                                f"{OAUTH[provider]['env'][1]} (see AUTH.md)."},
                               status=501)
                return True
            redirect = f"{self.base_url()}/api/oauth/{provider}/callback"
            q = urllib.parse.urlencode({
                "client_id": cid, "redirect_uri": redirect,
                "response_type": "code", "scope": OAUTH[provider]["scope"],
                "state": sign_state(provider),
                **({"response_mode": "query"} if provider == "apple" else {}),
            })
            self.send_response(302)
            self.send_header("Location", f"{OAUTH[provider]['auth']}?{q}")
            self.end_headers()
            return True
        # callback
        qs = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        code = (qs.get("code") or [""])[0]
        state = (qs.get("state") or [""])[0]
        err = (qs.get("error") or [""])[0]
        if err or not code or not verify_state(state, provider):
            self.send_response(302)
            self.send_header("Location", "/duo/?login=cancelled")
            self.end_headers()
            return True
        try:
            user = self.exchange_code(provider, code)
        except AuthError as e:
            sys.stderr.write(f"oauth {provider} failed: {e}\n")
            self.send_response(302)
            self.send_header("Location", "/duo/?login=failed")
            self.end_headers()
            return True
        token, _exp = create_session(
            user["email"], ip=self.address_string(),
            ua=self.headers.get("User-Agent", "")[:200])
        self.send_response(302)
        for k, v in self.session_cookie(token, _exp).items():
            self.send_header(k, v)
        self.send_header("Location", "/duo/?login=ok")
        self.end_headers()
        return True

    def exchange_code(self, provider, code):
        _ok, cid, csec = provider_configured(provider)
        redirect = f"{self.base_url()}/api/oauth/{provider}/callback"
        try:
            if provider == "google":
                tok = http_post_form("https://oauth2.googleapis.com/token", {
                    "code": code, "client_id": cid, "client_secret": csec,
                    "redirect_uri": redirect,
                    "grant_type": "authorization_code"})
                me = http_get_json(
                    "https://openidconnect.googleapis.com/v1/userinfo",
                    {"Authorization": f"Bearer {tok['access_token']}"})
                if me.get("email_verified") is False:
                    raise AuthError("Google email not verified.")
                return upsert_provider_user(
                    "google", me["sub"], me.get("email"),
                    me.get("given_name", ""), me.get("family_name", ""))
            if provider == "meta":
                tok = http_get_json(
                    "https://graph.facebook.com/v20.0/oauth/access_token?" +
                    urllib.parse.urlencode({
                        "client_id": cid, "redirect_uri": redirect,
                        "client_secret": csec, "code": code}))
                me = http_get_json(
                    "https://graph.facebook.com/me?fields=id,name,email&" +
                    urllib.parse.urlencode(
                        {"access_token": tok["access_token"]}))
                first, _, last = (me.get("name") or "").partition(" ")
                return upsert_provider_user(
                    "meta", me["id"], me.get("email"), first, last)
            if provider == "apple":
                return self.exchange_apple(code, cid, csec, redirect)
        except KeyError as e:
            raise AuthError(f"Unexpected provider response (missing {e}).")
        except urllib.error.URLError as e:
            raise AuthError(f"Provider request failed ({e}).")
        raise AuthError("Unknown provider.")  # unreachable

    def exchange_apple(self, code, cid, csec, redirect):
        try:
            import jwt  # PyJWT — see requirements-oauth.txt
        except ImportError:
            raise AuthError("Apple sign-in needs PyJWT: "
                            "pip install -r requirements-oauth.txt")
        tok = http_post_form("https://appleid.apple.com/auth/token", {
            "client_id": cid, "client_secret": csec, "code": code,
            "grant_type": "authorization_code", "redirect_uri": redirect})
        jwks = http_get_json("https://appleid.apple.com/auth/keys")
        header = jwt.get_unverified_header(tok["id_token"])
        key = next((k for k in jwks["keys"] if k["kid"] == header["kid"]), None)
        if not key:
            raise AuthError("Apple key not found.")
        claims = jwt.decode(tok["id_token"],
                            jwt.algorithms.RSAAlgorithm.from_jwk(json.dumps(key)),
                            audience=cid, issuer="https://appleid.apple.com",
                            algorithms=["RS256"])
        if claims.get("email_verified") is False:
            raise AuthError("Apple email not verified.")
        return upsert_provider_user("apple", claims["sub"],
                                    claims.get("email"), "", "")

    # -- dispatch --
    def dispatch(self, method):
        path = urllib.parse.urlparse(self.path).path
        try:
            if path.startswith("/api/"):
                if self.route_api(method, path):
                    return
                self.send_json({"error": "Not found."}, status=404)
                return
            if method != "GET":
                self.send_json({"error": "Method not allowed."}, status=405)
                return
            self.serve_static(path)
        except BrokenPipeError:
            pass

    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    # -- static files (whole lab tree, safe join) --
    def serve_static(self, path):
        rel = urllib.parse.unquote(path.lstrip("/"))
        full = os.path.realpath(os.path.join(ROOT, rel))
        if full != ROOT and not full.startswith(ROOT + os.sep):
            self.send_json({"error": "Not found."}, status=404)
            return
        if os.path.isdir(full):
            full = os.path.join(full, "index.html")
        if not os.path.isfile(full) or full.endswith((".sqlite3", "-secret")):
            self.send_json({"error": "Not found."}, status=404)
            return
        ctype, _enc = mimetypes.guess_type(full)
        ctype = ctype or "application/octet-stream"
        if ctype.startswith("text/") or ctype in (
                "application/javascript", "application/json",
                "image/svg+xml"):
            ctype += "; charset=utf-8"
        try:
            with open(full, "rb") as f:
                body = f.read()
        except OSError:
            self.send_json({"error": "Not found."}, status=404)
            return
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)


def main(argv):
    port = int(argv[1]) if len(argv) > 1 else 8123
    db().close()  # create schema now so misconfig fails fast
    secret()  # load or generate the signing secret now
    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"Duo auth server on http://localhost:{port}/duo/  "
          f"(db: {DB_PATH})", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main(sys.argv)
