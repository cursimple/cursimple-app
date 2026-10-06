package com.x500x.cursimple.app.update

/** Release fields required for selection. */
internal data class ReleaseEntry(
    val index: Int,
    val tagName: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String,
)

/**
 * Exclude drafts and optional prereleases; use publication time then input order for stable
 * selection.
 */
internal fun pickUpdateRelease(
    entries: List<ReleaseEntry>,
    includePrerelease: Boolean,
): ReleaseEntry? = entries
    .filterNot { it.draft }
    .filter { it.tagName.isNotBlank() }
    .filter { includePrerelease || !it.prerelease }
    .sortedWith(
        compareByDescending<ReleaseEntry> { it.publishedAt.isNotBlank() }
            .thenByDescending { it.publishedAt }
            .thenBy { it.index },
    )
    .firstOrNull()

/** Explicit release channel takes priority over legacy version suffixes. */
internal fun isPrereleaseBuild(versionName: String, releaseChannel: String? = null): Boolean =
    releaseChannel?.let { it == "beta" } ?: versionName.contains('-')
