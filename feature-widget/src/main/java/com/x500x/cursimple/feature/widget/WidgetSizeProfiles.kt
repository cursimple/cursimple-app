package com.x500x.cursimple.feature.widget

import java.time.LocalDate

internal enum class WidgetSizeClass {
    Compact,
    Regular,
    Expanded;

    companion object {
        fun fromDp(widthDp: Int, heightDp: Int): WidgetSizeClass = when {
            widthDp >= EXPANDED_MIN_WIDTH_DP && heightDp >= EXPANDED_MIN_HEIGHT_DP -> Expanded
            widthDp >= REGULAR_MIN_WIDTH_DP && heightDp >= REGULAR_MIN_HEIGHT_DP -> Regular
            else -> Compact
        }

        private const val REGULAR_MIN_WIDTH_DP = 200
        private const val REGULAR_MIN_HEIGHT_DP = 160
        private const val EXPANDED_MIN_WIDTH_DP = 280
        private const val EXPANDED_MIN_HEIGHT_DP = 240
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

    /** 开学前的空态文案，避免被误读成课表没导入成功。 */
    fun beforeTermStart(termStartDate: LocalDate?): String = termStartDate
        ?.let { "未开学 · ${it.monthValue}月${it.dayOfMonth}日开学" }
        ?: "未开学"

    /** 没有开学日期就算不出教学周，空态要和「今天没课」区分开。 */
    fun missingTermStart(): String = "未设开学日期 · 点按设置"
}

/** 假日名为空时的兜底称呼。 */
internal fun widgetHolidayLabel(holidayName: String?): String =
    holidayName?.takeIf { it.isNotBlank() } ?: "放假"

/**
 * 今日课表小组件的空态文案。
 * 放假优先于学期状态：课表是否同步、是否已开学，都不如“这天不上课”贴近实际。
 */
internal fun scheduleWidgetEmptyText(
    termStartMissing: Boolean,
    beforeTermStart: Boolean,
    termStartDate: LocalDate?,
    offset: Int,
    holidayLabel: String? = null,
): String = when {
    holidayLabel != null -> "$holidayLabel · 全天无课"
    termStartMissing -> WidgetDayLabels.missingTermStart()
    beforeTermStart -> WidgetDayLabels.beforeTermStart(termStartDate)
    else -> WidgetDayLabels.empty(offset)
}

/** 今日课表小组件副标题：星期几，再补上假日、学期状态或临时调课来源。 */
internal fun scheduleWidgetSubtitle(
    weekdayLabel: String,
    termStartMissing: Boolean,
    beforeTermStart: Boolean,
    sourceLabel: String?,
    holidayLabel: String? = null,
): String = when {
    holidayLabel != null -> "$weekdayLabel · $holidayLabel"
    termStartMissing -> "$weekdayLabel · 未设开学日期"
    beforeTermStart -> "$weekdayLabel · 未开学"
    sourceLabel != null -> "$weekdayLabel · 按${sourceLabel}课"
    else -> weekdayLabel
}

/** 下一节课小组件的空态文案。 */
internal fun nextCourseEmptyTitle(
    weekIndex: Int?,
    termStartDate: LocalDate?,
    targetDate: LocalDate,
    today: LocalDate,
    hasCourses: Boolean,
    holidayLabel: String? = null,
): String = when {
    holidayLabel != null -> "$holidayLabel · 全天无课"
    weekIndex == null -> WidgetDayLabels.missingTermStart()
    isBeforeTermStart(weekIndex) -> WidgetDayLabels.beforeTermStart(termStartDate)
    targetDate == today && hasCourses -> "今天没有更多课程"
    targetDate == today -> "今天没有课程"
    targetDate == today.plusDays(1) -> "明天没有课程"
    else -> "当天没有课程"
}

internal fun WidgetSizeClass.dailyCourseRows(): Int = when (this) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 3
    WidgetSizeClass.Expanded -> 5
}

internal fun WidgetSizeClass.nextCourseRows(): Int = when (this) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 4
    WidgetSizeClass.Expanded -> 5
}

internal fun WidgetSizeClass.reminderRows(): Int = when (this) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 3
    WidgetSizeClass.Expanded -> 4
}

/** 下一节课列表按尺寸档案裁剪，小尺寸不再靠滚动塞下整天。 */
internal fun visibleNextCourseRows(
    rows: List<NextCourseRow>,
    sizeClass: WidgetSizeClass,
): List<NextCourseRow> = rows.take(sizeClass.nextCourseRows())

/** 提醒列表按尺寸档案裁剪；总数仍由标题角标给出。 */
internal fun visibleReminderRows(
    rows: List<ReminderRowData>,
    sizeClass: WidgetSizeClass,
): List<ReminderRowData> = rows.take(sizeClass.reminderRows())
