/* Feather Atlas shared shell: icons, day/dusk, hub top bar. Load before page scripts. */
window.Feather = (() => {
  const HUBS = [
    { id: 'flies', name: 'Meet the flies', sub: 'Maxime · Hagrid', icon: 'i-house', href: 'flies/' },
    { id: 'study', name: 'Study', sub: 'computational lab', icon: 'i-flask', href: 'study.html' },
    { id: 'atlas', name: 'Fly Atlas', sub: '66 brain regions', icon: 'i-layers', href: 'library/' },
    { id: 'ask', name: 'Ask the flies', sub: 'Run it live', icon: 'i-chat', href: 'ask/' },
  ];
  const DEFS = `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>`
    + `<symbol id="i-house" viewBox="0 0 24 24"><path d="M4 11l8-7 8 7M6 10v10h12V10"/></symbol>`
    + `<symbol id="i-flask" viewBox="0 0 24 24"><path d="M10 3h4M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4A2 2 0 0 0 19 18l-5-9V3"/></symbol>`
    + `<symbol id="i-layers" viewBox="0 0 24 24"><path d="M12 3l9 5-9 5-9-5 9-5zM3 13l9 5 9-5"/></symbol>`
    + `<symbol id="i-chat" viewBox="0 0 24 24"><path d="M4 6h16v9H9l-5 4V6z"/></symbol>`
    + `<symbol id="i-sun" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.5 4.5l2 2M17.5 17.5l2 2M19.5 4.5l-2 2M6.5 17.5l-2 2"/></symbol>`
    + `<symbol id="i-chev" viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></symbol>`
    + `<symbol id="i-user" viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 5-6 8-6s6.5 2 8 6"/></symbol>`
    + `</defs></svg>`;
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  let onLight = null;

  function icons() {
    if (document.getElementById('i-house')) return;
    document.body.insertAdjacentHTML('afterbegin', DEFS);
  }
  function mode() { return document.documentElement.dataset.light || 'day'; }
  function apply(m) {
    document.documentElement.dataset.light = m;
    const hero = document.getElementById('hero');
    if (hero) hero.dataset.light = m;
    document.body.style.background = m === 'dusk' ? '#14170f' : '';
    document.querySelectorAll('[data-light-toggle]').forEach(b =>
      b.setAttribute('aria-pressed', String(m === 'dusk')));
    try { localStorage.setItem('feather-light', m); } catch {}
    if (onLight) onLight(m);
  }
  function init(opts = {}) {
    onLight = opts.onLight || null;
    let m = 'day';
    try { m = localStorage.getItem('feather-light') || 'day'; } catch {}
    if (m !== 'day' && m !== 'dusk') m = 'day';
    apply(m);
    document.addEventListener('click', e => {
      const g = e.target.closest('[data-goto]');
      if (g) { location.assign(g.dataset.goto); return; }
      if (e.target.closest('[data-light-toggle]')) apply(mode() === 'dusk' ? 'day' : 'dusk');
    });
    return m;
  }
  function wireProfile(wrap, base) {
    const btn = wrap.querySelector('[data-profile]');
    const menu = wrap.querySelector('.shell-menu');
    const paint = user => {
      if (user) {
        const nm = user.name || user.email || '?';
        btn.innerHTML = `<span class="shell-av" aria-hidden="true">${esc(nm.trim().charAt(0).toUpperCase())}</span>`;
        btn.setAttribute('aria-label', `Account: ${nm}`);
        menu.innerHTML = `<div class="shell-me"><b>${esc(nm)}</b><span>${esc(user.email || '')}</span></div>`
          + `<a href="${base}duo/profile.html">Profile details</a><button data-signout>Sign out</button>`;
        menu.querySelector('[data-signout]').onclick = async e => {
          e.stopPropagation();
          menu.hidden = true;
          try { await window.DuoAuth.signOut(); } catch {}
        };
      } else {
        btn.innerHTML = '<svg class="ic"><use href="#i-user"/></svg>';
        btn.setAttribute('aria-label', 'Sign in');
        menu.innerHTML = `<a href="${base}duo/?signin=1">Sign in / create account</a>`;
      }
    };
    paint(null);
    if (window.DuoAuth) {
      window.DuoAuth.ready.then(() => paint(window.DuoAuth.currentUser())).catch(() => paint(null));
      window.DuoAuth.onAuthChange(paint);
    }
    btn.onclick = e => {
      e.stopPropagation();
      const user = window.DuoAuth && window.DuoAuth.currentUser();
      if (!user) { location.assign(base + 'duo/?signin=1'); return; }
      menu.hidden = !menu.hidden;
    };
    document.addEventListener('click', e => { if (!wrap.contains(e.target)) menu.hidden = true; });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') menu.hidden = true; });
  }
  // Build the shared top bar into el. extras: selectors adopted into the right slot.
  function topbar(el, { brand, sub = '', base = './', current = '', extras = [], light = true, profile = true } = {}) {
    icons();
    const kept = extras.map(s => document.querySelector(s)).filter(Boolean);
    kept.forEach(n => n.remove());
    const items = HUBS.map(h => {
      const inner = `<svg class="ic"><use href="#${h.icon}"/></svg><span>${h.name}<small>${h.sub}</small></span>`;
      return h.id === current
        ? `<span class="cur" aria-current="page">${inner}</span>`
        : `<a href="${base + h.href}">${inner}</a>`;
    }).join('');
    el.classList.add('shell-top');
    el.innerHTML = `<div class="shell-brand"><span class="shell-name">${brand}</span>`
      + (sub ? `<span class="shell-sub">${sub}</span>` : '') + `</div>`
      + `<nav class="shell-nav" aria-label="Fly Study">${items}</nav><div class="shell-extra"></div>`;
    const slot = el.querySelector('.shell-extra');
    kept.forEach(n => slot.appendChild(n));
    if (light) {
      const b = document.createElement('button');
      b.className = 'iconbtn'; b.dataset.lightToggle = '';
      b.setAttribute('aria-label', 'Toggle day and dusk');
      b.setAttribute('aria-pressed', String(mode() === 'dusk'));
      b.innerHTML = '<svg class="ic"><use href="#i-sun"/></svg>';
      el.appendChild(b);
    }
    if (profile) {
      const wrap = document.createElement('div');
      wrap.className = 'shell-prof';
      wrap.innerHTML = '<button class="iconbtn" data-profile aria-label="Sign in"><svg class="ic"><use href="#i-user"/></svg></button><div class="shell-menu" hidden></div>';
      el.appendChild(wrap);
      wireProfile(wrap, base);
    }
    return el;
  }
  return { icons, init, mode, topbar, HUBS };
})();
