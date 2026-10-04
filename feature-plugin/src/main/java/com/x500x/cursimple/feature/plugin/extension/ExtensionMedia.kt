package com.x500x.cursimple.feature.plugin.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal fun extensionMediaUrl(raw: String): String? = raw.trim().toHttpUrlOrNull()
    ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
    ?.toString()

internal fun extensionMediaFileName(raw: String): String = raw.substringAfterLast('/').substringAfterLast('\\')
    .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim().trim('.').take(160).ifBlank { "attachment" }

internal fun extensionMediaMime(name: String, hint: String = ""): String {
    val typed = hint.lowercase().substringBefore(';').trim()
    if (Regex("[a-z0-9.+-]+/[a-z0-9.+-]+").matches(typed)) return typed
    return when (name.substringAfterLast('.', typed).lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt", "md", "log" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}

internal data class ExtensionMediaFile(val file: File, val name: String, val mime: String)

/** 图片及文件共用登录 Cookie；重定向时重新按目标域名取 Cookie，不把学校会话转发给其他域。 */
internal class ExtensionMediaLoader(context: Context) {
    private val app = context.applicationContext

    suspend fun fetch(url: String, name: String, hint: String = "", referer: String = "", userAgent: String? = null): ExtensionMediaFile {
        val original = requireNotNull(extensionMediaUrl(url)) { "Invalid media URL" }
        val safeName = extensionMediaFileName(name.ifBlank { original.toHttpUrlOrNull()!!.pathSegments.lastOrNull().orEmpty() })
        val digest = MessageDigest.getInstance("SHA-256").digest(original.toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(app.cacheDir, "extension-media").apply { mkdirs() }
        val file = File(cache, "$digest-$safeName")
        return withContext(Dispatchers.IO) {
            val inferredMime = extensionMediaMime(safeName, hint)
            if (file.isFile && file.length() > 0L) return@withContext ExtensionMediaFile(file, safeName, inferredMime)
            var target = original
            repeat(6) {
                coroutineContext.ensureActive()
                val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(target) }
                val request = Request.Builder().url(target).apply {
                    extensionMediaUrl(referer)?.let { header("Referer", it) }
                    cookie?.takeIf(String::isNotBlank)?.let { header("Cookie", it) }
                    userAgent?.takeIf(String::isNotBlank)?.let { header("User-Agent", it) }
                }.build()
                val call = client.newCall(request)
                try {
                    call.execute().use { response ->
                        if (response.code in listOf(301, 302, 303, 307, 308)) {
                            target = response.header("Location")?.let { location -> response.request.url.resolve(location)?.toString() }
                                ?.let(::extensionMediaUrl) ?: throw IOException("Invalid download redirect")
                        } else {
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            val body = response.body ?: throw IOException("Empty download")
                            val responseMime = body.contentType()?.toString()?.substringBefore(';')
                            if (responseMime == "text/html" && inferredMime != "text/html") throw IOException("Login or download page returned instead of a file")
                            val maxBytes = if (inferredMime.startsWith("image/")) 32L * 1024 * 1024 else 256L * 1024 * 1024
                            if (body.contentLength() > maxBytes) throw IOException("File exceeds ${maxBytes / 1024 / 1024} MB")
                            val partial = File.createTempFile("media-", ".part", cache)
                            try {
                                body.byteStream().use { input -> partial.outputStream().use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    var total = 0L
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > maxBytes) throw IOException("File exceeds ${maxBytes / 1024 / 1024} MB")
                                        output.write(buffer, 0, count)
                                    }
                                } }
                                if (partial.length() == 0L) throw IOException("Empty file")
                                if (!partial.renameTo(file)) throw IOException("Cannot save file")
                            } finally {
                                partial.delete()
                            }
                            return@withContext ExtensionMediaFile(file, safeName, responseMime?.takeUnless { it == "application/octet-stream" } ?: inferredMime)
                        }
                    }
                } finally {
                    call.cancel()
                }
            }
            throw IOException("Too many download redirects")
        }
    }

    suspend fun bitmap(file: File): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
        var sample = 1
        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("Cannot decode image")
    }

    companion object {
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    }
}
