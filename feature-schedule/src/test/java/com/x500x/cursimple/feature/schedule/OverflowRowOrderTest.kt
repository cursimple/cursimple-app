package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/** The folded-course list must put this week's courses first and never fake a countdown for other weeks. */
class OverflowRowOrderTest {

    /** One folded course: whether it runs this week and when it starts (minutes from midnight). */
    private data class Row(val title: String, val inactive: Boolean, val start: Int?)

    private fun order(rows: List<Row>, nowMinute: Int?): List<String> =
        rows.sortedWith(
            compareBy<Row> { it.inactive }
                .thenBy { overflowRowPriority(it.inactive, it.start, it.start?.plus(90), nowMinute) },
        ).map { it.title }

    @Test
    fun `this week comes before other weeks even when it runs later`() {
        val rows = listOf(
            Row("other-early", inactive = true, start = 8 * 60),
            Row("this-week-evening", inactive = false, start = 20 * 60),
        )
        assertEquals(listOf("this-week-evening", "other-early"), order(rows, nowMinute = 9 * 60))
    }

    @Test
    fun `this week is ordered around the clock`() {
        val rows = listOf(
            Row("upcoming-far", inactive = false, start = 16 * 60),
            Row("ongoing", inactive = false, start = 9 * 60),
            Row("finished", inactive = false, start = 8 * 60),
            Row("upcoming-near", inactive = false, start = 11 * 60),
        )
        assertEquals(
            listOf("ongoing", "upcoming-near", "upcoming-far", "finished"),
            order(rows, nowMinute = 9 * 60 + 45),
        )
    }

    @Test
    fun `other weeks keep plain clock order instead of distance from now`() {
        val rows = listOf(
            Row("other-evening", inactive = true, start = 20 * 60),
            Row("other-morning", inactive = true, start = 8 * 60),
            Row("other-noon", inactive = true, start = 12 * 60),
        )
        assertEquals(listOf("other-morning", "other-noon", "other-evening"), order(rows, nowMinute = 18 * 60))
    }

    @Test
    fun `courses without a clock sink to the bottom of their group`() {
        val rows = listOf(
            Row("timeless", inactive = true, start = null),
            Row("timed", inactive = true, start = 12 * 60),
        )
        assertEquals(listOf("timed", "timeless"), order(rows, nowMinute = null))
    }
}
