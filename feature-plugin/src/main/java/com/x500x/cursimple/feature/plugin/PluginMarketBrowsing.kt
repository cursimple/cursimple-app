package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary

const val MARKET_PREVIEW_COUNT: Int = 6

/** Positive [hiddenCount] enables the full-list destination. */
data class MarketPreview(
    val visible: List<GitHubRepoSummary>,
    val hiddenCount: Int,
)

fun marketPreview(
    repos: List<GitHubRepoSummary>,
    limit: Int = MARKET_PREVIEW_COUNT,
): MarketPreview {
    if (limit <= 0) return MarketPreview(visible = emptyList(), hiddenCount = repos.size)
    if (repos.size <= limit) return MarketPreview(visible = repos, hiddenCount = 0)
    return MarketPreview(visible = repos.take(limit), hiddenCount = repos.size - limit)
}

/**
 * Case-insensitive search across repository, owner, description and school aliases; empty
 * queries preserve the list.
 */
fun filterMarketRepos(
    repos: List<GitHubRepoSummary>,
    query: String,
): List<GitHubRepoSummary> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return repos
    return repos.filter { repo -> repo.matchesMarketQuery(keyword) }
}

internal fun GitHubRepoSummary.matchesMarketQuery(keyword: String): Boolean =
    fullName.contains(keyword, ignoreCase = true) ||
        name.contains(keyword, ignoreCase = true) ||
        owner.contains(keyword, ignoreCase = true) ||
        description.contains(keyword, ignoreCase = true) ||
        registrySource.contains(keyword, ignoreCase = true) ||
        latestRelease?.tagName?.contains(keyword, ignoreCase = true) == true ||
        schoolAliases.any { it.contains(keyword, ignoreCase = true) }

internal enum class MarketCatalogTab { Installed, Market }

/** Legacy cache entries may lack source metadata; prefer the entry's source when present. */
internal fun GitHubRepoSummary.marketSource(fallback: String): String =
    registrySource.trim().ifBlank { fallback.trim() }

internal fun isPublicMarketSource(source: String): Boolean =
    DefaultMarketSources.isDefault(source.trim().trim('/'))

internal fun InstalledPluginRecord.registrySourceFor(repos: List<GitHubRepoSummary>, fallback: String): String? =
    registrySource?.trim()?.takeIf { it.isNotBlank() }
        ?: repos.firstOrNull { it.fullName.equals(sourceRepo?.trim(), ignoreCase = true) }?.marketSource(fallback)

internal fun filterInstalledPlugins(
    installed: List<InstalledPluginRecord>,
    repos: List<GitHubRepoSummary>,
    query: String,
): List<InstalledPluginRecord> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return installed
    return installed.filter { plugin ->
        listOf(plugin.name, plugin.pluginId, plugin.publisher, plugin.version, plugin.sourceRepo.orEmpty(), plugin.registrySource.orEmpty())
            .any { it.contains(keyword, ignoreCase = true) } ||
            repos.any { repo ->
                repo.fullName.equals(plugin.sourceRepo?.trim(), ignoreCase = true) &&
                    repo.matchesMarketQuery(keyword)
            }
    }
}

/**
 * Use the first declared school alias as the import-page title; fall back to repository name.
 */
fun GitHubRepoSummary.schoolDisplayTitle(): String =
    schoolAliases.firstOrNull { it.isNotBlank() }?.trim() ?: displayTitle
