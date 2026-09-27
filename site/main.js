(() => {
  const REPO = 'cursimple/cursimple-app';
  const CDN = `https://cdn.jsdelivr.net/gh/${REPO}`;
  // App 检查更新也读这两份清单：走 jsDelivr，国内一般零点几秒就能拿到
  const FEED_STABLE = `${CDN}@update-feed/stable.json`;
  const FEED_BETA = `${CDN}@update-feed/beta.json`;
  const API = `https://api.github.com/repos/${REPO}/releases`;
  // 与 App 内 DownloadMirrorPool 的下载镜像同源，按 2026-09 实测速度排序
  const MIRRORS = [
    'https://ghproxy.monkeyray.net/',
    'https://gh-proxy.com/',
    'https://gh.llkk.cc/',
    'https://gh-proxy.org/',
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const modal = $('#changelog');
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* 隐私模式 */ } },
  };
  const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

  /* ================= 顶栏 ================= */
  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('scrolled', scrollY > 8 || !menu.hidden);
  addEventListener('scroll', onScroll, { passive: true });

  // 手机端菜单
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

  // 滚动时高亮当前栏目
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

  /* ================= 进场动画 ================= */
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

  /* ================= 课表截图切换 ================= */
  const shot = $('#scheduleShot');
  $$('.tabs button').forEach((btn) => btn.addEventListener('click', () => {
    if (btn.classList.contains('on')) return;
    $$('.tabs button').forEach((b) => b.classList.toggle('on', b === btn));
    shot.classList.add('fade');
    const next = new Image();
    next.onload = () => { shot.src = next.src; shot.alt = btn.textContent; shot.classList.remove('fade'); };
    next.src = btn.dataset.shot;
  }));
  // 预加载，切换时不闪
  addEventListener('load', () => $$('.tabs button').forEach((b) => { new Image().src = b.dataset.shot; }));

  /* ================= 看大图 ================= */
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

  /* ================= 微信 / QQ 内置浏览器提示 ================= */
  if (/MicroMessenger|QQ\/|\bQQBrowser\/.*MQQBrowser|WeiBo/i.test(navigator.userAgent)) {
    $('#wxTip').hidden = false;
  }

  /* ================= 下载 ================= */
  const assets = {};   // abi -> { url, size, sha256 }
  const release = { tag: null, date: null, prerelease: false };
  let route = 'github';
  let mirror = MIRRORS[0];

  const fmtSize = (n) => `${(n / 1048576).toFixed(1)} MB`;
  const fmtDate = (d) => `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`;
  const fallbackUrl = (abi) => `https://github.com/${REPO}/releases/latest/download/CurSimple-${abi}.apk`;
  const withRoute = (url) => (route === 'mirror' ? mirror + url : url);

  function applyLinks() {
    $$('[data-dl]').forEach((a) => {
      const abi = a.dataset.dl;
      a.href = withRoute(assets[abi]?.url || fallbackUrl(abi));
      if (assets[abi]?.sha256) a.title = `SHA-256: ${assets[abi].sha256}`;
    });
    $$('[data-size]').forEach((el) => {
      const size = assets[el.dataset.size]?.size;
      if (size) el.textContent = fmtSize(size);
    });
    const arm = assets['arm64-v8a'];
    $$('[data-dl-meta]').forEach((el, i) => {
      const base = i === 0 ? 'arm64 · 适合绝大多数手机' : '推荐 · 绝大多数手机选这个';
      el.textContent = arm?.size ? `${base} · ${fmtSize(arm.size)}` : base;
    });
  }

  function applyVersion() {
    if (release.tag) $$('[data-ver]').forEach((el) => { el.textContent = release.tag; });
    if (release.date) $('[data-date]').textContent = `${fmtDate(release.date)}发布`;
    $('#clGithub').href = release.tag ? `https://github.com/${REPO}/releases/tag/${release.tag}` : `https://github.com/${REPO}/releases/latest`;
    $('#clMeta').textContent = [release.date && `${fmtDate(release.date)}发布`, '最新正式版'].filter(Boolean).join(' · ');
    // 没看过这一版的更新公告就挂个小红点；弹窗开着时版本号才到，也算看过
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

  const feedReady = (async () => {
    try {
      const feed = await getJson(FEED_STABLE);
      if (feed.noRelease) return;
      for (const a of feed.assets || []) {
        assets[a.abi] = { ...assets[a.abi], url: a.downloadUrl, sha256: a.sha256 };
      }
      release.tag = feed.tagName;
      release.code = feed.versionCode;
      applyVersion();
      applyLinks();
    } catch { /* 静态兜底链接照样能用 */ }
  })();

  // 文件大小与发布日期只有 GitHub API 有；国内常常超时，拿不到就算了
  const apiReady = (async () => {
    try {
      const rel = await getJson(`${API}/latest`, 10000);
      for (const a of rel.assets || []) {
        const m = /^CurSimple-(.+)\.apk$/.exec(a.name);
        if (m) assets[m[1]] = { ...assets[m[1]], url: assets[m[1]]?.url || a.browser_download_url, size: a.size };
      }
      release.tag = release.tag || rel.tag_name;
      release.date = new Date(rel.published_at);
      release.body = rel.body;
      applyVersion();
      applyLinks();
    } catch { /* ignore */ }
  })();

  // 有比正式版新的测试版就在下载区提一句
  (async () => {
    try {
      const beta = await getJson(FEED_BETA);
      await feedReady;
      if (!beta.tagName || !(beta.versionCode > (release.code || 0))) return;
      const line = $('#betaLine');
      line.innerHTML = `抢先体验：测试版 <a href="https://github.com/${REPO}/releases/tag/${esc(beta.tagName)}" target="_blank" rel="noopener">${esc(beta.tagName)}</a> 已发布`;
      line.hidden = false;
    } catch { /* ignore */ }
  })();

  // 同时探测几个镜像，谁先有响应就用谁
  async function pickMirror() {
    const probe = `https://github.com/${REPO}/releases/latest/download/update.json`;
    const race = MIRRORS.map((m) => new Promise((resolve, reject) => {
      const ctl = new AbortController();
      setTimeout(() => { ctl.abort(); reject(); }, 5000);
      fetch(m + probe, { mode: 'no-cors', cache: 'no-store', signal: ctl.signal }).then(() => resolve(m), reject);
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

  /* ================= 更新公告：Markdown ================= */
  // 发版说明里的图都挂在 raw.githubusercontent.com，国内经常打不开，换成 jsDelivr
  function assetUrl(url) {
    const raw = /^https:\/\/raw\.githubusercontent\.com\/([^/]+\/[^/]+)\/([^/]+)\/(.+)$/.exec(url);
    if (raw) return `https://cdn.jsdelivr.net/gh/${raw[1]}@${raw[2]}/${raw[3]}`;
    if (!/^[a-z]+:/i.test(url) && release.tag) return `${CDN}@${release.tag}/docs/release-notes/${url.replace(/^\.\//, '')}`;
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

  // 中文断行接起来不加空格，英文之间补一个
  const CJK = /[　-鿿＀-￯]/;
  const joinLine = (a, b) => (CJK.test(a.slice(-1)) || CJK.test(b[0]) ? a + b : `${a} ${b}`);

  function parseNotes(md) {
    // GitHub 自动生成的变更列表不属于公告正文
    md = md.replace(/\r/g, '').split(/\n##\s+What's Changed|\n\*\*Full Changelog\*\*/)[0];
    const doc = { intro: [], sections: [] };
    let cur = null;          // 当前 ## 小节
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
  const notesReady = (async () => {
    await feedReady;
    let md = null;
    if (release.tag) {
      const path = `docs/release-notes/${release.tag}.md`;
      for (const url of [`${CDN}@${release.tag}/${path}`, `https://raw.githubusercontent.com/${REPO}/${release.tag}/${path}`]) {
        try { md = await getText(url); break; } catch { /* 换下一个 */ }
      }
    }
    if (!md) { await apiReady; md = release.body || null; }
    if (!md) throw new Error('no notes');
    notes = parseNotes(md);
    // 首屏胶囊换成这一版前三个亮点
    if (notes.sections.length) {
      $('[data-highlights]').textContent = notes.sections.slice(0, 3).map((s) => s.title).join(' · ');
    }
    if (!modal.hidden) renderNotes();
    return notes;
  })();
  notesReady.catch(() => { if (!modal.hidden) renderError(); });

  /* ================= 更新公告：弹窗 ================= */
  const slides = $('#clSlides');
  const dots = $('#clDots');
  const prev = $('#clPrev');
  const next = $('#clNext');
  let page = 0;
  let pushed = false;
  let lastFocus = null;

  function renderNotes() {
    const intro = notes.intro.join('') || '<p>这一版的更新内容如下。</p>';
    const toc = notes.sections.map((s, i) => `<button data-go="${i + 1}">${inline(s.title)}</button>`).join('');
    slides.innerHTML = `
      <section class="slide intro">
        <div class="big">${esc(release.tag || '')}</div>
        <div class="lead2">${intro}</div>
        ${toc ? `<div class="toc">${toc}</div><p class="swipe">左右滑动或点上面的标题翻页</p>` : ''}
      </section>
      ${notes.sections.map((s, i) => `
        <section class="slide">
          <span class="no">${String(i + 1).padStart(2, '0')} / ${String(notes.sections.length).padStart(2, '0')}</span>
          <h4>${inline(s.title)}</h4>
          ${s.blocks.join('')}
        </section>`).join('')}`;
    dots.innerHTML = [...slides.children].map((_, i) => `<button aria-label="第 ${i + 1} 页" data-go="${i}"></button>`).join('');
    go(0, false);
  }

  function renderError() {
    slides.innerHTML = `<div class="slide cl-loading"><p>更新内容暂时没加载出来</p>
      <a class="btn btn-ghost btn-md" href="https://github.com/${REPO}/releases/latest" target="_blank" rel="noopener">去 GitHub 查看<svg><use href="#i-ext"/></svg></a></div>`;
    dots.innerHTML = '';
    updateArrows();
  }

  function updateArrows() {
    const n = slides.children.length;
    prev.disabled = page <= 0;
    next.disabled = page >= n - 1;
    [...dots.children].forEach((d, i) => d.classList.toggle('on', i === page));
  }

  function go(i, smooth = true) {
    const n = slides.children.length;
    page = Math.max(0, Math.min(n - 1, i));
    slides.scrollTo({ left: page * slides.clientWidth, behavior: smooth ? 'smooth' : 'auto' });
    updateArrows();
  }

  let scrollTimer;
  slides.addEventListener('scroll', () => {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => {
      const i = Math.round(slides.scrollLeft / slides.clientWidth);
      if (i !== page) { page = i; updateArrows(); }
    }, 60);
  }, { passive: true });
  addEventListener('resize', () => { if (!modal.hidden) go(page, false); });

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
    else notesReady.then(null, renderError);
    if (release.tag) { store.set('seen-ver', release.tag); $$('.dot').forEach((d) => { d.hidden = true; }); }
    // 手机上按返回键关掉弹窗，而不是离开页面
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

  if (location.hash === '#changelog') open(true);
})();
