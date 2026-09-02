package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.R
import java.time.LocalDate

/**
 * 内置的中国大陆节假日数据，随应用一起发布，不联网获取。
 *
 * 日期照抄国务院办公厅当年的节假日安排通知，含调休连休在内的完整放假区间，
 * 与日历应用显示的假期一致。
 *
 * 通知里的补班日不收录：那几天要上哪一天的课由学校自行安排，通知本身没有规定，
 * 需要用户在临时调课里按学校通知填写。
 *
 * 每年的安排在上一年末发布，跨年前需要补录下一年的数据。
 */
private val BUILT_IN_HOLIDAY_ENTRIES: List<BuiltInHoliday> = listOf(
    // 元旦 1 月 1 日至 3 日
    BuiltInHoliday("2026-01-01", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-01-02", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    BuiltInHoliday("2026-01-03", HolidayEntryKind.Holiday, "元旦", R.string.kernel_holiday_new_year),
    // 春节 2 月 15 日至 23 日
    BuiltInHoliday("2026-02-15", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-16", HolidayEntryKind.Holiday, "除夕", R.string.kernel_holiday_spring_festival_eve),
    BuiltInHoliday("2026-02-17", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-18", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-19", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-20", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-21", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-22", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    BuiltInHoliday("2026-02-23", HolidayEntryKind.Holiday, "春节", R.string.kernel_holiday_spring_festival),
    // 清明节 4 月 4 日至 6 日
    BuiltInHoliday("2026-04-04", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-04-05", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    BuiltInHoliday("2026-04-06", HolidayEntryKind.Holiday, "清明节", R.string.kernel_holiday_qingming),
    // 劳动节 5 月 1 日至 5 日
    BuiltInHoliday("2026-05-01", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-02", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-03", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-04", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    BuiltInHoliday("2026-05-05", HolidayEntryKind.Holiday, "劳动节", R.string.kernel_holiday_labour_day),
    // 端午节 6 月 19 日至 21 日
    BuiltInHoliday("2026-06-19", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-06-20", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    BuiltInHoliday("2026-06-21", HolidayEntryKind.Holiday, "端午节", R.string.kernel_holiday_dragon_boat),
    // 中秋节 9 月 25 日至 27 日
    BuiltInHoliday("2026-09-25", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-09-26", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    BuiltInHoliday("2026-09-27", HolidayEntryKind.Holiday, "中秋节", R.string.kernel_holiday_mid_autumn),
    // 国庆节 10 月 1 日至 7 日
    BuiltInHoliday("2026-10-01", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-02", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-03", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-04", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-05", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-06", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
    BuiltInHoliday("2026-10-07", HolidayEntryKind.Holiday, "国庆节", R.string.kernel_holiday_national_day),
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
