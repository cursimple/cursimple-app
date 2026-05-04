# 桌面课表小组件独立快照实施计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让课表桌面小组件读取自己的课程快照并独立切换，避免每次点击都重走 app 的课表仓储链路。

**Architecture:** 在 `core-data.widget` 中新增小组件专用快照仓储，保存当前活动学期的课表、手动课程、提醒规则、时间表、开学日期、时区和调试时间。app 在课表、手动课程、提醒、学期或偏好变化后同步这份快照；课表小组件只读快照和自身 offset，点击只修改小组件状态并刷新课表小组件。

**Tech Stack:** Kotlin、AndroidX Glance、DataStore、kotlinx.serialization、Gradle。

---

## Chunk 1: 快照数据源

### Task 1: 新增小组件快照仓储

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/WidgetPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/DataStoreWidgetPreferencesRepository.kt`

- [x] **Step 1: 定义 `WidgetScheduleSnapshot`**

包含 `schedule`、`manualCourses`、`reminderRules`、`timingProfile`、`termStartDateIso`、`timeZoneId`、`debugForcedDateTimeIso`。

- [x] **Step 2: 在仓储中增加快照 flow 和保存方法**

用独立 JSON key 保存快照，保留现有 offset 和 timing profile 兼容字段。

## Chunk 2: app 同步快照

### Task 2: AppContainer 写入最新快照

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`

- [x] **Step 1: 汇总当前活动学期数据**

从 schedule、manual courses、reminder rules、用户偏好和 timing profile 组装 `WidgetScheduleSnapshot`。

- [x] **Step 2: refreshWidgets 先保存快照再刷新**

确保同步完成、切换学期、改偏好时小组件拿到最新快照。

### Task 3: UI 数据变化后刷新快照

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [x] **Step 1: 手动课程变化后刷新小组件快照**

添加/清理手动课程后调用 `container.refreshWidgets()`。

- [x] **Step 2: 开学日期和当前周变化后刷新快照**

写入活动学期后继续刷新小组件。

## Chunk 3: 小组件读取快照

### Task 4: 课表小组件只读快照

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionReceiver.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`

- [x] **Step 1: 替换课表小组件数据读取**

从 `snapshotFlow` 获取渲染数据，不再在课表小组件里创建 `DataStoreScheduleRepository`、`DataStoreUserPreferencesRepository`、`DataStoreReminderRepository` 或 `DataStoreTermProfileRepository`。

- [x] **Step 2: 切换只更新小组件状态**

receiver 只改 `widgetDayOffset` 并刷新课表小组件；可获得单实例 id 时优先刷新单实例。

- [x] **Step 3: 保留无快照空态**

快照不存在时显示空课表，等待 app 下次刷新写入。

## Chunk 4: 验证

### Task 5: 测试和编译

**Files:**
- None

- [x] **Step 1: 运行验证**

Run: `./gradlew :core-data:testDebugUnitTest :app:testDebugUnitTest :feature-widget:assembleDebug :app:assembleDebug`
Expected: 测试和编译通过，小组件课表渲染不再依赖 app 课表仓储。
