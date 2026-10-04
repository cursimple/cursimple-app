package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import java.time.LocalDate

/**
 * 某一天要不要下发提醒。
 *
 * 默认跳过假日；单个闹钟明确允许假日响铃时可例外，手动静音的日期始终优先。
 */
data class ReminderDayPolicy(
    val skipOnHoliday: Boolean = true,
    val mutedDates: Set<LocalDate> = emptySet(),
) {
    fun suppresses(date: LocalDate, day: ScheduleDayResolution, allowOnHoliday: Boolean = false): Boolean =
        date in mutedDates || (skipOnHoliday && day.isHoliday && !allowOnHoliday)

    companion object {
        /** 照常提醒，不因假日或静音跳过。 */
        val ALWAYS = ReminderDayPolicy(skipOnHoliday = false)
    }
}
