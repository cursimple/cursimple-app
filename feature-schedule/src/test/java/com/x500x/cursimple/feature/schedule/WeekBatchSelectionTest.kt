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

    @Test fun oddAndEvenWeekParitySplitsCorrectly() {
        val maxWeek = 16
        val allWeeks = (1..maxWeek).toList()
        val oddWeeks = allWeeks.filter { it % 2 != 0 }
        val evenWeeks = allWeeks.filter { it % 2 == 0 }
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), oddWeeks)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), evenWeeks)
        assertEquals(allWeeks.toSet(), (oddWeeks + evenWeeks).toSet())
    }

    @Test fun weeksShowTheirOverrideOrFallBackToTheDefault() {
        val stored = mapOf(2 to " Lab ", 3 to "  ")
        assertEquals("Lab", effectiveWeekValue(stored, 2, "Room"))
        assertEquals("Room", effectiveWeekValue(stored, 1, " Room "))
        assertEquals("Room", effectiveWeekValue(stored, 3, "Room"))
        assertEquals("", effectiveWeekValue(emptyMap(), 1))
    }

    @Test fun selectionLoadsTheSharedValueOnlyWhenAllWeeksAgree() {
        val stored = mapOf(2 to "Lab", 4 to "Lab")
        assertEquals("Lab", commonWeekValue(stored, setOf(2, 4), "Room"))
        assertEquals("Room", commonWeekValue(stored, setOf(1, 3), "Room"))
        assertNull(commonWeekValue(stored, setOf(1, 2), "Room"))
        assertNull(commonWeekValue(stored, emptySet(), "Room"))
    }

    @Test fun overviewGroupsWeeksByWhatTheyShowInWeekOrder() {
        val stored = mapOf(3 to "Lab", 4 to "Lab", 6 to "Hall")
        assertEquals(
            listOf("Room" to listOf(1, 2, 5), "Lab" to listOf(3, 4), "Hall" to listOf(6)),
            groupWeeksByValue(stored, listOf(6, 5, 4, 3, 2, 1), "Room"),
        )
    }

    @Test fun applyingTheDefaultToSelectedWeeksDropsTheirOverrides() {
        val weeks = listOf(1, 2, 3)
        assertEquals(mapOf(1 to "Lab"), batchWeekValues(mapOf(1 to "Lab", 2 to "Hall"), weeks, setOf(2, 3), " Room ", "Room"))
    }
}
