package com.x500x.cursimple.app.update

import android.content.Context
import android.os.Build
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.download.DownloadCandidate
import com.x500x.cursimple.app.download.DownloadMirrorPool
import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

class AppUpdateChecker(
    private val repository: String = "cursimple/cursimple-app",
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val downloader: MirrorDownloader = MirrorDownloader(
        mirrorPool = mirrorPool,
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
    ),
) {
    suspend fun check(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val releaseUrl = "https://api.github.com/repos/$repository/releases/latest"
            val releaseResponse = fastestText(
                urls = mirrorPool.candidates(
                    DownloadRequest(
                        purpose = DownloadPurpose.GithubRelease,
                        url = releaseUrl,
                    ),
                ).map { UrlCandidate(it.sourceName, it.url) },
                accept = "application/vnd.github+json",
                successfulOnly = false,
            )
            if (releaseResponse == null || releaseResponse.statusCode == 404) {
                return@withContext AppUpdateCheckResult.NoRelease
            }
            if (releaseResponse.statusCode !in 200..299) {
                return@withContext AppUpdateCheckResult.Failure("检查更新失败：HTTP ${releaseResponse.statusCode}")
            }

            val release = JSONObject(releaseResponse.body)
            val tagName = release.optString("tag_name")
            val htmlUrl = release.optString("html_url")
            val releaseBody = release.optString("body")
            val assets = release.optJSONArray("assets")
            val manifestUrl = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == UPDATE_MANIFEST_NAME }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext AppUpdateCheckResult.ManifestMissing

            val manifest = JSONObject(downloadUpdateManifest(manifestUrl, tagName))
            val remoteVersionCode = manifest.optInt("versionCode", -1)
            val remoteVersionName = manifest.optString("versionName")
            val manifestTag = manifest.optString("tagName", tagName)
            val releaseNotes = parseReleaseNotes(manifest, releaseBody)
            if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
                return@withContext AppUpdateCheckResult.UpToDate
            }
            val apkAsset = selectAsset(manifest)
                ?: return@withContext AppUpdateCheckResult.Failure("更新清单没有匹配当前设备的 APK")
            val candidates = probeDownloadCandidates(apkAsset.downloadUrl)
            AppUpdateCheckResult.Available(
                AppUpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    tagName = manifestTag,
                    releaseUrl = htmlUrl,
                    releaseNotes = releaseNotes,
                    asset = apkAsset,
                    candidates = candidates,
                ),
            )
        }.getOrElse { error ->
            AppUpdateCheckResult.Failure(error.message ?: "检查更新失败")
        }
    }

    suspend fun download(context: Context, info: AppUpdateInfo): AppUpdateDownloadResult = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        val target = File(updateDir, info.asset.fileName)
        val result = downloader.downloadFile(
            request = DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = info.asset.downloadUrl,
            ),
            target = target,
        ) { file ->
            val actual = sha256(file)
            require(actual.equals(info.asset.sha256, ignoreCase = true)) { "校验失败" }
        }
        when (result) {
            is MirrorDownloadResult.Success -> AppUpdateDownloadResult.Success(target, result.candidate.sourceName)
            is MirrorDownloadResult.Failure -> {
                runCatching { target.delete() }
                AppUpdateDownloadResult.Failure(result.message)
            }
        }
    }

    private fun selectAsset(manifest: JSONObject): AppUpdateAsset? {
        val assets = manifest.optJSONArray("assets") ?: return null
        val parsed = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .mapNotNull { json ->
                val fileName = json.optString("fileName").ifBlank { json.optString("name") }
                val abi = json.optString("abi")
                val sha256 = json.optString("sha256")
                val downloadUrl = json.optString("downloadUrl")
                if (fileName.isBlank() || abi.isBlank() || sha256.isBlank() || downloadUrl.isBlank()) {
                    null
                } else {
                    AppUpdateAsset(
                        abi = abi,
                        fileName = fileName,
                        sha256 = sha256,
                        downloadUrl = downloadUrl,
                    )
                }
            }
        val supported = Build.SUPPORTED_ABIS.toList()
        return supported.firstNotNullOfOrNull { abi -> parsed.firstOrNull { it.abi == abi } }
            ?: parsed.firstOrNull { it.abi == "universal" }
            ?: parsed.firstOrNull()
    }

    private fun parseReleaseNotes(manifest: JSONObject, releaseBody: String): String {
        val textFields = listOf("releaseNotes", "changelog", "changeLog", "notes")
        textFields.firstNotNullOfOrNull { key ->
            manifest.optString(key).trim().takeIf { it.isNotBlank() }
        }?.let { return it }

        val changes = manifest.opt("changes")
        val changesText = when (changes) {
            is org.json.JSONArray -> (0 until changes.length())
                .mapNotNull { changes.optString(it).trim().takeIf(String::isNotBlank) }
                .joinToString(separator = "\n") { "- $it" }
            is String -> changes.trim()
            else -> ""
        }
        if (changesText.isNotBlank()) return changesText

        return releaseBody.trim()
    }

    private suspend fun downloadUpdateManifest(manifestUrl: String, tagName: String): String {
        val failures = mutableListOf<String>()
        for (request in updateManifestRequests(manifestUrl, tagName)) {
            when (val result = downloader.downloadText(
                request = request,
                accept = "application/json",
                validate = { JSONObject(it) },
            )) {
                is MirrorDownloadResult.Success -> return result.value
                is MirrorDownloadResult.Failure -> failures += result.message
            }
        }
        throw IllegalStateException(failures.firstOrNull() ?: "无法下载更新清单")
    }

    private fun updateManifestRequests(manifestUrl: String, tagName: String): List<DownloadRequest> {
        val requests = mutableListOf(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = manifestUrl,
            ),
        )
        if (tagName.isNotBlank()) {
            requests += DownloadRequest(
                purpose = DownloadPurpose.GithubRepoFile,
                url = "https://raw.githubusercontent.com/$repository/$tagName/$UPDATE_MANIFEST_NAME",
                repository = repository,
                ref = tagName,
                path = UPDATE_MANIFEST_NAME,
            )
        }
        return requests.distinctBy { "${it.purpose}:${it.url}:${it.repository}:${it.ref}:${it.path}" }
    }

    private suspend fun probeDownloadCandidates(downloadUrl: String): List<AppUpdateDownloadCandidate> = coroutineScope {
        mirrorPool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = downloadUrl,
            ),
        )
            .map { candidate ->
                async {
                    val latency = runCatching {
                        measureTimeMillis { probeDownload(candidate.url) }
                    }.getOrNull()
                    AppUpdateDownloadCandidate(
                        sourceName = candidate.sourceName,
                        url = candidate.url,
                        latencyMillis = latency,
                    )
                }
            }
            .awaitAll()
            .sortedWith(
                compareBy<AppUpdateDownloadCandidate> { it.latencyMillis ?: Long.MAX_VALUE }
                    .thenBy { it.sourceName },
            )
    }

    private suspend fun fastestText(
        urls: List<UrlCandidate>,
        accept: String,
        successfulOnly: Boolean,
    ): TextResponse? = coroutineScope {
        val responses = urls
            .map { candidate ->
                async {
                    runCatching {
                        var response: TextResponse? = null
                        val latency = measureTimeMillis {
                            response = requestText(candidate, accept)
                        }
                        response?.copy(latencyMillis = latency)
                    }.getOrNull()
                }
            }
            .awaitAll()
            .filterNotNull()
            .filter { !successfulOnly || it.statusCode in 200..299 }
        val preferred = if (successfulOnly) {
            responses
        } else {
            val sourceNotFound = responses.firstOrNull {
                it.sourceName == SOURCE_NAME_GITHUB && it.statusCode == 404
            }
            if (sourceNotFound != null) {
                listOf(sourceNotFound)
            } else {
                responses.filter { it.statusCode in 200..299 || it.statusCode == 404 }
            }
                .ifEmpty { responses }
        }
        preferred.minByOrNull { it.latencyMillis }
    }

    private fun requestText(candidate: UrlCandidate, accept: String): TextResponse {
        val connection = (URL(candidate.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
        return connection.use { conn ->
            val status = conn.responseCode
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            TextResponse(
                sourceName = candidate.name,
                statusCode = status,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
                latencyMillis = Long.MAX_VALUE,
            )
        }
    }

    private fun probeDownload(url: String) {
        val headStatus = runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MILLIS
                readTimeout = NETWORK_TIMEOUT_MILLIS
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.use { conn -> conn.responseCode }
        }.getOrNull()
        if (headStatus != null && headStatus in 200..399) return

        val getStatus = runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MILLIS
                readTimeout = NETWORK_TIMEOUT_MILLIS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Range", "bytes=0-0")
            }
            connection.use { conn -> conn.responseCode }
        }.getOrElse { error ->
            throw IllegalStateException(error.message ?: "测速失败")
        }
        check(getStatus in 200..399) { "HTTP $getStatus" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class UrlCandidate(val name: String, val url: String)
    private data class TextResponse(
        val sourceName: String,
        val statusCode: Int,
        val body: String,
        val latencyMillis: Long,
    )

    private fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val UPDATE_MANIFEST_NAME = "update.json"
        const val SOURCE_NAME_GITHUB = "GitHub 源站"
        val USER_AGENT = "CurSimple/${BuildConfig.VERSION_NAME}"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
    }
}
