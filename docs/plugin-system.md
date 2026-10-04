# 插件平台说明

本文档描述当前插件平台的正式方向：插件以 zip 包安装，用 `manifest.json` 声明身份、入口、权限、可选 UA、组件依赖和运行限制，由系统 WebView 执行插件入口 JS，并通过受控 `ctx` 对象产出课程草稿。旧 QuickJS 与 `workflow.json` 运行模型已停止使用。

本次重构不内置示例插件，也不会在启动时自动安装旧 assets 插件。旧插件记录如果缺少新版 manifest 关键字段，会被标记为不兼容，用户可以移除后安装新版插件包。

## 插件包结构

插件 zip 至少包含：

```text
manifest.json
main.js
checksums.json
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
  "startUrl": "https://jw.school.edu.cn/",
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

安装器会拒绝绝对路径、`..` 路径穿越、Windows 盘符路径、重复规范化路径、缺失 `manifest.json`、缺失入口文件、文件数过多或解压体积过大的包。安装预检和正式安装都会读取 `checksums.json`，并校验其中列出的文件摘要，要求 `checksums.json` 精确覆盖包内全部非元数据文件。`signature.json` 不是必需的：包内没有该文件时照常安装，有则对 `checksums.json` 验签，解析失败或验签不通过会拒绝安装。签名只说明包内容前后一致，公钥随包分发，不构成来源可信的依据。

`startUrl` 可声明带路径或端口的 WebView 起始地址；未声明时宿主才会使用第一个 `allowedHosts` 拼出默认 `https://host`。`userAgent` 仍保留兼容，但推荐插件在 JS 中调用 `await ctx.web.setUserAgent(ua)`，让入口脚本按学校站点状态自行决定 UA；设置成功后，同一 WebView 后续导航和页面内请求共享该 UA。

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
| `feed.write` | 扩展组件交出条目（作业、公告……），由宿主通知、上日历、写课表事务 |

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

设置中的「插件来源」和「组件来源」是两个可增删的 GitHub 仓库列表，默认分别为 `cursimple/cursimple-plugins` 和 `cursimple/cursimple-components`，对应偏好字段 `pluginSources`、`componentSources`。添加来源会保留公有默认来源；用户可以删除全部来源，再按「恢复公有仓库」加回来。老版本的 `pluginRegistryRepo` 自定义地址会保留为额外插件来源。

地址支持 `owner/repo`、GitHub 完整链接、`.git`、`/tree/<branch>` 后缀和 SSH 地址，保存时统一为 `owner/repo`。每个来源独立检测和加载，一个仓库失败不影响其他仓库；同一个仓库条目以来源列表中排在前面的为准。市场显示来源为「公有仓库」或用户添加的仓库名，安装记录保存 `registrySource`（来源仓库）和 `sourceRepo`（插件自身仓库）。插件与组件页面都有「已安装」「市场」入口和搜索，导课页面只展示导课插件。

来源也可以直接填写某个插件或组件自己的仓库；仓库最新 Release 必须包含 `manifest.json` 附件，声明 `version` 和安装 ZIP 的 `filename`，ZIP 继续经过原有安装预检和校验。组件注册表读取 `component-stars-data/components-stars.json`，结构与插件注册表一致。旧插件来源中声明 `kind: "extension"` 的记录继续归入组件页。

同页提供 GitHub 账号登录：默认包使用细粒度个人令牌，可将权限限定为自己的仓库及 `Contents: Read-only`。令牌通过 GitHub `/user` 校验后，用 Android Keystore 的 AES-GCM 密钥加密保存，排除系统云备份、设备迁移和应用备份。退出登录或更换令牌会清理账号相关清单和版本缓存，已安装的本地插件保留。私有清单、版本及资产走 GitHub API，认证头只发给 `api.github.com`，下载重定向到资产存储时不携带令牌。

可通过 Gradle 属性 `github.oauthClientId` 或环境变量 `CURSIMPLE_GITHUB_OAUTH_CLIENT_ID` 配置自己的 GitHub OAuth App Client ID 并启用设备码登录。该 OAuth App 必须启用 device flow；Client ID 不是密钥。未配置时仍可使用令牌登录，无需外部 OAuth 服务。

详情页隐藏插件／组件分类切换，以独立卡片展示名称、版本、来源、简介和仓库信息。安装预检默认展示简介与实际 ZIP 大小，权限、可访问站点和技术信息可展开查看；弹窗保持固定高度，标题和底部操作按钮固定，内容在内部滚动，展开或收起时窗口不移动。校验不通过的原因直接显示并禁止安装。

公有镜像和私有 GitHub 资产下载都会按读取到的字节报告进度。服务端未提供长度时显示已下载字节，不推算百分比；总大小或累计下载达到 5 MiB 时显示大包进度，小包保留按钮加载状态。取消下载或切换账号会清理进度，旧请求的回调不会覆盖新请求。

注册表数据来自 `plugin-stars-data` 分支的 `plugins-stars.json`：

```json
{
  "repositories": [
    {
      "name": "cursimple/YangtzU_course_plugin",
      "repo": "YangtzU_course_plugin",
      "owner": "cursimple",
      "avatar": "https://avatars.githubusercontent.com/u/283925439?s=80&v=4",
      "description": "YangtzU course plugin for cursimple.",
      "star": 0,
      "language": "JavaScript",
      "url": "https://github.com/cursimple/YangtzU_course_plugin",
      "schools": ["长江大学", "长大", "changjiangdaxue", "YangtzU"]
    }
  ]
}
```

`schools` 声明这个插件覆盖的学校别名，供「从教务系统导课」按学校名搜索。仓库名多半是英文缩写，学生搜的却是中文校名，两者对不上就找不到插件，所以把中文全称、常用简称、拼音和英文缩写一并写进来。

- 按普通子串匹配，忽略大小写，前缀也算命中：打到「北京理工」就能出「北京理工大学」。
- App 侧不做拼音转换，要支持拼音就把拼音直接写成一条别名。
- 兼容键名 `aliases`，与 `schools` 合并去重。
- 该字段可省略，省略时退回按仓库名、账号和描述匹配。
- 新学校只改注册表，不必发版。

App 拉取流程：

1. 从 `https://raw.githubusercontent.com/<registry>/plugin-stars-data/plugins-stars.json` 读取仓库列表，下载候选优先使用 jsDelivr。
2. 并行读取 `https://github.com/<owner>/<repo>/releases/latest/download/manifest.json`。
3. 使用 manifest 的 `version` 展示最新版本，使用 `filename` 拼出 `https://github.com/<owner>/<repo>/releases/latest/download/<filename>`。
4. 网格展示 + 详情页"安装"按钮调用既有的远程包下载预检流程。没有 manifest 或 filename 时按钮显示"未找到版本"。

注册表的增删通过 Pages 静态页 [https://cursimple.github.io/cursimple-plugins/](https://cursimple.github.io/cursimple-plugins/) 完成，源码位于 [cursimple-plugins/docs/](https://github.com/cursimple/cursimple-plugins/tree/main/docs)。

下载镜像按用途建模：

- `github_release`：GitHub Release 资产，使用镜像池与 GitHub 源站测速下载。
- `github_raw`：`raw.githubusercontent.com` 文件，优先使用 jsDelivr，再使用 raw 代理、镜像池和 GitHub 源站。
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

App 更新和默认公有市场沿用镜像候选、随机抽样测速、最快优先和失败转移逻辑；账号访问的私有来源及其安装包直接访问 GitHub API。

## 运行结果

插件同步结果统一为：

- `Success`：产生 `TermSchedule`，交给调度层保存。
- `Failure`：插件不兼容、入口无效、权限不足、缺少课程草稿或草稿校验失败。
- `NeedsComponents`：必需组件缺失。
- `AwaitingWebSession`：需要打开 WebView 会话执行入口脚本或等待用户登录。

`AwaitingWebSession` 会携带入口脚本、权限、白名单、资源限制和起始 URL。若插件没有可确定的起始地址，启动同步会明确失败，不构造空白页或临时地址。

## 扩展组件（kind = "extension"）

导课插件跑一次、交出课表就结束；扩展组件装好后常驻：登录一次，之后宿主在后台定时跑它，把拿到的条目（作业、考试、公告……）变成通知、侧边栏日历和课表事务。第一个扩展组件是 [雨课堂通知](https://github.com/cursimple/YuKeTang_notice_plugin)。

包结构与导课插件相同（`manifest.json` + 入口脚本 + `checksums.json`），manifest 多两处：

```json
{
  "id": "yuketang-notice",
  "kind": "extension",
  "apiVersion": 4,
  "entry": "main.js",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) …",
  "permissions": ["network.fetch", "web.read_cookies", "feed.write"],
  "allowedHosts": ["changjiang.yuketang.cn", "www.yuketang.cn"],
  "extension": {
    "title": "雨课堂",
    "loginUrl": "https://{settings.site}/web",
    "runUrl": "https://{settings.site}/api/v3/user/basic-info",
    "syncIntervalMinutes": 60,
    "loginViewportWidth": 960,
    "feedTypes": [{ "id": "homework", "label": "作业", "color": "#2563EB", "kind": "task" }],
    "settings": [
      {
        "key": "site", "type": "select", "label": "雨课堂站点", "default": "changjiang.yuketang.cn",
        "requiresRelogin": true,
        "options": [{ "value": "changjiang.yuketang.cn", "label": "长江雨课堂" }]
      },
      { "key": "syncHomework", "type": "switch", "label": "作业", "default": true }
    ],
    "ui": {
      "entry": "ui/feed.html",
      "settingsEntry": "ui/settings.html",
      "loginEntry": "ui/login.html",
      "type": "html"
    }
  }
}
```

- `kind` 缺省是 `schedule`（导课插件）。扩展组件必须声明 `apiVersion >= 3`、带 `extension` 段、写明 `allowedHosts`；老版本 App 只认到 2，会按「接口版本太新」拒装，不会把它当导课插件跑。宿主也会拒绝不认识的 `kind`。
- `loginUrl`：登录页。宿主打开站点自己的登录页，用户怎么登由站点处理；宿主每 1.5 秒在当前页面上跑一次 `checkLogin`，登上了就关页、立即同步一次。登录页上**不挂** JS 桥，结果写在 `window` 上由宿主轮询。
- `runUrl`：后台同步时在看不见的 WebView 里打开的页面。只要是同站点的同源页面就行，越轻越好（雨课堂用的是一个 48 字节的 JSON 接口）；页面加载完宿主注入 ctx 和入口脚本。
- `{settings.KEY}` 按用户的选项替换，只接受 `[A-Za-z0-9._-]`，替换后的地址仍须落在 `allowedHosts` 里。
- `loginViewportWidth`：登录页只做了电脑版、又把 viewport 锁成手机宽度时用。宿主在页面脚本运行前把 viewport 改成这个宽度，再整页缩放进屏幕。
- `settings`：组件自己的选项，宿主按 `type`（`switch` / `select` / `number` / `text`）画在设置面板里。`requiresRelogin` 的项改了之后，旧登录和旧条目作废。
- `feedTypes`：条目类型、日历上的颜色，以及这一类的语义 `kind`。`kind` 取 `task`（有截止，会算逾期、能写进课表）或 `notice`（读完即止）。宿主只认这两个值，**不认具体类型名**；组件不声明时，宿主按条目上有没有截止/开始时间兜底推断。类型 id、标签、颜色全由组件决定，宿主不写死 `homework` / `announcement` 之类的名字。
- `ui`：可选的组件自带页面入口。`entry` 必须是包内 HTML 文件；存在时由 APK 的通用 WebView 容器加载，组件自己负责排版、月历、详情和资源展示，APK 不写死组件页面。没有 `ui` 的旧组件使用宿主兼容页面。

### 组件自带页面（API 4）

组件自己的登录、设置和内容页面由 `ui.loginEntry`、`ui.settingsEntry`、`ui.entry` 声明。HTML、CSS、JavaScript 和资源都在 ZIP 内，APK 不解析业务登录协议。前面的 `loginUrl` 轮询流程仅用于没有自带页面的旧组件。

页面通过 `window.CurSimpleComponent` 使用通用能力：`state` 是当前快照，`subscribe(fn)` 立即回调并订阅后续变化，`request(command, payload)` 返回 Promise。`context.theme` 提供当前宿主主题的颜色和 `dark` 标志，组件自行映射到 CSS；它不是某个组件专属的主题配置。

- `settings.update` / `host.update`：保存组件声明的选项或通用通知、展示设置。
- `ui.login` / `ui.settings` / `ui.feed` / `ui.close`：打开对应组件页面或返回。
- `web.cookie`：读取当前组件站点的 Cookie；`qr.encode`：将组件提供的内容绘制为二维码。
- `login.check`：在声明的 `runUrl` 下运行组件的 `checkLogin`，校验通过后才保存账号。传 `{ navigate: false }` 时返回 `ExtensionData` 而不强制跳页，组件可显示成功反馈；不传时保持旧包的自动返回行为。校验最长等待 35 秒。
- `sync` / `logout` / `component.remove` / `media.open`：同步、退出、请求移除及打开已同步条目中的附件。

组件页面的触摸序列由通用 WebView 容器处理；页面打开时宿主停止从内容区拖出侧边栏，菜单按钮仍可打开，已打开的侧边栏仍能滑动关闭。关闭组件后恢复普通页面手势。页面快照仅在页面、数据或主题等有效信息变化时推送，`context.nowMillis` 为这份快照的采样时间，组件若需连续计时应自行维护时钟。

一般桥接调用 45 秒无响应会拒绝 Promise；同步最长 185 秒。换页后旧回调不能写入新页面。组件应显示等待和错误信息，提供重试入口；不要把任意 Cookie 或短信接口成功当作账号校验成功。

### 入口脚本

按 ES 模块写、单文件、导出两个函数。宿主把 `export` 摘掉后**原样拼进注入脚本**，不走 eval，站点有 CSP 也照样跑；不支持 `import`。

```js
export async function checkLogin(ctx) {
  // 登上了：{ loggedIn: true, account: { id, name, school, number, avatar } }
  // 没登上：{ loggedIn: false }
}

export async function sync(ctx) {
  // 登录失效：return { loginRequired: true }
  return { account, items: [/* 条目 */], message: "可选：部分没取到之类的一句话" };
}
```

`ctx`：

| 成员 | 说明 |
| --- | --- |
| `ctx.mode` | `"checkLogin"` 或 `"sync"` |
| `ctx.settings` | 组件选项的完整一份（没改过的按 manifest 默认值补齐），只读 |
| `ctx.state.get(key)` / `ctx.state.set(key, value)` | 组件自己的缓存（截止时间之类），宿主原样存取，下次同步带回来 |
| `ctx.network.fetch(url, init)` | 页面自己的 `fetch`，默认 `credentials: "include"`；只放行 `allowedHosts` 的 https 地址。需要 `network.fetch` |
| 页面内 `WebSocket` | 组件自带页面里的 `WebSocket` 被重写为只放行 `allowedHosts` 的 `wss://` 地址，其余连接直接抛错。需要 `network.fetch` |
| `ctx.web.cookie(name)` | 读 `document.cookie` 里的一项（CSRF token 之类）。需要 `web.read_cookies` |
| `ctx.feed.add(item)` | 交一条条目，也可以直接在返回值的 `items` 里给。需要 `feed.write` |
| `ctx.log.info / warn / error` | 写进课简的插件日志 |
| `ctx.now()` | 当前时间（毫秒） |

条目字段：`id`（同一组件内必须稳定，去重全靠它）、`type`、`title`（必填）、`course`、`category`、`publishAt` / `startAt` / `dueAt`（毫秒）、`done`、`summary`、`content`、`author`、`url`（只收 http/https）。每次同步交的是**完整快照**：这次没交的条目就当没了。结果 JSON 不能超过 `limits.maxOutputBytes`，运行时间上限 `limits.timeoutMs`（宿主再限制在 10 秒到 3 分钟之间）。

### 宿主做的事

- **设置面板**：在插件页点开扩展组件就是它；先登录，登录后才有同步、提醒方式和组件选项。
- **后台同步**：各组件按自己的间隔（清单默认值，用户可调，最短 30 分钟）同步。唤醒来自两处：WorkManager 每 30 分钟一次（联网时），以及课表小组件那条每 5 分钟的静默守护闹钟（联网时顺带检查）——后者在会推迟 WorkManager 的系统上也准时。两处同时到点只跑一次；同步完立即刷新「待完成」小组件和课表事务。
- **新内容通知**：这次有、上次没有的 id 算新内容；登录后第一次同步只建基线，不推老内容。一次超过 3 条并成一条。
- **截止前提醒**：`dueAt` 前 N 小时（用户可选，默认 24）提醒一次；截止时间改了会再提醒。
- **写进课表事务**（默认关）：有截止、未完成的条目在截止那天挂一条事务，id 固定为 `ext-<组件>-<条目>`，每次同步增删改；登出、移除组件时一并撤掉。
- **侧边栏专属页**：有 `extension.ui` 时加载组件包自己的页面；宿主只注入当前 `ExtensionData` 并提供同步、设置等桥接能力。页面加载失败会回退到宿主兼容页面。
- **登录失效**：`sync` 返回 `loginRequired` 时发一条「请重新登录」，重新登录前不重复发。
- **退出登录 / 移除组件**：作废 `allowedHosts` 上的 Cookie，删掉存档、通知和它加的事务。
- **桌面「待完成」小组件**（组件小组件）：没做完、不是往期、声明为 `task` 的条目，按开始（还没开始时）或截止时间排好交给桌面；点一条打开它所在组件的页面。宿主只看 `kind` 和时间，不认类型名。没有启用中的组件时这个小组件不出现在任何选择器里，组件全部移除后随之从桌面下架。课程日历等只用课表与事务的小组件是系统小组件，不受影响。

注册表里给条目写上 `"kind": "extension"`，「从教务系统导课」就不会列它。

## 旧架构清理

以下内容不再是当前插件平台的一部分：

- QuickJS 执行器。
- `workflow.json` 步骤工作流。
- APK assets 内置插件目录自动安装。
- `login -> fetchSchedule -> normalize` 三段式固定调用。

保留的是统一课表模型、课表持久化、小组件刷新、提醒同步和可选的旧 `ui/schedule.json`、`datapack/timing.json` 展示数据读取。

#### 课表事务显示规则

组件设置中的「显示在课表事务」拥有独立的二级设置页。`host.schedule` 保存用户选择的类型、每种类型的日期来源和日期偏移，以及默认时间、短卡片时长、完成/已读状态和历史保留范围。默认包含全部类型；作业优先截止日期、考试优先开始日期、公告优先发布日期。没有可用日期时按首次同步日期显示；零点截止仍保留在截止当天。日期偏移只改变课表显示，不会改变通知的真实截止时间。

设置页的实际内容预览和事务桥共用 `placeExtensionItem` / `extensionScheduleItems`。保存后会用稳定的条目 ID 更新现有事务，移除不再匹配的条目，不会影响用户手动添加的事务。关闭总开关、退出登录或删除组件会撤掉组件生成的事务。已被组件同步选项过滤的内容无法在宿主端恢复。

#### 侧边栏日历的独立展示设置

没有自带 UI 的旧组件由 `host.feed` 控制兼容页面的日期来源、完成状态和历史范围。自带 UI 的组件可以直接读取完整 `ExtensionData`，自己决定月历、筛选、详情和附件的展示；宿主只负责数据、权限与生命周期，不保存组件页面状态。

组件产生的事务通过可选 `ScheduleEvent.source = { componentId, itemId }` 关联原始条目。课表模块只把点击交给宿主回调，宿主在课简内打开组件详情。旧事务可按原先的完整事务 ID 匹配；来源缺失或组件已移除时仍能查看事务里保存的文本。普通手动事务继续使用原来的编辑与删除操作。

#### 原生登录验证

`yuketang_web` 使用网页会话。短信发码先由用户完成官方腾讯验证，宿主收到真实 `ticket` / `randstr` 后才请求发码；验证码为空、取消或加载失败时不会发短信。旧清单的 App 短信路径会兼容映射到网页路径。微信二维码按当前官网协议通过 HTTPS 获取 `qrContent` 与 token 并等待确认，不再只依赖 WebSocket。

短信和扫码各自保存独立的临时 CookieJar；退出/刷新会取消本次 HTTP 请求。成功后须通过账号接口校验，再保留 Cookie 的 domain/path/HttpOnly/Secure 属性写入 WebView，等待写入回调完成后才同步。日志只记录阶段与异常类型，不记录手机号、短信码、腾讯验证票据、扫码 token 或 Cookie 值。
