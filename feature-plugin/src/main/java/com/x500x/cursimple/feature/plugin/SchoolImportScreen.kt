package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

/**
 * 「从教务系统导课」的引导页。
 *
 * 插件市场是按仓库列的，新用户并不知道自己学校对应哪个仓库；这里把入口收成
 * 一件事：搜学校名 → 装上匹配的插件 → 就地登录导课，装完的插件默认就是打开的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolImportRoute(
    pluginMarketViewModel: PluginMarketViewModel,
    pluginRegistryRepo: String,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    pendingWebSession: WebSessionRequest?,
    onSyncPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    onBack: () -> Unit,
    onBrowseAllPlugins: () -> Unit,
    onAddCourseManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pluginRegistryRepo) {
        if (pluginRegistryRepo.isNotBlank()) {
            pluginMarketViewModel.refreshIfStale(pluginRegistryRepo, MARKET_CACHE_TTL_MILLIS)
        }
    }

    val matched = remember(uiState.marketRepos, query) {
        filterMarketRepos(uiState.marketRepos, query)
    }
    // 装自哪个仓库记在安装记录里，据此判断这一条是不是已经装好了
    val installedByRepo = remember(uiState.installedPlugins) {
        uiState.installedPlugins
            .filter { !it.sourceRepo.isNullOrBlank() }
            .associateBy { it.sourceRepo.orEmpty() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.school_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.school_import_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SchoolImportSteps()

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.school_import_search_label)) },
                placeholder = { Text(stringResource(R.string.school_import_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )

            uiState.status?.let { status ->
                Text(
                    text = context.pluginMarketStatusText(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.marketRepos.isNotEmpty()) {
                Text(
                    text = if (query.isBlank()) {
                        stringResource(R.string.school_import_catalog_count, uiState.marketRepos.size)
                    } else {
                        stringResource(R.string.school_import_match_count, matched.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                uiState.marketRepos.isEmpty() && uiState.isLoading -> SchoolImportBusy()

                uiState.marketRepos.isEmpty() -> SchoolImportEmpty(
                    text = stringResource(R.string.school_import_market_empty),
                    actionText = stringResource(R.string.school_import_retry),
                    onAction = { pluginMarketViewModel.loadRegistry(pluginRegistryRepo) },
                )

                // 学校真没插件时，「浏览全部」帮不上忙，得告诉用户还能怎么把课表弄进来
                matched.isEmpty() -> SchoolImportEmpty(
                    text = stringResource(R.string.school_import_no_match, query.trim()),
                    hint = stringResource(R.string.school_import_no_match_hint),
                    actionText = stringResource(R.string.school_import_add_manually),
                    onAction = onAddCourseManually,
                    secondaryActionText = stringResource(R.string.school_import_browse_all),
                    onSecondaryAction = onBrowseAllPlugins,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(matched, key = { it.fullName }) { repo ->
                        SchoolPluginRow(
                            repo = repo,
                            installed = installedByRepo[repo.fullName],
                            busy = uiState.isLoading,
                            syncingPluginId = syncingPluginId,
                            onInstall = { pluginMarketViewModel.installFromGitHub(repo) },
                            onSync = onSyncPlugin,
                        )
                    }
                    item(key = "browse-all") {
                        TextButton(
                            onClick = onBrowseAllPlugins,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.school_import_browse_all))
                        }
                    }
                }
            }

            syncStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }

    uiState.installPreview?.let { preview ->
        InstallPreviewDialog(
            preview = preview,
            origin = uiState.installPreviewOrigin,
            isLoading = uiState.isLoading,
            onDismiss = pluginMarketViewModel::dismissInstallPreview,
            onConfirm = pluginMarketViewModel::confirmInstall,
        )
    }

    // 登录教务系统的网页会话就在本页弹出，装完插件不必再绕去插件页
    pendingWebSession?.let { request ->
        WebSessionOverlay(
            request = request,
            onFinish = onCompleteWebSession,
            onCancel = onCancelWebSession,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchoolImportSteps() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                stringResource(R.string.school_import_step_search),
                stringResource(R.string.school_import_step_install),
                stringResource(R.string.school_import_step_login),
            ).forEachIndexed { index, step ->
                if (index > 0) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SchoolPluginRow(
    repo: GitHubRepoSummary,
    installed: InstalledPluginRecord?,
    busy: Boolean,
    syncingPluginId: String?,
    onInstall: () -> Unit,
    onSync: (String) -> Unit,
) {
    val syncing = installed != null &&
        (syncingPluginId == installed.installKey || syncingPluginId == installed.pluginId)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = repo.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                // 把注册表声明的学校列出来，用户才知道这条为什么被搜出来
                if (repo.schoolAliases.isNotEmpty()) {
                    Text(
                        text = repo.schoolAliases.take(3).joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                } else if (repo.description.isNotBlank()) {
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (installed != null) {
                    Text(
                        text = stringResource(R.string.school_import_installed_enabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                syncing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)

                installed != null -> Button(
                    onClick = { onSync(installed.installKey) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.school_import_action_sync), maxLines = 1)
                }

                else -> OutlinedButton(onClick = onInstall, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.school_import_action_install), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SchoolImportBusy() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SchoolImportEmpty(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    hint: String? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onAction) { Text(actionText) }
        if (secondaryActionText != null && onSecondaryAction != null) {
            TextButton(onClick = onSecondaryAction) { Text(secondaryActionText) }
        }
    }
}
