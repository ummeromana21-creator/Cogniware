"""Maintained check for the duo story-entry page. Run from this folder: python3 check.py"""
import os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
fails = []


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (f" [{extra}]" if extra and not cond else ""))
    if not cond:
        fails.append(name)


html = open(os.path.join(HERE, "index.html")).read()
css = open(os.path.join(HERE, "style.css")).read()
app = open(os.path.join(HERE, "app.js")).read()
auth = open(os.path.join(HERE, "auth.js")).read()
srv = open(os.path.join(HERE, "server.py")).read()
md = open(os.path.join(HERE, "AUTH.md")).read()
prof = open(os.path.join(HERE, "profile.html")).read()
profjs = open(os.path.join(HERE, "profile.js")).read()
rst = open(os.path.join(HERE, "reset.html")).read()
rstjs = open(os.path.join(HERE, "reset.js")).read()

# refs resolve (page + lab deep links, no hotlinked lab CSS/JS)
for ref in ("style.css", "app.js", "auth.js", "server.py", "AUTH.md",
            "requirements-oauth.txt", ".gitignore", "profile.html",
            "profile.js", "reset.html", "reset.js", "../flies/index.html",
            "../study.html", "../library/index.html", "../ask/index.html"):
    check(f"ref {ref}", os.path.exists(os.path.join(HERE, ref)))
check("archived lab.html kept",
      os.path.exists(os.path.join(HERE, "..", "..", "archive", "lab.html")))
check("cache-busted assets",
      "style.css?v=" in html and "app.js?v=" in html and "auth.js?v=" in html)
check("no hotlinked lab css/js",
      "../library/style.css" not in html and "../ask/" not in
      "".join(re.findall(r'(?:href|src)="([^"]+\.(?:css|js))"', html)))

# ids wired between each html and its js
def id_used(js):
    return set(re.findall(r'(?:getElementById|\$\()\s*["\']([^"\']+)["\']', js))


def id_defined(page):
    return set(re.findall(r'id="([^"]+)"', page))


for jsname, js, page in (("app.js", app, html), ("profile.js", profjs, prof),
                         ("reset.js", rstjs, rst)):
    missing = id_used(js) - id_defined(page)
    check(f"{jsname} ids exist in html", not missing, sorted(missing)[:5])

# protagonists + story content
for pin in ("Maxime", "Hagrid", "BANC", "Male CNS", "Open the Lab"):
    check(f"content {pin}", pin in html)

# gated lab links (nav + footer + cards),incl. ask prefill handoff
check("gated links present", html.count("data-gated") >= 5, html.count("data-gated"))
check("ask prefill handoff", "../ask/?q=" in html)
check("no watch deep link without id", "watch/?id=" not in html)

# auth UI
for pin in ("authModal", "authForm", "authEmail", "authPass", "authSubmit",
            "btnGoogle", "btnMeta", "btnApple", "tabSignin", "tabSignup",
            "authFirst", "authLast", "passReqs", "nameRow"):
    check(f"auth ui {pin}", f'id="{pin}"' in html)
check("first/last name labels", "First name" in html and "Last name" in html)
check("no single display-name field", 'id="authName"' not in html)
for fn in ("signUpEmail", "signInEmail", "signInGoogle", "signInMeta",
           "signInApple", "signOut", "currentUser", "AUTH_CONFIG",
           "changePassword", "forgotPassword", "resetPassword"):
    check(f"auth api {fn}", fn in auth)
check("provider demo requires form email",
      "in the form first" in auth and "demo.local" not in auth)
check("provider handler passes form values",
      'DuoAuth[fn]($("authEmail").value' in app
      and 'authFirst' in app and 'authLast' in app)
check("password rules enforced",
      "PASSWORD_RULES" in auth and "passwordStatus" in auth
      and "uppercase" in auth and "symbol" in auth)
check("live checklist wired",
      "passwordStatus" in app and "duo-met" in app and "duo-met" in css)

# server mode: frontend detects /api, server implements it
for pin in ("/api/me", "/api/signup", "/api/login", "/api/logout",
            "/api/oauth/", "/api/change-password", "/api/forgot-password",
            "/api/reset-password", "credentials"):
    check(f"frontend api {pin}", pin in auth)
check("ready/mode facade",
      "ready" in auth and "mode:" in auth and "DuoAuth.ready" in app)
check("unconfigured-provider guard", "AUTH.md" in app)
for pin in ("/api/signup", "/api/login", "/api/logout", "/api/me",
            "/api/config", "/api/change-password", "/api/forgot-password",
            "/api/reset-password", "CREATE TABLE IF NOT EXISTS users",
            "CREATE TABLE IF NOT EXISTS sessions",
            "CREATE TABLE IF NOT EXISTS reset_tokens",
            "scrypt", "pbkdf2", "HttpOnly", "SameSite", "RATE_MAX",
            "realpath", "smtplib"):
    check(f"server {pin}", pin in srv)
check("server never serves secrets",
      ".sqlite3" in srv and "-secret" in srv)
check("apple jwt import guarded",
      "import jwt" in srv and "try:" in srv.split("import jwt")[0][-200:])
check("auth docs", "DUO_SECRET" in md and "/api/oauth/" in md
      and "GOOGLE_CLIENT_ID" in md and "META_APP_ID" in md
      and "APPLE_CLIENT_ID" in md and "DUO_SMTP_HOST" in md)

# profile + reset pages
check("profile cache-busted", "style.css?v=" in prof and "auth.js?v=" in prof
      and "profile.js?v=" in prof)
check("reset cache-busted", "style.css?v=" in rst and "auth.js?v=" in rst
      and "reset.js?v=" in rst)
check("profile change collapsed one-liner", 'id="chToggleRow"' in prof
      and 'aria-expanded="false"' in prof and 'id="chCurrent"' not in prof)
check("profile change single-arg call",
      'DuoAuth.changePassword($("chNew").value)' in profjs)
check("change needs no current password",
      '"current_password"' not in srv and "current_password" not in auth)
for pin in ("changeForm", "chToggle", "chNew", "chConfirm", "chReqs",
            "signoutBtn", "kvEmail", "providerNote", "disclaimer",
            "lockedPanel"):
    check(f"profile ui {pin}", f'id="{pin}"' in prof)
for pin in ("forgotForm", "fgEmail", "resetForm", "rsToken", "rsNew",
            "rsConfirm", "rsReqs"):
    check(f"reset ui {pin}", f'id="{pin}"' in rst)
check("profile linked", 'href="profile.html"' in html)
check("disclaimer section", 'id="disclaimer"' in prof
      and "workshop and education" in prof)
for cite in ("10.13140/RG.2.2.35928.67844", "Dorkenwald", "Ito et al. 2014",
             "Court et al. 2020", "flywire.ai/tos", "Hallem",
             "Dahanukar", "Soderlund", "157,789", "166,700"):
    check(f"citation {cite}", cite in prof)

# Feather Atlas tokens copied (day + dusk), duo-* namespace only
for tok in ("--bg:#f6f1e7", "--sage:#4d5a3d", "--bg:#14170f", "--sage:#a9b98a",
            "Newsreader", "Nunito Sans"):
    check(f"style token {tok}", tok in css)
check("dusk scoping", '.duo-hero[data-light="dusk"]' in css)
stray = sorted(set(re.findall(r"\.(top|card|hero|rail|parttip|lbl)\b", css)))
check("no unprefixed lab layout classes", not stray, stray[:5])

# taste guardrails: no gradient headline, no purple accent
check("no gradient headline", "bg-clip-text" not in css and "bg-clip-text" not in html)
check("no purple accent", not re.search(r"#(7c3aed|8b5cf6|6d28d9|a78bfa)", css, re.I))

if shutil.which("node"):
    for f in ("app.js", "auth.js", "profile.js", "reset.js"):
        r = subprocess.run(["node", "--check", os.path.join(HERE, f)], capture_output=True)
        check(f"{f} syntax", r.returncode == 0, r.stderr.decode()[:200])
else:
    print("SKIP js syntax (no node)")

# builtin compile(): no bytecode cache writes (sandbox-safe, unlike py_compile)
try:
    compile(srv, "server.py", "exec")
    check("server.py compiles", True)
except SyntaxError as e:
    check("server.py compiles", False, str(e))

print()
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"FAILURES: {fails}")
sys.exit(1 if fails else 0)
