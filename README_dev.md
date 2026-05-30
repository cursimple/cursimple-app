# 课简（CurSimple）

一个基于 Kotlin 的 Android 课表应用「课简」，采用微内核架构，支持：

- Android 7.0（API 24）到 Android 16（targetSdk 36）
- `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 ABI splits + `universal` 通用包
- Compose 主界面
- Glance 桌面小组件
- 使用 manifest + WebView 的 JS 插件平台完成学校课表采集
- GitHub 注册表驱动的插件市场（[cursimple-plugins](https://github.com/cursimple/cursimple-plugins)）
- 课程提醒（系统闹钟 / 应用内闹钟双后端）
- GitHub Actions CI/CD

## 模块结构

- `app`：应用壳、依赖组装、入口页面、更新检查与下载镜像
- `core-kernel`：统一课表模型与核心协议
- `core-plugin`：插件 manifest、安装、组件、Web 会话模型、GitHub 注册表与运行门面
- `core-data`：DataStore 仓储
- `core-reminder`：课程提醒规则、计划与派发后端
- `feature-schedule`：课表页面与同步逻辑
- `feature-plugin`：插件市场 UI（GitHub 网格 + 安装）、WebView 会话
- `feature-widget`：桌面小组件与定时刷新

## 快速开始

### 1) 环境要求

- JDK 17
- Android SDK（含 `platforms;android-36`）

### 2) 配置统一签名（本地）

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

> 所有本地构建（Debug/Release）都强制使用这套签名。
> `.signing/`、`keystore.properties`、`*.jks`、`*.keystore`、`*.p12` 已加入 `.gitignore`，不要提交本地生成的 keystore。

### 3) 构建 Debug

```bash
./gradlew assembleDebug
```

Debug/CI 包的 `applicationId` 是 `com.x500x.cursimple.ci`，可与 Release 包 `com.x500x.cursimple` 共存安装；两者仍使用同一套签名材料。

### 4) 构建 Release（含 v7a/v8a/x86/x86_64/universal）

```bash
./gradlew assembleRelease
```

构建产物目录：

`app/build/outputs/apk/release/`

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

当前版本不再内置示例插件，也不会启动时自动安装旧 assets 插件。

## 插件市场

插件市场以 GitHub 仓库 [cursimple/cursimple-plugins](https://github.com/cursimple/cursimple-plugins) 作为注册表。市场列表来自 `plugin-stars-data` 分支的 `plugins-stars.json`，每个 entry 对应一个独立的插件仓库，并携带 stars、描述、头像和语言等展示信息。

- **应用内浏览**：插件 Tab 顶部的"插件市场"区域，以 2 列网格展示注册表里的所有仓库，显示名称、作者、stars、描述与最新 release 的 tag。点开有详情，可"安装"或"在 GitHub 查看"。
- **安装约定**：每个插件仓库需在 GitHub 上发布 Release，并上传至少一个 `*.zip` 资产。app 会通过 GitHub 镜像池访问 `https://github.com/{owner}/{repo}/releases/latest` 解析真实 release，再从 `releases/expanded_assets/{release}` 中选择第一个 `/releases/download/...zip` 资产；GitHub 源站也参与测速排序。GitHub 自动生成的 Source code 压缩包不会作为插件包。没有 release 资产时按钮显示"未找到版本"灰态。
- **网页管理**：注册表的增删通过 [cursimple-plugins](https://github.com/cursimple/cursimple-plugins) 仓库 `docs/` 目录下的静态站点 ([https://cursimple.github.io/cursimple-plugins/](https://cursimple.github.io/cursimple-plugins/)) 完成。两种登录路径：
  - 方式 A：点击"在 GitHub 编辑"按钮，直接跳转 GitHub 网页编辑器，权限完全交给 GitHub（非协作者会进入 fork & PR 流程）。
  - 方式 B：在页面内粘贴一个 [Fine-grained PAT](https://github.com/settings/personal-access-tokens/new)（仓库 `Contents: Read & Write`），直接增删并 commit。Token 只保存在浏览器 localStorage。
- 注册表仓库默认为 `cursimple/cursimple-plugins`，可在 应用 → 设置 → 插件 中改为自己 fork 的仓库。

## GitHub Actions

工作流文件：

- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`

- CI（PR / push `main`）：加载同一套签名材料，执行单测 + `assembleDebug`，上传可共存安装的 CI APK artifact
- Release（仅 push tag，如 `v1.0.0`）：加载同一套签名材料，执行 `assembleRelease`、上传 APK、发布 GitHub Release
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
