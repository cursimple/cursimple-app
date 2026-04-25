# 课表查看（Class Schedule Viewer）设计文档

日期：2026-04-25  
状态：已确认  
范围：Android App（最低 Android 7，目标 Android 16）

## 1. 目标与约束

- 中文名：课表查看
- 技术栈：Kotlin + Jetpack Compose
- 架构：微内核架构
- 课程数据获取：登录到课表拉取全流程由 JS 插件完成
- JS 引擎：QuickJS
- ABI 构建输出：`armeabi-v7a`、`arm64-v8a`、`universal`
- 小组件：桌面小组件展示今日/近期课程
- CI/CD：GitHub Actions 自动构建与产物上传
- 版本支持：`minSdk=24`，`targetSdk=36`（Android 16）

## 2. 总体架构（微内核）

采用“壳 + 核心 + 插件执行器 + 功能模块”方式：

- `app`：应用壳、导航、DI 组装、资源与插件资产
- `core-kernel`：微内核抽象（插件目录、执行协议、统一数据模型）
- `core-js`：QuickJS 运行时与 JS 插件执行器（含受控桥接能力）
- `core-data`：课表与配置持久化（DataStore）
- `feature-schedule`：课表主界面（Compose）与同步交互
- `feature-widget`：Glance 小组件与刷新调度

核心依赖方向：

1. 业务层只依赖 `core-kernel` 抽象；
2. `core-js` 实现 `core-kernel` 定义的执行接口；
3. `app` 在组装层将 `core-js` 与 `core-data` 注入给功能模块。

## 3. JS 插件协议

每个插件文件（`*.js`）需导出三个函数：

- `login(ctx, input)`：登录，返回会话信息 JSON
- `fetchSchedule(ctx, session, term)`：拉取原始课表 JSON
- `normalize(raw)`：转换为统一 `ScheduleJson`

统一输出的 `ScheduleJson` 由宿主进行 Schema 校验并落库，避免 UI 直接依赖学校站点结构。

## 4. 受控桥接能力

JS 插件通过 `Host` 调用受控能力（同步调用）：

- `Host.httpRequest(requestJson)`：HTTP 请求
- `Host.log(message)`：日志输出
- `Host.nowIso()`：获取当前时间（便于调试与签名逻辑）

限制策略：

- 执行超时（防死循环）
- 内存限制（QuickJS 运行时参数）
- 统一异常映射（登录失败、网络失败、脚本错误、协议错误）

## 5. 数据流

1. 用户在主界面输入账号与密码，选择插件与学期；
2. 宿主加载插件脚本，调用 `login -> fetchSchedule -> normalize`；
3. 宿主校验 `ScheduleJson` 后写入 DataStore；
4. Compose 页面从仓储读取并渲染；
5. 小组件读取同一份数据并展示今日课程。

## 6. 小组件设计

- 使用 `androidx.glance.appwidget`；
- 展示：
  - 日期
  - 今日课程列表（课程名、时间、教室）
  - 空状态文案
- 刷新策略：
  - App 手动同步后立即触发更新；
  - 周期性 WorkManager 刷新；
  - 支持手动点击刷新动作（后续可增强）。

## 7. 构建与发布

- Gradle ABI split：
  - `armeabi-v7a`
  - `arm64-v8a`
  - `universalApk=true`
- CI（PR/Push）：
  - `ktlint`（可选）
  - `testDebugUnitTest`
  - `assembleDebug`
- CD（main/tag）：
  - `assembleRelease`
  - 上传 `v7a`、`v8a`、`universal` APK artifacts

## 8. 测试策略

- `core-kernel`：插件协议与 JSON 解析校验单测
- `core-js`：JS 调用链与异常映射单测
- `feature-schedule`：ViewModel 状态流单测
- `feature-widget`：小组件数据映射测试

## 9. 里程碑

1. 搭建多模块工程与基础依赖；
2. 打通 JS 插件执行与课表标准模型；
3. 完成 Compose 主界面 + 同步流程；
4. 完成 Glance 小组件；
5. 接入 GitHub Actions 与 ABI 产物输出。

