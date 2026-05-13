package com.x500x.cursimple.app.util

import android.util.Base64
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encodes a [ScheduleSharePayload] into a compact string suitable for embedding inside a QR
 * code, and decodes it back.
 *
 * Format: `CSV1:<base64(gzip(json))>`. The magic prefix lets us reject random text quickly
 * and version the format if we ever change the encoding.
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
