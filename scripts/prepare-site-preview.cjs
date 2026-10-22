#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const srcDir = process.argv[2] || 'site';
const outDir = process.argv[3] || 'site-preview-dist';
const commitSha = process.argv[4] || '';
const repo = process.argv[5] || 'cursimple/cursimple-app';

if (!fs.existsSync(srcDir)) {
  console.error(`Source directory not found: ${srcDir}`);
  process.exit(1);
}

// Copy recursively
fs.cpSync(srcDir, outDir, { recursive: true });

const htmlPath = path.join(outDir, 'index.html');
if (fs.existsSync(htmlPath) && commitSha) {
  let html = fs.readFileSync(htmlPath, 'utf8');
  const guardScript = `
<script id="preview-expiry-guard">
(() => {
  const currentSha = ${JSON.stringify(commitSha)};
  const repo = ${JSON.stringify(repo)};
  if (!currentSha) return;

  fetch(\`https://api.github.com/repos/\${repo}/commits/pre?per_page=1\`, { cache: 'no-store' })
    .then((r) => (r.ok ? r.json() : null))
    .then((data) => {
      if (!data || !data.sha || data.sha === currentSha) return;
      fetch(\`https://api.github.com/repos/\${repo}/commits/\${data.sha}/statuses\`, { cache: 'no-store' })
        .then((r) => (r.ok ? r.json() : []))
        .then((statuses) => {
          const previewStatus = statuses.find((s) => s.context === 'site/preview' && s.state === 'success');
          showExpired(data.sha, previewStatus?.target_url);
        })
        .catch(() => showExpired(data.sha));
    })
    .catch(() => {});

  function showExpired(newSha, targetUrl) {
    document.title = '预览已失效 · 课简';
    const shortOld = currentSha.slice(0, 7);
    const shortNew = newSha.slice(0, 7);
    const actionHtml = targetUrl
      ? \`<a href="\${targetUrl}" style="display:inline-flex;align-items:center;gap:8px;padding:12px 26px;background:#3FA277;color:#fff;border-radius:999px;text-decoration:none;font-weight:600;font-size:15px;box-shadow:0 6px 16px rgba(63,162,119,0.35);">前往最新预览 (\${shortNew}) →</a>\`
      : \`<a href="https://github.com/\${repo}/commits/pre" style="display:inline-flex;align-items:center;gap:8px;padding:12px 26px;background:#3FA277;color:#fff;border-radius:999px;text-decoration:none;font-weight:600;font-size:15px;">查看 pre 分支最新提交 →</a>\`;

    document.documentElement.innerHTML = \`
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>预览已失效 · 课简</title>
        <style>
          body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #F6FAF7; color: #16302A; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 24px; box-sizing: border-box; }
          .card { background: #fff; border-radius: 24px; padding: 42px 32px; max-width: 440px; width: 100%; text-align: center; box-shadow: 0 12px 32px rgba(22,48,42,0.08); border: 1px solid #E2EFE7; }
          .icon { width: 56px; height: 56px; border-radius: 50%; background: #FDE8E7; color: #E0483E; display: inline-flex; align-items: center; justify-content: center; font-size: 26px; margin: 0 auto 20px; font-weight: bold; }
          h1 { font-size: 22px; margin: 0 0 12px; font-weight: 700; color: #16302A; }
          p { font-size: 14px; line-height: 1.6; color: #5B7A70; margin: 0 0 28px; }
          .badge { display: inline-block; padding: 3px 8px; border-radius: 6px; background: #EEF4F0; font-family: monospace; font-size: 13px; color: #35534B; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✕</div>
          <h1>此临时预览已失效</h1>
          <p>当前页面属于旧版提交 <span class="badge">\${shortOld}</span>。<br>远端 pre 分支已有更新的官网提交发布，此旧版链接已停用。</p>
          \${actionHtml}
        </div>
      </body>
    \`;
  }
})();
</script>
`;
  html = html.replace('</body>', `${guardScript}\n</body>`);
  fs.writeFileSync(htmlPath, html, 'utf8');
  console.log(`Injected preview expiry guard for commit ${commitSha} into ${htmlPath}`);
}
