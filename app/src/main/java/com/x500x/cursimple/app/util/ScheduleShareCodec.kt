package com.x500x.cursimple.app.util

import android.util.Base64
import com.x500x.cursimple.core.kernel.model.asExternalImport
import com.x500x.cursimple.core.data.ShareCodeCompression
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

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
        // Absent keys fall back to the model defaults; writing "field": null only lengthened the code.
        explicitNulls = false
    }

    fun encode(payload: ScheduleSharePayload): String {
        val shared = payload.copy(schedule = payload.schedule?.asExternalImport())
        val raw = json.encodeToString(ScheduleSharePayload.serializer(), shared).toByteArray(Charsets.UTF_8)
        val compressed = ShareCodeCompression.compress(raw)
        val encoded = Base64.encodeToString(compressed, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        return ScheduleSharePayload.MAGIC_PREFIX_V2 + encoded
    }

    fun decode(text: String): Result<ScheduleSharePayload> = runCatching {
        if (text.length > MAX_ENCODED_CHARS) {
            throw ScheduleShareDecodeException(ScheduleShareDecodeReason.TooLarge)
        }
        // Messaging apps can insert line breaks into a long code. The alphabet has no whitespace.
        val trimmed = text.filterNot(Char::isWhitespace)
        // CSV2 codes are raw DEFLATE without padding; CSV1 codes from older versions stay readable.
        val legacy = trimmed.startsWith(ScheduleSharePayload.MAGIC_PREFIX)
        if (!legacy && !trimmed.startsWith(ScheduleSharePayload.MAGIC_PREFIX_V2)) {
            throw ScheduleShareDecodeException(ScheduleShareDecodeReason.NotShareData)
        }
        val body = trimmed.removePrefix(if (legacy) ScheduleSharePayload.MAGIC_PREFIX else ScheduleSharePayload.MAGIC_PREFIX_V2)
        val packed = Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE)
        // Bound decompression to prevent oversized payloads.
        val raw = if (legacy) {
            ByteArrayInputStream(packed).use { source ->
                GZIPInputStream(source).use { it.readAtMost(MAX_DECODED_BYTES) }
            }
        } else {
            ShareCodeCompression.decompress(packed, MAX_DECODED_BYTES)
        }
        json.decodeFromString(ScheduleSharePayload.serializer(), raw.toString(Charsets.UTF_8)).also {
            if (it.v != ScheduleSharePayload.CURRENT_VERSION) {
                throw ScheduleShareDecodeException(ScheduleShareDecodeReason.NotShareData)
            }
        }.let { it.copy(schedule = it.schedule?.asExternalImport()) }
    }

    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024
    private const val MAX_ENCODED_CHARS = 6 * 1024 * 1024

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
