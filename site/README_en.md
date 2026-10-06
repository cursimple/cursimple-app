# Website maintenance

[中文](README.md)

The static site is deployed from `site/` by `.github/workflows/pages.yml`, independently of Android builds.

## Languages

Support `zh-CN`, `zh-TW` and `en` with identical semantic dictionary keys in `i18n.js`. Language selection takes effect immediately and is stored in the browser. Initial selection defaults to Simplified Chinese, regardless of browser language. A `?lang=` link overrides saved preference and can include an anchor such as `#download`.

Use `data-i18n` for text-only nodes. For elements containing icons or links, mark a text child to preserve interactions. Alternative text, accessible labels and metadata use `data-i18n-alt`, `data-i18n-aria-label` and `data-i18n-content`. Dynamic strings use `SiteI18n.t()`; dates use `formatDate()`.

## Release notes

Keep localized files together in `release-notes/`:

```text
v<version>.md
v<version>.zh-TW.md
v<version>.en.md
```

Relative images resolve against each Markdown file. Missing local notes fall back to the tagged `docs/release-notes/` path; missing translations fall back to the original with a visible explanation. Version and language changes invalidate stale display results.

## Validation

Run from the repository root:

```sh
node --check site/i18n.js
node --check site/releases.js
node --check site/main.js
node --test site/tests/*.test.cjs
node site/check-links.cjs
python3 -m http.server 8892 --bind 127.0.0.1 --directory site
```

Check language persistence, mobile navigation, download choices and announcement paging. Every arrow press updates the current page and indicator immediately; manual scrolling keeps them synchronized. Tests cover locale priority, dictionary completeness, offline fallback and competing requests.
