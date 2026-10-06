package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import java.time.LocalDate

/** Date muting always wins; explicit per-alarm holiday access can bypass automatic skipping. */
data class ReminderDayPolicy(
    val skipOnHoliday: Boolean = true,
    val mutedDates: Set<LocalDate> = emptySet(),
) {
    fun suppresses(date: LocalDate, day: ScheduleDayResolution, allowOnHoliday: Boolean = false): Boolean =
        date in mutedDates || (skipOnHoliday && day.isHoliday && !allowOnHoliday)

    companion object {
        val ALWAYS = ReminderDayPolicy(skipOnHoliday = false)
    }
}
