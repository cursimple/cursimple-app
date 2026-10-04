const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const SiteReleases = require('../releases.js');

// 页面脚本的最小 DOM 接口：检验真实脚本的数据装配与公告资源地址，视觉验收仍由浏览器完成。
function element(dataset = {}) {
  const classes = new Set();
  return {
    dataset, hidden: true, children: [], clientWidth: 400, scrollLeft: 0,
    classList: { add: (c) => classes.add(c), remove: (c) => classes.delete(c), contains: (c) => classes.has(c), toggle(c, on) { if (on) classes.add(c); else classes.delete(c); } },
    addEventListener() {}, setAttribute() {}, focus() {}, scrollTo() {}, contains() { return false; },
    querySelector() { return element(); },
  };
}

async function runPage({ stable, beta, api, order = [] }) {
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
  const document = { baseURI: 'http://localhost/cursimple-app/', body: element(), querySelector: get, querySelectorAll: (s) => lists[s] || [], addEventListener() {} };
  const mockFetch = (url) => {
    requests.push(url);
    if (url === 'release-notes/v0.7.5.md') return Promise.resolve({ ok: true, text: async () => fs.readFileSync(path.join(__dirname, '../release-notes/v0.7.5.md'), 'utf8') });
    const key = url.includes('stable.json') ? 'stable' : url.includes('beta.json') ? 'beta' : url.startsWith('https://api.github.com/') ? 'api' : null;
    if (!key) return Promise.reject(new Error('offline'));
    return new Promise((resolve, reject) => pending.set(key, () => {
      const data = { stable, beta, api }[key];
      if (data === undefined) reject(new Error('offline'));
      else resolve({ ok: true, json: async () => data });
    }));
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../main.js'), 'utf8'), {
    SiteReleases, document, window: {}, navigator: { userAgent: 'Node' },
    localStorage: { getItem() { return null; }, setItem() {} },
    location: { hash: '#changelog' }, history: {}, scrollY: 0, addEventListener() {},
    fetch: mockFetch, AbortController, setTimeout, clearTimeout, URL,
  });
  for (const key of [...order, ...['stable', 'beta', 'api'].filter((k) => !order.includes(k))]) {
    pending.get(key)();
    await new Promise(setImmediate);
  }
  await new Promise(setImmediate);
  return { get, lists, requests };
}

test('真实页面脚本在 feed/API 断网时仍显示测试版并加载十张本地公告图', async () => {
  const page = await runPage({});
  assert.equal(page.lists['[data-ver]'][0].textContent, 'v0.7.5');
  assert.equal(page.lists['[data-channel-label]'][0].textContent, '最新测试版');
  assert.equal(page.get('[data-channel="stable"]').innerHTML, '最新正式版：暂无正式版');
  assert.equal(page.get('[data-date]').textContent, '');
  for (const link of page.lists['[data-dl]']) assert.ok(link.href.includes('/releases/download/v0.7.5/'));
  const slides = page.get('#clSlides').innerHTML;
  assert.equal((slides.match(/http:\/\/localhost\/cursimple-app\/assets\/shots\//g) || []).length, 10);
  assert.ok(slides.includes('雨课堂 v1.2.0'));
  assert.ok(!page.requests.some((url) => url.includes('/releases/latest')));
});

test('API 与 feed 不同到达顺序都不混用 0.7.4 的日期、包大小和公告', async () => {
  for (const order of [['api', 'stable', 'beta'], ['beta', 'stable', 'api']]) {
    const page = await runPage({
      stable: { tagName: 'v0.7.4', versionCode: 30, prerelease: false },
      beta: { tagName: 'v0.7.5', versionCode: 31, prerelease: true },
      api: [{ tag_name: 'v0.7.4', prerelease: false, published_at: '2026-09-01T00:00:00Z', body: '这是旧公告', assets: [{ name: 'CurSimple-arm64-v8a.apk', size: 1000000, browser_download_url: 'old.apk' }] }],
      order,
    });
    assert.equal(page.lists['[data-ver]'][0].textContent, 'v0.7.5');
    assert.equal(page.get('[data-channel="stable"]').innerHTML, '最新正式版：暂无正式版');
    assert.equal(page.get('[data-date]').textContent, '');
    assert.equal(page.lists['[data-size]'][0].textContent, '大小待获取');
    assert.ok(!page.get('#clSlides').innerHTML.includes('这是旧公告'));
    for (const link of page.lists['[data-dl]']) assert.ok(link.href.includes('/releases/download/v0.7.5/'));
  }
});
