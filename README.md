# 课简（CurSimple）

一个基于 Kotlin 的 Android 课表应用「课简」，采用微内核架构，支持：

- Android 7.0（API 24）到 Android 16（targetSdk 36）
- `armeabi-v7a`、`arm64-v8a`、`universal` 多 ABI 构建
- Compose 主界面
- Glance 桌面小组件
- 使用 manifest + WebView 的 JS 插件平台完成学校课表采集
- GitHub Actions CI/CD

## 模块结构

- `app`：应用壳、依赖组装、入口页面、更新检查与下载镜像
- `core-kernel`：统一课表模型与核心协议
- `core-plugin`：插件 manifest、安装、组件、Web 会话模型与运行门面
- `core-data`：DataStore 仓储
- `feature-schedule`：课表页面与同步逻辑
- `feature-plugin`：插件管理与 WebView 会话
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

### 4) 构建 Release（含 v7a/v8a/universal）

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

插件通过 `manifest.json` 声明 `permissions`、`allowedHosts`、`entry`、`webEngine`、`components` 和运行限制。运行时默认使用系统 WebView，只暴露受控 JS `ctx` 对象，不暴露 Android 原生对象。

当前版本不再内置示例插件，也不会启动时自动安装旧 assets 插件。

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
