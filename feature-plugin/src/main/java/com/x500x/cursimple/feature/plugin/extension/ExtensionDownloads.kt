package com.x500x.cursimple.feature.plugin.extension

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
internal data class ExtensionDownload(
    val id: String,
    val name: String,
    val url: String,
    val mime: String,
    val size: Long,
    val savedAt: Long,
)

/**
 * Component-scoped attachment storage is removed by [ExtensionStore.remove]; the host handles
 * generic file operations only.
 */
internal class ExtensionDownloads(context: Context, pluginId: String) {
    private val app = context.applicationContext
    private val dir = downloadDir(app.filesDir, pluginId)
    private val index = File(dir, ".index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(ExtensionDownload.serializer())

    suspend fun list(): List<ExtensionDownload> = withContext(Dispatchers.IO) {
        lock.withLock { read().filter { fileOf(it).isFile }.sortedByDescending { it.savedAt } }
    }

    suspend fun find(url: String): ExtensionDownload? = list().firstOrNull { it.url == url }

    suspend fun save(url: String, name: String, hint: String, userAgent: String?): ExtensionDownload {
        find(url)?.let { return it }
        val fetched = ExtensionMediaLoader(app).fetch(url, name, hint, userAgent = userAgent)
        return withContext(Dispatchers.IO) {
            lock.withLock {
                dir.mkdirs()
                val entry = ExtensionDownload(
                    id = idOf(url),
                    name = fetched.name,
                    url = url,
                    mime = fetched.mime,
                    size = fetched.file.length(),
                    savedAt = System.currentTimeMillis(),
                )
                fetched.file.copyTo(fileOf(entry), overwrite = true)
                write(read().filterNot { it.id == entry.id } + entry)
                entry
            }
        }
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = read()
            val entry = all.firstOrNull { it.id == id } ?: return@withLock false
            fileOf(entry).delete()
            write(all - entry)
            true
        }
    }

    suspend fun file(id: String): Pair<ExtensionDownload, File> {
        val entry = list().firstOrNull { it.id == id } ?: throw IOException("文件已被删除")
        return entry to fileOf(entry)
    }

    private fun fileOf(entry: ExtensionDownload) = File(dir, "${entry.id}-${extensionMediaFileName(entry.name)}")

    private fun read(): List<ExtensionDownload> =
        runCatching { json.decodeFromString(serializer, index.readText()) }.getOrDefault(emptyList())

    private fun write(entries: List<ExtensionDownload>) {
        val partial = File(dir, ".index.json.part")
        partial.writeText(json.encodeToString(serializer, entries))
        if (!partial.renameTo(index)) { index.writeText(partial.readText()); partial.delete() }
    }

    companion object {
        private val lock = Mutex()
        fun downloadDir(filesDir: File, pluginId: String) = File(filesDir, "extension-downloads/$pluginId")
        fun idOf(url: String): String =
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}

internal fun openExtensionFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Use MediaStore on Android 10+; older systems use sharing without requiring storage
 * permission. True confirms a gallery write.
 */
internal suspend fun saveExtensionImage(context: Context, file: File, name: String, mime: String): Boolean {
    val safeMime = mime.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, extensionMediaFileName(name))
                put(MediaStore.Images.Media.MIME_TYPE, safeMime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/课简")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("无法写入相册")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("无法写入相册")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (failure: Exception) {
                resolver.delete(uri, null, null)
                throw failure
            }
            true
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(safeMime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "保存图片",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return false
}
