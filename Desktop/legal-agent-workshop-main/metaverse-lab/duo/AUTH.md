# Duo auth server — real accounts for the Hagrid & Maxime page

The story page (`index.html`) signs users in two ways:

| Mode | When | Where accounts live |
|---|---|---|
| **Server** (real) | `server.py` is running | SQLite + scrypt, cookie sessions |
| **Demo** (fallback) | plain static hosting / `file://` | this browser's localStorage only |

The dialog tells the visitor which mode is active. Email + password works
in both; Google / Meta / Apple work on the server once configured,
otherwise they stay in clearly-labelled demo mode.

## Run it (no dependencies)

```bash
cd metaverse-lab/duo
python3 server.py           # serves the whole lab on http://localhost:8123
```

Then open http://localhost:8123/duo/ in a browser. Pass a port to change
it: `python3 server.py 8123`. This replaces `python3 -m http.server` — the
auth server serves the same static files plus `/api/*` on one origin.

First run creates two files next to `server.py` (both git-ignored):

- `duo-auth.sqlite3` — users + sessions
- `.duo-secret` (mode 0600) — session/OAuth signing secret

Set `DUO_SECRET` in production instead of relying on the file, and point
`DUO_DB` elsewhere to move the database.

## What the server does

- `POST /api/signup` / `/api/login` — validates email, first + last name,
  and the five password rules (8+, upper, lower, digit, symbol), then
  hashes with a per-user salt: scrypt (`n=2**14, r=8, p=1`) where the
  platform provides it, else PBKDF2-HMAC-SHA256 at 600k rounds (the
  stored string is algorithm-tagged, so either verifies anywhere).
- Cookie sessions: random 256-bit tokens, SHA-256 in the DB, `HttpOnly;
  SameSite=Lax`, 30-day expiry, idle pruning.
- `POST /api/change-password` — signed-in email accounts only; checks the
  five rules for the new one. No current-password check (re-auth planned),
  so anyone with an open session can change it.
- `POST /api/forgot-password` + `POST /api/reset-password` — single-use
  30-minute reset tokens (SHA-256 in the DB); redeeming one changes the
  hash and kills all sessions. Forgot always answers `{ok: true}` so
  addresses can't be probed. Needs SMTP to actually email (below).
- Rate limiting: 20 signup/login/forgot attempts per IP per 10 minutes
  (429 after).

## Outgoing email (password resets)

Set these for real reset emails (any SMTP provider works):

```bash
export DUO_SMTP_HOST=smtp.example.com DUO_SMTP_PORT=587
export DUO_SMTP_USER=lab@example.com DUO_SMTP_PASS=...
export DUO_SMTP_FROM="Fly Lab <lab@example.com>"
# export DUO_SMTP_SSL=1   # only for implicit-TLS ports like 465
```

Without `DUO_SMTP_HOST`, reset links print to the server log instead —
handy for the workshop (read the terminal), not for production.
- Static files served from the repo's `metaverse-lab/` root with safe path
  joins; the database and secret files are never served.

## Social login (Google / Meta / Apple)

Each provider needs an app registration with this redirect URI
(adjust the port if you changed it):

    http://localhost:8123/api/oauth/<google|meta|apple>/callback

Then set the matching env vars before starting the server:

```bash
export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...
export META_APP_ID=... META_APP_SECRET=...
export APPLE_CLIENT_ID=... APPLE_CLIENT_SECRET=...
```

- **Google / Meta**: stdlib-only; the server exchanges the code and reads
  the verified email from the provider's userinfo endpoint.
- **Apple**: needs one extra package to verify Apple's `id_token`:
  `pip install -r requirements-oauth.txt`. `APPLE_CLIENT_SECRET` is the
  ES256 JWT Apple documents for "Sign in with Apple" (generate it from
  your Team ID + Key ID + private key).

Without these vars, the provider buttons show a polite "not connected
yet" message in server mode (the email flow is unaffected), and the
`/api/config` endpoint reports which providers are live so the page can
say so.

Provider accounts link to an existing same-email account only when the
provider confirms the email address as verified.

## Production notes

- Terminate TLS in front (Caddy / nginx / Cloudflare) and set `DUO_HTTPS=1`
  so cookies get the `Secure` flag.
- Keep `DUO_SECRET` in the environment (or a secret manager), back up the
  SQLite file, and run behind a firewall — this is a workshop-grade server,
  not a hardened multi-tenant IdP.
