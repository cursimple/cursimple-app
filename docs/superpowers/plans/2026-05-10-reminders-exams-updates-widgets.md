# Reminders Exams Updates Widgets Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add temporary single-class cancellation, flexible first-course reminders, exam category/reminders, GitHub Release update checks, and quiet 5-minute widget refresh.

**Architecture:** Extend shared kernel/reminder models first, then reuse those shared functions from schedule UI, widget data, and alarm planning. Add update checking as an app-level service backed by GitHub Release `update.json`, with Release workflow generation for the manifest.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, AlarmManager, WorkManager, GitHub Actions, kotlinx.serialization, Android PackageInstaller intents.

---

## Chunk 1: Shared Models And Planner

### Task 1: Extend Schedule Models

**Files:**
- Modify: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/model/ScheduleModels.kt`
- Modify: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/model/TemporaryScheduleOverride.kt`
- Test: `core-kernel/src/test/java/com/kebiao/viewer/core/kernel/model/TemporaryScheduleOverrideTest.kt`

- [ ] Add `CourseCategory` with `Course` and `Exam`.
- [ ] Add `category: CourseCategory = CourseCategory.Course` to `CourseItem`.
- [ ] Add temporary override type and cancellation fields.
- [ ] Add shared course cancellation helpers.
- [ ] Add tests for date-level cancellation, node-range cancellation, and course-specific cancellation.

### Task 2: Extend Reminder Rules

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
- Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] Add node-range config for first-course period ranges and muted prelude ranges.
- [ ] Add `Evening` period while preserving existing morning/afternoon rules.
- [ ] Update planner to suppress a period when muted prelude slots have a course that day.
- [ ] Update planner to filter temporary-cancelled course occurrences.
- [ ] Add tests covering early-study present, early-study absent, and cancellation suppressing alarms.

## Chunk 2: Schedule And Settings UI

### Task 3: Temporary Cancellation UI

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`

- [ ] Add a mode selector in the temporary schedule dialog: make-up class or cancel class.
- [ ] For cancellation, let user pick target date, start/end node, and optional course scope.
- [ ] Display cancellation rules in the rule list with clear Chinese labels.

### Task 4: Exam Category UI

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/AddCourseDialog.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/CourseDetailDialog.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/CourseReminderDialog.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/BulkReminderDialog.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`

- [ ] Add category selection for manually added courses.
- [ ] Render exam cards with a more prominent style and badge.
- [ ] Default single exam reminders to 40 minutes.
- [ ] Use exam-aware labels in detail and reminder dialogs.

### Task 5: Flexible First-Course Reminder UI

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`

- [ ] Replace fixed morning/afternoon card copy with configurable period cards.
- [ ] Add muted prelude node controls per period.
- [ ] Save range and muted prelude config through `upsertFirstCourseReminder`.
- [ ] Keep existing rules readable after migration.

## Chunk 3: Widgets

### Task 6: Widget Data And Midnight Behavior

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetDataSource.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionReceiver.kt`
- Modify: `feature-widget/src/main/AndroidManifest.xml`
- Test: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/ReminderWidgetRecordsTest.kt`

- [ ] Change the auto-next-day threshold from 22:00 to 00:00.
- [ ] Filter temporary-cancelled courses in widget data.
- [ ] Include exam category in widget row presentation.
- [ ] Add AlarmManager-based 5-minute refresh receiver and scheduler.
- [ ] Reschedule refresh on app start and widget lifecycle events.

## Chunk 4: Updates

### Task 7: App Update Service

**Files:**
- Create: `app/src/main/java/com/kebiao/viewer/app/update/AppUpdateModels.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/update/AppUpdateChecker.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/update/AppUpdateInstaller.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppPreferencesViewModel.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AboutScreen.kt`

- [ ] Enable `BuildConfig` and expose current version at runtime.
- [ ] Add persistent automatic update preference.
- [ ] Implement source/mirror URL candidate generation and timing.
- [ ] Handle no Release, missing manifest, same version, higher version, and download failures.
- [ ] Download selected APK to cache and open Android install confirmation.
- [ ] Add About page UI for check update, update status, download/install, and auto update switch.

### Task 8: Release Workflow Manifest

**Files:**
- Modify: `.github/workflows/android-release.yml`

- [ ] Read `versionCode` and `versionName` from Gradle source.
- [ ] Calculate sha256 for each `ClassScheduleViewer-*.apk`.
- [ ] Generate `update.json` with APK metadata and GitHub Release URLs.
- [ ] Upload and publish `update.json` with APK assets.

## Chunk 5: Verification

### Task 9: Test And Build

**Files:**
- Modify tests listed above as needed.

- [ ] Run focused kernel/reminder/widget tests.
- [ ] Run app/core-data tests affected by update preference.
- [ ] Run `./gradlew testDebugUnitTest` or the closest available aggregate test.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Summarize any blocked checks without committing.
