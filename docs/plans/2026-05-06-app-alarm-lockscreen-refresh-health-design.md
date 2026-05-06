# App 自管闹钟锁屏交互、刷新与保活设计

日期：2026-05-06
状态：已按用户确认方向整理，待实现

## 背景

当前 App 自管闹钟已经使用 `AlarmManager.setAlarmClock()`、前台响铃服务、公开高优先级通知和锁屏 Activity。还需要补齐三类体验：

- 锁屏上像系统时钟 App 一样支持关闭、延后 5 分钟、左右滑动和音量键操作。
- 闹钟触发拉起 App/服务时，利用这个机会检查后续闹钟和保活设施。
- 提醒状态、App 内闹钟登记、小组件新增/删除和提醒小组件要及时更新，尤其要在用户真正关闭或延后闹钟后刷新。

参考 Fossify Clock 与 BlackyHawky Clock 后，推荐做法是：响铃服务只负责当前响铃生命周期，响铃触发时立即安排下一批闹钟，用户停止或延后时再提交最终状态变更并刷新 UI/小组件。

## 目标

- 锁屏响铃页支持按钮、左右滑动、音量键三类操作。
- 通知 action 和锁屏页操作走同一套 service action，避免状态分叉。
- 响铃拉起时执行闹钟维护和保活设施体检。
- 用户关闭闹钟时，移除当前触发登记，按规则删除一次性提醒，并刷新 App 与小组件。
- 用户延后 5 分钟时，移除当前触发登记，登记新的延后闹钟，并刷新 App 与小组件。
- 提醒小组件能显示已登记的延后闹钟，不出现“实际还会响，但组件看不到”的状态。
- 小组件新增、删除、尺寸变化、系统触发更新时，都刷新安装状态、提醒状态和必要的闹钟维护。

## 非目标

- 不接管系统时钟 App 的内部闹钟列表。
- 不把无法静默授权的系统权限做成假修复。精确闹钟、通知、全屏 intent、电池优化等只能检查状态、记录日志，并在前台引导用户处理。
- 不引入临时回退方案。所有新增路径都要进入明确的数据模型和生命周期。

## 交互设计

`AlarmRingingActivity` 作为锁屏响铃页：

- 显示课程标题、提醒文案、触发时间。
- 底部保留两个明确按钮：`关闭`、`延后 5 分钟`。
- 右滑执行关闭。
- 左滑执行延后 5 分钟。
- 音量上执行关闭。
- 音量下执行延后 5 分钟。
- 任意操作只允许处理一次，后续重复按键或重复滑动会被忽略。

按钮、手势和音量键都调用同一个 `sendServiceAction(...)`，最终由 `AlarmRingingService.ACTION_STOP` 或 `ACTION_SNOOZE` 处理。

## 响铃生命周期

### 响铃开始

`AlarmRingingService.ACTION_RING` 到达后：

1. 立即 `startForeground()`，保证铃声和锁屏通知可见。
2. 启动铃声、震动和短时 WakeLock。
3. 异步执行 `AlarmRuntimeMaintenance.onAlarmStarted(...)`：
   - 重新安排每日 22:00 检查。
   - 重新安排下一次下课检查。
   - 确保小组件 WorkManager 周期任务存在。
   - 检查精确闹钟、通知、全屏 intent、通知 channel、电池优化状态。
   - 同步今天剩余闹钟。
   - 若当前本地时间已到下午或晚上，同步下一天闹钟。

响铃开始阶段不把它当作最终用户状态刷新点。这里可以修正后续闹钟和基础设施，但当前闹钟仍处于“正在响”的生命周期。

### 用户关闭

`ACTION_STOP` 进入最终状态处理：

1. 停止铃声、震动、WakeLock 和前台通知。
2. 调用 `ReminderCoordinator.finishTriggeredAppAlarm(..., finishAction = Dismiss)`：
   - 删除当前 `alarmKey` 对应登记。
   - 对一次性单课提醒，按现有规则删除提醒规则和该规则未来登记。
   - 对首次课提醒等可复用规则，只删除本次触发登记。
3. 执行 `AlarmRuntimeMaintenance.onAlarmFinished(...)`：
   - 刷新 App 可观察到的登记状态。
   - 刷新所有桌面小组件，尤其是提醒小组件。
   - 安排/检查今天剩余和必要的下一天闹钟。

App 内页面已经通过 `systemAlarmRecordsFlow` 和 `reminderRulesFlow` 观察仓储；只要最终状态写入仓储，前台页面会自然刷新。后台场景则通过小组件刷新和下次进入页面读取仓储反映。

### 用户延后 5 分钟

`ACTION_SNOOZE` 进入最终状态处理：

1. 停止当前铃声、震动、WakeLock 和前台通知。
2. 构造新的延后 `ReminderPlan`，触发时间为当前时间 + 5 分钟。
3. 调用 `ReminderCoordinator.finishTriggeredAppAlarm(..., finishAction = Snooze(plan))`：
   - 删除当前触发登记。
   - 用 `AppAlarmClockDispatcher` 设置新的延后闹钟。
   - 成功后写入 `SystemAlarmRecord`，标记为 `AppAlarmOperationMode.SnoozeForegroundService`。
   - 延后登记保存 `displayTitle` 和 `displayMessage`，供设置页和提醒小组件展示。
4. 刷新 App 与所有小组件。

延后闹钟必须进入登记簿，而不是只创建一个无法观察的 `PendingIntent`。这样设置页、App 状态和提醒小组件都能看到“已延后”的真实状态。

## 数据模型

扩展 `AppAlarmOperationMode`：

- `ForegroundService`：正常 App 自管闹钟。
- `SnoozeForegroundService`：用户延后产生的一次性 App 自管闹钟。

扩展 `SystemAlarmRecord`：

- `displayTitle: String?`
- `displayMessage: String?`

现有 `message` 和 `alarmLabel` 保持兼容。`alarmLabel` 继续用于系统时钟 App 删除标签；`displayTitle/displayMessage` 用于 App UI 和小组件展示。

## 保活设施体检

新增 `AlarmRuntimeMaintenance`，由 app 模块持有，复用 `AppContainer` 和已有仓储：

- 可修复项：
  - `SystemAlarmCheckScheduler.scheduleDailyNextDayCheck(...)`
  - `SystemAlarmCheckScheduler.scheduleNextAfterClassCheck(...)`
  - `ScheduleWidgetWorkScheduler.schedule(...)`
  - `ScheduleWidgetUpdater.refreshAll(...)`
- 只能检查和记录的项：
  - `AlarmManager.canScheduleExactAlarms()`
  - `NotificationManagerCompat.areNotificationsEnabled()`
  - Android 14+ `NotificationManager.canUseFullScreenIntent()`
  - 响铃通知 channel 是否被关闭或降级
  - `PowerManager.isIgnoringBatteryOptimizations(packageName)`

检查结果写入结构化日志，并在用户进入设置页时通过已有设置入口展示。后台响铃时不弹无意义的系统设置页。

## 小组件生命周期

新增统一入口 `WidgetLifecycleRefresher.onWidgetSetChanged(context, reason)`：

- 通知 `WidgetCatalog.ACTION_WIDGET_INSTALLED_CHANGED`。
- 调用 `ScheduleWidgetWorkScheduler.schedule(context)`。
- 异步执行 `WidgetSystemAlarmSynchronizer.reconcileToday(context)`。
- 调用 `ScheduleWidgetUpdater.refreshAll(context)`。

接入点：

- 三类小组件的 `onUpdate`。
- 三类小组件的 `onDeleted`。
- 课表小组件的 `onAppWidgetOptionsChanged`。
- `requestPinAppWidget` 成功回调广播。

提醒小组件渲染时合并两类来源：

- 按提醒规则展开的未来 `ReminderPlan`。
- `SystemAlarmRecord` 中未来的 `SnoozeForegroundService` 延后登记。

合并时用 `alarmKey`/`planId` 去重，按触发时间排序。

## 错误处理

- 响铃开始和结束维护都不能阻塞停止铃声。
- 延后闹钟设置失败时，不保存延后登记，并在日志里记录失败原因。
- 刷新小组件失败不影响闹钟停止；失败只记录日志。
- 权限或保活设施异常不引入隐式替代方案，只记录状态并等待前台引导用户处理。

## 验证

- 单元测试：触发闹钟关闭后删除当前 App 自管登记。
- 单元测试：一次性单课提醒关闭后删除规则；首次课提醒关闭后保留规则。
- 单元测试：延后 5 分钟会写入 `SnoozeForegroundService` 登记。
- 单元测试：延后闹钟设置失败时不写入登记。
- 单元测试：提醒小组件数据源包含未来延后登记。
- 单元测试：小组件删除会触发安装状态通知和刷新入口。
- 编译验证：`:core-reminder:testDebugUnitTest`、`:feature-widget:testDebugUnitTest`、`:app:compileDebugKotlin`。
