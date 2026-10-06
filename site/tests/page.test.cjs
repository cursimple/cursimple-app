const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const SiteReleases = require('../releases.js');

// Minimal DOM fixture tests data assembly; browser checks cover layout.
function element(dataset = {}) {
  const classes = new Set();
  const attributes = new Map();
  const handlers = new Map();
  return {
    dataset, hidden: true, children: [], clientWidth: 400, scrollLeft: 0,
    classList: { add: (c) => classes.add(c), remove: (c) => classes.delete(c), contains: (c) => classes.has(c), toggle(c, on) { if (on) classes.add(c); else classes.delete(c); } },
    addEventListener(name, callback) { handlers.set(name, callback); },
    emit(name, event = {}) { handlers.get(name)?.(event); },
    setAttribute(k, v) { attributes.set(k, v); }, getAttribute(k) { return attributes.get(k); }, focus() {},
    scrollTo(options) { this.lastScroll = options; if (options.behavior === 'instant') this.scrollLeft = options.left; },
    contains() { return false; },
    querySelector() { return element(); },
  };
}

async function runPage({ stable, beta, api, order = [], language = 'zh-CN', notesFetch }) {
  const singles = new Map();
  const get = (selector) => {
    if (!singles.has(selector)) singles.set(selector, element());
    return singles.get(selector);
  };
  const lists = {
    '[data-ver]': [element()], '[data-channel-label]': [element()],
    '[data-release-link]': [element()], '[data-dl-meta]': [element(), element()],
    '[data-dl]': ['arm64-v8a', 'universal', 'armeabi-v7a', 'x86_64', 'x86'].map((dl) => element({ dl })),
    '[data-size]': [element({ size: 'arm64-v8a' })],
  };
  const pending = new Map();
  const requests = [];
  const handlers = new Map();
  const document = {
    baseURI: 'http://localhost/cursimple-app/', documentElement: element(), body: element(),
    querySelector: get, querySelectorAll: (s) => lists[s] || [],
    addEventListener(name, callback) { handlers.set(name, callback); },
    dispatchEvent(event) { handlers.get(event.type)?.(event); },
  };
  const mockFetch = (url) => {
    requests.push(url);
    if (notesFetch && /release-notes\//.test(url)) return notesFetch(url);
    if (/^release-notes\/v0\.7\.8(?:\.en|\.zh-TW)?\.md$/.test(url)) return Promise.resolve({ ok: true, text: async () => fs.readFileSync(path.join(__dirname, '..', url), 'utf8') });
    const key = url.includes('stable.json') ? 'stable' : url.includes('beta.json') ? 'beta' : url.startsWith('https://api.github.com/') ? 'api' : null;
    if (!key) return Promise.reject(new Error('offline'));
    return new Promise((resolve, reject) => pending.set(key, () => {
      const data = { stable, beta, api }[key];
      if (data === undefined) reject(new Error('offline'));
      else resolve({ ok: true, json: async () => data });
    }));
  };
  const location = { href: `http://localhost/cursimple-app/?lang=${language}#changelog`, hash: '#changelog' };
  const context = vm.createContext({
    SiteReleases, document, window: {}, navigator: { userAgent: 'Node', languages: ['zh-CN'] },
    localStorage: { getItem() { return null; }, setItem() {} },
    location, history: { replaceState(state, _, url) { location.href = String(url); } }, scrollY: 0, addEventListener() {},
    fetch: mockFetch, AbortController, setTimeout, clearTimeout, URL, CustomEvent: class { constructor(type, init) { this.type = type; Object.assign(this, init); } },
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../i18n.js'), 'utf8'), context);
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../main.js'), 'utf8'), context);
  for (const key of [...order, ...['stable', 'beta', 'api'].filter((k) => !order.includes(k))]) {
    pending.get(key)();
    await new Promise(setImmediate);
  }
  await new Promise(setImmediate);
  return { get, lists, requests, i18n: context.SiteI18n, document };
}

test('真实页面脚本在 feed/API 断网时仍显示测试版并加载十张本地公告图', async () => {
  const page = await runPage({});
  assert.equal(page.lists['[data-ver]'][0].textContent, 'v0.7.8');
  assert.equal(page.lists['[data-channel-label]'][0].textContent, '最新测试版');
  assert.equal(page.get('[data-channel="stable"]').innerHTML, '最新正式版：暂无正式版');
  assert.equal(page.get('[data-date]').textContent, '');
  for (const link of page.lists['[data-dl]']) assert.ok(link.href.includes('/releases/download/v0.7.8/'));
  const slides = page.get('#clSlides').innerHTML;
  assert.equal((slides.match(/http:\/\/localhost\/cursimple-app\/assets\/shots\//g) || []).length, 10);
  assert.ok(slides.includes('雨课堂 v1.2.0'));
  assert.ok(!page.requests.some((url) => url.includes('/releases/latest')));
});

test('API 与 feed 不同到达顺序都不混用 0.7.4 的日期、包大小和公告', async () => {
  for (const order of [['api', 'stable', 'beta'], ['beta', 'stable', 'api']]) {
    const page = await runPage({
      stable: { tagName: 'v0.7.4', versionCode: 30, prerelease: false },
      beta: { tagName: 'v0.7.8', versionCode: 34, prerelease: true },
      api: [{ tag_name: 'v0.7.4', prerelease: false, published_at: '2026-09-01T00:00:00Z', body: '这是旧公告', assets: [{ name: 'CurSimple-arm64-v8a.apk', size: 1000000, browser_download_url: 'old.apk' }] }],
      order,
    });
    assert.equal(page.lists['[data-ver]'][0].textContent, 'v0.7.8');
    assert.equal(page.get('[data-channel="stable"]').innerHTML, '最新正式版：暂无正式版');
    assert.equal(page.get('[data-date]').textContent, '');
    assert.equal(page.lists['[data-size]'][0].textContent, '大小待获取');
    assert.ok(!page.get('#clSlides').innerHTML.includes('这是旧公告'));
    for (const link of page.lists['[data-dl]']) assert.ok(link.href.includes('/releases/download/v0.7.8/'));
  }
});

test('英文和繁体公告离线仍能加载，日期与下载文案使用所选语言', async () => {
  for (const language of ['en', 'zh-TW']) {
    const page = await runPage({
      language,
      api: [{ tag_name: 'v0.7.8', prerelease: true, published_at: '2026-10-01T00:00:00Z', assets: [] }],
    });
    assert.equal(page.document.documentElement.lang, language);
    assert.equal(page.lists['[data-channel-label]'][0].textContent, language === 'en' ? 'Latest beta' : '最新測試版');
    assert.ok(page.get('[data-date]').textContent.includes(language === 'en' ? 'October' : '10月'));
    const slides = page.get('#clSlides').innerHTML;
    assert.ok(slides.includes(language === 'en' ? "See today's schedule clearly" : '把今日安排看清楚'));
    assert.equal((slides.match(/http:\/\/localhost\/cursimple-app\/assets\/shots\//g) || []).length, 10);
    assert.equal(page.lists['[data-size]'][0].textContent, language === 'en' ? 'Size unavailable' : '大小待取得');
  }
});

test('快速切换语言时，迟到的英文公告不会覆盖繁体公告', async () => {
  let resolveEnglish;
  const page = await runPage({ notesFetch: (url) => {
    if (url === 'release-notes/v0.7.8.en.md') return new Promise((resolve) => { resolveEnglish = resolve; });
    return Promise.resolve({ ok: true, text: async () => fs.readFileSync(path.join(__dirname, '..', url), 'utf8') });
  } });
  page.i18n.setLanguage('en');
  assert.ok(page.get('#clSlides').innerHTML.includes('Loading release notes'));
  page.i18n.setLanguage('zh-TW');
  await new Promise(setImmediate);
  const traditional = page.get('#clSlides').innerHTML;
  assert.ok(traditional.includes('測試版'));
  resolveEnglish({ ok: true, text: async () => '# Beta\n\nLate English text' });
  await new Promise(setImmediate);
  assert.equal(page.get('#clSlides').innerHTML, traditional);
});

test('公告缺少翻译时显示原文提示，语言切回中文后提示消失', async () => {
  const page = await runPage({ language: 'en', notesFetch: (url) => {
    if (url === 'release-notes/v0.7.8.md') return Promise.resolve({ ok: true, text: async () => fs.readFileSync(path.join(__dirname, '../release-notes/v0.7.8.md'), 'utf8') });
    return Promise.reject(new Error('missing translation'));
  } });
  assert.ok(page.get('#clSlides').innerHTML.includes('The original text is shown below.'));
  page.i18n.setLanguage('zh-CN');
  await new Promise(setImmediate);
  assert.ok(!page.get('#clSlides').innerHTML.includes('The original text is shown below.'));
});

test('公告连续点击三次，每次立即翻一页并同步底部指示，反向点击和边界也正确', async () => {
  const page = await runPage({});
  const slides = page.get('#clSlides');
  const dots = page.get('#clDots');
  slides.children = Array.from({ length: 5 }, () => element());
  dots.children = Array.from({ length: 5 }, () => element());
  const activeDot = () => dots.children.findIndex((dot) => dot.classList.contains('on'));
  for (let i = 1; i <= 3; i++) {
    page.get('#clNext').emit('click');
    assert.equal(slides.scrollLeft, i * slides.clientWidth, `第 ${i} 次点击已完成翻页`);
    assert.equal(activeDot(), i, `第 ${i} 次点击的底部指示立即同步`);
    slides.emit('scroll');
  }
  page.get('#clPrev').emit('click');
  assert.equal(slides.scrollLeft, 2 * slides.clientWidth);
  assert.equal(activeDot(), 2);
  for (let i = 0; i < 3; i++) page.get('#clPrev').emit('click');
  assert.equal(slides.scrollLeft, 0);
  assert.equal(activeDot(), 0);
  assert.equal(page.get('#clPrev').disabled, true);
});

test('手势滚动到下一页的过程中，底部指示立即同步，无需等待停止滚动', async () => {
  const page = await runPage({});
  const slides = page.get('#clSlides');
  const dots = page.get('#clDots');
  slides.children = Array.from({ length: 5 }, () => element());
  dots.children = Array.from({ length: 5 }, () => element());
  slides.scrollLeft = 1.6 * slides.clientWidth;
  slides.emit('scroll');
  assert.equal(dots.children.findIndex((dot) => dot.classList.contains('on')), 2);
  page.get('#clNext').emit('click');
  assert.equal(slides.scrollLeft, 3 * slides.clientWidth);
  assert.equal(dots.children.findIndex((dot) => dot.classList.contains('on')), 3);
});
