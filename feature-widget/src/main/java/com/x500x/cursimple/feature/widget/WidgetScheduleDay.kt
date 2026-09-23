package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate

/**
 * 小组件里一天的展示数据：目标日期、实际取课的来源日期、来源日期对应的教学周、
 * 假日称呼（非假日为 null）以及最终课程。
 */
internal data class WidgetScheduleDay(
    val targetDate: LocalDate,
    val sourceDate: LocalDate,
    val weekIndex: Int?,
    val holidayLabel: WidgetHolidayLabel?,
    val courses: List<CourseItem>,
    /** 当天放假。课程照常列出，只是按不可用态显示，也不参与上课中与倒计时判断。 */
    val onHoliday: Boolean = false,
)

/**
 * 合并临时调课与节假日，得出 [targetDate] 当天要显示的课程。
 * [coursesOfDayOfWeek] 按来源日期的星期几取课；判定为假日时课程照常列出并标记。
 */
internal fun resolveWidgetScheduleDay(
    targetDate: LocalDate,
    termStart: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    coursesOfDayOfWeek: (Int) -> List<CourseItem>,
): WidgetScheduleDay {
    val resolution = resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar)
    val sourceDate = resolution.sourceDate
    val weekIndex = resolveWeekIndex(sourceDate, termStart)
    // 只调某几节时，两天的课都得拿出来，再逐门问它今天归哪一天
    val candidates = (coursesOfDayOfWeek(sourceDate.dayOfWeek.value) + coursesOfDayOfWeek(targetDate.dayOfWeek.value))
        .distinct()
        // 被单独挪到别天的课，这天不再出现
        .filterNot { isCourseMovedAwayFrom(targetDate, it, temporaryScheduleOverrides) }
    // 从别天挪到这天的课；该不该上已按它原本那天判过，不再按本周过滤
    val movedIn = coursesMovedTo(
        date = targetDate,
        overrides = temporaryScheduleOverrides,
        courseById = { id -> (1..7).flatMap(coursesOfDayOfWeek).firstOrNull { it.id == id } },
        isOriginallyActive = { course, from -> course.activeOnWeek(resolveWeekIndex(from, termStart)) },
    ).visibleScheduleCourses()
    val courses = filterTemporaryCancelledCourses(
        date = targetDate,
        courses = candidates,
        overrides = temporaryScheduleOverrides,
    )
        .visibleScheduleCourses()
        .mapNotNull { course ->
            val courseSource = temporaryScheduleCourseSourceDate(
                date = targetDate,
                course = course,
                sourceDate = sourceDate,
                overrides = temporaryScheduleOverrides,
            ) ?: return@mapNotNull null
            course.takeIf { it.activeOnWeek(resolveWeekIndex(courseSource, termStart)) }
        }
        .plus(movedIn)
        .sortedBy { it.time.startNode }
    // 调课可以推翻放假：休息日里被挪过来的课照常上，这天就不再整体按休息日处理，
    // 只留这几门，倒计时与上课中判断都算上它们。
    // 没有挪课的放假日保持原样：课程照常列出，只是标成不可用。
    val holidayOverridden = resolution.isHoliday && movedIn.isNotEmpty()
    return WidgetScheduleDay(
        targetDate = targetDate,
        sourceDate = sourceDate,
        weekIndex = weekIndex,
        holidayLabel = if (resolution.isHoliday) widgetHolidayLabel(resolution.holidayName, resolution.holidayNameRes) else null,
        courses = if (holidayOverridden) movedIn.sortedBy { it.time.startNode } else courses,
        onHoliday = resolution.isHoliday && !holidayOverridden,
    )
}
