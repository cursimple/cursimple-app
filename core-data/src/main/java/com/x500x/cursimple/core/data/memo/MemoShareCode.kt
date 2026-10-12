package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64

enum class MemoShareCodeFailure { Invalid, TooLarge }

class MemoShareCodeException(val reason: MemoShareCodeFailure) : IllegalArgumentException(reason.name)

/** Offline codes carry the same portable bundle as .csnotes, including course names. */
object MemoShareCode {
    const val PREFIX = "CSN1:"
    const val MAX_CHARS = 100_000
    // Keep dense codes within QR byte-mode capacity at error correction M.
    const val MAX_QR_CHARS = 2_200

    fun encode(notes: List<MemoNote>, images: List<MemoTransferImage> = emptyList()): String {
        val raw = MemoTransferCodec.encode(notes, images)
        val compressed = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(raw) }
            out.toByteArray()
        }
        val code = PREFIX + Base64.UrlSafe.encode(compressed)
        if (code.length > MAX_CHARS) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        return code
    }

    fun recognizes(text: String): Boolean = text.length <= MAX_CHARS * 2 && text.trimStart().startsWith(PREFIX)

    fun decode(text: String): MemoTransferBundle {
        if (text.length > MAX_CHARS * 2) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        val code = text.filterNot(Char::isWhitespace)
        if (code.length > MAX_CHARS) throw MemoShareCodeException(MemoShareCodeFailure.TooLarge)
        if (!code.startsWith(PREFIX)) throw MemoShareCodeException(MemoShareCodeFailure.Invalid)
        try {
            val compressed = Base64.UrlSafe.decode(code.removePrefix(PREFIX))
            val bytes = GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
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
            return MemoTransferCodec.decode(bytes)
        } catch (error: MemoShareCodeException) { throw error }
        catch (_: Exception) { throw MemoShareCodeException(MemoShareCodeFailure.Invalid) }
    }
}
