# 设置页与总课表显示 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real settings page, move drawer preferences into it, and add a default-on "总课表显示" preference that controls week-view rendering.

**Architecture:** Keep plugin data ownership unchanged. Store the display preference in `UserPreferences`, route it from `MainActivity` into the schedule week renderer, and make the renderer choose between current-week-only entries and full-term entries.

**Tech Stack:** Android/Kotlin, Jetpack Compose Material 3, DataStore preferences, existing schedule and plugin models.

---

## Chunk 1: User Preference

### Task 1: Add Total Schedule Display Preference

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppPreferencesViewModel.kt`

- [ ] **Step 1: Add model and repository API**

Add `totalScheduleDisplayEnabled: Boolean = true` to `UserPreferences`, plus `setTotalScheduleDisplayEnabled(enabled: Boolean)` on `UserPreferencesRepository`.

- [ ] **Step 2: Persist through DataStore**

Read `KEY_TOTAL_SCHEDULE_DISPLAY_ENABLED` with default `true`, write it through the new repository method, and use key name `total_schedule_display_enabled`.

- [ ] **Step 3: Expose through app ViewModel**

Add `AppPreferencesViewModel.setTotalScheduleDisplayEnabled(enabled: Boolean)`.

## Chunk 2: Settings Page

### Task 2: Move Drawer Preferences Into Settings

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`

- [ ] **Step 1: Add settings navigation**

Add `AppScreen.Settings` with label `设置` and a settings icon.

- [ ] **Step 2: Remove drawer preference rows**

Keep the drawer focused on app navigation and status. Remove the inline `偏好` section and `桌面小组件` row from the drawer.

- [ ] **Step 3: Build settings UI**

Create an app settings route with rows for theme, appearance, term start date, current week, time zone, total schedule display switch, and desktop widgets.

- [ ] **Step 4: Wire existing dialogs**

Reuse the current theme, accent, term start, current week, time zone, and widget callbacks from `MainActivity`.

## Chunk 3: Week Renderer

### Task 3: Add Total Schedule Rendering Mode

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify/Test: `feature-schedule/src/test/java/com/kebiao/viewer/feature/schedule/ScheduleValidationTest.kt`

- [ ] **Step 1: Thread preference into weekly route**

Add `totalScheduleDisplayEnabled` to `ScheduleRoute`, `ScheduleScreen`, and `WeeklyScheduleSection`.

- [ ] **Step 2: Update render-entry builder**

When disabled, keep current-week-only behavior. When enabled, include all full-term courses, set inactive for courses not active in the visible week, and sort grouped courses so active courses become the main block when present.

- [ ] **Step 3: Preserve day view**

Do not pass this preference into `DailyScheduleSection`; day view keeps current-week filtering.

- [ ] **Step 4: Add focused tests**

Test disabled filtering, enabled inactive inclusion, and mixed active/inactive same-cell counting.

## Chunk 4: Verification

### Task 4: Run Checks

**Files:**
- No extra source files beyond tasks above.

- [ ] **Step 1: Run targeted tests**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew.bat :core-data:testDebugUnitTest :feature-schedule:testDebugUnitTest :app:testDebugUnitTest'`
Expected: PASS.

- [ ] **Step 2: Run compile check**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew.bat :app:assembleDebug'`
Expected: PASS.
