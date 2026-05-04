# Schedule Plugin Current Week And WebView Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix plugin-controlled schedule timing, current-week calculation, developer metadata export, week filtering, and plugin WebView navigation behavior.

**Architecture:** Keep timing data owned by plugin datapacks and make the schedule UI render the plugin-provided labels in chronological order. Treat "set current week" as another way to write the same term-start date preference. Keep plugin WebView rendering scoped to the plugin screen, and add a developer export utility that writes schedule metadata into a shareable cache file.

**Tech Stack:** Android/Kotlin, Jetpack Compose, DataStore-backed preferences, app cache FileProvider, existing plugin workflow/timing models.

---

## Chunk 1: Signing And Timing

### Task 1: Copy Signing Assets

**Files:**
- Copy ignored local files: `.signing/class-viewer.jks`, `keystore.properties`

- [x] **Step 1: Verify signing config shape**

Run: `pwsh -NoLogo -NoProfile -Command 'Get-Content -LiteralPath ".\app\build.gradle.kts" -Raw'`
Expected: app reads `CLASS_VIEWER_*` values from env or `keystore.properties`.

- [x] **Step 2: Copy signing files from `E:\work\Class-Schedule-Viewer1`**

Run: copy `.signing/class-viewer.jks` and `keystore.properties` into this workspace.
Expected: ignored signing files exist locally and are not staged by git.

### Task 2: Make Schedule Time Labels Plugin-Owned

**Files:**
- Modify: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/datapack/timing.json`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`

- [ ] **Step 1: Update bundled timing profile**

Use these ordered slots:
`第一节 08:00-09:35`, `第二节 10:05-11:40`, `午间课 12:00-13:35`, `第三节 14:00-15:35`, `第四节 16:05-17:40`, `晚间课 18:00-18:45`, `第五节 19:00-20:35`, `第六节 20:45-22:20`.

- [ ] **Step 2: Render plugin labels**

Sort timing slots by parsed start time, then node, and set `DisplaySlot.indexLabel` from `ClassSlotTime.label` with a numeric generated label only for extra/padded slots.

## Chunk 2: Week Calculation And Visibility

### Task 3: Add Current-Week Setting

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/WeekPickerSheet.kt`
- Modify/Test: `app/src/test/java/com/kebiao/viewer/app/WeekPickerSheetTest.kt`

- [ ] **Step 1: Add term-start derivation helper**

Given `today` and `currentWeek`, compute `todayMonday.minusWeeks(currentWeek - 1)`.

- [ ] **Step 2: Wire UI**

Add "按当前周设置" to the term-start picker and make the week picker's selected week button write the derived term start.

### Task 4: Only Show Current Week Courses

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify/Test: `feature-schedule/src/test/java/com/kebiao/viewer/feature/schedule/ScheduleValidationTest.kt`

- [ ] **Step 1: Remove inactive week placeholders**

`buildWeekRenderEntries` should only return courses whose `weeks` includes the displayed week, while `weeks=[]` remains always active.

- [ ] **Step 2: Keep detail dialogs current-week scoped**

Because cell groups are built from current-week entries only, click details must also exclude non-current-week courses.

## Chunk 3: Developer Export And WebView

### Task 5: Export Schedule Metadata

**Files:**
- Create: `app/src/main/java/com/kebiao/viewer/app/util/ScheduleMetadataExporter.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AboutScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Export cache file**

Write current schedule metadata, timing profile, plugin ids, messages, and derived week info to `cache/metadata/`.

- [ ] **Step 2: Add developer action**

Add a developer settings button "导出课表元数据" that opens the Android sharesheet.

### Task 6: Scope Plugin WebView To Plugin Page

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreen.kt`

- [ ] **Step 1: Remove schedule-page WebView overlay**

The schedule page should keep sync state but not render the plugin WebView.

- [ ] **Step 2: Increase WebView height**

Remove allowed-host display and keep cancel/back/refresh/upload controls in one toolbar row.

## Chunk 4: Verification

### Task 7: Run Checks

**Files:**
- No source changes beyond tasks above.

- [ ] **Step 1: Run targeted unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest :feature-schedule:testDebugUnitTest :feature-plugin:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 2: Run compile check**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS.
