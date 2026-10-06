(() => {
  const { REPO, selectChannels, releaseUrl, downloadUrl } = SiteReleases;
  const { t, formatDate } = SiteI18n;
  SiteI18n.init();
  const CDN = `https://cdn.jsdelivr.net/gh/${REPO}`;
  const FEED_STABLE = `${CDN}@update-feed/stable.json`;
  const FEED_BETA = `${CDN}@update-feed/beta.json`;
  const API = `https://api.github.com/repos/${REPO}/releases`;
  // Release mirrors share app sources and verified fallback routes.
  const MIRRORS = [
    'https://gh-proxy.com/',
    'https://gh.llkk.cc/',
    'https://gh-proxy.org/',
    'https://gh.dpik.top/',
    'https://ghfile.geekertao.top/',
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const modal = $('#changelog');
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch {   } },
  };
  const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('scrolled', scrollY > 8 || !menu.hidden);
  addEventListener('scroll', onScroll, { passive: true });

  const menu = $('#mMenu');
  const menuBtn = $('#menuBtn');
  const setMenu = (open) => {
    menu.hidden = !open;
    menuBtn.setAttribute('aria-expanded', open);
    $('use', menuBtn).setAttribute('href', open ? '#i-close' : '#i-menu');
    onScroll();
  };
  menuBtn.addEventListener('click', () => setMenu(menu.hidden));
  menu.addEventListener('click', (e) => { if (e.target.closest('a')) setMenu(false); });
  document.addEventListener('click', (e) => { if (!menu.hidden && !nav.contains(e.target)) setMenu(false); });
  onScroll();

  const spyLinks = $$('.nav-links a[href^="#"]:not([data-changelog])');
  const spyTargets = spyLinks.map((a) => $(a.getAttribute('href'))).filter(Boolean);
  if ('IntersectionObserver' in window && spyTargets.length) {
    const spy = new IntersectionObserver((entries) => {
      for (const e of entries) {
        if (!e.isIntersecting) continue;
        spyLinks.forEach((a) => a.classList.toggle('on', a.getAttribute('href') === `#${e.target.id}`));
      }
    }, { rootMargin: '-45% 0px -50% 0px' });
    spyTargets.forEach((t) => spy.observe(t));
  }

  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      }
    }, { rootMargin: '0px 0px -8% 0px' });
    $$('.reveal').forEach((el) => io.observe(el));
  } else {
    $$('.reveal').forEach((el) => el.classList.add('in'));
  }

  const shot = $('#scheduleShot');
  $$('.tabs button').forEach((btn) => btn.addEventListener('click', () => {
    if (btn.classList.contains('on')) return;
    $$('.tabs button').forEach((b) => b.classList.toggle('on', b === btn));
    shot.classList.add('fade');
    const next = new Image();
    next.onload = () => { shot.src = next.src; shot.alt = btn.textContent; shot.classList.remove('fade'); };
    next.src = btn.dataset.shot;
  }));
  addEventListener('load', () => $$('.tabs button').forEach((b) => { new Image().src = b.dataset.shot; }));

  const lightbox = $('#lightbox');
  const lbImg = $('img', lightbox);
  $$('.phone img, .card-img img, .t-wide img, .desktop .w').forEach((img) => { img.dataset.zoom = ''; });
  document.addEventListener('click', (e) => {
    const img = e.target.closest('[data-zoom]');
    if (!img) return;
    lbImg.src = img.currentSrc || img.src;
    lbImg.alt = img.alt;
    lightbox.hidden = false;
    document.body.classList.add('lock');
  });
  const closeLightbox = () => {
    lightbox.hidden = true;
    if (modal.hidden) document.body.classList.remove('lock');
  };
  lightbox.addEventListener('click', closeLightbox);

  if (/MicroMessenger|QQ\/|\bQQBrowser\/.*MQQBrowser|WeiBo/i.test(navigator.userAgent)) {
    $('#wxTip').hidden = false;
  }

  let channels = selectChannels();
  let release = channels.recommended;
  let assets = release.assets;
  let route = 'github';
  let mirror = MIRRORS[0];

  const fmtSize = (n) => `${(n / 1048576).toFixed(1)} MB`;
  const fallbackUrl = (abi) => downloadUrl(release.tag, abi);
  const withRoute = (url) => (route === 'mirror' ? mirror + url : url);

  function applyLinks() {
    $$('[data-dl]').forEach((a) => {
      const abi = a.dataset.dl;
      a.href = withRoute(assets[abi]?.url || fallbackUrl(abi));
      a.title = assets[abi]?.sha256 ? `SHA-256: ${assets[abi].sha256}` : '';
    });
    $$('[data-size]').forEach((el) => {
      const size = assets[el.dataset.size]?.size;
      el.textContent = size ? fmtSize(size) : t('download.sizePending');
    });
    const arm = assets['arm64-v8a'];
    $$('[data-dl-meta]').forEach((el, i) => {
      const base = t(i === 0 ? 'hero.downloadMeta' : 'download.arm64Meta');
      el.textContent = arm?.size ? `${base} · ${fmtSize(arm.size)}` : base;
    });
  }

  function applyVersion() {
    const label = t(release.prerelease ? 'channel.beta' : 'channel.stable');
    $$('[data-ver]').forEach((el) => { el.textContent = release.tag; });
    $$('[data-channel-label]').forEach((el) => { el.textContent = label; });
    $('[data-date]').textContent = release.date ? t('channel.published', { date: formatDate(release.date) }) : '';
    $$('[data-release-link]').forEach((el) => { el.href = releaseUrl(release.tag); });
    $('#clMeta').textContent = [label, release.date && t('channel.published', { date: formatDate(release.date) })].filter(Boolean).join(' · ');
    for (const key of ['stable', 'beta']) {
      const item = channels[key];
      const name = t(`channel.${key}`);
      const line = $(`[data-channel="${key}"]`);
      const value = item ? `<a href="${esc(releaseUrl(item.tag))}" target="_blank" rel="noopener">${esc(item.tag)}</a>` : esc(t(key === 'stable' ? 'channel.noStable' : 'channel.noBeta'));
      line.innerHTML = t('channel.line', { name: esc(name), value });
    }
    if (!modal.hidden && release.tag) store.set('seen-ver', release.tag);
    const unseen = release.tag && store.get('seen-ver') !== release.tag;
    $$('.dot').forEach((d) => { d.hidden = !unseen; });
  }

  async function getJson(url, ms = 6000) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), ms);
    try {
      const r = await fetch(url, { signal: ctl.signal, cache: 'no-cache' });
      if (!r.ok) throw new Error(r.status);
      return await r.json();
    } finally { clearTimeout(t); }
  }

  async function getText(url, ms = 8000) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), ms);
    try {
      const r = await fetch(url, { signal: ctl.signal });
      if (!r.ok) throw new Error(r.status);
      return await r.text();
    } finally { clearTimeout(t); }
  }

  const feeds = {};
  let apiReleases = [];
  function applyChannels() {
    channels = selectChannels(feeds, apiReleases);
    release = channels.recommended;
    assets = release.assets;
    applyVersion();
    applyLinks();
    ensureNotes();
  }

  Promise.allSettled([
    ['stable', FEED_STABLE], ['beta', FEED_BETA],
  ].map(async ([key, url]) => {
    try { feeds[key] = await getJson(url); applyChannels(); } catch {   }
  }));

  // Merge metadata only for the same tag, including prerelease entries.
  (async () => {
    try {
      apiReleases = await getJson(`${API}?per_page=100`, 10000);
      applyChannels();
    } catch { /* ignore */ }
  })();

  async function pickMirror() {
    const probe = `https://github.com/${REPO}/releases/download/${encodeURIComponent(release.tag)}/update.json`;
    const race = MIRRORS.map((m) => new Promise((resolve, reject) => {
      const ctl = new AbortController();
      const timer = setTimeout(() => { ctl.abort(); reject(); }, 5000);
      fetch(m + probe, { mode: 'no-cors', cache: 'no-store', signal: ctl.signal }).then(() => resolve(m), reject).finally(() => clearTimeout(timer));
    }));
    try { mirror = await Promise.any(race); } catch { mirror = MIRRORS[0]; }
    applyLinks();
  }

  function setRoute(next, save = true) {
    route = next;
    $$('.route button').forEach((b) => b.classList.toggle('on', b.dataset.route === next));
    if (save) store.set('dl-route', next);
    if (next === 'mirror') pickMirror();
    applyLinks();
  }

  $$('.route button').forEach((b) => b.addEventListener('click', () => setRoute(b.dataset.route)));

  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  const inMainland = /^Asia\/(Shanghai|Chongqing|Harbin|Urumqi|Kashgar)$/.test(tz);
  setRoute(store.get('dl-route') || (inMainland ? 'mirror' : 'github'), false);

  let notesBase = document.baseURI;
  // Rewrite repository raw image URLs through jsDelivr.
  function assetUrl(url) {
    const raw = /^https:\/\/raw\.githubusercontent\.com\/([^/]+\/[^/]+)\/([^/]+)\/(.+)$/.exec(url);
    if (raw) return `https://cdn.jsdelivr.net/gh/${raw[1]}@${raw[2]}/${raw[3]}`;
    if (!/^[a-z]+:/i.test(url)) return new URL(url, notesBase).href;
    return url;
  }

  function inline(t) {
    const codes = [];
    t = t.replace(/`([^`]+)`/g, (_, c) => `\u0000${codes.push(c) - 1}\u0000`);
    t = esc(t)
      .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (_, alt, u) => `<img src="${esc(assetUrl(u))}" alt="${alt}" loading="lazy">`)
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
    return t.replace(/\u0000(\d+)\u0000/g, (_, i) => `<code>${esc(codes[i])}</code>`);
  }

  const CJK = /[　-鿿＀-￯]/;
  const joinLine = (a, b) => (CJK.test(a.slice(-1)) || CJK.test(b[0]) ? a + b : `${a} ${b}`);

  function parseNotes(md) {
    md = md.replace(/\r/g, '').split(/\n##\s+What's Changed|\n\*\*Full Changelog\*\*/)[0];
    const doc = { intro: [], sections: [] };
    let cur = null;
    let list = null;         // { tag, items }
    let para = null;
    let fence = null;
    const out = () => (cur ? cur.blocks : doc.intro);
    const flush = () => {
      if (para) { out().push(`<p>${inline(para)}</p>`); para = null; }
      if (list) { out().push(`<${list.tag}>${list.items.map((i) => `<li>${inline(i)}</li>`).join('')}</${list.tag}>`); list = null; }
    };
    for (const raw of md.split('\n')) {
      const line = raw.trimEnd();
      if (fence !== null) {
        if (/^```/.test(line)) { out().push(`<pre><code>${esc(fence)}</code></pre>`); fence = null; } else fence += `${raw}\n`;
        continue;
      }
      if (/^```/.test(line)) { flush(); fence = ''; continue; }
      let m;
      if (!line.trim()) { flush(); continue; }
      if ((m = /^#\s+(.+)/.exec(line))) { flush(); doc.title = m[1]; continue; }
      if ((m = /^##\s+(.+)/.exec(line))) { flush(); cur = { title: m[1], blocks: [] }; doc.sections.push(cur); continue; }
      if ((m = /^#{3,6}\s+(.+)/.exec(line))) { flush(); out().push(`<h5>${inline(m[1])}</h5>`); continue; }
      if ((m = /^\s*!\[([^\]]*)\]\(([^)\s]+)\)\s*$/.exec(line))) {
        flush();
        out().push(`<figure><img src="${esc(assetUrl(m[2]))}" alt="${esc(m[1])}" loading="lazy" data-zoom></figure>`);
        continue;
      }
      if ((m = /^\s*([-*+]|\d+\.)\s+(.+)/.exec(line))) {
        const tag = /\d/.test(m[1]) ? 'ol' : 'ul';
        if (para) flush();
        if (list && list.tag !== tag) flush();
        list = list || { tag, items: [] };
        list.items.push(m[2]);
        continue;
      }
      if ((m = /^>\s?(.*)/.exec(line))) { flush(); out().push(`<p class="quote">${inline(m[1])}</p>`); continue; }
      if (list) { list.items[list.items.length - 1] = joinLine(list.items[list.items.length - 1], line.trim()); continue; }
      para = para ? joinLine(para, line.trim()) : line.trim();
    }
    flush();
    return doc;
  }

  let notes = null;
  let notesOriginal = false;
  let notesLoading = false;
  let notesKey = '';
  let notesGeneration = 0;
  const notesCache = new Map();

  async function fetchNotes(tag, language) {
    const suffix = language === 'zh-CN' ? '' : `.${language}`;
    const local = `release-notes/${encodeURIComponent(tag)}${suffix}.md`;
    try {
      const md = await getText(local, 3000);
      if (!/^#\s/m.test(md)) throw new Error('invalid notes');
      return { md, base: new URL(local, document.baseURI).href, original: false };
    } catch {   }
    const path = `docs/release-notes/${encodeURIComponent(tag)}${suffix}.md`;
    for (const url of [`${CDN}@${encodeURIComponent(tag)}/${path}`, `https://raw.githubusercontent.com/${REPO}/${encodeURIComponent(tag)}/${path}`]) {
      try {
        const md = await getText(url, 4000);
        if (!/^#\s/m.test(md)) throw new Error('invalid notes');
        return { md, base: url, original: false };
      } catch {   }
    }
    if (language !== 'zh-CN') {
      const original = await cachedNotes(tag, 'zh-CN');
      return { ...original, original: true };
    }
    if (release.tag === tag && release.body) return { md: release.body, base: `${CDN}@${encodeURIComponent(tag)}/docs/release-notes/`, original: true };
    throw new Error('no notes');
  }

  function cachedNotes(tag, language) {
    const key = `${tag}:${language}`;
    if (!notesCache.has(key)) {
      const request = fetchNotes(tag, language).catch((error) => { notesCache.delete(key); throw error; });
      notesCache.set(key, request);
    }
    return notesCache.get(key);
  }

  function ensureNotes() {
    const language = SiteI18n.language;
    const key = `${release.tag}:${language}`;
    if (notesKey === key) return;
    notesKey = key;
    const generation = ++notesGeneration;
    notes = null;
    notesLoading = true;
    $('[data-highlights]').textContent = t('hero.highlights');
    if (!modal.hidden) renderLoading();
    cachedNotes(release.tag, language).then((result) => {
      if (generation !== notesGeneration) return;
      notesBase = result.base;
      notesOriginal = result.original && language !== 'zh-CN';
      notes = parseNotes(result.md);
      notesLoading = false;
      if (notes.sections.length && !notesOriginal) $('[data-highlights]').textContent = notes.sections.slice(0, 3).map((s) => s.title).join(' · ');
      if (!modal.hidden) renderNotes();
    }).catch(() => {
      if (generation !== notesGeneration) return;
      notesLoading = false;
      notesKey = '';
      if (!modal.hidden) renderError();
    });
  }

  const slides = $('#clSlides');
  const dots = $('#clDots');
  const prev = $('#clPrev');
  const next = $('#clNext');
  let page = 0;
  let pushed = false;
  let lastFocus = null;

  function renderNotes() {
    const intro = notes.intro.join('') || `<p>${esc(t('notes.intro'))}</p>`;
    const toc = notes.sections.map((s, i) => `<button data-go="${i + 1}">${inline(s.title)}</button>`).join('');
    slides.innerHTML = `
      <section class="slide intro">
        <div class="big">${esc(release.tag || '')}</div>
        ${notesOriginal ? `<p class="quote">${esc(t('notes.original'))}</p>` : ''}
        <div class="lead2">${intro}</div>
        ${toc ? `<div class="toc">${toc}</div><p class="swipe">${esc(t('notes.swipe'))}</p>` : ''}
      </section>
      ${notes.sections.map((s, i) => `
        <section class="slide">
          <span class="no">${String(i + 1).padStart(2, '0')} / ${String(notes.sections.length).padStart(2, '0')}</span>
          <h4>${inline(s.title)}</h4>
          ${s.blocks.join('')}
        </section>`).join('')}`;
    dots.innerHTML = [...slides.children].map((_, i) => `<button aria-label="${esc(t('notes.page', { page: i + 1 }))}" data-go="${i}"></button>`).join('');
    go(0);
  }

  function renderLoading() {
    slides.innerHTML = `<div class="slide cl-loading"><i class="spin"></i><p>${esc(t('notes.loading'))}</p></div>`;
    dots.innerHTML = '';
    page = 0;
    updateArrows();
  }

  function renderError() {
    slides.innerHTML = `<div class="slide cl-loading"><p>${esc(t('notes.error'))}</p>
      <a class="btn btn-ghost btn-md" href="${esc(releaseUrl(release.tag))}" target="_blank" rel="noopener">${esc(t('notes.github'))}<svg><use href="#i-ext"/></svg></a></div>`;
    dots.innerHTML = '';
    page = 0;
    updateArrows();
  }

  function updateArrows() {
    const n = slides.children.length;
    prev.disabled = page <= 0;
    next.disabled = page >= n - 1;
    [...dots.children].forEach((d, i) => d.classList.toggle('on', i === page));
  }

  function go(i) {
    const n = slides.children.length;
    page = Math.max(0, Math.min(n - 1, i));
    slides.scrollTo({ left: page * slides.clientWidth, behavior: 'instant' });
    updateArrows();
  }

  slides.addEventListener('scroll', () => {
    if (!slides.clientWidth) return;
    const i = Math.max(0, Math.min(slides.children.length - 1, Math.round(slides.scrollLeft / slides.clientWidth)));
    if (i !== page) { page = i; updateArrows(); }
  }, { passive: true });
  addEventListener('resize', () => { if (!modal.hidden) go(page); });

  modal.addEventListener('click', (e) => {
    const t = e.target.closest('[data-go]');
    if (t) go(+t.dataset.go);
    if (e.target.closest('[data-close]')) close();
  });
  prev.addEventListener('click', () => go(page - 1));
  next.addEventListener('click', () => go(page + 1));

  function open(fromHash = false) {
    if (!modal.hidden) return;
    lastFocus = document.activeElement;
    modal.hidden = false;
    modal.classList.remove('closing');
    document.body.classList.add('lock');
    if (notes) renderNotes();
    else if (notesLoading) renderLoading();
    else renderError();
    if (release.tag) { store.set('seen-ver', release.tag); $$('.dot').forEach((d) => { d.hidden = true; }); }
    if (!fromHash) { history.pushState({ changelog: true }, '', '#changelog'); pushed = true; }
    $('.sheet', modal).focus({ preventScroll: true });
  }

  function close(fromPop = false) {
    if (modal.hidden || modal.classList.contains('closing')) return;
    modal.classList.add('closing');
    setTimeout(() => {
      modal.hidden = true;
      modal.classList.remove('closing');
      if (lightbox.hidden) document.body.classList.remove('lock');
      lastFocus?.focus?.({ preventScroll: true });
    }, 200);
    if (fromPop) return;
    if (pushed) { pushed = false; history.back(); } else history.replaceState(null, '', location.pathname + location.search);
  }

  document.addEventListener('click', (e) => {
    const a = e.target.closest('[data-changelog]');
    if (!a) return;
    e.preventDefault();
    open();
  });
  addEventListener('popstate', () => {
    if (location.hash === '#changelog') open(true);
    else { pushed = false; close(true); }
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!lightbox.hidden) closeLightbox();
      else if (!modal.hidden) close();
      else if (!menu.hidden) setMenu(false);
    }
    if (modal.hidden || !lightbox.hidden) return;
    if (e.key === 'ArrowLeft') go(page - 1);
    if (e.key === 'ArrowRight') go(page + 1);
  });

  document.addEventListener('site-language-change', () => {
    applyVersion();
    applyLinks();
    // Active screenshot labels also follow the language after a screenshot switch.
    const activeShot = $('.tabs button.on');
    if (activeShot) shot.alt = activeShot.textContent;
    if (!lightbox.hidden) {
      const source = $$('[data-zoom]').find((image) => (image.currentSrc || image.src) === lbImg.src);
      if (source) lbImg.alt = source.alt;
    }
    ensureNotes();
  });
  applyChannels();
  if (location.hash === '#changelog') open(true);
})();
