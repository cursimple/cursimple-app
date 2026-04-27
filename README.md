# 课表查看（Class Schedule Viewer）

一个基于 Kotlin 的 Android 课表查看应用，采用微内核架构，支持：

- Android 7.0（API 24）到 Android 16（targetSdk 36）
- `armeabi-v7a`、`arm64-v8a`、`universal` 多 ABI 构建
- Compose 主界面
- Glance 桌面小组件
- 使用 QuickJS 的 JS 插件完成登录到课表获取全流程
- GitHub Actions CI/CD

## 模块结构

- `app`：应用壳、依赖组装、入口页面、插件资产
- `core-kernel`：微内核协议（插件目录、执行器、统一课表模型）
- `core-js`：QuickJS 执行器与受控 Host 桥接
- `core-data`：DataStore 仓储
- `feature-schedule`：课表页面与同步逻辑
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

Debug/CI 包的 `applicationId` 是 `com.kebiao.viewer.ci`，可与 Release 包 `com.kebiao.viewer` 共存安装；两者仍使用同一套签名材料。

### 4) 构建 Release（含 v7a/v8a/universal）

```bash
./gradlew assembleRelease
```

构建产物目录：

`app/build/outputs/apk/release/`

## JS 插件协议

更详细的面向维护者说明见：

- [docs/plugin-system.md](docs/plugin-system.md)

每个插件必须导出 3 个函数：

- `login(ctx, input)`：登录并返回会话对象
- `fetchSchedule(ctx, session, term)`：获取原始课表
- `normalize(raw)`：转换为标准课表 JSON

标准课表 JSON 示例：

```json
{
  "termId": "2026-spring",
  "updatedAt": "2026-04-25T08:00:00+08:00",
  "dailySchedules": [
    {
      "dayOfWeek": 1,
      "courses": [
        {
          "id": "c1",
          "title": "高等数学",
          "teacher": "张老师",
          "location": "A101",
          "weeks": [1, 2, 3],
          "time": {
            "dayOfWeek": 1,
            "startNode": 1,
            "endNode": 2
          }
        }
      ]
    }
  ]
}
```

示例插件位于：

- `app/src/main/assets/plugins/index.json`
- `app/src/main/assets/plugins/demo-campus.js`

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

    gh secret set $Name --repo x500x/ClassScheduleViewer --body $Value
}

Set-GhSecretValue -Name 'CLASS_VIEWER_KEYSTORE_BASE64' -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes($props.CLASS_VIEWER_KEYSTORE_FILE)))
Set-GhSecretValue -Name 'CLASS_VIEWER_KEYSTORE_PASSWORD' -Value $props.CLASS_VIEWER_KEYSTORE_PASSWORD
Set-GhSecretValue -Name 'CLASS_VIEWER_KEY_ALIAS' -Value $props.CLASS_VIEWER_KEY_ALIAS
Set-GhSecretValue -Name 'CLASS_VIEWER_KEY_PASSWORD' -Value $props.CLASS_VIEWER_KEY_PASSWORD
```
