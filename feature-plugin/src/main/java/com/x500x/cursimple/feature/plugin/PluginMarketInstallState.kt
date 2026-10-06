package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource

/** A page lock does not mean every item is being checked or installed. */
internal fun PluginMarketUiState.processingRepo(repoSlug: String): Boolean =
    isLoading && installingRepo?.trim()?.equals(repoSlug.trim(), ignoreCase = true) == true

internal fun PluginMarketUiState.processingPlugin(record: InstalledPluginRecord): Boolean =
    isLoading && (upgradingKey == record.installKey ||
        (record.source == PluginInstallSource.Remote && record.sourceRepo?.let { processingRepo(it) } == true) ||
        (installPreview?.manifest?.id == record.pluginId && installPreview?.source == record.source))

internal sealed interface PluginRepoInstallState {
    data object NotInstalled : PluginRepoInstallState

    data class Installed(val record: InstalledPluginRecord) : PluginRepoInstallState

    data class Updatable(val record: InstalledPluginRecord, val latestTag: String) : PluginRepoInstallState
}

internal val PluginRepoInstallState.installedRecord: InstalledPluginRecord?
    get() = when (this) {
        is PluginRepoInstallState.Installed -> record
        is PluginRepoInstallState.Updatable -> record
        PluginRepoInstallState.NotInstalled -> null
    }

/**
 * Match installations by stored source repository; legacy records without provenance require
 * reinstalling to establish it.
 */
internal fun resolveRepoInstallState(
    repoSlug: String,
    latestTag: String?,
    installed: List<InstalledPluginRecord>,
): PluginRepoInstallState {
    val slug = repoSlug.trim().lowercase()
    val record = installed.firstOrNull { it.sourceRepo?.trim()?.lowercase() == slug }
        ?: return PluginRepoInstallState.NotInstalled
    val tag = latestTag?.trim().orEmpty()
    return if (tag.isNotEmpty() && isNewerVersion(tag, record.version)) {
        PluginRepoInstallState.Updatable(record, tag)
    } else {
        PluginRepoInstallState.Installed(record)
    }
}

/**
 * Compare normalized numeric version components, ignoring a leading v; unequal unparseable
 * versions use legacy fallback.
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    if (a == null || b == null) return normalizeVersion(candidate) != normalizeVersion(installed)
    for (index in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(index) { 0 }
        val right = b.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionParts(value: String): List<Int>? {
    val core = normalizeVersion(value).substringBefore('-').substringBefore('+')
    if (core.isBlank()) return null
    return core.split('.').map { it.toIntOrNull() ?: return null }
}

private fun normalizeVersion(value: String): String =
    value.trim().removePrefix("v").removePrefix("V")
