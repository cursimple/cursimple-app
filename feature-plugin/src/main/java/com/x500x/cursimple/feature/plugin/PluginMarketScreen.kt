package com.x500x.cursimple.feature.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled
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
                    pluginMarketViewModel.setStatusMessage(error.message ?: "读取插件包失败")
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
                    componentMarketViewModel.setStatusMessage(error.message ?: "读取组件包失败")
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
        if (detailRepoSlug != null && uiState.installPreview == null && !uiState.isLoading && uiState.statusMessage != null) {
            kotlinx.coroutines.delay(300)
            detailRepoSlug = null
        }
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
            isLoading = uiState.isLoading,
            onBack = { detailRepoSlug = null },
            onOpenRepo = { onOpenRepo(detailRepo.htmlUrl) },
            onInstall = { onInstallFromGitHub(detailRepo) },
            modifier = modifier,
        )
        return
    }

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

        uiState.statusMessage?.let { message ->
            item {
                StatusCard(message = message)
            }
        }

        item {
            MarketSectionHeader(registryRepo = pluginRegistryRepo)
        }

        if (uiState.marketRepos.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (uiState.isLoading) "正在加载..." else "插件市场为空",
                    subtitle = if (uiState.isLoading) {
                        "稍候。"
                    } else {
                        "注册表里没有任何插件，或还未刷新成功。打开管理页可以新增。"
                    },
                )
            }
        } else {
            item {
                MarketGrid(
                    repos = uiState.marketRepos,
                    onOpenDetail = { repo -> detailRepoSlug = repo.fullName },
                )
            }
        }

        item {
            SectionTitle("已安装插件")
        }

        if (uiState.installedPlugins.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "还没有任何插件",
                    subtitle = "从本地 ZIP 安装后会显示在这里。当前版本不再自动安装内置示例插件。",
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
        SectionTitle("插件市场")
        Text(
            text = registryRepo.ifBlank { "未配置注册表仓库" },
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
            GitHubRepoCard(repo = repo, onClick = { onOpenDetail(repo) })
        }
    }
}

@Composable
private fun GitHubRepoCard(
    repo: GitHubRepoSummary,
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
                text = repo.description.ifBlank { "（无描述）" },
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
                VersionPill(tag = repo.latestRelease?.tagName)
            }
        }
    }
}

@Composable
private fun VersionPill(tag: String?) {
    val text = tag?.takeIf { it.isNotBlank() } ?: "未找到版本"
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

@Composable
private fun GitHubRepoDetailScreen(
    repo: GitHubRepoSummary,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenRepo: () -> Unit,
    onInstall: () -> Unit,
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
                    TextButton(onClick = onBack) { Text("返回") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "插件详情",
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hasRelease = repo.latestRelease != null
                            Button(
                                onClick = onInstall,
                                enabled = hasRelease && !isLoading,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val label = when {
                                    isLoading -> "处理中..."
                                    !hasRelease -> "未找到版本"
                                    else -> "安装 ${repo.latestRelease!!.tagName}"
                                }
                                Text(label)
                            }
                            OutlinedButton(onClick = onOpenRepo) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("在 GitHub 查看")
                            }
                        }
                    }
                }
            }

            item {
                DetailSection("描述") {
                    Text(
                        text = repo.description.ifBlank { "（无描述）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                DetailSection("仓库") {
                    DetailRow("全名", repo.fullName)
                    repo.homepageUrl?.let { DetailRow("主页", it) }
                    repo.updatedAt?.let { DetailRow("更新", it) }
                    if (!repo.isFresh) {
                        DetailRow("提示", "未能加载最新元信息，已显示降级数据。")
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
                text = tab.label,
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
                        text = "插件",
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
                        text = "已启用 / 已安装",
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
                        Text("导入 ZIP", maxLines = 1, softWrap = false)
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
                        Text(if (isLoading) "加载中" else "刷新", maxLines = 1, softWrap = false)
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
                text = "缺少必需组件",
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
                Text("查看组件")
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
                text = plugin.permissions.joinToString { it.id }.ifBlank { "无权限声明" },
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
                    Text(if (isSyncing) "同步中..." else "同步课表")
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
            text = "已启用",
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
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "插件详情",
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
                                    Text(if (isSyncing) "同步中..." else "同步课表")
                                }
                            }
                            TextButton(onClick = { showRemoveConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("移除插件")
                            }
                        }
                    }
                }
            }

            item {
                DetailSection("基本信息") {
                    DetailRow("发布者", plugin.publisher.ifBlank { "未声明" })
                    DetailRow("来源", plugin.source.name.lowercase())
                    DetailRow("兼容性", plugin.compatibilityStatus.name.lowercase())
                    plugin.compatibilityMessage?.takeIf { it.isNotBlank() }?.let {
                        DetailRow("兼容性说明", it)
                    }
                    DetailRow("插件 ID", plugin.pluginId)
                    DetailRow("API", plugin.apiVersion.toString())
                    DetailRow("入口", plugin.entry.ifBlank { "未声明" })
                }
            }

            item {
                DetailSection("权限") {
                    Text(
                        text = plugin.permissions.joinToString { it.id }.ifBlank { "无" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                DetailSection("Web 引擎") {
                    DetailRow("首选", plugin.webEngine.preferred)
                    DetailRow("允许 Chromium", if (plugin.webEngine.allowChromium) "是" else "否")
                    plugin.webEngine.chromiumComponent?.takeIf { it.isNotBlank() }?.let {
                        DetailRow("Chromium 组件", it)
                    }
                }
            }

            item {
                DetailSection("组件依赖") {
                    if (plugin.components.isEmpty()) {
                        Text("无", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                DetailSection("站点白名单") {
                    if (plugin.allowedHosts.isEmpty()) {
                        Text("无", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = { Text("移除插件") },
                text = { Text("移除后会删除插件包文件，并取消正在等待的插件会话。") },
                confirmButton = {
                    Button(onClick = {
                        showRemoveConfirm = false
                        onRemove()
                    }) {
                        Text("移除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun InstallPreviewDialog(
    preview: PluginInstallPreview,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val manifest = preview.manifest
    val canInstall = preview.checksumVerified
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装插件") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                DetailRow("版本", "v${manifest.version}")
                DetailRow("插件 ID", manifest.id)
                DetailRow("发布者", manifest.publisher.ifBlank { "未声明" })
                DetailRow("API", manifest.apiVersion.toString())
                DetailRow("入口", manifest.entry)
                DetailRow("摘要", if (preview.checksumVerified) "通过" else "未通过")
                DetailRow(
                    "权限",
                    manifest.permissions.joinToString { it.id }.ifBlank { "无" },
                )
                if (!canInstall) {
                    Text(
                        text = "摘要未通过，不能安装。",
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
                Text(if (isLoading) "安装中" else "安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
    val label: String,
    val icon: ImageVector,
) {
    Plugins("插件", Icons.Rounded.Extension),
    Components("组件", Icons.Rounded.Widgets),
}

private fun installedPluginKey(plugin: InstalledPluginRecord): String =
    plugin.installKey

private fun componentRequirementText(component: PluginComponentRequirement): String = buildString {
    append(component.id)
    append(" / ")
    append(component.type)
    append(if (component.required) " / 必需" else " / 可选")
    component.version?.let { append(" / v").append(it) }
    component.abi?.let { append(" / ").append(it) }
}

private fun Context.readContentBytes(uri: Uri): ByteArray {
    return contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("无法读取文件内容")
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
