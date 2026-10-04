/* 官网渠道元数据与选择逻辑；GitHub prerelease 字段决定渠道，标签后缀不决定渠道。 */
((root, factory) => {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SiteReleases = factory();
})(globalThis, () => {
  const REPO = 'cursimple/cursimple-app';
  const CHANNEL_METADATA = {
    stable: { noRelease: true },
    beta: { tagName: 'v0.7.5', versionName: '0.7.5', versionCode: 31, prerelease: true, assets: [] },
  };
  // 发布方已确认的纠正：旧 feed / GitHub 元数据更新前也不能把 0.7.4 当成正式版。
  const PRERELEASE_OVERRIDES = { 'v0.7.4': true, 'v0.7.5': true };

  function normalize(data, fromApi = false) {
    if (!data || data.noRelease || data.draft) return null;
    const tag = fromApi ? data.tag_name : data.tagName;
    if (typeof tag !== 'string' || !tag.trim()) return null;
    const prerelease = PRERELEASE_OVERRIDES[tag] ?? data.prerelease;
    if (typeof prerelease !== 'boolean') return null;
    const result = { tag, prerelease, assets: {} };
    if (Number.isInteger(data.versionCode) && data.versionCode > 0) result.code = data.versionCode;
    if (data.published_at && Number.isFinite(Date.parse(data.published_at))) result.date = new Date(data.published_at);
    if (typeof data.body === 'string') result.body = data.body;
    for (const asset of Array.isArray(data.assets) ? data.assets : []) {
      const abi = fromApi ? /^CurSimple-(.+)\.apk$/.exec(asset.name || '')?.[1] : asset.abi;
      if (!abi) continue;
      const value = {};
      const url = fromApi ? asset.browser_download_url : asset.downloadUrl;
      if (url) value.url = url;
      if (asset.sha256) value.sha256 = asset.sha256;
      if (asset.size > 0) value.size = asset.size;
      result.assets[abi] = value;
    }
    return result;
  }

  function compare(a, b) {
    if (a.code && b.code && a.code !== b.code) return a.code - b.code;
    // 数字部分仅用于排序；即使 tag 没有 -beta，渠道仍然来自显式元数据。
    const parts = (tag) => /^v?(\d+)\.(\d+)\.(\d+)/.exec(tag)?.slice(1).map(Number) || [0, 0, 0];
    const av = parts(a.tag), bv = parts(b.tag);
    for (let i = 0; i < 3; i++) if (av[i] !== bv[i]) return av[i] - bv[i];
    return (a.date?.getTime() || 0) - (b.date?.getTime() || 0);
  }

  function selectChannels(feeds = {}, apiReleases = []) {
    const byTag = new Map();
    const add = (candidate) => {
      if (!candidate) return;
      const old = byTag.get(candidate.tag);
      const mergedAssets = { ...old?.assets };
      for (const [abi, asset] of Object.entries(candidate.assets)) mergedAssets[abi] = { ...mergedAssets[abi], ...asset };
      byTag.set(candidate.tag, { ...old, ...candidate, assets: mergedAssets });
    };
    add(normalize(CHANNEL_METADATA.beta));
    add(normalize(feeds.stable));
    add(normalize(feeds.beta));
    for (const release of Array.isArray(apiReleases) ? apiReleases : []) add(normalize(release, true));
    const channels = { stable: null, beta: null };
    for (const release of byTag.values()) {
      const key = release.prerelease ? 'beta' : 'stable';
      if (!channels[key] || compare(release, channels[key]) > 0) channels[key] = release;
    }
    channels.recommended = !channels.stable || compare(channels.beta, channels.stable) >= 0 ? channels.beta : channels.stable;
    return channels;
  }

  const releaseUrl = (tag) => `https://github.com/${REPO}/releases/tag/${encodeURIComponent(tag)}`;
  const downloadUrl = (tag, abi) => `https://github.com/${REPO}/releases/download/${encodeURIComponent(tag)}/CurSimple-${abi}.apk`;
  return { REPO, CHANNEL_METADATA, selectChannels, releaseUrl, downloadUrl };
});
