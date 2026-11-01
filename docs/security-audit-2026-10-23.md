# 仓库安全审计与加固（2026-10-23）

## 范围与结论

基线为 `pre` 分支 `4fe13ee`。检查 Android 主程序、插件/组件包、课表导入、WebView 与原生桥、文件提供器、更新/下载、备份、官网、GitHub Actions/Netlify 清理及依赖供应链。本次改动在本地 pre 工作区，未发布、未部署、未合并 main。

发现并处理 **51 个不同 CVE 的受影响依赖版本**：46 个来自 AGP 构建工具链的传递依赖，1 个来自 Kotlin Gradle 插件，4 个来自 APK 内置旧 jQuery。受影响版本命中不等于已证明每个 CVE 在本项目中可利用，尤其构建 classpath 的问题不能描述成 APK 运行时远程漏洞。

最终在线 OSV 扫描：**369 个已解析 Maven 坐标，0 命中；2 个内置 JavaScript 库，0 命中**。视频工具的 npm 锁文件审计为 0。扫描为本次查询时点的结果，不代表不存在未知漏洞。

## 依赖与供应链修复

| 依赖 | 原版本 | 加固版本 / 处理 | 位置 |
| --- | --- | --- | --- |
| Kotlin Gradle/Compose/serialization 插件 | 2.3.21 | 2.4.21 | 构建；CVE-2026-53914，不安全构建缓存元数据反序列化 |
| R8 | AGP 默认 | 9.1.57 | 构建；满足 Kotlin 2.4 所需 R8 最低版本 |
| Netty（io.netty 模块，排除独立版本序列的 tcnative） | 4.1.110.Final | 4.1.139.Final | AGP 构建 classpath |
| commons-compress | 1.21 | 1.28.0 | AGP 构建 classpath |
| jose4j | 0.9.5 | 0.9.7 | AGP 构建 classpath |
| Bouncy Castle jdk18on | 1.79 | 1.86 | AGP 构建 classpath |
| jdom2 | 2.0.6 | 2.0.6.1 | AGP 构建 classpath；XXE |
| jQuery | 1.12.4 | 3.7.1 | APK 内置旧站点兼容脚本 |
| jQuery Migrate | 1.4.1 | 3.6.0 | 与 jQuery 3.x 对应的兼容层 |

旧 jQuery 命中 CVE-2015-9251、CVE-2019-11358、CVE-2020-11022、CVE-2020-11023，涉及跨站脚本及原型污染。删除了旧资产，新增库保留上游许可证；不启用 Migrate 的不安全旧 HTML 预过滤补丁。脚本仅在原有白名单站点且页面没有 jQuery 时注入，不替换学校服务端自己加载的库。

构建工具链替换限定在 `buildscript` classpath，未对 App 运行时盲目全局 force。保留 AGP 8.13.2 的既有 DSL；以后升级 AGP 时应检查并逐步移除临时 override。

Gradle 8.14.5 distribution 增加官方 SHA-256 校验，wrapper JAR 替换为该版本的官方文件。旧 JAR 与官方版本不同，本次未发现证据表明它遭篡改。

- Distribution SHA-256：`6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854`
- Wrapper JAR SHA-256：`7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`

JitPack 限定到现有依赖组，避免普通 Maven 坐标意外从第三方仓库解析。所有 workflow 的外部 Action 固定提交 SHA；checkout 不持久保存 Git 凭证。普通 Android CI 不再注入正式签名密钥，正式发布流程仍执行证书指纹核验。发布 feed 的 Git 写入使用步骤内显式凭证，兼容上述 checkout 加固。

## 代码边界加固

| 边界 | 问题 / 触发条件 | 修复 |
| --- | --- | --- |
| 私有文件 DocumentsProvider | 手工构造文档 ID、符号链接或自定义 call 可能绕过预期路径/授权边界 | 严格 ID 分段、canonical 根约束、逐级拒绝符号链接、最终打开 `O_NOFOLLOW`；自定义 call 校验 content URI、authority 与写授权；禁止创建链接、修改根目录和设置非 owner 权限 |
| 插件与组件 ZIP | 目录 entry 的内容/数量未计入上限，重复路径或 file-as-parent 产生歧义 | 对全部 entry 计数、读取时限制展开量，限制压缩包/元数据/路径/JSON 深度；拒绝冲突路径与异常 ID |
| 插件与组件安装 | 重装先删除旧文件，落盘/registry 失败可能破坏可用版本 | 每次安装独立 UUID staging，完成后切 registry；失败只清理新目录；组件清理限定相同 ID、来源及安装根 |
| 插件存储读取 | 部分 schema/timing 与 storagePath 未统一约束 | 统一安全路径解析与根约束，限制文件和 JSON 大小/深度 |
| WakeUp / JSON / ICS | 超大文本、深层 JSON、ZIP 展开、过量课程或 RRULE 可能消耗内存/CPU | 默认导入 4 MiB、JSON 深度 64、课程 2000、展开 occurrence 20000；ICS 限制行长/行数及递归参数，节次限定合法区间 |
| AISchedule 规则 | 任意 JS 可能长期执行；手动反转义会破坏结果 | 15 秒超时返回并销毁 WebView、禁文件/content/网络访问；按 JSON 字符串规则解码，校验输入与输出大小 |
| 下载、更新、WebDAV | 仅预分配限制或无界 read，未知 Content-Length 可继续堆积 | 字节/文本下载实际流量上限 64 MiB；更新 feed/文本 4 MiB；WebDAV 下载 50 MiB。APK 文件流下载保留原有流式逻辑及哈希校验 |
| 旧站点导航兼容层 | 导航响应 `readBytes()` 无界缓存 | 声明长度和实际读取双重限制 8 MiB；失败交回 WebView 处理 |
| WebView 与复用池 | 文件/content/mixed content 默认开关、旧 client/UA/JS 状态可能遗留 | 统一禁本地访问与混合内容，开启可用的 Safe Browsing；复用时释放 bridge/client/listener、重置 UA 和页面状态 |
| 系统备份与设备迁移 | WebView cookie/profile 或文件提供器启用状态可能随备份迁移 | cloud/device transfer 均排除 `app_webview/` 和相关启用偏好，保留已有凭证排除 |
| 官网更新日志 | Markdown URL 未校验协议，生成可执行链接；文本规则可能再次修改已生成 HTML 属性 | 仅接受 HTTP(S) 无凭证 URL，先解析原始 URL、统一转义，再隔离生成片段；拒绝 javascript/data/vbscript 等协议 |
| Netlify pre 清理 | 宽松部署识别或缺少生产保护可能误删 | 校验 immutable deploy URL、site ID、pre/context/ready 状态与时间；分页读取后删除；每次删除前重查生产/locked/其他分支保护，元数据或 API 异常即失败 |

保留 main/pre 发布隔离：Pages 只在 main 发布；pre 的测试 release 不进入 App 或官网更新选择。Netlify 保留“新 pre 部署就绪后物理删除旧 pre 部署”的行为，保护生产、当前及其他分支部署。

## 持续扫描

新增 `.github/workflows/dependency-security.yml` 和 `.github/dependabot.yml`：PR、main/pre 依赖变化、每周及手动触发；Dependabot 更新目标为 pre。

`scripts/security-dependencies.init.gradle` 使用真实解析结果收集各模块 release/debug runtime、JVM/Android 测试 runtime 与 buildscript classpath，输出坐标及 `scopes`。`scripts/audit_dependencies.py` 调用 OSV，忽略已撤销公告；空清单、解析失败、网络错误不能被当作通过。

`scripts/vendored-dependencies.json` 记录内置库版本、官方来源和 SHA-256；CI 先验证实际文件哈希，再执行 OSV 查询。审计 workflow 分别记录 Maven、内置库与 npm 结果，上传证据后统一判失败，避免前一项命中后漏跑后续审计。

## 验证记录

- Python 脚本：40 项通过，包括 Netlify 生产保护/分页/竞态重新校验、依赖 API 错误和内置文件哈希变化。
- 官网 Node：26 项通过；324 个本地资源与锚点检查通过。
- 实际 Chromium 浏览器：注入恶意公告验证；恶意链接协议被拒绝，HTML 作为文字显示，0 个注入图片，正常 HTTPS 与相对 URL 保留。
- Android JVM：安全加固初次验证 1934 项通过；后续 pre 整体回归扩展为 1975 项，0 失败、0 错误、0 跳过。所有模块 Debug Lint 共 0 错误、451 条警告与 21 条提示（主 App 167 条警告与 7 条提示），未新增 baseline 掩盖问题。Debug APK 与两个 AndroidTest APK 构建通过，后续回归见 `pre-regression-2026-10-23.md`。
- Release APK：R8 优化、资源压缩及 Release Lint Vital 全部通过，重新生成 4 个 ABI 与 universal APK；arm64 Release 包已实际安装到模拟器，完成首次启动并进入课表，crash buffer 无记录。本地构建不等同于远端正式发布验证。
- 真实 Android 模拟器：API 34、AVD `CurSimpleOverlapQa34`；10 项仪器测试全部通过，覆盖提供器正常读写、路径穿越/符号链接/根目录修改拒绝、异常 URI、自定义 call、禁用状态、AISchedule JSON 转义和 15 秒死循环超时、凭证加密/坏密文处理、WebView 本地访问限制及新 jQuery 安全补丁。App 冷启动进入课表，已查看截图，crash buffer 无记录。
- 无授权 shell UID 访问实际导出 DocumentsProvider：收到权限拒绝，未返回数据行。
- 工作流 YAML 语法、`git diff --check` 通过。
- 最终工作区 1292 个受跟踪及未忽略的新文件高置信度密钥模式扫描无命中；全部 Git 引用历史共 459849 行 patch 的 GitHub PAT / AWS AKIA / 私钥头 / Netlify token 模式无命中。不是完整熵分析或密钥有效性验证。

## 可复现命令

```sh
./gradlew -I scripts/security-dependencies.init.gradle securityDependencyInventory
python3 -B scripts/audit_dependencies.py build/reports/security/dependencies.json --output build/reports/security/osv.json
python3 -B scripts/audit_dependencies.py scripts/vendored-dependencies.json --output build/reports/security/vendored-osv.json
npm --prefix promo-video audit --package-lock-only --audit-level=low
python3 -B -m unittest discover -s scripts/tests -v
node --test site/tests/*.test.cjs
node site/check-links.cjs
./gradlew testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest :feature-plugin:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

扫描产物在 `build/reports/security/`，包含修复前后依赖证据、Python/Node/Android 构建和仪器测试日志、Debug/Release 启动截图及 crash buffer；JVM XML 与 Lint HTML 在各模块的 `build/`。这些是生成文件，不需要作为源码提交。

## 限制与剩余风险

1. OSV/npm 只覆盖公告库已知且可按版本匹配的漏洞；未执行完整 SAST/DAST、模糊测试、全熵密钥扫描或独立渗透测试。清单不含设备系统与 WebView Chromium、自托管学校站点、Netlify 服务端的实现。Android WebView/OS 安全补丁需由设备更新提供。
2. 插件/组件仍是具有声明权限的可执行扩展；保留现有未签名包兼容行为。自签名的 checksum/signature 不等于发布者已受信。JS host allowlist 不是完整隔离沙箱；同 host 的 WebView cookie origin 仍可能共享。
3. 文件提供器新增了路径和最终文件链接检查，但未把所有目录操作改为基于目录 FD 的 openat；有本地并发改写祖先目录能力时，仍存在 TOCTOU 窗口。仪器测试覆盖同 UID 直接调用及 shell 无授权拒绝，未覆盖所有真实跨 UID URI grant 组合。
4. AISchedule 超时验证证明调用能返回并执行销毁，不等于对任意恶意 JS 的内存/CPU 实施操作系统级硬配额。第三方规则应来自可信来源。
5. jQuery 3.x/Migrate 通过基础 DOM、原型污染和 HTML 预过滤回归测试；未登录全部学校系统验证历史页面。学校服务端自行加载旧库不在本次资产替换控制范围内。
6. Netlify 清理在本地模拟 API 场景验证，未读取真实 Netlify secret、未执行远端删除或部署。最终发布签名和 GitHub/Netlify Actions 需推送到 pre 后由仓库环境运行验证；本次本地 release 构建不代表正式签名发布完成。
7. 现有非阻断 Lint 警告与 Kotlin warning 仍需后续维护，未通过新建 baseline 或全局 suppress 隐藏本次安全错误。

## 公告与上游依据

- [JetBrains 已修复安全问题](https://www.jetbrains.com/privacy-security/issues-fixed/)、[Kotlin CVE-2026-53914 / GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp)。
- [Android Kotlin 与 D8/R8 支持矩阵](https://developer.android.com/build/kotlin-support)。
- [OSV API](https://google.github.io/osv.dev/api/)、[GitHub Advisory Database](https://github.com/advisories)。下表 CVE 来自实际解析坐标的在线 OSV 公告及其别名。
- [jQuery 3.5.0 官方安全修复说明](https://blog.jquery.com/2020/04/10/jquery-3-5-0-released/)、[jQuery Migrate 官方兼容说明](https://github.com/jquery/jquery-migrate)。
- [Gradle Wrapper 校验](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification)。
- [Netlify OpenAPI](https://open-api.netlify.com/swagger.json)，清理使用 site 范围的 deployment GET/DELETE。

## 构建工具链 CVE 明细

下面为替换 AGP 传递依赖前的 47 条“坐标 × 公告”命中，去重后为 **46 个公告 / 46 个 CVE**。同一 CVE 可能出现在多个 Netty 模块中。Kotlin 单独的 1 个和 jQuery 的 4 个不在此表中。

| 构建依赖 | CVE |
| --- | --- |
| `io.netty:netty-codec` | CVE-2025-58057, CVE-2026-42583, CVE-2026-59901 |
| `io.netty:netty-codec-http` | CVE-2025-58056, CVE-2025-67735, CVE-2026-33870, CVE-2026-41417, CVE-2026-42580, CVE-2026-42581, CVE-2026-42584, CVE-2026-42585, CVE-2026-42587, CVE-2026-50020, CVE-2026-55831, CVE-2026-55833, CVE-2026-56745, CVE-2026-56746, CVE-2026-59898, CVE-2026-59899, CVE-2026-59903, CVE-2026-59921 |
| `io.netty:netty-codec-http2` | CVE-2025-55163, CVE-2026-33871, CVE-2026-42587, CVE-2026-47244, CVE-2026-48043, CVE-2026-50560, CVE-2026-56819, CVE-2026-59900 |
| `io.netty:netty-common` | CVE-2024-47535, CVE-2025-25193 |
| `io.netty:netty-handler` | CVE-2025-24970, CVE-2026-44249, CVE-2026-45416, CVE-2026-50010, CVE-2026-75595, CVE-2026-75596 |
| `io.netty:netty-handler-proxy` | CVE-2026-42578 |
| `org.apache.commons:commons-compress` | CVE-2024-25710, CVE-2024-26308 |
| `org.bitbucket.b_c:jose4j` | CVE-2024-29371 |
| `org.bouncycastle:bcpkix-jdk18on` | CVE-2026-5588 |
| `org.bouncycastle:bcprov-jdk18on` | CVE-2025-14813, CVE-2026-0636, CVE-2026-13506, CVE-2026-8763 |
| `org.jdom:jdom2` | CVE-2021-33813 |
