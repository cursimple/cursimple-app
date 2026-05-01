# 插件日志导出与清空设计

日期：2026-05-01

## 目标

- 将插件诊断日志并入“导出日志”文件，而不是只依赖设备 `logcat` 缓冲区。
- 增加“清空日志”功能，清理 App 自己维护的日志缓存和已生成导出文件。
- 不执行 `logcat -c`，避免系统权限和 Android 版本差异导致行为不稳定。

## 方案

采用 `PluginLogger` 双输出：

- 保留现有 Android `logcat` 输出，便于调试时继续按 tag 查看。
- 增加可注入的文件 sink，由 App 启动时绑定到私有缓存目录。
- 文件 sink 写入 `cache/plugin-logs/plugin-diagnostics.log`，内容复用 `PluginLogger` 已脱敏的结构化文本。

`core-plugin` 不直接持有 `Context`，只暴露轻量接口：

- `PluginLogSink.write(priority, tag, message, throwableText)`
- `PluginLogger.setSink(sink)`

这样插件核心模块仍保持独立，App 层负责文件位置、清空和导出策略。

## 导出

`LogExporter` 继续抓当前进程最近 2000 行 `logcat`。导出的 txt 增加两个章节：

- `# Logcat`：现有 logcat 内容。
- `# Plugin diagnostics`：App 内部插件诊断日志文件内容。

如果插件日志文件不存在或为空，导出文件仍正常生成，并写入空日志提示。

## 清空

关于页开发者调试卡片新增“清空日志”按钮。点击后：

- 删除 `cache/logs/` 下已生成导出文件。
- 清空或删除 `cache/plugin-logs/` 下插件诊断日志文件。
- 不清空系统 `logcat`。

清空失败时给用户 toast 提示，成功时提示“已清空日志”。

## 容错与隐私

- 文件 sink 的所有 I/O 都通过 `runCatching` 包裹，失败不影响插件运行。
- 插件日志文件设置大小上限，超过上限时截断旧内容，保留最近日志。
- 继续沿用 `PluginLogger` 的敏感字段脱敏规则，不记录密码、Cookie、token、请求体或响应体正文。

## 验证

- `PluginLoggerTest` 覆盖 sink 分发、异常文本和清空 sink。
- `LogExporterTest` 覆盖插件日志合并和缓存清空的纯文件逻辑。
- 运行 `:core-plugin:testDebugUnitTest`、`:app:testDebugUnitTest` 和 `:app:assembleDebug`。
