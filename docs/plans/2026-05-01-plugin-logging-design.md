# 插件详细日志系统设计

日期：2026-05-01

范围：插件安装、内置插件初始化、插件市场下载与预检、工作流执行、网页登录回传、HTTP 请求、同步结果与失败诊断。

## 1. 目标

- 将插件相关行为纳入统一日志系统，便于通过现有“导出日志”能力排查问题。
- 日志要足够详细，能定位失败发生在哪个插件、哪个流程阶段、哪个工作流步骤。
- 失败日志必须包含详细诊断信息：错误消息、异常类型、堆栈、耗时和安全上下文。
- 日志不得泄露密码、Cookie、token、请求体、响应体正文等敏感内容。

## 2. 推荐方案

采用集中式 `PluginLogger` + 关键链路埋点。

`PluginLogger` 位于 `core-plugin`，负责：

- 统一 tag 与事件名。
- 输出 `key=value` 形式的结构化文本。
- URL 查询参数脱敏。
- 异常堆栈输出。
- 统一耗时字段。
- 对集合和文本内容只记录数量、长度或摘要，不记录正文。

相比在各处直接写 `Log.i/w/e`，集中式工具能保证格式一致，也能让后续扩展文件日志或过滤导出时有稳定入口。

## 3. 日志内容边界

允许记录：

- 插件 ID、名称、版本、来源、是否内置。
- 工作流 ID、步骤 ID、步骤类型、步骤序号。
- URL，但敏感查询参数值必须替换为 `***`。
- HTTP 方法、状态码、耗时、请求头字段数量、响应头字段数量。
- Web 会话 startUrl/finalUrl、允许域名数量、Cookie 数量、localStorage 字段数量、sessionStorage 字段数量、capturedFields 数量、htmlDigest。
- 插件包字节数、校验结果、签名结果。
- 课表课程数量、消息数量、推荐提醒数量。
- 失败阶段、错误消息、异常类型、异常堆栈。

禁止记录：

- 密码。
- Cookie 值。
- token、ticket、session、authorization、credential 等敏感字段值。
- 请求体正文。
- 响应体正文。
- localStorage/sessionStorage/capturedFields 的字段值。

## 4. 组件设计

### 4.1 `PluginLogger`

新增 `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/logging/PluginLogger.kt`。

对外提供：

- `info(event, fields)`
- `warn(event, fields, error)`
- `error(event, fields, error)`
- `sanitizeUrl(url)`
- `sha256(value)`
- `measure` 辅助由调用方自行用 `System.currentTimeMillis()` 完成，避免把 suspend 逻辑放进日志工具。

日志格式示例：

```text
plugin.workflow.step.success pluginId=yangtzeu-eams-v2 workflowId=sync-schedule stepId=course-home stepType=HttpRequest stepIndex=3 elapsedMs=842 statusCode=200 responseLength=13920 responseSha256=...
```

失败日志示例：

```text
plugin.workflow.step.failure pluginId=yangtzeu-eams-v2 workflowId=sync-schedule stepId=course-meta stepType=EamsExtractMeta stepIndex=4 elapsedMs=12 errorType=IllegalStateException errorMessage=未找到教学周上限
```

异常对象交给 Android `Log` 输出堆栈。

### 4.2 `PluginManager`

记录：

- 内置插件初始化开始、跳过、新装、更新、失败。
- UI schema 与 timing profile 加载失败。
- 插件同步启动、等待网页登录、成功、失败。
- 插件同步恢复、成功、失败。
- 插件移除开始、完成、失败。
- 市场索引拉取与插件包下载开始、成功、失败。

### 4.3 `PluginInstaller`

记录：

- 本地/远程插件包预览开始、成功、失败。
- 摘要校验结果、签名校验结果。
- 安装开始、成功、失败。
- 内置 asset 目录安装成功或失败。

### 4.4 `WorkflowEngine`

记录：

- workflow start/resume。
- 每个步骤开始、成功、失败和耗时。
- WebSession 步骤生成 request 的安全摘要。
- HTTP 请求的 method、脱敏 URL、statusCode、elapsedMs、headers 数量、响应长度、响应 sha256。
- EAMS 元数据解析、课表构建、静态课表解析的成功/失败。

失败发生在任意步骤时，日志包含插件 ID、workflowId、stepId、stepType、stepIndex、elapsedMs、异常类型、错误消息和堆栈。

### 4.5 `PluginWebSessionScreen`

记录：

- Web 会话页面开始加载、完成加载、页面错误、HTTP 错误、白名单拦截、新窗口创建、控制台错误。
- 自动完成和手动完成触发。
- WebSessionPacket 捕获成功或兜底，记录字段数量与 htmlDigest。

不记录 Cookie/localStorage/sessionStorage/capturedFields 的字段值。

### 4.6 `ScheduleViewModel`

记录：

- 用户发起同步、等待网页登录、网页登录完成回传、用户取消网页登录。
- 同步成功后的课程数量、消息数量、提醒建议数量。
- 插件课表校验失败、保存课表失败、同步失败。

## 5. 错误处理

- 日志工具自身不得抛出异常影响业务流程。
- 业务失败保持现有 UI 提示逻辑，同时增加详细日志。
- CancellationException 继续向外抛出，不吞取消，但取消前后的业务状态可按现有逻辑处理。
- URL 脱敏失败时记录 `<invalid-url>` 或原始字符串的安全裁剪版本。

## 6. 验证

- 为 `PluginLogger` 增加单元测试，覆盖 URL 敏感参数脱敏、hash 稳定性、字段格式。
- 运行 `:core-plugin:testDebugUnitTest`。
- 运行 `:feature-plugin:testDebugUnitTest`，确保 Web 会话现有测试不回退。
- 运行 `:app:assembleDebug`，确认全量编译通过。
- 人工检查关键日志调用点，确认没有密码、Cookie、token、请求体、响应体正文进入日志字段。
