# 插件同步、小组件与开学日期统一实施计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复插件同步上传页自适应、同步进度反馈、同步后跳转、小组件数据读取，以及 `termStartDate` 多来源不一致问题。

**Architecture:** 当前学期的开学日期作为唯一权威来源；插件 timing profile 在保存和传给提醒/小组件前归一化到当前学期开学日期。同步 UI 只反映真实阶段，不引入临时回退链路。小组件直接读取当前学期仓储。

**Tech Stack:** Kotlin、Jetpack Compose、AndroidX Glance、DataStore、Gradle。

---

## Chunk 1: 同步体验

### Task 1: 插件 Web 会话工具栏和上传进度

**Files:**
- Modify: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreen.kt`

- [ ] 将顶部四个按钮改为可换行且在窄屏下等宽排列。
- [ ] 增加上传/写入阶段的线性进度动画和阶段文本。
- [ ] 上传期间禁用会造成状态跳变的按钮。

### Task 2: 同步成功自动回课表页

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [ ] 监听同步状态从 syncing 变为完成且已有课表。
- [ ] 自动切换到 `AppScreen.Schedule`，并重置周/日偏移到当前视图。

## Chunk 2: 数据一致性

### Task 3: 小组件读取当前学期数据

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/NextCourseGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ReminderGlanceWidget.kt`

- [ ] 用 `DataStoreTermProfileRepository` 初始化 `DataStoreScheduleRepository`。
- [ ] 保持三个小组件一致读取当前 active term 的 schedule。

### Task 4: 统一 `termStartDate`

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/util/ScheduleMetadataExporter.kt`

- [ ] 将同步完成后的 timing profile 归一化到当前学期开学日期。
- [ ] 当前学期没有开学日期时，用插件 timing profile 初始化当前学期开学日期。
- [ ] 元数据导出只在顶层展示权威 `termStartDate`，timing profile 中保留课节时间与时区。

## Chunk 3: 验证

- [ ] 运行相关单元测试。
- [ ] 至少执行 `:app:assembleDebug` 或等价编译验证。
