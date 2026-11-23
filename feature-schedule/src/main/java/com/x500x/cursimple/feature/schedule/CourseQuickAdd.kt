package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import java.time.LocalDate

/** Preserve displayed periods and follow day swaps so the new recurrence stays in that cell. */
internal fun quickAddPosition(
    course: CourseItem,
    displayedDate: LocalDate,
    overrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
): CourseTimeSlot {
    val sourceDate = resolveScheduleDay(displayedDate, overrides, holidayCalendar).sourceDate
    val sourceCandidate = course.copy(time = course.time.copy(dayOfWeek = sourceDate.dayOfWeek.value))
    val followsSource = sourceDate != displayedDate &&
        temporaryScheduleCourseSourceDate(displayedDate, sourceCandidate, sourceDate, overrides) != null
    return course.time.copy(dayOfWeek = (if (followsSource) sourceDate else displayedDate).dayOfWeek.value)
}

/** Lets a grid card report the courses sharing its cell to the screen that hosts the action sheet. */
internal val LocalCellSiblingsSink =
    androidx.compose.runtime.compositionLocalOf<(List<CourseItem>) -> Unit> { {} }

internal data class CourseActionRequest(
    val course: CourseItem,
    val position: CourseTimeSlot,
    val date: LocalDate? = null,
    /** Every course sharing the pressed cell, so the sheet can retarget without a second dialog. */
    val siblings: List<CourseItem> = emptyList(),
)

internal data class QuickAddPositionRequest(
    val position: CourseTimeSlot,
    val date: LocalDate? = null,
)
