package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only complete title lines should be visible. */
class FitLineCountTest {

    @Test
    fun `正好放得下几行就是几行`() {
        assertEquals(3, fitLineCount(availableHeightDp = 36f, lineHeightDp = 12f))
    }

    @Test
    fun `放不下的那半行不算`() {
        // Do not display the partial fourth line.
        assertEquals(3, fitLineCount(availableHeightDp = 42f, lineHeightDp = 12f))
    }

    @Test
    fun `再挤也要留一行`() {
        // Retain one title line even when height is insufficient.
        assertEquals(1, fitLineCount(availableHeightDp = 5f, lineHeightDp = 12f))
        assertEquals(1, fitLineCount(availableHeightDp = 0f, lineHeightDp = 12f))
    }

    @Test
    fun `高度不受限时不限制行数`() {
        assertEquals(Int.MAX_VALUE, fitLineCount(availableHeightDp = Float.POSITIVE_INFINITY, lineHeightDp = 12f))
    }

    @Test
    fun `行高非法时不做限制而不是崩掉`() {
        assertEquals(Int.MAX_VALUE, fitLineCount(availableHeightDp = 40f, lineHeightDp = 0f))
    }
}
