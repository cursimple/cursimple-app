package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.slotsCovering
import com.x500x.cursimple.core.kernel.model.scheduledCourseOccurrencesOn
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate
import java.time.LocalDateTime

data class UpcomingClass(
    val course: CourseItem,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val weekNumber: Int?,
    val slots: List<IndexedValue<ClassSlotTime>> = emptyList(),
)

/** Bound next-course search to a short horizon even for empty or holiday-only schedules. */
object ClassNoticePlanner {

    private const val LOOKAHEAD_DAYS = 14

    fun nextClass(
        now: LocalDateTime,
        allCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        termStartDate: LocalDate?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        advanceMinutes: Int = 0,
        lookaheadDays: Int = LOOKAHEAD_DAYS,
    ): UpcomingClass? {
        if (timingProfile == null || timingProfile.slotTimes.isEmpty()) return null
        val visible = allCourses.visibleScheduleCourses()
        if (visible.isEmpty()) return null

        for (offset in 0..lookaheadDays) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            scheduledCourseOccurrencesOn(date, visible, timingProfile, termStartDate, overrides, holidayCalendar)
                .mapNotNull { occurrence ->
                    val course = occurrence.course
                    val start = occurrence.start ?: return@mapNotNull null
                    val end = occurrence.end ?: return@mapNotNull null
                    UpcomingClass(
                        course = course,
                        startAt = LocalDateTime.of(date, start),
                        endAt = LocalDateTime.of(date, end),
                        weekNumber = occurrence.sourceWeek,
                        slots = timingProfile.slotsCovering(course.time.startNode, course.time.endNode),
                    )
                }
                // Skip elapsed reminder points so scheduling advances beyond the notice just delivered.
                .filter { it.startAt.minusMinutes(advanceMinutes.toLong()).isAfter(now) }
                .minByOrNull { it.startAt }
                ?.let { return it }
        }
        return null
    }
}

fun UpcomingClass.displayLocation(): String = course.locationForWeek(weekNumber)
