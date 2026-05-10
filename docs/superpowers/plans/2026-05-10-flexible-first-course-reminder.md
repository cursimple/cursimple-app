# Flexible First Course Reminder Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the confusing first-course "muted prelude node" rule with a flexible condition/action reminder system that supports custom time-based occupancies such as early study.

**Architecture:** Extend `ReminderRule` with a first-course candidate scope, condition list, and action list while keeping old fields readable for migration. Store custom occupancies separately in the reminder repository because they are reusable rule inputs, not courses. Move first-course evaluation into a focused core-reminder evaluator and replace the settings card UI with rule list, rule editor, and occupancy manager components.

**Tech Stack:** Kotlin, kotlinx.serialization, Android DataStore Preferences, Jetpack Compose Material3, JUnit4, Gradle Android unit tests.

---

## Notes

Do not commit while writing or finishing this plan. Commit steps below are implementation checkpoints and must only be run after the user explicitly asks for commits.

Use `pwsh` commands on Windows. Avoid `powershell`.

## File Structure

- Modify `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - Add serializable flexible first-course models.
  - Keep legacy fields for old stored rules.
- Create `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/FirstCourseRuleEvaluator.kt`
  - Own first-course candidate filtering, condition checks, action execution, and legacy rule normalization.
- Modify `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
  - Delegate `FirstCourseOfPeriod` rules to the evaluator.
  - Accept custom occupancies with a default empty list.
- Modify `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderRepository.kt`
  - Add custom occupancy flow and CRUD methods.
- Modify `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/DataStoreReminderRepository.kt`
  - Persist custom occupancies in a separate DataStore key.
- Modify `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
  - Add flexible first-course upsert and custom occupancy APIs.
  - Pass custom occupancies into reminder planning.
- Modify `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - Expose save/delete methods for flexible rules and occupancies.
  - Keep old save method as a compatibility wrapper until UI is migrated.
- Create `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/FirstCourseReminderSettings.kt`
  - Compose rule list, rule editor, template picker, and occupancy manager.
- Modify `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
  - Replace old first-course card with the new component.
  - Update reminder display summaries.
- Modify `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`
  - Cover early-study and flexible action scenarios.
- Modify `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`
  - Update fake repository and coordinator tests for new repository methods.
- Add or modify feature-schedule UI/unit tests if an existing test harness supports the touched helpers.

## Chunk 1: Core Models

### Task 1: Add flexible first-course data models

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
- Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] **Step 1: Add a serialization-focused failing test**

Add a test that constructs a rule with:

- `displayName = "上午首课提醒"`
- candidate scope: node range 1-4, normal courses only.
- condition mode: `All`.
- condition: custom occupancy `early-study` exists.
- action: continue after node 1.

Also construct a `ReminderCustomOccupancy` with `startTime = "07:00"` and `endTime = "07:40"`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest --tests "com.kebiao.viewer.core.reminder.ReminderPlannerTest"
```

Expected: compilation fails because the new model classes do not exist.

- [ ] **Step 3: Add the model types**

Add imports:

```kotlin
import com.kebiao.viewer.core.kernel.model.CourseCategory
```

Add nullable/defaulted fields to `ReminderRule`:

```kotlin
@SerialName("displayName") val displayName: String? = null,
@SerialName("firstCourseCandidate") val firstCourseCandidate: FirstCourseCandidateScope? = null,
@SerialName("conditionMode") val conditionMode: ReminderConditionMode = ReminderConditionMode.All,
@SerialName("conditions") val conditions: List<ReminderCondition> = emptyList(),
@SerialName("actions") val actions: List<ReminderAction> = emptyList(),
```

Add the new serializable models:

```kotlin
@Serializable
data class ReminderTimeRange(
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
)

@Serializable
data class FirstCourseCandidateScope(
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("includeDates") val includeDates: List<String> = emptyList(),
    @SerialName("excludeDates") val excludeDates: List<String> = emptyList(),
    @SerialName("timeRange") val timeRange: ReminderTimeRange? = null,
    @SerialName("nodeRange") val nodeRange: ReminderNodeRange? = null,
    @SerialName("categories") val categories: List<CourseCategory> = listOf(CourseCategory.Course),
    @SerialName("titleContains") val titleContains: String? = null,
    @SerialName("teacherContains") val teacherContains: String? = null,
    @SerialName("locationContains") val locationContains: String? = null,
)

@Serializable
enum class ReminderConditionMode {
    @SerialName("all") All,
    @SerialName("any") Any,
}

@Serializable
enum class ReminderConditionType {
    @SerialName("course_exists_in_nodes") CourseExistsInNodes,
    @SerialName("course_absent_in_nodes") CourseAbsentInNodes,
    @SerialName("course_exists_in_time") CourseExistsInTime,
    @SerialName("course_absent_in_time") CourseAbsentInTime,
    @SerialName("occupancy_exists") OccupancyExists,
    @SerialName("occupancy_absent") OccupancyAbsent,
    @SerialName("occupancy_overlaps_course") OccupancyOverlapsCourse,
    @SerialName("weekday_matches") WeekdayMatches,
    @SerialName("week_matches") WeekMatches,
    @SerialName("date_matches") DateMatches,
    @SerialName("course_text_matches") CourseTextMatches,
)

@Serializable
data class ReminderCondition(
    @SerialName("type") val type: ReminderConditionType,
    @SerialName("nodeRange") val nodeRange: ReminderNodeRange? = null,
    @SerialName("timeRange") val timeRange: ReminderTimeRange? = null,
    @SerialName("occupancyId") val occupancyId: String? = null,
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("dates") val dates: List<String> = emptyList(),
    @SerialName("text") val text: String? = null,
)

@Serializable
enum class ReminderActionType {
    @SerialName("remind_first_candidate") RemindFirstCandidate,
    @SerialName("skip") Skip,
    @SerialName("continue_after_node") ContinueAfterNode,
    @SerialName("continue_after_time") ContinueAfterTime,
    @SerialName("use_candidate_scope") UseCandidateScope,
)

@Serializable
data class ReminderAction(
    @SerialName("type") val type: ReminderActionType,
    @SerialName("afterNode") val afterNode: Int? = null,
    @SerialName("afterTime") val afterTime: String? = null,
    @SerialName("candidateScope") val candidateScope: FirstCourseCandidateScope? = null,
)

@Serializable
data class ReminderCustomOccupancy(
    @SerialName("occupancyId") val occupancyId: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("timeRange") val timeRange: ReminderTimeRange,
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("includeDates") val includeDates: List<String> = emptyList(),
    @SerialName("excludeDates") val excludeDates: List<String> = emptyList(),
    @SerialName("linkedNodeRange") val linkedNodeRange: ReminderNodeRange? = null,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("updatedAt") val updatedAt: String,
)
```

- [ ] **Step 4: Run the focused test and verify compilation moves forward**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest --tests "com.kebiao.viewer.core.reminder.ReminderPlannerTest"
```

Expected: either PASS for untouched behavior or fail only where evaluator behavior is still missing.

## Chunk 2: Repository Persistence

### Task 2: Persist custom occupancies

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/DataStoreReminderRepository.kt`
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`

- [ ] **Step 1: Update fake repository first**

In `SystemAlarmRegistryTest.FakeReminderRepository`, add an in-memory `MutableStateFlow<List<ReminderCustomOccupancy>>` and implement the methods planned below. This will fail until the interface changes.

- [ ] **Step 2: Extend `ReminderRepository`**

Add:

```kotlin
val customOccupanciesFlow: Flow<List<ReminderCustomOccupancy>>

suspend fun getCustomOccupancies(pluginId: String? = null): List<ReminderCustomOccupancy>

suspend fun saveCustomOccupancy(occupancy: ReminderCustomOccupancy)

suspend fun removeCustomOccupancy(occupancyId: String)
```

- [ ] **Step 3: Implement DataStore storage**

In `DataStoreReminderRepository`:

- Add `KEY_CUSTOM_OCCUPANCIES = stringPreferencesKey("custom_occupancies")`.
- Add decode helper with `ListSerializer(ReminderCustomOccupancy.serializer())`.
- Sort saved occupancies by `createdAt`.
- Filter `getCustomOccupancies(pluginId)` when pluginId is not null.

- [ ] **Step 4: Run core-data and core-reminder tests**

Run:

```powershell
.\gradlew.bat :core-data:testDebugUnitTest :core-reminder:testDebugUnitTest
```

Expected: tests compile. Some first-course planner assertions may still fail until evaluator work is complete.

## Chunk 3: First-Course Evaluator

### Task 3: Add evaluator tests for early-study behavior

**Files:**
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/FirstCourseRuleEvaluator.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] **Step 1: Write failing tests**

Add tests:

- `firstCourseWithOccupancyAndFirstNodeCourseSkipsMorning`
- `firstCourseWithoutOccupancyRemindsFirstNodeCourse`
- `firstCourseWithOccupancyAndEmptyFirstNodeContinuesToSecondNode`
- `customOccupancyHonorsWeekdayWeekAndExcludedDate`
- `occupancyTimeOverlapHandlesCourseAcrossMultipleNodes`
- `legacyMutedPreludeRuleStillBehavesUntilSaved`

The first three tests should model:

```kotlin
val earlyStudy = ReminderCustomOccupancy(
    occupancyId = "early-study",
    pluginId = "demo",
    name = "早自习",
    timeRange = ReminderTimeRange("07:00", "07:40"),
    daysOfWeek = listOf(1, 2, 3, 4, 5),
    linkedNodeRange = ReminderNodeRange(1, 1),
    createdAt = "2026-02-23T00:00:00+08:00",
    updatedAt = "2026-02-23T00:00:00+08:00",
)
```

And a rule with ordered actions:

```kotlin
conditions = listOf(ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = "early-study")),
actions = listOf(
    ReminderAction(ReminderActionType.Skip), // used by a condition branch for node 1 occupied by a real course
)
```

Because the model uses ordered rules, represent the complete early-study behavior as three separate rules with clear names:

- `早自习且第一节有课`
- `早自习且第一节无课`
- `无早自习`

This avoids introducing nested if/else blocks in v1.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest --tests "com.kebiao.viewer.core.reminder.ReminderPlannerTest"
```

Expected: compilation fails until planner accepts occupancies and evaluator exists.

- [ ] **Step 3: Create evaluator API**

Create `FirstCourseRuleEvaluator` with:

```kotlin
internal class FirstCourseRuleEvaluator {
    fun expand(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        customOccupancies: List<ReminderCustomOccupancy>,
    ): List<ReminderPlanTarget>
}

internal data class ReminderPlanTarget(
    val course: CourseItem,
    val courseDate: LocalDate,
    val slot: ClassSlotTime,
    val period: ReminderDayPeriod?,
)
```

Keep title/message creation in `ReminderPlanner` for now.

- [ ] **Step 4: Implement legacy normalization**

If a `FirstCourseOfPeriod` rule has empty `firstCourseCandidate`, empty `conditions`, and empty `actions`, build an in-memory candidate scope from:

- `periodStartNode` / `periodEndNode`, if present.
- otherwise `period` time buckets.
- legacy `mutedNodeRanges`, preserving the current behavior.

Do not write migrated data here.

- [ ] **Step 5: Implement flexible candidate collection**

Collect course occurrences using existing `courseOccurrenceDates` logic. Move or share that logic without changing temporary cancel behavior.

Candidate checks:

- `daysOfWeek` empty means all weekdays.
- `weeks` empty means all weeks.
- `includeDates` empty means all dates.
- `excludeDates` always removes a date.
- node range checks by overlap.
- time range checks by slot start/end overlap.
- category filter empty means all categories; default model uses normal courses.
- text filters are case-insensitive contains checks.

- [ ] **Step 6: Implement condition checks**

Evaluate conditions against the date, candidate courses, all day courses, timing profile, and active custom occupancies.

Occupancy active rules:

- Match plugin.
- Match weekday unless `daysOfWeek` empty.
- Match week unless `weeks` empty.
- Include date when `includeDates` is empty or contains date.
- Exclude when `excludeDates` contains date.

Time overlap uses parsed `LocalTime` ranges, not linked nodes.

- [ ] **Step 7: Implement action execution**

Actions:

- `Skip`: no plan for the day.
- `RemindFirstCandidate`: earliest candidate by start time, start node, end node, title, id.
- `ContinueAfterNode`: filter candidates where `startNode > afterNode`, then choose earliest.
- `ContinueAfterTime`: filter candidates where slot start time is after the parsed time.
- `UseCandidateScope`: re-run candidate filtering with the nested scope.

If no action is configured, default to `RemindFirstCandidate`.

- [ ] **Step 8: Wire planner to evaluator**

Change `ReminderPlanner.expandRule` signature:

```kotlin
fun expandRule(
    rule: ReminderRule,
    schedule: TermSchedule,
    timingProfile: TermTimingProfile,
    fromDate: LocalDate = LocalDate.now(),
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
): List<ReminderPlan>
```

Pass evaluator targets into existing `buildPlan`.

- [ ] **Step 9: Run tests**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest
```

Expected: PASS.

## Chunk 4: Coordinator and ViewModel APIs

### Task 4: Add flexible save APIs

**Files:**
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`

- [ ] **Step 1: Update coordinator tests**

Add tests that:

- Save a custom occupancy and read it back.
- Remove a custom occupancy.
- Save a flexible first-course rule and verify the repository stores candidate, conditions, and actions.
- Sync reminders with occupancies and verify the generated plan matches planner tests.

- [ ] **Step 2: Add coordinator methods**

Add:

```kotlin
suspend fun upsertFlexibleFirstCourseReminder(
    pluginId: String,
    ruleId: String?,
    displayName: String,
    enabled: Boolean,
    advanceMinutes: Int,
    ringtoneUri: String?,
    candidate: FirstCourseCandidateScope,
    conditionMode: ReminderConditionMode,
    conditions: List<ReminderCondition>,
    actions: List<ReminderAction>,
): ReminderRule

suspend fun upsertCustomOccupancy(
    pluginId: String,
    occupancyId: String?,
    name: String,
    timeRange: ReminderTimeRange,
    daysOfWeek: List<Int>,
    weeks: List<Int>,
    includeDates: List<String>,
    excludeDates: List<String>,
    linkedNodeRange: ReminderNodeRange?,
): ReminderCustomOccupancy

suspend fun removeCustomOccupancy(occupancyId: String)
```

Keep `upsertFirstCourseReminder` as a compatibility wrapper that writes legacy-compatible fields or calls the new API with a simple candidate.

- [ ] **Step 3: Pass occupancies into alarm sync**

Where `ReminderCoordinator` expands rules, load:

```kotlin
val occupancies = repository.getCustomOccupancies(pluginId)
```

Pass `customOccupancies = occupancies` to `planner.expandRule`.

- [ ] **Step 4: Expose ViewModel methods**

Add `customOccupancies` to `ScheduleUiState` if the settings screen needs the flow value. Collect `repository.customOccupanciesFlow` alongside reminder rules or expose it through coordinator.

Add:

- `saveFlexibleFirstCourseReminder(...)`
- `saveCustomOccupancy(...)`
- `removeCustomOccupancy(...)`

Each method should update `statusMessage` and resync today's alarms when a schedule exists.

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest :feature-schedule:testDebugUnitTest
```

Expected: PASS.

## Chunk 5: Rule UI

### Task 5: Replace the first-course settings card

**Files:**
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/FirstCourseReminderSettings.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`

- [ ] **Step 1: Extract UI inputs**

Create UI state data classes in the new file:

```kotlin
private data class FirstCourseRuleDraft(...)
private data class CustomOccupancyDraft(...)
```

Keep them UI-local unless tests need them.

- [ ] **Step 2: Build the rule list**

`FirstCourseReminderSettingsCard` should show:

- Title: `首次课提醒规则`
- Short subtitle: `按候选范围、条件和动作决定当天提醒哪一门课。`
- Existing flexible rules as rows.
- Icon buttons for add, edit, delete when appropriate.
- A button for `管理占用`.

Do not show "免提醒前置节次".

- [ ] **Step 3: Build the rule editor**

Use a modal bottom sheet or dialog consistent with existing app patterns.

Sections:

- 基础信息：名称、开关、提前分钟数、铃声。
- 候选范围：节次范围、可选时间范围、星期、周次、课程类别。
- 条件：条件组合模式、条件列表。
- 动作：动作列表。

For v1, support these UI-editable condition/action types:

- 自定义占用存在。
- 自定义占用不存在。
- 指定节次有课。
- 指定节次无课。
- 提醒候选范围内第一门课。
- 跳过本规则提醒。
- 从指定节次之后继续寻找第一门课。

Leave advanced time-overlap/text filters for the data model and evaluator, but hide them from the initial UI unless already present in stored data.

- [ ] **Step 4: Build occupancy manager**

Fields:

- 名称。
- 开始时间。
- 结束时间。
- 生效星期。
- 生效周次。
- 包含日期。
- 排除日期。
- 可选关联节次起止。

Validate:

- time format `HH:mm`.
- end time is after start time.
- node range is 1..32 when present.

- [ ] **Step 5: Add templates**

Templates should create drafts:

- 标准上午首课提醒。
- 有早自习时智能提醒上午首课.
- 只提醒工作日首课。
- 考试不参与首课提醒。

For "有早自习时智能提醒上午首课", create three rules:

- `早自习且第一节有课` -> skip.
- `早自习且第一节无课` -> continue after node 1.
- `无早自习` -> remind first candidate.

If no early-study occupancy exists, prompt the user to create it first.

- [ ] **Step 6: Wire `ScheduleScreen`**

Replace the old call shape:

```kotlin
FirstCourseReminderSettingsCard(
    reminderRules = reminderRules,
    pluginId = pluginId,
    onSave = onSaveFirstCourseReminder,
)
```

with the new component arguments:

- reminder rules.
- custom occupancies.
- plugin id.
- save flexible rule.
- save occupancy.
- remove occupancy.
- remove rule.

Keep the old composable deleted or private only if still used by previews.

- [ ] **Step 7: Update summaries**

Update `buildReminderRuleDisplay` so `FirstCourseOfPeriod` summaries use:

- rule display name.
- candidate range.
- condition summary.
- action summary.

Do not emit "免提醒前置第 N-M 节" for flexible rules.

- [ ] **Step 8: Run feature tests and compile**

Run:

```powershell
.\gradlew.bat :feature-schedule:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

## Chunk 6: Migration and Cleanup

### Task 6: Remove confusing legacy UI and keep data compatibility

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/FirstCourseRuleEvaluator.kt`
- Modify: `docs/plans/2026-05-10-reminders-exams-updates-widgets-design.md` if the old design text would mislead future work.

- [ ] **Step 1: Search for legacy wording**

Run:

```powershell
rg -n "前置节次|mutedPrelude|免提醒前置" .
```

Expected: only migration comments/tests or historical docs should remain.

- [ ] **Step 2: Update current docs if necessary**

If `docs/plans/2026-05-10-reminders-exams-updates-widgets-design.md` still reads like the active design, add a short note that it has been superseded by `docs/plans/2026-05-10-flexible-first-course-reminder-design.md`.

- [ ] **Step 3: Run full verification**

Run:

```powershell
.\gradlew.bat :core-reminder:testDebugUnitTest :core-data:testDebugUnitTest :feature-schedule:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

## Final Manual Checks

- [ ] Open the reminder settings UI.
- [ ] Confirm the old "免提醒前置节次" text is gone.
- [ ] Create an early-study occupancy with time `07:00-07:40`.
- [ ] Apply the early-study smart morning template.
- [ ] Verify rule summaries are understandable without implementation terms.
- [ ] Verify saving rules refreshes today's alarm sync status.

## Commit Guidance

Only commit when the user explicitly asks. Suggested commit after implementation:

```text
feat: 重做灵活首课提醒规则

- 新增自定义占用与条件动作模型
- 支持早自习等非课表时间段参与首课提醒判断
- 重排首课提醒设置页规则配置 UI
- 保留旧首课提醒数据兼容读取
```
