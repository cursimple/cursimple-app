package com.x500x.cursimple.core.kernel.model

import android.content.Context
import com.x500x.cursimple.core.kernel.R
import java.time.LocalDate

/**
 * 教学周在界面上的三种状态。
 * 逻辑层只判定处于哪一种，文字由界面层按当前语言渲染，纯逻辑因此不依赖 Context。
 */
sealed interface TermWeekLabel {
    /** 没有开学日期，算不出周次。 */
    data object TermStartMissing : TermWeekLabel

    /** 有开学日期，但还没到第 1 周。 */
    data object NotStarted : TermWeekLabel

    data class Week(val index: Int) : TermWeekLabel
}

fun termWeekLabel(termStart: LocalDate?, weekIndex: Int): TermWeekLabel = when {
    termStart == null -> TermWeekLabel.TermStartMissing
    !isTermWeekNumberStarted(weekIndex) -> TermWeekLabel.NotStarted
    else -> TermWeekLabel.Week(weekIndex)
}

/** 只按周次判定，供已经确认存在开学日期的调用方使用。 */
fun termWeekLabel(weekIndex: Int): TermWeekLabel =
    if (isTermWeekNumberStarted(weekIndex)) TermWeekLabel.Week(weekIndex) else TermWeekLabel.NotStarted

fun Context.termWeekText(label: TermWeekLabel): String = when (label) {
    TermWeekLabel.TermStartMissing -> getString(R.string.kernel_week_term_start_missing)
    TermWeekLabel.NotStarted -> getString(R.string.kernel_week_not_started)
    is TermWeekLabel.Week -> getString(R.string.kernel_week_index, label.index)
}

/** 星期几的文案资源 id。取值超出 1..7 时返回通用的未知文案。 */
fun weekdayNameRes(dayOfWeek: Int): Int = when (dayOfWeek) {
    1 -> R.string.kernel_weekday_monday
    2 -> R.string.kernel_weekday_tuesday
    3 -> R.string.kernel_weekday_wednesday
    4 -> R.string.kernel_weekday_thursday
    5 -> R.string.kernel_weekday_friday
    6 -> R.string.kernel_weekday_saturday
    7 -> R.string.kernel_weekday_sunday
    else -> R.string.kernel_weekday_unknown
}

fun Context.weekdayName(dayOfWeek: Int): String = getString(weekdayNameRes(dayOfWeek))
