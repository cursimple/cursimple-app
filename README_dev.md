# 课简（CurSimple）

最新测试版为 [0.7.5（beta）](https://github.com/cursimple/cursimple-app/releases/tag/v0.7.5)，标签保持 `v0.7.5`，GitHub Release 使用 `prerelease=true`；旧版 `v0.7.4` 也标记为 Pre-release。

一个基于 Kotlin 的 Android 课表应用「课简」，采用微内核架构，支持：

- Android 7.0（API 24）到 Android 16（targetSdk 36）
- `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 ABI splits + `universal` 通用包
- Kotlin 2.3.21（版本由 `gradle/libs.versions.toml` 维护）、Compose 主界面
- 今日课程概览、全天课程时间线与冲突明细，备忘录全文搜索
- RemoteViews 桌面小组件（今日课表、下一节课、提醒、课程日历；待完成仅在有启用中的扩展组件时提供）
- 使用 manifest + WebView 的 JS 插件平台完成学校课表采集
- GitHub 注册表驱动的插件 / 组件多来源市场与私有仓库令牌认证
- 课程提醒与应用内精确闹钟，支持手动导出明天闹钟到系统时钟；保留已有系统时钟后端兼容
- GitHub Actions CI/CD

## 模块结构

- `app`：应用壳、依赖组装、入口页面、更新检查与下载镜像
- `core-kernel`：统一课表模型与核心协议
- `core-plugin`：插件 manifest、安装、组件、Web 会话模型、GitHub 注册表与运行门面
- `core-data`：DataStore 仓储
- `core-reminder`：课程提醒规则、计划与派发后端
- `feature-schedule`：课表页面与同步逻辑
- `feature-plugin`：插件与组件市场 UI（搜索、来源、已安装管理）、WebView 会话
- `feature-widget`：桌面小组件与定时刷新

## 快速开始

### 1) 环境要求

- JDK 17
- Android SDK（含 `platforms;android-36`）

### 2) 配置 Release 签名（本地）

Debug 构建和 JVM 单测不需要私有签名材料，会使用 Android 默认 debug 签名。构建 Release 包时必须配置以下签名值，缺失会在 release 打包任务开始前明确失败。

本地推荐使用根目录 `keystore.properties`（已加入 `.gitignore`，不要提交）：

```properties
CLASS_VIEWER_KEYSTORE_FILE=.signing/class-viewer.jks
CLASS_VIEWER_KEYSTORE_PASSWORD=replace-with-store-password
CLASS_VIEWER_KEY_ALIAS=replace-with-key-alias
CLASS_VIEWER_KEY_PASSWORD=replace-with-key-password
```

可参考 `keystore.example.properties`。也可以直接设置同名环境变量。Windows 绝对路径请使用 `/`，例如 `E:/keys/class-viewer.jks`，不要在 properties 文件里直接写未转义的 `\`。

若本地只有 base64 形式的 keystore，可先设置：

- `CLASS_VIEWER_KEYSTORE_BASE64`
- `CLASS_VIEWER_KEYSTORE_PASSWORD`
- `CLASS_VIEWER_KEY_ALIAS`
- `CLASS_VIEWER_KEY_PASSWORD`

然后执行：

```pwsh
. ./scripts/load-signing-env.ps1
```

脚本只会从当前环境变量解码 keystore，不会调用 `gh` 或访问 GitHub。

> 只有 Release 打包强制使用这套签名；Debug/CI 构建与单测不依赖私有 keystore。
> `.signing/`、`keystore.properties`、`*.jks`、`*.keystore`、`*.p12` 已加入 `.gitignore`，不要提交本地生成的 keystore。

### 3) 构建 Debug

```bash
./gradlew assembleDebug
```

Debug/CI 包的 `applicationId` 是 `com.x500x.cursimple.ci`，可与 Release 包 `com.x500x.cursimple` 共存安装。Debug/CI 包使用默认 debug 签名，不能用于覆盖安装正式 Release 包。

### 4) 构建 Release（含 v7a/v8a/x86/x86_64/universal）

```bash
./gradlew assembleRelease
```

构建产物目录：

`app/build/outputs/apk/release/`

发布工作流会用 `scripts/verify_release_signing.py` 校验五个 APK 的签名证书与已发布版本一致。使用本地临时密钥构建的包不能直接覆盖官网安装包，也不能通过这项发布校验；官方签名由 GitHub Actions 的签名 Secrets 提供。

### 5) 版本与发布渠道

版本号与渠道统一在 `gradle.properties` 维护，0.7.5 的发布配置为：

```properties
app.versionCode=31
app.versionName=0.7.5
app.releaseChannel=beta
```

Release 工作流校验标签与 `app.versionName` 一致，并从 `app.releaseChannel` 生成 GitHub Release 的预发布状态及更新渠道状态。`beta` 对应 `prerelease=true`，即使标签 `v0.7.5` 不含 `-` 也按测试版发布；版本后缀不能单独决定发布状态。下载入口固定为 [v0.7.5](https://github.com/cursimple/cursimple-app/releases/tag/v0.7.5)，应用内默认只检查正式版，开启测试版更新后才能接收该渠道。

## 0.7.5 功能与维护

| 功能 | 实现与边界 |
|---|---|
| 今日概览与课程时间线 | `feature-schedule` 的 `TodayOverview`、`TodayOverviewCard` 与 `TodayOverviewSheet`；默认仅在今天的日视图显示，可在显示设置关闭。展示当前 / 下一节课、进度、上下课倒计时、全天课程与冲突详情；与上课通知复用 `core-kernel` 的 `ScheduledCourseOccurrence`，缺少作息时提示补充 |
| 备忘录全文搜索 | `MemoSearch` 检索全部笔记的标题、完整正文及课程信息，包括其他笔记本和已完成笔记；清空查询恢复原筛选，顶部统计紧凑横排 |
| 课程日历小组件 | `CalendarWidgetReceiver` 等提供周课表 / 月历、翻页、回到当前日期和日期深链，展示课程、考试、事务及休 / 班标记 |
| 待完成小组件 | `PendingTaskWidget` 读取扩展组件未完成内容，显示开始 / 截止时间和紧急状态，点击回到来源组件；`ComponentWidgetAvailability` 控制提供者与选择器，仅有启用中的扩展组件时开放 |
| 手动导出系统时钟 | `SystemClockExport` 仅处理明天已启用、未被假日或日期静音规则排除的应用内闹钟；按设备时区的同一分钟合并，避免系统时钟把明天的钟点解释为今天，未到可写时间时提示稍后再来。全部创建请求送出且无待写项目时可选择静音课简明天的闹钟，列表可取消静音 |
| 假日与静音策略 | `AlarmDayPolicy` 供排程与响铃复核；默认跳过假日，单个闹钟的 `allowOnHoliday` 可绕过假日跳过，手动静音日期仍优先，调休 / 补课日照常排程。例外随 Intent、贪睡及重建传递；被跳过的闹钟不发预告，假期前晚可发静默说明 |
| 响铃界面 | `AlarmRingingActivity` 展示大时钟、日期、课程与主题配色，滑动关闭和按钮延后；修复滑到底仍无法关闭 |
| 共享界面控件 | `feature-plugin` 的 `ui/` 提供搜索框、顶部按钮、信息块和确认框，市场与备忘录复用；安装弹窗固定大小、内部滚动，权限和校验默认折叠 |

系统时钟接口只发送 `AlarmClock.ACTION_SET_ALARM` 创建请求，不能证明系统已成功保存。用户需要打开时钟核对；导出后不会自动跟随调课、停课或假日变化。课简的日期静音与假日例外只影响自身提醒，已写入系统时钟的闹钟需在那里调整。

本版同时修复小组件编辑 / 删除课程后的重复和旧内容回流、跨周移入课程的地点及假日 / 静音策略、组件页面滑动误开侧边栏，以及来源 / 账号切换后的迟到请求与旧版本缓存。今日概览及小组件的无课、假日、下课和空任务提示同一天保持稳定。

## 插件平台

更详细的面向维护者说明见：

- [docs/plugin-system.md](docs/plugin-system.md)

插件以 zip 包安装，至少包含 `manifest.json` 和入口 JS。入口推荐导出：

```js
export async function run(ctx) {
  ctx.schedule.addCourse({
    title: "高等数学",
    dayOfWeek: 1,
    startNode: 1,
    endNode: 2,
    weeks: [1, 2, 3]
  });

  return ctx.schedule.commit({ termId: ctx.term.id });
}
```

插件通过 `manifest.json` 声明 `permissions`、`allowedHosts`、`entry`、可选 `startUrl`/`userAgent`、`webEngine`、`components` 和运行限制。运行时默认使用系统 WebView，只暴露受控 JS `ctx` 对象；入口脚本可用 `ctx.web.setUserAgent()` 自行决定当前会话 UA。

宿主当前接口版本为 **4**（`PluginApiVersion.CURRENT`）；导课插件继续兼容 API 2，扩展组件（`kind: "extension"`）最低 API 3，API 4 支持自带的登录、设置与内容页面及通用 UI 桥接。宿主承接后台同步、新内容与截止提醒、登录失效提示、可选的课表事务写入和内容详情联动，组件单独安装和更新。

雨课堂本地 v1.2.0 包使用 API 4，需独立导入 ZIP；公有市场可用版本以其组件仓库实际 Release 为准。

当前版本不再内置示例插件，也不会启动时自动安装旧 assets 插件。

## 插件市场

插件与组件各有独立的来源列表，默认分别是 [cursimple/cursimple-plugins](https://github.com/cursimple/cursimple-plugins) 和 [cursimple/cursimple-components](https://github.com/cursimple/cursimple-components)。插件读取 `plugin-stars-data/plugins-stars.json`，组件读取 `component-stars-data/components-stars.json`。每个 entry 对应一个独立仓库，可携带学校别名和 `kind`；旧插件清单中的 `kind: "extension"` 仍归入组件页。

- **应用内浏览**：插件与组件页各有「已安装」「市场」入口和直接搜索，可搜索名称、描述、学校别名及来源。条目显示来源、版本、安装状态和更新入口，公有默认来源显示「公有仓库」。
- **详情与安装预检**：详情不再显示分类切换，简介和仓库信息单独成块；手动刷新与安装前重新查版本。安装弹窗固定大小、内部滚动，优先展示简介、来源与真实包大小，权限和校验默认折叠。`PluginMarketCatalogUi` 在已知总大小达到 **5 MiB** 时显示实际下载字节、进度和百分比；总大小未知时，已下载达到阈值后显示字节数与不定进度。
- **额外来源与私有仓库**：设置 → 插件内可添加或移除两个来源列表，支持 `owner/repo` 和完整 GitHub 链接；新增来源与默认公有来源合并加载，也支持直接添加单个插件的 Release 仓库。同页用细粒度 GitHub 令牌登录，只需选定仓库的 `Contents: Read-only`，令牌经 Keystore 加密并排除备份；账号内容和附件直接走 GitHub API，退出登录后清掉账号市场缓存。
- **来源隔离与去重**：各来源独立加载和显示错误，一个来源不可用仍保留其他结果；重复仓库按来源顺序去重，移除来源二次确认，已安装项目保留。换账号同样清理账号缓存，迟到结果不能覆盖新账号或新来源的状态。
- **可选设备码登录**：构建时通过 `-Pgithub.oauthClientId=<Client ID>` 或 `CURSIMPLE_GITHUB_OAUTH_CLIENT_ID` 配置启用 device flow 的 GitHub OAuth App，可增加网页授权登录按钮；默认无需此配置即可使用令牌登录。
- **安装约定**：每个插件 / 组件仓库需在 GitHub 上发布 Release，并上传 `manifest.json` 与 `filename` 指向的包。公有未登录路径先读取 `https://github.com/{owner}/{repo}/releases/latest/download/manifest.json`，再下载 `https://github.com/{owner}/{repo}/releases/latest/download/{filename}`；列表优先使用 jsDelivr，镜像池和 GitHub 源站按测速结果作为后续候选。私有来源及账号附件通过认证 GitHub API 读取，不经公共镜像。GitHub 自动生成的 Source code 压缩包不会作为插件包。没有 manifest 或 filename 时按钮显示"未找到版本"灰态。
- **网页管理**：注册表的增删通过 [cursimple-plugins](https://github.com/cursimple/cursimple-plugins) 仓库 `docs/` 目录下的静态站点 ([https://cursimple.github.io/cursimple-plugins/](https://cursimple.github.io/cursimple-plugins/)) 完成。两种登录路径：
  - 方式 A：点击"在 GitHub 编辑"按钮，直接跳转 GitHub 网页编辑器，权限完全交给 GitHub（非协作者会进入 fork & PR 流程）。
  - 方式 B：在页面内粘贴一个 [Fine-grained PAT](https://github.com/settings/personal-access-tokens/new)（仓库 `Contents: Read & Write`），直接增删并 commit。Token 只保存在浏览器 localStorage。
- 插件与组件来源列表可在**设置 → 插件**分别管理，默认公有来源可以和自己的 fork 或独立仓库同时使用。

## 日期数据

和日期有关的数据都不写死在代码里，App 回到前台时静默联网刷新（一小时内不重复），取不到时沿用本地缓存：

- **放假安排**：取自公开维护的 [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn)，每次覆盖当年往后两年，还没发布的年份跳过。
- **节日与节气**：`data/calendar/cn-festivals.json`，列出往后几十年每一天是什么节日、节气。App 从本仓库 `main` 分支经下载镜像取回。
  要延长年份或调整节日，改 `scripts/gen_cn_calendar.py` 后重新生成（依赖寿星天文历 `pip install sxtwl`），提交到 `main` 即可，不用发版：

  ```bash
  python3 scripts/gen_cn_calendar.py 2024 2060
  ```

## 发布说明与更新公告

- 0.7.5 的公告路径为 `docs/release-notes/v0.7.5.md`；README 新截图统一引用 `docs/release-notes/images/v0.7.5/` 下的 `agenda.png`、`memo-search.png`、`plugin-market.png`、`calendar-widget.png`、`pending-widget.png`、`system-clock.png` 与 `alarm-ringing.png`，使用演示数据，由发布流程准备这些文件。
- 每个版本的说明写在 `docs/release-notes/v<版本>.md`，配图放在 `docs/release-notes/images/v<版本>/`，公告里按 `raw.githubusercontent.com/.../v<版本>/...` 引用。
- 带图的 `##` 小节会变成翻页公告的一页亮点，不带图的小节归到最后一页的文字清单。
- 发版前可以在真机上预览：把 `.md` 和图片放进应用私有目录的 `release-preview`，到「设置 → 高级诊断 → 数据与文件 → 预览本地更新公告」查看。
- 测试包也支持附带本地公告：在 `app/src/debug/assets/release-preview/` 放入 `next-local.md` 和图片，安装后从同一入口离线查看。私有目录里的自定义稿优先；这些素材仅进入 Debug 包，不随正式版本打包。

## GitHub Actions

工作流文件：

- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`

- CI（PR / push `main`）：执行单测 + `assembleDebug`，上传可共存安装的 CI APK artifact
- Release（仅 push tag，如 `v0.7.5`）：校验标签与版本一致，加载同一套签名材料，执行 `assembleRelease`、上传 APK、生成 `update.json`；按 `app.releaseChannel` 生成发布与更新渠道状态，0.7.5 的 `beta` 渠道使用 `prerelease=true`
- push `v*` tag 只触发 Release workflow，不触发 CI workflow
- 工作流通过 GitHub Actions Secrets 直接注入签名材料，随后用 `scripts/load-signing-env.ps1` 解码到 runner 临时目录

### CI/CD 需预置的仓库配置

- Secrets：
  - `CLASS_VIEWER_KEYSTORE_BASE64`
  - `CLASS_VIEWER_KEYSTORE_PASSWORD`
  - `CLASS_VIEWER_KEY_ALIAS`
  - `CLASS_VIEWER_KEY_PASSWORD`

GitHub Secrets 中的 keystore 必须和本地 `keystore.properties` 指向的 keystore 是同一份。可在本地用 `pwsh` 从 `keystore.properties` 同步：

```pwsh
$env:GH_TOKEN = $env:GH_TOKEN_class_viewer
$props = @{}
foreach ($line in Get-Content -LiteralPath .\keystore.properties) {
    if ($line -match '^\s*(?<key>[^#][^=]*)=(?<value>.*)$') {
        $props[$Matches.key.Trim()] = $Matches.value.Trim()
    }
}

function Set-GhSecretValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    gh secret set $Name --repo cursimple/cursimple-app --body $Value
}

Set-GhSecretValue -Name 'CLASS_VIEWER_KEYSTORE_BASE64' -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes($props.CLASS_VIEWER_KEYSTORE_FILE)))
Set-GhSecretValue -Name 'CLASS_VIEWER_KEYSTORE_PASSWORD' -Value $props.CLASS_VIEWER_KEYSTORE_PASSWORD
Set-GhSecretValue -Name 'CLASS_VIEWER_KEY_ALIAS' -Value $props.CLASS_VIEWER_KEY_ALIAS
Set-GhSecretValue -Name 'CLASS_VIEWER_KEY_PASSWORD' -Value $props.CLASS_VIEWER_KEY_PASSWORD
```
