# Development

[中文](../README_dev.md)

CurSimple is a modular Android timetable app. School and service protocols belong in independently distributed plugins or extension components.

## Build and test

Use JDK 17 and Android SDK platform 36. Configure `local.properties` with your SDK path, or set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest lintDebug
node --test site/tests/*.test.cjs
node site/check-links.cjs
python3 -m unittest discover -s scripts/tests
```

Debug APKs use `com.x500x.cursimple.ci` and the debug certificate. They install alongside the release app, `com.x500x.cursimple`, with separate data. Output is under `app/build/outputs/apk/debug/`; the universal APK supports every configured ABI.

Instrumentation tests that modify device state require an explicit `*Qa=true` argument and a dedicated emulator. Widget tests also require widget-binding access. These tests restore their temporary preferences and fixtures; do not run them on a personal device.

## Modules

| Module | Responsibility |
|---|---|
| `app` | Dependency wiring, navigation, notifications, updates and downloads |
| `core-kernel` | Shared models, schedule/date resolution and pure contracts |
| `core-data` | Persistent schedules, notes, preferences and notification outbox |
| `core-plugin` | Bundle validation, installation, permissions and GitHub access |
| `core-reminder` | Rules, planning, dispatch and device permission guidance |
| `feature-schedule` | Timetable, notes, course editing and import workflows |
| `feature-plugin` | Marketplace, component pages and restricted WebView runtime |
| `feature-widget` | RemoteViews providers, rendering and refresh scheduling |

Keep platform adapters out of the host. The development host supports plugin API **9**; compatibility is defined by `PluginApiVersion` and each bundle's manifest, independently of app version names. See the [plugin API guide](plugin-system.md).

## Signing and releases

Debug builds and JVM tests need no private signing files. Release builds require the values in `keystore.example.properties`, supplied through ignored `keystore.properties` or matching environment variables:

- `CLASS_VIEWER_KEYSTORE_FILE`
- `CLASS_VIEWER_KEYSTORE_PASSWORD`
- `CLASS_VIEWER_KEY_ALIAS`
- `CLASS_VIEWER_KEY_PASSWORD`

Use forward slashes in properties paths. `scripts/load-signing-env.ps1` optionally decodes `CLASS_VIEWER_KEYSTORE_BASE64` from the environment; it does not fetch credentials. Keep certificates, passwords and local configuration outside Git.

```sh
./gradlew :app:assembleRelease
```

Maintain `app.versionCode`, `app.versionName` and `app.releaseChannel` in `gradle.properties`. A `v<app.versionName>` tag triggers the release workflow. Channel metadata determines GitHub prerelease status, including tags without a beta suffix. Release signing checks compare all five APK certificates with the published certificate; temporary local signing cannot replace it.

| Workflow | Purpose |
|---|---|
| `.github/workflows/android-ci.yml` | Build and tests for pull requests and main-branch changes |
| `.github/workflows/android-release.yml` | Signed APKs, release metadata and stable/beta update feeds |
| `.github/workflows/pages.yml` | Validate and deploy the static website |

## Release announcements

Store release text in `docs/release-notes/v<version>.md`, with images under `docs/release-notes/images/v<version>/`. Tagged raw URLs keep announcement assets tied to their release. Image-bearing `##` sections become highlight pages; remaining text forms the final page.

Developer tools can preview a draft from the private `release-preview` directory. Debug-only bundled previews use `app/src/debug/assets/release-preview/next-local.md`. Private drafts take precedence. Release builds exclude debug preview assets.

Update checks run silently and report through badges. Users select beta updates in the check dialog or settings. Notes and eligible images are prefetched into local cache; metered-network prefetch stores text only. See [networking](networking.md) for transfer and cache behavior.

## Calendar data

Foreground calendar refresh has a one-hour cooldown and retains cached data after failure. Published holidays cover the current year and two following years when available. Festival dates are generated into `data/calendar/cn-festivals.json`:

```sh
python3 -m pip install sxtwl
python3 scripts/gen_cn_calendar.py 2024 2060
```

Data-only updates do not require an app release. Teaching-week anchors remain Monday-based even when the display starts on Sunday.

## Maintenance conventions

Write concise English comments explaining constraints or non-obvious behavior. Keep edit histories in commits and user-facing changes in release notes. Use generic institutions and accounts in fixtures. Preserve localized product text and technical identifiers such as IANA time zones.

Before removing code, check call sites, reflection, manifest registrations, persisted formats and plugin bridges. Keep compatibility paths that still read supported user data. Exclude caches, temporary reports and generated build output from the repository.

Additional guides: [website](../site/README.md), [today overview](today-overview.md), [holiday alarm policy](holiday-alarm-policy.md).
