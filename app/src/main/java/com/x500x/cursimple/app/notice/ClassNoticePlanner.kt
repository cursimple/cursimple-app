package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.slotsCovering
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 下一节要上的课，以及它的开始时刻。 */
data class UpcomingClass(
    val course: CourseItem,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    /** 这节课那天所在的教学周，用来取该周单独设置的地点。 */
    val weekNumber: Int?,
    /** 这节课覆盖到的作息时段，用来显示「第一节」「午间课」这类名字。 */
    val slots: List<IndexedValue<ClassSlotTime>> = emptyList(),
)

/**
 * 算出「下一节课」，纯函数，方便单测。
 *
 * 只往前看有限几天：课表空了或全是假期时，不至于一路算到学期末做无用功。
 */
object ClassNoticePlanner {

    private const val LOOKAHEAD_DAYS = 14

    fun nextClass(
        now: LocalDateTime,
        allCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        termStartDate: LocalDate?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        /** 提前几分钟提醒：提醒点已经过了的课不算「下一节」，要接着往后找。 */
        advanceMinutes: Int = 0,
        lookaheadDays: Int = LOOKAHEAD_DAYS,
    ): UpcomingClass? {
        if (timingProfile == null || timingProfile.slotTimes.isEmpty()) return null
        val visible = allCourses.visibleScheduleCourses()
        if (visible.isEmpty()) return null

        for (offset in 0..lookaheadDays) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val week = termStartDate?.let { resolveTermWeekNumber(it, date) }
            coursesOn(date, visible, overrides, holidayCalendar, termStartDate)
                .mapNotNull { course ->
                    val start = timingProfile.startTimeOf(course) ?: return@mapNotNull null
                    val end = timingProfile.endTimeOf(course) ?: return@mapNotNull null
                    UpcomingClass(
                        course = course,
                        startAt = LocalDateTime.of(date, start),
                        endAt = LocalDateTime.of(date, end),
                        weekNumber = week,
                        slots = timingProfile.slotsCovering(course.time.startNode, course.time.endNode),
                    )
                }
                // 提醒点已过的课不再提示，只找还来得及提醒的。
                // 刚发完一条提醒时正处在这节课的提醒点上，若只看「还没开始」，
                // 找到的还是这节课，下一节就永远排不上，退出应用后提醒链就断了
                .filter { it.startAt.minusMinutes(advanceMinutes.toLong()).isAfter(now) }
                .minByOrNull { it.startAt }
                ?.let { return it }
        }
        return null
    }

    /** 某一天实际要上的课，和课表主界面同一套判定（含调课、停课、放假）。 */
    private fun coursesOn(
        date: LocalDate,
        allCourses: List<CourseItem>,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        termStartDate: LocalDate?,
    ): List<CourseItem> {
        val resolution = resolveScheduleDay(date, overrides, holidayCalendar)
        val weekOf: (LocalDate) -> Int? = { d -> termStartDate?.let { resolveTermWeekNumber(it, d) } }
        val movedIn = coursesMovedTo(
            date = date,
            overrides = overrides,
            courseById = { id -> allCourses.firstOrNull { it.id == id } },
            isOriginallyActive = { course, from ->
                val week = weekOf(from)
                week == null || course.isActiveInTermWeekNumber(week)
            },
        )
        // 放假日不出常规课，但调过去的课照上
        val staying = if (resolution.isHoliday) {
            emptyList()
        } else {
            allCourses
                .filterNot { isCourseMovedAwayFrom(date, it, overrides) }
                .mapNotNull { course ->
                    val source =
                        temporaryScheduleCourseSourceDate(date, course, resolution.sourceDate, overrides)
                            ?: return@mapNotNull null
                    val week = weekOf(source)
                    course.takeIf { week == null || it.isActiveInTermWeekNumber(week) }
                }
        }
        return (staying + movedIn).filterNot { isCourseTemporarilyCancelled(date, it, overrides) }
    }
}

/** 这节课在当前作息下的实际显示地点。 */
fun UpcomingClass.displayLocation(): String = course.locationForWeek(weekNumber)

private fun TermTimingProfile.startTimeOf(course: CourseItem): LocalTime? =
    slotContaining(course.time.startNode)?.let { parseTime(it.startTime) }
        ?: course.reminderStartTime?.let(::parseTime)

private fun TermTimingProfile.endTimeOf(course: CourseItem): LocalTime? =
    slotContaining(course.time.endNode)?.let { parseTime(it.endTime) }
        ?: course.reminderEndTime?.let(::parseTime)

private fun TermTimingProfile.slotContaining(node: Int): ClassSlotTime? =
    slotTimes.firstOrNull { node in it.startNode..it.endNode }

private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
