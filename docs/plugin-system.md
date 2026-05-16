# 插件平台说明

本文档描述当前插件平台的正式方向：插件以 zip 包安装，用 `manifest.json` 声明身份、入口、权限、可选 UA、组件依赖和运行限制，由系统 WebView 执行插件入口 JS，并通过受控 `ctx` 对象产出课程草稿。旧 QuickJS 与 `workflow.json` 运行模型已停止使用。

本次重构不内置示例插件，也不会在启动时自动安装旧 assets 插件。旧插件记录如果缺少新版 manifest 关键字段，会被标记为不兼容，用户可以移除后安装新版插件包。

## 插件包结构

插件 zip 至少包含：

```text
manifest.json
main.js
checksums.json
signature.json
assets/
models/
```

`manifest.json` 示例：

```json
{
  "id": "edu.school.schedule",
  "name": "学校教务插件",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 2,
  "entry": "main.js",
  "userAgent": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
  "permissions": [
    "web.navigate",
    "web.read_dom",
    "web.inject_script",
    "schedule.write"
  ],
  "allowedHosts": [
    "jw.school.edu.cn"
  ],
  "webEngine": {
    "preferred": "system_webview",
    "allowChromium": true,
    "chromiumComponent": "engine.chromium.android"
  },
  "components": [
    {
      "id": "runtime.onnx",
      "type": "onnx_runtime",
      "required": false,
      "version": "1.0.0"
    }
  ],
  "limits": {
    "timeoutMs": 60000,
    "maxCourses": 1000,
    "maxStorageBytes": 1048576,
    "maxCapturedTextBytes": 524288,
    "maxOutputBytes": 1048576
  }
}
```

安装器会拒绝绝对路径、`..` 路径穿越、Windows 盘符路径、重复规范化路径、缺失 `manifest.json`、缺失入口文件、文件数过多或解压体积过大的包。

`userAgent` 为空或缺失时使用 WebView 默认 UA；插件显式声明后，宿主会在创建 WebView 会话时设置 `settings.userAgentString`，页面导航和页面内请求共享同一个 UA。

## 权限

权限是运行时能力的唯一来源：

| 权限 | 能力 |
| --- | --- |
| `web.navigate` | 允许插件通过 `ctx.web.open()` 导航到 `allowedHosts` 内页面 |
| `web.read_dom` | 允许读取 DOM 文本、查询元素和采集 HTML 摘要 |
| `web.read_cookies` | 允许读取白名单域名 Cookie |
| `web.inject_script` | 允许填表、点击等页面脚本操作 |
| `web.capture_packet` | 允许读取 manifest 精确声明的 WebView 请求/响应数据包 |
| `network.fetch` | 允许通过页面 `fetch` 请求白名单 URL |
| `schedule.write` | 允许写入课程草稿 |
| `storage.plugin` | 允许采集或使用插件私有存储相关数据 |
| `component.use` | 允许使用已安装组件 |

权限不足时运行时会抛出错误，不做隐式降级。

`web.capture_packet` 只打开数据包采集能力，不代表插件可以读取全部页面流量。插件还必须在 `manifest.json` 中声明 `networkCaptures`，宿主只采集命中规则的数据包，并且只返回规则允许的 header/body 字段：

```json
{
  "permissions": ["web.capture_packet"],
  "networkCaptures": [
    {
      "id": "course-table-json",
      "required": true,
      "method": "GET",
      "urlHost": "jw.school.edu.cn",
      "urlPathContains": "/api/course/table",
      "requestHeaders": ["accept"],
      "responseHeaders": ["content-type"],
      "captureResponseBody": true,
      "responseBodyMimeTypes": ["application/json"],
      "maxBodyBytes": 65536,
      "maxPackets": 4
    }
  ]
}
```

插件入口可通过 `ctx.web.packet("course-table-json")` 获取最新一条，或通过 `ctx.web.packets("course-table-json")` 获取该规则捕获到的列表。访问未声明 ID 或缺少权限都会抛错。

Android WebView 原生拦截能稳定采集 URL、method、部分请求头、响应状态、响应头和响应体。页面内 `fetch` 和 `XMLHttpRequest` 请求会安装受控 hook，因此可以在规则允许时采集文本请求体和响应体。普通表单 POST 与其他非 JS 发起请求的请求 body 不是 WebView 原生接口稳定可得的字段，宿主不会伪造该字段。

## JS 入口

插件入口推荐使用：

```js
export async function run(ctx) {
  const rows = ctx.web.queryAll(".course-row", (row) => ({
    title: row.querySelector(".title")?.textContent?.trim(),
    dayOfWeek: Number(row.dataset.day),
    startNode: Number(row.dataset.start),
    endNode: Number(row.dataset.end),
    weeks: row.dataset.weeks.split(",").map(Number)
  }));

  for (const row of rows) {
    ctx.schedule.addCourse(row);
  }

  return ctx.schedule.commit({ termId: ctx.term.id });
}
```

WebView 会在白名单页面加载完成后注入入口脚本。宿主不会把 Android 原生对象暴露给插件；`ctx` 是页面内纯 JS 对象，负责做权限校验、域名校验和课程草稿提交。

`ctx.schedule.commit()` 会把草稿写入 `scheduleDraftJson`。App 恢复会话时解析 `ScheduleDraft`，校验课程数量、星期、节次、周次和标题，再转换成现有 `TermSchedule`。课表保存、小组件刷新和提醒联动继续复用原有链路。

## 组件

插件可以声明组件依赖：

- `engine_chromium`
- `opencv_native`
- `onnx_runtime`
- `onnx_model`
- `generic_asset`

必需组件缺失时，`PluginManager.startSync()` 返回 `NeedsComponents`，插件不会运行，也不会静默切回其他引擎。组件包安装会校验 manifest、ABI、SHA-256、路径安全和解压大小。

第一阶段只集成系统 WebView。Chromium 作为组件类型和状态机存在，未安装时只进入组件申请流程。

## 市场和下载

插件市场默认索引地址：

```text
https://raw.githubusercontent.com/cursimple/cursimple-plugins/refs/heads/main/manifest.json
```

市场格式后续再定；当前实现保留加载、空状态、错误状态和刷新能力。

下载镜像按用途建模：

- `github_release`：GitHub Release 资产，不使用 jsDelivr。
- `github_raw`：`raw.githubusercontent.com` 文件，可使用 raw 代理和 jsDelivr。
- `github_repo_file`：仓库文件，可生成 `cdn.jsdelivr.net/gh/user/repo@ref/path`、`fastly.jsdelivr.net` 和 `xget.xi-xu.me/gh/...`。
- `direct_url`：普通 URL，保留源站候选。
- `local_file`：本地文件，不走网络。

特殊镜像规则是显式建模的：

- `down.npee.cn/?https://github.com...`
- `cors.isteed.cc/github.com...`
- `raw.ihtw.moe/raw.githubusercontent.com...`
- `xget.xi-xu.me/gh/user/repo/ref/path`
- `cdn.jsdelivr.net/gh/user/repo@ref/path`
- `fastly.jsdelivr.net/gh/user/repo@ref/path`

App 更新、插件市场、插件包和组件包共用镜像候选、随机抽样测速、最快优先和失败转移逻辑。

## 运行结果

插件同步结果统一为：

- `Success`：产生 `TermSchedule`，交给调度层保存。
- `Failure`：插件不兼容、入口无效、权限不足、缺少课程草稿或草稿校验失败。
- `NeedsComponents`：必需组件缺失。
- `AwaitingWebSession`：需要打开 WebView 会话执行入口脚本或等待用户登录。

`AwaitingWebSession` 会携带入口脚本、权限、白名单、资源限制和起始 URL。若插件没有可确定的起始地址，启动同步会明确失败，不构造空白页或临时地址。

## 旧架构清理

以下内容不再是当前插件平台的一部分：

- QuickJS 执行器。
- `workflow.json` 步骤工作流。
- APK assets 内置插件目录自动安装。
- `login -> fetchSchedule -> normalize` 三段式固定调用。

保留的是统一课表模型、课表持久化、小组件刷新、提醒同步和可选的旧 `ui/schedule.json`、`datapack/timing.json` 展示数据读取。
