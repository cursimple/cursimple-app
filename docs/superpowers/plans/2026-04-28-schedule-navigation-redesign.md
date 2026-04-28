# 课表导航与界面重设计 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将应用入口重构为三项底栏，并把课表主页改造成参考图风格的周视图界面，同时补齐设置页与插件页视觉统一。  
**Architecture:** 保持现有 ViewModel、插件工作流与仓储不变，只在 `app` 和 `feature-*` 的 Compose UI 层重构。入口壳层负责底栏与页面切换，课表页负责周视图布局与交互收纳。  
**Tech Stack:** Kotlin、Jetpack Compose、Material 3、ViewModel、DataStore、QuickJS

---

### Task 1: 入口壳层与底栏导航

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: 添加底栏所需图标依赖**
- [ ] **Step 2: 定义 `课表 / 插件 / 设置` 三项宿主页面状态**
- [ ] **Step 3: 使用 `Scaffold` 和自定义底栏容器替换顶部文本按钮**
- [ ] **Step 4: 新增设置页并接入内容区切换**

### Task 2: 课表页周视图重构

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`

- [ ] **Step 1: 重组页面为顶部信息区、同步设置区、周视图区、详情区**
- [ ] **Step 2: 根据 `TermTimingProfile` 构建节次时间轴与周日期头部**
- [ ] **Step 3: 将课程渲染为周视图网格中的彩色课程块**
- [ ] **Step 4: 保留并迁移提醒创建、状态消息、插件提示等现有交互**

### Task 3: 插件页视觉统一

**Files:**
- Modify: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginMarketScreen.kt`

- [ ] **Step 1: 改造页面容器为深色卡片布局**
- [ ] **Step 2: 调整远程市场与本地导入操作区样式**
- [ ] **Step 3: 统一已安装插件和远程插件卡片风格**
- [ ] **Step 4: 保持安装预检弹窗能力可用**

### Task 4: 验证与收尾

**Files:**
- Modify: `docs/plans/2026-04-28-schedule-navigation-redesign-design.md`
- Modify: `docs/superpowers/plans/2026-04-28-schedule-navigation-redesign.md`

- [ ] **Step 1: 运行 `assembleDebug` 检查编译**
- [ ] **Step 2: 修正因 Compose API 或依赖变化导致的问题**
- [ ] **Step 3: 校对文档与实际实现是否一致**
