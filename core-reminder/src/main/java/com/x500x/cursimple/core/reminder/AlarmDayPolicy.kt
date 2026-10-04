package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import java.time.Instant
import java.time.ZoneId

enum class AlarmDaySuppression { Holiday, MutedDate }

/** 排程和响铃共同使用：按闹钟的实际响铃日期判断，用户声明的调休上课日照常响。 */
fun alarmDaySuppression(
    triggerAtMillis: Long,
    allowOnHoliday: Boolean,
    zone: ZoneId,
    policy: ReminderDayPolicy,
    calendar: HolidayCalendarSettings,
    overrides: List<TemporaryScheduleOverride> = emptyList(),
): AlarmDaySuppression? {
    val date = Instant.ofEpochMilli(triggerAtMillis).atZone(zone).toLocalDate()
    if (date in policy.mutedDates) return AlarmDaySuppression.MutedDate
    val day = resolveScheduleDay(date, overrides, calendar)
    return if (policy.suppresses(date, day, allowOnHoliday)) AlarmDaySuppression.Holiday else null
}
