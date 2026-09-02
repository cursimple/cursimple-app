package com.x500x.cursimple.core.kernel.time

import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekStartTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val sunday = LocalDate.of(2026, 9, 13)

    @Test
    fun `the display window starts on the chosen day`() {
        assertEquals(monday, displayWeekStartOf(monday, WeekStartDay.Monday))
        assertEquals(monday, displayWeekStartOf(sunday, WeekStartDay.Monday))
        assertEquals(monday.minusDays(1), displayWeekStartOf(monday, WeekStartDay.Sunday))
        assertEquals(sunday, displayWeekStartOf(sunday, WeekStartDay.Sunday))
    }

    @Test
    fun `the term week number does not move when the display start day changes`() {
        val termStart = monday
        // 同一天的教学周编号只由周一锚点决定，与显示起始日无关
        listOf(monday, monday.plusDays(3), sunday, sunday.plusDays(1)).forEach { date ->
            assertEquals(
                resolveTermWeekNumber(termStart, date),
                resolveTermWeekNumber(termStart, date),
            )
        }
        assertEquals(1, resolveTermWeekNumber(termStart, monday))
        assertEquals(1, resolveTermWeekNumber(termStart, sunday))
        assertEquals(2, resolveTermWeekNumber(termStart, sunday.plusDays(1)))
    }

    @Test
    fun `a sunday-first window reports the term week of the monday inside it`() {
        val termStart = monday
        // 周日起时 9/13 那一页的窗口是 9/13-9/19，窗口内的周一是 9/14，属于第 2 周
        val window = displayWeekStartOf(sunday, WeekStartDay.Sunday)

        assertEquals(monday.plusDays(7), displayWeekAnchorMonday(window))
        assertEquals(2, displayWeekTermIndex(termStart, window))
    }

    @Test
    fun `a monday-first window reports its own term week`() {
        assertEquals(1, displayWeekTermIndex(monday, displayWeekStartOf(monday, WeekStartDay.Monday)))
        assertEquals(2, displayWeekTermIndex(monday, displayWeekStartOf(sunday.plusDays(1), WeekStartDay.Monday)))
    }

    @Test
    fun `column order follows the start day only when the weekend is shown`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            columnDayOfWeeks(WeekStartDay.Monday, weekendVisible = true, saturdayVisible = true),
        )
        assertEquals(
            listOf(7, 1, 2, 3, 4, 5, 6),
            columnDayOfWeeks(WeekStartDay.Sunday, weekendVisible = true, saturdayVisible = true),
        )
        // 周日不显示时没有把它排在最前的余地，两种起始日都从周一排起
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            columnDayOfWeeks(WeekStartDay.Sunday, weekendVisible = false, saturdayVisible = true),
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            columnDayOfWeeks(WeekStartDay.Sunday, weekendVisible = false, saturdayVisible = false),
        )
    }

    @Test
    fun `a weekday maps to its date inside the display window`() {
        val mondayWindow = displayWeekStartOf(monday, WeekStartDay.Monday)
        assertEquals(monday, columnDate(mondayWindow, 1))
        assertEquals(sunday, columnDate(mondayWindow, 7))

        val sundayWindow = displayWeekStartOf(sunday, WeekStartDay.Sunday)
        assertEquals(sunday, columnDate(sundayWindow, 7))
        assertEquals(sunday.plusDays(1), columnDate(sundayWindow, 1))
        assertEquals(sunday.plusDays(6), columnDate(sundayWindow, 6))
    }
}
