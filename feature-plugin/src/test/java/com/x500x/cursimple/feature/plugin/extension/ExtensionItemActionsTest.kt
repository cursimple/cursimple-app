package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ExtensionItemActionsTest {
    private val task = ExtensionFeedItem("task-1", title = "Report", kind = "task", dueAt = 100L)
    private val notice = ExtensionFeedItem("notice-1", title = "Notice", kind = "notice")

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
