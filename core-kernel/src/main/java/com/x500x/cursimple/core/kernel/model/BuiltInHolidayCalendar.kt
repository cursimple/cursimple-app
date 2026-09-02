package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.R
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
private val BUILT_IN_HOLIDAY_ENTRIES: List<BuiltInHoliday> = listOf(
    BuiltInHoliday("2026-01-01", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-02-16", HolidayEntryKind.Holiday, "除夕", R.string.kernel_holiday_spring_festival_eve),
    BuiltInHoliday("2026-02-17", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-18", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-19", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-04-05", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-04-06", HolidayEntryKind.Holiday, "清明节补假", R.string.kernel_holiday_qingming_makeup),
    BuiltInHoliday("2026-05-01", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-02", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-03", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-04", HolidayEntryKind.Holiday, "劳动节补假", R.string.kernel_holiday_labour_day_makeup),
    BuiltInHoliday("2026-06-19", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-09-25", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-10-01", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-02", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-03", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-04", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-05", HolidayEntryKind.Holiday, "国庆节补假", R.string.kernel_holiday_national_day_makeup),
)

/**
 * 内置条目在假日名之外多带一个文案资源，界面据此按当前语言显示。
 * 资源 id 每次编译都会变，只在内存里用，绝不写入持久化数据。
 */
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

/** 内置数据覆盖的年份。 */
val builtInHolidayYears: List<Int> by lazy {
    BUILT_IN_HOLIDAY_INDEX.keys.map { it.year }.distinct().sorted()
}

fun builtInHolidayEntryOn(date: LocalDate): HolidayCalendarEntry? = BUILT_IN_HOLIDAY_INDEX[date]?.toEntry()

/** 内置假日名对应的文案资源；该日不是内置假日时返回 null。 */
fun builtInHolidayNameResOn(date: LocalDate): Int? = BUILT_IN_HOLIDAY_INDEX[date]?.nameRes

fun builtInHolidayEntriesOfYear(year: Int): List<HolidayCalendarEntry> =
    BUILT_IN_HOLIDAY_INDEX
        .filterKeys { it.year == year }
        .toSortedMap()
        .values
        .map { it.toEntry() }
