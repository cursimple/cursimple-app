# Course Card Temporary Cancel Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to temporarily cancel or restore a specific course from the course card details.

**Architecture:** Reuse the existing `TemporaryScheduleOverride.CancelCourse` persistence model. Carry the actual displayed date from schedule cells into the detail dialog, render cancelled courses in-app with a diagonal mark, keep widgets on the existing filtered data path, and rely on reminder planning's existing cancellation filter.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android DataStore preferences, Glance widget data sources.

---

## Chunk 1: Schedule Interaction

### Task 1: Carry Actual Dates Into Course Details

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/CourseDetailDialog.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [ ] Add temporary override callbacks to `ScheduleRoute` / `ScheduleScreen`.
- [ ] Store the clicked courses with their actual target date.
- [ ] Create exact per-course `CancelCourse` rules from the detail dialog.
- [ ] Remove the matching rule when restoring.
- [ ] Verify the same rule suppresses matching reminder plans.

## Chunk 2: In-App Cancelled Rendering

### Task 2: Draw Cancelled Courses

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`

- [ ] Stop filtering cancelled courses from app week/day render lists.
- [ ] Add a `temporarilyCancelled` flag to week render entries and day rows.
- [ ] Draw two diagonal lines over cancelled course cards.
- [ ] Keep normal long-press multi-select behavior.

## Chunk 3: Verification

- [ ] Run `./gradlew :feature-schedule:compileDebugKotlin`.
- [ ] Run relevant unit tests if compile succeeds.
- [ ] Confirm `git status --short` and do not create a commit.
