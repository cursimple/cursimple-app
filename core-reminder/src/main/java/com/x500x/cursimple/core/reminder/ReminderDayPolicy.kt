package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import java.time.LocalDate

/**
 * 某一天要不要下发提醒。
 *
 * 放假当天默认照常提醒：课表把课程灰显，是否真去上课由用户自己决定，
 * 应用不替他判断。需要安静时可以打开 [skipOnHoliday]，或把单独某天放进 [mutedDates]。
 */
data class ReminderDayPolicy(
    val skipOnHoliday: Boolean = false,
    val mutedDates: Set<LocalDate> = emptySet(),
) {
    fun suppresses(date: LocalDate, day: ScheduleDayResolution): Boolean =
        date in mutedDates || (skipOnHoliday && day.isHoliday)

    companion object {
        /** 照常提醒，不因假日或静音跳过。 */
        val ALWAYS = ReminderDayPolicy()
    }
}
