# Plugin and component API

Plugins are independent ZIP bundles executed through a restricted WebView. The host owns installation, permissions, persistence and scheduling; each bundle owns its school or service protocol.

## API compatibility

The development host supports API **12**. Bundles declaring supported older APIs remain compatible; undeclared, invalid or newer APIs are rejected by the APK before package files or install records are written. The same check runs during local imports, market installs and upgrades; it cannot be bypassed by invoking installation directly. Failed compatibility checks preserve an existing installation.

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
| 10 | Native session requests and WebSockets for owned pages |
| 11 | Temporary reader orientation for owned pages |
| 12 | Component-provided app layouts drawn natively by the host |

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

The host injects entry code after an allowed page loads. `ctx` exposes controlled JavaScript operations, not arbitrary Android objects. Committed drafts are validated for course count, titles, weekdays, periods and weeks before becoming `TermSchedule`. The host records the executing installation's plugin ID on each imported timetable. Drafts cannot supply this origin; external timetable imports discard claimed origin metadata. Legacy timetables without an origin remain unclassified. A user can confirm their legacy importer for display settings only; this separate per-timetable binding does not invent recorded plugin execution. External imports clear both kinds of ownership and are marked as external.

### Import fallback

`webSession.manualUploadHintDelayMs` optionally offers manual upload after the configured delay on declared capture paths; login pages are excluded. `webSession.retryIncompleteAfterMs` opts a schedule importer into throttled retries after an incomplete run. Active runs remain exclusive, manual retry clears a previous completed draft, and upload waits for the new draft. Both options default to absent and are clamped to 1–60 seconds by the host.

An entry script may return `{ hold: true }` when it cannot finish without the user, for example a page that offers several curricula to choose from. The host then stops automatic retries for that page instead of looping, and keeps the manual upload action available. `ctx.manual` is `true` only on a run the user started, so an entry script can branch on it. Pressing upload releases the hold and runs the entry script again.

### Optional timetable rows

Schedule bundles can declare `timetableRows` and `timetableRowFormat` in `ui/schedule.json` to expose a separate native Plugin settings page. Each row has `id`, `title`, optional locale-keyed `titles`, `startNode`, `endNode`, `slotLabel`, `startTime`, `endTime` and `defaultVisible` (default `true`). `timetableRowFormat` contains the complete expected `ClassSlotTime` list using the same fields as `datapack/timing.json`'s `slotTimes`.

A display setting applies only when the active timetable's recorded importer or explicitly confirmed legacy display binding matches the declaring plugin and every period range, label and clock time matches the complete format. Missing, additional or changed slots prevent hiding; every optional row must belong to that format. Settings update the local display immediately without altering schedule or timing data. Actual courses, including manual edits and rescheduled meetings, keep their rows visible. Installing a plugin or selecting it for a future import does not assign it ownership of an existing timetable.

Legacy confirmation is offered only for unclassified local data with a complete matching format. It writes a reversible display binding to the reviewed term, checks the stored timetable against the reviewed snapshot to reject concurrent replacements, and does not change courses or timing. Imported shared codes, QR and AI data cannot acquire this binding.

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
| `login.check` | Validate through the component entry; `navigate:false` returns data without leaving; optional `expectedAccountId` verifies the confirmed identity. First login creates its archive, while duplicate confirmation preserves the accepted session |
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

Persist `sending` through `ctx.notification.receipt` before sending. False means the source or target changed and transmission must stop. Report `sent` only for explicit confirmation, `accepted` for platform acceptance without final confirmation, and `queued` for a resumable status check. `querying` resumes as `queued` after interruption; interrupted `sending` becomes `unknown`. Failed and unknown outcomes are separate; unknown delivery stops automatic resend until the user checks and requests retry. Queued checks query the existing operation rather than sending it again. A failed receipt may include an optional fifth argument `retryAt` (epoch milliseconds) in `ctx.notification.receipt(messageId, targetId, "failed", error, retryAt)`. The host bounds it to the next hour, persists it per target, and waits until that time without exhausting the normal retry limit. Expired events still stop delivery; a deferred receipt never implies platform acceptance.

`ctx.mail.send({host, port:465, username, password, from, to, subject, text, messageId})` implements generic SMTP over TLS with certificate verification, authorized hosts, UTF-8 MIME text and header-injection rejection. Sender must match the authenticated account. Final SMTP 250 confirms server acceptance, not reading or inbox placement. Disconnect after body submission is ambiguous and must not trigger automatic resend.

Keystore AES-GCM uses component identity as associated data. `extension_secure_config` and `notification-outbox/` are excluded from backup and device transfer. Public snapshots contain target summaries only, never secrets, tokens or session context.

## Component-owned desktop widgets (API 9)

A component declares `extension.widgets`, with `id`, `title`, `description`, local HTML `entry`, `columns` and `rows`. Every widget file, stylesheet, script and business rule ships in its component ZIP. The host infers no desktop widget from task types or component names.

`window.CurSimpleWidget.state` contains the owner's normal archive, settings, manifest and rendering context (width, height, fontScale, language, timeZone and nowMillis). Secure channel configurations are excluded. The component filters and lays out its own content, then calls `ready([{x,y,width,height,action}])`; actions may open its feed or settings. Use a viewport meta tag (`width=device-width, initial-scale=1`), size the page to the supplied viewport, and call `ready` once layout is complete. Use timers or direct layout measurement rather than waiting for an animation frame in an offscreen WebView. Coordinates are CSS pixels, with at most 12 bounded regions. Rendering uses local package assets only, without a network or credential bridge.

The app picker lists declarations from installed, enabled owners. Each declaration takes its own generic provider slot, so every component widget is a separate entry in the system picker and binds to its owner when placed, with no chooser. On Android 15 and later the host publishes the owner's render as the picker preview; older launchers, or launchers without generated previews, show the app icon instead of a host placeholder. Slots are disabled until assigned, and removing or disabling the owner frees its slot. Package changes invalidate render ownership. The provider label is fixed in the app and cannot carry the component's name. Providers default to four columns and two rows; launchers control the final cell placement, and components receive the actual content dimensions after launcher padding.

### Native session networking (API 10)

Owned pages can use `CurSimpleComponent.capabilities.sessionFetch` and `nativeWebSocket`. Authentication protocols remain inside each component; these commands provide generic transport only.

| Command | Payload and result |
|---|---|
| `network.session.fetch` | `{requestId,url,method,headers,body,referrer,timeoutMs}` returns `{status,body,contentType}`. GET/POST, HTTPS, current origin only, declared `network.fetch` and `web.read_cookies`. Same-origin redirects are checked; response cookies are persisted before reply. |
| `network.session.cancel` | `{requestId}` cancels the matching request. |
| `network.socket.open` | `{url}` returns an opaque `socketId`. WSS and declared hosts only; the host supplies the page origin and user agent. Same-origin cookies require `web.read_cookies`. |
| `network.socket.send` | `{socketId,text}` sends a bounded text frame. |
| `network.socket.poll` | `{socketId}` returns `{events:[...]}`. Events are `open`, `message` with `data`, `close`, or `error`. The host waits up to 25 seconds for frames, so receiving messages does not depend on page timers. |
| `network.socket.close` | `{socketId}` closes the connection and clears buffered frames. |

Sockets use native 20-second ping/pong, bounded queues, at most three connections per page and a five-minute lifetime. Leaving a page, changing its origin or removing the component closes its connections. Session request/response data and socket frames are not diagnostic log content. Components must verify account identity and request `login.check` before claiming a saved login. API 3–9 packages remain compatible with API 10 hosts.

### Reader orientation (API 11)

Owned pages may check `CurSimpleComponent.capabilities.orientation` and call `ui.orientation` with `{mode:"portrait"}`, `{mode:"landscape"}` or `{mode:"restore"}`. The host requests sensor portrait/landscape orientation and restores the Activity's previous request when the page leaves or closes. Requests are temporary and do not change user preferences. The Activity handles orientation and size changes in place so owned readers retain page and gesture state. Windowing policies may override an orientation request. API 3–10 packages remain compatible.

A successful `login.check` schedules one synchronization per saved session in the application scope, before optional navigation. Repeated confirmation does not repeat that synchronization, and leaving the login document does not cancel it. Results continue to reach active owned pages through the normal archive subscription.

## App layouts (API 12)

The host draws every layout natively; a component only declares which shells it may use and tells the host which one the user picked. Without a layout component the app uses its own sidebar, which users customize in Settings → Sidebar.

Declare shells in `extension.layouts`:

```json
"layouts": [{ "id": "bottom-bar", "title": "Bottom bar", "shell": "bottomBar" }]
```

At most 8 entries, each with a unique safe `id`, a non-blank `title` and a `shell` of 1–32 ASCII letters. Unknown shells install, so packages can prepare for later hosts, but they cannot be applied.

`CurSimpleComponent.capabilities.layouts` reports `{shells, destinations, maxBarItems}`. Treat a missing field as a host without layout support.

| Command | Payload and result |
|---|---|
| `layout.apply` | `{shell:"drawer"}` hands the app back to the sidebar (only when the caller owns the current layout). `{shell:"bottomBar", items:[...]}` sets the leading bar entries; the host appends the "me" tab. Returns the same value as `layout.current`. Invalid requests fail with a readable message and leave the layout unchanged. |
| `layout.current` | Returns `{shell, items, ownedByCaller}` for the layout actually drawn; `shell` is `drawer` when the sidebar is in use. |

Both commands require a declared `layouts` section; `layout.apply` also requires the page to be in the foreground. Bottom bar rules: `items` starts with `schedule`, ids are known and unique, and with "me" the bar has 3 to `maxBarItems` (5) entries. Destinations that are not on the bar are listed on the "me" page in the fixed order below, so Schedule and Settings stay reachable in every configuration.

| Destination id | Page |
|---|---|
| `schedule` | Timetable (always first on the bar) |
| `courses` | Courses |
| `memos` | Notes |
| `components` | Components hub: enabled components shown in navigation, plus the component market (bottom bar only) |
| `plugins` | Plugins and market |
| `reminders` | Reminders |
| `settings` | Settings |
| `about` | About |

Quick actions use `action.theme`, `action.background`, `action.overrides` and `action.update`; the host shows them at the bottom of the sidebar and as a row on the "me" page.

The applied layout is stored with the owner's id and included in in-app backups. It is used only while the owner is installed, enabled, compatible and still declares that shell; otherwise the app shows the sidebar. Disabling and re-enabling the owner restores its layout, and removing it clears the record. The last component to call `layout.apply` owns the layout. Settings → Sidebar always offers a way back to the sidebar. API 3–11 packages remain compatible.
