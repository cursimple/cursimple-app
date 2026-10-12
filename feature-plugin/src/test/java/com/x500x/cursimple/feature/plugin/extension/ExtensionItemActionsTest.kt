package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ExtensionItemActionsTest {
    private val task = ExtensionFeedItem("task-1", title = "Report", kind = "task", dueAt = 100L)
    private val notice = ExtensionFeedItem("notice-1", title = "Notice", kind = "notice")

    @Test fun `completed expired tasks are hidden consistently and explicit restores survive`() {
        val completed = task.copy(id = "completed", done = true)
        val future = task.copy(id = "future", dueAt = 300L)
        val unknown = task.copy(id = "unknown", dueAt = null, startAt = 100L)
        val data = ExtensionData("component", host = ExtensionHostSettings(ignoreOverdue = true,
            addToSchedule = true, schedule = ExtensionScheduleSettings(includeCompleted = true)), items = listOf(completed, task, future, unknown, notice))
        assertTrue(data.isIgnored(completed, 200L))
        assertFalse(data.isIgnored(unknown, 200L))
        val feed = extensionFeedItems(data, 200L, java.time.ZoneOffset.UTC)
        assertEquals(setOf("future", "unknown", "notice-1"), feed.map { it.id }.toSet())
        assertFalse(extensionScheduleItems(data, 200L, java.time.ZoneOffset.UTC).any { it.first.id == "completed" || it.first.id == task.id })
        assertFalse(data.withItemIgnored(completed.id, false).isIgnored(completed, 200L))
    }

    @Test fun `expiry boundary includes completed tasks and expired sessions but not restored items`() {
        val data = ExtensionData("component", loginState = ExtensionLoginState.Expired,
            host = ExtensionHostSettings(ignoreOverdue = true), items = listOf(task.copy(done = true)))
        assertEquals(100L, nextIgnoredDeadline(listOf(data), 99L))
        assertNull(nextIgnoredDeadline(listOf(data), 100L))
        assertNull(nextIgnoredDeadline(listOf(data.withItemIgnored(task.id, false)), 99L))
        assertNull(nextIgnoredDeadline(listOf(data.copy(loginState = ExtensionLoginState.LoggedOut)), 99L))
    }

    @Test fun `automatic overdue ignoring defaults off and restores survive later checks`() {
        val data = ExtensionData("component", items = listOf(task, notice))
        assertFalse(data.isIgnored(task, 200L))
        val automatic = data.copy(host = data.host.copy(ignoreOverdue = true))
        assertTrue(automatic.isIgnored(task, 100L))
        assertFalse(automatic.isIgnored(notice, 200L))
        val restored = automatic.withItemIgnored(task.id, false)
        assertFalse(restored.isIgnored(task, 1000L))
        assertTrue(restored.withItemIgnored(task.id, true).isIgnored(task, 1000L))
    }

    @Test fun `ignored items persist across disk encoding and snapshot sync`() {
        val saved = ExtensionData("component", items = listOf(task)).withItemIgnored(task.id, true)
        val restored = Json.decodeFromString<ExtensionData>(Json.encodeToString(saved))
        val run = ExtensionRunResult.Completed(JsonObject(emptyMap()), listOf(task.copy(title = "Updated report")), emptyMap())
        val merged = mergeSyncResult(restored, run, 300L).data
        assertTrue(merged.isIgnored(merged.items.single(), 300L))
        assertEquals("Updated report", merged.items.single().title)
        assertFalse(merged.withItemIgnored(task.id, false).isIgnored(task, 300L))
    }

    @Test fun `read status requires confirmation for the matching item`() {
        val data = ExtensionData("component", items = listOf(notice, task))
        val rejected = ExtensionRunResult.Completed(JsonObject(mapOf("itemId" to JsonPrimitive(notice.id), "done" to JsonPrimitive(false))), emptyList(), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { confirmedReadResult(data, notice.id, rejected) }
        assertFalse(data.items.first().done)
        val wrong = rejected.copy(result = JsonObject(mapOf("itemId" to JsonPrimitive("other"), "done" to JsonPrimitive(true))))
        assertThrows(IllegalArgumentException::class.java) { confirmedReadResult(data, notice.id, wrong) }
        val accepted = rejected.copy(result = JsonObject(mapOf("itemId" to JsonPrimitive(notice.id), "done" to JsonPrimitive(true))))
        val read = confirmedReadResult(data, notice.id, accepted)
        assertTrue(read.items.first().done)
        assertFalse(read.items.last().done)
    }
}
