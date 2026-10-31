/* DuoAuth — server-side accounts with a clearly-labelled demo fallback.
 *
 * When the duo auth server is reachable (same origin, /api/*), all auth
 * runs there: scrypt-hashed passwords in SQLite, cookie sessions. The
 * provider buttons redirect through the server's OAuth flow.
 *
 * With no server (e.g. plain static hosting or file://), it falls back to
 * demo mode: accounts live in this browser's localStorage. The page says
 * which mode is active. See AUTH.md to run the server.
 */
"use strict";

const AUTH_CONFIG = {
  apiBase: "", // same-origin api; set to "http://localhost:8123" if needed
  sessionDays: 30, // local demo sessions only; server sets its own expiry
};

const DuoAuth = (() => {
  const USERS_KEY = "duo.users.v1";
  const SESSION_KEY = "duo.session.v1";
  const listeners = new Set();
  let mode = "detecting"; // detecting -> server | local
  let cached = null;
  let providerFlags = { google: false, meta: false, apple: false };

  const validEmail = (e) =>
    /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(e || "").trim());

  // Same five rules the server enforces; the server re-validates.
  const PASSWORD_RULES = [
    { id: "len", label: "At least 8 characters",
      test: (p) => p.length >= 8 },
    { id: "upper", label: "An uppercase letter (A–Z)",
      test: (p) => /[A-Z]/.test(p) },
    { id: "lower", label: "A lowercase letter (a–z)",
      test: (p) => /[a-z]/.test(p) },
    { id: "digit", label: "A number (0–9)", test: (p) => /\d/.test(p) },
    { id: "sym", label: "A symbol (!@#$…)",
      test: (p) => /[^A-Za-z0-9]/.test(p) },
  ];

  function passwordStatus(pw) {
    pw = pw || "";
    return PASSWORD_RULES.map((r) => ({ ...r, ok: !!r.test(pw) }));
  }

  function assertPasswordLocal(pw) {
    const missing = passwordStatus(pw).filter((r) => !r.ok);
    if (missing.length)
      throw new Error("Password needs " +
        missing.map((r) => r.label.charAt(0).toLowerCase() +
          r.label.slice(1)).join(", ") + ".");
  }

  function currentUser() { return cached; }
  function onAuthChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  }
  function emit() {
    listeners.forEach((fn) => { try { fn(cached); } catch {} });
    return cached;
  }

  /* ---------------- local demo engine ---------------- */

  const readUsers = () => {
    try { return JSON.parse(localStorage.getItem(USERS_KEY)) || {}; }
    catch { return {}; }
  };
  const writeUsers = (u) => localStorage.setItem(USERS_KEY, JSON.stringify(u));

  const randomSalt = () => {
    const b = new Uint8Array(16);
    (crypto.getRandomValues ? crypto.getRandomValues(b)
      : b.map(() => Math.floor(Math.random() * 256)));
    return Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  };

  async function hashPassword(password, salt) {
    const msg = salt + "::" + password;
    if (crypto.subtle && crypto.subtle.digest) {
      const d = await crypto.subtle.digest(
        "SHA-256", new TextEncoder().encode(msg));
      return "s256:" + Array.from(new Uint8Array(d),
        (x) => x.toString(16).padStart(2, "0")).join("");
    }
    let h = 0x811c9dc5;
    for (let i = 0; i < msg.length; i++) {
      h ^= msg.charCodeAt(i);
      h = Math.imul(h, 0x01000193) >>> 0;
    }
    return "fnv1a:" + h.toString(16);
  }

  function localSession() {
    try {
      const s = JSON.parse(localStorage.getItem(SESSION_KEY));
      if (!s || !s.email || Date.now() > s.exp) {
        if (s) localStorage.removeItem(SESSION_KEY);
        return null;
      }
      return s;
    } catch { return null; }
  }

  const local = {
    async signUpEmail(email, password, firstName, lastName) {
      email = String(email || "").trim().toLowerCase();
      firstName = String(firstName || "").trim();
      lastName = String(lastName || "").trim();
      if (!validEmail(email)) throw new Error("Enter a valid email address.");
      if (!firstName || !lastName)
        throw new Error("Enter your first and last name.");
      assertPasswordLocal(password);
      const users = readUsers();
      if (users[email])
        throw new Error("That email already has an account — sign in instead.");
      const salt = randomSalt();
      users[email] = {
        name: `${firstName} ${lastName}`, firstName, lastName,
        salt, hash: await hashPassword(password, salt),
        provider: "email", created: Date.now(),
      };
      writeUsers(users);
      cached = { email, name: users[email].name, provider: "email",
        exp: Date.now() + AUTH_CONFIG.sessionDays * 864e5 };
      localStorage.setItem(SESSION_KEY, JSON.stringify(cached));
      return emit();
    },

    async signInEmail(email, password) {
      email = String(email || "").trim().toLowerCase();
      if (!validEmail(email)) throw new Error("Enter a valid email address.");
      const acct = readUsers()[email];
      if (!acct || !acct.hash)
        throw new Error("No account for that email — create one first.");
      if (acct.provider !== "email")
        throw new Error(`That email signed up with ${acct.provider} — use that button.`);
      const hash = await hashPassword(password || "", acct.salt);
      if (hash !== acct.hash) throw new Error("Wrong password — try again.");
      cached = { email, name: acct.name, provider: "email",
        exp: Date.now() + AUTH_CONFIG.sessionDays * 864e5 };
      localStorage.setItem(SESSION_KEY, JSON.stringify(cached));
      return emit();
    },

    async signInProvider(provider, email, name) {
      email = String(email || "").trim().toLowerCase();
      if (!validEmail(email))
        throw new Error("Enter your email in the form first, then pick a provider.");
      const users = readUsers();
      const acct = users[email];
      if (acct && acct.provider !== provider && !acct.demo)
        throw new Error(`That email signed up with ${acct.provider} — use that button.`);
      if (!acct) {
        users[email] = { name: (name || "").trim() || email.split("@")[0],
          salt: "", hash: "", provider, created: Date.now(), demo: true };
        writeUsers(users);
      }
      cached = { email, name: users[email].name, provider,
        exp: Date.now() + AUTH_CONFIG.sessionDays * 864e5 };
      localStorage.setItem(SESSION_KEY, JSON.stringify(cached));
      return emit();
    },

    async changePassword(next) {
      if (!cached) throw new Error("Sign in first.");
      const users = readUsers();
      const acct = users[cached.email];
      if (!acct || !acct.hash)
        throw new Error("This account has no password to change.");
      if (acct.provider !== "email")
        throw new Error(`This account uses ${acct.provider} sign-in — ` +
          "there is no lab password to change.");
      assertPasswordLocal(next);
      const salt = randomSalt();
      acct.salt = salt;
      acct.hash = await hashPassword(next, salt);
      writeUsers(users);
      return true;
    },

    async forgotPassword() {
      throw new Error("Password reset needs the lab server (it sends the " +
        "email). You are in demo mode — if you are signed in, use Change " +
        "password instead.");
    },

    async resetPassword() {
      throw new Error("Reset links come from the lab server, which is not " +
        "running. You are in demo mode — use Change password while signed in.");
    },

    async signOut() {
      localStorage.removeItem(SESSION_KEY);
      cached = null;
      return emit();
    },
  };

  /* ---------------- server engine ---------------- */

  async function api(path, opts = {}) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), opts.timeout || 8000);
    try {
      const r = await fetch(AUTH_CONFIG.apiBase + path, {
        method: opts.method || "GET",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: opts.body ? JSON.stringify(opts.body) : undefined,
        signal: ctrl.signal,
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(data.error || `Request failed (${r.status}).`);
      return data;
    } finally {
      clearTimeout(timer);
    }
  }

  const server = {
    async signUpEmail(email, password, firstName, lastName) {
      const data = await api("/api/signup", { method: "POST",
        body: { email, password, first_name: firstName, last_name: lastName } });
      cached = data.user;
      return emit();
    },
    async signInEmail(email, password) {
      const data = await api("/api/login", { method: "POST",
        body: { email, password } });
      cached = data.user;
      return emit();
    },
    async signInProvider(provider) {
      // Full-page redirect; the callback lands back on /duo/ signed in.
      location.href = AUTH_CONFIG.apiBase + `/api/oauth/${provider}/start`;
      return new Promise(() => {}); // never resolves; page navigates away
    },
    async changePassword(next) {
      await api("/api/change-password", { method: "POST",
        body: { new_password: next } });
      return true;
    },
    async forgotPassword(email) {
      await api("/api/forgot-password", { method: "POST", body: { email } });
      return true;
    },
    async resetPassword(token, next) {
      await api("/api/reset-password", { method: "POST",
        body: { token, new_password: next } });
      return true;
    },
    async signOut() {
      try { await api("/api/logout", { method: "POST" }); } catch {}
      cached = null;
      return emit();
    },
  };

  /* ---------------- facade: detect server, else demo ---------------- */

  const ready = (async () => {
    try {
      const data = await api("/api/me", { timeout: 2000 });
      mode = "server";
      cached = data.user || null;
      try {
        const cfg = await api("/api/config", { timeout: 2000 });
        if (cfg.providers) providerFlags = cfg.providers;
      } catch {}
    } catch {
      mode = "local";
      cached = localSession();
    }
    emit();
    return { mode, user: cached, providers: providerFlags };
  })();

  const engine = () => (mode === "server" ? server : local);

  return {
    ready,
    mode: () => mode,
    providers: () => ({ ...providerFlags }),
    currentUser, onAuthChange, passwordStatus,
    signUpEmail: (...a) => engine().signUpEmail(...a),
    signInEmail: (...a) => engine().signInEmail(...a),
    changePassword: (...a) => engine().changePassword(...a),
    forgotPassword: (...a) => engine().forgotPassword(...a),
    resetPassword: (...a) => engine().resetPassword(...a),
    signInProvider: (p, ...a) =>
      (mode === "server" ? server.signInProvider(p) : local.signInProvider(p, ...a)),
    signInGoogle: (...a) => DuoAuth.signInProvider("google", ...a),
    signInMeta: (...a) => DuoAuth.signInProvider("meta", ...a),
    signInApple: (...a) => DuoAuth.signInProvider("apple", ...a),
    signOut: (...a) => engine().signOut(...a),
    config: AUTH_CONFIG,
  };
})();

window.DuoAuth = DuoAuth;
