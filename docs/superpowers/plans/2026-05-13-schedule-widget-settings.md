# Schedule Widget Settings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make schedule appearance defaults, reset controls, schedule background behavior, and desktop widget preference refresh match the requested behavior.

**Architecture:** Extend the existing DataStore-backed preference models and keep rendering changes at the current UI/widget boundaries. Use explicit preference modes and reset APIs instead of temporary fallback behavior.

**Tech Stack:** Kotlin, Android DataStore Preferences, Jetpack Compose, RemoteViews App Widgets, Gradle/JUnit.

---

## Chunk 1: Preference Models And Defaults

### Task 1: Update Core Preference Defaults

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/UserPreferencesTest.kt`

- [ ] Change `ScheduleCardStylePreferences.DEFAULT_SCHEDULE_OPACITY_PERCENT` to `0`.
- [ ] Change `ScheduleCardStylePreferences.DEFAULT_INACTIVE_COURSE_OPACITY_PERCENT` to `50`.
- [ ] Change `ScheduleDisplayPreferences.teacherVisible` default to `true`.
- [ ] Change DataStore read default for `KEY_SCHEDULE_DISPLAY_TEACHER_VISIBLE` to `true`.
- [ ] Update tests for the new defaults.

### Task 2: Add Schedule Background Header Mode

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/AppPreferencesViewModel.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt`

- [ ] Add `Header` to `ScheduleBackgroundType`.
- [ ] Add repository/ViewModel method to set schedule background to header mode.
- [ ] Add Settings UI action in the schedule background page.
- [ ] Update schedule background rendering to resolve header mode from `ScheduleTextStylePreferences.resolvedTodayHeaderBackgroundColorArgb(darkTheme)`.

### Task 3: Add Reset APIs

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/widget/WidgetPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/AppPreferencesViewModel.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/WidgetPreferencesViewModel.kt`

- [ ] Implement `resetScheduleAppearanceAndDisplay()`.
- [ ] Implement `resetAllSettings()`.
- [ ] Implement `resetWidgetThemePreferences()`.
- [ ] Release persisted image URI permissions when resetting background image settings.

## Chunk 2: Widget Behavior

### Task 4: Add Open App Setting

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/widget/WidgetPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`
- Modify: `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/WidgetStyle.kt`
- Modify: widget receiver/layout files as needed.

- [ ] Store `openAppOnClickEnabled`, default `false`.
- [ ] Add switch to widget settings page.
- [ ] When enabled, set a launch `PendingIntent` on widget root/content background.
- [ ] Keep explicit widget controls working.

### Task 5: Add Preference Flow Refresh

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/ClassScheduleApplication.kt`

- [ ] Observe widget theme preferences after bootstrap.
- [ ] Call `appContainer.refreshWidgets()` on distinct changes after initial emission.

## Chunk 3: UI Reset Actions And Verification

### Task 6: Add Reset UI

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`

- [ ] Add root-level reset all settings action with confirmation.
- [ ] Add schedule settings reset appearance/display action with confirmation.
- [ ] Wire callbacks to ViewModels.

### Task 7: Verify

**Files:**
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/UserPreferencesTest.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/WidgetPreferencesTest.kt`

- [ ] Run `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:test :feature-schedule:test :feature-widget:test'`.
- [ ] If broad tests fail due unrelated environment issues, run narrower module tests and report the limitation.
