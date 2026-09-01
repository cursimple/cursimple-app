package com.x500x.cursimple.app.util

import android.util.Base64
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 把 [ScheduleSharePayload] 编码成适合嵌入二维码的紧凑字符串，并支持反向解码。
 *
 * 格式为 `CSV1:<base64(gzip(json))>`。前缀用于快速排除无关文本，也为编码格式提供版本标识。
 */
object ScheduleShareCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(payload: ScheduleSharePayload): String {
        val raw = json.encodeToString(ScheduleSharePayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val gzipped = ByteArrayOutputStream().use { sink ->
            GZIPOutputStream(sink).use { it.write(raw) }
            sink.toByteArray()
        }
        val encoded = Base64.encodeToString(gzipped, Base64.NO_WRAP or Base64.URL_SAFE)
        return ScheduleSharePayload.MAGIC_PREFIX + encoded
    }

    fun decode(text: String): Result<ScheduleSharePayload> = runCatching {
        val trimmed = text.trim()
        require(trimmed.startsWith(ScheduleSharePayload.MAGIC_PREFIX)) {
            "二维码内容不是课表分享数据"
        }
        val body = trimmed.removePrefix(ScheduleSharePayload.MAGIC_PREFIX)
        val gzipped = Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE)
        val raw = ByteArrayInputStream(gzipped).use { source ->
            GZIPInputStream(source).use { it.readBytes() }
        }
        json.decodeFromString(ScheduleSharePayload.serializer(), raw.toString(Charsets.UTF_8))
    }
}
