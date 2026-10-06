package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.R
import java.time.LocalDate

/**
 * Bundled public holiday fallback; make-up class assignments remain user-configured because
 * workday notices do not specify courses.
 */
private val BUILT_IN_HOLIDAY_ENTRIES: List<BuiltInHoliday> = listOf(
    BuiltInHoliday("2026-01-01", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-01-02", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-01-03", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-02-15", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-16", HolidayEntryKind.Holiday, "除夕", R.string.kernel_holiday_spring_festival_eve),
    BuiltInHoliday("2026-02-17", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-18", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-19", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-20", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-21", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-22", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-23", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-04-04", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-04-05", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-04-06", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-05-01", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-02", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-03", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-04", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-05", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-06-19", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-06-20", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-06-21", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-09-25", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-09-26", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-09-27", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-10-01", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-02", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-03", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-04", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-05", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-06", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-07", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
)

/** Localized resource IDs are runtime-only and must not be persisted. */
private data class BuiltInHoliday(
    val date: String,
    val kind: HolidayEntryKind,
    val name: String,
    val nameRes: Int,
) {
    fun toEntry(): HolidayCalendarEntry = HolidayCalendarEntry(date, kind, name)
}

private val BUILT_IN_HOLIDAY_INDEX: Map<LocalDate, BuiltInHoliday> by lazy {
    BUILT_IN_HOLIDAY_ENTRIES
        .mapNotNull { entry -> runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry } }
        .toMap()
}

val builtInHolidayYears: List<Int> by lazy {
    BUILT_IN_HOLIDAY_INDEX.keys.map { it.year }.distinct().sorted()
}

fun builtInHolidayEntryOn(date: LocalDate): HolidayCalendarEntry? = BUILT_IN_HOLIDAY_INDEX[date]?.toEntry()

fun builtInHolidayNameResOn(date: LocalDate): Int? = BUILT_IN_HOLIDAY_INDEX[date]?.nameRes

fun builtInHolidayEntriesOfYear(year: Int): List<HolidayCalendarEntry> =
    BUILT_IN_HOLIDAY_INDEX
        .filterKeys { it.year == year }
        .toSortedMap()
        .values
        .map { it.toEntry() }
