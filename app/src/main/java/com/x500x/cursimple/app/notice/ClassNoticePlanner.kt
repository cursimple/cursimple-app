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
                // 提醒点已过的课不再提示，只找还来得及提醒的。
                // 刚发完一条提醒时正处在这节课的提醒点上，若只看「还没开始」，
                // 找到的还是这节课，下一节就永远排不上，退出应用后提醒链就断了
                .filter { it.startAt.minusMinutes(advanceMinutes.toLong()).isAfter(now) }
                .minByOrNull { it.startAt }
                ?.let { return it }
        }
        return null
    }
}

/** 这节课在当前作息下的实际显示地点。 */
fun UpcomingClass.displayLocation(): String = course.locationForWeek(weekNumber)
