package com.x500x.cursimple.app.holiday

import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import java.time.LocalDate

/** Whether and how to explain tomorrow's holiday policy. */
sealed interface HolidayEveNotice {
    /** Explain skipped alarms without overriding explicit holiday exceptions. */
    data class AutoSkip(
        val date: LocalDate,
        val holidayName: String?,
        val holidayNameRes: Int?,
        val reminderCount: Int,
    ) : HolidayEveNotice
    data class SuggestMute(
        val date: LocalDate,
        val holidayName: String?,
        val holidayNameRes: Int?,
        val reminderCount: Int,
    ) : HolidayEveNotice

    data object None : HolidayEveNotice
}

/** Automatic skipping provides an explanation; otherwise suggest manual date muting. */
fun holidayEveNotice(
    today: LocalDate,
    holidayCalendar: HolidayCalendarSettings,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    skipRemindersOnHoliday: Boolean,
    mutedDates: Set<LocalDate>,
    reminderCountOn: (LocalDate) -> Int,
): HolidayEveNotice {
    val tomorrow = today.plusDays(1)
    if (tomorrow in mutedDates) return HolidayEveNotice.None
    val resolution = resolveScheduleDay(tomorrow, temporaryScheduleOverrides, holidayCalendar)
    if (!resolution.isHoliday) return HolidayEveNotice.None
    val count = reminderCountOn(tomorrow)
    if (skipRemindersOnHoliday) return HolidayEveNotice.AutoSkip(
        tomorrow, resolution.holidayName, resolution.holidayNameRes, count,
    )
    if (count <= 0) return HolidayEveNotice.None
    return HolidayEveNotice.SuggestMute(
        date = tomorrow,
        holidayName = resolution.holidayName,
        holidayNameRes = resolution.holidayNameRes,
        reminderCount = count,
    )
}
