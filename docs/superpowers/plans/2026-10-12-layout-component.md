# 布局组件与侧边栏自定义 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 主体自带侧边栏自定义；新增组件 API 12「布局」能力和原生底栏外壳；新建公开布局组件仓库切换并配置底栏。

**Architecture:** 目的地 ID、侧边栏偏好、生效布局记录和校验都是 core-data 里的纯 Kotlin，app 与 feature-plugin 共用。组件通过 `extension.layouts` 声明、通过页面命令 `layout.apply` 提交，主体校验后存入 UserPreferences；MainActivity 根据「记录 + 归属组件是否仍有效」选择抽屉外壳或底栏外壳，两种外壳都是原生 Compose。组件只是一个设置页 + 空的 `main.js`。

**Tech Stack:** Kotlin, Jetpack Compose M3, DataStore, kotlinx.serialization；组件端原生 JS（无依赖）、`node --test`。

**Spec:** `docs/superpowers/specs/2026-10-12-layout-component-design.md`

## Global Constraints

- 主体代码、字符串、注释、类型名不出现布局组件的名字或仓库名；只用「布局」「目的地」「底栏」等通用词。
- `schedule`、`settings` 在任何布局、任何配置下都可达；底栏中「我的」固定最后、承载其余目的地。
- 底栏入口总数（含「我的」）3–5；`items` 首项必须是 `schedule`。
- 目的地 ID（公开 API）：`schedule courses memos components plugins reminders settings about`；快捷操作 ID：`action.theme action.background action.overrides action.update`。
- `PluginApiVersion.CURRENT = 12`，`LAYOUT_MIN = 12`；组件 `apiVersion: 12`、`minHostVersion: "0.7.11"`。
- 本地 `gradle.properties` 升到 `app.versionName=0.7.11`、`app.versionCode=37`，否则组件装不上；不提交。
- UI 先读 `docs/ui-guidelines.local.md`：按钮有框（取消 `AppOutlinedButton`、确认实心）、图标 `Icons.Rounded`、48dp 触达、深浅色、320dp、1.3 倍字号。
- 新文案三语：`values`、`values-zh-rTW`、`values-en`。
- 全程不 commit、不 push；构建前 `export ANDROID_HOME=~/Library/Android/sdk`。下文各任务的「Commit」步骤一律改为「本地检查点：跑该任务测试」。

## Review Focus

1. 归属组件被停用、删除、降级到不声明 layouts 的版本：应立即回到侧边栏，不崩、不白屏。→ Task 3 `resolveEffectiveLayout` 测试。
2. 旧数据或手改的偏好里出现未知 ID、重复 ID、锁定项被标为隐藏：解码后锁定项可见、未知项丢弃、缺失项按默认顺序补到末尾。→ Task 1 解码测试。
3. 组件发来非法 `items`（缺 schedule、含 `me`/未知 ID、过多过少、重复）：拒绝且不改当前布局。→ Task 1 `validateBottomBarItems` 测试 + Task 4 bridge 不落盘。
4. 底栏模式下打开组件页面（嵌入 WebView）后返回、或在「我的」进入设置子页再返回：返回链不跳出应用、底栏不消失。→ Task 6 Compose 测试。
5. 侧边栏关掉全部可关项：只剩课表、设置 + 「更多」，「更多」里每项都能打开。→ Task 5 Compose 测试。

---

### Task 1: 目的地与布局偏好（core-data）

**Files:**
- Create: `core-data/src/main/java/com/x500x/cursimple/core/data/navigation/AppNavigation.kt`
- Modify: `core-data/.../UserPreferencesRepository.kt`（`UserPreferences` 加字段、接口加 setter）
- Modify: `core-data/.../DataStoreUserPreferencesRepository.kt`（键、`preferencesFlow` 解码、setter、`resetAllSettings` 移除）
- Test: `core-data/src/test/java/com/x500x/cursimple/core/data/navigation/AppNavigationTest.kt`

**Interfaces:**
- Produces（包 `com.x500x.cursimple.core.data.navigation`）：
  - `enum class NavDestination(val id: String, val locked: Boolean, val inDrawer: Boolean)`，顺序固定：`Schedule("schedule",true,true), Courses("courses",false,true), Memos("memos",false,true), Components("components",false,false), Plugins("plugins",false,true), Reminders("reminders",false,true), Settings("settings",true,true), About("about",false,true)`；`companion fun fromId(id: String): NavDestination?`。
  - `data class DrawerLayout(val order: List<NavDestination>, val hidden: Set<NavDestination>, val shortcutsVisible: Boolean)`，`companion val DEFAULT`（order = 全部 `inDrawer` 项按枚举序，hidden 空，shortcuts true）；属性 `visible: List<NavDestination>`、`overflow: List<NavDestination>`（按 order）。
  - `fun encodeDrawerLayout(layout: DrawerLayout): String` / `fun decodeDrawerLayout(raw: String?): DrawerLayout`（JSON `{"order":[ids],"hidden":[ids],"shortcuts":bool}`；坏 JSON → DEFAULT）。
  - `const val SHELL_DRAWER = "drawer"`，`const val SHELL_BOTTOM_BAR = "bottomBar"`，`val SUPPORTED_SHELLS = listOf(SHELL_DRAWER, SHELL_BOTTOM_BAR)`，`const val MAX_BAR_ITEMS = 5`。
  - `data class AppLayoutSelection(val ownerId: String, val shell: String, val items: List<NavDestination>)`；`encodeAppLayout(AppLayoutSelection?): String?` / `decodeAppLayout(raw: String?): AppLayoutSelection?`（坏数据 → null）。
  - `fun validateBottomBarItems(ids: List<String>): List<NavDestination>`，非法时抛 `IllegalArgumentException`（中文消息给组件页显示）。
  - `fun bottomBarOverflow(items: List<NavDestination>): List<NavDestination>` = 枚举序中不在 items 里的项。
  - `UserPreferences.drawerLayout: DrawerLayout = DrawerLayout.DEFAULT`，`UserPreferences.appLayout: AppLayoutSelection? = null`；`suspend fun setDrawerLayout(layout: DrawerLayout)`，`suspend fun setAppLayout(selection: AppLayoutSelection?)`；DataStore 键 `drawer_layout`、`app_layout`。

- [ ] **Step 1: 写失败测试** `AppNavigationTest`：
  - `default drawer lists every drawer destination in order` → `DrawerLayout.DEFAULT.visible.map{it.id} == listOf("schedule","courses","memos","plugins","reminders","settings","about")`，`overflow` 为空。
  - `decode drops unknown ids, unhides locked items and appends missing ones`：输入 `{"order":["about","x","schedule","about"],"hidden":["settings","memos"],"shortcuts":false}` → order 以 `about, schedule` 开头且其余按默认补齐、无重复；hidden == `{Memos}`；shortcuts false。
  - `decode of garbage returns default`：`decodeDrawerLayout("{")` 与 `decodeDrawerLayout(null)` 都等于 DEFAULT。
  - `drawer layout round trips`。
  - `bottom bar items must start with schedule and fit beside me`：`validateBottomBarItems(listOf("schedule","memos","components"))` 通过；以下各自抛异常：`[]`、`["memos"]`、`["schedule"]`（总数 2 < 3）、`["schedule","memos","memos"]`、`["schedule","me"]`、`["schedule","a","b","c","d"]`、`["schedule","memos","courses","plugins","about"]`（总数 6）。
  - `overflow keeps fixed order`：`bottomBarOverflow(listOf(Schedule, Memos, Components)).map{it.id} == listOf("courses","plugins","reminders","settings","about")`。
  - `app layout round trips and rejects garbage`：`decodeAppLayout(encodeAppLayout(sel)) == sel`；`decodeAppLayout("nope") == null`；items 里含未知 ID 的记录 → null。
- [ ] **Step 2: 运行确认失败** `./gradlew :core-data:testDebugUnitTest --tests '*AppNavigationTest*'` → 编译失败。
- [ ] **Step 3: 实现** `AppNavigation.kt`（用 kotlinx.serialization 的 `JsonObject` 手解码，容错）；接入 `UserPreferences`、仓库键、解码、setter、reset（两个键都 remove）。仓库里的其他实现/假对象若因新接口方法编译失败，补默认空实现。
- [ ] **Step 4: 运行** `./gradlew :core-data:testDebugUnitTest` → 全部 PASS。

### Task 2: Manifest `extension.layouts` 与 API 12（core-plugin）

**Files:**
- Modify: `core-plugin/.../PluginApiVersion.kt`（`CURRENT = 12`，加 `LAYOUT_MIN = 12`）
- Modify: `core-plugin/.../manifest/PluginManifest.kt`（`PluginLayoutSpec`，`PluginExtensionSpec.layouts`）
- Modify: `core-plugin/.../packageformat/PluginPackageReader.kt:101-128`（`validateKind` 加 layouts 块，照 widgets 块）
- Test: `core-plugin/src/test/.../packageformat/ComponentLayoutPackageTest.kt`（照 `ComponentWidgetPackageTest.kt` 的构造方式）

**Interfaces:**
- Produces: `@Serializable data class PluginLayoutSpec(val id: String, val title: String, val shell: String)`；`PluginExtensionSpec.layouts: List<PluginLayoutSpec> = emptyList()`。

- [ ] **Step 1: 写失败测试** `ComponentLayoutPackageTest`：
  - `layouts require api 12`：apiVersion 11 + layouts → 抛 `PluginException`。
  - `valid layouts install`：apiVersion 12，`[{id:"bottom-bar",title:"底边栏",shell:"bottomBar"}]` → `decodeValidatedManifest` 成功且 `layouts.single().shell == "bottomBar"`。
  - `unknown shell is accepted for forward compatibility`：shell `"rail"` 通过。
  - `duplicate, unsafe or blank layouts are rejected`：重复 id、id `"../x"`、空 title、shell `"bottom bar!"`（只允许 `[A-Za-z]{1,32}`）、9 项，各自抛异常。
  - `api 12 manifests pass compatibility`（`PluginCompatibilityTest` 里加一条 apiVersion 12 兼容、13 拒绝）。
- [ ] **Step 2: 运行确认失败** `./gradlew :core-plugin:testDebugUnitTest --tests '*ComponentLayoutPackageTest*'`。
- [ ] **Step 3: 实现**，错误码复用 `plugin_error_extension_api_too_old` / `plugin_error_package_illegal_path`（参数 `"layouts"`）。
- [ ] **Step 4: 运行** `./gradlew :core-plugin:testDebugUnitTest` → PASS（若有断言 `CURRENT == 11` 的旧测试，改成 12）。

### Task 3: 生效布局判定与删除清理（app + feature-plugin）

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/navigation/AppLayoutResolver.kt`
- Modify: `feature-plugin/.../PluginMarketViewModel.kt:847`（`removePlugin`：被删的是归属组件时 `setAppLayout(null)`）
- Test: `app/src/test/java/com/x500x/cursimple/app/navigation/AppLayoutResolverTest.kt`

**Interfaces:**
- Consumes: Task 1 `AppLayoutSelection`、`SUPPORTED_SHELLS`；Task 2 `PluginLayoutSpec`。
- Produces: `fun resolveEffectiveLayout(selection: AppLayoutSelection?, declaredShells: Map<String, Set<String>>): AppLayoutSelection?` —— `declaredShells` 是「已安装、已启用、兼容的组件 pluginId → 其 manifest 声明的 shell 集合」；仅当 owner 在表中、shell 在其集合且在 `SUPPORTED_SHELLS`、且 shell 不是 `drawer` 时返回 selection，否则 null（null = 侧边栏）。

- [ ] **Step 1: 写失败测试**：`active owner with declared bottom bar wins`；`disabled or missing owner falls back`（owner 不在表）；`owner no longer declaring the shell falls back`；`unsupported shell falls back`（声明了 `"rail"` 也回落）；`null selection is drawer`。
- [ ] **Step 2: 运行确认失败** `./gradlew :app:testDebugUnitTest --tests '*AppLayoutResolverTest*'`。
- [ ] **Step 3: 实现** resolver；在 `removePlugin` 里拿被删记录的 pluginId，若等于 `prefs.appLayout?.ownerId` 则清空（仓库已注入该 VM 时直接用；没有就加构造参数并在 AppContainer 传入）。
- [ ] **Step 4: 运行** `./gradlew :app:testDebugUnitTest --tests '*AppLayoutResolverTest*' :feature-plugin:testDebugUnitTest` → PASS。

### Task 4: 页面命令 `layout.apply` / `layout.current` 与文档（feature-plugin + app + docs）

**Files:**
- Modify: `feature-plugin/.../extension/ExtensionSettingsScreen.kt:87-127`（`ExtensionHostActions` 加两个默认报错的方法）
- Modify: `feature-plugin/.../extension/ExtensionOwnedPage.kt:145-310`（两个分支）、`:458`（capabilities）
- Modify: `app/.../MainActivity.kt:362-424`（actions 实现）
- Modify: `docs/plugin-system.md`（API 表加 12 行 + 「App layouts (API 12)」一节）
- Test: `feature-plugin/src/test/.../extension/LayoutCommandTest.kt`

**Interfaces:**
- Consumes: Task 1 全部；Task 2 `PluginLayoutSpec`。
- Produces:
  - `ExtensionHostActions.applyLayout(record: InstalledPluginRecord, payload: JsonObject): JsonElement = error("请更新应用以支持布局组件")`
  - `ExtensionHostActions.currentLayout(record: InstalledPluginRecord): JsonElement = error("请更新应用以支持布局组件")`
  - `internal fun layoutCapabilitiesJson(): String`（feature-plugin，生成 `{shells:[...SUPPORTED_SHELLS],destinations:[...全部 NavDestination.id],maxBarItems:5}`）
  - `fun applyLayoutRequest(ownerId: String, declared: Set<String>, payload: JsonObject): AppLayoutSelection?`（feature-plugin 纯函数：`shell=="drawer"` → null；`bottomBar` → 需在 declared 里，`items` 走 `validateBottomBarItems`；其他抛异常）
  - `layout.current` 返回 `{shell, items, ownedByCaller}`（未生效时 `shell:"drawer"`）。

- [ ] **Step 1: 写失败测试** `LayoutCommandTest`（测纯函数）：drawer → null；合法 bottomBar → selection（items 正确）；未声明 shell → 抛；非法 items → 抛；`layoutCapabilitiesJson()` 解析后 `destinations` 含全部 8 个 ID、`maxBarItems == 5`。
- [ ] **Step 2: 运行确认失败** `./gradlew :feature-plugin:testDebugUnitTest --tests '*LayoutCommandTest*'`。
- [ ] **Step 3: 实现**：bridge 两个分支先 `check(currentInteractive)`、`require(spec.layouts.isNotEmpty()) { "组件未声明布局能力" }`，再调 actions；capabilities 加 `layouts:<layoutCapabilitiesJson()>`；MainActivity 实现：`applyLayout` → `applyLayoutRequest(record.pluginId, manifest 声明的 shells, payload)` 然后 `setAppLayout(...)`（drawer 时仅当调用方是当前 owner 才清空），返回 `layout.current` 的结果。
- [ ] **Step 4: 文档**：`docs/plugin-system.md` 写清声明、能力探测、两条命令、校验规则、回退规则（停用/删除/不声明/不支持 → 侧边栏）、目的地与快捷操作 ID 表；不写组件名。
- [ ] **Step 5: 运行** `./gradlew :feature-plugin:testDebugUnitTest :app:compileDebugKotlin` → PASS。

### Task 5: 侧边栏自定义页与「更多」（app）

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/navigation/SidebarSettingsPage.kt`
- Create: `app/src/main/java/com/x500x/cursimple/app/navigation/NavDestinationUi.kt`（`NavDestination` → 字符串资源、`Icons.Rounded` 图标；`AppScreen` 映射）
- Modify: `app/.../SettingsScreen.kt`（`SettingsDestination.Sidebar`：枚举、`title()`、搜索 `page(...)`、根页行放在外观相关分组、`when(destination)` 分支）
- Modify: `app/.../MainActivity.kt` `AppDrawer`（2058-2237）
- Modify: `app/src/main/res/values{,-zh-rTW,-en}/strings.xml`
- Test: `app/src/test/.../navigation/SidebarSettingsLogicTest.kt`，`app/src/androidTest/.../SidebarCustomizationUiTest.kt`

**Interfaces:**
- Consumes: Task 1 `DrawerLayout`、`setDrawerLayout`；Task 3 resolver（判断是否显示「当前使用组件提供的布局」卡）。
- Produces:
  - `fun DrawerLayout.toggled(destination: NavDestination): DrawerLayout`（锁定项不变）、`fun DrawerLayout.moved(from: Int, to: Int): DrawerLayout`。
  - `@Composable fun SidebarSettingsPage(layout: DrawerLayout, componentLayoutActive: Boolean, onChange: (DrawerLayout) -> Unit, onRestoreDrawer: () -> Unit)`。
  - `NavDestination.labelRes: Int`、`NavDestination.icon: ImageVector`、`fun NavDestination.toAppScreen(): MainActivity.AppScreen?`（Components → null）。

- [ ] **Step 1: 写失败单测**：`locked destinations cannot be hidden`；`toggling hides and restores`；`moving reorders without losing items`。
- [ ] **Step 2: 运行确认失败**，`./gradlew :app:testDebugUnitTest --tests '*SidebarSettingsLogicTest*'`。
- [ ] **Step 3: 实现逻辑与页面**：列表行 = 拖动手柄 + 图标 + 名称 + `Switch`（锁定项显示锁图标、无开关）；拖动参考 `SettingsQuickDragState`，并给每行提供「上移/下移」无障碍操作；「底部快捷按钮」一行开关；`componentLayoutActive` 时页面顶部卡片「当前使用组件提供的布局」+ `AppOutlinedButton("换回侧边栏")` → `setAppLayout(null)`。
- [ ] **Step 4: 改 `AppDrawer`**：按 `drawerLayout.visible` 画主体项；`shortcutsVisible=false` 时隐藏快捷两行；有 overflow 或快捷被关时在版本号上方显示 `AppOutlinedButton`「更多（n）」（n = overflow 数 + 快捷被关时 4），点开 `ModalBottomSheet` 列出 overflow 目的地和（被关时）四个快捷操作，点击后关闭面板与抽屉再执行。组件分区与「查看全部」不动。
- [ ] **Step 5: 文案**（三语）：`settings_sidebar_title` 侧边栏 / 側邊欄 / Sidebar；`settings_sidebar_hint` 拖动排序，关掉的项目会放进侧边栏底部的「更多」；`settings_sidebar_shortcuts` 底部快捷按钮；`settings_sidebar_locked` 始终显示；`settings_sidebar_component_layout` 当前使用组件提供的布局，侧边栏设置在换回后生效；`settings_sidebar_restore_drawer` 换回侧边栏；`main_drawer_more` 更多（%1$d）；`main_drawer_more_title` 更多；`settings_sidebar_move_up/down` 上移/下移。
- [ ] **Step 6: Compose 测试** `SidebarCustomizationUiTest`：关掉所有可关项后抽屉只剩课表、设置和「更多（n）」，「更多」面板里点「关于」能进入关于页；锁定项无开关。
- [ ] **Step 7: 运行** `./gradlew :app:testDebugUnitTest` 与该 androidTest（`connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...SidebarCustomizationUiTest`）→ PASS。

### Task 6: 底栏外壳、「我的」与「组件」页（app）

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/navigation/BottomBarShell.kt`（`AppBottomBar`、`MeScreen`、`ComponentsHubScreen`）
- Modify: `app/.../MainActivity.kt`（外壳选择、`navigationIcon`、BackHandler、抽屉手势、红点、guide 锚点、内容 padding）
- Modify: `app/.../AllExtensionsScreen.kt`（抽出列表 composable 供「组件」页复用）
- Modify: `app/.../guide/FirstRunGuide.kt:30-35`（底栏模式指向「我的」）
- Modify: strings 三语
- Test: `app/src/test/.../navigation/BottomBarStateTest.kt`，`app/src/androidTest/.../BottomBarShellUiTest.kt`

**Interfaces:**
- Consumes: Task 1 `bottomBarOverflow`、`NavDestination`；Task 3 `resolveEffectiveLayout`；Task 5 `NavDestinationUi`。
- Produces:
  - `sealed interface BarTab { data class Destination(val destination: NavDestination) : BarTab; data object Me : BarTab }`
  - `fun selectedBarTab(items: List<NavDestination>, current: NavDestination?, openedFromMe: Boolean): BarTab`（当前目的地在 items 里且不是从「我的」进入 → 对应标签；否则 Me）
  - `@Composable fun AppBottomBar(items: List<NavDestination>, selected: BarTab, meBadge: Boolean, onSelect: (BarTab) -> Unit)`（M3 `NavigationBar`，标签单行省略）
  - `@Composable fun MeScreen(overflow: List<NavDestination>, termLabel: String, versionName: String, updateBadge: Boolean, onOpen: (NavDestination) -> Unit, onShortcut: (String) -> Unit, onOpenUpdate: () -> Unit)`（快捷 ID 用 `action.*`）
  - `@Composable fun ComponentsHubScreen(entries: List<Pair<String,String>>, current: String?, onOpen: (String) -> Unit, onOpenMarket: () -> Unit)`

- [ ] **Step 1: 写失败单测** `BottomBarStateTest`：`tab in bar is selected`；`destination outside bar selects me`；`opening a bar destination from me still selects me`；`overflow contains settings unless settings is in the bar`。
- [ ] **Step 2: 运行确认失败**。
- [ ] **Step 3: 实现 `BottomBarShell.kt`**：「我的」= 顶部卡片（应用名、当前周、版本；有更新时 primary 色可点）→ 四个 48dp 圆形快捷按钮（带框、图标同侧边栏）→ overflow 列表（`Surface` 16dp 圆角行 + chevron）。「组件」页 = 组件列表 + `AppOutlinedButton`「组件市场」。
- [ ] **Step 4: 接入 MainActivity**：`val layout = resolveEffectiveLayout(prefs.appLayout, declaredShells)`（`declaredShells` 由已加载的 extension manifest 构建，和 `extensionTitles` 同处加载）。`layout != null` 时：不包 `ModalNavigationDrawer`、`Scaffold(bottomBar = AppBottomBar)`、菜单按钮替换为（`openedFromMe` 时）返回按钮、`drawerGesturesEnabled=false`、更新红点（应用+插件）挂在「我的」；新增 `rememberSaveable` 状态 `meOpen`、`openedFromMe`、`componentsOpen`。返回顺序：组件页面 → 组件 Hub；从「我的」进入的页 → 「我的」；其他标签 → 课表。切换布局时保持当前目的地（不在 items 里则 `openedFromMe=true`）。嵌入网页内容用 `innerPadding` 留出底栏。引导第一步锚点在底栏模式挂到「我的」。
- [ ] **Step 5: 文案**（三语）：`nav_me` 我的 / 我的 / Me；`nav_components` 组件 / 元件 / Components；`components_hub_market` 组件市场 / 元件市場 / Component market；`me_version` 版本 %1$s。
- [ ] **Step 6: Compose 测试** `BottomBarShellUiTest`（直接写 `prefs.appLayout` 并让 resolver 返回底栏，或给外壳 composable 直接传参）：底栏显示 课表/笔记/组件/我的；从「我的」打开设置后返回回到「我的」且底栏仍在；系统返回在笔记标签回到课表；`setAppLayout(null)` 后菜单按钮回来、底栏消失；320dp + 1.3 倍字号下四个标签文字不重叠（检查 bounds）。
- [ ] **Step 7: 运行** `./gradlew :app:testDebugUnitTest` 与该 androidTest → PASS。

### Task 7: 公开布局组件仓库

**Files（新仓库 `/Users/sheep/Public/git/cursimple-layout-component`，照 `cursimple-notify-component` 结构）:**
- Create: `package.json`（scripts: `test` `pack` `preview`）、`.gitignore`、`LICENSE`（MIT, cursimple）、`README.md`、`README_en.md`、`.github/workflows/release.yml`
- Create: `plugin-packages/app-layout/manifest.json`、`main.js`、`checksums.json`（由 pack 生成）、`ui/index.html`、`ui/app.js`、`ui/layout-rules.js`、`ui/style.css`、`ui/material-icons.js` + `ui/material-icons.LICENSE.txt`（从 notify 复制所需图标）
- Create: `scripts/pack.mjs`、`scripts/preview.mjs`（模拟桥：`capabilities.layouts`、`layout.apply`/`layout.current`、`?theme=dark`、`?scale=1.3`、`?legacy=1` 去掉 capabilities）
- Test: `test/layout-rules.test.mjs`、`test/manifest.test.mjs`

**Interfaces:**
- Consumes: Task 4 的命令与 capabilities 契约。
- Produces（`ui/layout-rules.js`，ES module）：
  - `DEFAULT_ITEMS = ['schedule','memos','components']`
  - `PICKABLE = ['memos','components','courses','reminders','plugins','about','settings']`（顺序即列表初始顺序）
  - `normalizeItems(saved, destinations): string[]`（过滤宿主不支持的、去重、确保首项 schedule、中间最多 3 个，空则 DEFAULT）
  - `toggle(items, id): {items, bumped: string|null}`（开到第 4 个时挤掉最后一个并返回被挤掉的 id；关到中间 0 个时拒绝，返回原 items）
  - `move(items, id, delta): string[]`（schedule 不动）
- manifest 关键值：`id: "app-layout"`、`name: "布局"`、`kind: "extension"`、`apiVersion: 12`、`minHostVersion: "0.7.11"`、`version: "1.0.0"`、`versionCode: 1`、`permissions: []`、`allowedHosts: ["cursimple-extension.invalid"]`、`extension.loginUrl/runUrl: "https://cursimple-extension.invalid/"`、`extension.layouts: [{"id":"bottom-bar","title":"底边栏","shell":"bottomBar"}]`、`extension.ui: {"type":"html","entry":"ui/index.html","settingsEntry":"ui/index.html"}`、`homepage` 指向仓库。

- [ ] **Step 1: 写失败测试**：`normalize keeps schedule first and at most three middle items`；`unknown or host-unsupported ids are dropped`；`turning on a fourth item bumps the last one`；`the last middle item cannot be turned off`；`move never displaces schedule`；`manifest declares api 12, layouts and no permissions`。
- [ ] **Step 2: 运行确认失败** `npm test`。
- [ ] **Step 3: 实现规则与页面**：分段控件「侧边栏 / 底边栏」（照 notify `bindSegments`）；底栏预览条；列表（课表、我的锁定；其余开关 + 上移/下移按钮 + 拖动手柄）；每次变更 `request('layout.apply', …)`，失败回滚并在页内状态条显示错误；进入页面先 `layout.current` 回显；无 `capabilities.layouts` 时整页显示「需要课简 0.7.11 或更新版本」且禁用操作；选侧边栏时显示提示「侧边栏的内容在课简「设置 → 侧边栏」里调整」。主题按宿主令牌映射，深浅色、320px、1.3 倍字号可用；按钮有框；不用原生 `<select>`。
- [ ] **Step 4: pack 与 release 工作流**：照 notify `pack.mjs`（生成 checksums、`dist/app-layout-v1.0.0.zip`、`dist/manifest.json` 含 `filename`）；release.yml 校验 tag == `v`+版本。
- [ ] **Step 5: README**（中英）：用途、安装、两种布局说明、主体侧边栏设置位置、开发与验证、许可与图标署名。
- [ ] **Step 6: 运行** `npm test && npm run pack`，`npm run preview` 用无头 Chrome 截浅色、深色、320px、`?legacy=1` 四张图检查。

### Task 8: 整体验证与交付

- [ ] **Step 1:** `gradle.properties` 本地改为 `app.versionCode=37`、`app.versionName=0.7.11`。
- [ ] **Step 2:** `./gradlew :core-kernel:testDebugUnitTest :core-data:testDebugUnitTest :core-plugin:testDebugUnitTest :feature-plugin:testDebugUnitTest :feature-schedule:testDebugUnitTest :app:testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` → 全部通过、lint 0 error。
- [ ] **Step 3:** 模拟器：装 APK，用「导入 ZIP」装 `dist/app-layout-v1.0.0.zip`，在组件设置页切底栏、改底栏项、切回；停用组件 → 侧边栏；再启用 → 底栏；删除 → 侧边栏且再装不自动恢复；设置 → 侧边栏的「换回侧边栏」。用无障碍树核对（模拟器画面是黑的，视觉需真机）。
- [ ] **Step 4:** 交付：`~/Desktop/cursimple-0.7.11-layout-arm64.apk` 与组件 ZIP；向用户列出未验证项（真机视觉、各厂商手势）。收录（components.json、收录页三语介绍、官网组件区）留到组件正式发布后再做。
