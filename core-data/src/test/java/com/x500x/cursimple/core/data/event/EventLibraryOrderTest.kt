package com.x500x.cursimple.core.data.event

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class EventLibraryOrderTest {
    private val first = ScheduleEvent("first", "First", "2026-11-20", "10:00", "11:00")
    private val middle = ScheduleEvent("middle", "Middle", "2026-11-21", "10:00", "11:00")
    private val last = ScheduleEvent("last", "Last", "2026-11-22", "10:00", "11:00")

    @Test
    fun reorderMovesWholeRecordsWithoutChangingDatesOrUnlistedSlots() {
        assertEquals(
            listOf(last, middle, first),
            reorderStoredEvents(listOf(first, middle, last), listOf(last.id, first.id)),
        )
    }

    @Test
    fun reorderIgnoresRemovedIdsAndKeepsEditsMadeAfterTheListWasRead() {
        val edited = first.copy(title = "Edited", date = "2026-12-01")
        assertEquals(
            listOf(last, edited),
            reorderStoredEvents(listOf(edited, last), listOf(last.id, middle.id, edited.id)),
        )
    }
}
