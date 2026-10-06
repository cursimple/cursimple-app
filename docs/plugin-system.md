# Plugin and component API

Plugins are independent ZIP bundles executed through a restricted WebView. The host owns installation, permissions, persistence and scheduling; each bundle owns its school or service protocol.

## API compatibility

The development host supports API **9**. Bundles declaring supported older APIs remain compatible; undeclared, invalid or newer APIs are rejected by the APK before package files or install records are written. The same check runs during local imports, market installs and upgrades; it cannot be bypassed by invoking installation directly. Failed compatibility checks preserve an existing installation.

| API | Capability |
|---|---|
| 2 | Schedule import through a controlled `ctx` bridge |
| 3 | Extension components and background feed synchronization |
| 4 | Component-owned login, settings and content pages |
| 5 | Confirmed read actions, local ignoring and restoration |
| 6 | Notification receivers, encrypted configuration and restricted transport |
| 7 | Binding-page HTTP, encrypted sessions and generic TLS SMTP |
| 8 | Separate accepted, queued and confirmed delivery states with resumable status checks |
| 9 | Component-owned HTML desktop widgets |

`kind` defaults to `schedule`; `extension` requires API 3 or later and an `extension` section. Unknown kinds are rejected. Runtime asset components are a separate dependency mechanism, not extension pages.

## Bundle format

```text
manifest.json
main.js
checksums.json
ui/                 Optional owned HTML, CSS and JavaScript
assets/             Optional resources
models/             Optional model files
```

```json
{
  "id": "edu.example.schedule",
  "name": "Example schedule plugin",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 2,
  "minHostVersion": "0.7.5",
  "entry": "main.js",
  "startUrl": "https://portal.example.edu/",
  "permissions": ["web.navigate", "web.read_dom", "web.inject_script", "schedule.write"],
  "allowedHosts": ["portal.example.edu"],
  "limits": {
    "timeoutMs": 60000,
    "maxCourses": 1000,
    "maxStorageBytes": 1048576,
    "maxCapturedTextBytes": 524288,
    "maxOutputBytes": 1048576
  }
}
```

`minHostVersion` is the minimum supported app version, defaulting to `0.1.0` when absent. The APK reads its own installed version and compares numeric release parts and prerelease identifiers; CI suffixes identify equivalent test builds. An invalid requirement, an unreadable app version or a newer required version blocks installation. The preview explains the incompatibility and disables confirmation, and the native installer checks it again before writing. Runtime asset bundles also accept `minHostVersion` and retain their separate ABI checks.

Installation rejects absolute paths, parent traversal, drive-qualified paths, normalized duplicates, missing entries and exceeded archive limits. `checksums.json` must cover every nonmetadata file exactly. An optional `signature.json` signs the checksums; invalid signatures are rejected. A bundled public key proves consistency, not trusted publisher identity.

`startUrl` supports paths and ports. Without it, the host derives an HTTPS URL from the first allowed host. Prefer `ctx.web.setUserAgent()` to a fixed manifest user agent when portal state determines browser identity.

## Permissions

| Permission | Capability |
|---|---|
| `web.navigate` | Navigate within declared hosts |
| `web.read_dom` | Read DOM content and query elements |
| `web.read_cookies` | Read declared-host cookies |
| `web.inject_script` | Fill forms and invoke page actions |
| `web.capture_packet` | Read explicitly declared packet captures |
| `network.fetch` | Fetch declared HTTPS destinations in page context |
| `schedule.write` | Submit a schedule draft |
| `storage.plugin` | Use plugin-private storage capabilities |
| `component.use` | Use installed runtime dependencies |
| `feed.write` | Submit extension feed items |
| `notification.receive` | Receive generic host notification events |
| `storage.secure` | Use component-scoped encrypted configuration |
| `network.proxy` | Use restricted host transport |

Missing permission rejects the operation. Host allowlists restrict network destinations independently of permission declarations.

Packet capture also requires explicit `networkCaptures` rules:

```json
{
  "permissions": ["web.capture_packet"],
  "networkCaptures": [{
    "id": "course-table",
    "required": true,
    "method": "GET",
    "urlHost": "portal.example.edu",
    "urlPathContains": "/api/courses",
    "requestHeaders": ["accept"],
    "responseHeaders": ["content-type"],
    "captureResponseBody": true,
    "responseBodyMimeTypes": ["application/json"],
    "maxBodyBytes": 65536,
    "maxPackets": 4
  }]
}
```

`ctx.web.packet(id)` reads the latest match; `packets(id)` reads all retained matches. Undeclared IDs are rejected. Page fetch/XHR hooks can capture allowed textual bodies; native WebView interception does not reliably expose ordinary form POST bodies.

## Schedule entry

```js
export async function run(ctx) {
  const rows = ctx.web.queryAll('.course-row', row => ({
    title: row.querySelector('.title')?.textContent?.trim(),
    dayOfWeek: Number(row.dataset.day),
    startNode: Number(row.dataset.start),
    endNode: Number(row.dataset.end),
    weeks: row.dataset.weeks.split(',').map(Number)
  }));
  for (const row of rows) ctx.schedule.addCourse(row);
  return ctx.schedule.commit({ termId: ctx.term.id });
}
```

The host injects entry code after an allowed page loads. `ctx` exposes controlled JavaScript operations, not arbitrary Android objects. Committed drafts are validated for course count, titles, weekdays, periods and weeks before becoming `TermSchedule`.

Required runtime dependencies may use `engine_chromium`, `opencv_native`, `onnx_runtime`, `onnx_model` or `generic_asset`. Missing dependencies produce `NeedsComponents`; they do not silently select another engine. The integrated execution engine is system WebView.

Sync outcomes are `Success`, `Failure`, `NeedsComponents` or `AwaitingWebSession`. Web sessions carry validated start URL, permissions, entry code and limits. A missing usable start URL fails explicitly.

## Registries and installation

Plugin and extension source lists are separate:

| Kind | Default registry | Data path |
|---|---|---|
| Schedule | `cursimple/cursimple-plugins` | `plugin-stars-data/plugins-stars.json` |
| Extension | `cursimple/cursimple-components` | `component-stars-data/components-stars.json` |

Sources accept GitHub shorthand, HTTPS or SSH repository URLs and normalize to `owner/repo`. Individual bundle repositories can serve as one-entry sources. Ordered sources load independently; the first duplicate wins. Removing a source does not uninstall its bundles.

```json
{
  "repositories": [{
    "name": "example/schedule-plugin",
    "repo": "schedule-plugin",
    "owner": "example",
    "description": "Schedule import for Example University",
    "url": "https://github.com/example/schedule-plugin",
    "schools": ["Example University", "示例大学", "example-university"],
    "kind": "schedule"
  }]
}
```

`schools` and optional `aliases` are merged for case-insensitive substring search. The first school alias supplies the school-import title. Romanized aliases must be declared explicitly; the host performs no transliteration. Extension entries do not appear in school import.

Each repository's Release must contain `manifest.json` with `version` and `filename`, plus that exact bundle asset. GitHub-generated source archives are not installable bundles. Latest-manifest URLs are cache-busted; installation rechecks metadata. During refresh, cached version numbers are hidden and market installation is disabled until usable metadata arrives. A known newer version cannot be replaced by an older mirror result.

Public default sources use mirrors. Custom sources require visibility verification; private and account-derived entries use authenticated GitHub API throughout. Credentials are sent only to `api.github.com`, stripped from cross-host asset redirects, encrypted with Keystore and excluded from backups. Signing out invalidates account caches and in-flight results. Optional device authorization requires a configured OAuth Client ID; fine-grained tokens need selected-repository `Contents: Read-only`.

Installed cards provide enablement, details and host-owned removal even if component UI fails. Details show installed version, installation time, package size and available component-data estimates. Package/data measurement excludes encrypted bindings and shared host caches. Downloads report real bytes; unknown totals do not produce invented percentages. See [networking](networking.md).

## Extension components

```json
{
  "id": "example-notices",
  "name": "Example notices",
  "version": "1.0.0",
  "apiVersion": 5,
  "kind": "extension",
  "entry": "main.js",
  "permissions": ["network.fetch", "web.read_cookies", "feed.write"],
  "allowedHosts": ["service.example.org"],
  "extension": {
    "title": "Notices",
    "loginUrl": "https://service.example.org/login",
    "runUrl": "https://service.example.org/api/account",
    "syncIntervalMinutes": 60,
    "feedTypes": [{"id": "assignment", "label": "Assignment", "kind": "task", "color": "#2563EB"}],
    "settings": [{"key": "syncTasks", "type": "switch", "label": "Tasks", "default": true}],
    "ui": {"type": "html", "entry": "ui/feed.html", "settingsEntry": "ui/settings.html", "loginEntry": "ui/login.html"}
  }
}
```

`loginUrl` and `runUrl` may contain `{settings.KEY}` restricted to safe host/path fragments; resolved destinations must remain allowed. `loginViewportWidth` optionally supports desktop login layouts. Declared settings use `switch`, `select`, `number` or `text`; `requiresRelogin` invalidates prior session and data on change.

Components define type IDs and labels. Semantic `kind` is `task` or `notice`; undeclared semantics fall back to timing information. The host does not infer platform-specific names.

Entry code is a single module-style bundle exporting `checkLogin(ctx)` and `sync(ctx)`, optionally `performItemAction(ctx)` or `deliverNotifications(ctx)`. Imports are unsupported. Exports are stripped and code is injected directly, without eval.

```js
export async function checkLogin(ctx) {
  const account = await fetchAccount(ctx);
  return account ? { loggedIn: true, account } : { loggedIn: false };
}
export async function sync(ctx) {
  return { account: await fetchAccount(ctx), items: await fetchItems(ctx) };
}
```

| Runtime member | Contract |
|---|---|
| `ctx.mode` | Current entry operation |
| `ctx.settings` | Read-only effective declared settings |
| `ctx.action` | Read-action parameters, otherwise empty |
| `ctx.state.get/set` | Component-private persistent sync cache |
| `ctx.network.fetch` | Same-origin, cookie-bearing fetch; isolated receivers use restricted host transport |
| `ctx.web.cookie` | Read a document cookie with permission |
| `ctx.feed.add` | Add an item, alternatively return `items` |
| `ctx.log.info/warn/error` | Bounded component logs |
| `ctx.now()` | Current milliseconds |

Items have stable `id`, required `title`, `type`, optional `kind`, `course`, `category`, `publishAt`, `startAt`, `dueAt`, `done`, `summary`, `content`, `author`, media and web URL. Times are milliseconds. Every sync is a complete snapshot: missing IDs are removed. Respect `maxOutputBytes` and bounded runtime timeout. Return `loginRequired` after session expiry.

The host serializes runs per component, establishes a baseline without replaying historical notices, synchronizes due components when online, schedules deadline notices and reconciles optional timetable events. Initial background baselines do not send every old item. Removed or logged-out components lose their archives, cookies and generated events; expired sessions retain useful cached deadlines.

### Owned pages

HTML, CSS, JavaScript and assets ship inside the ZIP. The host loads declared entries and falls back to generic UI on failure. `window.CurSimpleComponent` provides `state`, immediate `subscribe(fn)` and Promise-based `request(command, payload)`. Theme tokens and a sampled `nowMillis` live in `context`; continuous clocks belong to the component.

| Command | Purpose |
|---|---|
| `settings.update` / `host.update` | Save declared or shared host settings |
| `ui.login/settings/feed/close` | Navigate or close owned pages |
| `web.cookie` / `qr.encode` | Read allowed cookies or encode component-provided QR text |
| `login.check` | Validate through the component entry; `navigate:false` returns data without leaving |
| `sync` / `logout` | Synchronize or invalidate the session |
| `component.remove` | Request host-owned removal |
| `media.open` | Open an attachment in the current archive |

Ordinary requests time out after 45 seconds; sync and read actions allow up to 185 seconds. Login validation allows 35 seconds. Page tokens reject late replies after navigation. Owned WebSocket connections require declared `wss` hosts and network permission. Embedded-page gestures stay with WebView instead of opening the drawer.

### Read and ignore operations

`item.markRead` accepts `{itemId}` and calls `performItemAction(ctx)` with `ctx.action = {type:'markRead', itemId}`. The component performs official requests and rechecks status, returning `{itemId, done:true}` only after confirmation. The host rejects mismatched IDs, empty responses and stale results after account, package or lifecycle changes.

`item.ignore` accepts `{itemId, ignored}` and affects local presentation only. `ignoredItemIds` stores manual ignores; `restoredItemIds` preserves explicit restoration under `host.ignoreOverdue`. That option defaults false and applies only to overdue unfinished tasks. Choices survive sync and reset on account change or logout.

Ignored items remain restorable from archives but disappear from ordinary views, generated events, component-task widgets and reminders. Restoration does not replay historical new-content notices. Each component filters and lays out its widget from its archive and the rendering clock. The Notes checklist widget reads the app's own repository and is independent of component availability.

### Timetable placement

`host.schedule` controls types, date sources, offsets, default time, duration, completion and history. Placement changes display only, never real deadlines. Stable event IDs update existing generated items and remove stale ones without modifying manual events. `ScheduleEvent.source = {componentId, itemId}` returns clicks to the original content; removed sources retain their saved event text.

`host.feed` configures the generic fallback calendar. Owned pages receive full permitted archives and manage their own filtering.

## Notification receivers

Declare `extension.notificationReceiver:true` and the `notification.receive`, `storage.secure`, `network.proxy` permissions. Platform authorization, recipients, signing and formatting belong to the component; the host provides event queues, encrypted storage and transport.

| Page command | Contract |
|---|---|
| `notification.config.get` | Private encrypted configuration, excluded from public snapshots |
| `notification.config.save` | Save `{values, hosts, targets}`; targets summarize IDs, names, enablement and kinds |
| `notification.history` | Per-target delivery history |
| `notification.test` | User-initiated test for `{targetId}` |
| `notification.retry` | Retry eligible targets for `{id}`, retaining completed targets |
| `notification.fetch` | Restricted binding HTTP `{url, method, headers, body, timeoutMs}` |
| `ui.openExternal` | Open declared HTTPS documentation links after user interaction |

Events include `id, kind, title, body, sourceId, sourceName, itemId, createdAt, expiresAt`. Kinds are `class`, `memo.due`, `component.new` and `component.due`. Previews do not leave the app. Completed, read, ignored, invalidated or expired sources stop pending delivery.

The isolated runtime invokes `deliverNotifications(ctx)` with `secureConfiguration`, target-scoped `secureSessions` and delivery records in `notifications`. `ctx.notification.session(targetId,value)` persists encrypted session updates; false means the current binding is stale. `ctx.crypto.hmacSha256` returns Base64. `ctx.network.fetch` permits authorized HTTPS GET/POST, omits WebView cookies and rejects redirects and credential URLs. Binding call timeouts are bounded to 1–40 seconds.

Persist `sending` through `ctx.notification.receipt` before sending. False means the source or target changed and transmission must stop. Report `sent` only for explicit confirmation, `accepted` for platform acceptance without final confirmation, and `queued` for a resumable status check. `querying` resumes as `queued` after interruption; interrupted `sending` becomes `unknown`. Failed and unknown outcomes are separate; unknown delivery stops automatic resend until the user checks and requests retry. Queued checks query the existing operation rather than sending it again.

`ctx.mail.send({host, port:465, username, password, from, to, subject, text, messageId})` implements generic SMTP over TLS with certificate verification, authorized hosts, UTF-8 MIME text and header-injection rejection. Sender must match the authenticated account. Final SMTP 250 confirms server acceptance, not reading or inbox placement. Disconnect after body submission is ambiguous and must not trigger automatic resend.

Keystore AES-GCM uses component identity as associated data. `extension_secure_config` and `notification-outbox/` are excluded from backup and device transfer. Public snapshots contain target summaries only, never secrets, tokens or session context.

## Component-owned desktop widgets (API 9)

A component declares `extension.widgets`, with `id`, `title`, `description`, local HTML `entry`, `columns` and `rows`. Every widget file, stylesheet, script and business rule ships in its component ZIP. The host infers no desktop widget from task types or component names.

`window.CurSimpleWidget.state` contains the owner's normal archive, settings, manifest and rendering context (width, height, fontScale, language, timeZone and nowMillis). Secure channel configurations are excluded. The component filters and lays out its own content, then calls `ready([{x,y,width,height,action}])`; actions may open its feed or settings. Use a viewport meta tag (`width=device-width, initial-scale=1`), size the page to the supplied viewport, and call `ready` once layout is complete. Use timers or direct layout measurement rather than waiting for an animation frame in an offscreen WebView. Coordinates are CSS pixels, with at most 12 bounded regions. Rendering uses local package assets only, without a network or credential bridge.

The app picker lists declarations from installed, enabled owners. Each desktop instance binds one definition; the launcher configuration screen chooses among installed declarations. Package changes invalidate render ownership. Removal deletes the entry and stops its content; removing the last owner disables the shared desktop provider. The Android provider is a generic host surface and does not contain platform UI. In the system picker, add the component container and choose a declaration; the app picker lists each owned widget by its declared name. The shared provider defaults to four columns and two rows; launchers control the final cell placement, and components receive the actual content dimensions after launcher padding.
