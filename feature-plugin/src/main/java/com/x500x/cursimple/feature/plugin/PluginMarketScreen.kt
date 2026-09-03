package com.x500x.cursimple.feature.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled
import com.x500x.cursimple.core.plugin.install.pluginCompatibilityText
import com.x500x.cursimple.core.plugin.install.resolvePluginCompatibility
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

@Composable
fun PluginMarketRoute(
    pluginMarketViewModel: PluginMarketViewModel,
    componentMarketViewModel: ComponentMarketViewModel,
    pluginRegistryRepo: String,
    componentMarketIndexUrl: String,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    missingComponents: List<PluginComponentRequirement>,
    pendingWebSession: WebSessionRequest?,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pluginUiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
    val componentUiState by componentMarketViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(PluginPlatformTab.Plugins) }

    val pluginPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching { context.readContentBytes(it) }
                .onSuccess(pluginMarketViewModel::previewLocalPackage)
                .onFailure { error ->
                    pluginMarketViewModel.setStatus(pluginPackageReadFailure(error))
                }
        }
    }
    val componentPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching { context.readContentBytes(it) }
                .onSuccess(componentMarketViewModel::installLocalPackage)
                .onFailure { error ->
                    componentMarketViewModel.setStatus(componentPackageReadFailure(error))
                }
        }
    }

    LaunchedEffect(pluginRegistryRepo) {
        if (pluginRegistryRepo.isNotBlank()) {
            pluginMarketViewModel.refreshIfStale(pluginRegistryRepo, MARKET_CACHE_TTL_MILLIS)
        }
    }

    PluginMarketScreen(
        uiState = pluginUiState,
        componentUiState = componentUiState,
        selectedTab = selectedTab,
        enabledPluginIds = enabledPluginIds,
        syncingPluginId = syncingPluginId,
        syncStatusMessage = syncStatusMessage,
        missingComponents = missingComponents,
        pendingWebSession = pendingWebSession,
        pluginRegistryRepo = pluginRegistryRepo,
        onSelectTab = { selectedTab = it },
        onPickLocalPlugin = { pluginPackageLauncher.launch(PACKAGE_MIME_TYPES) },
        onRefreshMarket = { pluginMarketViewModel.loadRegistry(pluginRegistryRepo) },
        onOpenRepo = { url -> context.openExternalUrl(url) },
        onInstallFromGitHub = pluginMarketViewModel::installFromGitHub,
        onConfirmInstall = pluginMarketViewModel::confirmInstall,
        onDismissInstallPreview = pluginMarketViewModel::dismissInstallPreview,
        onRemovePlugin = pluginMarketViewModel::removePlugin,
        onSetPluginEnabled = onSetPluginEnabled,
        onSyncPlugin = onSyncPlugin,
        onPickLocalComponent = { componentPackageLauncher.launch(PACKAGE_MIME_TYPES) },
        onRefreshComponentMarket = { componentMarketViewModel.loadRemoteMarket(componentMarketIndexUrl) },
        onInstallRemoteComponentEntry = componentMarketViewModel::installRemoteEntry,
        onCompleteWebSession = onCompleteWebSession,
        onCancelWebSession = onCancelWebSession,
        modifier = modifier,
    )
}

@Composable
private fun PluginMarketScreen(
    uiState: PluginMarketUiState,
    componentUiState: ComponentMarketUiState,
    selectedTab: PluginPlatformTab,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    missingComponents: List<PluginComponentRequirement>,
    pendingWebSession: WebSessionRequest?,
    pluginRegistryRepo: String,
    onSelectTab: (PluginPlatformTab) -> Unit,
    onPickLocalPlugin: () -> Unit,
    onRefreshMarket: () -> Unit,
    onOpenRepo: (String) -> Unit,
    onInstallFromGitHub: (GitHubRepoSummary) -> Unit,
    onConfirmInstall: () -> Unit,
    onDismissInstallPreview: () -> Unit,
    onRemovePlugin: (String) -> Unit,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onPickLocalComponent: () -> Unit,
    onRefreshComponentMarket: () -> Unit,
    onInstallRemoteComponentEntry: (ComponentMarketEntry) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PluginPlatformTabs(
                selected = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            when (selectedTab) {
                PluginPlatformTab.Plugins -> PluginListContent(
                    uiState = uiState,
                    enabledPluginIds = enabledPluginIds,
                    syncingPluginId = syncingPluginId,
                    syncStatusMessage = syncStatusMessage,
                    missingComponents = missingComponents,
                    pluginRegistryRepo = pluginRegistryRepo,
                    onOpenComponents = { onSelectTab(PluginPlatformTab.Components) },
                    onPickLocalPlugin = onPickLocalPlugin,
                    onRefreshMarket = onRefreshMarket,
                    onOpenRepo = onOpenRepo,
                    onInstallFromGitHub = onInstallFromGitHub,
                    onRemovePlugin = onRemovePlugin,
                    onSetPluginEnabled = onSetPluginEnabled,
                    onSyncPlugin = onSyncPlugin,
                    modifier = Modifier.weight(1f),
                )

                PluginPlatformTab.Components -> ComponentMarketScreen(
                    uiState = componentUiState,
                    onPickLocalPackage = onPickLocalComponent,
                    onRefreshMarket = onRefreshComponentMarket,
                    onInstallRemoteEntry = onInstallRemoteComponentEntry,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        uiState.installPreview?.let { preview ->
            InstallPreviewDialog(
                preview = preview,
                origin = uiState.installPreviewOrigin,
                isLoading = uiState.isLoading,
                onDismiss = onDismissInstallPreview,
                onConfirm = onConfirmInstall,
            )
        }

        pendingWebSession?.let { request ->
            WebSessionOverlay(
                request = request,
                onFinish = onCompleteWebSession,
                onCancel = onCancelWebSession,
            )
        }
    }
}

@Composable
private fun PluginListContent(
    uiState: PluginMarketUiState,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    missingComponents: List<PluginComponentRequirement>,
    pluginRegistryRepo: String,
    onOpenComponents: () -> Unit,
    onPickLocalPlugin: () -> Unit,
    onRefreshMarket: () -> Unit,
    onOpenRepo: (String) -> Unit,
    onInstallFromGitHub: (GitHubRepoSummary) -> Unit,
    onRemovePlugin: (String) -> Unit,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailPluginKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailRepoSlug by rememberSaveable { mutableStateOf<String?>(null) }
    val detailPlugin = detailPluginKey?.let { key ->
        uiState.installedPlugins.firstOrNull { installedPluginKey(it) == key }
    }
    val detailRepo = detailRepoSlug?.let { slug -> uiState.marketRepos.firstOrNull { it.fullName == slug } }

    LaunchedEffect(uiState.installPreview, uiState.isLoading) {
        if (detailRepoSlug != null && uiState.installPreview == null && !uiState.isLoading && uiState.status != null) {
            kotlinx.coroutines.delay(300)
            detailRepoSlug = null
        }
    }

    // 详情页要先退回列表，否则系统返回键会一路退出应用
    androidx.activity.compose.BackHandler(enabled = detailPluginKey != null || detailRepoSlug != null) {
        detailPluginKey = null
        detailRepoSlug = null
    }

    if (detailPlugin != null) {
        PluginDetailScreen(
            plugin = detailPlugin,
            isEnabled = isPluginInstallEnabled(detailPlugin, enabledPluginIds, uiState.installedPlugins),
            isSyncing = syncingPluginId == detailPlugin.pluginId || syncingPluginId == detailPlugin.installKey,
            onBack = { detailPluginKey = null },
            onSetEnabled = { onSetPluginEnabled(detailPlugin.installKey, it) },
            onSync = { onSyncPlugin(detailPlugin.installKey) },
            onRemove = {
                onRemovePlugin(detailPlugin.installKey)
                detailPluginKey = null
            },
            modifier = modifier,
        )
        return
    }
    if (detailRepo != null) {
        GitHubRepoDetailScreen(
            repo = detailRepo,
            installState = resolveRepoInstallState(
                repoSlug = detailRepo.fullName,
                latestTag = detailRepo.latestRelease?.tagName,
                installed = uiState.installedPlugins,
            ),
            isLoading = uiState.isLoading,
            onBack = { detailRepoSlug = null },
            onOpenRepo = { onOpenRepo(detailRepo.htmlUrl) },
            onInstall = { onInstallFromGitHub(detailRepo) },
            onUninstall = onRemovePlugin,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val enabledCount = uiState.installedPlugins.count { plugin ->
        isPluginInstallEnabled(plugin, enabledPluginIds, uiState.installedPlugins)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PluginCountHeader(
                enabledCount = enabledCount,
                totalCount = uiState.installedPlugins.size,
                isLoading = uiState.isLoading,
                onPickLocalPlugin = onPickLocalPlugin,
                onRefreshMarket = onRefreshMarket,
            )
        }

        if (missingComponents.isNotEmpty()) {
            item {
                MissingComponentsCard(
                    components = missingComponents,
                    onOpenComponents = onOpenComponents,
                )
            }
        }

        syncStatusMessage?.let { message ->
            item {
                StatusCard(message = message)
            }
        }

        uiState.status?.let { status ->
            item {
                StatusCard(message = context.pluginMarketStatusText(status))
            }
        }

        item {
            MarketSectionHeader(registryRepo = pluginRegistryRepo)
        }

        if (uiState.marketRepos.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (uiState.isLoading) {
                        stringResource(R.string.plugin_market_loading_title)
                    } else {
                        stringResource(R.string.plugin_market_empty_title)
                    },
                    subtitle = if (uiState.isLoading) {
                        stringResource(R.string.plugin_market_loading_subtitle)
                    } else {
                        stringResource(R.string.plugin_market_empty_subtitle)
                    },
                )
            }
        } else {
            item {
                MarketGrid(
                    repos = uiState.marketRepos,
                    installed = uiState.installedPlugins,
                    onOpenDetail = { repo -> detailRepoSlug = repo.fullName },
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.plugin_market_section_installed))
        }

        if (uiState.installedPlugins.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.plugin_market_installed_empty_title),
                    subtitle = stringResource(R.string.plugin_market_installed_empty_subtitle),
                )
            }
        } else {
            items(uiState.installedPlugins, key = { installedPluginKey(it) }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isEnabled = isPluginInstallEnabled(plugin, enabledPluginIds, uiState.installedPlugins),
                    isSyncing = syncingPluginId == plugin.pluginId || syncingPluginId == plugin.installKey,
                    onSetEnabled = { onSetPluginEnabled(plugin.installKey, it) },
                    onSync = { onSyncPlugin(plugin.installKey) },
                    onOpenDetail = { detailPluginKey = installedPluginKey(plugin) },
                )
            }
        }
    }
}

@Composable
private fun MarketSectionHeader(registryRepo: String) {
    Column {
        SectionTitle(stringResource(R.string.plugin_market_section_market))
        Text(
            text = registryRepo.ifBlank { stringResource(R.string.plugin_market_registry_unset) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MarketGrid(
    repos: List<GitHubRepoSummary>,
    installed: List<InstalledPluginRecord>,
    onOpenDetail: (GitHubRepoSummary) -> Unit,
) {
    val rows = (repos.size + 1) / 2
    val rowHeight = 168.dp
    val totalHeight = rowHeight * rows + 12.dp * (rows - 1).coerceAtLeast(0)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(repos, key = { it.fullName }) { repo ->
            GitHubRepoCard(
                repo = repo,
                installState = resolveRepoInstallState(
                    repoSlug = repo.fullName,
                    latestTag = repo.latestRelease?.tagName,
                    installed = installed,
                ),
                onClick = { onOpenDetail(repo) },
            )
        }
    }
}

@Composable
private fun GitHubRepoCard(
    repo: GitHubRepoSummary,
    installState: PluginRepoInstallState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OwnerAvatar(owner = repo.owner, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = repo.owner,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = repo.description.ifBlank { stringResource(R.string.plugin_market_repo_no_description) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = repo.stars.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                InstallStatePill(installState)
                VersionPill(tag = repo.latestRelease?.tagName)
            }
        }
    }
}

@Composable
private fun VersionPill(tag: String?) {
    val text = tag?.takeIf { it.isNotBlank() } ?: stringResource(R.string.plugin_market_version_missing)
    val hasVersion = tag?.isNotBlank() == true
    val container = if (hasVersion) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (hasVersion) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OwnerAvatar(owner: String, size: Dp) {
    val initial = owner.firstOrNull()?.uppercase() ?: "?"
    val hash = owner.hashCode()
    val palette = listOf(
        Color(0xFF5B8DEF),
        Color(0xFFEF5B8D),
        Color(0xFF8DEF5B),
        Color(0xFF5BEFEF),
        Color(0xFFEF8D5B),
        Color(0xFFB45BEF),
    )
    val color = palette[(hash and 0x7FFFFFFF) % palette.size]
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubRepoDetailScreen(
    repo: GitHubRepoSummary,
    installState: PluginRepoInstallState,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenRepo: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DetailBackButton(onBack)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.plugin_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OwnerAvatar(owner = repo.owner, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = repo.displayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = repo.owner,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${repo.stars} stars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            repo.language?.let {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            VersionPill(tag = repo.latestRelease?.tagName)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val hasRelease = repo.latestRelease != null
                            val installed = installState.installedRecord
                            // 已装且版本一致时按钮只作状态展示，不再重复安装
                            val actionEnabled = hasRelease && !isLoading &&
                                installState !is PluginRepoInstallState.Installed
                            Button(onClick = onInstall, enabled = actionEnabled) {
                                Icon(
                                    imageVector = if (installState is PluginRepoInstallState.Installed) {
                                        Icons.Rounded.Check
                                    } else {
                                        Icons.Rounded.Download
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val label = when {
                                    isLoading -> stringResource(R.string.plugin_repo_action_processing)
                                    !hasRelease -> stringResource(R.string.plugin_market_version_missing)
                                    installState is PluginRepoInstallState.Installed -> stringResource(
                                        R.string.plugin_repo_action_installed,
                                        installState.record.version,
                                    )
                                    installState is PluginRepoInstallState.Updatable -> stringResource(
                                        R.string.plugin_repo_action_update,
                                        installState.latestTag,
                                    )
                                    else -> stringResource(
                                        R.string.plugin_repo_action_install,
                                        repo.latestRelease!!.tagName,
                                    )
                                }
                                Text(label, maxLines = 2)
                            }
                            if (installed != null) {
                                OutlinedButton(onClick = { onUninstall(installed.installKey) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.plugin_repo_action_uninstall), maxLines = 2)
                                }
                            }
                            OutlinedButton(onClick = onOpenRepo) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.plugin_repo_action_open_github), maxLines = 2)
                            }
                        }
                    }
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_repo_section_description)) {
                    Text(
                        text = repo.description.ifBlank {
                            stringResource(R.string.plugin_market_repo_no_description)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_repo_section_repository)) {
                    DetailRow(stringResource(R.string.plugin_repo_field_full_name), repo.fullName)
                    repo.homepageUrl?.let { DetailRow(stringResource(R.string.plugin_repo_field_homepage), it) }
                    repo.updatedAt?.let { DetailRow(stringResource(R.string.plugin_repo_field_updated), it) }
                    if (!repo.isFresh) {
                        DetailRow(
                            stringResource(R.string.plugin_repo_field_notice),
                            stringResource(R.string.plugin_repo_stale_notice),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginPlatformTabs(
    selected: PluginPlatformTab,
    onSelect: (PluginPlatformTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PluginPlatformTab.entries.forEach { tab ->
            PlatformTabChip(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlatformTabChip(
    tab: PluginPlatformTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PluginCountHeader(
    enabledCount: Int,
    totalCount: Int,
    isLoading: Boolean,
    onPickLocalPlugin: () -> Unit,
    onRefreshMarket: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val compact = maxWidth < 360.dp

            @Composable
            fun CountColumn(modifier: Modifier = Modifier) {
                Column(modifier = modifier) {
                    Text(
                        text = stringResource(R.string.plugin_market_count_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$enabledCount / $totalCount",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.plugin_market_count_caption),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            @Composable
            fun ActionButtons(modifier: Modifier = Modifier) {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPickLocalPlugin,
                        modifier = if (compact) Modifier.weight(1f) else Modifier,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.plugin_market_action_import_zip),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Button(
                        onClick = onRefreshMarket,
                        enabled = !isLoading,
                        modifier = if (compact) Modifier.weight(1f) else Modifier,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isLoading) {
                                stringResource(R.string.plugin_market_action_loading)
                            } else {
                                stringResource(R.string.plugin_market_action_refresh)
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountColumn(modifier = Modifier.fillMaxWidth())
                    ActionButtons(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountColumn(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionButtons()
                }
            }
        }
    }
}

@Composable
private fun MissingComponentsCard(
    components: List<PluginComponentRequirement>,
    onOpenComponents: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_market_missing_components_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = components.joinToString { it.id },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onOpenComponents) {
                Text(stringResource(R.string.plugin_market_missing_components_action))
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: InstalledPluginRecord,
    isEnabled: Boolean,
    isSyncing: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${plugin.version} · ${plugin.source.name.lowercase()} · ${plugin.compatibilityStatus.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onSetEnabled,
                )
            }
            Text(
                text = plugin.permissions.joinToString { it.id }.ifBlank {
                    stringResource(R.string.plugin_card_no_permission)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isEnabled) {
                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                ) {
                    Text(
                        if (isSyncing) {
                            stringResource(R.string.plugin_card_action_syncing)
                        } else {
                            stringResource(R.string.plugin_card_action_sync)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EnabledBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = stringResource(R.string.plugin_badge_enabled),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PluginDetailScreen(
    plugin: InstalledPluginRecord,
    isEnabled: Boolean,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // 旧记录里存过渲染好的原因，没有时按记录声明的接口版本现算
    val compatibilityMessage = plugin.compatibilityMessage?.takeIf { it.isNotBlank() }
        ?: context.pluginCompatibilityText(resolvePluginCompatibility(plugin.apiVersion))
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DetailBackButton(onBack)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.plugin_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "v${plugin.version}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isEnabled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    EnabledBadge()
                                }
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = onSetEnabled,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isEnabled) {
                                Button(
                                    onClick = onSync,
                                    enabled = !isSyncing,
                                ) {
                                    Text(
                                        if (isSyncing) {
                                            stringResource(R.string.plugin_card_action_syncing)
                                        } else {
                                            stringResource(R.string.plugin_card_action_sync)
                                        },
                                    )
                                }
                            }
                            TextButton(onClick = { showRemoveConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.plugin_detail_action_remove))
                            }
                        }
                    }
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_detail_section_basic)) {
                    val undeclared = stringResource(R.string.plugin_detail_value_undeclared)
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_publisher),
                        plugin.publisher.ifBlank { undeclared },
                    )
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_source),
                        plugin.source.name.lowercase(),
                    )
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_compatibility),
                        plugin.compatibilityStatus.name.lowercase(),
                    )
                    compatibilityMessage?.let {
                        DetailRow(stringResource(R.string.plugin_detail_field_compatibility_message), it)
                    }
                    DetailRow(stringResource(R.string.plugin_detail_field_plugin_id), plugin.pluginId)
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_api),
                        plugin.apiVersion?.toString() ?: undeclared,
                    )
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_entry),
                        plugin.entry.ifBlank { undeclared },
                    )
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_detail_section_permissions)) {
                    Text(
                        text = plugin.permissions.joinToString { it.id }.ifBlank {
                            stringResource(R.string.plugin_detail_value_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_detail_section_web_engine)) {
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_preferred),
                        plugin.webEngine.preferred,
                    )
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_allow_chromium),
                        if (plugin.webEngine.allowChromium) {
                            stringResource(R.string.plugin_detail_value_yes)
                        } else {
                            stringResource(R.string.plugin_detail_value_no)
                        },
                    )
                    plugin.webEngine.chromiumComponent?.takeIf { it.isNotBlank() }?.let {
                        DetailRow(stringResource(R.string.plugin_detail_field_chromium_component), it)
                    }
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_detail_section_components)) {
                    if (plugin.components.isEmpty()) {
                        Text(
                            stringResource(R.string.plugin_detail_value_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            plugin.components.forEach { component ->
                                Text(
                                    text = componentRequirementText(component),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            item {
                DetailSection(stringResource(R.string.plugin_detail_section_allowed_hosts)) {
                    if (plugin.allowedHosts.isEmpty()) {
                        Text(
                            stringResource(R.string.plugin_detail_value_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            plugin.allowedHosts.forEach { host ->
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = { Text(stringResource(R.string.plugin_remove_dialog_title)) },
                text = { Text(stringResource(R.string.plugin_remove_dialog_message)) },
                confirmButton = {
                    Button(onClick = {
                        showRemoveConfirm = false
                        onRemove()
                    }) {
                        Text(stringResource(R.string.plugin_remove_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirm = false }) {
                        Text(stringResource(R.string.plugin_action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun InstallPreviewDialog(
    preview: PluginInstallPreview,
    origin: PluginInstallOrigin?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val manifest = preview.manifest
    val canInstall = canConfirmPluginInstall(preview)
    val allowedHosts = manifest.allowedHosts.filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_install_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val undeclared = stringResource(R.string.plugin_detail_value_undeclared)
                    DetailRow(stringResource(R.string.plugin_detail_field_version), "v${manifest.version}")
                    DetailRow(stringResource(R.string.plugin_detail_field_plugin_id), manifest.id)
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_publisher),
                        manifest.publisher.ifBlank { undeclared },
                    )
                    DetailRow(
                        stringResource(R.string.plugin_detail_field_api),
                        manifest.apiVersion?.toString() ?: undeclared,
                    )
                    DetailRow(stringResource(R.string.plugin_detail_field_entry), manifest.entry)
                }

                SectionTitle(stringResource(R.string.plugin_install_section_source))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow(
                        stringResource(R.string.plugin_install_field_channel),
                        context.pluginInstallOriginText(
                            pluginInstallOriginLabel(preview.source, origin),
                        ),
                    )
                    origin?.let {
                        DetailRow(stringResource(R.string.plugin_install_field_download_url), it.downloadUrl)
                    }
                }

                SectionTitle(stringResource(R.string.plugin_install_section_allowed_hosts))
                if (allowedHosts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_install_allowed_hosts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        allowedHosts.forEach { host ->
                            Text(
                                text = host,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                SectionTitle(stringResource(R.string.plugin_install_section_permissions))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    context.pluginPermissionListText(
                        pluginPermissionList(manifest.permissions),
                    ).forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.plugin_install_permission_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(stringResource(R.string.plugin_install_section_integrity))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow(
                        stringResource(R.string.plugin_install_field_checksum),
                        stringResource(pluginChecksumLabelRes(preview.checksumVerified)),
                    )
                    DetailRow(
                        stringResource(R.string.plugin_install_field_signature),
                        context.pluginSignatureText(
                            pluginSignatureLabel(preview.signatureStatus, preview.signerFingerprint),
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.plugin_install_integrity_trust_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                pluginInstallBlockReason(preview)?.let { reason ->
                    Text(
                        text = context.pluginInstallBlockReasonText(reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canInstall && !isLoading,
            ) {
                Text(
                    if (isLoading) {
                        stringResource(R.string.plugin_install_action_installing)
                    } else {
                        stringResource(R.string.plugin_install_action_install)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plugin_action_cancel))
            }
        },
    )
}

@Composable
private fun WebSessionOverlay(
    request: WebSessionRequest,
    onFinish: (WebSessionPacket) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xD9000000))
            .padding(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            PluginWebSessionScreen(
                request = request,
                onFinish = onFinish,
                onCancel = onCancel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatusCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class PluginPlatformTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Plugins(R.string.plugin_market_tab_plugins, Icons.Rounded.Extension),
    Components(R.string.plugin_market_tab_components, Icons.Rounded.Widgets),
}

private fun installedPluginKey(plugin: InstalledPluginRecord): String =
    plugin.installKey

@Composable
@ReadOnlyComposable
private fun componentRequirementText(component: PluginComponentRequirement): String {
    val requirement = if (component.required) {
        stringResource(R.string.plugin_detail_component_required)
    } else {
        stringResource(R.string.plugin_detail_component_optional)
    }
    return buildString {
        append(component.id)
        append(" / ")
        append(component.type)
        append(" / ").append(requirement)
        component.version?.let { append(" / v").append(it) }
        component.abi?.let { append(" / ").append(it) }
    }
}

private fun Context.readContentBytes(uri: Uri): ByteArray {
    return contentResolver.openInputStream(uri)?.use { it.readLocalPackageBytes() }
        ?: error(getString(R.string.plugin_market_read_file_failed))
}

private fun Context.openExternalUrl(url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

private const val MARKET_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L

private val PACKAGE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
    "*/*",
)

/** 已装或可更新时在卡片上标一下，未安装时不占位。 */
@Composable
private fun InstallStatePill(state: PluginRepoInstallState) {
    val labelRes = when (state) {
        is PluginRepoInstallState.Installed -> R.string.plugin_repo_state_installed
        is PluginRepoInstallState.Updatable -> R.string.plugin_repo_state_update
        PluginRepoInstallState.NotInstalled -> return
    }
    val container = if (state is PluginRepoInstallState.Updatable) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
    Spacer(modifier = Modifier.width(4.dp))
}

/** 详情页左上角的返回，带边框以便和旁边的标题区分开。 */
@Composable
private fun DetailBackButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.plugin_action_back), maxLines = 1)
    }
}
