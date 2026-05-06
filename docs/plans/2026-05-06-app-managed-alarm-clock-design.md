# App 自管 AlarmManager.setAlarmClock 闹钟设计

日期：2026-05-06
状态：按用户确认方向整理，待实现

## 目标

- 新增 App 自管 `AlarmManager.setAlarmClock()` 闹钟通道，并作为默认通道。
- 保留现有系统时钟 App 通道，在设置中允许用户切换。
- 用户刚设置规则时，如果规则当天仍有未过期触发时间，立即设置当天闹钟。
- 每天 22:00 检查并设置下一天闹钟。
- 每节课下课时检查后续闹钟，并删除前面已经过去的 App 自管系统闹钟。
- 用户删除规则时删除该规则对应闹钟。
- 添加闹钟前先查 App 登记簿，确认未添加过同一个计划。
- 响铃时长、响铃间隔、响铃次数都可以设置。
- App 本体和小组件共用 40 分钟轮询节流状态；任一端触发后，另一端在 40 分钟内不重复轮询。
- 提前向用户申请 `SCHEDULE_EXACT_ALARM` 特殊权限，并引导关闭电池优化、允许通知和厂商自启动。

## 官方资料核对

- `AlarmManager.setAlarmClock()` 是面向闹钟应用的精确可见闹钟，系统会向用户显示下一次闹钟，并在触发时发送配置的 `PendingIntent`。
- Android 12+ 精确闹钟需要检查 `AlarmManager.canScheduleExactAlarms()`；未授权时应跳转 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 让用户授予“闹钟与提醒”权限。
- Android 文档说明 `setAlarmClock()` 这类闹钟用于用户可见的定时提醒，并且在低电耗/Doze 场景下具有比普通后台任务更强的触发能力。
- `AlarmManager.cancel(pendingIntent)` 只能取消与之前设置时匹配的 `PendingIntent`，因此 App 自管闹钟必须保存稳定 requestCode/action/data，删除时重建同一个 `PendingIntent`。
- 省电白名单通过 `PowerManager.isIgnoringBatteryOptimizations()` 检查，必要时用 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 请求用户授权；这不能绕过厂商所有限制，但能降低系统省电策略对响铃服务和轮询的影响。
- 前台服务适合承载响铃播放，通知需提供停止动作；响铃过程用短时 WakeLock，并在停止、超时和异常路径释放。

参考：

- Android `AlarmManager`: https://developer.android.com/reference/android/app/AlarmManager
- Android 闹钟调度说明: https://developer.android.com/develop/background-work/services/alarms/schedule
- Android 精确闹钟权限变更: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Android 低电耗与应用待机: https://developer.android.com/training/monitoring-device-state/doze-standby
- Android WakeLock: https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set
- Android Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android `AlarmClock` Intent: https://developer.android.com/reference/android/provider/AlarmClock

## 方案选择

推荐方案：双通道、单协调器、默认 App 自管闹钟。

- `AppAlarmClock`：默认通道，使用 `AlarmManager.setAlarmClock()` 设置 App 自己的可见闹钟。触发后进入 App 的响铃 Receiver 和前台响铃服务。
- `SystemClockApp`：保留现有系统时钟 App 通道，继续使用 `AlarmClock.ACTION_SET_ALARM` 和 `ACTION_DISMISS_ALARM`。
- 两个通道共用提醒规则、提醒规划、登记簿、检查窗口和轮询节流；区别只在提交、删除和响铃实现。

不采用混合双发。双发会造成重复响铃、重复删除和错误去重，不能作为正式架构手段。

## 数据模型

新增全局提醒设置，存入 `UserPreferences`：

- `alarmBackend`：`AppAlarmClock` 或 `SystemClockApp`，默认 `AppAlarmClock`。
- `alarmRingDurationSeconds`：单次响铃时长，默认 60 秒。
- `alarmRepeatIntervalSeconds`：每次响铃之间的间隔，默认 120 秒。
- `alarmRepeatCount`：最多响铃次数，默认 1 次。
- `lastAlarmPollAtMillis`：App 与小组件共用的 40 分钟轮询节流时间。

扩展 `SystemAlarmRecord`：

- `backend`：记录该闹钟由哪个通道创建。
- `requestCode`：App 自管通道取消闹钟时重建 `PendingIntent`。
- `triggerAtMillis`、`alarmKey`、`ruleId`、`planId`、`courseId`：沿用现有登记与去重能力。
- `alarmLabel`：保留给系统时钟 App 通道删除标签使用。

`alarmKey` 继续由 `pluginId + triggerAtMillis + title + message + ringtoneUri` 生成，同一个计划只登记一次。

## 权限与省电策略

启动或进入提醒设置时检查：

1. `SCHEDULE_EXACT_ALARM`
   - Android 12+ 调用 `AlarmManager.canScheduleExactAlarms()`。
   - 未授权时，在提醒设置页展示明确状态和按钮，跳转 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`。
   - 未授权时不创建 App 自管闹钟，只保存规则并提示“精确闹钟权限未开启”。

2. 通知权限
   - Android 13+ 未授予 `POST_NOTIFICATIONS` 时提醒用户授权。
   - 未授权时仍允许保存规则，但响铃服务无法可靠展示通知，需要在状态中提示。

3. 电池优化
   - 用 `PowerManager.isIgnoringBatteryOptimizations(packageName)` 检查。
   - 未加入白名单时提供按钮跳转 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
   - 文案说明：`setAlarmClock()` 负责闹钟触发；电池白名单用于提高响铃服务、轮询和厂商后台场景可靠性。

4. 厂商自启动
   - 不硬编码厂商私有 Intent 作为正式流程。
   - 设置页提供说明：允许自启动、后台运行、锁屏显示、通知全屏/横幅。

## 调度时机

所有入口都进入 `ReminderCoordinator.syncAlarmsForWindow(...)`，由当前 `alarmBackend` 决定提交通道。

### 用户刚设置规则

- 窗口：当前时间到当天 23:59:59。
- 只创建当天尚未过去的闹钟。
- 添加前查登记簿，已有同 `alarmKey + backend` 则跳过。

### 每天 22:00

- 内部检查闹钟仍由 `AlarmManager.setExactAndAllowWhileIdle()` 唤起。
- 窗口：下一天 00:00:00 到 23:59:59。
- 检查并创建下一天所有符合规则且未登记的闹钟。
- 完成后重排下一次 22:00 检查。

### 每节课下课

- 内部检查闹钟在每个下课时间唤起。
- 先删除当前时间之前的 App 自管闹钟登记，并调用 `AlarmManager.cancel(pendingIntent)`。
- 再检查当前时间到当天 23:59:59 的后续闹钟。
- 完成后重排下一次下课检查。

### 40 分钟共用轮询

- App 本体和小组件都调用统一 `tryPollAlarms(reason)`。
- 读取 `lastAlarmPollAtMillis`，若距离上次检查不足 40 分钟则直接跳过。
- 任何成功进入检查的入口都先更新时间戳，再执行当天窗口同步。
- “上次查询”包含用户建规则、22:00 检查、下课检查、App 触发轮询、小组件触发轮询。

## App 自管闹钟提交

`AppAlarmClockDispatcher`：

1. 检查 `SCHEDULE_EXACT_ALARM`。
2. 为每个计划创建稳定 `PendingIntent.getBroadcast(...)`。
3. 创建 `AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)`。
4. 调用 `alarmManager.setAlarmClock(info, operationIntent)`。
5. 成功后写入登记簿；失败不登记，下次检查可重试。

删除时：

1. 根据登记记录重建同一个 operation `PendingIntent`。
2. 调用 `alarmManager.cancel(pendingIntent)`。
3. 删除登记记录。

## 响铃实现

`AppAlarmReceiver`：

- 接收闹钟触发。
- 读取 `alarmKey`、`ruleId`、`planId`、`ringtoneUri`。
- 启动 `AlarmRingingService`。
- 所有异常只记录日志，避免 BroadcastReceiver 崩溃。

`AlarmRingingService`：

- 前台服务，展示高优先级响铃通知和停止按钮。
- 播放用户选择铃声；为空时使用系统默认闹钟铃声。
- 每次响铃最多播放 `alarmRingDurationSeconds`。
- 若 `alarmRepeatCount > 1`，停止播放后等待 `alarmRepeatIntervalSeconds`，继续下一次。
- 到达次数、用户点击停止、服务销毁或播放异常时结束。
- 使用短时 `PARTIAL_WAKE_LOCK`，超时时间覆盖一次响铃周期，并在所有退出路径释放。
- 响铃开始时记录 missed-alarm 检测所需日志；如果触发时间明显晚于计划时间，则发出迟到提醒日志/通知。

## 防崩溃

- 所有 `AlarmManager`、`startForegroundService`、通知、MediaPlayer/Ringtone、WakeLock 操作使用 `runCatching` 并写结构化日志。
- `BroadcastReceiver` 使用 `goAsync()` 或只做轻量启动，不在主线程执行长任务。
- 前台服务必须及时 `startForeground()`；失败时停止服务并记录。
- 未授权精确闹钟、通知被禁用、电池优化未关闭都返回明确状态，不抛异常到 UI。
- 登记簿写入失败时不认为闹钟已创建，避免未来无法删除。

## 设置页

在提醒设置中新增：

- 闹钟通道：`App 自管闹钟`（默认） / `系统时钟 App 闹钟`。
- 精确闹钟权限状态与“去开启”按钮。
- 通知权限状态与“去授权”按钮。
- 省电优化状态与“允许后台运行”按钮。
- 响铃时长、响铃间隔、响铃次数设置。

切换通道时：

- 不立即删除另一通道历史登记。
- 下一次同步会按当前通道创建新闹钟；失效登记按通道分别清理。
- 用户删除规则时删除所有通道下该规则对应登记，避免遗留。

## 验证

- 单元测试：默认 backend 为 `AppAlarmClock`。
- 单元测试：`SCHEDULE_EXACT_ALARM` 未授权时不登记 App 自管闹钟。
- 单元测试：同一 `alarmKey + backend` 不重复添加。
- 单元测试：删除规则会取消 App 自管 PendingIntent 并删除登记。
- 单元测试：40 分钟轮询状态由 App 与小组件共用。
- 编译验证 `:core-reminder`、`:core-data`、`:feature-schedule`、`:feature-widget`、`:app`。
