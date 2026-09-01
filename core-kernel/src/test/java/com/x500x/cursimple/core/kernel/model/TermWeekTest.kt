package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TermWeekTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `no term start means no week is the current one`() {
        assertFalse(isCurrentTermWeek(termStart = null, displayedWeekIndex = 1, currentWeekIndex = 1))
        assertFalse(isCurrentTermWeek(termStart = null, displayedWeekIndex = 3, currentWeekIndex = 3))
    }

    @Test
    fun `before the term starts no week is the current one`() {
        assertFalse(isCurrentTermWeek(termStart = monday, displayedWeekIndex = 0, currentWeekIndex = 0))
        assertFalse(isCurrentTermWeek(termStart = monday, displayedWeekIndex = -1, currentWeekIndex = -1))
    }

    @Test
    fun `the displayed week is current only when it matches the current one`() {
        assertTrue(isCurrentTermWeek(termStart = monday, displayedWeekIndex = 3, currentWeekIndex = 3))
        assertFalse(isCurrentTermWeek(termStart = monday, displayedWeekIndex = 2, currentWeekIndex = 3))
        assertFalse(isCurrentTermWeek(termStart = monday, displayedWeekIndex = 4, currentWeekIndex = 3))
    }

    @Test
    fun `a term start midweek still anchors week one to its own monday`() {
        val wednesday = LocalDate.of(2026, 9, 9)

        assertEquals(1, resolveTermWeekNumber(wednesday, LocalDate.of(2026, 9, 7)))
        assertEquals(1, resolveTermWeekNumber(wednesday, LocalDate.of(2026, 9, 13)))
        assertEquals(2, resolveTermWeekNumber(wednesday, LocalDate.of(2026, 9, 14)))
        assertEquals(0, resolveTermWeekNumber(wednesday, LocalDate.of(2026, 9, 6)))
    }

    @Test
    fun `an empty week list means every week once the term has started`() {
        assertTrue(isTermWeekNumberActive(weekNumber = 1, weeks = emptyList()))
        assertTrue(isTermWeekNumberActive(weekNumber = 20, weeks = emptyList()))
        assertFalse(isTermWeekNumberActive(weekNumber = 0, weeks = emptyList()))
        assertFalse(isTermWeekNumberActive(weekNumber = -3, weeks = emptyList()))
    }

    @Test
    fun `a listed week only counts once the term has started`() {
        assertTrue(isTermWeekNumberActive(weekNumber = 2, weeks = listOf(2, 4)))
        assertFalse(isTermWeekNumberActive(weekNumber = 3, weeks = listOf(2, 4)))
        assertFalse(isTermWeekNumberActive(weekNumber = 0, weeks = listOf(0, 2)))
    }

    @Test
    fun `a timing profile without a term start resolves to no date`() {
        assertNull(profile(termStartDate = "").termStartLocalDate())
        assertNull(profile(termStartDate = "   ").termStartLocalDate())
    }

    @Test
    fun `a timing profile with an unparseable term start resolves to no date`() {
        assertNull(profile(termStartDate = "2026-13-45").termStartLocalDate())
        assertNull(profile(termStartDate = "not a date").termStartLocalDate())
        assertNull(profile(termStartDate = "2026/09/07").termStartLocalDate())
    }

    @Test
    fun `a timing profile with a valid term start resolves to it`() {
        assertEquals(LocalDate.of(2026, 9, 7), profile(termStartDate = "2026-09-07").termStartLocalDate())
    }

    private fun profile(termStartDate: String) =
        TermTimingProfile(termStartDate = termStartDate, slotTimes = emptyList())
}
