package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.plugin.market.github.GitHubRepoAddress

internal fun normalizeMarketSources(sources: List<String>): List<String> =
    sources.mapNotNull(GitHubRepoAddress::parse).distinctBy { it.lowercase() }

internal fun encodeMarketSources(sources: List<String>): String =
    normalizeMarketSources(sources).joinToString("\n")

internal fun decodeMarketSources(raw: String): List<String> = normalizeMarketSources(raw.lines())

internal fun restoredPluginSources(saved: String?, legacyRepo: String?): List<String> =
    saved?.let(::decodeMarketSources)
        ?: normalizeMarketSources(listOfNotNull(DEFAULT_PLUGIN_REGISTRY_REPO, legacyRepo))
