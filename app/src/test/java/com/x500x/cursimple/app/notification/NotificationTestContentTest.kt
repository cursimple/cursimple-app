package com.x500x.cursimple.app.notification

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NotificationTestContentTest {
    @Test fun oldClientsKeepTheDefaultMessage() {
        val content = notificationTestContent(buildJsonObject { put("targetId", "test") })
        assertEquals("课简通知测试", content.title)
        assertTrue(content.body.contains("连接测试"))
    }
    @Test fun customMessageKeepsNewlinesAndUnicode() {
        val content = notificationTestContent(buildJsonObject { put("title", "  课程提醒 🔔  "); put("body", "第一行\n第二行") })
        assertEquals("课程提醒 🔔", content.title)
        assertEquals("第一行\n第二行", content.body)
    }
    @Test fun invalidContentIsRejectedBeforeEnqueue() {
        for ((key, value) in listOf("title" to JsonPrimitive(" "), "title" to JsonPrimitive("a".repeat(121)),
            "body" to JsonPrimitive("a".repeat(2001)), "body" to JsonPrimitive(123), "body" to JsonNull)) {
            assertThrows(IllegalArgumentException::class.java) { notificationTestContent(JsonObject(mapOf(key to value))) }
        }
    }
}
