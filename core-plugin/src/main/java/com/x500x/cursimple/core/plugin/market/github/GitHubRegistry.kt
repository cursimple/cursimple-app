package com.x500x.cursimple.core.plugin.market.github

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginCheck
import com.x500x.cursimple.core.plugin.pluginRequire
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
) {
    val displayTitle: String get() = name.ifBlank { fullName }
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
) {

    /** Returns the repository summaries declared in plugins-stars.json. */
    suspend fun fetchRegistry(registryRepo: String, branch: String = PLUGIN_STARS_BRANCH): List<GitHubRepoSummary> =
        withContext(Dispatchers.IO) {
            val slug = registryRepo.trim().trim('/')
            pluginRequire(slug.matches(REPO_SLUG_REGEX), R.string.plugin_error_registry_repo_invalid, slug)
            val url = "https://raw.githubusercontent.com/$slug/$branch/$PLUGIN_STARS_FILE"
            val raw = fetchText(url)
            val parsed = runCatching { json.decodeFromString<PluginStarsPayload>(raw) }.getOrNull()
                ?: return@withContext emptyList()
            parsed.repositories
                .mapNotNull { it.toSummary() }
                .distinctBy { it.fullName.lowercase() }
        }

    /**
     * 读取仓库最新 release 里的 manifest.json，其中声明了插件包文件名和市场展示的版本号。
     */
    suspend fun fetchLatestReleaseAsset(repoSlug: String): GitHubReleaseAsset? = withContext(Dispatchers.IO) {
        runCatching {
            pluginRequire(
                repoSlug.matches(REPO_SLUG_REGEX),
                R.string.plugin_error_plugin_repo_invalid,
                repoSlug,
            )
            val raw = fetchText(latestReleaseDownloadUrl(repoSlug, RELEASE_MANIFEST_FILE))
            val manifest = json.decodeFromString<LatestPluginReleaseManifest>(raw)
            val filename = manifest.filename.ifBlank { manifest.name }.trim()
            val version = manifest.version.trim()
            pluginRequire(
                filename.isNotBlank(),
                R.string.plugin_error_release_manifest_missing_filename,
            )
            pluginRequire(version.isNotBlank(), R.string.plugin_error_release_manifest_missing_version)
            GitHubReleaseAsset(
                tagName = version,
                assetName = filename,
                downloadUrl = latestReleaseDownloadUrl(repoSlug, filename),
                sizeBytes = 0,
            )
        }.getOrNull()
    }

    /**
     * 拉取 stars 注册表，并并发地为每条记录补齐最新 release 的 manifest。
     */
    suspend fun fetchAll(registryRepo: String, branch: String = PLUGIN_STARS_BRANCH): List<GitHubRepoSummary> =
        coroutineScope {
            val summaries = fetchRegistry(registryRepo, branch)
            summaries.map { summary ->
                async {
                    if (summary.latestRelease != null) summary
                    else summary.copy(latestRelease = fetchLatestReleaseAsset(summary.fullName))
                }
            }.awaitAll()
        }

    companion object {
        private val REPO_SLUG_REGEX = Regex("^[\\w.-]+/[\\w.-]+$")
        private const val PLUGIN_STARS_BRANCH = "plugin-stars-data"
        private const val PLUGIN_STARS_FILE = "plugins-stars.json"
        private const val RELEASE_MANIFEST_FILE = "manifest.json"

        private fun defaultFetchText(client: OkHttpClient, url: String): String {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                pluginCheck(response.isSuccessful, R.string.plugin_error_http_request_failed, response.code, url)
                return response.body.string()
            }
        }

        private fun PluginStarsRepositoryApi.toSummary(): GitHubRepoSummary? {
            val fullName = this.fullName.trim().takeIf { it.matches(REPO_SLUG_REGEX) }
                ?: "${owner.trim()}/${repo.trim()}".takeIf { it.matches(REPO_SLUG_REGEX) }
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
            )
        }

        private fun latestReleaseDownloadUrl(repoSlug: String, filename: String): String {
            pluginRequire(
                !filename.contains('/'),
                R.string.plugin_error_release_filename_path,
                filename,
            )
            val encodedFilename = URLEncoder.encode(filename, Charsets.UTF_8.name()).replace("+", "%20")
            return "https://github.com/$repoSlug/releases/latest/download/$encodedFilename"
        }

    }
}
