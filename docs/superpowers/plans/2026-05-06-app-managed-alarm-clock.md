# App Managed Alarm Clock Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 将课程提醒默认改为 App 自管 `AlarmManager.setAlarmClock()` 闹钟，同时保留现有系统时钟 App 通道并支持设置页切换。

**Architecture:** 保留现有 `ReminderRule -> ReminderPlanner -> ReminderCoordinator` 主线，把“提交/删除闹钟”抽成按 backend 路由的通道。App 自管通道用 `setAlarmClock()` + 前台响铃服务 + 短时 WakeLock；系统时钟 App 通道沿用 `AlarmClock` Intent。所有入口共用登记簿、检查窗口和 40 分钟轮询节流。

**Tech Stack:** Kotlin、Android AlarmManager、AlarmClockInfo、PendingIntent、BroadcastReceiver、Foreground Service、Notification、WakeLock、DataStore、Jetpack Compose、WorkManager、JUnit4、pwsh。

---

## File Structure

- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - 增加 `ReminderAlarmBackend`、`ReminderAlarmSettings`、App 自管闹钟记录字段和通道枚举。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/AlarmDispatcher.kt`
  - 增加 `AppAlarmClockDispatcher`、`AppAlarmClockDismisser`、精确闹钟权限检查和稳定 `PendingIntent` 构造。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
  - 改为按当前 backend 同步/删除闹钟；下课检查时删除过期 App 自管闹钟；保留旧系统时钟 App 通道。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderRepository.kt`
  - 如需新增按 backend 清理/查询方法，在接口中补齐。
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/UserPreferencesRepository.kt`
  - 增加全局闹钟设置、40 分钟轮询时间戳和原子 claim 方法。
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreUserPreferencesRepository.kt`
  - 持久化闹钟 backend、响铃参数、`lastAlarmPollAtMillis`。
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/DataStoreReminderRepository.kt`
  - 兼容扩展后的 `SystemAlarmRecord`。
- Create: `app/src/main/java/com/kebiao/viewer/app/reminder/AppAlarmReceiver.kt`
  - 接收 App 自管闹钟触发，启动响铃服务。
- Create: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRingingService.kt`
  - 前台服务，负责铃声、震动、重复、停止按钮、WakeLock。
- Create: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmPermissionIntents.kt`
  - 统一生成精确闹钟、电池优化、应用详情设置 Intent。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/SystemAlarmCheckReceiver.kt`
  - 继续安排 22:00 和下课检查；检查完成后更新共用查询时间。
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`
  - 注入 `ReminderAlarmSettings` provider；增加强制检查与 40 分钟轮询检查入口。
- Modify: `app/src/main/java/com/kebiao/viewer/app/ClassScheduleApplication.kt`
  - 启动时安排检查与小组件 worker，必要时触发一次轮询 claim。
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppPreferencesViewModel.kt`
  - 增加设置 backend、响铃时长、间隔、次数的方法。
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
  - 在设置页增加闹钟通道、权限状态、省电状态、响铃参数控件。
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - 状态文案从“系统闹钟”调整为当前 backend；创建规则后仍强制同步当天窗口。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetSystemAlarmSynchronizer.kt`
  - 小组件使用同一设置和 40 分钟轮询 claim。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`
  - worker 刷新后调用共用轮询入口，避免与 App 本体重复检查。
- Modify: `app/src/main/AndroidManifest.xml`
  - 增加 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`WAKE_LOCK`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`，注册 Receiver 和 Service。
- Test: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/AppAlarmClockRegistryTest.kt`
- Test: `core-data/src/test/java/com/kebiao/viewer/core/data/UserPreferencesTest.kt`

## Chunk 1: 数据模型与偏好设置

- [ ] **Step 1: 扩展提醒模型**

在 `ReminderModels.kt` 增加：

```kotlin
@Serializable
enum class ReminderAlarmBackend {
    @SerialName("app_alarm_clock")
    AppAlarmClock,

    @SerialName("system_clock_app")
    SystemClockApp,
}

data class ReminderAlarmSettings(
    val backend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val ringDurationSeconds: Int = 60,
    val repeatIntervalSeconds: Int = 120,
    val repeatCount: Int = 1,
)
```

同时把 `AlarmDispatchChannel` 扩展为 `AppAlarmClock` 和 `SystemClockApp`，并给 `SystemAlarmRecord` 增加带默认值的字段：

```kotlin
@SerialName("backend") val backend: ReminderAlarmBackend = ReminderAlarmBackend.SystemClockApp,
@SerialName("requestCode") val requestCode: Int? = null,
```

- [ ] **Step 2: 编译模型**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:compileDebugKotlin'`

Expected: PASS。

- [ ] **Step 3: 扩展 `UserPreferences`**

在 `UserPreferencesRepository.kt` 的 `UserPreferences` 增加：

```kotlin
val alarmBackend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
val alarmRingDurationSeconds: Int = 60,
val alarmRepeatIntervalSeconds: Int = 120,
val alarmRepeatCount: Int = 1,
val lastAlarmPollAtMillis: Long = 0L,
```

接口增加：

```kotlin
suspend fun setAlarmBackend(backend: ReminderAlarmBackend)
suspend fun setAlarmRingDurationSeconds(seconds: Int)
suspend fun setAlarmRepeatIntervalSeconds(seconds: Int)
suspend fun setAlarmRepeatCount(count: Int)
suspend fun markAlarmPollAt(millis: Long)
suspend fun tryClaimAlarmPoll(nowMillis: Long, minIntervalMillis: Long): Boolean
```

- [ ] **Step 4: 持久化闹钟偏好**

在 `DataStoreUserPreferencesRepository.kt` 增加 preferences key。参数写入时做边界收敛：

```kotlin
seconds.coerceIn(5, 600)
count.coerceIn(1, 10)
```

`tryClaimAlarmPoll` 在单次 `store.edit` 内读取旧值并决定是否写入，返回是否 claim 成功。

- [ ] **Step 5: 测试默认值和轮询 claim**

在 `UserPreferencesTest.kt` 增加测试：

```kotlin
@Test
fun alarmSettingsDefaultToAppManagedClock() = runTest {
    val prefs = repository.preferencesFlow.first()
    assertEquals(ReminderAlarmBackend.AppAlarmClock, prefs.alarmBackend)
    assertEquals(60, prefs.alarmRingDurationSeconds)
}
```

另加测试：第一次 `tryClaimAlarmPoll(1000, 2400000)` 返回 true，第二次 `tryClaimAlarmPoll(2000, 2400000)` 返回 false，超过间隔后返回 true。

- [ ] **Step 6: 运行数据测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'`

Expected: PASS。

## Chunk 2: App 自管 setAlarmClock 提交与删除

- [ ] **Step 1: 增加稳定 requestCode**

在 `ReminderModels.kt` 增加：

```kotlin
fun ReminderPlan.appAlarmRequestCode(): Int = systemAlarmKey().hashCode()
```

若担心负数，使用 `systemAlarmKey().hashCode() and Int.MAX_VALUE`。

- [ ] **Step 2: 实现 App 自管 PendingIntent 构造**

在 `AlarmDispatcher.kt` 增加内部 helper：

```kotlin
private fun appAlarmOperationIntent(context: Context, plan: ReminderPlan, requestCode: Int): PendingIntent
```

Intent action 使用固定值，例如 `com.kebiao.viewer.action.APP_ALARM_TRIGGER`，并设置 `setPackage(context.packageName)`，extras 包含 `alarmKey`、`ruleId`、`planId`、`triggerAtMillis`、`title`、`message`、`ringtoneUri`。

- [ ] **Step 3: 实现 `AppAlarmClockDispatcher`**

逻辑：

```kotlin
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
if (!alarmManager.canScheduleExactAlarmCompat()) {
    return AlarmDispatchResult(AlarmDispatchChannel.AppAlarmClock, false, "精确闹钟权限未开启")
}
val requestCode = plan.appAlarmRequestCode()
val operation = appAlarmOperationIntent(context, plan, requestCode)
val showIntent = PendingIntent.getActivity(...)
alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(plan.triggerAtMillis, showIntent), operation)
```

捕获 `SecurityException`、`RuntimeException`，失败不登记。

- [ ] **Step 4: 实现 `AppAlarmClockDismisser`**

根据 `SystemAlarmRecord.requestCode` 或 `alarmKey.hashCode()` 重建 operation `PendingIntent`，调用：

```kotlin
alarmManager.cancel(operation)
operation.cancel()
```

删除成功只代表 App 已向系统取消同一个 `PendingIntent`。

- [ ] **Step 5: 单元测试 dispatcher 路由可注入**

在 `AppAlarmClockRegistryTest.kt` 使用 fake dispatcher/dismisser，不直接依赖 Android `AlarmManager`。覆盖：

- 当前 backend 为 AppAlarmClock 时调用 App dispatcher。
- 当前 backend 为 SystemClockApp 时调用旧 system dispatcher。
- App dispatcher 返回失败时不写登记。
- 成功后登记包含 backend 和 requestCode。

- [ ] **Step 6: 运行提醒测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

Expected: PASS。

## Chunk 3: Coordinator backend 路由与检查窗口

- [ ] **Step 1: 修改 `ReminderCoordinator` 构造参数**

增加：

```kotlin
private val alarmSettingsProvider: suspend () -> ReminderAlarmSettings = { ReminderAlarmSettings() },
private val appDispatcher: AlarmDispatcher = AppAlarmClockDispatcher(context),
private val appDismisser: AlarmDismisser = AppAlarmClockDismisser(context),
private val systemDispatcher: AlarmDispatcher = SystemAlarmClockDispatcher(context),
private val systemDismisser: AlarmDismisser = SystemAlarmClockDismisser(context),
```

- [ ] **Step 2: 重命名同步入口**

将 `syncSystemClockAlarmsForWindow` 改成 `syncAlarmsForWindow`，保留旧方法作为一行委托以减少调用点一次性改动：

```kotlin
suspend fun syncSystemClockAlarmsForWindow(...) = syncAlarmsForWindow(...)
```

- [ ] **Step 3: 添加 backend 去重**

读取登记簿时按 `alarmKey + backend` 判断是否已添加。`plannedKeys` 也带 backend，避免两个通道互相误判。

- [ ] **Step 4: 按 backend 选择 dispatcher**

```kotlin
val settings = alarmSettingsProvider()
val dispatcher = when (settings.backend) {
    ReminderAlarmBackend.AppAlarmClock -> appDispatcher
    ReminderAlarmBackend.SystemClockApp -> systemDispatcher
}
```

成功登记时写入 `backend = settings.backend`，App 通道写入 `requestCode`。

- [ ] **Step 5: 按 backend 删除**

`dismissRecords` 根据 `record.backend` 选择 `AppAlarmClockDismisser` 或 `SystemAlarmClockDismisser`。

删除规则、清空课表、失效记录清理时删除所有 backend 下的记录。

- [ ] **Step 6: 下课检查删除过期 App 自管闹钟**

在 `syncAlarmsForWindow` 开头，如果 `reason == ReminderSyncReason.AfterClassToday`，先找出 `backend == AppAlarmClock && triggerAtMillis < nowMillis` 的记录，调用 App dismisser 并移除登记。

- [ ] **Step 7: 调整文案模型**

`SystemAlarmSyncSummary` 可保留名字，或新增 `AlarmSyncSummary`。如果重命名，逐步替换调用点；如果保留，UI 文案不要再固定说“系统闹钟”。

- [ ] **Step 8: 运行核心测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest :core-reminder:compileDebugKotlin'`

Expected: PASS。

## Chunk 4: Receiver、前台响铃服务与 Manifest

- [ ] **Step 1: 增加权限**

在 `app/src/main/AndroidManifest.xml` 增加：

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

保留现有 `SCHEDULE_EXACT_ALARM`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`。

- [ ] **Step 2: 注册 App 闹钟 Receiver**

Manifest 中注册：

```xml
<receiver
    android:name=".app.reminder.AppAlarmReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.kebiao.viewer.action.APP_ALARM_TRIGGER" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: 注册响铃服务**

```xml
<service
    android:name=".app.reminder.AlarmRingingService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

- [ ] **Step 4: 实现 `AppAlarmReceiver`**

用 `goAsync()` 包住轻量逻辑；调用 `ContextCompat.startForegroundService(...)` 启动 `AlarmRingingService`。异常写 `ReminderLogger.warn`。

- [ ] **Step 5: 实现通知渠道和停止动作**

`AlarmRingingService` 创建 notification channel：`course_alarm_ringing`。通知 category 用 `NotificationCompat.CATEGORY_ALARM`，priority high，ongoing true，添加“停止” action。

- [ ] **Step 6: 实现响铃循环**

服务读取 `ReminderAlarmSettings`：

- 播放 `ringDurationSeconds`。
- 间隔 `repeatIntervalSeconds`。
- 最多 `repeatCount` 次。
- 用户停止、服务销毁、播放异常时结束。

播放优先用 `RingtoneManager.getRingtone(context, uri)`；空 uri 用 `TYPE_ALARM` 默认铃声。

- [ ] **Step 7: 实现 WakeLock**

响铃每轮开始前获取短时 `PARTIAL_WAKE_LOCK`：

```kotlin
wakeLock.acquire((ringDurationSeconds + 10) * 1000L)
```

在 finally、停止动作、`onDestroy` 中检查 `isHeld` 后释放。

- [ ] **Step 8: 防崩溃审查**

确认以下调用都包了 `runCatching`：

- `startForegroundService`
- `startForeground`
- `Ringtone.play/stop`
- `Vibrator`
- `WakeLock.acquire/release`
- notification manager 调用

- [ ] **Step 9: 编译 app**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 5: 权限、省电设置与 UI

- [ ] **Step 1: 增加权限 Intent helper**

创建 `AlarmPermissionIntents.kt`：

- `exactAlarmSettingsIntent(context)`
- `batteryOptimizationIntent(context)`
- `appDetailsIntent(context)`

所有 Intent 启动失败时由 UI fallback 到应用详情页。

- [ ] **Step 2: 扩展 `AppPreferencesViewModel`**

增加：

```kotlin
fun setAlarmBackend(backend: ReminderAlarmBackend)
fun setAlarmRingDurationSeconds(seconds: Int)
fun setAlarmRepeatIntervalSeconds(seconds: Int)
fun setAlarmRepeatCount(count: Int)
```

设置 backend 后调用 `refreshScheduleOutputs()`，让当前通道补齐当天闹钟。

- [ ] **Step 3: 设置页传入新状态**

更新 `MainActivity.kt` 中 `AppSettingsRoute` 调用，传入：

- `alarmBackend`
- `alarmRingDurationSeconds`
- `alarmRepeatIntervalSeconds`
- `alarmRepeatCount`
- setter 回调

- [ ] **Step 4: 增加提醒可靠性设置区**

在 `SettingsScreen.kt` 中添加“提醒与闹钟”区：

- 通道选择：App 自管闹钟 / 系统时钟 App 闹钟。
- 精确闹钟权限状态和“去开启”按钮。
- 通知权限状态和“去授权”按钮。
- 电池优化状态和“允许后台运行”按钮。
- 响铃时长、间隔、次数行。

控件使用现有 `SettingsActionRow` 和 `SettingsSwitchRow` 风格，避免新增复杂设计系统。

- [ ] **Step 5: 权限状态检测**

在 Composable 中用 `LocalContext` 检测：

```kotlin
alarmManager.canScheduleExactAlarms()
NotificationManagerCompat.from(context).areNotificationsEnabled()
powerManager.isIgnoringBatteryOptimizations(context.packageName)
```

Android 版本低于对应权限要求时显示“已满足”。

- [ ] **Step 6: 编译设置页**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 6: App 与小组件共用 40 分钟轮询

- [ ] **Step 1: AppContainer 增加设置 provider**

`ReminderCoordinator` 注入：

```kotlin
alarmSettingsProvider = {
    userPreferencesRepository.preferencesFlow.first().toReminderAlarmSettings()
}
```

写一个 private mapper，把 `UserPreferences` 转为 `ReminderAlarmSettings`。

- [ ] **Step 2: 强制检查入口标记查询时间**

`runSystemAlarmCheck(reason)` 在真正执行检查前调用：

```kotlin
userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
```

这覆盖用户建规则、22:00、下课、课表变化等“上次查询”。

- [ ] **Step 3: 增加轮询入口**

新增：

```kotlin
suspend fun tryRunSharedAlarmPoll(reason: ReminderSyncReason = ReminderSyncReason.WidgetRefresh)
```

逻辑：

1. `tryClaimAlarmPoll(now, 40.minutes)`。
2. claim 失败直接返回空 summary。
3. claim 成功执行当天窗口同步。
4. 重排 22:00 与下课检查。

- [ ] **Step 4: 小组件使用同一 claim**

`WidgetSystemAlarmSynchronizer` 构造 `DataStoreUserPreferencesRepository`，调用 `tryClaimAlarmPoll`。claim 失败时只刷新 UI，不重复同步闹钟。

- [ ] **Step 5: WorkManager 保留 30 分钟周期但用 40 分钟节流**

`ScheduleWidgetRefreshWorker` 仍可 30 分钟刷新小组件，但闹钟同步必须走 `tryRunSharedAlarmPoll`。这样 App 本体刚查过，小组件不会再查。

- [ ] **Step 6: 测试轮询**

用 `UserPreferencesTest` 覆盖 App claim 后 widget claim 失败、超过 40 分钟后成功。

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'`

Expected: PASS。

## Chunk 7: 调用点文案与旧通道保留

- [ ] **Step 1: 替换调用点**

把 `syncSystemClockAlarmsForWindow` 调用逐步替换为 `syncAlarmsForWindow`：

- `AppContainer.kt`
- `ScheduleViewModel.kt`
- `WidgetSystemAlarmSynchronizer.kt`

- [ ] **Step 2: 更新状态文案**

`ScheduleViewModel.systemAlarmSyncMessage` 改为中性文案：

- “成功添加 X 个闹钟”
- “跳过 X 个已添加闹钟”
- “X 个闹钟设置失败”
- “已删除 X 个失效闹钟”

不要固定写“系统时钟”。

- [ ] **Step 3: 保留旧系统时钟 App 删除逻辑**

确认 `SystemAlarmClockDismisser` 未删除，且 `record.backend == SystemClockApp` 时仍按 label 走 `ACTION_DISMISS_ALARM`。

- [ ] **Step 4: 删除规则覆盖所有 backend**

`deleteRule(ruleId)` 必须查询该规则所有 records，并逐条按 backend 删除。

- [ ] **Step 5: 搜索确认旧通道仍存在**

Run: `pwsh -NoLogo -NoProfile -Command 'rg -n "ACTION_SET_ALARM|ACTION_DISMISS_ALARM|SystemAlarmClockDispatcher|SystemAlarmClockDismisser|setAlarmClock|AppAlarmClock" app core-reminder core-data feature-schedule feature-widget'`

Expected: 能看到两条通道均存在。

## Chunk 8: 全量验证

- [ ] **Step 1: 核心测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

Expected: PASS。

- [ ] **Step 2: 数据测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:testDebugUnitTest'`

Expected: PASS。

- [ ] **Step 3: 功能模块测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :feature-schedule:testDebugUnitTest :feature-widget:testDebugUnitTest'`

Expected: PASS。

- [ ] **Step 4: 编译验证**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:compileDebugKotlin :core-data:compileDebugKotlin :feature-schedule:compileDebugKotlin :feature-widget:compileDebugKotlin :app:compileDebugKotlin'`

Expected: PASS。

- [ ] **Step 5: Debug APK**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:assembleDebug'`

Expected: PASS。

- [ ] **Step 6: 静态搜索**

Run: `pwsh -NoLogo -NoProfile -Command 'rg -n "临时兜底|先这样跑起来|TODO|AppAlarm|SystemClockApp|lastAlarmPollAtMillis|REQUEST_IGNORE_BATTERY_OPTIMIZATIONS|FOREGROUND_SERVICE_MEDIA_PLAYBACK|WAKE_LOCK" app core-reminder core-data feature-schedule feature-widget'`

Expected: 没有禁止用语；关键实现点都能搜到。

- [ ] **Step 7: Git 状态**

Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`

Expected: 只包含本任务相关文件；不执行 `git add` 或 `git commit`。
