package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.data.widget.PendingTask
import com.x500x.cursimple.core.data.widget.PendingTaskMomentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PendingTaskRowsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ms("2026-10-03T21:00")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: String, due: String? = null, start: String? = null) =
        PendingTask(id = id, sourceId = "c", sourceTitle = "组件", title = id, dueAtMillis = due?.let(::ms), startAtMillis = start?.let(::ms))

    @Test fun `rows are ordered upcoming then undated then most recent overdue`() {
        val rows = pendingTaskRows(
            listOf(
                task("old", due = "2026-09-20T23:59"),
                task("later", due = "2026-10-10T23:59"),
                task("none"),
                task("soon", due = "2026-10-03T23:59"),
                task("recent", due = "2026-10-02T23:59"),
            ),
            now, zone,
        )
        assertEquals(listOf("soon", "later", "none", "recent", "old"), rows.map { it.task.id })
    }

    @Test fun `urgency rounds up and switches units at an hour and a day`() {
        val rows = pendingTaskRows(
            listOf(
                task("m", due = "2026-10-03T21:20"),
                task("h", due = "2026-10-03T23:59"),
                task("d", due = "2026-10-05T08:00"),
                task("x", due = "2026-10-03T20:00"),
            ),
            now, zone,
        ).associateBy { it.task.id }
        assertEquals(PendingTaskUrgency.Minutes(20), rows.getValue("m").urgency)
        assertEquals(PendingTaskUrgency.Hours(3), rows.getValue("h").urgency)
        assertEquals(PendingTaskUrgency.Days(2), rows.getValue("d").urgency)
        assertEquals(PendingTaskUrgency.Overdue, rows.getValue("x").urgency)
        assertTrue(rows.getValue("m").urgent)
        assertTrue(rows.getValue("x").urgent)
        assertFalse(rows.getValue("d").urgent)
        assertEquals(0L, rows.getValue("h").dayDelta)
        assertEquals(2L, rows.getValue("d").dayDelta)
    }

    @Test fun `a scheduled session shows its start until it begins and its deadline afterwards`() {
        val before = pendingTaskRows(listOf(task("exam", start = "2026-10-04T09:00", due = "2026-10-04T11:00")), now, zone).single()
        assertEquals(PendingTaskMomentKind.Start, before.momentKind)
        assertEquals(1L, before.dayDelta)
        val during = pendingTaskRows(listOf(task("exam", start = "2026-10-03T20:00", due = "2026-10-03T22:00")), now, zone).single()
        assertEquals(PendingTaskMomentKind.Due, during.momentKind)
        assertEquals(PendingTaskUrgency.Hours(1), during.urgency)
        val startOnly = pendingTaskRows(listOf(task("live", start = "2026-10-03T20:00")), now, zone).single()
        assertEquals(PendingTaskUrgency.Started, startOnly.urgency)
    }

    @Test fun `tasks without any time are kept and marked as such`() {
        val row = pendingTaskRows(listOf(task("none")), now, zone).single()
        assertEquals(PendingTaskUrgency.NoTime, row.urgency)
        assertEquals(null, row.momentAt)
    }

    @Test fun `row count follows the real height so the last row is never cut in half`() {
        assertEquals(1, taskRowsForHeight(110))
        assertEquals(3, taskRowsForHeight(230))
        assertEquals(6, taskRowsForHeight(400))
        assertEquals(1, taskRowsForHeight(40))
        assertEquals(8, taskRowsForHeight(2000))
    }
}
