(() => {
  const REPO = 'cursimple/cursimple-app';
  // App 检查更新也读这份清单：走 jsDelivr，国内一般零点几秒就能拿到
  const FEED = `https://cdn.jsdelivr.net/gh/${REPO}@update-feed/stable.json`;
  const API = `https://api.github.com/repos/${REPO}/releases/latest`;
  // 与 App 内 DownloadMirrorPool 的下载镜像同源，按 2026-09 实测速度排序
  const MIRRORS = [
    'https://ghproxy.monkeyray.net/',
    'https://gh-proxy.com/',
    'https://gh.llkk.cc/',
    'https://gh-proxy.org/',
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];

  /* ---------- 顶栏阴影 ---------- */
  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('scrolled', scrollY > 8);
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ---------- 进场动画 ---------- */
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

  /* ---------- 课表截图切换 ---------- */
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
  $$('.tabs button').forEach((b) => { new Image().src = b.dataset.shot; });

  /* ---------- 微信 / QQ 内置浏览器提示 ---------- */
  if (/MicroMessenger|QQ\/|\bQQBrowser\/.*MQQBrowser|WeiBo/i.test(navigator.userAgent)) {
    $('#wxTip').hidden = false;
  }

  /* ---------- 下载 ---------- */
  const assets = {};   // abi -> { url, size, sha256 }
  let route = 'github';
  let mirror = MIRRORS[0];

  const fmtSize = (n) => `${(n / 1048576).toFixed(1)} MB`;
  const fallbackUrl = (abi) => `https://github.com/${REPO}/releases/latest/download/CurSimple-${abi}.apk`;

  function applyLinks() {
    $$('[data-dl]').forEach((a) => {
      const abi = a.dataset.dl;
      const origin = assets[abi]?.url || fallbackUrl(abi);
      a.href = route === 'mirror' ? mirror + origin : origin;
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

  function setVersion(tag, date) {
    if (tag) $$('[data-ver]').forEach((el) => { el.textContent = tag; });
    if (date) {
      const d = new Date(date);
      $('[data-date]').textContent = `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日发布`;
    }
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

  async function loadRelease() {
    try {
      const feed = await getJson(FEED);
      for (const a of feed.assets || []) {
        assets[a.abi] = { ...assets[a.abi], url: a.downloadUrl, sha256: a.sha256 };
      }
      setVersion(feed.tagName);
      applyLinks();
    } catch { /* 静态兜底链接照样能用 */ }

    // 文件大小与发布日期只有 GitHub API 有；国内常常超时，拿不到就算了
    try {
      const rel = await getJson(API, 10000);
      for (const a of rel.assets || []) {
        const m = /^CurSimple-(.+)\.apk$/.exec(a.name);
        if (m) assets[m[1]] = { ...assets[m[1]], url: assets[m[1]]?.url || a.browser_download_url, size: a.size };
      }
      setVersion(rel.tag_name, rel.published_at);
      applyLinks();
    } catch { /* ignore */ }
  }

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
    if (save) try { localStorage.setItem('dl-route', next); } catch { /* ignore */ }
    if (next === 'mirror') pickMirror();
    applyLinks();
  }

  $$('.route button').forEach((b) => b.addEventListener('click', () => setRoute(b.dataset.route)));

  let saved = null;
  try { saved = localStorage.getItem('dl-route'); } catch { /* ignore */ }
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  const inMainland = /^Asia\/(Shanghai|Chongqing|Harbin|Urumqi|Kashgar)$/.test(tz);
  setRoute(saved || (inMainland ? 'mirror' : 'github'), false);

  loadRelease();
})();
