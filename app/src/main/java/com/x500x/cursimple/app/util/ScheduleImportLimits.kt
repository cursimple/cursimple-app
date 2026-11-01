package com.x500x.cursimple.app.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Limits apply before parsing/recurrence expansion, including direct String entry points. */
internal object ScheduleImportLimits {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_COURSES = 2_000
    const val MAX_OCCURRENCES = 20_000

    fun read(stream: InputStream, limit: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer, 0, minOf(buffer.size, limit - out.size() + 1))
            if (count == -1) return out.toByteArray()
            require(out.size() + count <= limit) { "导入文件过大" }
            out.write(buffer, 0, count)
        }
    }

    fun validateText(text: String, json: Boolean = false) {
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "导入文件过大" }
        if (!json) return
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '[', '{' -> { depth++; require(depth <= 64) { "JSON 嵌套过深" } }
                ']', '}' -> depth--
            }
        }
    }
}
