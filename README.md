<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="104" alt="课简">

# 课简 · CurSimple

**课表、笔记、提醒与扩展组件，一个应用管理整个学期。**

开源、免费、无广告的 Android 课表应用。用学校插件导入课表，用扩展组件接入学习平台和通知渠道，在手机与桌面管理课程、事务和待办。

[![CI](https://github.com/cursimple/cursimple-app/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cursimple/cursimple-app/actions/workflows/android-ci.yml)
[![最新测试版 0.7.10](https://img.shields.io/badge/Latest%20beta-0.7.10-orange)](https://github.com/cursimple/cursimple-app/releases/tag/v0.7.10)
[![Downloads](https://img.shields.io/github/downloads/cursimple/cursimple-app/total)](https://github.com/cursimple/cursimple-app/releases)
[![License](https://img.shields.io/github/license/cursimple/cursimple-app)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[官网](https://cursimple.github.io/cursimple-app/) · [下载](https://github.com/cursimple/cursimple-app/releases) · [插件收录](https://cursimple.github.io/cursimple-plugins/) · [组件收录](https://cursimple.github.io/cursimple-components/) · [English](README_en.md)

</div>

当前发布渠道为 **测试版（beta / Pre-release）**。下文介绍当前代码能力；本地新增功能仍在测试，公开安装包请以对应 Release 公告为准。

## 先认识课简

| 部分 | 用途 | 入口 |
|---|---|---|
| 课简主体 | 课表、课程笔记、事务、提醒、备份与桌面小组件 | [应用下载](https://github.com/cursimple/cursimple-app/releases) |
| 学校导课插件 | 登录学校教务系统，获取并导入课表；学校适配独立更新 | [插件收录网页](https://cursimple.github.io/cursimple-plugins/) · [注册表](https://github.com/cursimple/cursimple-plugins) |
| 扩展组件 | 同步学习任务与公告，接入通知渠道，提供组件主页和可声明的桌面卡片 | [组件收录网页](https://cursimple.github.io/cursimple-components/) · [注册表](https://github.com/cursimple/cursimple-components) |

桌面小组件是课程、笔记或扩展组件在桌面的展示入口。扩展组件按需安装与启用；提供了桌面卡片的组件会显示对应添加入口，卸载或停用后移除。

## 下载安装与开始使用

在 [Releases](https://github.com/cursimple/cursimple-app/releases) 选择对应版本，或从[官网的下载区](https://cursimple.github.io/cursimple-app/#download)下载安装包。测试版需要在应用更新设置中开启对应渠道，是否为测试版以发布渠道和 GitHub 的 Pre-release 标记为准。

| APK | 适用设备 |
|---|---|
| `CurSimple-arm64-v8a.apk` | 常见的 64 位 ARM 手机 |
| `CurSimple-armeabi-v7a.apk` | 较旧的 32 位 ARM 设备 |
| `CurSimple-x86_64.apk` / `CurSimple-x86.apk` | 对应 Intel 架构设备或模拟器 |
| `CurSimple-universal.apk` | 不确定架构时选择通用包 |

1. 设置学期和开学日期。
2. 打开「插件」市场，安装学校插件并按提示登录、同步课表；也可以手动添加、导入课表分享码、扫码或用 AI 识图导入。
3. 在备忘录记录课程笔记与待办，在课表添加课程以外的事务。
4. 设置上课通知和闹钟，检查系统通知、精确闹钟及后台权限。
5. 按需安装扩展组件、完成账号或通知渠道绑定，再把需要的小组件加到桌面。

## 功能特性

### 课表与事务

- **周视图与日视图**：连堂课合并展示，支持单双周、自选周次、考试、临时停课与调课。
- **今日概览**：查看当前课程、下一节课、倒计时和全天时间线，点开检查冲突与课程详情。
- **真实时间事务**：组会、社团和个人安排按起止时间排入课表，重叠时并排展示或折叠。
- **便捷编辑**：长按课程可调课，也能在相同星期和节次位置快速添加另一门课程；教师和地点支持按周设置。
- **外观与缩放**：自定义课表文字、颜色、卡片、网格和背景图，支持双指放大与拖动。
- **学期管理**：多个学期分别保存课表、作息与周数；支持节假日、调休和临时安排。

### 课程笔记与备忘录

| 能力 | 用法 |
|---|---|
| 课程笔记本 | 按课程归类，也能记录通用笔记；搜索覆盖标题、正文、课程与已完成笔记 |
| 清单与待办 | 清单复选框、优先级、置顶、截止时间和完成状态，支持今天到期与逾期筛选 |
| 安心阅读 | 已保存笔记默认只读，点击编辑进入编写；修改保存与放弃草稿有确认 |
| 图文编辑 | 插入本地图片，缩略图点开可放大、缩放与拖动；图片复制到应用存储中 |
| 常用工具 | 清单、列表、序号、标题、粗体、斜体、引用、删除线、行内代码、链接和分隔线；工具栏横向滑动，右上角撤销与重做 |
| 输入方式 | 按回车正常换行，列表续号由工具按钮控制；非当前输入位置渲染行内格式 |
| 分享与导入 | 全部或单篇笔记可选课简笔记文件、分享码、二维码、长图、Word、Markdown 或纯文本；粘贴分享码、扫码、相册识别与文件导入共用预览和确认 |
| 课程匹配 | 接收笔记按同名课程归类，保留正文、清单、优先级和置顶；重复导入保留本地编辑 |

图片随课简笔记文件与备份携带，Word 和图片导出包含图片；含图片的 Markdown 打包为 ZIP。图文内容较多时请选择笔记文件，分享码和单张二维码有容量限制。

### 提醒与闹钟

上课通知、手动闹钟、自动节次规则和单课提醒分别管理。可设置提前时间、铃声、震动、响铃次数、节假日例外，以及响铃前预告；支持手动向系统时钟发送可写入的闹钟。

默认使用系统通知；悬浮提醒、状态栏实时通知和锁屏呈现依设备能力及系统权限提供。后台巡检可补挂被清理的计划，仍需允许系统后台运行。系统时钟接收的是创建请求，写入后请到时钟核对，后续调课变化需自行调整已写入的闹钟。

日期选择器支持左右滑动换月，也可使用箭头和日期点选。

### 桌面小组件

| 入口 | 内容与操作 |
|---|---|
| 今日课表 / 下一节课 | 查看课程时间、教室与当前或下一节课程 |
| 提醒 | 查看即将响铃的课程闹钟 |
| 课程日历 | 周课表与月历切换，查看课程、考试、事务和休 / 班标记；点日期打开课表 |
| 笔记待办 | 在桌面直接勾选，通过迷你编辑窗口编写或新增待办、选择课程；显示优先级、课程颜色与置顶状态 |
| 组件自带卡片 | 由独立组件声明布局与内容，安装启用后添加，点击进入对应组件主页 |

小组件在系统、后台任务和课时边界刷新，实际更新及时性受桌面与后台限制影响。鸿蒙原生服务卡片仍在接入测试；支持 Android 小工具的华为桌面可从「服务卡片」底部的「窗口小工具」查看。

### 导入、分享与备份

- 课表支持二维码、分享字符、图片、系统日历写入和 `.ics` 导出；二维码可保存，分享图片可先预览。
- AI 识图支持选图和拍照，需要自行配置识别 API。
- 本地备份与 WebDAV 备份包含课表、事务、备忘录及笔记图片，账号密码、令牌和通知绑定凭据不随普通备份迁移。
- 支持简体中文、繁體中文与 English；更新公告随应用语言切换，应用时区可独立设置。
- 设置搜索可直达分项，常用项目与课表外观可按习惯调整。

## 扩展组件

组件有独立的主页、设置、登录与数据获取逻辑，安装和更新在课简的「组件」市场完成。可以按名称、用途、别名或来源搜索，在详情查看仓库、兼容要求与安装状态。

| 组件 | 作用 | 跳转 |
|---|---|---|
| 雨课堂通知 | 同步作业、考试、课件任务和课程公告，提供新内容与截止提醒、日历、课表事务以及组件声明的桌面卡片；学习与提交操作到雨课堂完成 | [组件仓库](https://github.com/cursimple/YuKeTang_notice_plugin) · [版本与下载](https://github.com/cursimple/YuKeTang_notice_plugin/releases) |
| 多平台通知 | 将课简上课提醒、笔记到期和组件消息发送到微信、QQ、飞书、企业微信、钉钉、邮箱、Server酱、PushPlus、WxPusher、PushDeer、Bark 等；按目标设置消息类型并查看发送回执 | [组件仓库](https://github.com/cursimple/cursimple-notify-component) · [版本与下载](https://github.com/cursimple/cursimple-notify-component/releases) |

[浏览组件收录网页](https://cursimple.github.io/cursimple-components/) · [查看组件注册表](https://github.com/cursimple/cursimple-components) · [提交组件](https://github.com/cursimple/cursimple-components#contribute)

**安装方式**：在应用市场安装，或从组件 Release 下载 ZIP，再选择「导入 ZIP」。完成登录或渠道绑定后启用所需同步与通知；平台授权可能过期，首次绑定后先发送测试并核对真实结果。可用功能与最低宿主版本以该组件版本的清单为准。

## 学校插件与市场来源

学校插件负责登录教务系统和导入课表。目前默认注册表收录[长江大学教务插件](https://github.com/cursimple/YangtzU_course_plugin)，支持完整教学周、教师、地点和节次信息。

[浏览学校插件](https://cursimple.github.io/cursimple-plugins/) · [插件注册表](https://github.com/cursimple/cursimple-plugins) · [插件版本与下载](https://github.com/cursimple/YangtzU_course_plugin/releases)

插件与组件使用独立来源列表。默认来源是 `cursimple/cursimple-plugins` 和 `cursimple/cursimple-components`，也可在设置中添加其他注册表或单个包的 Release 仓库；一个来源加载失败不影响其他来源。

公有来源可直接读取。私有仓库使用具有目标仓库只读权限的 GitHub 令牌，在应用内加密保存；退出或更换 GitHub 账号后清理账号市场缓存。学校账号和学习平台账号在对应插件或组件中管理。

## 界面预览

<div align="center">

| 课表周视图 | 课表日视图 | 备忘录 |
|:--:|:--:|:--:|
| <img src="docs/screenshots/week.png" width="230"> | <img src="docs/screenshots/day.png" width="230"> | <img src="docs/screenshots/memo.png" width="230"> |
| 节次平铺一屏，自己的事务按真实时间排进课表 | 左右滑动切换日期，事务插在各节课之间 | 每门课一个笔记本，清单、优先级与截止时间 |

| 双指缩放 | 上课提醒 | 设置 |
|:--:|:--:|:--:|
| <img src="docs/screenshots/zoom.png" width="230"> | <img src="docs/screenshots/notice.png" width="230"> | <img src="docs/screenshots/settings.png" width="230"> |
| 放大到 300% 任意拖动，表头与节次栏冻结 | 快上课时弹出课名、时间与地点，支持状态栏胶囊 | 搜索直达，「常用」放哪几项自己定 |

| 提醒设置 | 管理列表 | 插件市场 |
|:--:|:--:|:--:|
| <img src="docs/screenshots/reminder.png" width="230"> | <img src="docs/screenshots/library.png" width="230"> | <img src="docs/release-notes/images/v0.7.5/plugin-market.png" width="230" alt="插件市场"> |
| 上课提醒与闹钟分开管理，按机型引导放行 | 课程与事务集中管理，可搜索 | 搜索插件与组件，查看来源、版本与安装状态 |

| 今日课程概览 | 备忘录全文搜索 | 闹钟响铃 |
|:--:|:--:|:--:|
| <img src="docs/release-notes/images/v0.7.5/agenda.png" width="230" alt="今日课程时间线"> | <img src="docs/release-notes/images/v0.7.5/memo-search.png" width="230" alt="备忘录全文搜索"> | <img src="docs/release-notes/images/v0.7.5/alarm-ringing.png" width="230" alt="闹钟响铃界面"> |
| 当前与下一节课、倒计时、全天时间线和冲突明细 | 跨笔记本搜索标题、全文与课程，包括已完成笔记 | 大时钟、日期与主题配色，滑动关闭或延后 |

| 课程日历小组件 | 待完成小组件 | 写入系统时钟 |
|:--:|:--:|:--:|
| <img src="docs/release-notes/images/v0.7.5/calendar-widget.png" width="230" alt="课程日历小组件"> | <img src="docs/release-notes/images/v0.7.5/pending-widget.png" width="230" alt="待完成小组件"> | <img src="docs/release-notes/images/v0.7.5/system-clock.png" width="230" alt="写入系统时钟确认"> |
| 周课表与月历切换，点日期打开课表 | 显示启用组件的未完成内容与截止状态 | 手动发送明天闹钟，需到系统时钟核对 |

</div>

截图使用演示数据，部分界面来自已公开版本。

## 从源码构建

使用 **JDK 17** 和含 `platforms;android-36` 的 Android SDK：

```bash
./gradlew assembleDebug       # com.x500x.cursimple.ci，可与正式安装包共存
./gradlew assembleRelease     # 各架构包与通用包，需要本地签名配置
./gradlew testDebugUnitTest lintDebug
```

版本在 `gradle.properties` 中维护：

```properties
app.versionCode=35
app.versionName=0.7.10
app.releaseChannel=beta
```

Release 签名配置参考 [keystore.example.properties](keystore.example.properties)，详细步骤见 [开发说明](README_dev.md)。当前源码的宿主组件接口为 **API 11**，安装前会校验接口与最低版本要求。

| 模块 | 职责 |
|---|---|
| `app` | 入口、依赖组装、设置、更新、通知与通用组件宿主 |
| `core-kernel` | 统一模型与核心协议 |
| `core-plugin` | 包清单、安装、Web 会话与注册表接口 |
| `core-data` | 数据仓储、配置、笔记图片与分享协议 |
| `core-reminder` | 提醒规则、计划和派发 |
| `feature-schedule` | 课表、事务、笔记和编辑界面 |
| `feature-plugin` | 插件与组件市场、组件页面和共用界面 |
| `feature-widget` | 桌面展示、操作与刷新 |

编写或适配插件、组件请看[开发指南](docs/plugin-system.md)。组件的业务、页面与平台协议由组件维护，主体提供通用主题、通知、存储和课表能力。

## 常见问题

<details>
<summary>通知没弹出或闹钟不响</summary>

在提醒页面检查系统通知、渠道重要性、精确闹钟和后台权限，再检查勿扰模式、电池限制与机型设置。强制停止应用会清理计划，需要重新打开应用；系统时钟中的闹钟请单独核对。

</details>

<details>
<summary>找不到组件或安装失败</summary>

检查对应来源是否启用、网络是否可用、仓库是否包含 Release 的组件 ZIP 和下载清单。私有来源需要正确的只读权限；提示版本不兼容时先检查宿主和组件的版本要求。

</details>

<details>
<summary>笔记分享码或二维码装不下</summary>

图文笔记和大量笔记请选择课简笔记文件。接收方从「导入课简笔记」选择文件，预览确认后归入同名课程；已有内容不会被覆盖。

</details>

<details>
<summary>桌面小组件没有刷新</summary>

检查组件是否安装并启用、是否提供了桌面卡片，以及应用的后台运行权限；也可移除后重新添加。华为桌面注意区分原生服务卡片与 Android 窗口小工具入口。

</details>

## 反馈与参与

- [反馈缺陷或建议](https://github.com/cursimple/cursimple-app/issues)
- [使用交流](https://github.com/cursimple/cursimple-app/discussions)
- [贡献学校插件](https://github.com/cursimple/cursimple-plugins)
- [贡献扩展组件](https://github.com/cursimple/cursimple-components#contribute)

## 开源许可

[MIT License](LICENSE) · Copyright © 2026 CurSimple 团队
