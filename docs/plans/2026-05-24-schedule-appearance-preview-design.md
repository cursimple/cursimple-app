# 课表外观预览页设计

日期：2026-05-24
状态：已确认
范围：设置页、课表外观静态预览

## 1. 目标

- 在“设置 -> 课表设置 -> 外观”页面顶部新增课表预览。
- 预览是静态示意，不提供点击、滚动、编辑或周切换交互。
- 预览实时反映当前外观设置，包括文字、表头、卡片、背景、边框和显示项。
- 预览视觉尽量贴近真实课表，方便用户调整外观时直接看到效果。

## 2. 推荐方案

在 `feature-schedule` 中新增专用轻量预览 Composable，并在 `app` 模块的设置页调用。

该预览复用现有课表外观偏好模型：

- `ScheduleTextStylePreferences`
- `ScheduleCardStylePreferences`
- `ScheduleBackgroundPreferences`
- `ScheduleDisplayPreferences`
- `scheduleCustomColorsAdaptToTheme`

预览使用固定示例课程和固定表头日期，只渲染一个压缩周课表片段。这样可以保证外观和真实课表一致，同时避免把完整 `ScheduleScreen` 的同步、选择、弹窗和手势交互带入设置页。

## 3. 备选方案

### 方案 A：设置页手写假预览

直接在 `SettingsScreen.kt` 内绘制一个假课表卡片。改动少，但文字颜色适配、表头背景、卡片透明度、边框、背景图片等逻辑容易和真实课表脱节，后续维护成本更高。

### 方案 B：嵌入完整课表页裁剪版

复用完整 `ScheduleScreen`。一致性最高，但依赖过重，会引入同步状态、选择状态、手势、弹窗和较多无关参数，不适合作为设置页里的静态示意。

### 方案 C：新增轻量预览组件

新增 `ScheduleAppearancePreview`。它只关心外观偏好和少量示例数据，不参与真实课表业务状态。推荐采用该方案。

## 4. 界面设计

在“外观”页面顶部展示一个预览区域：

- 使用圆角容器承载预览。
- 顶部显示一行周表头。
- 左侧显示节数和时间，受“节数栏显示时间”控制。
- 主区域显示若干示例课程卡片。
- 课程卡片展示课程名、地点和老师，受地点、地点 `@` 前缀、老师显示设置控制。
- 背景支持“与表头背景一致”、纯色背景和图片背景。
- 边框颜色、透明度、粗细、虚线跟随卡片样式设置。

预览不展示说明性长文案，只作为即时视觉反馈。

## 5. 数据与状态

不新增持久化字段，不修改 DataStore。

设置页已有外观偏好参数，直接传给预览组件。用户调整任意外观项后，Compose 重组会即时刷新预览。

## 6. 验证方式

- 编译 `:feature-schedule` 和 `:app`，确保新 Composable 与设置页调用通过。
- 优先运行：
  - `pwsh -NoLogo -NoProfile -Command './gradlew.bat :feature-schedule:compileDebugKotlin :app:compileDebugKotlin'`
- 如编译任务不可用，运行：
  - `pwsh -NoLogo -NoProfile -Command './gradlew.bat :app:assembleDebug'`

## 7. 约束

- 不把“兜底设计”当成正式架构手段。
- 不引入“临时兜底后续再删”的回退方案。
- 写入设计与计划文档后不执行 git commit，提交由用户明确发起。
