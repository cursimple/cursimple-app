package com.x500x.cursimple.app.webdav

import com.xayah.libsardine.DavResource
import com.x500x.cursimple.R
import com.xayah.libsardine.impl.OkHttpSardine
import com.xayah.libsardine.impl.SardineException
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

class WebDavClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(25))
        .build(),
) {
    fun test(config: WebDavConfig) {
        webDavRequire(config.isComplete, R.string.webdav_config_incomplete)
        runWebDav {
            webDav(config).list(config.url.directoryUrl(), 0)
        }
    }

    fun uploadBackup(config: WebDavConfig, name: String, bytes: ByteArray): WebDavBackupFile {
        return runWebDav {
            val dav = webDav(config)
            val backupDir = ensureBackupCollectionWith(dav, config)
            val target = backupDir.resolveFile(name)
            dav.put(target, bytes, BACKUP_MEDIA_TYPE)
            WebDavBackupFile(name = name, href = target, size = bytes.size.toLong(), lastModified = null)
        }
    }

    fun listBackups(config: WebDavConfig): List<WebDavBackupFile> {
        return runWebDav {
            val dav = webDav(config)
            val backupDir = ensureBackupCollectionWith(dav, config)
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
        webDavRequire(config.isComplete, R.string.webdav_config_incomplete)
        val baseUrl = config.url.directoryUrl()
        val url = requireSameWebDavOrigin(
            baseUrl = baseUrl,
            url = requireHttpsWebDavUrl(href.toAbsoluteAgainst(baseUrl)),
        )
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

    private fun ensureBackupCollectionWith(dav: OkHttpSardine, config: WebDavConfig): String {
        webDavRequire(config.isComplete, R.string.webdav_config_incomplete)
        val appDir = config.url.directoryUrl().resolveDirectory(APP_DIR)
        val backupDir = appDir.resolveDirectory(BACKUP_DIR)
        dav.createDirectoryIfMissing(appDir)
        dav.createDirectoryIfMissing(backupDir)
        return backupDir
    }

    private fun OkHttpSardine.createDirectoryIfMissing(url: String) {
        try {
            createDirectory(url)
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

    private fun String.directoryUrl(): String = normalizeSecureWebDavDirectoryUrl(this)

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
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(toInstant().atZone(ZoneOffset.UTC))
    }

    private fun IOException.toReadableException(): IOException {
        if (this is WebDavRequestException) return this
        if (this is SardineException) {
            val hintRes = when (statusCode) {
                HTTP_BAD_REQUEST -> R.string.webdav_hint_bad_request
                HTTP_UNAUTHORIZED -> R.string.webdav_hint_unauthorized
                HTTP_FORBIDDEN -> R.string.webdav_hint_forbidden
                HTTP_NOT_FOUND -> R.string.webdav_hint_not_found
                else -> null
            }
            val status = buildString {
                append(statusCode)
                responsePhrase?.takeIf(String::isNotBlank)?.let { append(" ").append(it) }
            }
            return if (hintRes == null) {
                WebDavRequestException(R.string.webdav_request_failed_status, listOf(status), this)
            } else {
                WebDavRequestException(
                    messageRes = R.string.webdav_request_failed_status_hint,
                    formatArgs = listOf(status, WebDavTextArg(hintRes)),
                    cause = this,
                )
            }
        }
        return WebDavRequestException(
            messageRes = R.string.webdav_request_failed_reason,
            formatArgs = listOf(message ?: WebDavTextArg(R.string.webdav_generic_failure)),
            cause = this,
        )
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

internal fun normalizeSecureWebDavDirectoryUrl(rawUrl: String): String {
    val trimmedRaw = rawUrl.trim().ifBlank { webDavError(R.string.webdav_url_blank) }
    val url = requireHttpsWebDavUrl(
        if (trimmedRaw.contains("://")) trimmedRaw else "https://$trimmedRaw",
    )
    return if (url.endsWith("/")) url else "$url/"
}

// 服务端返回的 href 可能指向别的主机，下载前限制在配置的服务器上，避免 Basic 凭据外发
internal fun requireSameWebDavOrigin(baseUrl: String, url: String): String {
    val baseUri = runCatching { URI(baseUrl) }.getOrNull()
    val targetUri = runCatching { URI(url) }.getOrNull()
    val baseHost = baseUri?.host
    val targetHost = targetUri?.host
    webDavRequire(!baseHost.isNullOrBlank() && !targetHost.isNullOrBlank(), R.string.webdav_backup_url_unparseable)
    webDavRequire(
        targetHost.equals(baseHost, ignoreCase = true) && webDavPort(targetUri) == webDavPort(baseUri),
        R.string.webdav_backup_host_mismatch,
    )
    return url
}

private fun webDavPort(uri: URI?): Int {
    val port = uri?.port ?: -1
    return if (port != -1) port else DEFAULT_HTTPS_PORT
}

private const val DEFAULT_HTTPS_PORT = 443

internal fun requireHttpsWebDavUrl(url: String): String {
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    webDavRequire(scheme == "https", R.string.webdav_https_required)
    return url
}
