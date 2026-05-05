# Schedule Logs Alarm Stability Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 优化课表左侧节次时间显示，完善 App 日志导出与清理，并加固系统闹钟/应用提醒调度，避免设置失败导致崩溃。

**Architecture:** UI 只改课表时间栏渲染，不改变课表模型。日志采用 App 自有文件诊断日志 + 插件诊断日志 + 当前进程完整 logcat 的导出组合，并用 WorkManager 每 3 天执行清理。提醒调度保留现有规则模型，所有系统闹钟和应用内闹钟调用都先检查能力并返回结构化结果。

**Tech Stack:** Kotlin、Jetpack Compose、Android AlarmManager、AlarmClock Intent、NotificationCompat、WorkManager、JUnit4、pwsh。

---

## File Structure

- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
  - 拆分左侧节次时间为开始/结束两行，并收窄日视图和周视图时间栏。
- Create: `app/src/main/java/com/kebiao/viewer/app/util/AppDiagnostics.kt`
  - App 自有结构化诊断日志、文件 sink、3 天清理 helper。
- Modify: `app/src/main/java/com/kebiao/viewer/app/util/LogExporter.kt`
  - 导出完整 logcat，合并 App 与插件诊断日志，提供自动清理入口。
- Create: `app/src/main/java/com/kebiao/viewer/app/util/LogCleanupWorker.kt`
  - WorkManager 3 天周期清理任务。
- Modify: `app/src/main/java/com/kebiao/viewer/app/ClassScheduleApplication.kt`
  - 安装 App/插件/提醒日志 sink，并注册日志清理任务。
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
  - 更新导出日志文案。
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/logging/ReminderLogger.kt`
  - 提醒模块结构化日志。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/AlarmDispatcher.kt`
  - 加固系统闹钟、应用内闹钟和通知发送。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - 增加调度失败汇总 helper。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
  - 记录同步汇总。
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - 创建提醒后显示调度成功/失败摘要。
- Modify: `app/src/main/AndroidManifest.xml`
  - 声明精确闹钟权限。
- Modify: `app/src/test/java/com/kebiao/viewer/app/util/LogExporterTest.kt`
  - 覆盖完整 logcat 命令、App 诊断合并和 3 天清理。

## Chunk 1: 课表时间栏

- [ ] 将 `slotTimeRange()` 改成输出两行 `startTime\nendTime`。
- [ ] 给时间文本设置紧凑 `lineHeight` 与 `maxLines = 2`。
- [ ] 周视图 `timeColumnWidth` 改为按屏宽选择 44/48/52dp。
- [ ] 日视图 `DayRow` 左侧列从固定 62dp 改为 `widthIn(min = 38.dp, max = 52.dp)`。

## Chunk 2: 日志系统

- [ ] 新增 App 诊断日志文件 sink，写入时间、等级、tag、事件和字段。
- [ ] `LogExporter` 去掉 `maxLines` 参数和 `logcat -t`。
- [ ] 导出文件增加 `# App diagnostics` 章节。
- [ ] 保留 `# Plugin diagnostics` 章节。
- [ ] 新增 `cleanupExpiredLogs(cacheDir, now, retentionMillis)`，清理 3 天前日志。
- [ ] 新增 WorkManager 周期任务，每 3 天执行一次清理。
- [ ] App 启动时注册清理任务。

## Chunk 3: 闹钟稳定性

- [ ] Manifest 声明 `android.permission.SCHEDULE_EXACT_ALARM`。
- [ ] 新增 `ReminderLogger`，支持 logcat 与可注入 sink。
- [ ] 系统时钟通道直接尝试启动 `ACTION_SET_ALARM`，捕获 `ActivityNotFoundException`、`SecurityException` 与 `RuntimeException`。
- [ ] 应用内闹钟通道调用 `setAlarmClock` 前检查 `canScheduleExactAlarms()`。
- [ ] 应用内闹钟通道捕获所有运行时异常并返回失败结果。
- [ ] 通知发送前检查通知是否启用，发送失败只记录日志。
- [ ] 提醒同步后按结果生成用户可读摘要。

## Chunk 4: 测试与验证

- [ ] 更新 `LogExporterTest`，覆盖 `logcat` 命令不含 `-t`。
- [ ] 更新 `LogExporterTest`，覆盖 App 诊断日志合并。
- [ ] 更新 `LogExporterTest`，覆盖 3 天清理边界。
- [ ] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:testDebugUnitTest'`
- [ ] Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:assembleDebug :feature-schedule:assembleDebug :app:assembleDebug'`
- [ ] Run: `pwsh -NoLogo -NoProfile -Command 'rg -n "-t|2000|exportRecentLogs\\(context, maxLines" app/src/main/java app/src/test/java'`

## Chunk 5: 收尾但不提交

- [ ] Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`
- [ ] 检查没有意外修改签名、本地配置或生成产物。
- [ ] 不执行 `git add` 或 `git commit`。
