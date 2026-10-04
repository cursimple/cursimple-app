package com.x500x.cursimple.core.plugin.market.github

import java.net.URI

/**
 * 市场来源分两类：插件仓库和组件仓库。
 *
 * 两类仓库结构相同（手写清单 + CI 汇总到数据分支），只是汇总文件的分支和文件名不同。
 * 来源仓库也可以直接是一个插件 / 组件自己的仓库，这时按它最新的 Release 当作只有一条的清单。
 */
enum class MarketSourceKind(val dataBranch: String, val dataFile: String) {
    Plugin("plugin-stars-data", "plugins-stars.json"),
    Component("component-stars-data", "components-stars.json"),
}

/** 公有仓库，用户没动过来源列表时就是它们。 */
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

/**
 * 把用户填的仓库地址规整成 `owner/repo`。
 *
 * `owner/repo`、`https://github.com/owner/repo`、带 `.git`、带 `/tree/main` 之类的尾巴、
 * `git@github.com:owner/repo.git` 都认；别的网站的链接不认（令牌只发给 GitHub）。
 */
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

/** 检测一个来源能不能用的结果。 */
sealed interface MarketSourceCheck {
    /** 读到了 [count] 条；[viaAccount] 表示走账号 API 路径，后续查询和下载也必须走 API。 */
    data class Available(val count: Int, val viaAccount: Boolean) : MarketSourceCheck

    /** 没登录时 GitHub 对私有仓库和不存在的仓库都回 404，分不清，只能请人先登录再看。 */
    data object NotFoundOrPrivate : MarketSourceCheck

    /** 登录了也看不到：仓库不存在，或者这个账号 / 令牌没有它的权限。 */
    data object NotFound : MarketSourceCheck

    /** 令牌失效或被撤销。 */
    data object AccountExpired : MarketSourceCheck

    /** 仓库能打开，但既没有汇总清单，也没有带 manifest.json 的 Release。 */
    data object NothingPublished : MarketSourceCheck

    /** 网络不通或 GitHub 拒绝（比如请求太频繁）。 */
    data class Unreachable(val reason: String?) : MarketSourceCheck
}

/** 登录的 GitHub 账号。 */
data class GitHubViewer(val login: String, val avatarUrl: String)
