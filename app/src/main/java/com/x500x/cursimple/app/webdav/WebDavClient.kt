package com.x500x.cursimple.app.webdav

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.impl.SardineException
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Date

class WebDavClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(25))
        .build(),
) {
    fun test(config: WebDavConfig) {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        runWebDav {
            webDav(config).list(config.url.directoryUrl(), 0)
        }
    }

    fun ensureBackupCollection(config: WebDavConfig): String {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        return runWebDav {
            val dav = webDav(config)
            val appDir = config.url.directoryUrl().resolveDirectory(APP_DIR)
            val backupDir = appDir.resolveDirectory(BACKUP_DIR)
            dav.createDirectoryIfMissing(appDir)
            dav.createDirectoryIfMissing(backupDir)
            backupDir
        }
    }

    fun uploadBackup(config: WebDavConfig, name: String, bytes: ByteArray): WebDavBackupFile {
        return runWebDav {
            val dav = webDav(config)
            val backupDir = ensureBackupCollection(config)
            val target = backupDir.resolveFile(name)
            dav.put(target, bytes, BACKUP_MEDIA_TYPE)
            WebDavBackupFile(name = name, href = target, size = bytes.size.toLong(), lastModified = null)
        }
    }

    fun listBackups(config: WebDavConfig): List<WebDavBackupFile> {
        return runWebDav {
            val dav = webDav(config)
            val backupDir = ensureBackupCollection(config)
            dav.list(backupDir, 1)
                .asSequence()
                .filterNot(DavResource::isDirectory)
                .mapNotNull { resource -> resource.toBackupFile(backupDir) }
                .distinctBy(WebDavBackupFile::href)
                .sortedByDescending(WebDavBackupFile::name)
                .toList()
        }
    }

    fun download(config: WebDavConfig, href: String): ByteArray {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        val url = href.toAbsoluteAgainst(config.url.directoryUrl())
        return runWebDav {
            webDav(config).get(url).use { stream ->
                stream.readBytes()
            }
        }
    }

    private fun webDav(config: WebDavConfig): OkHttpSardine {
        return OkHttpSardine(httpClient).apply {
            setCredentials(config.username, config.password, true)
        }
    }

    private fun OkHttpSardine.createDirectoryIfMissing(url: String) {
        try {
            if (!exists(url)) {
                createDirectory(url)
            }
        } catch (error: SardineException) {
            if (error.statusCode != HTTP_METHOD_NOT_ALLOWED) throw error
        }
    }

    private inline fun <T> runWebDav(block: () -> T): T {
        return try {
            block()
        } catch (error: SardineException) {
            throw error.toReadableException()
        } catch (error: IOException) {
            throw error.toReadableException()
        }
    }

    private fun DavResource.toBackupFile(baseUrl: String): WebDavBackupFile? {
        val name = name?.takeIf { it.endsWith(BACKUP_EXTENSION, ignoreCase = true) } ?: return null
        return WebDavBackupFile(
            name = name,
            href = href.toString().toAbsoluteAgainst(baseUrl),
            size = contentLength ?: 0L,
            lastModified = modified?.formatWebDavDate(),
        )
    }

    private fun String.directoryUrl(): String = trim().ifBlank {
        error("WebDAV URL 不能为空")
    }.let { value ->
        if (value.endsWith("/")) value else "$value/"
    }

    private fun String.resolveDirectory(child: String): String = directoryUrl() + child.toPathSegment() + "/"

    private fun String.resolveFile(child: String): String = directoryUrl() + child.toPathSegment()

    private fun String.toPathSegment(): String = trim('/').split('/')
        .filter(String::isNotBlank)
        .joinToString("/") { segment ->
            URI(null, null, segment, null).rawPath
        }

    private fun String.toAbsoluteAgainst(baseUrl: String): String {
        return runCatching { URI(baseUrl).resolve(this).toString() }.getOrDefault(this)
    }

    private fun Date.formatWebDavDate(): String {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(toInstant().atZone(java.time.ZoneOffset.UTC))
    }

    private fun IOException.toReadableException(): IOException {
        if (message?.startsWith("WebDAV 请求失败：") == true) return this
        if (this is SardineException) {
            val hint = when (statusCode) {
                HTTP_BAD_REQUEST -> "请检查 WebDAV URL 是否为服务商提供的目录地址，并确保以 / 结尾"
                HTTP_UNAUTHORIZED -> "请检查账号、密码或应用专用密码"
                HTTP_FORBIDDEN -> "账号无权访问或写入该 WebDAV 目录，请换成可写目录"
                HTTP_NOT_FOUND -> "WebDAV 目录不存在或 URL 写错"
                else -> null
            }
            val message = buildString {
                append("WebDAV 请求失败：HTTP ")
                append(statusCode)
                responsePhrase?.takeIf(String::isNotBlank)?.let { append(" ").append(it) }
                if (hint != null) append("。").append(hint)
            }
            return IOException(message, this)
        }
        return IOException("WebDAV 请求失败：${message ?: "网络或服务端异常"}", this)
    }

    private companion object {
        const val APP_DIR = "cursimple"
        const val BACKUP_DIR = "backups"
        const val BACKUP_EXTENSION = ".json"
        const val BACKUP_MEDIA_TYPE = "application/vnd.cursimple.backup+json"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}
