package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate

/**
 * 某一天的最终课表归属。
 * [sourceDate] 是当天实际按哪一天的课表上课，[isHoliday] 为真时当天不出课也不提醒。
 */
data class ScheduleDayResolution(
    val date: LocalDate,
    val sourceDate: LocalDate,
    val isHoliday: Boolean,
    val holidayName: String?,
)

/**
 * 合并临时调课与节假日日历，得出 [date] 当天的课表归属。
 *
 * 优先级由高到低：
 * 1. 用户手动写下的节假日条目，无论是设为假日还是设为调休上课日；
 * 2. 覆盖当天的临时调课（补课/调课），它明示当天要上课，因此推翻内置假日；
 * 3. 内置节假日数据。
 *
 * 临时取消只移除指定节次的课，不参与当天是否为假日的判定。
 */
fun resolveScheduleDay(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): ScheduleDayResolution {
    val userEntry = holidayCalendar.userEntryOn(date)
    val effectiveEntry = when {
        userEntry != null -> userEntry
        matchingTemporaryScheduleOverride(date, overrides) != null -> null
        else -> holidayCalendar.builtInEntryOn(date)
    }
    val holiday = effectiveEntry?.kind == HolidayEntryKind.Holiday
    return ScheduleDayResolution(
        date = date,
        sourceDate = if (holiday) date else resolveTemporaryScheduleSourceDate(date, overrides),
        isHoliday = holiday,
        holidayName = if (holiday) effectiveEntry?.name?.takeIf { it.isNotBlank() } else null,
    )
}

/** [date] 是否为不上课的假日。 */
fun isScheduleHoliday(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): Boolean = resolveScheduleDay(date, overrides, holidayCalendar).isHoliday

/** [date] 假日名称，非假日为 null。 */
fun scheduleHolidayName(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): String? = resolveScheduleDay(date, overrides, holidayCalendar).holidayName
