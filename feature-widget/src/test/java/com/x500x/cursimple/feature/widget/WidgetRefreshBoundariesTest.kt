package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetRefreshBoundariesTest {

    private val day: LocalDate = LocalDate.of(2026, 9, 7)

    private fun slot(start: String, end: String, node: Int = 1) = ClassSlotTime(
        startNode = node,
        endNode = node,
        startTime = start,
        endTime = end,
    )

    private fun at(hour: Int, minute: Int) = day.atTime(hour, minute)

    @Test
    fun `no slots means nothing to schedule`() {
        assertEquals(emptyList<LocalDateTime>(), widgetRefreshBoundaries(emptyList(), at(8, 0)))
    }

    @Test
    fun `each slot contributes lead start and end`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40")),
            now = at(7, 0),
            limit = 10,
        )

        assertTrue(at(7, 55) in result)
        assertTrue(at(8, 0) in result)
        assertTrue(at(9, 40) in result)
    }

    @Test
    fun `boundaries already passed are dropped`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40")),
            now = at(8, 30),
            limit = 10,
        )

        // 课前和开始都过去了，只剩下课那一刻
        assertTrue(at(7, 55) !in result)
        assertTrue(at(8, 0) !in result)
        assertTrue(at(9, 40) in result)
    }

    @Test
    fun `results come out in time order`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("10:00", "11:40", node = 2), slot("08:00", "09:40")),
            now = at(7, 0),
            limit = 10,
        )

        assertEquals(result.sorted(), result)
        assertEquals(at(7, 55), result.first())
    }

    @Test
    fun `the limit keeps only the nearest boundaries`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40"), slot("10:00", "11:40", node = 2)),
            now = at(7, 0),
            limit = 2,
        )

        assertEquals(listOf(at(7, 55), at(8, 0)), result)
    }

    @Test
    fun `after the last class it rolls over to tomorrow`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40")),
            now = at(22, 0),
            limit = 3,
        )

        assertEquals(
            listOf(
                day.plusDays(1).atTime(7, 55),
                day.plusDays(1).atTime(8, 0),
                day.plusDays(1).atTime(9, 40),
            ),
            result,
        )
    }

    @Test
    fun `a class running past midnight ends on the next day`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("23:00", "00:40")),
            now = at(23, 30),
            limit = 1,
        )

        assertEquals(listOf(day.plusDays(1).atTime(0, 40)), result)
    }

    @Test
    fun `slots sharing a boundary are not scheduled twice`() {
        // 上一节结束与下一节课前提前量重合时只排一次
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40"), slot("09:45", "11:25", node = 2)),
            now = at(9, 0),
            limit = 10,
        )

        assertEquals(result.distinct(), result)
        assertEquals(1, result.count { it == at(9, 40) })
    }

    @Test
    fun `unparsable times are skipped instead of crashing`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("bad", "09:40"), slot("08:00", "09:40", node = 2)),
            now = at(7, 0),
            limit = 10,
        )

        assertTrue(result.isNotEmpty())
        assertTrue(at(8, 0) in result)
    }
}
