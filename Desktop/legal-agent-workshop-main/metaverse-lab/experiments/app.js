// Experiment Atlas: filterable grid over build.py's data.json. No invention:
// every string on a card comes from the JSON, which comes from lab files.
const KINDS = ["all", "drug", "model check", "comparison", "new sense", "mutant"];
const REALS = ["all", "already known", "partly known", "not yet tested", "can't test yet"];
const LABS = [["all", "All"], ["run", "Run"], ["ready", "Ready to run"],
  ["needs_model", "Needs a model change"], ["cant_test", "Can't test yet"]];
const LAB_LABEL = { run: "Run", ready: "Ready to run", needs_model: "Needs a model change", cant_test: "Can't test yet" };
const LAB_PILL = { run: "p-run", ready: "p-ready", needs_model: "p-needs", cant_test: "p-cant" };
const KIND_SHORT = { "drug": "drug", "model check": "check", "comparison": "compare", "new sense": "sense", "mutant": "mutant" };
const state = { q: "", kind: "all", real: "all", lab: "all", sort: "name" };
let CARDS = [], GENERATED = "";

const $ = id => document.getElementById(id);
const esc = s => String(s == null ? "" : s).replace(/[&<>"']/g,
  c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const hypText = c => c.hypothesis ? [c.hypothesis.text, c.hypothesis.given, c.hypothesis.falsified].join(" ") : "";
const hay = c => [c.title, c.kind, c.sentence, c.claim || "", c.real, c.real_source,
  c.target || "", (c.behavior || []).join(" "), hypText(c)].join(" ").toLowerCase();
const labRank = s => ({ run: 0, ready: 1, needs_model: 2, cant_test: 3 }[s] ?? 9);
function sortKey(c) {
  if (state.sort === "kind") return c.kind + "\0" + c.title.toLowerCase();
  if (state.sort === "lab") return labRank(c.lab.status) + "\0" + c.title.toLowerCase();
  return c.title.toLowerCase();
}
const filtered = () => CARDS
  .filter(c => state.kind === "all" || c.kind === state.kind)
  .filter(c => state.real === "all" || c.real === state.real)
  .filter(c => state.lab === "all" || c.lab.status === state.lab)
  .filter(c => !state.q || hay(c).includes(state.q))
  .sort((a, b) => { const ka = sortKey(a), kb = sortKey(b); return ka < kb ? -1 : ka > kb ? 1 : 0; });

function verdictPill(v) {
  const cls = v === "SUPPORTED" ? "v-sup" : v === "NOT SUPPORTED" ? "v-not"
    : v === "MIXED" ? "v-mix" : "v-cant";
  return `<span class="verdict ${cls}">${esc(v)}</span>`;
}

function labBlock(c) {
  const lab = c.lab, st = lab.status;
  let inner = `<span class="pill ${LAB_PILL[st]}">${LAB_LABEL[st]}</span>`;
  if (st === "run" && lab.record) {
    const r = lab.record;
    inner += verdictPill(r.verdict);
    if (r.kind === "notebook" || r.id) {
      inner += `<a class="runlink" href="../ask/watch/?id=${encodeURIComponent(r.id)}">replay&nbsp;→</a>`;
      inner += `<span class="rec-title">${esc(r.title)} <span class="rec-date">${esc(r.date || "")}</span></span>`;
      if (r.story) inner += `<p class="story">${esc(r.story)}</p>`;
    } else {
      inner += `<span class="rec-title">${esc(r.title)}</span>`;
      if (r.note) inner += `<p class="benchnote">${esc(r.note)}</p>`;
    }
  } else if (st === "ready" && lab.ask) {
    inner += `<a class="asklink" href="../study.html?q=${encodeURIComponent(lab.ask)}">Run it in Ask the flies&nbsp;→</a>`;
  } else if (st === "ready" && lab.note) {
    inner += `<p class="benchnote">${esc(lab.note)}</p>`;
  } else if ((st === "needs_model" || st === "cant_test") && lab.why) {
    inner += `<p class="why">${esc(lab.why)}</p>`;
  }
  return `<div class="rlab">${inner}</div>`;
}

function figure(c) {
  if (c.kind === "drug") {
    const cap = c.formula ? `<figcaption>${esc(c.formula)}</figcaption>` : ``;
    return c.structure
      ? `<figure><img src="${esc(c.structure)}" alt="2D structure of ${esc(c.title)}" loading="lazy" decoding="async">${cap}</figure>`
      : `<figure><span class="nosvg">structure not found</span>${cap}</figure>`;
  }
  const svg = (c.schematic || "").replace("<svg ", '<svg class="schematic" ');
  return `<figure>${svg}</figure>`;
}

function render() {
  const list = filtered();
  const grid = $("grid");
  grid.innerHTML = list.length ? list.map(c => `
    <article class="card" data-key="${esc(c.key)}" tabindex="0" role="link" aria-label="${esc(c.title)}, open detail view">
      ${figure(c)}
      <div class="body">
        <div class="hd"><h2>${esc(c.title)}</h2><span class="tag">${esc(KIND_SHORT[c.kind] || c.kind)}</span></div>
        <p class="sentence">${esc(c.sentence)}</p>
        <div class="rows">
          <div class="row"><span class="k">Real flies</span>
            <span class="v">${esc(cap(c.real))}<span class="src">${esc(c.real_source)}</span></span></div>
          <div class="row"><span class="k">Our lab</span><span class="v">${labBlock(c)}</span></div>
        </div>
      </div>
    </article>`).join("")
    : `<p class="empty">No experiments match these filters. Try clearing the search.</p>`;
  $("count").textContent = `${list.length} of ${CARDS.length} experiments`;
  document.querySelectorAll("#kinds button").forEach(b => b.classList.toggle("on", b.dataset.v === state.kind));
  document.querySelectorAll("#reals button").forEach(b => b.classList.toggle("on", b.dataset.v === state.real));
  document.querySelectorAll("#labs button").forEach(b => b.classList.toggle("on", b.dataset.v === state.lab));
  grid.querySelectorAll(".card").forEach(el => {
    el.addEventListener("click", e => {
      if (e.target.closest("a")) return;  // Run-it / replay links act alone
      openDetail(el.dataset.key);
    });
    el.addEventListener("keydown", e => {
      if ((e.key === "Enter" || e.key === " ") && !e.target.closest("a")) {
        e.preventDefault();
        openDetail(el.dataset.key);
      }
    });
  });
}

// ---------- detail view ----------
const byKey = k => CARDS.find(c => c.key === k);

function detailFigure(c) {
  if (c.kind === "drug") {
    const cap = c.formula ? `<p class="formula">${esc(c.formula)}</p>` : ``;
    return (c.structure
      ? `<img src="${esc(c.structure)}" alt="2D structure of ${esc(c.title)}">`
      : `<span class="nosvg">structure not found — neither the pharmacology file nor PubChem had it</span>`) + cap;
  }
  return (c.schematic || "").replace("<svg ", '<svg class="schematic" ');
}

function detailLab(c) {
  const lab = c.lab, st = lab.status;
  let h = `<div class="drun"><span class="pill ${LAB_PILL[st]}">${LAB_LABEL[st]}</span>`;
  if (st === "run" && lab.record) {
    const r = lab.record;
    h += verdictPill(r.verdict) + `</div>`;
    h += `<div class="traits">`;
    h += `<div class="trait"><span class="k">Record</span><span class="v">${esc(r.title)}</span></div>`;
    if (r.date) h += `<div class="trait"><span class="k">Date</span><span class="v">${esc(r.date)}</span></div>`;
    if (r.measure) h += `<div class="trait"><span class="k">Measure</span><span class="v">${esc(r.measure)}</span></div>`;
    h += `</div>`;
    if (r.story) h += `<p class="story full">${esc(r.story)}</p>`;
    if (r.recipe) {
      const R = r.recipe;
      const row = (k, v) => `<div class="trait"><span class="k">${esc(k)}</span><span class="v">${esc(v)}</span></div>`;
      h += `<p class="dim">The run, row by row:</p><div class="traits">`
        + row("Control", R.control) + row("Treatment", R.treatment) + row("Effort", R.effort)
        + row("Prediction", R.prediction) + row("Measure", R.measure) + `</div>`;
    }
    if (r.id) h += `<a class="dask" href="../ask/watch/?id=${encodeURIComponent(r.id)}">Replay this run →</a>`;
    else if (r.note) h += `<p class="dim">${esc(r.note)}</p>`;
  } else if (st === "ready" && lab.ask) {
    h += `</div><a class="dask" href="../study.html?q=${encodeURIComponent(lab.ask)}">Run it in Ask the flies →<small>“${esc(lab.ask)}”</small></a>`;
  } else {
    h += `</div><p>${esc(lab.note || lab.why || "")}</p>`;
  }
  return h;
}

function openDetail(key) {
  const c = byKey(key);
  if (!c) return;
  state.sel = key;
  $("atlasView").hidden = true;
  $("detailView").hidden = false;
  const list = filtered();
  const sib = $("sibNav");
  sib.innerHTML = `<span class="cap">In this filter (${list.length})</span>` + list.map(x =>
    `<button data-k="${esc(x.key)}" class="${x.key === key ? "on" : ""}">${esc(x.title)}<small>${esc(x.kind)}</small></button>`).join("");
  sib.querySelectorAll("button").forEach(b => b.onclick = () => openDetail(b.dataset.k));
  $("dfig").innerHTML = detailFigure(c);
  $("dcap").textContent = c.kind === "drug"
    ? (c.structure ? "2D structure · Guide to Pharmacology / PubChem" : "No structure on file")
    : "Where it acts → what to measure";
  let h = `<p class="eyebrow">${esc(c.kind)}</p><h1>${esc(c.title)}</h1><p class="dsent">${esc(c.sentence)}</p>`;
  if (c.hypothesis) {
    const hp = c.hypothesis;
    h += `<h2>The hypothesis</h2><p>${esc(hp.text)}</p>`
      + `<p class="dim">Rests on: ${esc(hp.given)}</p>`
      + `<p class="dim">Would fail if: ${esc(hp.falsified)}</p>`
      + `<p class="dim">Reconstructed from the standing rules after the run — the card's rationale, not a sealed prediction.</p>`
      + `<p><a class="dask" href="../study.html?hyp=${encodeURIComponent(c.key)}">Ask about this hypothesis →<small>plain-language answers + follow-ups</small></a></p>`;
  }
  if (c.kind === "drug") {
    if (c.mechanism) h += `<h2>How it works</h2><p>${esc(c.mechanism)}</p>`;
    h += `<h2>Standing rule</h2><div class="traits">`
      + `<div class="trait"><span class="k">Target</span><span class="v">${esc(c.target || "—")}</span></div>`
      + `<div class="trait"><span class="k">Measure</span><span class="v">${esc((c.behavior || []).join(" · ") || "—")}</span></div>`
      + (c.drug_class ? `<div class="trait"><span class="k">Class</span><span class="v">${esc(c.drug_class)}</span></div>` : "")
      + `</div>`;
    if (c.evidence && c.evidence.length)
      h += `<h2>Evidence on file</h2><ul class="ev">${c.evidence.map(e => `<li>${esc(e)}</li>`).join("")}</ul>`;
  } else {
    if (c.claim) h += `<h2>The claim</h2><p>${esc(c.claim)}</p>`;
    if (c.recorded) h += `<h2>Recorded result</h2><p>${esc(c.recorded)}</p>`;
  }
  h += `<h2>Real flies</h2><p><b>${esc(cap(c.real))}</b> — ${esc(c.real_source)}</p>`;
  h += `<h2>Our lab</h2>${detailLab(c)}`;
  $("detail").innerHTML = h;
  const i = list.findIndex(x => x.key === key);
  $("prevBtn").onclick = () => openDetail(list[(i - 1 + list.length) % list.length].key);
  $("nextBtn").onclick = () => openDetail(list[(i + 1) % list.length].key);
  history.replaceState(null, "", "#/c/" + encodeURIComponent(key));
  scrollTo(0, 0);
}

function closeDetail() {
  state.sel = null;
  $("detailView").hidden = true;
  $("atlasView").hidden = false;
  history.replaceState(null, "", location.pathname);
}

const cap = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;

function renderCounts(counts) {
  const k = counts.kind, lab = counts.lab;
  const parts = [`<b>${CARDS.length}</b> experiments`, `<b>${lab.run || 0}</b> run`,
    `<b>${lab.ready || 0}</b> ready to run`];
  if (lab.needs_model) parts.push(`<b>${lab.needs_model}</b> need a model change`);
  if (lab.cant_test) parts.push(`<b>${lab.cant_test}</b> can't test yet`);
  $("counts").innerHTML = parts.join(" · ") + ` — ${esc(k.drug || 0)} drugs · ${esc(k["model check"] || 0)} model checks · ${esc(k.comparison || 0)} comparisons · ${esc(k["new sense"] || 0)} new senses · ${esc(k.mutant || 0)} mutants`;
}

function chips(el, values, key) {
  el.innerHTML = values.map(([v, label]) =>
    `<button data-v="${esc(v)}" class="${state[key] === v ? "on" : ""}">${esc(label)}</button>`).join("");
  el.querySelectorAll("button").forEach(b => b.onclick = () => { state[key] = b.dataset.v; render(); });
}

async function boot() {
  try {
    const res = await fetch("data.json");
    if (!res.ok) throw new Error("data.json missing — run build.py");
    const data = await res.json();
    CARDS = data.cards; GENERATED = data.generated;
  } catch (e) {
    $("grid").innerHTML = `<p class="empty">The atlas data hasn't been built yet. Run <b>experiments/build.py</b>, then reload.</p>`;
    $("counts").textContent = "";
    return;
  }
  $("gen").textContent = `data as of ${GENERATED}`;
  renderCounts(countsOf());
  chips($("kinds"), KINDS.map(k => [k, k === "all" ? "All" : KIND_SHORT[k] || k]), "kind");
  chips($("reals"), REALS.map(r => [r, r === "all" ? "All" : cap(r)]), "real");
  chips($("labs"), LABS, "lab");
  $("q").addEventListener("input", e => { state.q = e.target.value.trim().toLowerCase(); render(); });
  $("sort").addEventListener("change", e => { state.sort = e.target.value; render(); });
  const panel = $("filterPanel");
  if (matchMedia("(max-width:760px)").matches) panel.classList.add("drawer-hidden");
  $("filterBtn").onclick = () => {
    if (matchMedia("(max-width:760px)").matches) panel.classList.toggle("drawer-hidden");
    else panel.classList.toggle("hidden");
  };
  $("backBtn").onclick = closeDetail;
  addEventListener("keydown", e => { if (e.key === "Escape" && !$("detailView").hidden) closeDetail(); });
  render();
  const m = location.hash.match(/^#\/c\/(.+)$/);
  if (m && byKey(decodeURIComponent(m[1]))) openDetail(decodeURIComponent(m[1]));
}

function countsOf() {
  const kinds = {}, lab = {};
  for (const c of CARDS) {
    kinds[c.kind] = (kinds[c.kind] || 0) + 1;
    lab[c.lab.status] = (lab[c.lab.status] || 0) + 1;
  }
  return { kind: kinds, lab };
}

boot();
