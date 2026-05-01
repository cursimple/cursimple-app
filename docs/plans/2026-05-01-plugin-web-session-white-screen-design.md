# 插件网页登录白屏与滚动能力修复设计

日期：2026-05-01

范围：插件网页登录 WebView、长江大学内置插件白名单、网页登录回传容错

## 1. 目标

- 内置插件网页登录页支持手指拖动浏览，并显示水平、垂直滚动条。
- 长江大学统一身份认证登录成功后，不再因为认证中转或教务跳转链路被静默拦截而白屏停住。
- Web 会话自动完成与手动回传都必须容错，避免 JavaScript 抓取失败导致界面一直停在“回传中”或同步中。
- 如果仍遇到白名单之外的跳转，界面要给出当前 URL 或被拦截域名，便于继续补充配置。

## 2. 推荐方案

采用 WebView 容错增强 + 插件白名单补齐的组合方案：

- WebView 启用 DOM Storage、宽视口、概览模式、水平与垂直滚动条。
- 对白名单外跳转不再静默丢弃，而是记录并展示被拦截 URL。
- 对统一认证链路补充常见中转域名，允许登录成功后的 ATrust 与教务代理域名跳转完成。
- 回传脚本包裹在 `try/catch` 中，抓取 localStorage、sessionStorage、HTML、选择器字段时单项失败不影响整体回传。
- Kotlin 端解析 JavaScript 返回值时兜底为空 JSON，避免 malformed payload 造成崩溃。

## 3. 组件设计

### 3.1 Web 会话界面

`PluginWebSessionScreen` 保持现有弹窗式 Web 登录流程，但补充运行状态：

- 当前 URL 显示在调试提示区域。
- 若跳转被白名单拦截，显示“已拦截非白名单跳转”与 URL。
- 自动完成仍由 `completionUrlContains` 触发。
- 手动完成按钮始终可作为兜底入口。

### 3.2 WebView 设置

WebView 初始化时启用：

- `settings.useWideViewPort = true`
- `settings.loadWithOverviewMode = true`
- `settings.builtInZoomControls = true`
- `settings.displayZoomControls = false`
- `isVerticalScrollBarEnabled = true`
- `isHorizontalScrollBarEnabled = true`
- `scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY`

这些能力只影响内嵌网页登录页面，不改变 App 其他界面。

### 3.3 域名白名单

长江大学插件继续使用插件声明的 `allowedHosts` 作为安全边界。基于现有入口和跳转链路，补齐 ATrust/CAS/教务代理可能出现的同源或子域名。白名单校验仍保持精确 host 或子域匹配，不放开为任意 URL。

### 3.4 回传容错

`captureWebSessionPacket` 的 JavaScript 不再假设页面一定允许访问所有 Web Storage 或完整 HTML。每一类采集独立兜底：

- localStorage 访问失败时返回空对象。
- sessionStorage 访问失败时返回空对象。
- DOM 选择器访问失败时跳过该字段。
- HTML 获取失败时返回空字符串。

Kotlin 端解析 payload 时继续生成 `WebSessionPacket`，保证 workflow 能继续执行并把真实 HTTP 失败反馈给用户。

## 4. 错误处理

- 非白名单跳转：WebView 停留在当前页，界面显示拦截 URL。
- 页面加载错误：界面显示错误说明和失败 URL，不直接关闭 Web 会话。
- 回传脚本异常：生成空 payload 的 `WebSessionPacket`，并继续恢复工作流。
- 后续教务 HTTP 请求失败：沿用现有 `ScheduleViewModel` 的失败提示。

## 5. 验证

- 编译 `feature-plugin`、`app` 相关模块。
- 运行现有核心插件测试，确认 workflow 模板解析不回退。
- 人工检查 WebView 登录弹窗：
  - 能垂直拖动页面。
  - 页面宽于屏幕时可水平滚动。
  - 登录成功跳转到教务域名后自动回传。
  - 白名单外跳转会显示被拦截 URL，而不是纯白屏。
