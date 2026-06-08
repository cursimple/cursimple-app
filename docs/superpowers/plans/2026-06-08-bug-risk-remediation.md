# Bug Risk Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the confirmed security, reminder, data integrity, widget, update, and build reliability issues recorded in `docs/analysis/2026-06-08-bug-findings.md`.

**Architecture:** Keep fixes scoped to the owning modules. Prefer strict validation and explicit user-facing errors over fallback behavior. Do not introduce temporary fallback designs as formal architecture.

**Tech Stack:** Android, Kotlin, Jetpack Compose, Glance/AppWidget, DataStore Preferences, OkHttp, WebView, Gradle Kotlin DSL, JUnit4.

**Commit Policy:** This plan intentionally contains no git commit steps. Commit only after the user explicitly asks for it.

---

## Chunk 0: Build Workflow Reliability

### Task 0: Decouple Debug/Test Configuration From Private Release Signing

**Findings:** 2

**Files:**
- Modify: `app/build.gradle.kts`
- Modify if needed: `README_dev.md`
- Test or document: CI smoke command in project docs

- [ ] Make release signing required only for release signing tasks.
- [ ] Let debug builds and unit tests use the default debug signing config or a non-secret local debug config.
- [ ] Keep release signing failure explicit and early for release packaging.
- [ ] Document required environment variables for release builds.
- [ ] Complete this task before running the Gradle verification commands in later chunks on a clean checkout.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew tasks :core-kernel:testDebugUnitTest :app:testDebugUnitTest'
```

## Chunk 1: Security And Package Integrity

### Task 1: Restrict Cleartext Network Use

**Findings:** 1

**Files:**
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportClient.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/webdav/WebDavClient.kt`
- Modify: `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreen.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/security/NetworkSecurityConfigTest.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/ai/AiScheduleImportClientTest.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/webdav/WebDavClientTest.kt`

- [ ] Remove global `<base-config cleartextTrafficPermitted="true" />`.
- [ ] Reject `http://` AI import and WebDAV URLs by default with clear user-facing messages.
- [ ] Change WebView mixed content from `MIXED_CONTENT_ALWAYS_ALLOW` to a restricted mode unless a plugin-level, reviewed exception is added later.
- [ ] Add manifest/resource tests proving release config does not globally allow cleartext.
- [ ] Add AI/WebDAV tests proving HTTP URLs are rejected and HTTPS URLs are accepted.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest :feature-plugin:testDebugUnitTest'
```

### Task 2: Exclude Plaintext Credentials From Android Auto Backup

**Findings:** 20

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Create or modify: `app/src/main/res/xml/backup_rules.xml`
- Test: `app/src/test/java/com/x500x/cursimple/app/BackupRulesManifestTest.kt`

- [ ] Add `android:dataExtractionRules` for Android 12+ backup/transfer rules.
- [ ] Add `android:fullBackupContent` for pre-Android 12 backup rules.
- [ ] Exclude credential-bearing DataStore files, at minimum `datastore/user_preferences.preferences_pb`, unless credentials are first moved to a separate excluded store.
- [ ] Keep the explicit user-created app backup flow separate from OS Auto Backup; do not silently include secrets in system backup.
- [ ] Add manifest/XML tests proving both backup rule resources are referenced and exclude credential storage.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest'
```

### Task 3: Harden Plugin Package Integrity

**Findings:** 4, 5, 6

**Files:**
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/security/PluginVerification.kt`
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReader.kt`
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/storage/PluginFileStore.kt`
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/install/PluginInstaller.kt`
- Test: `core-plugin/src/test/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReaderTest.kt`

- [ ] Require non-empty checksum manifests.
- [ ] Require checksum coverage for every package file except `checksums.json` and optional signature metadata.
- [ ] Reject checksum paths that are not present in the package.
- [ ] Validate digest format for SHA-256 and reject unsupported algorithms unless deliberately supported.
- [ ] Validate plugin `manifest.id` with a safe character pattern such as `[A-Za-z0-9._-]+`.
- [ ] Add canonical path containment checks before `deleteRecursively()` and before writing package files.
- [ ] Persist the normalized entry path or reject non-normalized `manifest.entry` values consistently.
- [ ] Add tests for empty checksums, partial checksums, path traversal IDs, slash-containing IDs, and Windows-style entry paths.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-plugin:testDebugUnitTest'
```

### Task 4: Harden Component Package Integrity

**Findings:** 10, 15

**Files:**
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt`
- Modify: `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/ComponentMarketScreen.kt`
- Test: `core-plugin/src/test/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstallerTest.kt`
- Test: `feature-plugin/src/test/java/com/x500x/cursimple/feature/plugin/ComponentMarketScreenTest.kt`

- [ ] Reject component packages containing payload files not listed in `manifest.files` when `manifest.files` is non-empty.
- [ ] Keep canonical root containment checks for every installed file.
- [ ] Change component market item key from `id` to a stable composite key such as `id:version:abi:downloadUrl`.
- [ ] Add tests for extra unlisted component files and duplicate component IDs with different ABI/version.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-plugin:testDebugUnitTest :feature-plugin:testDebugUnitTest'
```

## Chunk 2: Reminder Correctness

### Task 5: Include First-Course Rules In Alarm Sync

**Findings:** 8

**Files:**
- Modify: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderCoordinator.kt`
- Modify if needed: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderPlanner.kt`
- Test: `core-reminder/src/test/java/com/x500x/cursimple/core/reminder/SystemAlarmRegistryTest.kt`
- Test: `core-reminder/src/test/java/com/x500x/cursimple/core/reminder/ReminderPlannerTest.kt`

- [ ] Update `syncAlarmsForWindow(...)` to expand enabled `FirstCourseOfPeriod` rules as well as label rules.
- [ ] Pass temporary schedule overrides and custom occupancies into first-course expansion.
- [ ] Ensure stale cleanup uses the full planned-key set so old first-course alarms are dismissed when rules change.
- [ ] Add tests proving first-course rules create alarms and rule edits remove stale alarms.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'
```

### Task 6: Unify Exam Reminder State And Muting

**Findings:** 9

**Files:**
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt`
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt`
- Modify if needed: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/LabelReminderRuleEvaluator.kt`
- Test: `feature-schedule/src/test/java/com/x500x/cursimple/feature/schedule/ReminderRuleEditorValidationTest.kt`
- Test or create: `core-reminder/src/test/java/com/x500x/cursimple/core/reminder/LabelReminderRuleEvaluatorTest.kt`

- [ ] Choose one canonical model for exam reminders. Prefer continuing with `LabelRule` because the current creation path already uses it.
- [ ] Make exam detail enabled-state detection check exam label rules, not the legacy `Exam` rule only.
- [ ] Replace `updateExamMute(...)` logic with label-rule-aware muting or an explicit per-course suppression model.
- [ ] Add tests proving one exam can be muted while other exam reminders continue to plan.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-schedule:testDebugUnitTest :core-reminder:testDebugUnitTest'
```

### Task 7: Fix Temporary Make-Up Reminder Weekday Text

**Findings:** 7

**Files:**
- Modify: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderPlanner.kt`
- Test: `core-reminder/src/test/java/com/x500x/cursimple/core/reminder/ReminderPlannerTest.kt`

- [ ] Format reminder title and message weekdays from `courseDate.dayOfWeek.value`.
- [ ] Preserve original source weekday only as optional extra context if the UI wants it later.
- [ ] Extend the existing temporary make-up reminder test to assert actual target weekday text.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'
```

### Task 8: Make App Timezone Semantics Explicit

**Findings:** 3

**Files:**
- Modify: `core-kernel/src/main/java/com/x500x/cursimple/core/kernel/time/BeijingTime.kt`
- Modify callers if needed: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/time/LocalAppZone.kt`
- Modify callers if needed: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSource.kt`
- Modify callers if needed: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/NextCourseDataSource.kt`
- Modify callers if needed: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ReminderDataSource.kt`
- Test: `core-kernel/src/test/java/com/x500x/cursimple/core/kernel/time/BeijingTimeTest.kt`

- [ ] Decide whether the app zone is always `Asia/Shanghai` or truly the device default. Rename or implement accordingly.
- [ ] Make `today(zone)`, `todayIn(zone)`, `nowTimeIn(zone)`, `nowDateTimeIn(zone)`, `nowMillis(zone)`, and `dayOfWeek(zone)` honor the passed zone.
- [ ] Avoid process-global forced time leakage where possible; prefer injectable clock/time provider in data sources over mutating global state.
- [ ] Add timezone boundary tests around midnight.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-kernel:testDebugUnitTest :feature-widget:testDebugUnitTest'
```

## Chunk 3: Data Resilience And Widget Consistency

### Task 9: Make Reminder Repository Bad-Data Tolerant

**Findings:** 17

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/reminder/DataStoreReminderRepository.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/ReminderRepositoryTest.kt`

- [ ] Add safe decode helpers using `runCatching`.
- [ ] Return empty lists or quarantined data on decode failure while logging the failure.
- [ ] Ensure save/delete methods can overwrite bad existing JSON.
- [ ] Add tests for bad JSON in each reminder key.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'
```

### Task 10: Preserve Persisted URI Permissions During Restore

**Findings:** 18

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/WidgetPreferencesTest.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/UserPreferencesTest.kt`

- [ ] During restore, capture old URI values and restored URI values.
- [ ] Release only old URIs that are no longer referenced after restore.
- [ ] Add test seams for permission release if direct ContentResolver verification is not practical.
- [ ] Add tests for same URI, changed URI, and removed URI restore cases.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'
```

### Task 11: Fix Term Start Mirroring And Widget Week Consistency

**Findings:** 19

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/TermProfileViewModel.kt`
- Modify: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/NextCourseDataSource.kt`
- Test or create: `app/src/test/java/com/x500x/cursimple/app/TermProfileViewModelTest.kt`
- Test or create: `feature-widget/src/test/java/com/x500x/cursimple/feature/widget/NextCourseDataSourceTest.kt`

- [ ] After deleting a term, read the repository's actual active term id and mirror only that term's start date.
- [ ] Do not change mirrored user preference start date when deleting a non-active term.
- [ ] Make `NextCourseDataSource` resolve active term start date the same way `ScheduleWidgetDataSource` does.
- [ ] Add tests for deleting active and non-active terms.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest :feature-widget:testDebugUnitTest'
```

### Task 12: Fix Widget Reminder Badges For Label Rules

**Findings:** 16

**Files:**
- Modify: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSource.kt`
- Test or create: `feature-widget/src/test/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSourceTest.kt`

- [ ] Replace direct `course.slotLabelOverride` comparison with `course.reminderSlotLabel(timingProfile)`.
- [ ] Keep behavior for courses that explicitly set `slotLabelOverride`.
- [ ] Add tests for timing-profile labels and override labels.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:testDebugUnitTest'
```

## Chunk 4: Update And Resource Reliability

### Task 13: Fix Update Metadata And ABI Selection

**Findings:** 11, 12

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/update/AppUpdateChecker.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/update/AppUpdateCheckerTest.kt`

- [ ] Prefer any valid 2xx response over mirror/proxy 404 responses.
- [ ] Treat only GitHub source 404 as `NoRelease` when no 2xx response exists.
- [ ] Remove `parsed.firstOrNull()` ABI fallback.
- [ ] Return a clear failure when no asset matches current ABI and no universal APK exists.
- [ ] Add tests for fast mirror 404 plus slower source 200, source 404, and no-compatible-ABI manifests.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest'
```

### Task 14: Bound Image, ZIP, And Local Package Reads

**Findings:** 13

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportClient.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginMarketScreen.kt`
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReader.kt`
- Modify: `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/ai/AiScheduleImportClientTest.kt`
- Test: `core-plugin/src/test/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReaderTest.kt`
- Test: `core-plugin/src/test/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstallerTest.kt`

- [ ] Decode images with bounds and sample size before creating full bitmaps.
- [ ] Add maximum local plugin package byte limits before `readBytes()`.
- [ ] Replace ZIP entry `readBytes()` with streaming reads that stop once per-file or cumulative limits are exceeded.
- [ ] Add tests proving oversized images/packages fail without full allocation.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest :core-plugin:testDebugUnitTest :feature-plugin:testDebugUnitTest'
```

### Task 15: Bound Plugin WebView Capture Fetches

**Findings:** 14

**Files:**
- Modify: `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreen.kt`
- Test: `feature-plugin/src/test/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreenTest.kt`

- [ ] Set explicit connect and read timeouts for capture `HttpURLConnection`.
- [ ] Read at most `safeMaxBodyBytes()` plus a small sentinel byte instead of full `readBytes()`.
- [ ] Preserve response behavior for non-captured resources.
- [ ] Add tests for bounded body reads and timeout failure messages.

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-plugin:testDebugUnitTest'
```

## Final Verification

- [ ] Confirm Chunk 0 is complete before running Gradle validation on a clean checkout.
- [ ] Run focused module tests listed above.
- [ ] Run all JVM unit tests:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew testDebugUnitTest'
```

- [ ] Run Android lint or the project's standard verification task if configured:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew lintDebug'
```

- [ ] Manually verify:
  - Plugin install rejects unsafe IDs and incomplete checksums.
  - First-course reminders create alarms.
  - Exam reminder mute/restore works from exam detail.
  - WebDAV/AI HTTP URLs are rejected.
  - Widgets agree on active-term week and label reminder badges.
