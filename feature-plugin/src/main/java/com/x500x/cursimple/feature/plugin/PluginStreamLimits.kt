package com.x500x.cursimple.feature.plugin

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

internal const val STREAM_READ_BUFFER_BYTES = 8 * 1024

internal const val BYTES_PER_MEGABYTE = 1024L * 1024L

/** 本地导入的插件包/组件包上限。 */
internal const val MAX_LOCAL_PACKAGE_BYTES = 64L * 1024L * 1024L

/** 抓包拦截器允许转交给 WebView 的响应体上限。 */
internal const val MAX_INTERCEPTED_BODY_BYTES = 4 * 1024 * 1024

internal const val NETWORK_CAPTURE_CONNECT_TIMEOUT_MS = 15_000

internal const val NETWORK_CAPTURE_READ_TIMEOUT_MS = 20_000

/**
 * 最多读取 [limit] 字节。流中数据超过上限时立即停止并返回 null。
 */
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

/** 安装包超过 [limitBytes] 字节的上限。 */
internal class PluginPackageTooLargeException(val limitBytes: Long) : IllegalArgumentException()

/**
 * 读取本地选中的安装包，超过 [limit] 时中止并抛出，避免整包进内存。
 */
internal fun InputStream.readLocalPackageBytes(limit: Long = MAX_LOCAL_PACKAGE_BYTES): ByteArray {
    return readAtMostBytes(limit) ?: throw PluginPackageTooLargeException(limit)
}

internal fun HttpURLConnection.applyNetworkCaptureTimeouts() {
    connectTimeout = NETWORK_CAPTURE_CONNECT_TIMEOUT_MS
    readTimeout = NETWORK_CAPTURE_READ_TIMEOUT_MS
}
