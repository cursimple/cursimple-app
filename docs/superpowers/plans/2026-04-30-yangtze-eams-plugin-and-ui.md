# 长江大学 EAMS 插件与界面收敛 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将课表页收敛为纯课表视图，并为 V2 插件引擎补充可复用的 EAMS 课表解析能力，内置长江大学课表插件。

**Architecture:** 保持现有底栏与数据仓储不变，把宿主同步配置和提醒管理迁移到设置页；在 `core-plugin` 中新增一个结构化 EAMS 解析步骤，插件通过 `web_session + http_request + 解析步骤` 输出标准课表。

**Tech Stack:** Kotlin、Jetpack Compose、kotlinx.serialization、OkHttp、Android WebView、DataStore

---

### Task 1: 调整课表页与设置页职责边界

**Files:**
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`

- [ ] 把同步表单、提醒创建、提醒规则从课表页迁出。
- [ ] 在设置页接入同步表单、提醒创建和提醒规则列表。
- [ ] 让 `MainActivity` 把同一个 `ScheduleViewModel` 注入设置页。
- [ ] 调整课表页空状态和状态文案，使其引导用户去设置页或插件页。

### Task 2: 扩展工作流协议与运行时

**Files:**
- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/workflow/WorkflowDefinition.kt`
- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/WorkflowEngine.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/EamsScheduleParser.kt`

- [ ] 为工作流新增 EAMS 课表输出步骤及其参数字段。
- [ ] 为 `http_request` 注入来自 `web_session` 的 Cookie 组装能力。
- [ ] 实现 EAMS 解析器，把 GET/POST 返回内容转换为 `TermSchedule`。
- [ ] 保持现有 `schedule_emit_static` 行为不回归。

### Task 3: 新增长江大学内置插件

**Files:**
- Create: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/manifest.json`
- Create: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/workflow.json`
- Create: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/ui/schedule.json`
- Create: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/datapack/timing.json`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`

- [ ] 写插件 manifest、白名单域名和工作流步骤。
- [ ] 提供基础课表 UI 横幅与默认节次时间。
- [ ] 在应用启动时确保内置安装该插件。

### Task 4: 验证

**Files:**
- Modify if needed: `README.md`

- [ ] 运行单元测试或最小编译验证。
- [ ] 检查新增代码是否符合现有 V2 插件模型。
- [ ] 汇总未覆盖的真实联调风险。
