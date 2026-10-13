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
    fun pickedWeeks_occurOnlyOnTheChosenDates() {
        val picked = base.copy(weekDates = listOf("2026-09-29", "2026-10-13", "2026-11-03"))
        assertTrue(picked.occursOn(LocalDate.of(2026, 10, 13)))
        assertTrue(picked.occursOn(LocalDate.of(2026, 11, 3)))
        assertFalse(picked.occursOn(LocalDate.of(2026, 10, 6)))
        assertTrue(picked.isRecurring)
        assertFalse(base.isRecurring)
        assertTrue(base.copy(repeatWeekly = true).isRecurring)
    }

    @Test
    fun termWeeks_mapToTheSameWeekdayAndBack() {
        // The term starts on a Wednesday; week 1 still begins on that Monday.
        val termStart = LocalDate.of(2026, 9, 2)
        val dates = termWeekDates(termStart, java.time.DayOfWeek.TUESDAY, listOf(5, 1, 3))
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 29)), dates)
        assertEquals(listOf(1, 3, 5), base.copy(weekDates = dates.map { it.toString() }).termWeeks(termStart))
    }

    @Test
    fun legacyJsonWithoutPickedWeeksStillDecodes() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val legacy = json.decodeFromString(ScheduleEvent.serializer(),
            "{\"id\":\"e\",\"title\":\"组会\",\"date\":\"2026-09-29\",\"startTime\":\"10:30\",\"endTime\":\"11:20\"}")
        assertEquals(emptyList<String>(), legacy.weekDates)
        val picked = base.copy(weekDates = listOf("2026-09-29", "2026-10-13"))
        assertEquals(picked, json.decodeFromString(ScheduleEvent.serializer(), json.encodeToString(ScheduleEvent.serializer(), picked)))
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
