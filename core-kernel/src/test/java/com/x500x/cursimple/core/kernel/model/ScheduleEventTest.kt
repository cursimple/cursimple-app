package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleEventTest {

    private val base = ScheduleEvent(
        id = "e",
        title = "组会",
        date = "2026-09-29",
        startTime = "10:30",
        endTime = "11:20",
    )

    @Test
    fun oneOff_occursOnlyOnItsDate() {
        assertTrue(base.occursOn(LocalDate.of(2026, 9, 29)))
        assertFalse(base.occursOn(LocalDate.of(2026, 10, 6)))
    }

    @Test
    fun weekly_repeatsOnSameWeekdayUntilTheEndDate() {
        val weekly = base.copy(repeatWeekly = true, repeatUntil = "2026-10-13")
        assertFalse(weekly.occursOn(LocalDate.of(2026, 9, 22)))
        assertTrue(weekly.occursOn(LocalDate.of(2026, 10, 6)))
        assertTrue(weekly.occursOn(LocalDate.of(2026, 10, 13)))
        assertFalse(weekly.occursOn(LocalDate.of(2026, 10, 20)))
        assertFalse(weekly.occursOn(LocalDate.of(2026, 10, 7)))
        assertTrue(base.copy(repeatWeekly = true).occursOn(LocalDate.of(2027, 3, 2)))
    }

    @Test
    fun invalidTimes_neverShow() {
        assertFalse(base.copy(endTime = "10:30").occursOn(LocalDate.of(2026, 9, 29)))
        assertFalse(base.copy(startTime = "25:00").occursOn(LocalDate.of(2026, 9, 29)))
        assertFalse(base.copy(title = " ").isValid)
    }

    @Test
    fun occurrencesOn_sortsByStart() {
        val early = base.copy(id = "a", startTime = "08:00", endTime = "09:00")
        val day = LocalDate.of(2026, 9, 29)
        assertEquals(listOf("a", "e"), listOf(base, early).occurrencesOn(day).map { it.id })
    }
}
