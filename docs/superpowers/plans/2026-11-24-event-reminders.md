# 事务提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为事务增加默认开启的全局提醒、单条事务提前时间覆盖和真实系统调度。

**Architecture:** 在 `ScheduleEvent` 中保存单条覆盖值，在用户偏好中保存全局开关和提前分钟数。新增事务提醒调度器统一解析重复事务并复用现有系统通知接收器，事务保存变更时由应用容器重排。

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Android AlarmManager, kotlinx.serialization, JUnit。

**Spec:** `docs/superpowers/specs/2026-11-24-event-reminders-design.md`

## Global Constraints

- 默认事务提醒开启，全局提前 20 分钟。
- 旧事务没有单条覆盖时跟随全局设置。
- 不改变课程提醒和事务重复规则。
- UI 遵守 `docs/ui-guidelines.local.md`。
- 不推送远端，不创建 Release。

## Review Focus

- 全局关闭后不能继续留下旧事务闹钟，覆盖值也不能绕过全局开关。测试归入调度器任务。
- 重复事务只提醒下一次有效发生，不为已过去发生时间创建闹钟。测试归入调度器任务。
- 修改事务时间、日期或重复规则后旧提醒必须撤销并安排新提醒。测试归入仓库和调度任务。
- 单条覆盖为关闭时不能被全局开启重新启用，跟随全局时全局分钟数变化要生效。测试归入模型任务。
- 旧序列化事务读取后仍能正常显示和保存。测试归入模型任务。

### Task 1: 事务提醒模型和全局偏好

**Files:**
- Modify: `core-kernel/src/main/java/com/x500x/cursimple/core/kernel/model/ScheduleEvent.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/AppPreferencesViewModel.kt`
- Test: `core-kernel/src/test/java/com/x500x/cursimple/core/kernel/model/ScheduleEventTest.kt`
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/EventReminderPreferencesTest.kt`

**Interfaces:**
- `ScheduleEvent.reminderMinutes: Int?`, null means follow global, 0 means on time, negative values are invalid and normalized away.
- `EventReminderPreferences(enabled: Boolean = true, advanceMinutes: Int = 20)`.
- `UserPreferences.scheduleEventReminder: EventReminderPreferences`.
- Repository methods `setScheduleEventReminderEnabled(Boolean)` and `setScheduleEventReminderAdvanceMinutes(Int)`.

- [ ] Write failing tests for old event JSON compatibility, reminder override semantics, and preference defaults.
- [ ] Run the focused tests and confirm they fail for the missing field and preference accessors.
- [ ] Implement the serializable event field and DataStore preference mapping.
- [ ] Run focused core tests and confirm they pass.

### Task 2: Pure occurrence and alarm plan

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/notice/ScheduleEventReminderPlanner.kt`
- Modify: `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/model/ReminderModels.kt` only if a shared plan type is needed
- Test: `app/src/test/java/com/x500x/cursimple/app/notice/ScheduleEventReminderPlannerTest.kt`

**Interfaces:**
- `ScheduleEventReminderPlanner.nextReminder(event, now, globalPreferences): EventReminderPlan?`.
- `EventReminderPlan(eventId, occurrenceDate, triggerAtMillis, title, location)`.

- [ ] Write failing tests for one-time events, weekly events, selected week dates, past events, global off, per-event off, and per-event override.
- [ ] Run the focused planner tests and confirm they fail.
- [ ] Implement the pure planner using `ScheduleEvent.occursOn` and the project time zone.
- [ ] Run the planner tests and confirm they pass.

### Task 3: Alarm scheduling and receiver

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/notice/ScheduleEventReminderScheduler.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/notice/ClassNoticeGateway.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/notice/ClassNoticeScheduler.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/x500x/cursimple/app/notice/ScheduleEventReminderSchedulerTest.kt`

**Interfaces:**
- `ScheduleEventReminderScheduler.reschedule(context)`.
- `ScheduleEventReminderScheduler.cancelAll(context)`.
- `ScheduleEventReminderScheduler.deliver(context, eventId, occurrenceDate)`.

- [ ] Write failing scheduler tests for cancel-and-reschedule and stable PendingIntent identity.
- [ ] Run focused scheduler tests and confirm they fail.
- [ ] Implement one next-occurrence AlarmManager alarm per event and a receiver that posts the existing notification style and reschedules.
- [ ] Call reschedule from the gateway after event or preference changes and from environment maintenance.
- [ ] Run scheduler tests and the existing reminder tests.

### Task 4: Settings and transaction detail UI

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/RemindersScreen.kt` if the reminders home needs the entry
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleEventUi.kt`
- Modify: `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Test: `app/src/test/java/com/x500x/cursimple/app/SettingsSearchTest.kt` or a focused settings test
- Test: `app/src/androidTest/java/com/x500x/cursimple/app/EventReminderUiQaTest.kt`

**Interfaces:**
- New settings destination `ScheduleEventReminders`.
- `ScheduleEventReminderSection(preferences, onEnabledChange, onAdvanceMinutesChange)`.
- `ScheduleEventDetailDialog` receives `reminderMinutes` and `onReminderChange`.

- [ ] Write failing UI tests for the settings row, default enabled state, global minute choices, and single-event override choices.
- [ ] Run UI tests and confirm the new nodes are absent.
- [ ] Add the settings menu and form using existing settings cards and stepper or option rows.
- [ ] Add a “提醒” row to the event detail dialog with follow-global, off, and minute options.
- [ ] Thread event reminder updates through `ScheduleViewModel.saveEvent`.
- [ ] Run UI tests in Chinese and English layouts.

### Task 5: Integration and device verification

**Files:**
- Modify: `app/src/androidTest/java/com/x500x/cursimple/app/EventReminderUiQaTest.kt`
- Modify: `app/src/androidTest/java/com/x500x/cursimple/app/WidgetQaTest.kt` only if event reminder state affects fixtures

- [ ] Run all focused unit tests and app compilation.
- [ ] Install the local Debug APK over the current simulator app without clearing data.
- [ ] Verify the settings page, single-event detail, and alarm scheduling on the emulator.
- [ ] Verify a one-minute or immediate QA event delivers once and the next repeat is scheduled.
- [ ] Record any unverified manufacturer-specific notification behavior without claiming universal delivery.
