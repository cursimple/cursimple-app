# 课表与小组件设置完善设计

## 背景

本次调整聚焦设置体验和桌面小组件同步：

- 桌面小组件主题色和背景图片修改后需要实时刷新。
- 小组件设置新增“点击小组件打开 App”开关，默认关闭。
- 调整课表外观/显示默认值。
- 课表背景支持“与表头背景一致”。
- 课表设置内提供恢复课表外观/显示默认值，设置根部提供恢复所有设置。

## 设计

### 小组件偏好

在 `WidgetThemePreferences` 中加入 `openAppOnClickEnabled`，默认 `false`。设置页新增开关，持久化到小组件 DataStore。开启后，为小组件主体背景区域绑定打开主 Activity 的 `PendingIntent`；每日课程小组件的前进、后退、回到今天按钮保持原有点击行为。

小组件偏好写入后仍由 ViewModel 主动刷新，同时在 Application 层监听 `themePreferencesFlow`。当主题色、背景模式、背景图片 URI 或打开 App 开关变化时，调用统一的小组件刷新，确保非设置页入口修改偏好时也能刷新桌面小组件。

### 课表偏好

调整默认值：

- `teacherVisible = true`
- `scheduleOpacityPercent = 0`
- `inactiveCourseOpacityPercent = 50`

新增 `ScheduleBackgroundType.Header`，表示课表背景颜色跟随当前天表头背景颜色。渲染时使用当前明暗主题下解析后的表头背景色，不引入兜底式架构。

### 恢复默认

`UserPreferencesRepository` 新增两个重置方法：

- `resetScheduleAppearanceAndDisplay()`：重置文字样式、表头、卡片样式、课表背景、显示选项，并释放课表背景图片权限。
- `resetAllSettings()`：重置用户设置 DataStore 中的应用设置、课表外观/显示、提醒运行参数、更新设置、开发者调试等设置项，并释放课表背景图片权限；不清除课表数据、插件安装数据、手动课程或提醒规则。

`WidgetPreferencesRepository` 新增 `resetWidgetThemePreferences()`，用于根部“恢复所有设置”同时重置小组件主题、背景图片和打开 App 开关。

### UI

课表设置页增加“恢复课表外观/显示默认值”入口，点击弹确认框。设置根部增加“恢复所有设置”入口，点击弹确认框。两个动作都只在用户确认后执行。

## 验证

- 更新偏好默认值单元测试。
- 新增重置方法相关单元测试。
- 运行 `pwsh -NoLogo -NoProfile -Command './gradlew :core-data:test :feature-schedule:test :feature-widget:test'`。
