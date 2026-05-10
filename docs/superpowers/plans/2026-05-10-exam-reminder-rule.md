# Exam Reminder Rule Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not commit unless the user explicitly asks.

**Goal:** Add an independent exam reminder rule with 40-minute default lead time, per-exam temporary mute, and automatic cleanup after the exam date passes.

**Architecture:** Extend the reminder rule model with an `Exam` scope and `mutedCourseIds`. The planner expands `Exam` rules by matching `CourseCategory.Exam`, while the coordinator cleans expired muted exam IDs during sync. The schedule UI exposes a global exam reminder setting and per-exam mute/restore actions.

**Tech Stack:** Kotlin, kotlinx.serialization, Jetpack Compose, Android AlarmManager reminder backends, Gradle unit tests.

---

### Task 1: Extend Reminder Model And Planner

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
- Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] **Step 1: Add failing planner tests**

Add tests proving `ReminderScopeType.Exam` only creates plans for `CourseCategory.Exam`, and skips exams whose IDs are in `mutedCourseIds`.

- [ ] **Step 2: Run planner tests**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest --tests "*ReminderPlannerTest"'`

Expected: fails because `Exam` does not exist yet.

- [ ] **Step 3: Add model fields**

Add `ReminderScopeType.Exam` with `@SerialName("exam")`, and add `mutedCourseIds: List<String> = emptyList()` to `ReminderRule`.

- [ ] **Step 4: Implement planner matching**

Update `ReminderPlanner` so `Exam` rules match `course.category == CourseCategory.Exam && course.id !in mutedCourseIds`.

- [ ] **Step 5: Re-run planner tests**

Run the same focused test command and expect pass.

### Task 2: Add Rule Operations And Expired Mute Cleanup

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
- Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`

- [ ] **Step 1: Add coordinator tests**

Add tests proving expired muted exam IDs are removed during sync, while future muted exams remain muted.

- [ ] **Step 2: Run coordinator tests**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest --tests "*SystemAlarmRegistryTest"'`

Expected: fails before implementation.

- [ ] **Step 3: Add coordinator APIs**

Add methods to upsert the global exam reminder rule, mute a specific exam, and restore a muted exam.

- [ ] **Step 4: Clean expired muted exams during sync**

Before expanding rules, inspect enabled `Exam` rules and remove muted course IDs that have no active occurrence on or after the sync window start date.

- [ ] **Step 5: Re-run coordinator tests**

Run the same focused test command and expect pass.

### Task 3: Wire ViewModel And UI

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify as needed: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/CourseDetailDialog.kt`

- [ ] **Step 1: Add ViewModel actions**

Expose `saveExamReminder`, `muteExamReminder`, and `restoreExamReminder`, each syncing today alarms after rule changes.

- [ ] **Step 2: Add global setting UI**

Add an “考试提醒” setting beside existing reminder controls, with default 40 minutes, enabled state, lead time, and ringtone.

- [ ] **Step 3: Add per-exam mute/restore UI**

For exam course detail/actions, show cancel or restore action depending on whether the global exam rule contains the exam ID in `mutedCourseIds`.

- [ ] **Step 4: Update rule display**

Display `Exam` rules as “考试提醒”, with timing text “自动提醒全部考试” and muted count when applicable.

### Task 4: Verify

**Files:**
- No new files expected.

- [ ] **Step 1: Run focused tests**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

- [ ] **Step 2: Build debug APK**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew assembleDebug'`

- [ ] **Step 3: Inspect git diff**

Run: `pwsh -NoLogo -NoProfile -Command 'git diff --stat; git status --short'`

Expected: only planned files are changed; no git commit is created.
