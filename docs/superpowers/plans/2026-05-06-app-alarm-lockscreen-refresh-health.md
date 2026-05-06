# App Alarm Lockscreen Refresh Health Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 补齐 App 自管闹钟的锁屏交互、关闭/延后后的 App 与小组件刷新、响铃时后续闹钟维护和保活设施体检。

**Architecture:** 保留现有 `AlarmManager.setAlarmClock()`、`AlarmRingingService`、`AlarmRingingActivity` 和 `ReminderCoordinator` 主线。把响铃生命周期拆成“响铃开始维护”和“用户结束响铃后的最终状态刷新”；延后闹钟进入正式登记模型，提醒小组件合并规则展开结果和延后登记。

**Tech Stack:** Kotlin、Android AlarmManager、Foreground Service、Notification、Activity lock screen flags、KeyEvent、View touch handling、DataStore、Glance、AppWidgetProvider、WorkManager、JUnit4、pwsh。

---

## File Structure

- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderModels.kt`
  - 增加延后登记 operation mode 和 UI 展示字段。
- Modify: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderCoordinator.kt`
  - 新增统一的触发闹钟结束处理，支持关闭和延后。
- Modify: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/SystemAlarmRegistryTest.kt`
  - 覆盖关闭、延后、失败不登记、复用规则保留。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRingingService.kt`
  - 拆分响铃开始维护和用户结束后的刷新；延后成功后登记新闹钟。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRingingActivity.kt`
  - 增加左右滑动和音量键交互。
- Create: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRuntimeMaintenance.kt`
  - 统一响铃时闹钟维护、保活设施检查、小组件刷新。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmPermissionIntents.kt`
  - 如当前文件未包含全屏 intent、电池优化等检查入口，在这里补齐。
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`
  - 暴露给运行时维护复用的检查/刷新方法，避免 service 直接重复仓储拼装。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetAlarmSyncHooks.kt`
  - 改为统一小组件生命周期刷新入口。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
  - 新增/删除/尺寸变化时调用统一刷新入口。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/NextCourseGlanceWidget.kt`
  - 删除和更新时调用统一刷新入口。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ReminderGlanceWidget.kt`
  - 删除和更新时调用统一刷新入口；渲染时合并延后登记。
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetLifecycleRefresher.kt`
  - 统一通知安装状态、安排 worker、刷新所有小组件、触发提醒 reconcile。
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetPinResultReceiver.kt`
  - 处理 `requestPinAppWidget` 成功回调。
- Modify: `feature-widget/src/main/AndroidManifest.xml`
  - 注册 pin 成功回调 receiver。
- Test: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/ReminderWidgetRecordsTest.kt`
  - 覆盖提醒小组件包含延后登记。

## Chunk 1: 延后登记进入正式模型

- [ ] **Step 1: 扩展 App 闹钟 operation mode**

在 `ReminderModels.kt` 中把 `AppAlarmOperationMode` 扩展为：

```kotlin
@Serializable
enum class AppAlarmOperationMode {
    @SerialName("legacy_broadcast")
    LegacyBroadcast,

    @SerialName("foreground_service")
    ForegroundService,

    @SerialName("snooze_foreground_service")
    SnoozeForegroundService,
}
```

- [ ] **Step 2: 给登记补展示字段**

在 `SystemAlarmRecord` 增加默认字段：

```kotlin
@SerialName("displayTitle") val displayTitle: String? = null,
@SerialName("displayMessage") val displayMessage: String? = null,
```

给 `ReminderPlan` 增加创建 App 自管记录的 helper：

```kotlin
fun ReminderPlan.toAppAlarmRecord(
    backend: ReminderAlarmBackend,
    operationMode: AppAlarmOperationMode,
): SystemAlarmRecord {
    val label = systemAlarmLabel()
    return SystemAlarmRecord(
        alarmKey = systemAlarmKey(),
        ruleId = ruleId,
        pluginId = pluginId,
        planId = planId,
        courseId = courseId,
        triggerAtMillis = triggerAtMillis,
        message = label,
        alarmLabel = label,
        backend = backend,
        requestCode = appAlarmRequestCode(),
        operationMode = operationMode,
        displayTitle = title,
        displayMessage = message,
        createdAtMillis = System.currentTimeMillis(),
    )
}
```

- [ ] **Step 3: 更新普通同步写登记**

在 `ReminderCoordinator.syncAlarmsForWindow(...)` 保存 `SystemAlarmRecord` 的地方写入：

```kotlin
displayTitle = plan.title,
displayMessage = plan.message,
```

保持 `message` 与 `alarmLabel` 兼容旧系统时钟删除逻辑。

- [ ] **Step 4: 运行模型测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

Expected: PASS。

## Chunk 2: Coordinator 统一处理关闭与延后

- [ ] **Step 1: 增加结束动作模型**

在 `ReminderCoordinator.kt` 中增加内部或公开模型：

```kotlin
sealed interface TriggeredAppAlarmFinishAction {
    data object Dismiss : TriggeredAppAlarmFinishAction
    data class Snooze(val plan: ReminderPlan) : TriggeredAppAlarmFinishAction
}

data class TriggeredAppAlarmFinishResult(
    val consumed: Boolean,
    val snoozeCreated: Boolean = false,
    val message: String = "",
)
```

- [ ] **Step 2: 新增 `finishTriggeredAppAlarm`**

实现：

```kotlin
suspend fun finishTriggeredAppAlarm(
    alarmKey: String,
    ruleId: String,
    action: TriggeredAppAlarmFinishAction,
): TriggeredAppAlarmFinishResult = SYSTEM_ALARM_LOCK.withLock {
    val rule = repository.getReminderRules().firstOrNull { it.ruleId == ruleId }
    repository.removeSystemAlarmRecord(alarmKey, ReminderAlarmBackend.AppAlarmClock)

    if (rule != null && rule.shouldDeleteAfterAppAlarmRing()) {
        val nowMillis = System.currentTimeMillis()
        val records = repository.getSystemAlarmRecords().filter { it.ruleId == ruleId }
        dismissRecords(records.filter { it.triggerAtMillis > nowMillis })
        repository.removeReminderRule(ruleId)
        repository.removeSystemAlarmRecordsForRule(ruleId)
    }

    when (action) {
        TriggeredAppAlarmFinishAction.Dismiss -> TriggeredAppAlarmFinishResult(consumed = true)
        is TriggeredAppAlarmFinishAction.Snooze -> dispatchAndRecordSnooze(action.plan)
    }
}
```

注意：`dispatchAndRecordSnooze` 不能重新进入同一个 `SYSTEM_ALARM_LOCK`。

- [ ] **Step 3: 实现 `dispatchAndRecordSnooze`**

逻辑：

```kotlin
val result = appDispatcher.dispatch(plan)
if (result.succeeded) {
    repository.saveSystemAlarmRecord(
        plan.toAppAlarmRecord(
            backend = ReminderAlarmBackend.AppAlarmClock,
            operationMode = AppAlarmOperationMode.SnoozeForegroundService,
        ),
    )
}
```

失败时返回 `snoozeCreated = false`，不写登记。

- [ ] **Step 4: 保留兼容旧调用**

保留现有 `consumeTriggeredAppAlarm(...)`，让它委托到：

```kotlin
finishTriggeredAppAlarm(alarmKey, ruleId, TriggeredAppAlarmFinishAction.Dismiss)
```

- [ ] **Step 5: 写失败优先测试**

在 `SystemAlarmRegistryTest.kt` 增加测试：

- 关闭当前 App 自管闹钟会删除当前登记。
- 单课一次性规则关闭后删除规则。
- 首次课规则关闭后保留规则。
- 延后 5 分钟会保存 `SnoozeForegroundService` 登记。
- 延后 dispatcher 失败时不保存登记。

- [ ] **Step 6: 运行测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

Expected: PASS。

## Chunk 3: 响铃服务开始维护与结束刷新

- [ ] **Step 1: 创建 `AlarmRuntimeMaintenance`**

创建 `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRuntimeMaintenance.kt`：

```kotlin
object AlarmRuntimeMaintenance {
    suspend fun onAlarmStarted(context: Context) {
        val app = context.applicationContext as? ClassScheduleApplication ?: return
        app.appContainer.ensureAlarmRuntimeHealth()
        app.appContainer.runAlarmFollowUpSync()
    }

    suspend fun onAlarmFinished(context: Context) {
        val app = context.applicationContext as? ClassScheduleApplication ?: return
        app.appContainer.refreshWidgets()
        app.appContainer.runAlarmFollowUpSync()
    }
}
```

`runAlarmFollowUpSync()` 负责今天剩余窗口；若当前本地时间不早于 12:00，再跑下一天窗口。

- [ ] **Step 2: 在 `AppContainer` 增加健康检查入口**

增加：

```kotlin
suspend fun ensureAlarmRuntimeHealth()
suspend fun runAlarmFollowUpSync(nowMillis: Long = System.currentTimeMillis())
```

`ensureAlarmRuntimeHealth()`：

- 调用 `scheduleSystemAlarmChecks()`。
- 调用 `ScheduleWidgetWorkScheduler.schedule(app)`。
- 检查并记录精确闹钟、通知、全屏 intent、channel、电池优化状态。

不可静默修复的项只记录日志。

- [ ] **Step 3: 替换 `AlarmRingingService.consumeTriggeredAlarm`**

把原先 `consumeTriggeredAlarm(alarm)` 与 `scheduleSnooze(alarm)` 分离逻辑改成：

```kotlin
val action = if (snooze) {
    TriggeredAppAlarmFinishAction.Snooze(alarm.toSnoozePlan())
} else {
    TriggeredAppAlarmFinishAction.Dismiss
}
ReminderCoordinator(...).finishTriggeredAppAlarm(
    alarmKey = alarm.alarmKey,
    ruleId = alarm.ruleId,
    action = action,
)
```

删除服务里直接调用 `AppAlarmClockDispatcher.dispatch(plan)` 的旧延后路径。

- [ ] **Step 4: 响铃开始时执行维护**

在 `startRinging(intent)` 成功 `startForegroundCompat(alarm)` 后启动：

```kotlin
serviceScope.launch(Dispatchers.IO) {
    AlarmRuntimeMaintenance.onAlarmStarted(applicationContext)
}
```

这个协程失败只记录日志，不停止当前响铃。

- [ ] **Step 5: 用户结束后刷新**

在 `finishRinging(...)` 完成 `finishTriggeredAppAlarm(...)` 后调用：

```kotlin
AlarmRuntimeMaintenance.onAlarmFinished(applicationContext)
```

这一步必须在关闭和延后两条路径都执行。

- [ ] **Step 6: 运行 app 编译**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 4: 锁屏左右滑动与音量键

- [ ] **Step 1: 给 Activity 增加单次动作保护**

在 `AlarmRingingActivity` 增加：

```kotlin
private var actionSent = false
```

`sendServiceAction` 开头：

```kotlin
if (actionSent) return
actionSent = true
```

- [ ] **Step 2: 增加滑动识别**

在 root view 设置 touch listener。阈值使用 `96.dp()`：

```kotlin
var downX = 0f
root.setOnTouchListener { _, event ->
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            downX = event.x
            true
        }
        MotionEvent.ACTION_UP -> {
            val delta = event.x - downX
            when {
                delta >= SWIPE_THRESHOLD_PX -> sendServiceAction(AlarmRingingService.ACTION_STOP)
                delta <= -SWIPE_THRESHOLD_PX -> sendServiceAction(AlarmRingingService.ACTION_SNOOZE)
            }
            true
        }
        else -> true
    }
}
```

右滑关闭，左滑延后 5 分钟。

- [ ] **Step 3: 增加音量键处理**

重写：

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
            sendServiceAction(AlarmRingingService.ACTION_STOP)
            true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            sendServiceAction(AlarmRingingService.ACTION_SNOOZE)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
```

- [ ] **Step 4: 更新锁屏文案**

在 `render()` 中补一行小字号提示：

```text
右滑关闭 · 左滑延后
```

不要把文案写成使用说明页，只作为当前响铃交互提示。

- [ ] **Step 5: 运行 app 编译**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 5: 小组件新增删除与提醒状态刷新

- [ ] **Step 1: 创建 `WidgetLifecycleRefresher`**

创建 `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetLifecycleRefresher.kt`：

```kotlin
internal object WidgetLifecycleRefresher {
    fun onWidgetSetChanged(context: Context, reason: String) {
        val appContext = context.applicationContext
        WidgetCatalog.notifyInstalledChanged(appContext)
        ScheduleWidgetWorkScheduler.schedule(appContext)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            ScheduleWidgetUpdater.refreshAll(appContext)
            WidgetSystemAlarmSynchronizer.reconcileToday(appContext)
        }
    }
}
```

失败路径写 `ReminderLogger.warn(...)`。

- [ ] **Step 2: 改造现有 widget hook**

把 `reconcileSystemAlarmsFromWidget(context)` 改为委托：

```kotlin
WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "widget_update")
```

避免各 receiver 自己拼装 worker/reconcile。

- [ ] **Step 3: 删除时也刷新所有状态**

三类 receiver 的 `onDeleted` 保留自身清理逻辑，然后调用：

```kotlin
WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "widget_deleted")
```

`ScheduleGlanceWidgetReceiver` 先清理 per-widget day offset，再刷新。

- [ ] **Step 4: 处理 pin 成功回调**

创建 `WidgetPinResultReceiver`：

```kotlin
class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetCatalog.ACTION_WIDGET_PINNED) {
            WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "widget_pinned")
        }
    }
}
```

在 `feature-widget/src/main/AndroidManifest.xml` 注册非导出 receiver，并让 `WidgetCatalog.requestPin(...)` 的 callback 指向它。

- [ ] **Step 5: 让提醒小组件读取延后登记**

在 `ReminderGlanceWidget.provideGlance(...)` 读取：

```kotlin
val records = reminderRepository.systemAlarmRecordsFlow.first()
```

把未来的延后登记转为可展示条目：

```kotlin
records
    .filter { it.backend == ReminderAlarmBackend.AppAlarmClock }
    .filter { it.operationMode == AppAlarmOperationMode.SnoozeForegroundService }
    .filter { it.triggerAtMillis >= now }
```

展示标题使用 `displayTitle ?: alarmLabel ?: message`，副标题使用 `displayMessage ?: "已延后 5 分钟"`。

- [ ] **Step 6: 提取提醒条目数据结构**

把 `ReminderGlanceWidget` 中直接渲染 `ReminderPlan` 的逻辑改成渲染：

```kotlin
private data class ReminderWidgetEntry(
    val id: String,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
)
```

规则计划和延后登记都转成 `ReminderWidgetEntry`，按触发时间排序并去重。

- [ ] **Step 7: 写 widget 单元测试**

新增 `ReminderWidgetRecordsTest.kt`，测试纯函数：

```kotlin
@Test
fun snoozedAppAlarmRecordIsIncludedInReminderEntries()
```

如果现有 widget 文件没有可测试纯函数，先提取 `buildReminderWidgetEntries(plans, records, nowMillis)` 到同文件 internal 函数。

- [ ] **Step 8: 运行 widget 测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:testDebugUnitTest'`

Expected: PASS。

## Chunk 6: 全量验证

- [ ] **Step 1: 运行核心测试**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest :feature-widget:testDebugUnitTest'`

Expected: PASS。

- [ ] **Step 2: 运行 app 编译**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

- [ ] **Step 3: 检查工作树**

Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`

Expected: 只包含本功能相关文件。

- [ ] **Step 4: 提交门禁**

不执行 git commit。只有用户明确要求提交时，再按仓库要求使用如下格式：

```text
feat: 完善自管闹钟锁屏交互与状态刷新
- 支持锁屏滑动和音量键关闭/延后
- 延后闹钟写入正式登记并刷新 App 与小组件
- 响铃时检查后续闹钟和保活设施
```
