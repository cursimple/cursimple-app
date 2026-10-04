package com.x500x.cursimple.core.plugin.market.github

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginCheck
import com.x500x.cursimple.core.plugin.pluginRequire
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.net.URLEncoder
import java.time.Duration

/**
 * plugins-stars.json 注册表中的一条记录，并附带最新 release 的 manifest 信息。
 */
@Serializable
data class GitHubRepoSummary(
    val fullName: String,
    val owner: String,
    val name: String,
    val description: String,
    val stars: Int,
    val avatarUrl: String,
    val htmlUrl: String,
    val language: String? = null,
    val updatedAt: String? = null,
    val ownerHtmlUrl: String,
    val homepageUrl: String? = null,
    val pushedAt: String? = null,
    val isFresh: Boolean,
    val latestRelease: GitHubReleaseAsset? = null,
    /**
     * 这个插件覆盖的学校别名，由注册表声明。
     *
     * 仓库名多半是 `bit-schedule` 这类英文缩写，学生搜的却是「北京理工」；
     * 注册表把中文全称、简称、拼音一并写进来，搜索时按普通子串比对即可命中，
     * 应用侧不做拼音转换，新学校只改注册表、不必发版。
     */
    val schoolAliases: List<String> = emptyList(),
    /** 注册表里写了 `"kind": "extension"` 的是扩展组件，不出现在「从教务系统导课」里 */
    val kind: String = "",
    /** 从哪个来源仓库读到的（`owner/repo`），界面上据此显示「公有仓库」或仓库名。 */
    val registrySource: String = "",
    /** 靠登录的 GitHub 账号才读到的（私有仓库）：版本和安装包都得直连 GitHub API 去拿。 */
    val viaAccount: Boolean = false,
) {
    val displayTitle: String get() = name.ifBlank { fullName }

    val isExtension: Boolean get() = kind == com.x500x.cursimple.core.plugin.manifest.PluginManifest.KIND_EXTENSION
}

@Serializable
private data class PluginStarsPayload(
    @SerialName("repositories") val repositories: List<PluginStarsRepositoryApi> = emptyList(),
)

@Serializable
private data class PluginStarsRepositoryApi(
    @SerialName("name") val fullName: String = "",
    @SerialName("repo") val repo: String = "",
    @SerialName("owner") val owner: String = "",
    @SerialName("avatar") val avatarUrl: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("star") val stars: Int = 0,
    @SerialName("language") val language: String? = null,
    @SerialName("url") val htmlUrl: String = "",
    @SerialName("release") val release: PluginStarsReleaseApi? = null,
    // 两个键名都认：schools 是本意，aliases 留给只想补几个别称的条目
    @SerialName("schools") val schools: List<String> = emptyList(),
    @SerialName("aliases") val aliases: List<String> = emptyList(),
    @SerialName("kind") val kind: String = "",
)

@Serializable
private data class PluginStarsReleaseApi(
    @SerialName("tag") val tag: String = "",
    @SerialName("filename") val filename: String = "",
)

@Serializable
private data class LatestPluginReleaseManifest(
    @SerialName("filename") val filename: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
)

@Serializable
data class GitHubReleaseAsset(
    val tagName: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

class GitHubRegistryRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(15))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val fetchText: suspend (String) -> String = { url -> defaultFetchText(client, url) },
    private val apiClient: GitHubApiClient = GitHubApiClient(json = json),
    /** 登录的 GitHub 令牌；没登录时为 null。只交给 [apiClient]，从不进镜像那条路。 */
    private val tokenProvider: () -> String? = { null },
    private val fetchBytes: (suspend (String) -> ByteArray)? = null,
    private val fetchBytesWithProgress: (suspend (String, (Long, Long) -> Unit) -> ByteArray)? = null,
) {

    private data class AccountSession(val token: String?, val generation: Long)
    private data class ReleaseRequest(val slug: String, val apiOnly: Boolean)
    private class NothingPublished : java.io.IOException("仓库没有可用清单或 manifest.json Release")
    private class CachedRelease(val asset: GitHubReleaseAsset, val atMillis: Long)

    private val accountLock = Any()
    private var accountToken: String? = null
    private var accountGeneration = 0L
    private val accountRepos = HashSet<String>()
    /** 公开清单已公开声明的仓库，查版本无需额外依赖 API 可达性。 */
    private val knownPublicRepos = HashSet<String>()
    private val releaseCache = HashMap<String, CachedRelease>()
    private val inFlight = HashMap<ReleaseRequest, CompletableDeferred<GitHubReleaseAsset?>>()

    private fun token(): String? = tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }

    /** 注销 / 切换账号时调用。旧账号的标记、缓存和等待中的查询一起失效。 */
    fun clearAccountCache() = synchronized(accountLock) {
        resetAccountState()
        accountToken = token()
    }

    private fun resetAccountState() {
        accountGeneration++
        accountRepos.clear()
        knownPublicRepos.clear()
        releaseCache.clear()
        inFlight.values.forEach { it.cancel(CancellationException("GitHub 账号已变更")) }
        inFlight.clear()
    }

    private fun session(): AccountSession = synchronized(accountLock) {
        val currentToken = token()
        if (accountToken != currentToken) {
            resetAccountState()
            accountToken = currentToken
        }
        AccountSession(currentToken, accountGeneration)
    }

    private fun ensureSession(expected: AccountSession) {
        if (session() != expected) throw CancellationException("GitHub 账号已变更")
    }

    private fun usesAccount(slug: String): Boolean = synchronized(accountLock) { slug.lowercase() in accountRepos }

    private fun isKnownPublic(slug: String): Boolean = synchronized(accountLock) {
        DefaultMarketSources.isDefault(slug) || slug.lowercase() in knownPublicRepos
    }

    private fun markPublic(slugs: Collection<String>, expected: AccountSession) = synchronized(accountLock) {
        ensureSession(expected)
        slugs.map { it.lowercase() }.filter { it !in accountRepos }.forEach { knownPublicRepos.add(it) }
    }

    private fun markAccount(slugs: Collection<String>, expected: AccountSession) = synchronized(accountLock) {
        ensureSession(expected)
        slugs.forEach { slug ->
            val key = slug.lowercase()
            knownPublicRepos.remove(key)
            if (accountRepos.add(key)) releaseCache.remove(key)
        }
    }

    private suspend fun <T> accountRequest(expected: AccountSession, block: suspend () -> T): T {
        ensureSession(expected)
        return try {
            block().also { ensureSession(expected) }
        } catch (error: Exception) {
            ensureSession(expected)
            throw error
        }
    }

    /**
     * 读注册表，或把来源当作只有一个插件 / 组件的 Release 仓库。
     * 只有默认公有来源可以直接走镜像；其他来源先在 GitHub API 确认可见性。
     * 账号优先、私有或已经靠账号读取的来源始终走 API，失败不会回退到镜像。
     */
    suspend fun fetchSource(
        source: String,
        kind: MarketSourceKind,
        preferAccount: Boolean = false,
    ): List<GitHubRepoSummary> = fetchSourceInternal(source, kind, preferAccount, kind.dataBranch)

    private suspend fun fetchSourceInternal(
        source: String,
        kind: MarketSourceKind,
        preferAccount: Boolean,
        branch: String,
    ): List<GitHubRepoSummary> = withContext(Dispatchers.IO) {
        val slug = GitHubRepoAddress.parse(source)
        pluginRequire(slug != null, R.string.plugin_error_registry_repo_invalid, source)
        slug!!
        val expected = session()
        val knownAccount = usesAccount(slug)
        var triedPublicRegistry = false

        fun finish(entries: List<GitHubRepoSummary>, viaAccount: Boolean): List<GitHubRepoSummary> {
            ensureSession(expected)
            if (viaAccount) markAccount(listOf(slug) + entries.map { it.fullName }, expected)
            else markPublic(listOf(slug) + entries.map { it.fullName }, expected)
            return entries.map { entry ->
                entry.copy(
                    registrySource = slug,
                    viaAccount = viaAccount,
                    // 私有注册表声明的浏览器下载 URL 不能交给公有下载器。
                    latestRelease = if (viaAccount && !GitHubApiClient.isAssetApiUrl(entry.latestRelease?.downloadUrl.orEmpty())) {
                        null
                    } else entry.latestRelease,
                    kind = entry.kind.ifBlank {
                        if (kind == MarketSourceKind.Component) com.x500x.cursimple.core.plugin.manifest.PluginManifest.KIND_EXTENSION else ""
                    },
                )
            }
        }

        if (DefaultMarketSources.isDefault(slug) && !preferAccount && !knownAccount) {
            triedPublicRegistry = true
            attempt { publicRegistry(slug, branch, kind.dataFile) }?.let { return@withContext finish(it, false) }
        }
        val meta = accountRequest(expected) { apiClient.repo(slug, expected.token) }
        val apiOnly = preferAccount || knownAccount || meta.isPrivate
        if (!apiOnly) {
            if (!triedPublicRegistry) {
                attempt { publicRegistry(slug, branch, kind.dataFile) }?.let { return@withContext finish(it, false) }
            }
            publicReleaseAsset(slug, fresh = false)?.let {
                return@withContext finish(listOf(singleRepoEntry(slug, meta, it)), false)
            }
        }
        val viaAccount = expected.token != null
        if (viaAccount) markAccount(listOf(slug), expected)
        val registry = try {
            parseRegistry(accountRequest(expected) { apiClient.fileText(slug, branch, kind.dataFile, expected.token) })
        } catch (error: GitHubApiClient.HttpError) {
            if (error.code != 404) throw error
            null
        }
        if (registry != null) return@withContext finish(registry, viaAccount)
        val asset = apiReleaseAsset(slug, expected) ?: throw NothingPublished()
        finish(listOf(singleRepoEntry(slug, meta, asset)), viaAccount)
    }

    private suspend fun publicRegistry(slug: String, branch: String, file: String): List<GitHubRepoSummary> {
        val url = "https://raw.githubusercontent.com/$slug/$branch/$file".toHttpUrl().newBuilder()
            .addQueryParameter("ts", cacheBucket().toString()).build()
        return parseRegistry(fetchText(url.toString()))
    }

    private fun singleRepoEntry(
        slug: String,
        meta: GitHubApiClient.ApiRepo,
        asset: GitHubReleaseAsset,
    ): GitHubRepoSummary {
        val owner = slug.substringBefore('/')
        return GitHubRepoSummary(
            fullName = slug,
            owner = owner,
            name = meta.name.ifBlank { slug.substringAfter('/') },
            description = meta.description.orEmpty(),
            stars = meta.stars,
            avatarUrl = meta.owner.avatarUrl.ifBlank { "https://github.com/$owner.png?size=80" },
            htmlUrl = meta.htmlUrl.ifBlank { "https://github.com/$slug" },
            language = meta.language,
            ownerHtmlUrl = "https://github.com/$owner",
            isFresh = true,
            latestRelease = asset,
        )
    }

    /** API 清单的附件 URL 要与仓库一致；清单和安装包都只走 API。 */
    private suspend fun apiReleaseAsset(slug: String, expected: AccountSession): GitHubReleaseAsset? {
        val release = try {
            accountRequest(expected) { apiClient.latestRelease(slug, expected.token) }
        } catch (error: GitHubApiClient.HttpError) {
            if (error.code != 404) throw error
            return null
        }
        val manifestAsset = release.assets.firstOrNull { it.name == RELEASE_MANIFEST_FILE } ?: return null
        requireRepoAssetUrl(slug, manifestAsset.url)
        val manifestText = try {
            accountRequest(expected) { apiClient.downloadAsset(manifestAsset.url, expected.token) }.decodeToString()
        } catch (error: GitHubApiClient.HttpError) {
            // 仓库本身可见；资产下载的 404 不能误报成仓库不存在。
            if (error.code == 404) throw IOException("GitHub manifest 附件不可用", error)
            throw error
        }
        val manifest = json.decodeFromString<LatestPluginReleaseManifest>(manifestText)
        val filename = manifest.filename.ifBlank { manifest.name }.trim()
        requireFilename(filename)
        val zip = release.assets.firstOrNull { it.name == filename } ?: return null
        requireRepoAssetUrl(slug, zip.url)
        val version = manifest.version.trim().ifBlank { release.tagName.trim() }
        pluginRequire(version.isNotBlank(), R.string.plugin_error_release_manifest_missing_version)
        return GitHubReleaseAsset(version, zip.name, zip.url, zip.size)
    }

    private fun requireRepoAssetUrl(slug: String, url: String) {
        require(GitHubApiClient.isAssetApiUrl(url)) { "不是 GitHub 附件地址" }
        val parts = url.toHttpUrl().pathSegments
        require("${parts[1]}/${parts[2]}".equals(slug, ignoreCase = true)) { "GitHub 附件不属于该仓库" }
    }

    /** 下载账号路径的附件。保留旧入口，注销后不会继续使用旧令牌或缓存。 */
    suspend fun downloadAccountAsset(
        assetApiUrl: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray {
        val expected = session()
        return accountRequest(expected) { apiClient.downloadAsset(assetApiUrl, expected.token, onProgress) }
    }

    suspend fun downloadReleaseAsset(
        asset: GitHubReleaseAsset,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray = downloadReleaseAsset(asset.downloadUrl, onProgress)

    /** API 附件直连；浏览器下载地址仅用于已确认公有的仓库。 */
    suspend fun downloadReleaseAsset(
        downloadUrl: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray {
        if (GitHubApiClient.isAssetApiUrl(downloadUrl)) return downloadAccountAsset(downloadUrl, onProgress)
        val url = downloadUrl.toHttpUrl()
        require(url.isHttps && url.host == "github.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty()) { "不是 GitHub Release 下载地址" }
        val parts = url.pathSegments
        require(parts.size == 6 && parts[2] == "releases" &&
            (parts[3] == "latest" && parts[4] == "download" || parts[3] == "download")) {
            "不是 GitHub Release 下载地址"
        }
        val slug = "${parts[0]}/${parts[1]}"
        require(GitHubRepoAddress.isSlug(slug)) { "不是 GitHub 仓库地址" }
        requireFilename(parts.last())
        val expected = session()
        require(!usesAccount(slug)) { "账号仓库必须使用 API 附件地址" }
        if (!isKnownPublic(slug)) {
            val meta = accountRequest(expected) { apiClient.repo(slug, expected.token) }
            if (meta.isPrivate) {
                markAccount(listOf(slug), expected)
                throw IllegalArgumentException("私有仓库必须使用 API 附件地址")
            }
        }
        ensureSession(expected)
        val bytes = when {
            fetchBytesWithProgress != null -> fetchBytesWithProgress.invoke(downloadUrl, onProgress)
            fetchBytes != null -> {
                onProgress(0L, -1L)
                fetchBytes.invoke(downloadUrl).also { onProgress(it.size.toLong(), -1L) }
            }
            else -> defaultFetchBytes(client, downloadUrl, onProgress)
        }
        ensureSession(expected)
        return bytes
    }

    suspend fun viewer(token: String): GitHubViewer = apiClient.viewer(token)

    /** 缺少发布内容与网络、解析、鉴权失败分别处理；取消始终向调用者传播。 */
    suspend fun checkSource(source: String, kind: MarketSourceKind, preferAccount: Boolean = true): MarketSourceCheck {
        val slug = GitHubRepoAddress.parse(source) ?: return MarketSourceCheck.NotFound
        return try {
            val entries = fetchSource(slug, kind, preferAccount)
            MarketSourceCheck.Available(entries.size, usesAccount(slug) || entries.any { it.viaAccount })
        } catch (error: CancellationException) {
            throw error
        } catch (error: NothingPublished) {
            MarketSourceCheck.NothingPublished
        } catch (error: GitHubApiClient.HttpError) {
            when (error.code) {
                401 -> MarketSourceCheck.AccountExpired
                404 -> if (token() == null) MarketSourceCheck.NotFoundOrPrivate else MarketSourceCheck.NotFound
                else -> MarketSourceCheck.Unreachable(error.message)
            }
        } catch (error: Exception) {
            MarketSourceCheck.Unreachable(error.message)
        }
    }

    private fun parseRegistry(raw: String): List<GitHubRepoSummary> {
        val root = json.parseToJsonElement(raw) as? JsonObject
        require(root?.get("repositories") is JsonArray) { "GitHub 注册表缺少 repositories 数组" }
        return json.decodeFromString<PluginStarsPayload>(raw).repositories
            .mapNotNull { it.toSummary() }.distinctBy { it.fullName.lowercase() }
    }

    /** 保留公有 API 的分支参数，同样保护账号仓库。 */
    suspend fun fetchRegistry(registryRepo: String, branch: String = PLUGIN_STARS_BRANCH): List<GitHubRepoSummary> =
        fetchSourceInternal(registryRepo, MarketSourceKind.Plugin, preferAccount = false, branch = branch)

    /** [viaAccount] 强制 API 路径；未标记的自定义仓库也必须先确认 metadata，才能走镜像。 */
    suspend fun fetchLatestReleaseAsset(
        repoSlug: String,
        fresh: Boolean = false,
        viaAccount: Boolean = false,
    ): GitHubReleaseAsset? {
        val slug = GitHubRepoAddress.parse(repoSlug) ?: return null
        val key = slug.lowercase()
        val expected = session()
        if (viaAccount) markAccount(listOf(slug), expected)
        val now = System.currentTimeMillis()
        val (deferred, owner, request) = synchronized(accountLock) {
            ensureSession(expected)
            releaseCache[key]?.takeIf {
                now - it.atMillis < if (fresh) FRESH_RELEASE_TTL_MILLIS else RELEASE_TTL_MILLIS
            }?.let { return it.asset }
            val request = ReleaseRequest(key, usesAccount(slug))
            inFlight[request]?.let { Triple(it, false, request) } ?: CompletableDeferred<GitHubReleaseAsset?>().also {
                inFlight[request] = it
            }.let { Triple(it, true, request) }
        }
        if (!owner) return deferred.await()
        return try {
            val found = attempt {
                val accountOnly = usesAccount(slug) || if (isKnownPublic(slug)) false else {
                    val meta = accountRequest(expected) { apiClient.repo(slug, expected.token) }
                    if (!meta.isPrivate) markPublic(listOf(slug), expected)
                    meta.isPrivate
                }
                if (accountOnly) {
                    if (expected.token != null) markAccount(listOf(slug), expected)
                    apiReleaseAsset(slug, expected)
                } else {
                    publicReleaseAsset(slug, fresh) ?: apiReleaseAsset(slug, expected)?.also {
                        if (expected.token != null) markAccount(listOf(slug), expected)
                    }
                }
            }
            synchronized(accountLock) {
                ensureSession(expected)
                if (found != null && usesAccount(slug) && !GitHubApiClient.isAssetApiUrl(found.downloadUrl)) {
                    throw CancellationException("GitHub 仓库已切换到账号路径")
                }
                if (found != null) releaseCache[key] = CachedRelease(found, System.currentTimeMillis())
                deferred.complete(found)
            }
            found
        } catch (error: Exception) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            synchronized(accountLock) { if (inFlight[request] === deferred) inFlight.remove(request) }
        }
    }

    private suspend fun publicReleaseAsset(slug: String, fresh: Boolean): GitHubReleaseAsset? = attempt {
        val manifestUrl = latestReleaseDownloadUrl(slug, RELEASE_MANIFEST_FILE)
        val raw = fetchText(if (fresh) "$manifestUrl?ts=${System.currentTimeMillis() / 60_000}" else manifestUrl)
        val manifest = json.decodeFromString<LatestPluginReleaseManifest>(raw)
        val filename = manifest.filename.ifBlank { manifest.name }.trim()
        requireFilename(filename)
        val version = manifest.version.trim()
        pluginRequire(version.isNotBlank(), R.string.plugin_error_release_manifest_missing_version)
        GitHubReleaseAsset(version, filename, latestReleaseDownloadUrl(slug, filename), 0)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    suspend fun fetchAll(registryRepo: String, branch: String = PLUGIN_STARS_BRANCH): List<GitHubRepoSummary> = coroutineScope {
        fetchRegistry(registryRepo, branch).map { summary ->
            async {
                if (summary.latestRelease != null) summary
                else summary.copy(latestRelease = fetchLatestReleaseAsset(summary.fullName))
            }
        }.awaitAll()
    }

    companion object {
        /**
         * 注册表 URL 上带的时间片，用来击穿中间 CDN 的缓存。
         *
         * 清单走的是分支路径，各家代理与 CDN 会按整条 URL 缓存上十几个小时，
         * 而且是按边缘节点各自缓存的——源站和我这边都更新了，用户那边的节点
         * 仍可能发旧数据，谁也没法把全世界的节点都清一遍。
         * 加一个按 5 分钟取整的参数，等于把缓存上限压到 5 分钟；
         * 清单只有几百字节，这点重复请求可以忽略。
         */
        private fun cacheBucket(): Long =
            System.currentTimeMillis() / CACHE_BUCKET_MILLIS

        private const val CACHE_BUCKET_MILLIS = 5 * 60 * 1000L

        /** 要最新版时，一分钟内查过的算数：导课前那次查询紧跟在进页面那次后面。 */
        private const val FRESH_RELEASE_TTL_MILLIS = 60 * 1000L
        private const val RELEASE_TTL_MILLIS = 10 * 60 * 1000L
        private const val PLUGIN_STARS_BRANCH = "plugin-stars-data"
        private const val PLUGIN_STARS_FILE = "plugins-stars.json"
        private const val RELEASE_MANIFEST_FILE = "manifest.json"

        private suspend fun defaultFetchText(client: OkHttpClient, url: String): String =
            defaultFetchBytes(client, url).decodeToString()

        private suspend fun defaultFetchBytes(
            client: OkHttpClient,
            url: String,
            onProgress: (Long, Long) -> Unit = { _, _ -> },
        ): ByteArray =
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(url)
                    .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.8").build()
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            try {
                                pluginCheck(response.isSuccessful, R.string.plugin_error_http_request_failed, response.code, url)
                                val bytes = readDownloadBody(response.body, onProgress) {
                                    if (!continuation.isActive) throw CancellationException("Download cancelled")
                                }
                                continuation.resume(bytes)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }

        private fun PluginStarsRepositoryApi.toSummary(): GitHubRepoSummary? {
            val fullName = this.fullName.trim().takeIf { GitHubRepoAddress.isSlug(it) }
                ?: "${owner.trim()}/${repo.trim()}".takeIf { GitHubRepoAddress.isSlug(it) }
                ?: return null
            val normalizedOwner = owner.trim().ifBlank { fullName.substringBefore('/') }
            val normalizedName = repo.trim().ifBlank { fullName.substringAfter('/') }
            val embeddedRelease = release?.takeIf { it.filename.isNotBlank() && it.tag.isNotBlank() }?.let {
                GitHubReleaseAsset(
                    tagName = it.tag,
                    assetName = it.filename,
                    downloadUrl = latestReleaseDownloadUrl(fullName, it.filename),
                    sizeBytes = 0,
                )
            }
            return GitHubRepoSummary(
                fullName = fullName,
                owner = normalizedOwner,
                name = normalizedName,
                description = description.orEmpty(),
                stars = stars,
                avatarUrl = avatarUrl.ifBlank { "https://github.com/$normalizedOwner.png?size=80" },
                htmlUrl = htmlUrl.ifBlank { "https://github.com/$fullName" },
                language = language?.takeIf { it.isNotBlank() },
                updatedAt = null,
                ownerHtmlUrl = "https://github.com/$normalizedOwner",
                homepageUrl = null,
                pushedAt = null,
                isFresh = true,
                latestRelease = embeddedRelease,
                schoolAliases = (schools + aliases)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct(),
                kind = kind.trim(),
            )
        }

        private fun requireFilename(filename: String) {
            pluginRequire(filename.isNotBlank(), R.string.plugin_error_release_manifest_missing_filename)
            pluginRequire(
                '/' !in filename && '\\' !in filename && filename !in setOf(".", "..") &&
                    filename.none { it.isISOControl() },
                R.string.plugin_error_release_filename_path,
                filename,
            )
        }

        private fun latestReleaseDownloadUrl(repoSlug: String, filename: String): String {
            requireFilename(filename)
            val encodedFilename = URLEncoder.encode(filename, Charsets.UTF_8.name()).replace("+", "%20")
            return "https://github.com/$repoSlug/releases/latest/download/$encodedFilename"
        }

    }
}
