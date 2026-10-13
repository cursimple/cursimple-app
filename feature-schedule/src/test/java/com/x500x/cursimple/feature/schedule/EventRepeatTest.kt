package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepeatTest {
    // Term starts Wednesday 2026-09-02; week 1 runs from Monday 2026-08-31.
    private val termStart = LocalDate.of(2026, 9, 2)
    private val event = ScheduleEvent(
        id = "e",
        title = "组会",
        date = "2026-09-08",
        startTime = "19:00",
        endTime = "20:00",
        repeatWeekly = true,
        repeatUntil = "2026-12-31",
    )

    @Test fun `picked weeks land on the event weekday and start from the earliest`() {
        val saved = event.withRepeat(EventRepeat.Weeks, repeatUntil = null, weeks = listOf(5, 2, 3), anchor = termStart)

        assertEquals(listOf("2026-09-08", "2026-09-15", "2026-09-29"), saved.weekDates)
        assertEquals("2026-09-08", saved.date)
        assertFalse(saved.repeatWeekly)
        assertEquals(null, saved.repeatUntil)
        assertTrue(saved.occursOn(LocalDate.of(2026, 9, 29)))
        assertFalse(saved.occursOn(LocalDate.of(2026, 9, 22)))
    }

    @Test fun `switching back to once or weekly drops the picked weeks`() {
        val picked = event.withRepeat(EventRepeat.Weeks, null, listOf(2, 4), termStart)

        val once = picked.withRepeat(EventRepeat.Once, "2026-12-31", listOf(2, 4), termStart)
        assertEquals(emptyList<String>(), once.weekDates)
        assertFalse(once.repeatWeekly)
        assertEquals(null, once.repeatUntil)

        val weekly = picked.withRepeat(EventRepeat.Weekly, "2026-12-31", listOf(2, 4), termStart)
        assertEquals(emptyList<String>(), weekly.weekDates)
        assertTrue(weekly.repeatWeekly)
        assertEquals("2026-12-31", weekly.repeatUntil)
    }

    @Test fun `editor opens in the mode the event was saved with`() {
        assertEquals(EventRepeat.Weekly, event.repeatMode)
        assertEquals(EventRepeat.Once, event.copy(repeatWeekly = false).repeatMode)
        assertEquals(EventRepeat.Weeks, event.withRepeat(EventRepeat.Weeks, null, listOf(2), termStart).repeatMode)
    }

    @Test fun `without a term start the weeks count from the event's own week`() {
        val anchor = eventWeekAnchor(termStart = null, date = LocalDate.of(2026, 9, 10))

        assertEquals(LocalDate.of(2026, 9, 7), anchor)
        val saved = event.withRepeat(EventRepeat.Weeks, null, listOf(1, 3), anchor)
        assertEquals(listOf("2026-09-08", "2026-09-22"), saved.weekDates)
    }
}
