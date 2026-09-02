package com.x500x.cursimple.app.holiday

import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import java.time.LocalDate

/** 假期前一天要不要提示，以及提示什么。 */
sealed interface HolidayEveNotice {
    /** 明天放假但仍排着提醒，建议关掉。 */
    data class SuggestMute(
        val date: LocalDate,
        val holidayName: String?,
        val holidayNameRes: Int?,
        val reminderCount: Int,
    ) : HolidayEveNotice

    /** 不需要提示。 */
    data object None : HolidayEveNotice
}

/**
 * 判定假期前一天的提示。
 *
 * 只在明天确实放假、当天还排着提醒、且用户没有用其它方式关掉时才提示：
 * 已经打开假日跳过提醒，或已经把明天静音的，都不再打扰。
 */
fun holidayEveNotice(
    today: LocalDate,
    holidayCalendar: HolidayCalendarSettings,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    skipRemindersOnHoliday: Boolean,
    mutedDates: Set<LocalDate>,
    reminderCountOn: (LocalDate) -> Int,
): HolidayEveNotice {
    if (skipRemindersOnHoliday) return HolidayEveNotice.None
    val tomorrow = today.plusDays(1)
    if (tomorrow in mutedDates) return HolidayEveNotice.None
    val resolution = resolveScheduleDay(tomorrow, temporaryScheduleOverrides, holidayCalendar)
    if (!resolution.isHoliday) return HolidayEveNotice.None
    val count = reminderCountOn(tomorrow)
    if (count <= 0) return HolidayEveNotice.None
    return HolidayEveNotice.SuggestMute(
        date = tomorrow,
        holidayName = resolution.holidayName,
        holidayNameRes = resolution.holidayNameRes,
        reminderCount = count,
    )
}
