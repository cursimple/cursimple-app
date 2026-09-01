package com.x500x.cursimple.core.plugin.packageformat

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val BOUNDED_READ_BUFFER_BYTES = 8 * 1024

/**
 * 最多读取 [limit] 字节。流中数据超过上限时立即停止并返回 null，不会把整段内容读进内存。
 */
internal fun InputStream.readAtMostBytes(limit: Long): ByteArray? {
    if (limit < 0L) {
        return null
    }
    val initialCapacity = minOf(limit + 1L, BOUNDED_READ_BUFFER_BYTES.toLong()).toInt()
    val output = ByteArrayOutputStream(initialCapacity)
    val buffer = ByteArray(BOUNDED_READ_BUFFER_BYTES)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            break
        }
        if (read == 0) {
            continue
        }
        total += read.toLong()
        if (total > limit) {
            return null
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
