package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration

/**
 * One entry from the plugins.json registry, after enrichment with GitHub repo metadata.
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
) {
    val displayTitle: String get() = name.ifBlank { fullName }
}

@Serializable
private data class GitHubRepoApi(
    @SerialName("name") val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("language") val language: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("owner") val owner: GitHubOwnerApi = GitHubOwnerApi(),
)

@Serializable
private data class GitHubOwnerApi(
    @SerialName("login") val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
private data class GitHubReleaseApi(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("assets") val assets: List<GitHubReleaseAssetApi> = emptyList(),
)

@Serializable
private data class GitHubReleaseAssetApi(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("size") val size: Long = 0,
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
) {

    /** Returns the list of `owner/repo` strings declared in plugins.json. */
    suspend fun fetchRegistry(registryRepo: String, branch: String = "main"): List<String> =
        withContext(Dispatchers.IO) {
            val slug = registryRepo.trim().trim('/')
            require(slug.matches(REPO_SLUG_REGEX)) { "无效的注册表仓库：$slug" }
            val url = "https://raw.githubusercontent.com/$slug/$branch/plugins.json"
            val raw = fetchText(url)
            val parsed = runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
                ?: return@withContext emptyList()
            parsed
                .map { it.trim() }
                .filter { it.matches(REPO_SLUG_REGEX) }
                .distinct()
        }

    /** Enriches one repo slug via GitHub REST API. Returns null if the repo cannot be loaded. */
    suspend fun fetchRepoSummary(repoSlug: String): GitHubRepoSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetchText("https://api.github.com/repos/$repoSlug")
            val body = json.decodeFromString<GitHubRepoApi>(raw)
            GitHubRepoSummary(
                fullName = body.fullName.ifBlank { repoSlug },
                owner = body.owner.login.ifBlank { repoSlug.substringBefore('/') },
                name = body.name.ifBlank { repoSlug.substringAfter('/', missingDelimiterValue = repoSlug) },
                description = body.description.orEmpty(),
                stars = body.stars,
                avatarUrl = body.owner.avatarUrl,
                htmlUrl = body.htmlUrl.ifBlank { "https://github.com/$repoSlug" },
                language = body.language?.takeIf { it.isNotBlank() },
                updatedAt = body.updatedAt,
                ownerHtmlUrl = body.owner.htmlUrl.ifBlank { "https://github.com/${body.owner.login}" },
                homepageUrl = body.homepage?.takeIf { it.isNotBlank() },
                pushedAt = body.pushedAt,
                isFresh = true,
            )
        }.getOrNull()
    }

    /**
     * Returns the first `.zip` asset of the repo's latest release, or `null` if no release / no zip asset.
     * Convention: a plugin repo publishes a Release whose first ZIP asset is the installable plugin package.
     */
    suspend fun fetchLatestReleaseAsset(repoSlug: String): GitHubReleaseAsset? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetchText("https://api.github.com/repos/$repoSlug/releases/latest")
            val release = json.decodeFromString<GitHubReleaseApi>(raw)
            val asset = release.assets.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
                ?: return@runCatching null
            GitHubReleaseAsset(
                tagName = release.tagName,
                assetName = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                sizeBytes = asset.size,
            )
        }.getOrNull()
    }

    /**
     * Fetches the registry and enriches every entry in parallel. For each repo we fan out into
     * (1) repo summary call and (2) latest-release call. Unreachable entries fall back to a placeholder.
     */
    suspend fun fetchAll(registryRepo: String, branch: String = "main"): List<GitHubRepoSummary> =
        coroutineScope {
            val slugs = fetchRegistry(registryRepo, branch)
            slugs.map { slug ->
                async {
                    val summary = async { fetchRepoSummary(slug) ?: placeholderSummary(slug) }
                    val release = async { fetchLatestReleaseAsset(slug) }
                    summary.await().copy(latestRelease = release.await())
                }
            }.awaitAll()
        }

    private fun placeholderSummary(slug: String): GitHubRepoSummary {
        val owner = slug.substringBefore('/')
        val name = slug.substringAfter('/', missingDelimiterValue = slug)
        return GitHubRepoSummary(
            fullName = slug,
            owner = owner,
            name = name,
            description = "（无法加载 GitHub 元信息，可能受 API 速率限制）",
            stars = 0,
            avatarUrl = "https://github.com/$owner.png?size=80",
            htmlUrl = "https://github.com/$slug",
            language = null,
            updatedAt = null,
            ownerHtmlUrl = "https://github.com/$owner",
            homepageUrl = null,
            pushedAt = null,
            isFresh = false,
        )
    }

    companion object {
        private val REPO_SLUG_REGEX = Regex("^[\\w.-]+/[\\w.-]+$")

        private fun defaultFetchText(client: OkHttpClient, url: String): String {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "请求失败 ${response.code}: $url" }
                return response.body.string()
            }
        }

    }
}
