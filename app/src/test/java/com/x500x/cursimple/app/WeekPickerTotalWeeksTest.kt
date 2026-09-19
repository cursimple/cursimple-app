package com.x500x.cursimple.app

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPickerTotalWeeksTest {

    private fun course(weeks: List<Int>) = CourseItem(
        id = "c",
        title = "高数",
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    private fun schedule(vararg courses: CourseItem) = TermSchedule(
        termId = "t",
        updatedAt = "2026-09-20T00:00:00Z",
        dailySchedules = listOf(DailySchedule(dayOfWeek = 1, courses = courses.toList())),
    )

    @Test
    fun `课程推出来的周数取最大周次`() {
        val derived = derivedWeekCount(
            schedule = schedule(course(listOf(1, 2, 18))),
            manualCourses = emptyList(),
            currentWeek = 1,
            selectedWeek = 1,
        )

        assertEquals(18, derived)
    }

    @Test
    fun `自己加的空白周叠在课程周数之上`() {
        val total = resolveWeekPickerTotalWeeks(
            schedule = schedule(course(listOf(1, 18))),
            manualCourses = emptyList(),
            currentWeek = 1,
            selectedWeek = 1,
            extraWeekCount = 3,
        )

        assertEquals(21, total)
    }

    @Test
    fun `没加过空白周时总周数就是课程推出来的那个`() {
        val args = schedule(course(listOf(1, 16)))
        assertEquals(
            derivedWeekCount(args, emptyList(), currentWeek = 1, selectedWeek = 1),
            resolveWeekPickerTotalWeeks(args, emptyList(), currentWeek = 1, selectedWeek = 1),
        )
    }

    @Test
    fun `负数的额外周数按零处理`() {
        val total = resolveWeekPickerTotalWeeks(
            schedule = schedule(course(listOf(1, 16))),
            manualCourses = emptyList(),
            currentWeek = 1,
            selectedWeek = 1,
            extraWeekCount = -5,
        )

        assertEquals(16, total)
    }

    @Test
    fun `当前周比课程周次还靠后时总周数跟着当前周走`() {
        // 课表只排到 16 周但人已经在第 20 周，周次面板不能把当前周关在外面
        val derived = derivedWeekCount(
            schedule = schedule(course(listOf(1, 16))),
            manualCourses = emptyList(),
            currentWeek = 20,
            selectedWeek = 1,
        )

        assertEquals(20, derived)
    }
}
