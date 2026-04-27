# Workflow Plugin Engine V2 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 QuickJS 内置插件链路升级为可本地/远程安装、可校验、可声明权限、由宿主解释执行的 V2 工作流插件系统，并打通受限 Web 登录、声明式课表 UI、自动闹钟与当天/第二天切换 widget。

**Architecture:** 新增 `core-plugin` 作为 V2 插件核心模块，负责插件包、manifest、权限、工作流与安装校验；新增 `core-reminder` 负责提醒规则与分发；新增 `feature-plugin` 负责插件市场、安装授权和 Web 登录页面；`feature-schedule` 与 `feature-widget` 继续承接产品 UI，但改为消费 V2 引擎产出的结构化结果。

**Tech Stack:** Kotlin、AGP、Jetpack Compose、WebView、Glance、WorkManager、DataStore、kotlinx.serialization、Junit4

---

## Chunk 1: Foundation

### Task 1: 工程脚手架与模块边界

**Files:**
- Create: `core-plugin/build.gradle.kts`
- Create: `core-plugin/src/main/AndroidManifest.xml`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/PluginApiVersion.kt`
- Create: `core-reminder/build.gradle.kts`
- Create: `core-reminder/src/main/AndroidManifest.xml`
- Create: `feature-plugin/build.gradle.kts`
- Create: `feature-plugin/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `feature-schedule/build.gradle.kts`
- Modify: `feature-widget/build.gradle.kts`

- [ ] **Step 1: 新增模块并接入 Gradle**

```kotlin
include(
    ":app",
    ":core-kernel",
    ":core-js",
    ":core-data",
    ":core-plugin",
    ":core-reminder",
    ":feature-plugin",
    ":feature-schedule",
    ":feature-widget",
)
```

- [ ] **Step 2: 为新模块补齐最小 build.gradle**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}
```

- [ ] **Step 3: 定义 V2 API 版本常量与基础包名空间**

```kotlin
object PluginApiVersion {
    const val CURRENT = 2
}
```

- [ ] **Step 4: 跑一次装配，确保模块依赖闭合**

Run: `./gradlew :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts feature-schedule/build.gradle.kts feature-widget/build.gradle.kts core-plugin core-reminder feature-plugin
git commit -m "build: scaffold v2 plugin engine modules"
```

### Task 2: V2 插件清单、权限与工作流协议

**Files:**
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/manifest/PluginManifest.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/manifest/PluginPermission.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/workflow/WorkflowDefinition.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/workflow/WorkflowStep.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/workflow/WorkflowState.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/ui/UiContribution.kt`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/manifest/PluginManifestTest.kt`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/workflow/WorkflowDefinitionTest.kt`

- [ ] **Step 1: 先写 manifest 解析与校验失败测试**

```kotlin
@Test
fun rejectsMissingPluginId() {
    val raw = """{"name":"demo"}"""
    assertFails { decodeManifest(raw) }
}
```

- [ ] **Step 2: 定义 manifest、权限和 allowedHosts 结构**

```kotlin
@Serializable
data class PluginManifest(
    val pluginId: String,
    val version: String,
    val declaredPermissions: List<PluginPermission>,
    val allowedHosts: List<String>,
)
```

- [ ] **Step 3: 定义首批工作流步骤与状态机枚举**

```kotlin
@Serializable
sealed interface WorkflowStep {
    @Serializable data class FormCollect(val formId: String) : WorkflowStep
    @Serializable data class WebOpen(val sessionId: String, val url: String) : WorkflowStep
    @Serializable data class ScheduleEmit(val payloadRef: String) : WorkflowStep
}
```

- [ ] **Step 4: 跑协议层单测**

Run: `./gradlew :core-plugin:testDebugUnitTest --tests "*PluginManifestTest" --tests "*WorkflowDefinitionTest"`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core-plugin/src/main/java/com/kebiao/viewer/core/plugin core-plugin/src/test/java/com/kebiao/viewer/core/plugin
git commit -m "feat: define v2 plugin manifest and workflow contracts"
```

### Task 3: 插件包读取、摘要校验、签名校验与注册表

**Files:**
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/packageformat/PluginPackageReader.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/packageformat/PluginPackageLayout.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/security/PluginChecksumVerifier.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/security/PluginSignatureVerifier.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/install/PluginInstaller.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/plugin/PluginRegistryRepository.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/plugin/DataStorePluginRegistryRepository.kt`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/security/PluginSignatureVerifierTest.kt`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/install/PluginInstallerTest.kt`

- [ ] **Step 1: 写包结构缺失时安装失败的测试**

```kotlin
@Test
fun failsWhenManifestMissing() {
    val result = installer.install(packageBytesWithoutManifest)
    assertTrue(result is InstallResult.Failure)
}
```

- [ ] **Step 2: 实现 ZIP 读取与固定目录结构校验**

```kotlin
class PluginPackageReader {
    fun read(bytes: ByteArray): PluginPackageLayout = TODO()
}
```

- [ ] **Step 3: 实现摘要和签名校验器**

```kotlin
interface PluginSignatureVerifier {
    fun verify(layout: PluginPackageLayout): SignatureVerificationResult
}
```

- [ ] **Step 4: 接入已安装插件注册表**

```kotlin
interface PluginRegistryRepository {
    suspend fun saveInstalledPlugin(plugin: InstalledPluginRecord)
}
```

- [ ] **Step 5: 运行安装链路单测**

Run: `./gradlew :core-plugin:testDebugUnitTest --tests "*PluginInstallerTest" --tests "*PluginSignatureVerifierTest"`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core-plugin/src/main/java/com/kebiao/viewer/core/plugin/packageformat core-plugin/src/main/java/com/kebiao/viewer/core/plugin/security core-plugin/src/main/java/com/kebiao/viewer/core/plugin/install core-data/src/main/java/com/kebiao/viewer/core/data/plugin core-plugin/src/test/java/com/kebiao/viewer/core/plugin
git commit -m "feat: add plugin package install and verification pipeline"
```

## Chunk 2: Runtime

### Task 4: 插件市场、本地导入与安装授权界面

**Files:**
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/market/PluginMarketScreen.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/market/PluginMarketViewModel.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/install/PluginInstallReviewScreen.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/install/PluginInstallReviewViewModel.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/market/MarketIndex.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/market/MarketIndexRepository.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Create: `app/src/main/java/com/kebiao/viewer/app/MainNavGraph.kt`

- [ ] **Step 1: 写市场索引解析与签名失败测试**

```kotlin
@Test
fun rejectsUnsignedMarketIndex() {
    assertFails { repository.parse(rawIndexWithoutSignature) }
}
```

- [ ] **Step 2: 实现市场索引读取和本地导入入口**

```kotlin
data class MarketPluginSummary(
    val pluginId: String,
    val version: String,
    val downloadUrl: String,
)
```

- [ ] **Step 3: 实现安装确认页，展示发布者、权限和 allowedHosts**

```kotlin
Text("将授权以下能力：")
```

- [ ] **Step 4: 在 `MainActivity` 中接入插件市场导航**

Run: `./gradlew :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin core-plugin/src/main/java/com/kebiao/viewer/core/plugin/market app/src/main/java/com/kebiao/viewer/app/MainActivity.kt app/src/main/java/com/kebiao/viewer/app/MainNavGraph.kt
git commit -m "feat: add plugin market and install review flows"
```

### Task 5: 工作流引擎与运行上下文

**Files:**
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/PluginRuntimeContext.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/WorkflowEngine.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/WorkflowReducer.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/StepDispatcher.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/ExecutionResult.kt`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/runtime/WorkflowEngineTest.kt`

- [ ] **Step 1: 先写一个从 `Ready -> Running -> WaitingUserInput` 的状态机测试**

```kotlin
@Test
fun movesIntoWaitingUserInputForFormStep() {
    val result = engine.start(definitionWithFormStep)
    assertEquals(WorkflowState.WaitingUserInput, result.state)
}
```

- [ ] **Step 2: 定义运行上下文与引擎入口**

```kotlin
interface WorkflowEngine {
    suspend fun start(request: StartWorkflowRequest): ExecutionResult
}
```

- [ ] **Step 3: 实现基础步骤分发器和错误映射**

```kotlin
sealed interface ExecutionError {
    data class PermissionDenied(val permission: String) : ExecutionError
}
```

- [ ] **Step 4: 运行工作流引擎单测**

Run: `./gradlew :core-plugin:testDebugUnitTest --tests "*WorkflowEngineTest"`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime core-plugin/src/test/java/com/kebiao/viewer/core/plugin/runtime
git commit -m "feat: add v2 workflow engine runtime"
```

### Task 6: 受限 Web 会话宿主与 Web 数据包提取

**Files:**
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/web/WebSessionSpec.kt`
- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/web/WebSessionPacket.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/web/PluginWebSessionScreen.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/web/PluginWebSessionViewModel.kt`
- Create: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/web/PluginWebViewClient.kt`
- Create: `feature-plugin/src/androidTest/java/com/kebiao/viewer/feature/plugin/web/PluginWebSessionTest.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainNavGraph.kt`

- [ ] **Step 1: 写域名不在白名单时阻止打开的测试**

```kotlin
@Test
fun blocksNonWhitelistedHost() {
    assertTrue(client.shouldBlock("https://evil.example"))
}
```

- [ ] **Step 2: 定义 Web 会话规格和回传数据包**

```kotlin
@Serializable
data class WebSessionPacket(
    val finalUrl: String,
    val cookies: Map<String, String>,
    val htmlDigest: String,
)
```

- [ ] **Step 3: 实现 `WebView` 宿主和 `web.await / web.extract` 回调通道**

```kotlin
AndroidView(factory = { WebView(it) })
```

- [ ] **Step 4: 跑受限 Web 会话测试或最小装配测试**

Run: `./gradlew :feature-plugin:connectedDebugAndroidTest`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core-plugin/src/main/java/com/kebiao/viewer/core/plugin/web feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/web feature-plugin/src/androidTest/java/com/kebiao/viewer/feature/plugin/web app/src/main/java/com/kebiao/viewer/app/MainNavGraph.kt
git commit -m "feat: add restricted web session host"
```

### Task 7: 构建最小可运行的 V2 示例插件

**Files:**
- Create: `app/src/main/assets/plugin-dev/demo-campus-v2/manifest.json`
- Create: `app/src/main/assets/plugin-dev/demo-campus-v2/workflow.json`
- Create: `app/src/main/assets/plugin-dev/demo-campus-v2/permissions.json`
- Create: `app/src/main/assets/plugin-dev/demo-campus-v2/ui/schedule.json`
- Create: `app/src/main/assets/plugin-dev/demo-campus-v2/datapack/slot-mapping.json`
- Create: `app/src/main/assets/plugin-market/dev-index.json`
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/runtime/DemoCampusWorkflowTest.kt`

- [ ] **Step 1: 写示例插件工作流能产出 `TermSchedule` 的测试**

```kotlin
@Test
fun demoWorkflowEmitsSchedule() {
    val result = engine.start(demoPluginRequest)
    assertTrue(result.schedule != null)
}
```

- [ ] **Step 2: 创建最小示例插件包内容**

```json
{
  "pluginId": "demo-campus-v2",
  "entryWorkflow": "sync-schedule"
}
```

- [ ] **Step 3: 把市场索引或开发模式入口接到示例插件**

- [ ] **Step 4: 运行示例插件测试**

Run: `./gradlew :core-plugin:testDebugUnitTest --tests "*DemoCampusWorkflowTest"`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/plugin-dev app/src/main/assets/plugin-market core-plugin/src/test/java/com/kebiao/viewer/core/plugin/runtime/DemoCampusWorkflowTest.kt
git commit -m "test: add minimal demo v2 plugin package"
```

## Chunk 3: Product Integration

### Task 8: 课表页面声明式 UI 扩展与选择模式

**Files:**
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ui/UiContributionRenderer.kt`
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/selection/ScheduleSelectionState.kt`
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/selection/TimeSlotSelection.kt`
- Create: `feature-schedule/src/test/java/com/kebiao/viewer/feature/schedule/selection/ScheduleSelectionStateTest.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleScreen.kt`

- [ ] **Step 1: 写横向节次选择规则测试**

```kotlin
@Test
fun selectsAllCoursesForTimeSlot() {
    val selected = state.selectTimeSlot(startNode = 3, endNode = 4)
    assertEquals(3, selected.size)
}
```

- [ ] **Step 2: 定义单课选择和横向节次选择状态**

```kotlin
sealed interface ScheduleSelectionState {
    data class SingleCourse(val courseId: String) : ScheduleSelectionState
    data class TimeSlot(val startNode: Int, val endNode: Int) : ScheduleSelectionState
}
```

- [ ] **Step 3: 接入 `UiContributionRenderer` 和课程动作插槽**

```kotlin
@Composable
fun UiContributionRenderer(contributions: List<UiContribution>) { }
```

- [ ] **Step 4: 跑 `feature-schedule` 单测和装配**

Run: `./gradlew :feature-schedule:testDebugUnitTest :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule feature-schedule/src/test/java/com/kebiao/viewer/feature/schedule
git commit -m "feat: add schedule ui contributions and selection modes"
```

### Task 9: 自动闹钟规则、计划展开与混合分发

**Files:**
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderRule.kt`
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/model/ReminderPlan.kt`
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/AlarmDispatcher.kt`
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/SystemAlarmClockDispatcher.kt`
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/dispatch/FallbackReminderDispatcher.kt`
- Create: `core-reminder/src/main/java/com/kebiao/viewer/core/reminder/ReminderPlanner.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/ReminderRepository.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/reminder/DataStoreReminderRepository.kt`
- Create: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/reminder/ReminderRuleEditor.kt`
- Create: `core-reminder/src/test/java/com/kebiao/viewer/core/reminder/ReminderPlannerTest.kt`

- [ ] **Step 1: 先写单课规则和横向节次规则展开测试**

```kotlin
@Test
fun expandsTimeSlotRuleAcrossWeek() {
    val plans = planner.expand(rule, schedule)
    assertEquals(5, plans.size)
}
```

- [ ] **Step 2: 定义提醒规则与分发结果**

```kotlin
data class ReminderRule(
    val ruleId: String,
    val scopeType: ScopeType,
    val advanceMinutes: Int,
    val ringtoneUri: String?,
)
```

- [ ] **Step 3: 实现系统闹钟优先、宿主提醒降级的分发器**

```kotlin
interface AlarmDispatcher {
    suspend fun dispatch(plan: ReminderPlan): AlarmDispatch
}
```

- [ ] **Step 4: 接入课表页的提醒规则编辑入口**

- [ ] **Step 5: 跑提醒层单测**

Run: `./gradlew :core-reminder:testDebugUnitTest`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core-reminder/src/main/java/com/kebiao/viewer/core/reminder core-reminder/src/test/java/com/kebiao/viewer/core/reminder core-data/src/main/java/com/kebiao/viewer/core/data/reminder feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/reminder
git commit -m "feat: add reminder rules and hybrid alarm dispatch"
```

### Task 10: Widget 当天/第二天切换与主流程切换到 V2

**Files:**
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/WidgetPreferencesRepository.kt`
- Create: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleWidgetUpdater.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/AppContainer.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/MainActivity.kt`
- Modify: `README.md`
- Create: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/ScheduleWidgetProjectionTest.kt`

- [ ] **Step 1: 写 widget 根据状态切换今天/明天数据的测试**

```kotlin
@Test
fun projectsTomorrowCoursesWhenStateIsTomorrow() {
    val model = projector.project(schedule, WidgetDay.Tomorrow)
    assertEquals("明天", model.title)
}
```

- [ ] **Step 2: 增加 widget 视图状态存储**

```kotlin
enum class WidgetDay { Today, Tomorrow }
```

- [ ] **Step 3: 修改 Glance widget，接入切换动作与轻量提醒标记**

- [ ] **Step 4: 把主同步入口切换到 `core-plugin` V2 引擎，并保留迁移期开关**

- [ ] **Step 5: 跑最终回归**

Run: `./gradlew :feature-widget:testDebugUnitTest :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core-data/src/main/java/com/kebiao/viewer/core/data/widget feature-widget/src/main/java/com/kebiao/viewer/feature/widget feature-widget/src/test/java/com/kebiao/viewer/feature/widget app/src/main/java/com/kebiao/viewer/app README.md
git commit -m "feat: switch widget to today tomorrow toggle and wire v2 engine"
```

## Notes

- V2 插件引擎完成前，不要删除 `core-js` 与现有 demo 资产；先保留迁移开关
- 所有运行时能力都要经过 manifest 权限和 allowedHosts 双重校验
- `feature-plugin:connectedDebugAndroidTest` 依赖模拟或测试 Web 内容；如 CI 暂不稳定，可先以最小 instrumentation 用例起步
- 若提醒规则、安装记录和授权记录在 `DataStore` 中出现明显复杂度膨胀，再追加单独计划迁移至 `Room`

Plan complete and saved to `docs/superpowers/plans/2026-04-27-workflow-plugin-engine-v2.md`. Ready to execute?
