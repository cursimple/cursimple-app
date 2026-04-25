# 课表查看 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于 Kotlin 的 Android 课表查看应用，支持 JS 插件拉取课表、桌面小组件和多 ABI 产物。  
**Architecture:** 采用微内核分层：`core-kernel` 定义协议，`core-js` 实现插件执行，`feature` 负责 UI/Widget，`app` 进行组装。插件通过 QuickJS 调用并输出统一课表 JSON。  
**Tech Stack:** Kotlin、AGP、Jetpack Compose、Glance、DataStore、QuickJS、GitHub Actions

---

### Task 1: 工程初始化与模块结构

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `core-kernel/build.gradle.kts`
- Create: `core-js/build.gradle.kts`
- Create: `core-data/build.gradle.kts`
- Create: `feature-schedule/build.gradle.kts`
- Create: `feature-widget/build.gradle.kts`

- [ ] **Step 1: 创建根工程与版本目录**
- [ ] **Step 2: 创建六个模块并声明依赖关系**
- [ ] **Step 3: 配置 Android 7~16 与 ABI split**
- [ ] **Step 4: 运行 Gradle 基础任务验证配置**

### Task 2: 微内核协议与数据模型

**Files:**
- Create: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/model/ScheduleModels.kt`
- Create: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/plugin/PluginContracts.kt`
- Create: `core-kernel/src/main/java/com/kebiao/viewer/core/kernel/service/ScheduleSyncService.kt`

- [ ] **Step 1: 定义统一课程 JSON 对应数据结构**
- [ ] **Step 2: 定义插件目录与执行器抽象接口**
- [ ] **Step 3: 编写微内核同步服务**
- [ ] **Step 4: 添加基础单元测试入口**

### Task 3: QuickJS 插件执行器与桥接

**Files:**
- Create: `core-js/src/main/java/com/kebiao/viewer/core/js/QuickJsScheduleExecutor.kt`
- Create: `core-js/src/main/java/com/kebiao/viewer/core/js/JsHostBridge.kt`
- Create: `core-js/src/main/java/com/kebiao/viewer/core/js/PluginCatalogAssetSource.kt`

- [ ] **Step 1: 加载 JS 插件脚本与元数据**
- [ ] **Step 2: 实现 Host 桥接能力（HTTP/日志）**
- [ ] **Step 3: 实现 login/fetch/normalize 调用链**
- [ ] **Step 4: 将输出反序列化为标准模型**

### Task 4: 数据层与应用层组装

**Files:**
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/ScheduleRepository.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/DataStoreScheduleRepository.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/ClassScheduleApplication.kt`

- [ ] **Step 1: 用 DataStore 实现课表缓存仓储**
- [ ] **Step 2: 提供同步与查询接口**
- [ ] **Step 3: 在 app 层组装 Kernel + JS + Data**
- [ ] **Step 4: 暴露给 feature 模块使用**

### Task 5: Compose 课表页面

**Files:**
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [ ] **Step 1: 创建账号输入与同步按钮 UI**
- [ ] **Step 2: 接入同步流程并展示状态**
- [ ] **Step 3: 渲染今日课程与全周列表**
- [ ] **Step 4: 处理错误态与空态提示**

### Task 6: 桌面小组件（Glance）

**Files:**
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidget.kt`
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 构建小组件 UI 布局**
- [ ] **Step 2: 读取仓储并映射今日课程**
- [ ] **Step 3: 同步后触发小组件刷新**
- [ ] **Step 4: 完成 Manifest 注册**

### Task 7: 插件样例与 CI/CD

**Files:**
- Create: `app/src/main/assets/plugins/index.json`
- Create: `app/src/main/assets/plugins/demo-campus.js`
- Create: `.github/workflows/android-ci.yml`
- Create: `README.md`

- [ ] **Step 1: 提供可运行的示例 JS 插件**
- [ ] **Step 2: 编写插件协议说明与本地调试说明**
- [ ] **Step 3: 配置 GitHub Actions 构建流水线**
- [ ] **Step 4: 上传 v7a/v8a/universal 产物**

### Task 8: 验证与收尾

**Files:**
- Modify: `docs/plans/2026-04-25-class-schedule-viewer-design.md`
- Modify: `docs/superpowers/plans/2026-04-25-class-schedule-viewer.md`

- [ ] **Step 1: 运行 `assembleDebug` 验证工程可构建**
- [ ] **Step 2: 运行关键单测任务**
- [ ] **Step 3: 修正文档中的实际路径与命令**
- [ ] **Step 4: 记录后续可扩展项（插件签名/发布）**

