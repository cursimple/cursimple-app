<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="112" alt="CurSimple">

# CurSimple

**Timetable, reminders and home screen widgets for the whole term.**

An open-source Android timetable app built on a microkernel architecture. Each school portal is handled by its own plugin, updated independently of the app.

[![CI](https://github.com/cursimple/cursimple-app/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cursimple/cursimple-app/actions/workflows/android-ci.yml)
[![Release](https://github.com/cursimple/cursimple-app/actions/workflows/android-release.yml/badge.svg)](https://github.com/cursimple/cursimple-app/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/cursimple/cursimple-app?include_prereleases&sort=semver)](https://github.com/cursimple/cursimple-app/releases)
[![Downloads](https://img.shields.io/github/downloads/cursimple/cursimple-app/total)](https://github.com/cursimple/cursimple-app/releases)

[![License](https://img.shields.io/github/license/cursimple/cursimple-app)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--36)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[Download](#download) · [Features](#features) · [Plugins](#plugin-system) · [Build from source](#build-from-source) · [中文](README.md)

</div>

---

## Screenshots

<div align="center">

| Week view | Day view | Notes |
|:--:|:--:|:--:|
| <img src="docs/screenshots/week.png" width="230"> | <img src="docs/screenshots/day.png" width="230"> | <img src="docs/screenshots/memo.png" width="230"> |
| Every period on one screen, with your own events placed at their real times | Swipe between days; events sit between classes | One notebook per course: checklists, priorities and due dates |

| Pinch to zoom | Class notice | Settings |
|:--:|:--:|:--:|
| <img src="docs/screenshots/zoom.png" width="230"> | <img src="docs/screenshots/notice.png" width="230"> | <img src="docs/screenshots/settings.png" width="230"> |
| Zoom up to 300% and pan freely; headers stay pinned | Course, time and room just before class, with a status bar chip | Search jumps straight to a setting; pick your own quick settings |

| Reminder settings | Manage list | Plugin marketplace |
|:--:|:--:|:--:|
| <img src="docs/screenshots/reminder.png" width="230"> | <img src="docs/screenshots/library.png" width="230"> | <img src="docs/screenshots/plugin.png" width="230"> |
| Class notices and alarms managed separately, with a per-device setup guide | Courses and events in one searchable list | Browse and install school plugins from a GitHub registry |

</div>

## Features

### Timetable

| Feature | Details |
|---|---|
| Week / day view | The week view fits every period on one screen by default; the day view pages sideways and peeks at neighbouring days |
| Events | Meetings, clubs or a game of badminton go straight onto the timetable at their real start and end times. When they overlap a class they sit beside it, several overlapping events fold into a "⋯" you can open, and events outside class hours get their own band that disappears when the event is deleted |
| Pinch to zoom | Once enabled in settings, zoom up to 300% and drag in any direction; the day header and period column stay pinned |
| Multi-period and alternating weeks | Back-to-back periods render as one block; odd, even and arbitrary week patterns are supported |
| Make-up days and cancellations | Swap a day's classes for another day's, take a whole day off, or cancel a single course |
| Drag to reschedule | Long-press a course card and drop it on another day or period, confirm before it moves; drag it back to undo |
| Holidays and make-up workdays | The public holiday schedule refreshes silently whenever the app opens with a connection; extra make-up days can be added by hand |
| Appearance | Text, header, cards, grid lines and a croppable background image; every colour is picked from a palette |
| Shrink long titles | Font size follows the space actually left in the cell, so the title and room fit whenever they can |

### Notes

The **Notes** page in the drawer gives every course its own notebook, plus one for anything else. Notes support checklists you can tick right on the card (with progress), priorities, pinning and due dates; the overview shows what's open, due today and overdue at a glance. Editing is what-you-see-is-what-you-get: lists, headings, quotes and bold render as you type.

### Reminders

| Feature | Details |
|---|---|
| Class notices | A notice with the course, period, time and room just before class. Three styles: system, branded card and overlay; the overlay supports blur and animation and can be swiped away |
| Status bar chip | On systems with Live Updates (Android 16, ColorOS 16, One UI 8.5 and later, among others) a class chip sits in the status bar and the notice is pinned to the top of the shade; Xiaomi HyperOS uses focus notifications |
| No missed notices | If the reminder time was missed but class hasn't started, the notice is posted when you open the app; minutes are counted from the real clock |
| Silent guard | No persistent "guarding" notification: after you leave the app, periodic checks re-register any notices or alarms the system cleared |
| Setup guide | Lists exactly what this phone needs allowed (notifications, heads-up, overlay, chip, background pop-ups) and opens each system page directly |
| Alarms | Exact alarms generated from "period + condition + action" rules; system ringtones or local audio, ring, vibrate or both, with an optional heads-up before the alarm rings |
| Silence during class | Enters silent mode on schedule and restores the previous ringer mode after class |

### Home screen widgets

Three widgets: today's timetable, next class and reminders. Tapping empty space opens the app too. Refresh is layered four ways: the system period, a WorkManager period, the alarm guard chain, and exact refreshes aligned to period boundaries (5 minutes before class, class start, class end).

### Settings

The search box at the top finds any setting by name, page or keyword. **Quick settings** is its own block: tap + to choose what goes in, or long-press any item below and drag it up; long-press a tile to reorder it. The home page switches between list and grid layouts.

### Data

| Feature | Details |
|---|---|
| Import and export | Local JSON backup (including events and notes), timetable exchange by QR code or passphrase, timetable image export, `.ics` export |
| WebDAV | Back up to and restore from a self-hosted or third-party WebDAV server |
| AI screenshot import | Recognise courses from a screenshot or photo of a timetable; bring your own API |
| System calendar | Write the whole term into the phone's calendar, undoable in one tap |
| Term profiles | Several terms side by side, each with its own timetable, periods and week count |

### Elsewhere

- **Languages**: Simplified Chinese, Traditional Chinese and English, switched instantly inside the app, independent of the system language
- **Time zone**: Can be set independently of the device, so online classes across time zones don't need a system change
- **Updates**: Once a new version is found, tap **Update** to download and install it; only stable releases by default, prereleases after opting in to beta updates
- **Release notes**: The first launch after an update pages through the highlights of that version
- **Per-ABI builds**: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` and a `universal` APK

## Download

Grab the APK for your device from [Releases](https://github.com/cursimple/cursimple-app/releases):

| File | Device |
|---|---|
| `app-arm64-v8a-release.apk` | Modern 64-bit ARM phones — pick this one unless you know otherwise |
| `app-armeabi-v7a-release.apk` | Older 32-bit ARM devices |
| `app-x86_64-release.apk` | Intel devices and emulators |
| `app-universal-release.apk` | Works everywhere, at a larger size |

Versions with a `-beta` or `-alpha` suffix are prereleases and are marked as such on GitHub.

After installing:

1. Set the term start date (the hint button on the timetable screen, or the drawer)
2. Open **Plugins** and install the plugin for your school from the marketplace
3. Sign in to the school portal through the plugin and sync your timetable
4. Open **Settings → Class notice → Notification setup guide** and allow what it lists, so notices show up on time

If no plugin covers your school, add courses and events by hand in **Manage list**, or import them from a QR code or an image.

## Plugin system

School portals differ wildly, so CurSimple keeps the scraping logic in plugins. A plugin is a `manifest.json` plus a JS bundle that runs inside an in-app WebView session, handling sign-in, fetching and parsing, and finally emitting the shared timetable model. Plugins ship on their own schedule, so a portal redesign only needs a plugin update.

### Installing a plugin

1. Open **Plugins** and browse the marketplace
2. Each card shows the name, author, star count, description and latest version
3. Open one for details, then choose Install or View on GitHub
4. Local bundles can be installed with Import ZIP

The marketplace index comes from [cursimple/cursimple-plugins](https://github.com/cursimple/cursimple-plugins).

### Bundle requirements

Every plugin repository needs at least one Release carrying:

- `manifest.json`, declaring the plugin metadata, whose `filename` points at the bundle
- the bundle file that `filename` names

The app reads `releases/latest/download/manifest.json` first, then downloads the bundle named by `filename`. GitHub's generated Source code archives are never treated as bundles.

To write your own, see the [plugin guide](docs/plugin-system.md).

## Build from source

### Requirements

- JDK 17
- Android SDK with `platforms;android-36`

### Commands

```bash
# Debug (applicationId com.x500x.cursimple.ci, installs alongside release)
./gradlew assembleDebug

# Release (four ABI splits plus universal)
./gradlew assembleRelease

# Unit tests and static analysis
./gradlew testDebugUnitTest lintDebug
```

Release signing is configured through `keystore.properties`; see `keystore.example.properties`:

```properties
CLASS_VIEWER_KEYSTORE_FILE=.signing/class-viewer.jks
CLASS_VIEWER_KEYSTORE_PASSWORD=replace with the keystore password
CLASS_VIEWER_KEY_ALIAS=replace with the key alias
CLASS_VIEWER_KEY_PASSWORD=replace with the key password
```

The version lives in exactly one place, `gradle.properties`:

```properties
app.versionCode=30
app.versionName=0.7.4
```

### Modules

```
app              App shell, dependency wiring, entry screens, update checks and download mirrors
core-kernel      Shared timetable model and core contracts
core-plugin      Plugin manifest, installation, components, web session model and GitHub registry
core-data        DataStore repositories
core-reminder    Reminder rules, planning and dispatch backends
feature-schedule Timetable screens and sync logic
feature-plugin   Marketplace UI and WebView sessions
feature-widget   Home screen widgets and scheduled refresh
```

### Continuous integration

| Workflow | Trigger | What it does |
|---|---|---|
| `android-ci.yml` | Pull requests and pushes to `main` | Compile, unit tests, Lint |
| `android-release.yml` | Pushing a `v*` tag | Verifies the tag matches `app.versionName`, builds every ABI, generates `update.json`, and marks the release as a prerelease based on the version suffix |

Deeper development notes live in the [developer documentation](README_dev.md).

## Troubleshooting

<details>
<summary><b>Alarms do not ring, or reminders are late</b></summary>

Start with **Settings → Class notice → Notification setup guide** and allow whatever it lists as missing. Then check alarm access under **Settings → Reminders and permissions → Permissions**, which also has per-brand shortcuts for autostart, background pop-ups and battery policy. On some Chinese OEM systems, swiping the app away from Recents is the same as a force stop and clears its alarms until the app is opened again; locking CurSimple in Recents avoids that.

</details>

<details>
<summary><b>A plugin fails to install</b></summary>

Check connectivity first, then confirm the plugin repository has a valid Release with both `manifest.json` and the bundle it names. On restricted networks the app races several mirrors in parallel; if all of them fail, try switching between Wi-Fi and mobile data.

</details>

<details>
<summary><b>The timetable is empty, or week numbers look wrong</b></summary>

The current week and term appear at the top of the timetable screen. A blank week number means the term start date is not set yet — use the hint button to set it. If a plugin synced but no courses appeared, confirm the plugin is enabled and trigger the sync again from its detail screen.

</details>

<details>
<summary><b>Widgets do not refresh</b></summary>

Allow the app to run in the background, then check **Settings → Quick settings → Widgets**. If it still lags, remove the widget and add it again.

</details>

## Feedback and contributing

- Bugs and feature requests: [GitHub Issues](https://github.com/cursimple/cursimple-app/issues)
- Questions and discussion: [GitHub Discussions](https://github.com/cursimple/cursimple-app/discussions)
- Plugin submissions: open a request on [cursimple-plugins](https://github.com/cursimple/cursimple-plugins)

## License

[MIT License](LICENSE) · Copyright © 2026 the CurSimple Team
