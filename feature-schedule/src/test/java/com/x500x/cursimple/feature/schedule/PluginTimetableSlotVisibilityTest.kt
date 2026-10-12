package com.x500x.cursimple.feature.schedule

import org.junit.Assert.*
import org.junit.Test

class PluginTimetableSlotVisibilityTest {
    @Test fun hidingEmptyRowsCompressesTheGridWithoutRenumberingOrPadding() {
        val slots = (1..8).map { DisplaySlot(it, it, "Slot $it", "", "") }
        val visible = visibleTimetableSlots(slots, setOf(3 to 3, 6 to 6))
        assertEquals(listOf(1, 2, 4, 5, 7, 8), visible.map { it.startNode })
        assertEquals(listOf("Slot 1", "Slot 2", "Slot 4", "Slot 5", "Slot 7", "Slot 8"), visible.map { it.label })
        assertEquals(8, slots.size)
    }
}
