# App Alarm Lockscreen Fullscreen Volume Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 让 App 自管闹钟触发后自动亮屏并展示锁屏响铃页，且锁屏页中音量上关闭、音量下延后 5 分钟。

**Architecture:** 保留 `AlarmManager.setAlarmClock()` 到 `AlarmRingingService` 的触发链路，由前台响铃服务发布 alarm full-screen notification 来拉起 `AlarmRingingActivity`。Activity 只负责锁屏交互采集，所有关闭和延后动作仍回到 service action 与 `ReminderCoordinator.finishTriggeredAppAlarm(...)`。

**Tech Stack:** Kotlin、Android AlarmManager、Foreground Service、Notification full-screen intent、ActivityOptions、Window showWhenLocked/turnScreenOn、KeyEvent、Jetpack Compose 设置页、JUnit4、pwsh。

---

## File Structure

- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRingingService.kt`
  - 为 full-screen Activity `PendingIntent` 附加后台 Activity 启动授权选项，确保 targetSdk 36 下响铃页能被系统拉起。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmRingingActivity.kt`
  - 改为 `dispatchKeyEvent` 处理音量键；优化锁屏 window 配置；移除主动解锁请求；调整锁屏页关闭/延后布局。
- Modify: `app/src/main/java/com/kebiao/viewer/app/reminder/AlarmPermissionIntents.kt`
  - 增加 Android 14+ full-screen intent 权限设置 Intent。
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
  - 在“提醒与闹钟”区展示 full-screen intent 权限状态和跳转入口。
- Modify: `app/src/main/res/values/themes.xml`
  - 增加响铃 Activity 专用无 ActionBar、全屏、锁屏显示主题。
- Modify: `app/src/main/AndroidManifest.xml`
  - 让 `AlarmRingingActivity` 使用专用响铃主题。

## Chunk 1: Full-screen PendingIntent

- [ ] **Step 1: 修改 full-screen PendingIntent 创建**

在 `AlarmRingingService.startForegroundCompat(...)` 中，把 `PendingIntent.getActivity(...)` 改成传入 `ActivityOptions` bundle 的重载。

- [ ] **Step 2: 增加 ActivityOptions helper**

新增私有方法：

```kotlin
private fun alarmActivityOptions(): Bundle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    return ActivityOptions.makeBasic().apply {
        setPendingIntentCreatorBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
        )
    }.toBundle()
}
```

- [ ] **Step 3: 编译检查**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 2: 锁屏 Activity 与音量键

- [ ] **Step 1: 调整 window 配置**

`AlarmRingingActivity.configureWindow()` 保留 `setShowWhenLocked(true)`、`setTurnScreenOn(true)` 和 `FLAG_KEEP_SCREEN_ON`，移除 `KeyguardManager.requestDismissKeyguard(...)`。

- [ ] **Step 2: 改用 `dispatchKeyEvent`**

捕获 `KEYCODE_VOLUME_UP` 和 `KEYCODE_VOLUME_DOWN`。`ACTION_DOWN && repeatCount == 0` 发送动作，`ACTION_UP` 也消费，避免系统音量变化和长按重复触发。

- [ ] **Step 3: 调整锁屏页布局**

把按钮顺序调整为左侧/上方延后，右侧/下方关闭；文案保留 `左滑延后 · 右滑关闭`。按钮、滑动和音量键共用 `sendServiceAction(...)`。

- [ ] **Step 4: 编译检查**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 3: Full-screen 权限入口

- [ ] **Step 1: 增加设置 Intent**

在 `AlarmPermissionIntents` 新增 `fullScreenIntentSettingsIntent(context)`，Android 14+ 使用 `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 和 `package:` data。

- [ ] **Step 2: 设置页增加权限状态**

`AlarmReliabilitySection` 中新增：

- Android 14 以下显示已满足。
- Android 14+ 调用 `NotificationManager.canUseFullScreenIntent()`。
- 未开启时点击跳转 full-screen intent 设置页，失败则进入应用详情。

- [ ] **Step 3: 编译检查**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

## Chunk 4: 全量验证

- [ ] **Step 1: App 编译**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :app:compileDebugKotlin'`

Expected: PASS。

- [ ] **Step 2: 提醒核心回归**

Run: `pwsh -NoLogo -NoProfile -Command './gradlew :core-reminder:testDebugUnitTest'`

Expected: PASS。

- [ ] **Step 3: 静态搜索**

Run: `pwsh -NoLogo -NoProfile -Command 'rg -n "setFullScreenIntent|setPendingIntentCreatorBackgroundActivityStartMode|dispatchKeyEvent|ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT|requestDismissKeyguard|KEYCODE_VOLUME" app/src/main/java app/src/main/res'`

Expected: 能看到 full-screen、音量键和权限入口；不再看到 `requestDismissKeyguard`。

- [ ] **Step 4: Git 状态**

Run: `pwsh -NoLogo -NoProfile -Command 'git status --short'`

Expected: 只包含本任务相关文件；不执行 `git add` 或 `git commit`。
