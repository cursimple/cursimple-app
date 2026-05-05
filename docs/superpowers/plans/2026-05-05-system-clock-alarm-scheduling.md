# System Clock Alarm Scheduling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 让课程提醒只通过系统时钟 App 创建/关闭闹钟，并用成功登记簿避免重复添加。

**Architecture:** 保留现有 `ReminderRule -> ReminderPlanner -> ReminderPlan` 结构。新增系统闹钟登记簿和窗口化同步入口，所有课程闹钟提交都走 `AlarmClock.ACTION_SET_ALARM`，过期或失效闹钟通过带唯一标签的 `AlarmClock.ACTION_DISMISS_ALARM` 请求关闭；内部检查闹钟只负责在 22:00 和下课时唤起检查，不作为正式课程闹钟。

**Tech Stack:** Kotlin、Android AlarmClock Intent、AlarmManager、BroadcastReceiver、DataStore、kotlinx.serialization、JUnit4、pwsh。

---

## File Structure

- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - 增加系统闹钟登记模型、调度窗口模型、同步原因枚举。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderRepository.kt`
  - 增加登记簿读写、按规则删除、清理过期登记的方法。
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/DataStoreReminderRepository.kt`
  - 用 DataStore 保存系统闹钟登记列表。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/AlarmDispatcher.kt`
  - 保留系统时钟 App 通道，移除正式流程中的 AppAlarm 分支使用。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
  - 新增窗口化系统时钟同步、成功后登记、失败不登记、登记去重、下次检查安排。
- Create: `app/src/main/java/com/kebiao/viewer/app/reminder/SystemAlarmCheckReceiver.kt`
  - 接收 22:00 和下课检查的内部唤起广播。
- Modify: `core-reminder/src/main/AndroidManifest.xml`
  - 移除旧应用内闹钟 receiver 注册。
- Modify: `app/src/main/AndroidManifest.xml`
  - 注册内部检查 receiver 和开机重排检查权限。
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`
  - 为 receiver 提供静态入口需要的仓储组装 helper，或新增 coordinator 工厂。
- Modify: `app/src/main/java/com/kebiao/viewer/app/ClassScheduleApplication.kt`
  - 启动时安排每日 22:00 检查和当天下一次下课检查。
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - 创建提醒规则后只检查当天窗口；删除规则时清理登记。
- Create/Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`
  - 覆盖登记簿成功去重、失败不登记、失败后可重试。

## Chunk 1: 模型与仓储

- [x] 在 `ReminderModels.kt` 新增 `SystemAlarmRecord`。
- [x] 在 `ReminderModels.kt` 新增 `ReminderSyncWindow(startMillis, endMillis)`。
- [x] 在 `ReminderModels.kt` 新增 `ReminderSyncReason`，包含 `RuleCreatedToday`、`DailyNextDay`、`AfterClassToday`、`ScheduleChanged`。
- [x] 在 `ReminderRepository` 增加 `systemAlarmRecordsFlow`、`getSystemAlarmRecords()`、`saveSystemAlarmRecord(record)`、`removeSystemAlarmRecord(alarmKey)`、`removeSystemAlarmRecordsForRule(ruleId)`、`clearSystemAlarmRecords()`、`clearSystemAlarmRecordsBefore(cutoffMillis)`。
- [x] 在 `DataStoreReminderRepository` 增加 `KEY_SYSTEM_ALARM_RECORDS`，读写 `SystemAlarmRecord` 列表。
- [x] 写单元测试覆盖成功登记、同 key 跳过、失败不登记。

## Chunk 2: 系统时钟提交与成功登记

- [x] 在 `ReminderCoordinator` 新增 `syncSystemClockAlarmsForWindow(pluginId, schedule, timingProfile, window, reason)`。
- [x] 该方法只展开 enabled rule，并只保留 `triggerAtMillis in window.startMillis..window.endMillis` 的计划。
- [x] 调度前读取登记簿，已存在同 `alarmKey` 的计划跳过。
- [x] 对未登记计划调用 `SystemAlarmClockDispatcher.dispatch(plan)`。
- [x] 只有 `result.succeeded == true` 时保存 `SystemAlarmRecord`。
- [x] 失败时只返回结果和写日志，不保存登记。
- [x] 将创建提醒、批量提醒、首次课提醒保存后的同步入口改为当天窗口且固定走系统时钟 App。
- [x] 更新状态消息，展示成功添加、已添加跳过、无法安全表达日期和失败数量。

## Chunk 3: 检查时机

- [x] 新建 `SystemAlarmCheckReceiver`，支持 action `ACTION_DAILY_NEXT_DAY_CHECK` 和 `ACTION_AFTER_CLASS_CHECK`。
- [x] receiver 通过 `ClassScheduleApplication.appContainer` 读取当前 schedule、pluginId、timingProfile、临时调课配置。
- [x] 每天 22:00 检查窗口为明天 00:00:00 到 23:59:59。
- [x] 下课检查窗口为当前时刻到当天 23:59:59。
- [x] 在独立 scheduler 中新增 `scheduleDailyNextDayCheck(context, timingProfile)`。
- [x] 新增 `scheduleNextAfterClassCheck(context, timingProfile, now)`，从 slot endTime 中选择下一次下课检查。
- [x] 每次 receiver 检查结束后重新安排对应下一次检查。
- [x] App 启动、课表同步完成、手动课程变化、提醒规则变化后重新安排检查。

## Chunk 4: 清理与数据生命周期

- [x] 删除单条提醒规则时调用 `removeSystemAlarmRecordsForRule(ruleId)`。
- [x] 清空全部课表时调用 `clearSystemAlarmRecords()`。
- [x] 调度前按当前时间请求删除并清理过期登记，避免误删同日稍晚闹钟。
- [x] 下课检查、规则删除、课表变化和小组件刷新时按唯一标签请求删除过期或失效的系统时钟闹钟。
- [x] 清理登记簿时只以当前时间为过期线，避免 22:00 检查明天闹钟时误删今天晚间尚未触发的闹钟。

## Chunk 5: 测试与验证

- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :feature-schedule:testDebugUnitTest'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:compileDebugKotlin :core-data:compileDebugKotlin :feature-schedule:compileDebugKotlin :app:compileDebugKotlin'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:assembleDebug'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command 'rg -n "syncRulesForSchedule|AppAlarm|ReminderAlarmReceiver|HybridAlarm|AlarmDispatchChannel\\.AppAlarm|响铃时长|响铃间隔|响铃次数|repeatCount" core-reminder core-data feature-schedule app'`
- [x] Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`
- [x] 不执行 `git add` 或 `git commit`。
