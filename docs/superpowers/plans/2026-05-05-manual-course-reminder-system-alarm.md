# Manual Course Reminder System Alarm Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 让用户在课程详情里当场设置单课提醒参数，并保证删除提醒规则会删除对应系统闹钟，同时隐藏闹钟名称中的 hash。

**Architecture:** UI 新增单课提醒设置弹窗，ViewModel 新增按课程 ID 创建提醒的入口。系统闹钟仍由 `ReminderCoordinator` 统一调度和清理，闹钟 label 改为面向用户的简洁文本，内部去重继续使用登记簿 `alarmKey`。

**Tech Stack:** Kotlin、Jetpack Compose、Android AlarmClock Intent、DataStore、JUnit4、pwsh。

---

## File Structure

- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/CourseReminderDialog.kt`
  - 单课提醒弹窗，负责提前分钟数和铃声选择。
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
  - 点击课程详情的“设为提醒”后打开弹窗，并把确认结果传给 ViewModel。
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - 增加 `createReminderForCourse(courseId, advanceMinutes, ringtoneUri)`。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - 调整 `systemAlarmLabel()`，不再追加 `#xxxx`。
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`
  - 补充删除规则清理系统闹钟和 label 格式测试。

## Chunk 1: UI 入口

- [x] 新增单课提醒弹窗，复用 `launchAlarmRingtonePicker()`。
- [x] `ScheduleScreen` 增加 `pendingReminderCourse` 状态。
- [x] 课程详情点击“设为提醒”时关闭详情弹窗并打开提醒设置弹窗。
- [x] 弹窗确认时调用新的单课提醒回调。

## Chunk 2: ViewModel 入口

- [x] 新增 `createReminderForCourse()`，按课程 ID 创建 `SingleCourse` 规则。
- [x] 创建成功后调用当天系统闹钟同步。
- [x] 保留现有 `createReminderForSelection()`，让设置页旧入口继续可用。

## Chunk 3: 系统闹钟名称与删除测试

- [x] 修改 `systemAlarmLabel()` 返回不含 hash 的课程提醒名称。
- [x] 补测试确认 label 不包含 `#`。
- [x] 补测试确认 `deleteRule()` 会调用 `AlarmDismisser` 并删除登记。

## Chunk 4: 验证

- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :feature-schedule:compileDebugKotlin'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`
- [x] 不执行 `git add` 或 `git commit`。
