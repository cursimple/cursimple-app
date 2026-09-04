package com.x500x.cursimple.app.update

import android.content.Context
import android.os.Build
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.download.DownloadCandidate
import com.x500x.cursimple.app.download.DownloadFailureReason
import com.x500x.cursimple.app.download.DownloadMirrorPool
import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.MirrorDownloaderLabels
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

class AppUpdateChecker(
    downloaderLabels: MirrorDownloaderLabels,
    private val repository: String = "cursimple/cursimple-app",
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val downloader: MirrorDownloader = MirrorDownloader(
        labels = downloaderLabels,
        mirrorPool = mirrorPool,
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
    ),
) {
    suspend fun check(includePrerelease: Boolean = false): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            // 预发布不会出现在 latest 接口里，开启测试版更新时改取列表自行挑选
            val releaseUrl = if (includePrerelease) {
                "https://api.github.com/repos/$repository/releases?per_page=$RELEASE_PAGE_SIZE"
            } else {
                "https://api.github.com/repos/$repository/releases/latest"
            }
            val attempts = requestAllSources(
                candidates = mirrorPool.candidates(
                    DownloadRequest(
                        purpose = DownloadPurpose.GithubRelease,
                        url = releaseUrl,
                    ),
                ),
                accept = "application/vnd.github+json",
            )
            val accepts: (String) -> Boolean =
                if (includePrerelease) ::isJsonArrayBody else ::isJsonObjectBody
            val selection = UpdateSourceSelector.select(attempts, accepts)
            val releaseResponse = when (selection) {
                is UpdateSourceSelection.Success -> selection.response
                UpdateSourceSelection.NotFound -> return@withContext AppUpdateCheckResult.NoRelease
                is UpdateSourceSelection.HttpError,
                is UpdateSourceSelection.UnusableBody,
                is UpdateSourceSelection.Unreachable,
                -> return@withContext AppUpdateCheckResult.Failure(
                    updateSourceFailureMessage(selection) ?: UpdateStatusReason.CheckRetry,
                )
            }

            val release = if (includePrerelease) {
                pickReleaseFromList(releaseResponse.body)
                    ?: return@withContext AppUpdateCheckResult.NoRelease
            } else {
                JSONObject(releaseResponse.body)
            }
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
            // 读不到版本号就无从比较，不能当作已是最新
            updateManifestVersionProblem(remoteVersionCode, remoteVersionName)?.let {
                return@withContext AppUpdateCheckResult.Failure(it)
            }
            // 关掉测试版后本地还留着预发布版，此时线上正式版版本号更低，作为回退目标返回
            val rollback = !includePrerelease &&
                isPrereleaseVersionName(BuildConfig.VERSION_NAME) &&
                remoteVersionCode < BuildConfig.VERSION_CODE
            if (remoteVersionCode <= BuildConfig.VERSION_CODE && !rollback) {
                return@withContext AppUpdateCheckResult.UpToDate
            }
            val assetSelection = UpdateAssetSelector.select(
                assets = parseAssets(manifest),
                deviceAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            )
            val apkAsset = when (assetSelection) {
                is UpdateAssetSelection.Matched -> assetSelection.asset
                UpdateAssetSelection.NoAsset,
                is UpdateAssetSelection.NoCompatibleAbi,
                -> return@withContext AppUpdateCheckResult.Failure(
                    updateAssetFailureMessage(assetSelection) ?: UpdateStatusReason.AssetNoMatch,
                )
            }
            val candidates = probeDownloadCandidates(apkAsset.downloadUrl)
            val info = AppUpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                tagName = manifestTag,
                releaseUrl = htmlUrl,
                releaseNotes = releaseNotes,
                asset = apkAsset,
                candidates = candidates,
            )
            if (rollback) AppUpdateCheckResult.Rollback(info) else AppUpdateCheckResult.Available(info)
        }.getOrElse { error ->
            AppUpdateCheckResult.Failure(UpdateStatusReason.CheckError(describeUpdateError(error)))
        }
    }

    /** 取某个 tag 的发布说明，用于安装完成后展示本次更新内容。 */
    suspend fun releaseNotes(tagName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val attempts = requestAllSources(
                candidates = mirrorPool.candidates(
                    DownloadRequest(
                        purpose = DownloadPurpose.GithubRelease,
                        url = "https://api.github.com/repos/$repository/releases/tags/$tagName",
                    ),
                ),
                accept = "application/vnd.github+json",
            )
            val response = (UpdateSourceSelector.select(attempts, ::isJsonObjectBody)
                as? UpdateSourceSelection.Success)?.response ?: return@withContext null
            JSONObject(response.body).optString("body").trim().takeIf { it.isNotBlank() }
        }.getOrNull()
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
            if (!actual.equals(info.asset.sha256, ignoreCase = true)) {
                throw UpdateException(UpdateErrorReason.ChecksumFailed)
            }
        }
        when (result) {
            is MirrorDownloadResult.Success -> AppUpdateDownloadResult.Success(target, result.candidate.sourceName)
            is MirrorDownloadResult.Failure -> {
                runCatching { target.delete() }
                AppUpdateDownloadResult.Failure(downloadFailureStatus(result.reason))
            }
        }
    }

    private fun parseAssets(manifest: JSONObject): List<AppUpdateAsset> {
        val assets = manifest.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length())
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
        val reasons = mutableListOf<DownloadFailureReason>()
        for (request in updateManifestRequests(manifestUrl, tagName)) {
            when (val result = downloader.downloadText(
                request = request,
                accept = "application/json",
                validate = { JSONObject(it) },
            )) {
                is MirrorDownloadResult.Success -> return result.value
                is MirrorDownloadResult.Failure -> reasons += result.reason
            }
        }
        val detail = reasons.firstNotNullOfOrNull { reason ->
            when (reason) {
                is DownloadFailureReason.Thrown -> describeUpdateError(reason.error).takeIf {
                    it != UpdateErrorReason.Unknown
                }
                DownloadFailureReason.NoSource -> null
            }
        } ?: UpdateErrorReason.NoSource
        throw UpdateException(UpdateErrorReason.ManifestDownloadFailed(detail))
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

    private suspend fun requestAllSources(
        candidates: List<DownloadCandidate>,
        accept: String,
    ): List<UpdateSourceAttempt> = coroutineScope {
        candidates
            .map { candidate ->
                async {
                    runCatching {
                        val startedAt = System.nanoTime()
                        val response = requestText(candidate, accept)
                        val latency = (System.nanoTime() - startedAt) / 1_000_000L
                        UpdateSourceAttempt(
                            sourceName = candidate.sourceName,
                            response = response.copy(latencyMillis = latency),
                        )
                    }.getOrElse { error ->
                        UpdateSourceAttempt(
                            sourceName = candidate.sourceName,
                            errorReason = describeUpdateError(error),
                        )
                    }
                }
            }
            .awaitAll()
    }

    private fun requestText(candidate: DownloadCandidate, accept: String): UpdateSourceResponse {
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
            UpdateSourceResponse(
                sourceName = candidate.sourceName,
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
            throw UpdateException(UpdateErrorReason.ProbeFailed(describeUpdateError(error)))
        }
        check(getStatus in 200..399) { "HTTP $getStatus" }
    }

    private fun isJsonObjectBody(body: String): Boolean =
        runCatching { JSONObject(body) }.isSuccess

    private fun isJsonArrayBody(body: String): Boolean =
        runCatching { JSONArray(body) }.isSuccess

    /** 从 Release 列表里挑出该更新到哪一个，挑不出时视作没有可用版本。 */
    private fun pickReleaseFromList(body: String): JSONObject? {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val entries = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ReleaseEntry(
                index = index,
                tagName = item.optString("tag_name"),
                draft = item.optBoolean("draft"),
                prerelease = item.optBoolean("prerelease"),
                publishedAt = item.optString("published_at"),
            )
        }
        val picked = pickUpdateRelease(entries, includePrerelease = true) ?: return null
        return array.optJSONObject(picked.index)
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

    private fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val UPDATE_MANIFEST_NAME = "update.json"
        private const val RELEASE_PAGE_SIZE = 20
        val USER_AGENT = "CurSimple/${BuildConfig.VERSION_NAME}"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
    }
}
