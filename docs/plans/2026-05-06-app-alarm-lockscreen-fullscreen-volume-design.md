# App 自管闹钟全屏锁屏与音量键设计

日期：2026-05-06
状态：已确认，待实现

## 背景

当前 App 自管闹钟已经使用 `AlarmManager.setAlarmClock()` 触发 `AlarmRingingService`，服务会播放铃声、震动、展示高优先级通知，并提供关闭和延后 5 分钟动作。锁屏页 `AlarmRingingActivity` 也已经支持按钮、左右滑动和音量键，但它只有在 Activity 已经显示时才能收到音量键。

用户确认的目标是：闹钟触发后自动亮屏并展示锁屏响铃页，此时音量上关闭、音量下延后 5 分钟可用。目标不是在没有响铃界面时全局监听音量键。

## 参考结论

- Fossify Clock 与 BlackyHawky Clock 都采用可见闹钟调度、响铃通知 full-screen intent、锁屏 Activity 和统一关闭/延后动作。
- Chrono 的 Flutter 实现同样使用 full-screen alarm notification，并在全屏页内把关闭/延后动作回传给响铃控制层。
- Android 官方文档要求后台启动 Activity 走系统允许的场景；闹钟类交互应使用 full-screen notification，并在较新系统上显式处理 full-screen intent 权限和后台 Activity 启动授权。

## 目标

- 闹钟触发后自动点亮屏幕并显示 `AlarmRingingActivity`。
- `AlarmRingingActivity` 显示在锁屏上，不主动要求用户解锁。
- 音量上关闭当前闹钟。
- 音量下延后 5 分钟。
- 锁屏页按钮、滑动、音量键和通知 action 都进入 `AlarmRingingService.ACTION_STOP` 或 `ACTION_SNOOZE`。
- 任意操作只处理一次，避免重复关闭、重复延后或重复写登记。
- 设置页展示 Android 14+ full-screen intent 权限状态，并提供跳转入口。

## 非目标

- 不实现后台全局音量键监听。
- 不引入辅助功能服务、无障碍权限、媒体会话抢占或厂商私有后台启动方案。
- 不把无法静默授权的权限包装成隐式回退路径；精确闹钟、通知、full-screen intent 和省电优化都只做正式检查和用户引导。

## 方案选择

推荐方案：补强 full-screen 锁屏响铃链路。

- 保持 `AlarmRingingService` 为响铃生命周期中心。
- `AlarmRingingService` 创建响铃通知时，full-screen `PendingIntent` 指向 `AlarmRingingActivity`。
- 创建 Activity `PendingIntent` 时，在支持的系统版本上附加后台 Activity 启动授权选项，适配 targetSdk 36。
- Activity 使用 `setShowWhenLocked(true)`、`setTurnScreenOn(true)` 和 keep-screen-on 标志显示在锁屏上。
- Activity 内通过 `dispatchKeyEvent` 捕获音量键，避免只覆盖部分 key down 路径。

不采用直接把 `setAlarmClock()` 的 operation 改成 Activity。那会让 UI 和响铃服务耦合，且扩大调度、取消和测试改动面。

## 交互

锁屏页保持一屏完成：

- 中部显示课程标题、提醒文案。
- 底部显示两个明确操作：`延后 5 分钟`、`关闭`。
- 左滑执行延后 5 分钟。
- 右滑执行关闭。
- 音量上执行关闭。
- 音量下执行延后 5 分钟。

Activity 不主动调用 `requestDismissKeyguard()`。它只覆盖锁屏显示并点亮屏幕，符合闹钟响铃场景，也避免误导用户以为 App 会绕过锁屏安全。

## 数据流

1. `AlarmManager.setAlarmClock()` 到点触发 `AlarmRingingService.ACTION_RING`。
2. `AlarmRingingService` 立即 `startForeground()` 并发布 alarm category 通知。
3. 通知的 full-screen intent 拉起 `AlarmRingingActivity`。
4. Activity 的按钮、滑动或音量键调用 `sendServiceAction(...)`。
5. `AlarmRingingService` 收到 `ACTION_STOP` 或 `ACTION_SNOOZE`。
6. Service 停止铃声、震动和 WakeLock。
7. Service 调用 `ReminderCoordinator.finishTriggeredAppAlarm(...)`：
   - 关闭：删除当前触发登记，必要时删除一次性规则。
   - 延后：删除当前触发登记，创建并登记 5 分钟后的 App 自管闹钟。
8. Service 执行响铃结束维护，刷新 App 可观察状态和小组件。

## 权限与兼容

- `USE_FULL_SCREEN_INTENT` 已在 manifest 声明。
- Android 14+ 使用 `NotificationManager.canUseFullScreenIntent()` 检查 full-screen intent 能力。
- 设置页增加“全屏响铃权限”行；未开启时跳转 `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`，失败时进入应用详情。
- Android 15+ 创建 full-screen Activity `PendingIntent` 时附加 creator background activity start mode。
- Android 旧版本继续使用已有 `showWhenLocked` / `turnScreenOn` window 标志。

## 错误处理

- full-screen 权限未开启时不伪造替代架构；响铃通知和铃声仍正常工作，并在设置页/运行日志提示权限状态。
- Activity 重复 intent、重复按键、长按音量键只会触发一次 service action。
- 设置页启动系统设置失败时进入应用详情页。
- Service 的通知、铃声、震动、WakeLock 和后续维护继续用 `runCatching` 记录结构化日志。

## 验证

- 静态检查：full-screen `PendingIntent` 指向 `AlarmRingingActivity`，且 Activity extras 完整。
- 静态检查：`dispatchKeyEvent` 捕获音量上/下，`ACTION_DOWN` 首次触发，`ACTION_UP` 被消费。
- 编译验证：`:app:compileDebugKotlin`。
- 回归验证：`:core-reminder:testDebugUnitTest`，确保关闭/延后登记逻辑未被破坏。
- 手测建议：锁屏后触发一个 1 分钟内的 App 自管闹钟，确认屏幕点亮、锁屏页出现、音量上关闭、音量下延后 5 分钟。
