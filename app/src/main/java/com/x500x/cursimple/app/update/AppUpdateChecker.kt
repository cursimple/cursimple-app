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
     * 检查更新。
     *
     * 先读仓库 update-feed 分支上的版本清单（[checkFromFeed]）：走国内的 jsDelivr 镜像，
     * 一次请求就知道最新版，通常零点几秒。读不到才退回老路子——查 GitHub API 再下 update.json，
     * API 在国内常常要十几秒甚至超时，这是以前检查更新奇慢的主要原因。
     */
    suspend fun check(includePrerelease: Boolean = false): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        checkFromFeed(includePrerelease)?.let { return@withContext it }
        checkFromReleaseApi(includePrerelease)
    }

    /**
     * 从版本清单判断有没有新版；清单一份都取不到时返回 null，交给 [checkFromReleaseApi]。
     *
     * 清单由发版流程写到 update-feed 分支：beta.json 是含测试版在内的最新版，stable.json 只算正式版，
     * 内容就是那一版的 update.json。
     */
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
        // 发布说明只在真有新版（或要回退）时才去读：大多数检查的结果是「已是最新」，没必要多跑一趟
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
            // 预发布不会出现在 latest 接口里，开启测试版更新时改取列表自行挑选
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

    /** 拿到 update.json 之后的判断：比版本号、挑适合本机的安装包，两条路共用。 */
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
            // 读不到版本号就无从比较，不能当作已是最新
            updateManifestVersionProblem(remoteVersionCode, remoteVersionName)?.let {
                return AppUpdateCheckResult.Failure(it)
            }
            // 关掉测试版后本地还留着预发布版，此时线上正式版版本号更低，作为回退目标返回
            val rollback = !includePrerelease &&
                isPrereleaseVersionName(BuildConfig.VERSION_NAME) &&
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
            // 以前这里还要把十几个下载镜像挨个测一遍速，结果却没人用（下载时会自己再测），
            // 白白让「检查更新」多等十几秒，已去掉
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
     * 并发向所有镜像要版本清单，第一份有效的到了之后再等 [FEED_SETTLE_MILLIS]，取版本号最大的那份。
     *
     * 不直接用最快的那份：镜像各自缓存，某一家可能还在给发版前的旧清单，取最快的就会以为没有更新。
     * 慢的请求不等它们结束（放在 [detachedScope] 里自己超时收尾），一家卡死不会拖慢整次检查。
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
        // 按几分钟取整挂一个参数击穿 CDN 缓存，同一时段内的请求仍能共用缓存
        val bucket = System.currentTimeMillis() / FEED_CACHE_BUCKET_MILLIS
        val candidates = mirrorPool.candidates(request).map { candidate ->
            // 把目标地址整个放进查询串的代理（down.npee.cn 那种）再拼参数会把地址拼坏，这类不挂
            if ("/?http" in candidate.url) return@map candidate
            val separator = if ('?' in candidate.url) '&' else '?'
            candidate.copy(url = "${candidate.url}${separator}ts=$bucket")
        }
        if (candidates.isEmpty()) return null
        // 失败的镜像回 [UpdateFeed.Miss] 而不是 null：下面等其余镜像时 withTimeoutOrNull 超时也给 null，
        // 两者混在一起的话，一家镜像失败就会被当成「等够了」，只拿第一份（可能是旧缓存）就收工
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
        // 有一家给出了清单就按清单算：「暂无正式版」可能是发正式版之前的旧缓存
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
            // 镜像出错时常回一张 200 的网页，得是像样的清单才算数
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@use null
            if (json.optBoolean("noRelease", false)) return@use UpdateFeed.NoRelease
            json.takeIf {
                it.optInt("versionCode", -1) > 0 &&
                    it.optString("tagName").isNotBlank() &&
                    hasOnlyOwnReleaseAssets(it)
            }?.let(UpdateFeed::Manifest)
        }
    }

    /**
     * 清单里的安装包必须都指向本仓库的 Release。
     *
     * 各家镜像给的清单取版本号最大的那份，要是有一家给了份假清单（版本号很大、下载地址指向别处），
     * 就会被它抢走；系统装包时会校验签名，假包装不上，但弹出一个假更新也够烦人的，这里先挡掉。
     */
    private fun hasOnlyOwnReleaseAssets(manifest: JSONObject): Boolean {
        val assets = manifest.optJSONArray("assets") ?: return false
        if (assets.length() == 0) return false
        val prefix = "https://github.com/$repository/releases/download/"
        return (0 until assets.length()).all { index ->
            assets.optJSONObject(index)?.optString("downloadUrl")?.startsWith(prefix) == true
        }
    }

    /**
     * 从仓库里读某一版的发布说明（docs/release-notes/<tag>.md），走 jsDelivr 等 CDN。
     * 标签对应的文件不会再变，CDN 缓存正好；取不到返回 null。
     */
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
            // 发布说明以「# v…」开头；镜像回的错误页多半是 HTML，挡掉
            validate = { require(it.trimStart().startsWith("#")) },
        )
        return (result as? MirrorDownloadResult.Success)?.value?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * 取版本历史。
     *
     * [includePrerelease] 为真时列出测试版（含正式版），为假只列正式版——
     * 和「接收测试版更新」那个开关同义：关着的人不该在历史里看到 beta。
     */
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

    /** 取某个 tag 的发布说明，用于安装完成后展示本次更新内容。 */
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

    /** [onProgress] 报告已下载与总字节数，总数未知时为 -1；换镜像重下会从 0 重新报。 */
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
     * 记住的镜像先单独试一次：成功即返回，整次只有一个请求。
     * 只有从来没有记录或记住的镜像失效时，才并发请求全部镜像。
     * 代理源的 404 不具备权威性，快路径只认成功响应与源站的 404，其余落回全量竞速。
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
     * 并发请求全部镜像，有一家给出可用的结果就立刻用它，不再等其余的。
     *
     * 以前是等所有镜像都返回再挑最快的，于是一家卡死的镜像能把检查拖到超时。
     * 一家都没成功时，再按全部结果判断是「没有发布」还是「都连不上」。
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

    /** 版本清单的内容：某一版的 update.json，或者发版流程写下的「暂无正式版」标记。 */
    private sealed interface UpdateFeed {
        data class Manifest(val json: JSONObject) : UpdateFeed

        data object NoRelease : UpdateFeed

        /** 这家镜像没给出能用的清单（连不上、错误页、内容不对）。 */
        data object Miss : UpdateFeed
    }

    /** 不跟着检查一起结束的慢请求放这里，让它们自己超时收尾，见 [fetchUpdateFeed]。 */
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** 版本清单所在的分支与文件，由发版流程维护。 */
        private const val FEED_BRANCH = "update-feed"
        private const val FEED_BETA_FILE = "beta.json"
        private const val FEED_STABLE_FILE = "stable.json"
        private const val FEED_CACHE_BUCKET_MILLIS = 5 * 60 * 1000L
        private const val FEED_SETTLE_MILLIS = 600L
        private const val FEED_TOTAL_TIMEOUT_MILLIS = 8_000L
        private const val FEED_CONNECT_TIMEOUT_MILLIS = 4_000
        private const val FEED_READ_TIMEOUT_MILLIS = 5_000

        /** 发布所在的仓库。 */
        const val DEFAULT_REPOSITORY = "cursimple/cursimple-app"

        /** 某个标签的发布页地址。 */
        fun releasePageUrl(tagName: String): String =
            "https://github.com/$DEFAULT_REPOSITORY/releases/tag/$tagName"

        const val UPDATE_MANIFEST_NAME = "update.json"
        private const val RELEASE_PAGE_SIZE = 20
        private const val GITHUB_API_ACCEPT = "application/vnd.github+json"
        val USER_AGENT = "CurSimple/${BuildConfig.VERSION_NAME}"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
    }
}
