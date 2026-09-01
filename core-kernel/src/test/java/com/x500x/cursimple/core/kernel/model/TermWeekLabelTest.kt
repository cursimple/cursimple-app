package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class TermWeekLabelTest {

    private val termStart = LocalDate.of(2026, 9, 7)

    @Test
    fun `no term start is its own state, not week one`() {
        assertEquals(TermWeekLabel.TermStartMissing, termWeekLabel(termStart = null, weekIndex = 1))
        assertEquals(TermWeekLabel.TermStartMissing, termWeekLabel(termStart = null, weekIndex = 0))
        assertEquals(TermWeekLabel.TermStartMissing, termWeekLabel(termStart = null, weekIndex = -3))
    }

    @Test
    fun `a week before the term start reads as not started`() {
        assertEquals(TermWeekLabel.NotStarted, termWeekLabel(termStart, weekIndex = 0))
        assertEquals(TermWeekLabel.NotStarted, termWeekLabel(termStart, weekIndex = -1))
    }

    @Test
    fun `a started week carries its number`() {
        assertEquals(TermWeekLabel.Week(1), termWeekLabel(termStart, weekIndex = 1))
        assertEquals(TermWeekLabel.Week(18), termWeekLabel(termStart, weekIndex = 18))
    }

    @Test
    fun `missing term start and not started are distinct states`() {
        assertNotEquals(
            termWeekLabel(termStart = null, weekIndex = 0),
            termWeekLabel(termStart, weekIndex = 0),
        )
    }

    @Test
    fun `the week only overload never reports a missing term start`() {
        assertEquals(TermWeekLabel.NotStarted, termWeekLabel(weekIndex = 0))
        assertEquals(TermWeekLabel.Week(4), termWeekLabel(weekIndex = 4))
    }

    @Test
    fun `every weekday maps to its own text`() {
        val ids = (1..7).map(::weekdayNameRes)

        assertEquals(7, ids.distinct().size)
    }

    @Test
    fun `a weekday outside one through seven falls back to the unknown text`() {
        val unknown = weekdayNameRes(0)

        assertEquals(unknown, weekdayNameRes(8))
        assertEquals(unknown, weekdayNameRes(-1))
        (1..7).forEach { assertNotEquals(unknown, weekdayNameRes(it)) }
    }
}
