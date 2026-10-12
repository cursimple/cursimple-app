package com.x500x.cursimple.app.notification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class NotificationTestContent(val title: String, val body: String)

internal fun notificationTestContent(payload: JsonObject): NotificationTestContent {
    fun text(key: String, fallback: String, limit: Int): String {
        if (key !in payload) return fallback
        val value = payload[key] as? JsonPrimitive
        require(value != null && value.isString) { "测试内容格式无效" }
        val text = value.content.trim()
        require(text.isNotEmpty() && text.length <= limit) { "测试${if (key == "title") "标题" else "正文"}须为 1–$limit 字符" }
        return text
    }
    return NotificationTestContent(
        text("title", "课简通知测试", 120),
        text("body", "这是一条连接测试消息。收到它表示当前目标已接入。", 2000),
    )
}
