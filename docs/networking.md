# Downloads and update caching

Public GitHub assets use a purpose-specific mirror pool. Private repositories and account-derived assets remain on authenticated GitHub API routes; credentials never pass through public mirrors.

## Routing

| Purpose | Candidates |
|---|---|
| GitHub release | Release proxies and origin |
| GitHub raw / repository file | Repository CDNs, compatible proxies and origin |
| GitHub API | Status-preserving API routes only |
| Direct URL | Original destination |
| Local file | File read without networking |

URL rewrites preserve query parameters. Mutable registries and latest manifests use cache-busting time slices; immutable tagged notes and images can reuse cache. Source IDs stay language-independent for persisted preferences.

## Transfer strategy

`SharedHttp` shares connection pooling across text and fast binary transfers. Small text races validate content before choosing a winner and cancel stalled calls. Error pages, including JSON with HTTP 200, cannot satisfy registry validation.

`FastTransfer` ranks preferred, measured-fast, unmeasured, slow and recently failing mirrors. Throughput measurements are smoothed and expire; failed hosts receive a short cooldown. Initial pool order is only a starting point, not a promise of regional speed.

Small binaries use staggered requests with immediate loser cancellation. Large files probe Range capability and total size, then split work across compatible mirrors. Faster workers take more chunks; stalled tails can be hedged. Only the first completed copy contributes progress. Exact Content-Range and assembled-file validation protect integrity.

If chunking or validation fails, use complete-file fallback. Large servers without Range support do not start multiple full downloads. Progress reflects actual bytes; unknown totals remain indeterminate. Update APKs require expected SHA-256, and plugin bundles additionally validate their internal file checksums.

## Update state

App startup performs silent enabled checks. Plugin/component page entry refreshes again with a short successful-check debounce. Cached catalog identities remain available, but stale version labels and install actions are hidden during verification. Known newer releases cannot regress to older mirror responses. Account or source changes invalidate late results.

Installed-item checks publish independently with bounded concurrency and per-repository timeout. Update settings control automatic checks, interval and badges. The menu button exposes available app or plugin updates while the drawer is closed. Automatic checks never open update dialogs.

## Announcement cache

`ReleaseNotesCache` stores tag-specific Markdown in app cache. Eligible image prefetch writes encoded image bytes to disk without keeping decoded bitmaps. In-flight downloads are shared between prefetch and visible loaders. Prefetch failure does not block version checks; metered-network prefetch stores text only.

Local developer previews never request remote fallback images. User-visible decoding rejects invalid image bodies and permits retry. System cache eviction restores ordinary network loading.

## Verification

Mirror qualification checks both response structure and real file bytes against origin SHA-256, over repeated requests. Unit fixtures cover malformed content, slow sources, cancellations, Range rejection, incomplete data, fallback and monotonic progress. Local measured timings are not global or permanent reliability guarantees.
