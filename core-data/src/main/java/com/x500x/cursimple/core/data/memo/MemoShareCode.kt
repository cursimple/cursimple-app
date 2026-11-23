package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.data.ShareCodeCompression
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlin.io.encoding.Base64

enum class MemoShareCodeFailure { Invalid, TooLarge }

class MemoShareCodeException(val reason: MemoShareCodeFailure) : IllegalArgumentException(reason.name)

/** Offline codes carry the same portable bundle as .csnotes, including course names. */
object MemoShareCode {
    const val PREFIX = "CSN1:"

    /** Raw DEFLATE instead of gzip: same content, shorter code. */
    const val PREFIX_V2 = "CSN2:"
    const val MAX_CHARS = 100_000
    // Keep dense codes within QR byte-mode capacity at error correction M.
    const val MAX_QR_CHARS = 2_200

    fun encode(notes: List<MemoNote>, images: List<MemoTransferImage> = emptyList()): String {
        val raw = MemoTransferCodec.encode(notes, images)
        val compressed = ShareCodeCompression.compress(raw)
        val code = PREFIX_V2 + Base64.UrlSafe.encode(compressed)
        if (code.length > MAX_CHARS) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        return code
    }

    fun recognizes(text: String): Boolean {
        if (text.length > MAX_CHARS * 2) return false
        val start = text.trimStart()
        return start.startsWith(PREFIX) || start.startsWith(PREFIX_V2)
    }

    fun decode(text: String): MemoTransferBundle {
        if (text.length > MAX_CHARS * 2) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        val code = text.filterNot(Char::isWhitespace)
        if (code.length > MAX_CHARS) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        // CSN2 is raw DEFLATE; CSN1 codes from older versions keep decoding as gzip.
        val legacy = code.startsWith(PREFIX)
        if (!legacy && !code.startsWith(PREFIX_V2)) throw MemoShareCodeException(MemoShareCodeFailure.Invalid)
        try {
            val compressed = Base64.UrlSafe.decode(code.removePrefix(if (legacy) PREFIX else PREFIX_V2))
            val bytes = if (legacy) {
                GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (out.size() + count > MemoTransferCodec.MAX_BYTES) {
                            throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
                        }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
            } else {
                ShareCodeCompression.decompress(compressed, MemoTransferCodec.MAX_BYTES)
            }
            return MemoTransferCodec.decode(bytes)
        } catch (error: MemoShareCodeException) { throw error }
        catch (_: Exception) { throw MemoShareCodeException(MemoShareCodeFailure.Invalid) }
    }
}
