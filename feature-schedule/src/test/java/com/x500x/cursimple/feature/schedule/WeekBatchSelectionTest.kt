package com.x500x.cursimple.feature.schedule

import org.junit.Assert.*
import org.junit.Test

class WeekBatchSelectionTest {
    @Test fun strokeSelectsOnlyExistingWeeksAndKeepsOtherSelections() {
        val weeks = listOf(1, 3, 5, 7, 9)
        assertEquals(setOf(1, 3, 5, 7), dragSelectedWeeks(weeks, setOf(1), 3, 7))
        assertEquals(setOf(3, 5, 7), dragSelectedWeeks(weeks, emptySet(), 7, 3))
    }

    @Test fun draggingFromSelectedWeekClearsTheRangeAndCanReverse() {
        val weeks = (1..8).toList()
        val initial = setOf(1, 2, 3, 4, 8)
        assertEquals(setOf(1, 8), dragSelectedWeeks(weeks, initial, 2, 4))
        assertEquals(setOf(1, 3, 4, 8), dragSelectedWeeks(weeks, initial, 2, 2))
        assertEquals(initial, dragSelectedWeeks(weeks, initial, 2, 99))
    }

    @Test fun batchEditingPreservesUnselectedAndStoredLaterWeeks() {
        val original = mapOf(1 to "A", 2 to "B", 30 to "Later")
        val changed = batchWeekValues(original, listOf(1, 2, 3), setOf(2, 3, 4), " New ")
        assertEquals(mapOf(1 to "A", 2 to "New", 3 to "New", 30 to "Later"), changed)
        assertEquals(mapOf(1 to "A", 2 to "B", 30 to "Later"), original)
    }

    @Test fun usingDefaultClearsOnlySelectedOverrides() {
        assertEquals(mapOf(1 to "A"), batchWeekValues(mapOf(1 to "A", 2 to "B", 3 to "C"), listOf(1, 2, 3), setOf(2, 3), " "))
        assertEquals(mapOf(1 to "A"), batchWeekValues(mapOf(1 to "A"), listOf(1, 2), emptySet(), "B"))
    }
}
