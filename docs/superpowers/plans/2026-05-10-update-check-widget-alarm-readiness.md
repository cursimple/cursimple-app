# Update Check Widget Alarm Readiness Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make automatic updates behave as automatic update checks only, add update/ignore choices for discovered versions, and record widget refresh alarm registration readiness.

**Architecture:** Reuse the existing GitHub Release update checker and reminder synchronization pipeline. Add small data fields for release notes and ignored update version, move update checks into Settings, and derive widget registration readiness from `SystemAlarmSyncSummary`.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, AlarmManager, existing reminder coordinator, GitHub Release `update.json`.

---

## Chunk 1: Update Check Semantics

### Task 1: Persist Ignored Update Version

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppPreferencesViewModel.kt`

- [ ] Add an ignored update `versionCode` preference.
- [ ] Expose a setter for ignoring a discovered version.
- [ ] Keep default as `null`/unset.

### Task 2: Carry Release Notes

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/update/AppUpdateModels.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/update/AppUpdateChecker.kt`

- [ ] Add release notes to `AppUpdateInfo`.
- [ ] Prefer `update.json` changelog/release notes fields.
- [ ] Fall back to GitHub Release body.

### Task 3: Settings Update UI

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AboutScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [ ] Move the update check UI to Settings.
- [ ] Rename automatic update copy to automatic update check.
- [ ] Automatic checks must not download.
- [ ] Show a dialog with version, changes, and buttons: update / ignore this update.
- [ ] Manual checks must still show ignored versions.

## Chunk 2: Widget Alarm Registration Readiness

### Task 4: Derive Readiness

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetSystemAlarmSynchronizer.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`
- Test: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/WidgetAlarmRegistrationReadinessTest.kt`

- [ ] Add a small readiness model derived from `SystemAlarmSyncSummary`.
- [ ] Map existing reminders to ready.
- [ ] Map newly created reminders to repaired/registered.
- [ ] Map failures and unrepresentable reminders to failed.
- [ ] Log the readiness status after each 5-minute refresh and worker refresh.

## Chunk 3: Verification

### Task 5: Test And Build

**Files:**
- Modify tests as needed.

- [ ] Run focused app/core-data/widget tests.
- [ ] Run `./gradlew testDebugUnitTest` if practical.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Summarize any blocked checks without committing.
