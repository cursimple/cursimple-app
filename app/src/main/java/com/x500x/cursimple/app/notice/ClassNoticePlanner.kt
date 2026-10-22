package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.titleForWeek
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

    fun classesOnDate(
        date: LocalDate,
        allCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        termStartDate: LocalDate?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
    ): List<UpcomingClass> {
        if (timingProfile == null || timingProfile.slotTimes.isEmpty()) return emptyList()
        val visible = allCourses.visibleScheduleCourses()
        return scheduledCourseOccurrencesOn(date, visible, timingProfile, termStartDate, overrides, holidayCalendar)
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
            .sortedBy { it.startAt }
    }

    fun nextExam(
        now: LocalDateTime,
        allCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        termStartDate: LocalDate?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        advanceMinutes: Int = 45,
        lookaheadDays: Int = LOOKAHEAD_DAYS,
    ): UpcomingClass? {
        val exams = allCourses.filter { it.category == com.x500x.cursimple.core.kernel.model.CourseCategory.Exam }
        if (exams.isEmpty()) return null
        for (offset in 0..lookaheadDays) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val classes = classesOnDate(date, exams, timingProfile, termStartDate, overrides, holidayCalendar)
            val upcoming = classes.filter { it.startAt.minusMinutes(advanceMinutes.toLong()).isAfter(now) }
                .minByOrNull { it.startAt }
            if (upcoming != null) return upcoming
        }
        return null
    }

    fun nextTransit(
        now: LocalDateTime,
        allCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        termStartDate: LocalDate?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        lookaheadDays: Int = 2,
    ): TransitEvent? {
        for (offset in 0..lookaheadDays) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val classes = classesOnDate(date, allCourses, timingProfile, termStartDate, overrides, holidayCalendar)
            for (i in 0 until classes.size - 1) {
                val c1 = classes[i]
                val c2 = classes[i + 1]
                val loc1 = c1.displayLocation().trim()
                val loc2 = c2.displayLocation().trim()
                if (loc1.isNotEmpty() && loc2.isNotEmpty() && loc1 != loc2) {
                    val triggerAt = c1.endAt.minusMinutes(5)
                    if (triggerAt.isAfter(now)) {
                        return TransitEvent(
                            triggerAt = triggerAt,
                            nextTitle = c2.displayTitle(),
                            nextLocation = loc2,
                        )
                    }
                }
            }
        }
        return null
    }
}

data class TransitEvent(
    val triggerAt: LocalDateTime,
    val nextTitle: String,
    val nextLocation: String,
)

fun UpcomingClass.displayLocation(): String = course.locationForWeek(weekNumber)

fun UpcomingClass.displayTitle(): String = course.titleForWeek(weekNumber)
