package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/** 列表适配器的内容版本号：内容没变就别换，换了日期或课程就必须换。 */
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
        // 放假态只改了一行的灰字，也得换版本号，否则启动器会留着上一天的那一份
        assertNotEquals(base, widgetListRevision(date, 0, listOf(row("a", onHoliday = true))))
    }
}
