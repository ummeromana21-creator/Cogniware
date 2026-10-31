/* Duo story entry — page wiring: theme, auth modal, lab gate. */
"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const hero = $("hero"), modal = $("authModal");
  let mode = "signin";
  let pendingHref = null;

  // --- day/dusk (Feather Atlas var names, per lab convention) ---
  try {
    if (localStorage.getItem("duo.light") === "dusk") setLight("dusk");
  } catch {}
  function setLight(v) {
    hero.dataset.light = v;
    $("lightBtn").setAttribute("aria-pressed", String(v === "dusk"));
    try { localStorage.setItem("duo.light", v); } catch {}
  }
  $("lightBtn").addEventListener("click", () =>
    setLight(hero.dataset.light === "dusk" ? "day" : "dusk"));

  // --- auth modal ---
  function setMode(m) {
    mode = m;
    const signup = m === "signup";
    $("tabSignin").setAttribute("aria-selected", String(!signup));
    $("tabSignup").setAttribute("aria-selected", String(signup));
    $("nameRow").hidden = !signup;
    $("pass2Wrap").hidden = !signup;
    $("passReqs").hidden = !signup;
    $("authPass").setAttribute("autocomplete",
      signup ? "new-password" : "current-password");
    $("authSubmit").textContent = signup ? "Create account" : "Sign in";
    hideError();
  }
  function showError(msg) {
    const e = $("authError");
    e.textContent = msg; e.hidden = false;
  }
  function hideError() { $("authError").hidden = true; }

  function openModal(href) {
    pendingHref = href || null;
    hideError();
    if (!modal.open) modal.showModal();
    setTimeout(() => $("authEmail").focus(), 50);
  }
  $("signinBtn").addEventListener("click", () => { setMode("signin"); openModal(); });
  $("gateSignin").addEventListener("click", () => { setMode("signup"); openModal(); });
  $("tabSignin").addEventListener("click", () => setMode("signin"));
  $("tabSignup").addEventListener("click", () => setMode("signup"));
  modal.addEventListener("click", (e) => {
    if (e.target === modal) modal.close(); // backdrop click
  });

  async function withBusy(btn, fn) {
    const orig = btn.innerHTML;
    btn.disabled = true; btn.textContent = "One moment…";
    try { await fn(); } finally { btn.disabled = false; btn.innerHTML = orig; }
  }

  // --- live password checklist (single source: DuoAuth.passwordStatus) ---
  const reqItems = {};
  DuoAuth.passwordStatus("").forEach((r) => {
    const li = document.createElement("li");
    li.textContent = r.label;
    reqItems[r.id] = li;
    $("passReqs").appendChild(li);
  });
  function updateReqs() {
    DuoAuth.passwordStatus($("authPass").value).forEach((r) => {
      reqItems[r.id].classList.toggle("duo-met", r.ok);
    });
  }
  $("authPass").addEventListener("input", updateReqs);
  updateReqs();

  $("authForm").addEventListener("submit", (e) => {
    e.preventDefault();
    withBusy($("authSubmit"), async () => {
      try {
        const email = $("authEmail").value;
        const pass = $("authPass").value;
        if (mode === "signup") {
          if (pass !== $("authPass2").value)
            throw new Error("Passwords do not match.");
          await DuoAuth.signUpEmail(email, pass,
            $("authFirst").value, $("authLast").value);
        } else {
          await DuoAuth.signInEmail(email, pass);
        }
        modal.close();
        followPending();
      } catch (err) { showError(err.message || "Sign-in failed."); }
    });
  });

  const providers = [
    ["btnGoogle", "signInGoogle", "google", "Google"],
    ["btnMeta", "signInMeta", "meta", "Meta"],
    ["btnApple", "signInApple", "apple", "Apple"],
  ];
  for (const [id, fn, key, label] of providers) {
    $(id).addEventListener("click", () =>
      withBusy($(id), async () => {
        try {
          if (DuoAuth.mode() === "server" && !DuoAuth.providers()[key])
            throw new Error(`${label} isn't connected on this server yet — ` +
              "use email for now (setup: AUTH.md).");
          const both = ($("authFirst").value + " " + $("authLast").value).trim();
          await DuoAuth[fn]($("authEmail").value, both);
          modal.close(); followPending();
        }
        catch (err) { showError(err.message || "Sign-in failed."); }
      }));
  }
  $("signoutBtn").addEventListener("click", () => DuoAuth.signOut());

  function followPending() {
    if (pendingHref) {
      const href = pendingHref; pendingHref = null;
      location.href = href;
    }
  }

  // --- gate: locked CTAs intercept until signed in ---
  document.querySelectorAll("a[data-gated]").forEach((a) => {
    a.addEventListener("click", (e) => {
      if (!DuoAuth.currentUser()) {
        e.preventDefault();
        setMode("signin");
        openModal(a.getAttribute("href"));
      }
    });
  });
  $("openLabBtn").addEventListener("click", () => {
    if (DuoAuth.currentUser()) {
      $("labGate").scrollIntoView({ behavior: "smooth", block: "center" });
    } else {
      setMode("signup");
      openModal();
    }
  });

  function renderGate(user) {
    const in_ = !!user;
    $("userChip").hidden = !in_;
    $("signinBtn").hidden = in_;
    $("signoutBtn").hidden = !in_;
    if (in_) $("userName").textContent = user.name;
    $("gateLock").hidden = in_;
    $("gateOpen").hidden = !in_;
    document.querySelector(".duo-gatecard").classList.toggle("open", in_);
    $("gateTitle").textContent = in_
      ? `The lab is open, ${user.name.split(" ")[0]}`
      : "The lab is locked — for now";
    $("gateSub").textContent = in_
      ? `Signed in as ${user.email} (${user.provider}). Pick a bench.`
      : "One account opens every bench: the study pages and Ask the flies. Browsing this story stays free.";
    $("openLabLabel").textContent = in_ ? "Open the Lab · enter" : "Open the Lab · sign in";
    if (in_) $("gateWelcome").textContent =
      `Welcome${user.provider === "email" ? " back" : ""}, ${user.name} — the benches are open.`;
  }
  DuoAuth.onAuthChange(renderGate);
  renderGate(DuoAuth.currentUser());

  // Deep link from profile/reset pages: open the sign-in dialog on arrival.
  if (new URLSearchParams(location.search).get("signin") === "1") {
    history.replaceState(null, "", location.pathname);
    setMode("signin");
    openModal();
  }

  // Server-vs-demo note + landing after an OAuth redirect.
  DuoAuth.ready.then(({ mode, user, providers }) => {
    if (mode === "server") {
      const on = Object.keys(providers).filter((k) => providers[k]);
      $("demoNote").textContent = on.length
        ? `Connected to the lab auth server — real accounts. Live providers: ${on.join(", ")}.`
        : "Connected to the lab auth server — real accounts. Social providers aren't connected yet, so email it is.";
    }
    const res = new URLSearchParams(location.search).get("login");
    if (!res) return;
    history.replaceState(null, "", location.pathname);
    if (res === "ok" && user) {
      $("labGate").scrollIntoView({ block: "center" });
    } else if (res === "failed") {
      setMode("signin"); openModal();
      showError("Provider sign-in failed — try email instead.");
    }
  });
})();
