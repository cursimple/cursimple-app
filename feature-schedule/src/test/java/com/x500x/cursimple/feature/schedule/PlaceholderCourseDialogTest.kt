package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceholderCourseDialogTest {
    @Test
    fun `empty weeks mean every week`() {
        assertEquals(WeekParity.All, placeholderWeekParity(emptyList(), 20))
        assertEquals(emptyList<Int>(), placeholderWeeksFor(WeekParity.All, listOf(3, 4), 20))
    }

    @Test
    fun `full odd and even sets map to presets`() {
        val odd = (1..20).filter { it % 2 == 1 }
        val even = (1..20).filter { it % 2 == 0 }
        assertEquals(WeekParity.Odd, placeholderWeekParity(odd, 20))
        assertEquals(WeekParity.Even, placeholderWeekParity(even.reversed(), 20))
        assertEquals(odd, placeholderWeeksFor(WeekParity.Odd, emptyList(), 20))
        assertEquals(even, placeholderWeeksFor(WeekParity.Even, emptyList(), 20))
    }

    @Test
    fun `partial ranges stay custom so saving keeps stored weeks`() {
        val range = (1..16).toList()
        assertEquals(WeekParity.Custom, placeholderWeekParity(range, 20))
        assertEquals(WeekParity.Custom, placeholderWeekParity(listOf(3, 5, 7), 20))
        assertEquals(range, placeholderWeeksFor(WeekParity.Custom, range, 20))
        assertEquals(listOf(2, 5), placeholderWeeksFor(WeekParity.Custom, listOf(5, 2, 5), 20))
    }

    @Test
    fun `parses and formats minute of day`() {
        assertEquals(7 * 60 + 10, parseMinuteOfDay("07:10"))
        assertEquals(7 * 60 + 10, parseMinuteOfDay(" 07:10 "))
        assertNull(parseMinuteOfDay("7:1"))
        assertNull(parseMinuteOfDay(null))
        assertEquals("07:05", formatMinuteOfDay(7 * 60 + 5))
        assertEquals("23:59", formatMinuteOfDay(23 * 60 + 59))
    }
}
