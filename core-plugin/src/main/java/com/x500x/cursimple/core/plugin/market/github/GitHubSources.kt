package com.x500x.cursimple.core.plugin.market.github

import java.net.URI

/**
 * Plugin and component registries share structure but use separate data branches; individual
 * release repositories are supported too.
 */
enum class MarketSourceKind(val dataBranch: String, val dataFile: String) {
    Plugin("plugin-stars-data", "plugins-stars.json"),
    Component("component-stars-data", "components-stars.json"),
}

object DefaultMarketSources {
    const val PLUGIN_REGISTRY = "cursimple/cursimple-plugins"
    const val COMPONENT_REGISTRY = "cursimple/cursimple-components"

    fun of(kind: MarketSourceKind): String = when (kind) {
        MarketSourceKind.Plugin -> PLUGIN_REGISTRY
        MarketSourceKind.Component -> COMPONENT_REGISTRY
    }

    fun isDefault(slug: String): Boolean =
        slug.equals(PLUGIN_REGISTRY, ignoreCase = true) || slug.equals(COMPONENT_REGISTRY, ignoreCase = true)
}

/** Normalize GitHub shorthand, HTTPS and SSH URLs to owner/repo; reject non-GitHub hosts. */
object GitHubRepoAddress {
    private val SLUG = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}$")

    internal fun isSlug(value: String): Boolean =
        SLUG.matches(value) && value.substringAfter('/') !in setOf(".", "..")

    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() } || '\\' in text) return null
        val path: String
        val isUrl: Boolean
        when {
            text.startsWith("git@github.com:", ignoreCase = true) -> {
                path = text.substringAfter(':')
                isUrl = false
            }
            text.contains("://") || text.startsWith("github.com/", ignoreCase = true) ||
                text.startsWith("www.github.com/", ignoreCase = true) -> {
                val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull()
                    ?: return null
                val scheme = uri.scheme?.lowercase()
                if (uri.host?.lowercase() !in setOf("github.com", "www.github.com")) return null
                if (scheme !in setOf("http", "https", "ssh")) return null
                if (scheme == "ssh") {
                    if (uri.rawUserInfo != "git" || uri.port !in setOf(-1, 22)) return null
                } else if (uri.rawUserInfo != null || uri.port !in setOf(-1, if (scheme == "https") 443 else 80)) {
                    return null
                }
                path = uri.rawPath?.removePrefix("/") ?: return null
                isUrl = true
            }
            else -> {
                path = text
                isUrl = false
            }
        }
        val parts = path.trimEnd('/').split('/')
        if (parts.size < 2 || (!isUrl && parts.size != 2) || parts.any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        val repo = parts[1].replace(Regex("\\.git$", RegexOption.IGNORE_CASE), "")
        val slug = "${parts[0]}/$repo"
        return slug.takeIf(::isSlug)
    }
}

/** Source connectivity result. */
sealed interface MarketSourceCheck {
    /**
     * [count] readable entries; [viaAccount] requires API for subsequent metadata and
     * downloads.
     */
    data class Available(val count: Int, val viaAccount: Boolean) : MarketSourceCheck

    /** Unauthenticated 404 cannot distinguish private from nonexistent repositories. */
    data object NotFoundOrPrivate : MarketSourceCheck

    /** The signed-in account cannot see the repository or lacks token access. */
    data object NotFound : MarketSourceCheck

    data object AccountExpired : MarketSourceCheck

    data object NothingPublished : MarketSourceCheck

    /** Network or GitHub request failure. */
    data class Unreachable(val reason: String?) : MarketSourceCheck
}

data class GitHubViewer(val login: String, val avatarUrl: String)
