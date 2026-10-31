/* Duo reset page — request a link, redeem it for a new password. */
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

  const tok = new URLSearchParams(location.search).get("token");
  if (tok) {
    $("rsToken").value = tok;
    history.replaceState(null, "", location.pathname);
  }

  // Live password checklist (single source: DuoAuth.passwordStatus).
  const reqItems = {};
  DuoAuth.passwordStatus("").forEach((r) => {
    const li = document.createElement("li");
    li.textContent = r.label;
    reqItems[r.id] = li;
    $("rsReqs").appendChild(li);
  });
  $("rsNew").addEventListener("input", () => {
    DuoAuth.passwordStatus($("rsNew").value).forEach((r) => {
      reqItems[r.id].classList.toggle("duo-met", r.ok);
    });
  });

  $("forgotForm").addEventListener("submit", (e) => {
    e.preventDefault();
    $("fgError").hidden = true;
    $("fgOk").hidden = true;
    const btn = $("fgSubmit");
    btn.disabled = true;
    (async () => {
      try {
        await DuoAuth.forgotPassword($("fgEmail").value);
        $("fgOk").hidden = false;
      } catch (err) {
        const box = $("fgError");
        box.textContent = err.message || "Request failed.";
        box.hidden = false;
      } finally {
        btn.disabled = false;
      }
    })();
  });

  $("resetForm").addEventListener("submit", (e) => {
    e.preventDefault();
    $("rsError").hidden = true;
    $("rsOk").hidden = true;
    const btn = $("rsSubmit");
    btn.disabled = true;
    (async () => {
      try {
        if ($("rsNew").value !== $("rsConfirm").value)
          throw new Error("New passwords do not match.");
        await DuoAuth.resetPassword($("rsToken").value.trim(), $("rsNew").value);
        $("resetForm").reset();
        $("rsOk").hidden = false;
      } catch (err) {
        const box = $("rsError");
        box.textContent = err.message || "Reset failed.";
        box.hidden = false;
      } finally {
        btn.disabled = false;
      }
    })();
  });
})();
