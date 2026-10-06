package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun `no slots still refreshes at midnight`() {
        // Midnight refresh remains scheduled even without timing data.
        assertEquals(
            listOf(day.plusDays(1).atStartOfDay()),
            widgetRefreshBoundaries(emptyList(), at(8, 0)),
        )
    }

    @Test
    fun `midnight is always scheduled`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40")),
            now = at(7, 0),
            limit = 10,
        )

        assertTrue(day.plusDays(1).atStartOfDay() in result)
    }

    @Test
    fun `each slot contributes soon lead start and end`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40")),
            now = at(7, 0),
            limit = 10,
        )

        // Refresh when starting-soon status first becomes active.
        assertTrue(at(7, 30) in result)
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

        // After earlier boundaries pass, only class-end refresh remains.
        assertTrue(at(7, 30) !in result)
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
        assertEquals(at(7, 30), result.first())
    }

    @Test
    fun `the limit keeps only the nearest boundaries`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("08:00", "09:40"), slot("10:00", "11:40", node = 2)),
            now = at(7, 0),
            limit = 2,
        )

        assertEquals(listOf(at(7, 30), day.plusDays(1).atStartOfDay()), result)
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
                day.plusDays(1).atStartOfDay(),
                day.plusDays(1).atTime(7, 30),
                day.plusDays(1).atTime(7, 55),
            ),
            result,
        )
    }

    @Test
    fun `a class running past midnight ends on the next day`() {
        val result = widgetRefreshBoundaries(
            listOf(slot("23:00", "00:40")),
            now = at(23, 30),
            limit = 2,
        )

        assertEquals(
            listOf(day.plusDays(1).atStartOfDay(), day.plusDays(1).atTime(0, 40)),
            result,
        )
    }

    @Test
    fun `slots sharing a boundary are not scheduled twice`() {
        // Deduplicate coincident end and advance boundaries.
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
