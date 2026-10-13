# Website maintenance

[中文](README.md)

The static site is deployed from `site/` by `.github/workflows/pages.yml`, independently of Android builds.

## Plugin and component entry points

The market section and footer link to the plugin and component catalogs. The Components section describes YuKeTang notices and multi-platform notifications and links to their repositories and Releases. The component registry is `cursimple/cursimple-components`, with its site at `https://cursimple.github.io/cursimple-components/`; initial Pages enablement and deployment belong to that repository's workflow.

Compatibility and platform capabilities follow component documentation. Avoid hard-coding a particular local ZIP version, and update all three languages for new copy.

## Languages

Support `zh-CN`, `zh-TW` and `en` with identical semantic dictionary keys in `i18n.js`. Language selection takes effect immediately and is stored in the browser. Initial selection defaults to Simplified Chinese, regardless of browser language. A `?lang=` link overrides saved preference and can include an anchor such as `#download`.

Use `data-i18n` for text-only nodes. For elements containing icons or links, mark a text child to preserve interactions. Alternative text, accessible labels and metadata use `data-i18n-alt`, `data-i18n-aria-label` and `data-i18n-content`. Dynamic strings use `SiteI18n.t()`; dates use `formatDate()`.

## Downloads

Download buttons use the China mirror by default: the page starts with the first mirror, then switches to whichever mirror first returns the real `update.json`; rate-limited or failing mirrors are never chosen. Choosing “GitHub direct” is remembered. Only mirrors verified to serve the APK and allow cross-origin probes belong in the list.

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
