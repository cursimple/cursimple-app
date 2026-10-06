const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const site = __dirname;
const failures = [];
let checked = 0;

function checkLink(file, reference, ids) {
  const ref = reference.replace(/&amp;/g, '&');
  if (!ref || /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(ref)) return;
  const [pathname, fragment] = ref.split(/[?#]/);
  if (!pathname) {
    if (ref.startsWith('#') && ids && !ids.has(fragment)) failures.push(`${path.relative(site, file)}: 缺少锚点 ${ref}`);
    checked++;
    return;
  }
  const target = path.resolve(path.dirname(file), decodeURIComponent(pathname));
  if (!target.startsWith(site + path.sep) || !fs.existsSync(target)) failures.push(`${path.relative(site, file)}: 本地链接不存在 ${ref}`);
  checked++;
}

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) { walk(file); continue; }
    if (!/\.(?:html|css|md)$/.test(file)) continue;
    const source = fs.readFileSync(file, 'utf8');
    if (file.endsWith('.html')) {
      const ids = new Set([...source.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
      for (const m of source.matchAll(/\b(?:src|href|data-shot)="([^"]+)"/g)) checkLink(file, m[1], ids);
    } else if (file.endsWith('.md')) {
      for (const m of source.matchAll(/!?\[[^\]]*\]\(([^)\s]+)\)/g)) checkLink(file, m[1]);
    } else {
      for (const m of source.matchAll(/url\(\s*['"]?([^)'"\s]+)['"]?\s*\)/g)) checkLink(file, m[1]);
    }
  }
}

walk(site);
for (const name of ['index.html', 'main.js', 'releases.js']) {
  if (fs.readFileSync(path.join(site, name), 'utf8').includes('/releases/latest')) failures.push(`${name}: 不应使用 /releases/latest`);
}
const html = fs.readFileSync(path.join(site, 'index.html'), 'utf8');
assert.ok(html.indexOf('src="releases.js"') < html.indexOf('src="main.js"'), '渠道模块须先于页面脚本加载');
assert.ok(html.includes('最新测试版') && html.includes('暂无正式版'), '静态页面须包含正确渠道文案');
assert.equal(failures.length, 0, failures.join('\n'));
console.log(`静态链接校验通过：${checked} 个本地资源与锚点；下载回退固定 tag。`);
