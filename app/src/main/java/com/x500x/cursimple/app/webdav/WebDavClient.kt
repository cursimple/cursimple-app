package com.x500x.cursimple.app.webdav

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

class WebDavClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(25))
        .build(),
) {
    fun test(config: WebDavConfig) {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        propfind(config, config.url.directoryUrl(), depth = "0").close()
    }

    fun ensureBackupCollection(config: WebDavConfig): String {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        val root = config.url.directoryUrl()
        val appDir = root.resolveChild(APP_DIR)
        val backupDir = appDir.resolveChild(BACKUP_DIR)
        mkcolIfMissing(config, appDir)
        mkcolIfMissing(config, backupDir)
        return backupDir
    }

    fun uploadBackup(config: WebDavConfig, name: String, bytes: ByteArray): WebDavBackupFile {
        val backupDir = ensureBackupCollection(config)
        val target = backupDir.resolveChild(name)
        val request = requestBuilder(config, target)
            .put(bytes.toRequestBody(BACKUP_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WebDAV 上传失败：HTTP ${response.code}" }
        }
        return WebDavBackupFile(name = name, href = target, size = bytes.size.toLong(), lastModified = null)
    }

    fun listBackups(config: WebDavConfig): List<WebDavBackupFile> {
        val backupDir = ensureBackupCollection(config)
        val body = propfind(config, backupDir, depth = "1").use { response ->
            response.body.string()
        }
        return parseBackupFiles(body, backupDir)
    }

    fun download(config: WebDavConfig, href: String): ByteArray {
        require(config.isComplete) { "请先完整填写 WebDAV URL、账号和密码" }
        val url = href.toAbsoluteAgainst(config.url.directoryUrl())
        val request = requestBuilder(config, url).get().build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WebDAV 下载失败：HTTP ${response.code}" }
            response.body.bytes()
        }
    }

    private fun propfind(config: WebDavConfig, url: String, depth: String): okhttp3.Response {
        val request = requestBuilder(config, url)
            .method(
                "PROPFIND",
                PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()),
            )
            .header("Depth", depth)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("WebDAV 访问失败：HTTP $code")
        }
        return response
    }

    private fun mkcolIfMissing(config: WebDavConfig, url: String) {
        val request = requestBuilder(config, url)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 201 || response.code == 405) return
            check(response.isSuccessful) { "WebDAV 创建目录失败：HTTP ${response.code}" }
        }
    }

    private fun requestBuilder(config: WebDavConfig, url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", config.basicAuthHeader())
            .header("User-Agent", "CurSimple")
    }

    private fun parseBackupFiles(xml: String, backupDir: String): List<WebDavBackupFile> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val responses = document.getElementsByTagNameNS("*", "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as? Element ?: return@mapNotNull null
            val href = response.firstText("href") ?: return@mapNotNull null
            val absoluteHref = href.toAbsoluteAgainst(backupDir)
            val name = absoluteHref.fileNameFromUrl()
            if (!name.endsWith(BACKUP_EXTENSION, ignoreCase = true)) return@mapNotNull null
            val contentLength = response.firstText("getcontentlength")?.toLongOrNull() ?: 0L
            WebDavBackupFile(
                name = name,
                href = absoluteHref,
                size = contentLength,
                lastModified = response.firstText("getlastmodified"),
            )
        }.distinctBy { it.href }
            .sortedByDescending { it.name }
    }

    private fun WebDavConfig.basicAuthHeader(): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun String.directoryUrl(): String = trim().ifBlank {
        error("WebDAV URL 不能为空")
    }.let { value ->
        if (value.endsWith("/")) value else "$value/"
    }

    private fun String.resolveChild(child: String): String = directoryUrl() + child.trim('/').encodePathSegment() + "/"

    private fun String.toAbsoluteAgainst(baseUrl: String): String {
        return runCatching { URI(baseUrl).resolve(this).toString() }.getOrDefault(this)
    }

    private fun String.fileNameFromUrl(): String {
        val path = runCatching { URI(this).path }.getOrNull() ?: this
        return URLDecoder.decode(path.substringAfterLast('/'), "UTF-8")
    }

    private fun String.encodePathSegment(): String = replace(" ", "%20")

    private fun Element.firstText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val APP_DIR = "CurSimple"
        const val BACKUP_DIR = "backups"
        const val BACKUP_EXTENSION = ".csv1"
        val BACKUP_MEDIA_TYPE = "application/vnd.cursimple.schedule-backup".toMediaType()
        const val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname />
                    <d:getcontentlength />
                    <d:getlastmodified />
                    <d:resourcetype />
                </d:prop>
            </d:propfind>
        """
    }
}
