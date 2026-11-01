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

internal fun WidgetSizeClass.reminderRows(): Int = when (this) {
    WidgetSizeClass.Compact -> 2
    WidgetSizeClass.Regular -> 3
    WidgetSizeClass.Expanded -> 4
}

/** The list scrolls, so every course of the day stays reachable at any widget size. */
internal fun visibleNextCourseRows(rows: List<NextCourseRow>): List<NextCourseRow> =
    rows.take(NEXT_COURSE_MAX_ROWS)

/** A guard against runaway data; no real day comes close. */
private const val NEXT_COURSE_MAX_ROWS = 30

internal fun visibleReminderRows(
    rows: List<ReminderRowData>,
    sizeClass: WidgetSizeClass,
): List<ReminderRowData> = rows.take(sizeClass.reminderRows())
