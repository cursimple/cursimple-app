package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class EventActionDefaultsTest {
    @Test
    fun addAtEventPositionKeepsDateAndTime() {
        val event = ScheduleEvent(
            id = "event-1",
            title = "组会",
            date = "2026-09-26",
            startTime = "08:00",
            endTime = "10:00",
        )

        assertEquals(
            EventAddDefaults("2026-09-26", "08:00", "10:00"),
            event.addDefaults(),
        )
    }
}
