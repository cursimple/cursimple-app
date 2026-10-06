# 开发说明

[English](docs/development.en.md)

课简按模块组织，学校和服务平台的协议由独立插件或组件实现。主程序负责通用安装、权限、存储、提醒和运行容器。

## 构建与验证

使用 JDK 17、Android SDK platform 36。通过 `local.properties` 或 `ANDROID_HOME` 配置 SDK。

```sh
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest lintDebug
node --test site/tests/*.test.cjs
node site/check-links.cjs
python3 -m unittest discover -s scripts/tests
```

测试包使用 `com.x500x.cursimple.ci`，与正式版 `com.x500x.cursimple` 独立安装、独立保存数据。输出位于 `app/build/outputs/apk/debug/`，通用包支持全部配置的架构。

会修改设备状态的仪器测试需在独立模拟器上显式传入对应 `*Qa=true`；小组件测试还需授予绑定权限。测试会恢复临时设置，不应在个人日用设备上运行。

## 模块

| 模块 | 职责 |
|---|---|
| `app` | 依赖组装、导航、通知、更新与下载 |
| `core-kernel` | 通用模型、课程和日期解析、纯逻辑协议 |
| `core-data` | 课表、笔记、偏好与通知队列持久化 |
| `core-plugin` | 包校验、安装、权限与 GitHub 访问 |
| `core-reminder` | 提醒规则、计划、派发和设备授权引导 |
| `feature-schedule` | 课表、笔记、课程编辑与导入流程 |
| `feature-plugin` | 市场、组件页面与受限 WebView 运行时 |
| `feature-widget` | 桌面小组件、绘制与定时刷新 |

开发版插件接口为 **API 9**。兼容性由 `PluginApiVersion` 和组件清单决定，与软件版本名分开。平台协议保留在独立组件中，接口详见[插件开发指南](docs/plugin-system.md)。

## 签名与发布

Debug 和 JVM 测试不需要私有签名。Release 构建需要 `keystore.example.properties` 中的四个值，通过已忽略的 `keystore.properties` 或同名环境变量提供：

- `CLASS_VIEWER_KEYSTORE_FILE`
- `CLASS_VIEWER_KEYSTORE_PASSWORD`
- `CLASS_VIEWER_KEY_ALIAS`
- `CLASS_VIEWER_KEY_PASSWORD`

properties 路径使用正斜杠。`scripts/load-signing-env.ps1` 可从环境中的 `CLASS_VIEWER_KEYSTORE_BASE64` 解码，不会联网取凭据。证书、密码和本地配置不要提交。

```sh
./gradlew :app:assembleRelease
```

版本和渠道在 `gradle.properties` 的 `app.versionCode`、`app.versionName`、`app.releaseChannel` 中维护。匹配的 `v<app.versionName>` 标签触发发布。渠道决定 GitHub 的预发布状态，不靠标签是否含 beta 后缀判断。发布检查会核对五个 APK 的签名；本地临时签名不能替代已发布的证书。

| 工作流 | 用途 |
|---|---|
| `android-ci.yml` | PR 和 main 分支构建、测试 |
| `android-release.yml` | 签名包、发布信息、正式／测试版更新清单 |
| `pages.yml` | 官网验证与部署 |

## 公告和日期数据

版本公告放在 `docs/release-notes/v<version>.md`，图片在对应 `images/v<version>/`。带图片的二级标题拆成亮点页，其余文字进入最后一页。

隐藏入口优先读私有 `release-preview` 稿件，也支持 Debug 专用的 `app/src/debug/assets/release-preview/next-local.md`；正式包不包含这些调试素材。更新检查静默运行、用红点提示；测试版在检查弹窗或设置中选择。公告和符合网络条件的图片会预加载，计费网络只预取正文。详见[网络与缓存](docs/networking.md)。

日期数据前台刷新间隔一小时，失败沿用缓存；放假数据覆盖已发布的当年和往后两年。节日数据可重新生成：

```sh
python3 -m pip install sxtwl
python3 scripts/gen_cn_calendar.py 2024 2060
```

数据更新不必发 APK。课表显示可从周日开始，但已存教学周仍固定以周一为锚点。

## 维护约定

代码注释使用精简英文，解释约束和不直观的行为，不记录“修改了什么”。修改经过写提交说明，用户变化写公告。示例使用通用机构和账号；本地化产品文案和时区等技术标识保留正确语义。

删除代码前核对调用、反射、清单注册、持久化格式和组件桥接。仍用于旧数据的兼容逻辑保留。缓存、临时报表和构建产物不进入仓库。

其他说明：[官网](site/README.md)、[今日概览](docs/today-overview.md)、[假日闹钟策略](docs/holiday-alarm-policy.md)。
