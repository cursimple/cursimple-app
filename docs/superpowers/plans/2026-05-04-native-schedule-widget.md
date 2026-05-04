# 原生今日课程小组件实施计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将“今日课程”小组件改为原生 `AppWidgetProvider + RemoteViews`，让切换直接在小组件 receiver 中完成并立即更新对应实例。

**Architecture:** 保留小组件专用快照作为数据源；今日课程小组件不再使用 Glance 渲染。Receiver 收到更新或点击广播后，按 `appWidgetId` 读取/更新 offset，直接生成 RemoteViews 并调用 `AppWidgetManager.updateAppWidget`。下一节课和课程提醒继续使用现有 Glance 实现。

**Tech Stack:** Kotlin、Android AppWidgetProvider、RemoteViews、DataStore、Gradle。

---

## Chunk 1: RemoteViews UI

### Task 1: 新增今日课程小组件布局

**Files:**
- Create: `feature-widget/src/main/res/layout/widget_schedule_today.xml`
- Create: `feature-widget/src/main/res/layout/widget_schedule_course_row.xml`

- [x] **Step 1: 创建容器布局**

包含上一天按钮、标题/日期区域、下一天按钮、课程列表容器和空态文本。

- [x] **Step 2: 创建课程行布局**

包含节次、时间、标题、地点/教师和提醒标记。

## Chunk 2: 原生 Receiver

### Task 2: 替换今日课程 Glance receiver

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetActionReceiver.kt`

- [x] **Step 1: 将 receiver 改为 AppWidgetProvider**

处理 `onUpdate`、`onReceive`、`onDeleted`，保留 vendor twin 子类。

- [x] **Step 2: 在 receiver 中直接渲染 RemoteViews**

读取 `WidgetScheduleSnapshot` 和按实例 offset，计算目标日期课程并生成 RemoteViews。

- [x] **Step 3: 点击直接更新对应实例**

左右/回今天 PendingIntent 带 `appWidgetId` 和 action，收到后只更新该实例。

## Chunk 3: 状态和刷新

### Task 3: 支持按实例 offset 和直更

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/WidgetPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`

- [x] **Step 1: 为每个 appWidgetId 保存 offset**

保留旧全局 offset 兼容，但今日课程点击优先使用实例 offset。

- [x] **Step 2: updater 调用原生 receiver 更新今日课程**

刷新全部小组件时今日课程走原生 receiver，下一节课和提醒仍走 Glance。

## Chunk 4: 验证

### Task 4: 构建验证

**Files:**
- None

- [x] **Step 1: 运行验证**

Run: `./gradlew :core-data:testDebugUnitTest :app:testDebugUnitTest :feature-widget:assembleDebug :app:assembleDebug`
Expected: 测试和编译通过，今日课程小组件不再依赖 `ScheduleGlanceWidget` 渲染。
