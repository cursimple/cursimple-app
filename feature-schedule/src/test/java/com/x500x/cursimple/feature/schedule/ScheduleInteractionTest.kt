package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleInteractionTest {

    private val termStart = LocalDate.of(2026, 3, 2)

    private fun course(id: String): CourseItem = CourseItem(
        id = id,
        title = id,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    @Test
    fun `tapping a stacked cell selects every course in it`() {
        val cell = listOf(course("a"), course("b"))

        val afterFirstTap = toggleCellSelection(emptySet(), cell)
        assertEquals(setOf("a", "b"), afterFirstTap)

        val afterSecondTap = toggleCellSelection(afterFirstTap, cell)
        assertTrue(afterSecondTap.isEmpty())
    }

    @Test
    fun `tapping a partially selected cell completes the selection`() {
        val cell = listOf(course("a"), course("b"))

        assertEquals(setOf("a", "b"), toggleCellSelection(setOf("a"), cell))
    }

    @Test
    fun `tapping a single course cell keeps toggling that course`() {
        val cell = listOf(course("a"))

        assertEquals(setOf("a"), toggleCellSelection(emptySet(), cell))
        assertEquals(emptySet<String>(), toggleCellSelection(setOf("a"), cell))
        assertEquals(setOf("b"), toggleCellSelection(setOf("a", "b"), cell))
    }

    @Test
    fun `tapping an empty cell changes nothing`() {
        assertEquals(setOf("a"), toggleCellSelection(setOf("a"), emptyList()))
    }

    @Test
    fun `detail dialog week number follows the tapped date`() {
        // 第 5 周周三与第 6 周周三，弹窗都应按格子自身的日期判断
        assertEquals(5, detailWeekNumber(LocalDate.of(2026, 4, 1), termStart, emptyList()))
        assertEquals(6, detailWeekNumber(LocalDate.of(2026, 4, 8), termStart, emptyList()))
    }

    @Test
    fun `detail dialog week number follows the borrowed source date on a make-up day`() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup-1",
                type = TemporaryScheduleOverrideType.MakeUp,
                targetDate = "2026-04-08",
                sourceDate = "2026-04-01",
            ),
        )

        assertEquals(5, detailWeekNumber(LocalDate.of(2026, 4, 8), termStart, overrides))
    }

    @Test
    fun `empty schedule keeps a hint while the grid stays usable`() {
        assertNull(emptyScheduleHint(hasSchedule = true, hasAnyCourse = true, hasCoursesThisWeek = true))
        assertTrue(
            emptyScheduleHint(hasSchedule = false, hasAnyCourse = false, hasCoursesThisWeek = false)
                .orEmpty()
                .contains("还没有同步到课表"),
        )
        assertTrue(
            emptyScheduleHint(hasSchedule = true, hasAnyCourse = false, hasCoursesThisWeek = false)
                .orEmpty()
                .contains("空"),
        )
        assertTrue(
            emptyScheduleHint(hasSchedule = true, hasAnyCourse = true, hasCoursesThisWeek = false)
                .orEmpty()
                .contains("这一周"),
        )
    }

    @Test
    fun `bulk reminder message reports failures instead of claiming success`() {
        assertEquals("已为 3 门课程创建提醒", bulkReminderStatusMessage(3, emptyList(), true))

        val partial = bulkReminderStatusMessage(2, listOf("体育"), true)
        assertTrue(partial.startsWith("已为 2 门课程创建提醒"))
        assertTrue(partial.contains("体育"))
        assertTrue(partial.contains("节次时间表"))

        val allFailed = bulkReminderStatusMessage(0, listOf("体育", "军训"), true)
        assertTrue(allFailed.startsWith("提醒创建失败："))
        assertTrue(allFailed.contains("体育、军训"))

        val noProfile = bulkReminderStatusMessage(0, listOf("体育"), false)
        assertTrue(noProfile.contains("还没有节次时间表"))

        val many = bulkReminderStatusMessage(0, listOf("a", "b", "c", "d"), true)
        assertTrue(many.contains("等 4 门"))
    }
}
