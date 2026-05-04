# 桌面小组件内切换实施计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让课表桌面小组件的上一天、下一天、回今天切换完全在小组件内部完成，不再通过 app 组件中转。

**Architecture:** 用显式小组件广播直接修改小组件偏移并触发刷新，保留现有 DataStore 偏好作为单一状态源。删除仅用于点击转发的 Activity，避免点击链路进入应用界面层；当前周设定统一写入活动学期档案和用户偏好镜像。

**Tech Stack:** Kotlin、AndroidX Glance、DataStore、Gradle。

---

## Chunk 1: 小组件内切换

### Task 1: 用显式广播替换点击 Activity

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidget.kt`
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionReceiver.kt`
- Delete: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionCallback.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionActivity.kt`
- Modify: `feature-widget/src/main/AndroidManifest.xml`

- [x] **Step 1: 设计广播参数**

为上一天、下一天、回今天定义广播 extra，直接映射到小组件偏移操作。

- [x] **Step 2: 改写点击处理**

把 `DayHeader` 里的左右按钮和“回今天”改为 `actionSendBroadcast`，不再启动 Activity 或 Glance action trampoline。

- [x] **Step 3: 实现广播逻辑**

在 receiver 里异步更新 `widgetDayOffset` 并刷新课表小组件。

- [x] **Step 4: 清理旧入口**

移除不再需要的 action Activity 和 callback 文件，注册非导出的 action receiver。

### Task 2: 当前周设定持久化到活动学期

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [x] **Step 1: 修复周次菜单写入路径**

把 `WeekPickerSheet` 的 `onSetSelectedAsCurrent` 从直接写用户偏好改为调用 `setActiveTermStartDate`。

- [x] **Step 2: 保持视图复位**

写入后保持 `weekOffset` 和 `dayOffset` 归零，避免显示周次和持久化周次不同步。

## Chunk 2: 验证

### Task 3: 构建检查

**Files:**
- None

- [x] **Step 1: 运行测试和编译验证**

Run: `./gradlew :app:testDebugUnitTest :feature-widget:assembleDebug :app:assembleDebug`
Expected: 测试和编译通过，且点击链路不再依赖 `ScheduleWidgetActionActivity` 或 `ScheduleWidgetActionCallback`。
