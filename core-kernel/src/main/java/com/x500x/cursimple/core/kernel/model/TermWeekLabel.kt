package com.x500x.cursimple.core.kernel.model

import android.content.Context
import com.x500x.cursimple.core.kernel.R
import java.time.LocalDate

/** Typed teaching-week state, localized outside pure logic. */
sealed interface TermWeekLabel {
    data object TermStartMissing : TermWeekLabel

    data object NotStarted : TermWeekLabel

    data class Week(val index: Int) : TermWeekLabel
}

fun termWeekLabel(termStart: LocalDate?, weekIndex: Int): TermWeekLabel = when {
    termStart == null -> TermWeekLabel.TermStartMissing
    !isTermWeekNumberStarted(weekIndex) -> TermWeekLabel.NotStarted
    else -> TermWeekLabel.Week(weekIndex)
}

/** Week-only classification for callers with a known term date. */
fun termWeekLabel(weekIndex: Int): TermWeekLabel =
    if (isTermWeekNumberStarted(weekIndex)) TermWeekLabel.Week(weekIndex) else TermWeekLabel.NotStarted

fun Context.termWeekText(label: TermWeekLabel): String = when (label) {
    TermWeekLabel.TermStartMissing -> getString(R.string.kernel_week_term_start_missing)
    TermWeekLabel.NotStarted -> getString(R.string.kernel_week_not_started)
    is TermWeekLabel.Week -> getString(R.string.kernel_week_index, label.index)
}

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

/**
 * Use explicit short weekday resources; system narrow labels can be identical in some locales.
 */
fun weekdayNarrowRes(dayOfWeek: Int): Int = when (dayOfWeek) {
    1 -> R.string.kernel_weekday_narrow_monday
    2 -> R.string.kernel_weekday_narrow_tuesday
    3 -> R.string.kernel_weekday_narrow_wednesday
    4 -> R.string.kernel_weekday_narrow_thursday
    5 -> R.string.kernel_weekday_narrow_friday
    6 -> R.string.kernel_weekday_narrow_saturday
    7 -> R.string.kernel_weekday_narrow_sunday
    else -> R.string.kernel_weekday_unknown
}
