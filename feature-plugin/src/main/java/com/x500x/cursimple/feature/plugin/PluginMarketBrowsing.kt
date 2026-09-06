package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary

/** 插件页里市场区块最多直接铺开几个，多出来的收进浏览全部。 */
const val MARKET_PREVIEW_COUNT: Int = 6

/**
 * 市场区块要展示的内容。
 * [hiddenCount] 大于 0 时界面给出浏览全部的入口，否则该入口没有意义。
 */
data class MarketPreview(
    val visible: List<GitHubRepoSummary>,
    val hiddenCount: Int,
)

/** 截断到 [limit] 个，并数清还剩多少个没露出来。 */
fun marketPreview(
    repos: List<GitHubRepoSummary>,
    limit: Int = MARKET_PREVIEW_COUNT,
): MarketPreview {
    if (limit <= 0) return MarketPreview(visible = emptyList(), hiddenCount = repos.size)
    if (repos.size <= limit) return MarketPreview(visible = repos, hiddenCount = 0)
    return MarketPreview(visible = repos.take(limit), hiddenCount = repos.size - limit)
}

/**
 * 按关键词过滤插件。
 * 仓库名、所属账号与描述任一命中即算匹配，忽略大小写与首尾空白；
 * 空关键词返回原列表，避免搜索框还没输入就把列表清空。
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
        description.contains(keyword, ignoreCase = true)
