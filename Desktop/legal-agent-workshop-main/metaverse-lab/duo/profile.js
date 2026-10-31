/* Duo profile page — account details, sign out, change password. */
"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const hero = $("hero");

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

  $("lockedSignin").addEventListener("click", () => {
    location.href = "index.html?signin=1";
  });
  $("chToggle").addEventListener("click", () => {
    const form = $("changeForm");
    form.hidden = !form.hidden;
    $("chToggle").setAttribute("aria-expanded", String(!form.hidden));
    if (!form.hidden) $("chNew").focus();
  });
  $("signoutBtn").addEventListener("click", async () => {
    await DuoAuth.signOut();
    location.href = "./";
  });

  // Live password checklist (single source: DuoAuth.passwordStatus).
  const reqItems = {};
  DuoAuth.passwordStatus("").forEach((r) => {
    const li = document.createElement("li");
    li.textContent = r.label;
    reqItems[r.id] = li;
    $("chReqs").appendChild(li);
  });
  $("chNew").addEventListener("input", () => {
    DuoAuth.passwordStatus($("chNew").value).forEach((r) => {
      reqItems[r.id].classList.toggle("duo-met", r.ok);
    });
  });

  $("changeForm").addEventListener("submit", (e) => {
    e.preventDefault();
    $("chError").hidden = true;
    $("chOk").hidden = true;
    const btn = $("chSubmit");
    btn.disabled = true;
    (async () => {
      try {
        if ($("chNew").value !== $("chConfirm").value)
          throw new Error("New passwords do not match.");
        await DuoAuth.changePassword($("chNew").value);
        $("changeForm").reset();
        $("chOk").hidden = false;
      } catch (err) {
        const box = $("chError");
        box.textContent = err.message || "Password change failed.";
        box.hidden = false;
      } finally {
        btn.disabled = false;
      }
    })();
  });

  DuoAuth.ready.then(({ mode, user }) => {
    if (!user) {
      $("lockedPanel").hidden = false;
      return;
    }
    $("profileBody").hidden = false;
    $("pfName").textContent = user.name || "Lab member";
    $("kvEmail").textContent = user.email || "—";
    const prov = { email: "Email + password", google: "Google",
      meta: "Meta", apple: "Apple" }[user.provider] || user.provider;
    $("kvProvider").textContent = prov;
    $("kvMode").textContent = mode === "server"
      ? "Server account (salted hash + cookie session)"
      : "Demo account (this browser only)";
    $("kvSince").textContent = user.created
      ? new Date(user.created * 1000).toLocaleDateString(undefined,
        { year: "numeric", month: "long", day: "numeric" })
      : "—";
    if (user.provider === "email") {
      $("chToggleRow").hidden = false;
    } else {
      $("providerName").textContent = prov;
      $("providerNote").hidden = false;
    }
  });
})();
