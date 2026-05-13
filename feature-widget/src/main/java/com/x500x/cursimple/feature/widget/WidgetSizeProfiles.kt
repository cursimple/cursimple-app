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
