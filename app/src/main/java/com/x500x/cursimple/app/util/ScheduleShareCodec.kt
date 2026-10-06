package com.x500x.cursimple.app.util

import android.util.Base64
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** QR payload format: CSV1 followed by Base64-encoded gzip JSON; prefix versions the codec. */

/** Typed decoding failures for localized UI messages. */
enum class ScheduleShareDecodeReason {
    NotShareData,

    /** Decompressed content exceeds the accepted size. */
    TooLarge,
}

class ScheduleShareDecodeException(
    val reason: ScheduleShareDecodeReason,
) : IllegalArgumentException(reason.name)

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
        if (!trimmed.startsWith(ScheduleSharePayload.MAGIC_PREFIX)) {
            throw ScheduleShareDecodeException(ScheduleShareDecodeReason.NotShareData)
        }
        val body = trimmed.removePrefix(ScheduleSharePayload.MAGIC_PREFIX)
        val gzipped = Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE)
        // Bound decompression to prevent oversized gzip payloads.
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
            if (total > limit) {
                throw ScheduleShareDecodeException(ScheduleShareDecodeReason.TooLarge)
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
