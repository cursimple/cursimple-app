package com.x500x.cursimple.feature.widget

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
