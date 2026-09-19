package com.x500x.cursimple.app.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 版本历史里的一条。
 *
 * 只保留界面要用的几项；正文不另发请求——列表接口本来就把 body 一起带回来了。
 */
data class AppReleaseSummary(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val notes: String,
    val prerelease: Boolean,
    val htmlUrl: String,
)

/**
 * GitHub releases 列表里用得上的字段。
 *
 * 用 kotlinx.serialization 而不是 org.json：后者在单元测试里是空壳，
 * 解析逻辑就没法直接测了。
 */
@Serializable
private data class RawRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

private val historyJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * 解析 GitHub 的 releases 列表。
 *
 * [includePrerelease] 为假时只留正式版；草稿一律不要——它还没发出去，
 * 出现在用户的版本历史里只会让人困惑。整段解析失败时返回空列表而不是抛出。
 */
internal fun parseReleaseHistory(body: String, includePrerelease: Boolean): List<AppReleaseSummary> {
    val raw = runCatching {
        historyJson.decodeFromString(ListSerializer(RawRelease.serializer()), body)
    }.getOrNull() ?: return emptyList()
    return raw.asSequence()
        .filterNot { it.draft }
        .filter { includePrerelease || !it.prerelease }
        .filter { it.tagName.isNotBlank() }
        .map { release ->
            AppReleaseSummary(
                tagName = release.tagName,
                name = release.name.ifBlank { release.tagName },
                publishedAt = release.publishedAt,
                notes = release.body.trim(),
                prerelease = release.prerelease,
                htmlUrl = release.htmlUrl,
            )
        }
        .toList()
}
