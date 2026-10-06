package com.x500x.cursimple.feature.plugin

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

/** Shared pre-import upgrade gate for both school search and plugin pages. */
@Composable
internal fun PluginUpgradeGate(
    uiState: PluginMarketUiState,
    viewModel: PluginMarketViewModel,
    onSyncPlugin: (String) -> Unit,
) {
    LaunchedEffect(uiState.readyToSyncKey) {
        val key = uiState.readyToSyncKey ?: return@LaunchedEffect
        viewModel.consumeReadyToSync()
        onSyncPlugin(key)
    }
    uiState.pendingUpgrade?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingUpgrade,
            title = { Text(stringResource(R.string.plugin_upgrade_required_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.plugin_upgrade_required_body,
                        pending.record.name,
                        displayVersion(pending.latestVersion),
                        displayVersion(pending.record.version),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = viewModel::startPendingUpgrade) {
                    Text(stringResource(R.string.plugin_upgrade_required_confirm))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = viewModel::dismissPendingUpgrade) {
                    Text(stringResource(R.string.plugin_upgrade_required_dismiss))
                }
            },
        )
    }
}

/**
 * Return the newer of fresh installed-release metadata and market data, or null without an
 * upgrade.
 */
internal fun availableUpgrade(
    record: InstalledPluginRecord,
    uiState: PluginMarketUiState,
): GitHubRepoSummary? {
    val slug = record.sourceRepo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val repo = uiState.marketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }
    val candidates = listOfNotNull(uiState.latestReleases[slug.lowercase()], repo?.latestRelease)
        .filter { it.tagName.isNotBlank() }
    val latest = candidates.maxWithOrNull { a, b ->
        when {
            isNewerVersion(a.tagName, b.tagName) -> 1
            isNewerVersion(b.tagName, a.tagName) -> -1
            else -> 0
        }
    } ?: return null
    if (!isNewerVersion(latest.tagName, record.version)) return null
    return (repo ?: minimalRepo(slug)).copy(latestRelease = latest)
}

internal fun minimalRepo(slug: String): GitHubRepoSummary {
    val owner = slug.substringBefore('/')
    return GitHubRepoSummary(
        fullName = slug,
        owner = owner,
        name = slug.substringAfter('/'),
        description = "",
        stars = 0,
        avatarUrl = "",
        htmlUrl = "https://github.com/$slug",
        ownerHtmlUrl = "https://github.com/$owner",
        isFresh = false,
    )
}

internal fun displayVersion(version: String): String =
    "v" + version.trim().removePrefix("v").removePrefix("V")
