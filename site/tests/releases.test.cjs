const test = require('node:test');
const assert = require('node:assert/strict');
const { CHANNEL_METADATA, selectChannels, downloadUrl } = require('../releases.js');
const feed = (tagName, prerelease, versionCode, assets = []) => ({ tagName, prerelease, versionCode, assets });
const api = (tag_name, prerelease, extra = {}) => ({ tag_name, prerelease, ...extra });

test('无正式版时使用 beta feed 的无后缀 v0.7.5', () => {
  const result = selectChannels({ stable: { noRelease: true }, beta: feed('v0.7.5', true, 31) });
  assert.equal(result.stable, null);
  assert.equal(result.beta.code, 31);
  assert.equal(result.recommended.tag, 'v0.7.5');
  assert.equal(result.recommended.prerelease, true);
});

test('旧 stable feed 与 GitHub 误标的 0.7.4 都纠正为测试版', () => {
  const result = selectChannels({ stable: feed('v0.7.4', false, 30) }, [api('v0.7.4', false)]);
  assert.equal(result.stable, null);
  assert.equal(result.recommended.tag, 'v0.7.5');
});

test('v0.7.5 发布方明确为 prerelease，忽略暂未纠正的 false', () => {
  assert.equal(selectChannels({}, [api('v0.7.5', false)]).recommended.prerelease, true);
});

test('断网、空 feed 或无效 feed 都固定回退 v0.7.5', () => {
  for (const feeds of [{}, { stable: { noRelease: true }, beta: null }, { beta: feed('v9.0.0', undefined, 99) }]) {
    const result = selectChannels(feeds);
    assert.equal(result.recommended.tag, CHANNEL_METADATA.beta.tagName);
    assert.equal(result.stable, null);
    assert.equal(downloadUrl(result.recommended.tag, 'arm64-v8a'), 'https://github.com/cursimple/cursimple-app/releases/download/v0.7.5/CurSimple-arm64-v8a.apk');
  }
});

test('测试版比正式版新时，主下载推荐测试版并保留两个渠道', () => {
  const result = selectChannels({ stable: feed('v0.8.0', false, 32), beta: feed('v0.8.1', true, 33) });
  assert.equal(result.stable.tag, 'v0.8.0');
  assert.equal(result.beta.tag, 'v0.8.1');
  assert.equal(result.recommended, result.beta);
});

test('beta feed 也可能是正式发布；带连字符的 tag 不能推断为测试版', () => {
  const result = selectChannels({ beta: feed('v0.8.0-preview', false, 32) });
  assert.equal(result.stable.tag, 'v0.8.0-preview');
  assert.equal(result.recommended, result.stable);
});

test('API 列表过滤 draft，显式 prerelease 无需标签后缀', () => {
  const result = selectChannels({}, [api('v0.8.0', true), api('v9.0.0', false, { draft: true })]);
  assert.equal(result.stable, null);
  assert.equal(result.recommended.tag, 'v0.8.0');
});

test('只为相同 tag 合并 URL、校验值、大小、日期和公告', () => {
  const result = selectChannels({ beta: feed('v0.7.5', true, 31, [{ abi: 'arm64-v8a', downloadUrl: downloadUrl('v0.7.5', 'arm64-v8a'), sha256: 'same-tag' }]) }, [
    api('v0.7.4', false, { published_at: '2026-09-01T00:00:00Z', body: '旧版', assets: [{ name: 'CurSimple-x86.apk', size: 123, browser_download_url: 'old.apk' }] }),
    api('v0.7.5', true, { published_at: '2026-10-04T00:00:00Z', body: '本版', assets: [{ name: 'CurSimple-arm64-v8a.apk', size: 456, browser_download_url: downloadUrl('v0.7.5', 'arm64-v8a') }] }),
  ]);
  assert.equal(result.recommended.body, '本版');
  assert.equal(result.recommended.date.toISOString(), '2026-10-04T00:00:00.000Z');
  assert.equal(result.recommended.assets['arm64-v8a'].sha256, 'same-tag');
  assert.equal(result.recommended.assets['arm64-v8a'].size, 456);
  assert.equal(result.recommended.assets.x86, undefined);
});

test('旧 beta feed 不会把本次推荐降回 0.7.4', () => {
  assert.equal(selectChannels({ beta: feed('v0.7.4', true, 30) }).recommended.tag, 'v0.7.5');
});

test('更晚的 API 发布只使用自己的资产与公告', () => {
  const result = selectChannels({ beta: feed('v0.7.5', true, 31, [{ abi: 'arm64-v8a', sha256: 'old-sha' }]) }, [api('v0.7.6', true, { body: '下一版' })]);
  assert.equal(result.recommended.tag, 'v0.7.6');
  assert.deepEqual(result.recommended.assets, {});
  assert.equal(result.recommended.body, '下一版');
});
