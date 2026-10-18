package com.x500x.cursimple.core.data.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 事务按学期分桶存储，旧的无学期键只在迁移前作为兜底。 */
class ScheduleEventTermStorageTest {

    @Test
    fun `each term keeps its own key`() {
        assertEquals("schedule_events_json__term-a", scheduleEventsPreferenceKey("term-a"))
        assertEquals("schedule_events_json__term-b", scheduleEventsPreferenceKey("term-b"))
    }

    @Test
    fun `no active term falls back to the legacy key`() {
        assertEquals("schedule_events_json", scheduleEventsPreferenceKey(""))
    }

    @Test
    fun `reads prefer the active term and only fall back to legacy`() {
        val stored = mapOf(
            "schedule_events_json__term-a" to "A",
            "schedule_events_json__term-b" to "B",
            "schedule_events_json" to "legacy",
        )

        assertEquals("A", resolveStoredEvents(stored::get, "term-a"))
        assertEquals("B", resolveStoredEvents(stored::get, "term-b"))
        assertEquals("legacy", resolveStoredEvents(stored::get, "term-c"))
    }

    @Test
    fun `a term with no stored events resolves to nothing`() {
        val stored = mapOf("schedule_events_json__term-a" to "A")

        assertNull(resolveStoredEvents(stored::get, "term-b"))
        assertNull(resolveStoredEvents(emptyMap<String, String>()::get, ""))
    }
}
