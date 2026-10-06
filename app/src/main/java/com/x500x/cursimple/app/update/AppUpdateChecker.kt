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
import com.x500x.cursimple.app.download.MirrorPreferenceStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateChecker(
    downloaderLabels: MirrorDownloaderLabels,
    private val repository: String = DEFAULT_REPOSITORY,
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val mirrorStore: MirrorPreferenceStore? = null,
    private val downloader: MirrorDownloader = MirrorDownloader(
        labels = downloaderLabels,
        mirrorPool = mirrorPool,
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
        preferenceStore = mirrorStore,
    ),
) {
    /**
     * Read CDN-accessible update feeds first; fall back to GitHub release metadata when
     * unavailable.
     */
    suspend fun check(includePrerelease: Boolean = false): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        checkFromFeed(includePrerelease)?.let { return@withContext it }
        checkFromReleaseApi(includePrerelease)
    }

    /** Read the selected stable or beta feed; null requests release-API fallback. */
    private suspend fun checkFromFeed(includePrerelease: Boolean): AppUpdateCheckResult? {
        val manifest = when (val feed = fetchUpdateFeed(includePrerelease)) {
            null, UpdateFeed.Miss -> return null
            UpdateFeed.NoRelease -> return AppUpdateCheckResult.NoRelease
            is UpdateFeed.Manifest -> feed.json
        }
        val tagName = manifest.optString("tagName")
        val result = evaluateManifest(
            manifest = manifest,
            tagName = tagName,
            releaseUrl = releasePageUrl(tagName),
            releaseBody = "",
            includePrerelease = includePrerelease,
        )
        // Fetch release notes only for available updates or downgrade candidates.
        val info = when (result) {
            is AppUpdateCheckResult.Available -> result.info
            is AppUpdateCheckResult.Rollback -> result.info
            else -> return result
        }
        val notes = info.releaseNotes.ifBlank { releaseNotesFromRepo(tagName).orEmpty() }
        val withNotes = info.copy(releaseNotes = notes)
        return if (result is AppUpdateCheckResult.Rollback) {
            AppUpdateCheckResult.Rollback(withNotes)
        } else {
            AppUpdateCheckResult.Available(withNotes)
        }
    }

    private suspend fun checkFromReleaseApi(includePrerelease: Boolean): AppUpdateCheckResult =
        runCatching {
            val releaseUrl = if (includePrerelease) {
                "https://api.github.com/repos/$repository/releases?per_page=$RELEASE_PAGE_SIZE"
            } else {
                "https://api.github.com/repos/$repository/releases/latest"
            }
            val accepts: (String) -> Boolean =
                if (includePrerelease) ::isJsonArrayBody else ::isJsonObjectBody
            val selection = selectReleaseSource(releaseUrl, accepts)
            val releaseResponse = when (selection) {
                is UpdateSourceSelection.Success -> selection.response
                UpdateSourceSelection.NotFound -> return@runCatching AppUpdateCheckResult.NoRelease
                is UpdateSourceSelection.HttpError,
                is UpdateSourceSelection.UnusableBody,
                is UpdateSourceSelection.Unreachable,
                -> return@runCatching AppUpdateCheckResult.Failure(
                    updateSourceFailureMessage(selection) ?: UpdateStatusReason.CheckRetry,
                )
            }

            val release = if (includePrerelease) {
                pickReleaseFromList(releaseResponse.body)
                    ?: return@runCatching AppUpdateCheckResult.NoRelease
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
                ?: return@runCatching AppUpdateCheckResult.ManifestMissing

            val manifest = JSONObject(downloadUpdateManifest(manifestUrl, tagName))
            evaluateManifest(manifest, tagName, htmlUrl, releaseBody, includePrerelease)
        }.getOrElse { error ->
            AppUpdateCheckResult.Failure(UpdateStatusReason.CheckError(describeUpdateError(error)))
        }

    private fun evaluateManifest(
        manifest: JSONObject,
        tagName: String,
        releaseUrl: String,
        releaseBody: String,
        includePrerelease: Boolean,
    ): AppUpdateCheckResult {
        run {
            val remoteVersionCode = manifest.optInt("versionCode", -1)
            val remoteVersionName = manifest.optString("versionName")
            val manifestTag = manifest.optString("tagName", tagName)
            val releaseNotes = parseReleaseNotes(manifest, releaseBody)
            // An unreadable version cannot establish that the app is current.
            updateManifestVersionProblem(remoteVersionCode, remoteVersionName)?.let {
                return AppUpdateCheckResult.Failure(it)
            }
            val rollback = !includePrerelease &&
                isPrereleaseBuild(BuildConfig.VERSION_NAME, BuildConfig.RELEASE_CHANNEL) &&
                remoteVersionCode < BuildConfig.VERSION_CODE
            if (remoteVersionCode <= BuildConfig.VERSION_CODE && !rollback) {
                return AppUpdateCheckResult.UpToDate
            }
            val assetSelection = UpdateAssetSelector.select(
                assets = parseAssets(manifest),
                deviceAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            )
            val apkAsset = when (assetSelection) {
                is UpdateAssetSelection.Matched -> assetSelection.asset
                UpdateAssetSelection.NoAsset,
                is UpdateAssetSelection.NoCompatibleAbi,
                -> return AppUpdateCheckResult.Failure(
                    updateAssetFailureMessage(assetSelection) ?: UpdateStatusReason.AssetNoMatch,
                )
            }
            val info = AppUpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                tagName = manifestTag,
                releaseUrl = releaseUrl,
                releaseNotes = releaseNotes,
                asset = apkAsset,
                candidates = emptyList(),
            )
            return if (rollback) AppUpdateCheckResult.Rollback(info) else AppUpdateCheckResult.Available(info)
        }
    }

    /**
     * After the first valid feed, allow a bounded settling interval and choose the highest
     * version. Slow requests finish independently without delaying the check.
     */
    private suspend fun fetchUpdateFeed(includePrerelease: Boolean): UpdateFeed? {
        val file = if (includePrerelease) FEED_BETA_FILE else FEED_STABLE_FILE
        val request = DownloadRequest(
            purpose = DownloadPurpose.GithubRepoFile,
            url = "https://raw.githubusercontent.com/$repository/$FEED_BRANCH/$file",
            repository = repository,
            ref = FEED_BRANCH,
            path = file,
        )
        val bucket = System.currentTimeMillis() / FEED_CACHE_BUCKET_MILLIS
        val candidates = mirrorPool.candidates(request).map { candidate ->
            // Avoid appending cache parameters to proxies whose query already contains the full destination URL.
            if ("/?http" in candidate.url) return@map candidate
            val separator = if ('?' in candidate.url) '&' else '?'
            candidate.copy(url = "${candidate.url}${separator}ts=$bucket")
        }
        if (candidates.isEmpty()) return null
        // Use [UpdateFeed.Miss] for request failure; null is reserved for settling timeout.
        val results = Channel<UpdateFeed>(Channel.UNLIMITED)
        candidates.forEach { candidate ->
            detachedScope.launch {
                results.trySend(runCatching { requestFeed(candidate) }.getOrNull() ?: UpdateFeed.Miss)
            }
        }
        val found = mutableListOf<UpdateFeed>()
        withTimeoutOrNull(FEED_TOTAL_TIMEOUT_MILLIS) {
            var received = 0
            var settleUntil = Long.MAX_VALUE
            while (received < candidates.size) {
                val waitFor = settleUntil - System.currentTimeMillis()
                if (waitFor <= 0) break
                val next = if (settleUntil == Long.MAX_VALUE) {
                    results.receive()
                } else {
                    withTimeoutOrNull(waitFor) { results.receive() } ?: break
                }
                received++
                if (next != UpdateFeed.Miss) {
                    found += next
                    if (settleUntil == Long.MAX_VALUE) settleUntil = System.currentTimeMillis() + FEED_SETTLE_MILLIS
                }
            }
        }
        val manifests = found.filterIsInstance<UpdateFeed.Manifest>()
        return manifests.maxByOrNull { it.json.optInt("versionCode", -1) }
            ?: found.firstOrNull { it == UpdateFeed.NoRelease }
    }

    private fun requestFeed(candidate: DownloadCandidate): UpdateFeed? {
        val connection = (URL(candidate.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = FEED_CONNECT_TIMEOUT_MILLIS
            readTimeout = FEED_READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return connection.use { conn ->
            if (conn.responseCode != 200) return@use null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@use null
            if (json.optBoolean("noRelease", false)) return@use UpdateFeed.NoRelease
            json.takeIf {
                it.optInt("versionCode", -1) > 0 &&
                    it.optString("tagName").isNotBlank() &&
                    hasOnlyOwnReleaseAssets(it)
            }?.let(UpdateFeed::Manifest)
        }
    }

    /** Reject feed assets outside this repository's Releases before presenting an update. */
    private fun hasOnlyOwnReleaseAssets(manifest: JSONObject): Boolean {
        val assets = manifest.optJSONArray("assets") ?: return false
        if (assets.length() == 0) return false
        val prefix = "https://github.com/$repository/releases/download/"
        return (0 until assets.length()).all { index ->
            assets.optJSONObject(index)?.optString("downloadUrl")?.startsWith(prefix) == true
        }
    }

    /** Fetch immutable tag-specific notes through repository CDNs; return null on failure. */
    private suspend fun releaseNotesFromRepo(tagName: String): String? {
        if (tagName.isBlank()) return null
        val path = "docs/release-notes/$tagName.md"
        val result = downloader.downloadText(
            request = DownloadRequest(
                purpose = DownloadPurpose.GithubRepoFile,
                url = "https://raw.githubusercontent.com/$repository/$tagName/$path",
                repository = repository,
                ref = tagName,
                path = path,
            ),
            accept = "text/plain",
            validate = { require(it.trimStart().startsWith("#")) },
        )
        return (result as? MirrorDownloadResult.Success)?.value?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Release history follows [includePrerelease], matching the user's update channel. */
    suspend fun history(includePrerelease: Boolean = false): List<AppReleaseSummary>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val selection = selectReleaseSource(
                    "https://api.github.com/repos/$repository/releases?per_page=$RELEASE_PAGE_SIZE",
                    ::isJsonArrayBody,
                )
                val response = (selection as? UpdateSourceSelection.Success)
                    ?.response ?: return@withContext null
                parseReleaseHistory(response.body, includePrerelease)
            }.getOrNull()
        }

    suspend fun releaseNotes(tagName: String): String? = withContext(Dispatchers.IO) {
        releaseNotesFromRepo(tagName)?.let { return@withContext it }
        runCatching {
            val selection = selectReleaseSource(
                "https://api.github.com/repos/$repository/releases/tags/$tagName",
                ::isJsonObjectBody,
            )
            val response = (selection as? UpdateSourceSelection.Success)
                ?.response ?: return@withContext null
            JSONObject(response.body).optString("body").trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Unknown totals are -1; mirror retries restart [onProgress] at zero. */
    suspend fun download(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): AppUpdateDownloadResult = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        val target = File(updateDir, info.asset.fileName)
        val result = downloader.downloadFile(
            request = DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = info.asset.downloadUrl,
            ),
            target = target,
            validate = { file ->
                val actual = sha256(file)
                if (!actual.equals(info.asset.sha256, ignoreCase = true)) {
                    throw UpdateException(UpdateErrorReason.ChecksumFailed)
                }
            },
            onProgress = onProgress,
        )
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

    /**
     * Try the preferred API route first. Only origin 404 is authoritative; proxy errors trigger
     * the full race.
     */
    private suspend fun selectReleaseSource(
        releaseUrl: String,
        accepts: (String) -> Boolean,
    ): UpdateSourceSelection {
        val request = DownloadRequest(purpose = DownloadPurpose.GithubRelease, url = releaseUrl)
        val key = MirrorPreferenceStore.cacheKeyOf(request)
        val candidates = mirrorPool.candidates(request)
        val preferred = mirrorStore?.preferred(key)
            ?.let { name -> candidates.firstOrNull { it.sourceName == name } }
        if (preferred != null) {
            val fastPath = UpdateSourceSelector.select(
                requestAllSources(listOf(preferred), accept = GITHUB_API_ACCEPT),
                accepts,
            )
            val terminal = fastPath is UpdateSourceSelection.Success ||
                (fastPath is UpdateSourceSelection.NotFound &&
                    preferred.sourceName == UpdateSourceSelector.AUTHORITATIVE_SOURCE_NAME)
            if (terminal) return fastPath
            mirrorStore?.recordFailure(key, preferred.sourceName)
        }
        val rest = candidates.filterNot { it.sourceName == preferred?.sourceName }
        val selection = raceSources(rest, accepts)
        if (selection is UpdateSourceSelection.Success) {
            mirrorStore?.recordSuccess(key, selection.response.sourceName)
        }
        return selection
    }

    /**
     * Accept the first usable response and cancel losers; combine failures only if every source
     * fails.
     */
    private suspend fun raceSources(
        candidates: List<DownloadCandidate>,
        accepts: (String) -> Boolean,
    ): UpdateSourceSelection {
        if (candidates.isEmpty()) return UpdateSourceSelector.select(emptyList(), accepts)
        val results = Channel<UpdateSourceAttempt>(Channel.UNLIMITED)
        candidates.forEach { candidate ->
            detachedScope.launch {
                val startedAt = System.nanoTime()
                val attempt = runCatching {
                    val response = requestText(candidate, GITHUB_API_ACCEPT)
                    val latency = (System.nanoTime() - startedAt) / 1_000_000L
                    UpdateSourceAttempt(sourceName = candidate.sourceName, response = response.copy(latencyMillis = latency))
                }.getOrElse { error ->
                    UpdateSourceAttempt(sourceName = candidate.sourceName, errorReason = describeUpdateError(error))
                }
                results.trySend(attempt)
            }
        }
        val attempts = mutableListOf<UpdateSourceAttempt>()
        repeat(candidates.size) {
            val attempt = results.receive()
            attempts += attempt
            val single = UpdateSourceSelector.select(listOf(attempt), accepts)
            if (single is UpdateSourceSelection.Success) return single
        }
        return UpdateSourceSelector.select(attempts, accepts)
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

    private fun isJsonObjectBody(body: String): Boolean =
        runCatching { JSONObject(body) }.isSuccess

    private fun isJsonArrayBody(body: String): Boolean =
        runCatching { JSONArray(body) }.isSuccess

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

    private sealed interface UpdateFeed {
        data class Manifest(val json: JSONObject) : UpdateFeed

        data object NoRelease : UpdateFeed

        data object Miss : UpdateFeed
    }

    /** Scope for slow feed requests that finish independently of [fetchUpdateFeed]. */
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val FEED_BRANCH = "update-feed"
        private const val FEED_BETA_FILE = "beta.json"
        private const val FEED_STABLE_FILE = "stable.json"
        private const val FEED_CACHE_BUCKET_MILLIS = 5 * 60 * 1000L
        private const val FEED_SETTLE_MILLIS = 600L
        private const val FEED_TOTAL_TIMEOUT_MILLIS = 8_000L
        private const val FEED_CONNECT_TIMEOUT_MILLIS = 4_000
        private const val FEED_READ_TIMEOUT_MILLIS = 5_000

        const val DEFAULT_REPOSITORY = "cursimple/cursimple-app"

        fun releasePageUrl(tagName: String): String =
            "https://github.com/$DEFAULT_REPOSITORY/releases/tag/$tagName"

        const val UPDATE_MANIFEST_NAME = "update.json"
        private const val RELEASE_PAGE_SIZE = 20
        private const val GITHUB_API_ACCEPT = "application/vnd.github+json"
        val USER_AGENT = "CurSimple/${BuildConfig.VERSION_NAME}"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
    }
}
