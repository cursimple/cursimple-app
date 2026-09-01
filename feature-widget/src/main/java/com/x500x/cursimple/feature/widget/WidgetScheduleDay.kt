package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
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
    val holidayLabel: String?,
    val courses: List<CourseItem>,
)

/**
 * 合并临时调课与节假日，得出 [targetDate] 当天要显示的课程。
 * [coursesOfDayOfWeek] 按来源日期的星期几取课；判定为假日时当天不出课。
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
    val courses = if (resolution.isHoliday) {
        emptyList()
    } else {
        filterTemporaryCancelledCourses(
            date = targetDate,
            courses = coursesOfDayOfWeek(sourceDate.dayOfWeek.value),
            overrides = temporaryScheduleOverrides,
        )
            .visibleScheduleCourses()
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }
    }
    return WidgetScheduleDay(
        targetDate = targetDate,
        sourceDate = sourceDate,
        weekIndex = weekIndex,
        holidayLabel = if (resolution.isHoliday) widgetHolidayLabel(resolution.holidayName) else null,
        courses = courses,
    )
}
