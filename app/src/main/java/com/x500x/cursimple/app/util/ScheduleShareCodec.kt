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
        // 解压封顶，防止构造出的「gzip 炸弹」把内存撑爆（正常分享码解压后也就几十 KB）
        val raw = ByteArrayInputStream(gzipped).use { source ->
            GZIPInputStream(source).use { it.readAtMost(MAX_DECODED_BYTES) }
        }
        json.decodeFromString(ScheduleSharePayload.serializer(), raw.toString(Charsets.UTF_8))
    }

    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "分享数据过大" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
