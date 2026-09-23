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

/**
 * 「导课前先查新版」在界面上的那一半：查到新版就弹提示让人先升级，放行了就真正发起同步。
 *
 * 导课入口（从教务系统导课）和插件页都挂一份，两处的行为一样。
 */
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
 * 已知有比本机新的版本时，给出带着那个 release 的仓库；没有就是 null。
 *
 * 现查到的（[PluginMarketUiState.latestReleases]）和市场列表里的取较新的那个：
 * 市场列表可能是一天前的缓存，现查的更准。
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

/** 市场列表里没有这个仓库时，按仓库名拼一份最小的摘要，够下载 release 用。 */
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

/** 版本号统一写成 v1.0.34：市场的带 v，插件清单里的不带。 */
internal fun displayVersion(version: String): String =
    "v" + version.trim().removePrefix("v").removePrefix("V")
