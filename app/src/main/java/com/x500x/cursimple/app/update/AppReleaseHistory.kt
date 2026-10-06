package com.x500x.cursimple.app.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Release-history fields used by the UI, including the body already returned by the list API.
 */
data class AppReleaseSummary(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val notes: String,
    val prerelease: Boolean,
    val htmlUrl: String,
)

/** Serializable API fields keep release parsing testable without Android JSON stubs. */
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

/** Filter drafts and optionally prereleases; return an empty list on parse failure. */
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
