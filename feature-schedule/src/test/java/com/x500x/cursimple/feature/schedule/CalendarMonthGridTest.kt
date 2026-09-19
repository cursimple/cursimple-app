package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历格子的排布。
 *
 * 自己画这张表是因为 M3 的 DatePicker 星期表头在中文下七列全是「星」，
 * 换成自绘之后，日号落在哪一列必须自己算对。
 */
class CalendarMonthGridTest {

    @Test
    fun `week starting on monday puts the first day under its own weekday`() {
        // 2026 年 9 月 1 日是周二，周一起始时前面空一格
        val cells = monthGridCells(YearMonth.of(2026, 9), weekStartDayOfWeek = 1)

        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        // 19 日是周六，周一起始时排在第 6 列
        val index = cells.indexOf(LocalDate.of(2026, 9, 19))
        assertEquals(5, index % 7)
    }

    @Test
    fun `week starting on sunday shifts every column by one`() {
        val cells = monthGridCells(YearMonth.of(2026, 9), weekStartDayOfWeek = 7)

        assertNull(cells[0])
        assertNull(cells[1])
        assertEquals(LocalDate.of(2026, 9, 1), cells[2])
        assertEquals(6, cells.indexOf(LocalDate.of(2026, 9, 19)) % 7)
    }

    @Test
    fun `grid keeps whole weeks and covers every day of the month`() {
        val month = YearMonth.of(2026, 2)
        val cells = monthGridCells(month, weekStartDayOfWeek = 1)

        assertEquals(0, cells.size % 7)
        assertEquals(month.lengthOfMonth(), cells.count { it != null })
    }
}
