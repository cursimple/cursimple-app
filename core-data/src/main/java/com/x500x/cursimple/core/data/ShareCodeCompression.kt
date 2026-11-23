package com.x500x.cursimple.core.data

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Raw DEFLATE for share codes: the gzip container adds its own header and trailer, which cost about
 * twenty bytes (twenty-seven Base64 characters) on every code. Same deflate stream, no wrapper.
 */
object ShareCodeCompression {
    fun compress(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, /* nowrap = */ true)
        return try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream(raw.size / 2 + 64)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** [maxBytes] bounds the output so a crafted code cannot expand without limit. */
    fun decompress(compressed: ByteArray, maxBytes: Int): ByteArray {
        val inflater = Inflater(/* nowrap = */ true)
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(compressed.size * 3 + 64)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (_: DataFormatException) {
                    throw IllegalArgumentException("inflate_failed")
                }
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw IllegalArgumentException("inflate_truncated")
                    }
                } else {
                    if (out.size() + count > maxBytes) throw IllegalArgumentException("inflate_too_large")
                    out.write(buffer, 0, count)
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
