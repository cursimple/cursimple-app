# Temporary Schedule Overrides Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add temporary schedule override rules for holiday make-up classes, and update widget pin fallback detection.

**Architecture:** Store temporary override rules in user preferences as a JSON list of shared core-kernel models. Resolve effective course weekday at display/planning time so original schedules, week calculation, and export remain unchanged. Widget pinning uses an immediate request result instead of a delayed manual-help trigger.

**Tech Stack:** Kotlin, Android DataStore Preferences, Jetpack Compose Material3, Glance widgets, JUnit4.

---

## Chunk 1: Shared Data And Persistence

### Task 1: Add Temporary Override Model

**Files:**
- Create: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/model/TemporaryScheduleOverride.kt`
- Test: `core-kernel/src/test/java/com/kebiao/viewer/core/kernel/model/TemporaryScheduleOverrideTest.kt`

- [ ] Add `TemporaryScheduleOverride` with `id`, `startDate`, `endDate`, and `sourceDayOfWeek`.
- [ ] Add helpers to parse dates, normalize ranges, test containment, resolve source day for a date, and format weekday labels.
- [ ] Test single-day mapping, range mapping, invalid dates ignored, and overlapping rules using the last matching rule.

### Task 2: Persist Rules In User Preferences

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `core-data/src/test/java/com/kebiao/viewer/core/data/UserPreferencesTest.kt`

- [ ] Add `temporaryScheduleOverrides` to `UserPreferences`.
- [ ] Add repository methods to save and remove override rules.
- [ ] Store rules as a JSON string preference using kotlinx.serialization.
- [ ] Test default list is empty.

## Chunk 2: UI And App Wiring

### Task 3: Add Settings UI

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppPreferencesViewModel.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`

- [ ] Add a settings row named `临时调课`.
- [ ] Add a dialog that chooses start date, end date, and source weekday.
- [ ] List existing rules and allow deleting each rule.
- [ ] On rule changes, refresh widgets and resync reminders without changing week calculation.

### Task 4: Apply Rules To Schedule UI

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `feature-schedule/src/test/java/com/kebiao/viewer/feature/schedule/ScheduleValidationTest.kt`

- [ ] Pass override rules from `MainActivity` into `ScheduleRoute`.
- [ ] In week view, render each actual date column using its resolved source weekday.
- [ ] In day view, filter courses by resolved source weekday.
- [ ] Add tests for overridden week columns.

## Chunk 3: Widgets And Reminders

### Task 5: Apply Rules To Widgets

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetDataSource.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/NextCourseGlanceWidget.kt`

- [ ] Daily widget loads courses by resolved source weekday.
- [ ] Next-course widget loads today's courses by resolved source weekday.
- [ ] Widget labels show the override hint when actual weekday differs.

### Task 6: Apply Rules To Reminder Planning

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] Add optional temporary override list to reminder expansion.
- [ ] Generate occurrence dates by actual date, using resolved source weekday and actual week index.
- [ ] Test a make-up date that maps Saturday to Monday courses.

## Chunk 4: Widget Pin Flow

### Task 7: Replace Delayed Manual Guide With Immediate Request Result

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetCatalog.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/WidgetPickerSheet.kt`

- [ ] Make `requestPin` return a result that distinguishes started, unsupported, and failed.
- [ ] Remove the 6-second pending watch.
- [ ] Show vendor-specific manual instructions immediately when request start fails.
- [ ] Reuse the same vendor guidance text as the help dialog.

## Chunk 5: Verification

- [ ] Run `./gradlew :core-kernel:testDebugUnitTest :core-data:testDebugUnitTest :core-reminder:testDebugUnitTest :feature-schedule:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Check `git status --short` and confirm no commit is created.
