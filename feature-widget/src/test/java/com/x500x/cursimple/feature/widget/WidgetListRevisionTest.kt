package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/** Adapter revisions change when date or content changes, otherwise remain stable. */
class WidgetListRevisionTest {

    private fun row(id: String, onHoliday: Boolean = false) = ScheduleWidgetCourseRow(
        id = id,
        nodeRange = "第1-2节",
        timeRange = "08:00–09:40",
        title = "高等数学",
        subtitle = "东12-201",
        hasReminder = false,
        onHoliday = onHoliday,
    )

    @Test
    fun `same content keeps the same revision`() {
        val date = LocalDate.of(2026, 9, 19)

        assertEquals(
            widgetListRevision(date, 0, listOf(row("a"))),
            widgetListRevision(date, 0, listOf(row("a"))),
        )
    }

    @Test
    fun `changing the day or the rows changes the revision`() {
        val date = LocalDate.of(2026, 9, 19)
        val base = widgetListRevision(date, 0, listOf(row("a")))

        assertNotEquals(base, widgetListRevision(date.plusDays(1), 1, listOf(row("a"))))
        assertNotEquals(base, widgetListRevision(date, 0, listOf(row("b"))))
        // Holiday color-only changes must also invalidate the launcher adapter cache.
        assertNotEquals(base, widgetListRevision(date, 0, listOf(row("a", onHoliday = true))))
    }
}
