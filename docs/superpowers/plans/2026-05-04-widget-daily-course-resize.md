# 桌面小组件每日课程与尺寸适配实施计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Do not run git commit unless the user explicitly requests it.

**Goal:** 将“今日课程”改为“每日课程”，解除每日课程日期切换限制，并让每日课程、下一节课、课程提醒三个桌面小组件支持 2x2/3x3 等尺寸下的稳定展示。

**Architecture:** 保留现有三个 widget provider，不新增重复尺寸入口。RemoteViews 的每日课程通过 `AppWidgetManager` options 读取实际尺寸并选择行数；Glance 的下一节课和课程提醒通过 `SizeMode.Responsive` 与 `LocalSize.current` 选择紧凑或常规内容。共享纯 Kotlin helper 承担日期标签与尺寸分档，避免把尺寸判断散落在 UI 代码里。

**Tech Stack:** Kotlin、Android AppWidget RemoteViews、AndroidX Glance、DataStore、Gradle、JUnit4、pwsh。

---

## File Structure

- Modify: `feature-widget/src/main/res/values/strings.xml`
  - 负责系统小组件名称和描述文案。
- Modify: `feature-widget/src/main/AndroidManifest.xml`
  - 注释从 Today schedule 改为 Daily schedule，receiver label 继续引用 string。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetCatalog.kt`
  - 应用内小组件选择页标题和描述。
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
  - 移除 `-1..1` 切换限制，改为明确的大范围边界。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
  - 每日课程读取 widget options、解除按钮显隐限制、按尺寸控制行数和行内容密度。
- Modify: `feature-widget/src/main/res/layout/widget_schedule_today.xml`
  - 保留整体布局，微调 2x2 下不会挤压的空间。
- Modify: `feature-widget/src/main/res/layout/widget_schedule_course_row.xml`
  - 移除左侧蓝色竖条，统一条目内边距。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetStyle.kt`
  - `AccentRow` 去掉左侧强调条，作为三个小组件的统一条目容器。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/NextCourseGlanceWidget.kt`
  - 启用 Glance 响应式尺寸，2x2 减少辅助信息和条目数。
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ReminderGlanceWidget.kt`
  - 启用 Glance 响应式尺寸，2x2 减少辅助信息和条目数。
- Modify: `feature-widget/src/main/res/xml/schedule_widget_info.xml`
  - 明确每日课程默认 3x3、允许缩到 2x2。
- Modify: `feature-widget/src/main/res/xml/next_course_widget_info.xml`
  - 明确下一节课默认 2x2、允许拉大。
- Modify: `feature-widget/src/main/res/xml/reminder_widget_info.xml`
  - 明确课程提醒默认 3x2、允许缩到 2x2/拉大到 3x3。
- Modify: `feature-widget/build.gradle.kts`
  - 增加 JUnit4 test dependency。
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetSizeProfiles.kt`
  - 纯 Kotlin 尺寸分档和日期偏移标签 helper。
- Create: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/WidgetSizeProfilesTest.kt`
  - 覆盖日期偏移标签、空状态文案和尺寸分档。

## Chunk 1: 文案与日期切换

### Task 1: 写日期偏移 helper 和测试

**Files:**
- Create: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetSizeProfiles.kt`
- Create: `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/WidgetSizeProfilesTest.kt`
- Modify: `feature-widget/build.gradle.kts`

- [ ] **Step 1: 增加测试依赖**

在 `feature-widget/build.gradle.kts` 的 dependencies 中加入：

```kotlin
testImplementation(libs.junit4)
```

- [ ] **Step 2: 写失败测试**

创建 `feature-widget/src/test/java/com/kebiao/viewer/feature/widget/WidgetSizeProfilesTest.kt`：

```kotlin
package com.kebiao.viewer.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeProfilesTest {
    @Test
    fun `day offset labels support unbounded daily switching`() {
        assertEquals("昨天", WidgetDayLabels.tag(-1))
        assertEquals("今天", WidgetDayLabels.tag(0))
        assertEquals("明天", WidgetDayLabels.tag(1))
        assertEquals("+7天", WidgetDayLabels.tag(7))
        assertEquals("-8天", WidgetDayLabels.tag(-8))
    }

    @Test
    fun `empty labels stay date aware`() {
        assertEquals("昨日没有课程", WidgetDayLabels.empty(-1))
        assertEquals("今日没有课程，享受一天", WidgetDayLabels.empty(0))
        assertEquals("明日没有课程", WidgetDayLabels.empty(1))
        assertEquals("当日没有课程", WidgetDayLabels.empty(5))
    }

    @Test
    fun `size profiles distinguish compact and regular widgets`() {
        assertEquals(WidgetSizeClass.Compact, WidgetSizeClass.fromDp(widthDp = 160, heightDp = 120))
        assertEquals(WidgetSizeClass.Regular, WidgetSizeClass.fromDp(widthDp = 220, heightDp = 180))
        assertEquals(WidgetSizeClass.Expanded, WidgetSizeClass.fromDp(widthDp = 300, heightDp = 260))
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:testDebugUnitTest --tests "*WidgetSizeProfilesTest"'
```

Expected: FAIL，提示 `WidgetDayLabels` 或 `WidgetSizeClass` 未定义。

- [ ] **Step 4: 实现 helper**

创建 `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetSizeProfiles.kt`：

```kotlin
package com.kebiao.viewer.feature.widget

internal enum class WidgetSizeClass {
    Compact,
    Regular,
    Expanded;

    companion object {
        fun fromDp(widthDp: Int, heightDp: Int): WidgetSizeClass = when {
            widthDp >= 280 && heightDp >= 240 -> Expanded
            widthDp >= 200 && heightDp >= 160 -> Regular
            else -> Compact
        }
    }
}

internal object WidgetDayLabels {
    fun tag(offset: Int): String = when (offset) {
        -1 -> "昨天"
        0 -> "今天"
        1 -> "明天"
        else -> if (offset > 0) "+${offset}天" else "${offset}天"
    }

    fun empty(offset: Int): String = when (offset) {
        -1 -> "昨日没有课程"
        0 -> "今日没有课程，享受一天"
        1 -> "明日没有课程"
        else -> "当日没有课程"
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:testDebugUnitTest --tests "*WidgetSizeProfilesTest"'
```

Expected: PASS。

### Task 2: 改名为每日课程

**Files:**
- Modify: `feature-widget/src/main/res/values/strings.xml`
- Modify: `feature-widget/src/main/AndroidManifest.xml`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetCatalog.kt`
- Modify: `feature-widget/src/main/res/layout/widget_preview_today.xml`

- [ ] **Step 1: 修改 string 资源**

把 `glance_appwidget_description` 改为 `课表查看 · 每日课程`，把 `widget_label_today` 改为 `每日课程`。

- [ ] **Step 2: 修改 WidgetCatalog**

把 `id = "today"` 的标题改为 `每日课程`，描述改为 `查看任意日期的课程列表，可一键切换和回到今天`。

- [ ] **Step 3: 修改 Manifest 注释和预览文案**

Manifest 注释改为 `Daily schedule`。`widget_preview_today.xml` 中可见文案从 `今日课程` 改为 `每日课程`。

- [ ] **Step 4: 检查旧文案**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command 'rg -n "今日课程|昨天/今天/明天" feature-widget'
```

Expected: 仅允许“下一节课”内部状态文本仍出现 `今日课程 · 上课中` 或 `今日课程已结束`；每日课程入口和描述不再出现旧名。

### Task 3: 解除每日课程切换限制

**Files:**
- Modify: `core-data/src/main/java/com/kebiao/viewer/core/data/widget/DataStoreWidgetPreferencesRepository.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`

- [ ] **Step 1: 扩大偏移边界**

在 `DataStoreWidgetPreferencesRepository` 中把：

```kotlin
const val MIN_OFFSET = -1
const val MAX_OFFSET = 1
```

改为明确边界，例如：

```kotlin
const val MIN_OFFSET = -3650
const val MAX_OFFSET = 3650
```

- [ ] **Step 2: 保持所有写入仍使用 coerceIn**

确认 `setWidgetDayOffset`、`shiftWidgetDayOffset`、按 appWidgetId 写入路径都继续调用 `coerceIn(MIN_OFFSET, MAX_OFFSET)`。

- [ ] **Step 3: 移除按钮限制**

在 `ScheduleGlanceWidgetReceiver.buildScheduleViews` 中把左右按钮显隐：

```kotlin
views.setViewVisibility(R.id.widget_prev, if (offset > -1) View.VISIBLE else View.INVISIBLE)
views.setViewVisibility(R.id.widget_next, if (offset < 1) View.VISIBLE else View.INVISIBLE)
```

改为始终可见：

```kotlin
views.setViewVisibility(R.id.widget_prev, View.VISIBLE)
views.setViewVisibility(R.id.widget_next, View.VISIBLE)
```

- [ ] **Step 4: 使用 helper 标签**

把本文件私有 `offsetTagLabel`、`offsetEmptyLabel` 删除，改为：

```kotlin
views.setTextViewText(R.id.widget_title, "${dateFormatter.format(targetDate)} · ${WidgetDayLabels.tag(offset)}")
views.setTextViewText(R.id.widget_empty, WidgetDayLabels.empty(offset))
```

## Chunk 2: 三个小组件尺寸适配

### Task 4: 调整 appwidget-provider 尺寸声明

**Files:**
- Modify: `feature-widget/src/main/res/xml/schedule_widget_info.xml`
- Modify: `feature-widget/src/main/res/xml/next_course_widget_info.xml`
- Modify: `feature-widget/src/main/res/xml/reminder_widget_info.xml`

- [ ] **Step 1: 每日课程允许缩到 2x2**

保留 `targetCellWidth="3"`、`targetCellHeight="3"`，把 `minResizeWidth` 调整到约 `160dp`，`minResizeHeight` 调整到约 `110dp`。

- [ ] **Step 2: 下一节课保留 2x2 默认**

保留 `targetCellWidth="2"`、`targetCellHeight="2"`，确认 `resizeMode="horizontal|vertical"` 和最小 resize 尺寸存在。

- [ ] **Step 3: 课程提醒支持 2x2 到 3x3**

保留默认 `targetCellWidth="3"`、`targetCellHeight="2"`，把 `minResizeWidth` 调整到约 `160dp`，`minResizeHeight` 调整到约 `110dp`。

### Task 5: 每日课程按 RemoteViews 实际尺寸控制密度

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ScheduleGlanceWidgetReceiver.kt`
- Modify: `feature-widget/src/main/res/layout/widget_schedule_today.xml`
- Modify: `feature-widget/src/main/res/layout/widget_schedule_course_row.xml`

- [ ] **Step 1: 读取 AppWidget options**

在 `updateWidgets` 中为每个 id 读取：

```kotlin
val options = manager.getAppWidgetOptions(appWidgetId)
val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
val sizeClass = WidgetSizeClass.fromDp(widthDp, heightDp)
```

并把 `sizeClass` 传入 `buildScheduleViews`。

- [ ] **Step 2: 监听尺寸变化**

在 `ScheduleGlanceWidgetReceiver` 中覆盖：

```kotlin
override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    updateWidgets(context.applicationContext, intArrayOf(appWidgetId))
}
```

同时添加 `android.os.Bundle` import。

- [ ] **Step 3: 按尺寸控制行数**

把固定 `MAX_ROWS` 改为：

```kotlin
private fun maxRows(sizeClass: WidgetSizeClass): Int = when (sizeClass) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 3
    WidgetSizeClass.Expanded -> 5
}
```

然后使用 `courses.take(maxRows(sizeClass))`。

- [ ] **Step 4: 移除每日课程蓝色竖条**

在 `widget_schedule_course_row.xml` 删除 `@id/course_accent` 这个 `TextView`，把内容容器改为占满宽度，并把 `paddingStart` 调整为 `12dp`。

- [ ] **Step 5: 调整 2x2 可用空间**

在 `widget_schedule_today.xml` 中确认头部高度、外边距和 `widget_reset` 不会挤掉列表。必要时把 reset 高度保持 `24dp`，不增加额外文案。

### Task 6: Glance 小组件按尺寸控制内容

**Files:**
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/NextCourseGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/ReminderGlanceWidget.kt`
- Modify: `feature-widget/src/main/java/com/kebiao/viewer/feature/widget/WidgetStyle.kt`

- [ ] **Step 1: 下一节课启用 Responsive size mode**

在 `NextCourseGlanceWidget` 中添加：

```kotlin
override val sizeMode = SizeMode.Responsive(
    setOf(
        DpSize(160.dp, 120.dp),
        DpSize(220.dp, 180.dp),
        DpSize(300.dp, 260.dp),
    ),
)
```

在 `provideContent` 内读取 `val sizeClass = WidgetSizeClass.fromDp(LocalSize.current.width.value.toInt(), LocalSize.current.height.value.toInt())`。

- [ ] **Step 2: 下一节课控制展示条目和辅助信息**

Compact 下最多展示当前/下一节和 1 条辅助课程；Regular/Expanded 保持现有列表，Expanded 可展示更多 `annotated`。

- [ ] **Step 3: 课程提醒启用 Responsive size mode**

在 `ReminderGlanceWidget` 中添加同样的 `sizeMode`，并按尺寸决定 `plans.take(count)`：

```kotlin
val maxPlans = when (sizeClass) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 3
    WidgetSizeClass.Expanded -> 4
}
```

- [ ] **Step 4: 统一 AccentRow 样式**

在 `WidgetStyle.AccentRow` 删除左侧 `Box(width = accentWidth)` 和紧随的 `Spacer`。保留背景、圆角和内边距：

```kotlin
Box(
    modifier = GlanceModifier
        .padding(horizontal = WidgetStyle.rowPaddingH, vertical = WidgetStyle.rowPaddingV)
        .fillMaxWidth(),
) {
    content()
}
```

保留 `accent` 参数可先不使用，随后确认无调用警告；如果 Kotlin 报未使用警告不阻断编译，可以暂留以减少调用点改动。

## Chunk 3: 验证

### Task 7: 自动验证

**Files:**
- None

- [ ] **Step 1: 跑新增单元测试**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:testDebugUnitTest --tests "*WidgetSizeProfilesTest"'
```

Expected: PASS。

- [ ] **Step 2: 编译三个小组件所在模块和 app**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :feature-widget:assembleDebug :app:assembleDebug'
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 搜索旧入口文案**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command 'rg -n "widget_label_today|今日课程|昨天/今天/明天" feature-widget'
```

Expected: `widget_label_today` 的值为 `每日课程`；每日课程入口与描述不再叫“今日课程”。

### Task 8: 手动验证

**Files:**
- None

- [ ] **Step 1: 安装 debug 包到设备或模拟器**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command './gradlew :app:installDebug'
```

Expected: 安装成功。

- [ ] **Step 2: 验证每日课程切换**

在桌面添加每日课程小组件，连续点下一天至少 5 次、上一天至少 5 次，再点“回今天”。

Expected: 日期连续变化，左右按钮始终可用，回今天后标题显示今天。

- [ ] **Step 3: 验证尺寸**

分别添加每日课程、下一节课、课程提醒，长按拖拽到 2x2、3x3 和更大尺寸。

Expected: 三个小组件内容不重叠，2x2 显示紧凑内容，3x3 显示更完整列表。

- [ ] **Step 4: 验证样式**

观察三个小组件的课程或提醒条目。

Expected: 条目左侧没有偏蓝色竖条，浅色卡片风格一致。

### Task 9: 收尾但不提交

**Files:**
- None

- [ ] **Step 1: 查看工作区状态**

Run:

```powershell
pwsh -NoLogo -NoProfile -Command 'git status --short'
```

Expected: 只出现本计划相关文档和本功能代码改动。

- [ ] **Step 2: 不执行 commit**

不要运行 `git add` 或 `git commit`。如用户之后明确要求提交，再使用项目要求格式：

```text
feat: 优化桌面小组件每日课程与尺寸适配
- 将今日课程小组件改名为每日课程
- 解除每日课程日期切换限制
- 统一三个桌面小组件尺寸适配和条目样式
```
