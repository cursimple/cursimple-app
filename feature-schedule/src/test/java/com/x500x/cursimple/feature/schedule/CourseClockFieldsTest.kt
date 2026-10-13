package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseClockFieldsTest {
    private val slots = listOf(
        ClassSlotTime(1, 1, "08:00", "08:45"),
        ClassSlotTime(2, 2, "08:55", "09:40"),
        ClassSlotTime(3, 4, "10:00", "11:35"),
        ClassSlotTime(5, 5, "14:00", "14:45"),
        ClassSlotTime(6, 6, "broken", "15:40"),
    )
    private fun m(h: Int, min: Int) = h * 60 + min

    @Test fun `typed time takes every period it overlaps`() {
        assertEquals(1 to 2, nodesForClock(slots, m(8, 20), m(9, 10)))
        assertEquals(2 to 4, nodesForClock(slots, m(9, 0), m(10, 30)))
    }

    @Test fun `a time inside a break keeps the closest period`() {
        assertEquals(4 to 4, nodesForClock(slots, m(12, 10), m(12, 50)))
        assertEquals(5 to 5, nodesForClock(slots, m(13, 20), m(13, 50)))
        assertEquals(1 to 1, nodesForClock(slots, m(7, 0), m(7, 40)))
        assertEquals(5 to 5, nodesForClock(slots, m(19, 0), m(20, 30)))
    }

    @Test fun `without usable periods there is nothing to map`() {
        assertNull(nodesForClock(emptyList(), m(8, 0), m(9, 0)))
        assertNull(nodesForClock(slots, m(9, 0), m(9, 0)))
    }

    @Test fun `periods prefill the typed fields`() {
        assertEquals(m(8, 0) to m(9, 40), clockForNodes(slots, 1, 2))
        assertEquals(m(10, 0) to m(11, 35), clockForNodes(slots, 4, 4))
        assertNull(clockForNodes(slots, 6, 6))
        assertNull(clockForNodes(slots, 9, 9))
    }
}

class CourseMoveClockTest {
    private val typed = com.x500x.cursimple.core.kernel.model.CourseItem(
        id = "c", title = "实验", time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(1, 4, 4),
        reminderStartTime = "12:10", reminderEndTime = "13:00",
    )

    @Test fun `moving to other periods drops the typed time`() {
        val moved = typed.movedTo(com.x500x.cursimple.core.kernel.model.CourseTimeSlot(2, 5, 6))
        assertNull(moved.reminderStartTime)
        assertNull(moved.reminderEndTime)
    }

    @Test fun `moving to another day keeps the typed time`() {
        val moved = typed.movedTo(com.x500x.cursimple.core.kernel.model.CourseTimeSlot(3, 4, 4))
        assertEquals("12:10", moved.reminderStartTime)
        assertEquals(3, moved.time.dayOfWeek)
    }

    @Test fun `placeholders keep their times`() {
        val placeholder = typed.copy(reminderOnly = true)
        assertEquals("12:10", placeholder.movedTo(com.x500x.cursimple.core.kernel.model.CourseTimeSlot(2, 5, 6)).reminderStartTime)
    }
}
