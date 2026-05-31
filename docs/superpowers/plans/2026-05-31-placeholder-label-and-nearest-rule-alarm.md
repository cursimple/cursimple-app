# Placeholder Label And Nearest Rule Alarm Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make placeholder course labels selectable and editable, and register only the nearest future alarm when a label rule is newly created.

**Architecture:** Keep placeholder label selection in the Compose dialog. Add a focused nearest-rule alarm sync API in `ReminderCoordinator`, then call it only from the new-label-rule path in `ScheduleViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android alarm coordination, JUnit.

---

## Chunk 1: Placeholder Label UI

### Task 1: Replace Cycle Button With Editable Dropdown

**Files:**
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/PlaceholderCourseDialog.kt`

- [ ] Add Material3 exposed dropdown imports and opt in where needed.
- [ ] Replace the `OutlinedTextField` plus “切换已有 label” button with an editable dropdown text field.
- [ ] Keep direct typing enabled and cap label length at 40 characters.
- [ ] Render existing `slotLabels` as dropdown items and update the text when selected.
- [ ] Remove the now-unused `floorMod` helper.

## Chunk 2: Nearest Alarm Sync

### Task 2: Add Core Sync API

**Files:**
- Modify: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderCoordinator.kt`
- Test: `core-reminder/src/test/java/com/x500x/cursimple/core/reminder/SystemAlarmRegistryTest.kt`

- [ ] Add a coordinator method that accepts a `ruleId`, expands only that enabled label rule, filters to `now..now+48h`, keeps the earliest plan, and dispatches through the existing registry path.
- [ ] Reuse the same settings, stale record, existing record, and system-clock representability checks as `syncAlarmsForWindow`.
- [ ] Add tests for “only nearest plan is created” and “no plan within 48h creates nothing”.

### Task 3: Use It For Newly Created Rules

**Files:**
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt`

- [ ] Detect `isNewRule = ruleId == null` before saving.
- [ ] For new rules, call the nearest-rule sync method after `upsertLabelRule`.
- [ ] For edits, keep using the existing today reconcile path.
- [ ] Preserve existing status message format.

## Chunk 3: Verification

### Task 4: Run Focused Tests

**Commands:**
- `./gradlew :core-reminder:testDebugUnitTest`
- `./gradlew :feature-schedule:compileDebugKotlin`

- [ ] Run tests without adding artificial timeout waits.
- [ ] Fix failures caused by the change.
- [ ] Leave git uncommitted unless the user explicitly requests a commit.
