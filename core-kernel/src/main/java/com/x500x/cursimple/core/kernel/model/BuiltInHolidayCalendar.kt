package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate

/**
 * 内置的中国大陆节假日数据，随应用一起发布，不联网获取。
 *
 * 只收录来源确定的日期：
 * 一是《全国年节及纪念日放假办法》规定的全体公民放假日（元旦、除夕至正月初三、清明、劳动节两天、
 * 端午、中秋、国庆三天，全年 13 天）；
 * 二是上述假日适逢周六周日时按同一办法顺延的补假，以及被补假夹在中间的周末。
 *
 * 每年由国务院办公厅另行通知决定的调休连休与补班日期不在此列，需要用户在设置里手动添加。
 */
private val BUILT_IN_HOLIDAY_ENTRIES: List<HolidayCalendarEntry> = listOf(
    HolidayCalendarEntry("2026-01-01", HolidayEntryKind.Holiday, "元旦"),
    HolidayCalendarEntry("2026-02-16", HolidayEntryKind.Holiday, "除夕"),
    HolidayCalendarEntry("2026-02-17", HolidayEntryKind.Holiday, "春节"),
    HolidayCalendarEntry("2026-02-18", HolidayEntryKind.Holiday, "春节"),
    HolidayCalendarEntry("2026-02-19", HolidayEntryKind.Holiday, "春节"),
    HolidayCalendarEntry("2026-04-05", HolidayEntryKind.Holiday, "清明节"),
    HolidayCalendarEntry("2026-04-06", HolidayEntryKind.Holiday, "清明节补假"),
    HolidayCalendarEntry("2026-05-01", HolidayEntryKind.Holiday, "劳动节"),
    HolidayCalendarEntry("2026-05-02", HolidayEntryKind.Holiday, "劳动节"),
    HolidayCalendarEntry("2026-05-03", HolidayEntryKind.Holiday, "劳动节"),
    HolidayCalendarEntry("2026-05-04", HolidayEntryKind.Holiday, "劳动节补假"),
    HolidayCalendarEntry("2026-06-19", HolidayEntryKind.Holiday, "端午节"),
    HolidayCalendarEntry("2026-09-25", HolidayEntryKind.Holiday, "中秋节"),
    HolidayCalendarEntry("2026-10-01", HolidayEntryKind.Holiday, "国庆节"),
    HolidayCalendarEntry("2026-10-02", HolidayEntryKind.Holiday, "国庆节"),
    HolidayCalendarEntry("2026-10-03", HolidayEntryKind.Holiday, "国庆节"),
    HolidayCalendarEntry("2026-10-04", HolidayEntryKind.Holiday, "国庆节"),
    HolidayCalendarEntry("2026-10-05", HolidayEntryKind.Holiday, "国庆节补假"),
)

private val BUILT_IN_HOLIDAY_INDEX: Map<LocalDate, HolidayCalendarEntry> by lazy {
    BUILT_IN_HOLIDAY_ENTRIES
        .mapNotNull { entry -> entry.localDate()?.let { it to entry } }
        .toMap()
}

/** 内置数据覆盖的年份。 */
val builtInHolidayYears: List<Int> by lazy {
    BUILT_IN_HOLIDAY_INDEX.keys.map { it.year }.distinct().sorted()
}

fun builtInHolidayEntryOn(date: LocalDate): HolidayCalendarEntry? = BUILT_IN_HOLIDAY_INDEX[date]

fun builtInHolidayEntriesOfYear(year: Int): List<HolidayCalendarEntry> =
    BUILT_IN_HOLIDAY_INDEX
        .filterKeys { it.year == year }
        .toSortedMap()
        .values
        .toList()
