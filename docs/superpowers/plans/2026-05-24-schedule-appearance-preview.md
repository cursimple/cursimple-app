# Schedule Appearance Preview Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a static, live-updating schedule appearance preview to Settings -> Schedule Settings -> Appearance.

**Architecture:** Implement a lightweight preview Composable in `feature-schedule` that accepts existing schedule appearance preferences and renders fixed sample courses. The `app` settings page imports this Composable and places it at the top of the appearance settings page.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing schedule preference models.

---

## Chunk 1: Preview Component

### Task 1: Add A Reusable Appearance Preview

**Files:**
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt`
- Optionally modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/SampleSchedule.kt`

- [ ] **Step 1: Add a public Composable entry**

Add `ScheduleAppearancePreview` in the `feature.schedule` package. It should accept:

```kotlin
scheduleTextStyle: ScheduleTextStylePreferences
scheduleCardStyle: ScheduleCardStylePreferences
scheduleBackground: ScheduleBackgroundPreferences
scheduleDisplay: ScheduleDisplayPreferences
customColorsAdaptToTheme: Boolean
modifier: Modifier = Modifier
```

- [ ] **Step 2: Use fixed preview data**

Use a small fixed set of `CourseItem` records or reuse `sampleManualCourses()` if its visibility is changed safely. Keep the preview deterministic and independent from real user schedule data.

- [ ] **Step 3: Render a non-interactive compressed grid**

Render a bounded-height preview with:

- Month corner cell.
- Weekday/date header.
- Time column.
- Grid background.
- Grid border lines.
- Several course cards.

The preview must not open dialogs, add courses, scroll, or react to course clicks.

- [ ] **Step 4: Reuse existing style helpers**

Use the existing color resolution helpers and style preferences wherever possible so the preview matches the real schedule surface.

## Chunk 2: Settings Integration

### Task 2: Place The Preview In Appearance Settings

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`

- [ ] **Step 1: Import the preview Composable**

Add an import for `ScheduleAppearancePreview`.

- [ ] **Step 2: Add the preview at the top of `SettingsDestination.ScheduleAppearance`**

Place it before the “自定义颜色适应亮暗主题” switch:

```kotlin
ScheduleAppearancePreview(
    scheduleTextStyle = scheduleTextStyle,
    scheduleCardStyle = scheduleCardStyle,
    scheduleBackground = scheduleBackground,
    scheduleDisplay = scheduleDisplay,
    customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
)
```

- [ ] **Step 3: Keep existing controls unchanged**

Do not rename or move the existing appearance entries except for inserting the preview above them.

## Chunk 3: Verification

### Task 3: Build And Review

**Files:**
- None expected beyond implementation files.

- [ ] **Step 1: Check formatting by compile**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew.bat :feature-schedule:compileDebugKotlin :app:compileDebugKotlin'
```

Expected: build succeeds.

- [ ] **Step 2: If compile task is unavailable, build debug APK**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew.bat :app:assembleDebug'
```

Expected: build succeeds.

- [ ] **Step 3: Review git diff**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command 'git diff -- docs/plans/2026-05-24-schedule-appearance-preview-design.md docs/superpowers/plans/2026-05-24-schedule-appearance-preview.md feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt'
```

Expected: diff only contains the design doc, implementation plan, preview component, and settings page integration.

## Notes

- Do not run `git commit` unless the user explicitly asks.
- Do not introduce fallback architecture for preview rendering.
- Keep the preview static and live-updating from the current appearance preferences.
