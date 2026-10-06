package com.x500x.cursimple.feature.plugin

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

internal const val STREAM_READ_BUFFER_BYTES = 8 * 1024

internal const val BYTES_PER_MEGABYTE = 1024L * 1024L

/** Maximum local plugin or component package size. */
internal const val MAX_LOCAL_PACKAGE_BYTES = 64L * 1024L * 1024L

/** Maximum intercepted body forwarded to WebView. */
internal const val MAX_INTERCEPTED_BODY_BYTES = 4 * 1024 * 1024

internal const val NETWORK_CAPTURE_CONNECT_TIMEOUT_MS = 15_000

internal const val NETWORK_CAPTURE_READ_TIMEOUT_MS = 20_000

/** Read at most [limit] bytes; null if exceeded. */
internal fun InputStream.readAtMostBytes(limit: Long): ByteArray? {
    if (limit < 0L) {
        return null
    }
    val initialCapacity = minOf(limit + 1L, STREAM_READ_BUFFER_BYTES.toLong()).toInt()
    val output = ByteArrayOutputStream(initialCapacity)
    val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
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

/** Package exceeds [limitBytes]. */
internal class PluginPackageTooLargeException(val limitBytes: Long) : IllegalArgumentException()

/** Abort local package reading above [limit] before allocating the whole file. */
internal fun InputStream.readLocalPackageBytes(limit: Long = MAX_LOCAL_PACKAGE_BYTES): ByteArray {
    return readAtMostBytes(limit) ?: throw PluginPackageTooLargeException(limit)
}

internal fun HttpURLConnection.applyNetworkCaptureTimeouts() {
    connectTimeout = NETWORK_CAPTURE_CONNECT_TIMEOUT_MS
    readTimeout = NETWORK_CAPTURE_READ_TIMEOUT_MS
}
