package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled
import com.x500x.cursimple.core.plugin.install.pluginCompatibilityText
import com.x500x.cursimple.core.plugin.install.resolvePluginCompatibility
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

@Composable
fun PluginMarketRoute(
    pluginMarketViewModel: PluginMarketViewModel,
    pluginSources: List<String>,
    componentSources: List<String>,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    missingComponents: List<PluginComponentRequirement>,
    pendingWebSession: WebSessionRequest?,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
    /** 课表那边的同步进度与结果（正在打开登录页、导入成功、失败原因）。 */
    syncStatusMessage: String? = null,
    /** 扩展组件的设置面板要用；为空时扩展组件按普通插件详情显示 */
    extensionActions: com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions? = null,
    /** 从侧边栏日历页点「设置」过来时，直接打开这个组件的设置面板 */
    openExtensionPluginId: String? = null,
    onExtensionOpenConsumed: () -> Unit = {},
    /** 账号身份和刷新版本组成的标识；不要传访问令牌。 */
    accountKey: String? = null,
) {
    val context = LocalContext.current
    val pluginUiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
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

    LaunchedEffect(openExtensionPluginId) {
        if (openExtensionPluginId != null) selectedTab = PluginPlatformTab.Extensions
    }

    LaunchedEffect(pluginSources, componentSources, accountKey) {
        pluginMarketViewModel.setSources(pluginSources, componentSources)
        pluginMarketViewModel.onAccountChanged(accountKey)
        pluginMarketViewModel.refreshIfStale(MARKET_CACHE_TTL_MILLIS)
        pluginMarketViewModel.refreshInstalledPluginVersions()
    }

    PluginMarketScreen(
        uiState = pluginUiState,
        // 兼容旧页面状态：曾选中运行环境时回到插件页。
        selectedTab = selectedTab.takeIf { it in PluginPlatformTab.visibleTabs } ?: PluginPlatformTab.Plugins,
        enabledPluginIds = enabledPluginIds,
        syncingPluginId = syncingPluginId,
        syncStatusMessage = syncStatusMessage,
        missingComponents = missingComponents,
        pendingWebSession = pendingWebSession,
        pluginSources = pluginSources,
        componentSources = componentSources,
        onSelectTab = { selectedTab = it },
        onPickLocalPlugin = { pluginPackageLauncher.launch(PACKAGE_MIME_TYPES) },
        onRefreshMarket = { pluginMarketViewModel.loadRegistry() },
        onOpenRepo = { url -> context.openExternalUrl(url) },
        onInstallFromGitHub = pluginMarketViewModel::installFromGitHub,
        onConfirmInstall = pluginMarketViewModel::confirmInstall,
        onDismissInstallPreview = pluginMarketViewModel::dismissInstallPreview,
        onRemovePlugin = { installKey ->
            // 移除插件时一并清掉它在网页登录里存过的密码
            pluginUiState.installedPlugins.firstOrNull { it.installKey == installKey }?.let { record ->
                WebLoginCredentialStore(context).clear(record.pluginId)
            }
            pluginMarketViewModel.removePlugin(installKey)
        },
        onSetPluginEnabled = onSetPluginEnabled,
        // 同步课表前先查插件有没有新版，有新版就先升级；放行后由下面的 PluginUpgradeGate 真正发起同步
        onSyncPlugin = { installKey ->
            pluginUiState.installedPlugins.firstOrNull { it.installKey == installKey }
                ?.let(pluginMarketViewModel::syncWithUpdateCheck)
                ?: onSyncPlugin(installKey)
        },
        onUpgradePlugin = pluginMarketViewModel::upgradeThenSync,
        onCompleteWebSession = onCompleteWebSession,
        onCancelWebSession = onCancelWebSession,
        extensionActions = extensionActions,
        openExtensionPluginId = openExtensionPluginId,
        onExtensionOpenConsumed = onExtensionOpenConsumed,
        modifier = modifier,
    )
    PluginUpgradeGate(uiState = pluginUiState, viewModel = pluginMarketViewModel, onSyncPlugin = onSyncPlugin)
}

@Composable
internal fun PluginMarketScreen(
    uiState: PluginMarketUiState,
    selectedTab: PluginPlatformTab,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    missingComponents: List<PluginComponentRequirement>,
    pendingWebSession: WebSessionRequest?,
    pluginSources: List<String>,
    componentSources: List<String>,
    syncStatusMessage: String?,
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
    onUpgradePlugin: (InstalledPluginRecord, GitHubRepoSummary) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    extensionActions: com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions?,
    openExtensionPluginId: String?,
    onExtensionOpenConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogState = rememberSaveableStateHolder()
    var detailVisible by remember(selectedTab) { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<GitHubRepoSummary?>(null) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!detailVisible) PluginPlatformTabs(
                selected = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            catalogState.SaveableStateProvider(selectedTab.name) {
                val extensionMode = selectedTab == PluginPlatformTab.Extensions
                PluginListContent(
                    uiState = uiState,
                    enabledPluginIds = enabledPluginIds,
                    syncingPluginId = syncingPluginId,
                    syncStatusMessage = syncStatusMessage,
                    missingComponents = if (extensionMode) emptyList() else missingComponents,
                    pluginRegistryRepo = if (extensionMode) componentSources.firstOrNull().orEmpty()
                        else pluginSources.firstOrNull().orEmpty(),
                    extensionMode = extensionMode,
                    onPickLocalPlugin = onPickLocalPlugin,
                    onRefreshMarket = onRefreshMarket,
                    onOpenRepo = onOpenRepo,
                    onInstallFromGitHub = { repo -> downloadTarget = repo; onInstallFromGitHub(repo) },
                    onRemovePlugin = onRemovePlugin,
                    onSetPluginEnabled = onSetPluginEnabled,
                    onSyncPlugin = onSyncPlugin,
                    onUpgradePlugin = { record, repo -> downloadTarget = repo; onUpgradePlugin(record, repo) },
                    extensionActions = extensionActions,
                    openExtensionPluginId = openExtensionPluginId,
                    onExtensionOpenConsumed = onExtensionOpenConsumed,
                    onDetailVisibilityChange = { detailVisible = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        uiState.installPreview?.let { preview ->
            InstallPreviewDialog(
                preview = preview,
                origin = uiState.installPreviewOrigin,
                packageSizeBytes = uiState.packageSizeBytes,
                isLoading = uiState.isLoading,
                onDismiss = onDismissInstallPreview,
                onConfirm = onConfirmInstall,
            )
        }

        val upgradingRecord = uiState.installedPlugins.firstOrNull { it.installKey == uiState.upgradingKey }
        MarketDownloadOverlay(
            downloading = uiState.isLoading && uiState.status is PluginMarketStatus.DownloadingAsset,
            progress = uiState.downloadProgress,
            displayName = upgradingRecord?.name ?: downloadTarget?.let { it.detailDisplayTitle() },
            extensionMode = upgradingRecord?.isExtension ?: downloadTarget?.isExtension
                ?: (selectedTab == PluginPlatformTab.Extensions),
            onCancel = onDismissInstallPreview,
        )

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
internal fun PluginListContent(
    uiState: PluginMarketUiState,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    missingComponents: List<PluginComponentRequirement>,
    pluginRegistryRepo: String,
    /** true 时是「组件」标签页：只列扩展组件；false 时只列学校系统插件 */
    extensionMode: Boolean,
    onPickLocalPlugin: () -> Unit,
    onRefreshMarket: () -> Unit,
    onOpenRepo: (String) -> Unit,
    onInstallFromGitHub: (GitHubRepoSummary) -> Unit,
    onRemovePlugin: (String) -> Unit,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onUpgradePlugin: (InstalledPluginRecord, GitHubRepoSummary) -> Unit,
    extensionActions: com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions?,
    openExtensionPluginId: String?,
    onExtensionOpenConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    onDetailVisibilityChange: (Boolean) -> Unit = {},
) {
    var detailPluginKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 两个标签页共用这份列表：插件页只看学校系统，组件页只看扩展组件
    val shownInstalled = uiState.installedPlugins.filter { it.isExtension == extensionMode }
    val catalogRepos = (uiState.allMarketRepos + uiState.marketRepos + uiState.componentRepos)
        .distinctBy { it.fullName.lowercase() }
    val shownRepos = catalogRepos.filter { it.isExtension == extensionMode }
    LaunchedEffect(openExtensionPluginId, uiState.installedPlugins, enabledPluginIds) {
        if (!extensionMode) return@LaunchedEffect
        val target = openExtensionPluginId ?: return@LaunchedEffect
        val record = uiState.installedPlugins.firstOrNull { it.installKey == target && it.isExtension }
            ?: uiState.installedPlugins.filter { it.pluginId == target && it.isExtension }
                .let { matches -> matches.filter { com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled(it, enabledPluginIds, uiState.installedPlugins) }.ifEmpty { matches } }
                .maxByOrNull { it.versionCode }
            ?: return@LaunchedEffect
        detailPluginKey = installedPluginKey(record)
        onExtensionOpenConsumed()
    }
    var detailRepoSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogTab by rememberSaveable { mutableStateOf(MarketCatalogTab.Installed) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredRepos = remember(shownRepos, query) { filterMarketRepos(shownRepos, query) }
    val filteredInstalled = remember(shownInstalled, shownRepos, query) {
        filterInstalledPlugins(shownInstalled, shownRepos, query)
    }
    val detailPlugin = detailPluginKey?.let { key ->
        uiState.installedPlugins.firstOrNull { installedPluginKey(it) == key }
    }
    val detailRepo = detailRepoSlug?.let { slug -> catalogRepos.firstOrNull { it.fullName == slug } }
    SideEffect { onDetailVisibilityChange(detailPlugin != null || detailRepo != null) }

    // 详情页要先退回列表，否则系统返回键会一路退出应用
    androidx.activity.compose.BackHandler(
        enabled = detailPluginKey != null || detailRepoSlug != null,
    ) {
        detailPluginKey = null
        detailRepoSlug = null
    }

    // 扩展组件点开就是它的设置面板：登录、同步、提醒方式都在那里
    if (detailPlugin != null && detailPlugin.isExtension && extensionActions != null) {
        com.x500x.cursimple.feature.plugin.extension.ExtensionSettingsScreen(
            record = detailPlugin,
            actions = extensionActions,
            onBack = { detailPluginKey = null },
            onRemove = {
                onRemovePlugin(detailPlugin.installKey)
                detailPluginKey = null
            },
            modifier = modifier,
        )
        return
    }
    if (detailPlugin != null) {
        PluginDetailScreen(
            plugin = detailPlugin,
            repo = catalogRepos.firstOrNull { it.fullName.equals(detailPlugin.sourceRepo?.trim(), ignoreCase = true) },
            registrySource = detailPlugin.registrySourceFor(catalogRepos, pluginRegistryRepo),
            isEnabled = isPluginInstallEnabled(detailPlugin, enabledPluginIds, uiState.installedPlugins),
            isSyncing = syncingPluginId == detailPlugin.pluginId || syncingPluginId == detailPlugin.installKey ||
                uiState.checkingUpdateKey == detailPlugin.installKey || uiState.upgradingKey == detailPlugin.installKey,
            upgrade = availableUpgrade(detailPlugin, uiState.copy(marketRepos = catalogRepos)),
            onBack = { detailPluginKey = null },
            onSetEnabled = { onSetPluginEnabled(detailPlugin.installKey, it) },
            onSync = { onSyncPlugin(detailPlugin.installKey) },
            onUpgrade = { latest ->
                if (detailPlugin.isExtension || !isPluginInstallEnabled(detailPlugin, enabledPluginIds, uiState.installedPlugins)) {
                    onInstallFromGitHub(latest)
                } else onUpgradePlugin(detailPlugin, latest)
            },
            onOpenRepo = onOpenRepo,
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
            registryRepo = pluginRegistryRepo,
            onBack = { detailRepoSlug = null },
            onOpenRepo = { onOpenRepo(detailRepo.htmlUrl) },
            onInstall = { onInstallFromGitHub(detailRepo) },
            onUninstall = onRemovePlugin,
            modifier = modifier,
        )
        return
    }


    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("catalog-list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "controls") {
            MarketCatalogControls(
                tab = catalogTab,
                onSelectTab = { catalogTab = it },
                query = query,
                onQueryChange = { query = it },
                extensionMode = extensionMode,
                isLoading = uiState.isLoading,
                isRefreshingReleases = uiState.isRefreshingReleases,
                onImport = onPickLocalPlugin,
                onRefresh = onRefreshMarket,
            )
        }
        if (missingComponents.isNotEmpty()) {
            item {
                MissingComponentsCard(
                    components = missingComponents,
                )
            }
        }

        // 「已加载 N 个插件」这种流水账不常驻；但进行中的步骤和失败原因必须看得见，
        // 以前这里一并删掉了，查新版、升级、下载失败、导课失败在插件页上全都没有声音
        val busyText = when {
            uiState.checkingUpdateKey != null || uiState.upgradingKey != null ->
                uiState.status?.let { context.pluginMarketStatusText(it) }
            syncingPluginId != null -> syncStatusMessage?.takeIf { it.isNotBlank() }
            else -> null
        }
        val failureText = uiState.status?.takeIf {
            it is PluginMarketStatus.DownloadFailed ||
                it is PluginMarketStatus.InstallFailed ||
                it is PluginMarketStatus.ParsePackageFailed ||
                (it is PluginMarketStatus.MarketLoadFailed && uiState.sourceErrors.isEmpty()) ||
                it is PluginMarketStatus.ReleaseAssetMissing
        }?.let { context.pluginMarketStatusText(it) }
        when {
            busyText != null -> item(key = "busy") { SchoolImportProgressCard(text = busyText) }
            failureText != null -> item(key = "failure") { StatusCard(message = failureText) }
        }

        val sourceKind = if (extensionMode) MarketSourceKind.Component else MarketSourceKind.Plugin
        items(uiState.sourceErrors.filter { it.kind == sourceKind }, key = { "source-error:${it.kind}:${it.source}" }) { failure ->
            MarketSourceErrorCard(failure, uiState.isLoading, onRefreshMarket)
        }

        val marketSelected = catalogTab == MarketCatalogTab.Market
        item(key = "count") {
            Text(
                text = pluralStringResource(
                    if (marketSelected && extensionMode) R.plurals.extension_search_count
                    else if (marketSelected) R.plurals.plugin_market_search_count
                    else R.plurals.plugin_catalog_installed_count,
                    if (marketSelected) filteredRepos.size else filteredInstalled.size,
                    if (marketSelected) filteredRepos.size else filteredInstalled.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val empty = if (marketSelected) filteredRepos.isEmpty() else filteredInstalled.isEmpty()
        if (empty) {
            item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmptyStateCard(
                        title = when {
                            query.isNotBlank() -> stringResource(
                                if (extensionMode) R.string.extension_search_empty else R.string.plugin_market_search_empty,
                                query.trim(),
                            )
                            marketSelected && uiState.isLoading -> stringResource(R.string.plugin_market_loading_title)
                            marketSelected && extensionMode -> stringResource(R.string.extension_market_empty_title)
                            marketSelected -> stringResource(R.string.plugin_market_empty_title)
                            extensionMode -> stringResource(R.string.extension_installed_empty_title)
                            else -> stringResource(R.string.plugin_market_installed_empty_title)
                        },
                        subtitle = when {
                            query.isNotBlank() -> stringResource(R.string.plugin_catalog_search_empty_hint)
                            marketSelected && uiState.isLoading -> stringResource(R.string.plugin_market_loading_subtitle)
                            marketSelected && extensionMode -> stringResource(R.string.extension_market_empty_subtitle)
                            marketSelected -> stringResource(R.string.plugin_market_empty_subtitle)
                            extensionMode -> stringResource(R.string.extension_installed_empty_subtitle)
                            else -> stringResource(R.string.plugin_market_installed_empty_subtitle)
                        },
                    )
                    if (!marketSelected && query.isBlank()) {
                        Button(onClick = { catalogTab = MarketCatalogTab.Market }) {
                            Text(stringResource(R.string.plugin_catalog_browse_market))
                        }
                    }
                }
            }
        } else if (marketSelected) {
            items(filteredRepos, key = { "repo:${it.fullName}" }) { repo ->
                GitHubRepoCard(
                    repo = repo,
                    registryRepo = pluginRegistryRepo,
                    installState = resolveRepoInstallState(repo.fullName, repo.latestRelease?.tagName, uiState.installedPlugins),
                    isLoading = uiState.isLoading,
                    isRefreshingReleases = uiState.isRefreshingReleases,
                    onInstall = { onInstallFromGitHub(repo) },
                    onOpenDetail = { detailRepoSlug = repo.fullName },
                )
            }
        } else {
            items(filteredInstalled, key = { "installed:${installedPluginKey(it)}" }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    registrySource = plugin.registrySourceFor(shownRepos, pluginRegistryRepo),
                    isEnabled = isPluginInstallEnabled(plugin, enabledPluginIds, uiState.installedPlugins),
                    isSyncing = syncingPluginId == plugin.pluginId || syncingPluginId == plugin.installKey ||
                        uiState.checkingUpdateKey == plugin.installKey || uiState.upgradingKey == plugin.installKey,
                    isLoading = uiState.isLoading,
                    upgrade = availableUpgrade(plugin, uiState.copy(marketRepos = catalogRepos)),
                    onSetEnabled = { onSetPluginEnabled(plugin.installKey, it) },
                    onSync = { onSyncPlugin(plugin.installKey) },
                    onUpgrade = { latest -> onUpgradePlugin(plugin, latest) },
                    onInstallUpgrade = { latest -> onInstallFromGitHub(latest) },
                    onOpenDetail = { detailPluginKey = installedPluginKey(plugin) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubRepoCard(
    repo: GitHubRepoSummary,
    registryRepo: String,
    installState: PluginRepoInstallState,
    isLoading: Boolean,
    isRefreshingReleases: Boolean,
    onInstall: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    MarketItemSurface(Modifier.testTag("repo:${repo.fullName}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OwnerAvatar(owner = repo.owner, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = repo.displayTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionPill(repo.latestRelease?.tagName)
            InstallStatePill(installState)
        }
        MarketSourceLabel(repo.marketSource(registryRepo))
        Text(
            text = repo.description.ifBlank { stringResource(R.string.plugin_market_repo_no_description) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RepoInstallButton(repo, installState, isLoading, isRefreshingReleases, onInstall)
            AppOutlinedButton(onClick = onOpenDetail) { Text(stringResource(R.string.plugin_catalog_details)) }
        }
    }
}

@Composable
private fun RepoInstallButton(
    repo: GitHubRepoSummary,
    installState: PluginRepoInstallState,
    isLoading: Boolean,
    isRefreshingReleases: Boolean,
    onInstall: () -> Unit,
) {
    val hasRelease = repo.latestRelease?.tagName?.isNotBlank() == true
    Button(
        onClick = onInstall,
        enabled = hasRelease && !isLoading && installState !is PluginRepoInstallState.Installed,
        modifier = Modifier.testTag("install:${repo.fullName}"),
    ) {
        Text(
            when {
                isLoading -> stringResource(R.string.plugin_repo_action_processing)
                installState is PluginRepoInstallState.Installed -> stringResource(R.string.plugin_repo_state_installed)
                !hasRelease && isRefreshingReleases -> stringResource(R.string.plugin_catalog_loading_version)
                !hasRelease -> stringResource(R.string.plugin_market_version_missing)
                installState is PluginRepoInstallState.Updatable -> stringResource(
                    R.string.plugin_repo_action_update, displayVersion(installState.latestTag),
                )
                else -> stringResource(R.string.plugin_repo_action_install, displayVersion(repo.latestRelease!!.tagName))
            },
        )
    }
}

@Composable
private fun VersionPill(tag: String?) {
    val text = tag?.takeIf { it.isNotBlank() }?.let(::displayVersion) ?: stringResource(R.string.plugin_market_version_missing)
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

/** 已装包名和学校名优先；英文回退标题把分隔符整理为空格，仓库标识另行展示。 */
private fun GitHubRepoSummary.detailDisplayTitle(): String =
    (if (isExtension) displayTitle else schoolDisplayTitle()).replace('_', ' ').replace('-', ' ')

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubRepoDetailScreen(
    repo: GitHubRepoSummary,
    installState: PluginRepoInstallState,
    isLoading: Boolean,
    registryRepo: String,
    onBack: () -> Unit,
    onOpenRepo: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag("market-detail")) {
        MarketDetailTopBar(
            title = stringResource(if (repo.isExtension) R.string.extension_detail_title else R.string.plugin_detail_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("detail-list"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "identity") {
                MarketItemSurface {
                    MarketDetailHeading(installState.installedRecord?.name ?: repo.detailDisplayTitle(), Modifier.testTag("detail-name"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VersionPill(repo.latestRelease?.tagName)
                        InstallStatePill(installState)
                    }
                    MarketSourceLabel(repo.marketSource(registryRepo))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepoInstallButton(repo, installState, isLoading, false, onInstall)
                        installState.installedRecord?.let { installed ->
                            AppOutlinedButton(onClick = { onUninstall(installed.installKey) }, enabled = !isLoading) {
                                Text(stringResource(R.string.plugin_repo_action_uninstall))
                            }
                        }
                    }
                }
            }
            item(key = "description") {
                DetailSection(stringResource(R.string.plugin_detail_intro)) {
                    Text(
                        text = repo.description.ifBlank { stringResource(R.string.plugin_detail_no_description) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "repository") {
                DetailSection(stringResource(R.string.plugin_repo_section_repository)) {
                    MarketInfoBlock(
                        stringResource(R.string.plugin_repo_field_full_name), repo.fullName,
                        singleLine = true, valueModifier = Modifier.testTag("detail-repository-name"),
                    )
                    MarketInfoBlock(stringResource(R.string.plugin_detail_field_publisher), repo.owner, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MarketStatusBadge(stringResource(R.string.plugin_catalog_stars, repo.stars))
                        repo.language?.takeIf { it.isNotBlank() }?.let { MarketStatusBadge(it) }
                    }
                    repo.homepageUrl?.takeIf { it.isNotBlank() }?.let {
                        MarketInfoBlock(stringResource(R.string.plugin_repo_field_homepage), it, singleLine = true)
                    }
                    repo.updatedAt?.let { DetailRow(stringResource(R.string.plugin_repo_field_updated), it) }
                    if (!repo.isFresh) {
                        Text(stringResource(R.string.plugin_repo_stale_notice), style = MaterialTheme.typography.bodySmall)
                    }
                    AppOutlinedButton(onClick = onOpenRepo) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.plugin_repo_action_open_github))
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
        PluginPlatformTab.visibleTabs.forEach { tab ->
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
    val tabModifier = modifier.testTag("platform-${tab.name.lowercase()}")
        .semantics { this.selected = selected }
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(tab.labelRes))
    }
    if (selected) Button(onClick = onClick, modifier = tabModifier, content = content)
    else AppOutlinedButton(onClick = onClick, modifier = tabModifier, content = content)
}

@Composable
private fun MissingComponentsCard(
    components: List<PluginComponentRequirement>,
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
            Text(
                text = stringResource(R.string.plugin_market_runtime_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCard(
    plugin: InstalledPluginRecord,
    registrySource: String?,
    isEnabled: Boolean,
    isSyncing: Boolean,
    isLoading: Boolean,
    upgrade: GitHubRepoSummary?,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onUpgrade: (GitHubRepoSummary) -> Unit,
    onInstallUpgrade: (GitHubRepoSummary) -> Unit,
    onOpenDetail: () -> Unit,
) {
    MarketItemSurface(Modifier.testTag("installed:${plugin.installKey}")) {
        Text(
            text = plugin.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionPill(displayVersion(plugin.version))
            MarketStatusBadge(stringResource(if (isEnabled) R.string.plugin_badge_enabled else R.string.plugin_catalog_disabled))
            if (upgrade != null) MarketStatusBadge(stringResource(R.string.plugin_repo_state_update), attention = true)
            if (plugin.compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
                MarketStatusBadge(stringResource(R.string.plugin_catalog_incompatible), attention = true)
            }
        }
        val source = registrySource ?: plugin.sourceRepo?.takeIf { it.isNotBlank() } ?: stringResource(
            when (plugin.source) {
                PluginInstallSource.Local -> R.string.plugin_install_origin_local
                PluginInstallSource.Bundled -> R.string.plugin_install_origin_bundled
                PluginInstallSource.Remote -> R.string.plugin_install_origin_remote
            },
        )
        MarketSourceLabel(source)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (plugin.isExtension) {
                Button(onClick = onOpenDetail) { Text(stringResource(R.string.plugin_card_action_extension_settings)) }
            } else if (isEnabled) {
                PluginSyncOrUpgradeButton(plugin, isSyncing || isLoading, upgrade, onSync, onUpgrade)
            } else {
                AppOutlinedButton(onClick = onOpenDetail) { Text(stringResource(R.string.plugin_catalog_details)) }
            }
            // 组件更新只走安装预览；不能走学校插件的升级后导课流程。
            if (upgrade != null && (plugin.isExtension || !isEnabled)) {
                Button(onClick = { onInstallUpgrade(upgrade) }, enabled = !isLoading && !isSyncing) {
                    Text(stringResource(R.string.plugin_repo_action_update, displayVersion(upgrade.latestRelease!!.tagName)))
                }
            }
            AppOutlinedButton(onClick = { onSetEnabled(!isEnabled) }, enabled = !isLoading && !isSyncing) {
                Text(stringResource(if (isEnabled) R.string.plugin_catalog_disable else R.string.plugin_catalog_enable))
            }
            if (!plugin.isExtension && isEnabled) {
                AppOutlinedButton(onClick = onOpenDetail) { Text(stringResource(R.string.plugin_catalog_details)) }
            }
        }
    }
}

/**
 * 同步课表按钮；市场上已知有新版时换成「升级到 vX」，旁边写明当前版本，升级装好后自动接着同步。
 */
@Composable
private fun PluginSyncOrUpgradeButton(
    plugin: InstalledPluginRecord,
    isSyncing: Boolean,
    upgrade: GitHubRepoSummary?,
    onSync: () -> Unit,
    onUpgrade: (GitHubRepoSummary) -> Unit,
) {
    val newVersion = upgrade?.latestRelease?.tagName
    if (upgrade != null && newVersion != null && !isSyncing) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    R.string.plugin_upgrade_available_line,
                    displayVersion(newVersion),
                    displayVersion(plugin.version),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Button(onClick = { onUpgrade(upgrade) }) {
                Text(stringResource(R.string.plugin_upgrade_action_version, displayVersion(newVersion)))
            }
        }
        return
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginDetailScreen(
    plugin: InstalledPluginRecord,
    repo: GitHubRepoSummary?,
    registrySource: String?,
    isEnabled: Boolean,
    isSyncing: Boolean,
    upgrade: GitHubRepoSummary?,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onUpgrade: (GitHubRepoSummary) -> Unit,
    onOpenRepo: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    var technicalExpanded by rememberSaveable(plugin.installKey) { mutableStateOf(false) }
    val context = LocalContext.current
    val credentialStore = remember { WebLoginCredentialStore(context) }
    var hasSavedPasswords by remember(plugin.pluginId) { mutableStateOf(credentialStore.hasAny(plugin.pluginId)) }
    val compatibilityMessage = plugin.compatibilityMessage?.takeIf { it.isNotBlank() }
        ?: context.pluginCompatibilityText(resolvePluginCompatibility(plugin.apiVersion))
    val sourceLabel = registrySource ?: plugin.sourceRepo?.takeIf { it.isNotBlank() } ?: stringResource(
        when (plugin.source) {
            PluginInstallSource.Local -> R.string.plugin_install_origin_local
            PluginInstallSource.Bundled -> R.string.plugin_install_origin_bundled
            PluginInstallSource.Remote -> R.string.plugin_install_origin_remote
        },
    )
    Box(modifier = modifier.fillMaxSize().testTag("installed-detail")) {
        Column(Modifier.fillMaxSize()) {
            MarketDetailTopBar(
                title = stringResource(if (plugin.isExtension) R.string.extension_detail_title else R.string.plugin_detail_title),
                onBack = onBack,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("detail-list"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "identity") {
                    MarketItemSurface {
                        MarketDetailHeading(plugin.name, Modifier.testTag("detail-name"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VersionPill(plugin.version)
                            MarketStatusBadge(stringResource(if (isEnabled) R.string.plugin_badge_enabled else R.string.plugin_catalog_disabled))
                            if (upgrade != null) MarketStatusBadge(stringResource(R.string.plugin_repo_state_update), attention = true)
                        }
                        MarketSourceLabel(sourceLabel)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isEnabled && !plugin.isExtension) {
                                PluginSyncOrUpgradeButton(plugin, isSyncing, upgrade, onSync, onUpgrade)
                            } else if (upgrade != null) {
                                Button(onClick = { onUpgrade(upgrade) }, enabled = !isSyncing) {
                                    Text(stringResource(R.string.plugin_repo_action_update, displayVersion(upgrade.latestRelease!!.tagName)))
                                }
                            }
                            AppOutlinedButton(onClick = { onSetEnabled(!isEnabled) }, enabled = !isSyncing) {
                                Text(stringResource(if (isEnabled) R.string.plugin_catalog_disable else R.string.plugin_catalog_enable))
                            }
                            AppOutlinedButton(onClick = { showRemoveConfirm = true }, enabled = !isSyncing) {
                                Text(stringResource(if (plugin.isExtension) R.string.extension_action_remove else R.string.plugin_detail_action_remove))
                            }
                        }
                        if (hasSavedPasswords) {
                            val clearedMessage = stringResource(R.string.plugin_detail_passwords_cleared)
                            AppOutlinedButton(onClick = {
                                credentialStore.clear(plugin.pluginId)
                                hasSavedPasswords = false
                                Toast.makeText(context, clearedMessage, Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.plugin_detail_action_clear_passwords)) }
                        }
                    }
                }
                item(key = "description") {
                    DetailSection(stringResource(R.string.plugin_detail_intro)) {
                        Text(
                            repo?.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.plugin_detail_no_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "repository") {
                    DetailSection(stringResource(R.string.plugin_repo_section_repository)) {
                        MarketInfoBlock(
                            stringResource(R.string.plugin_repo_field_full_name),
                            plugin.sourceRepo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.plugin_detail_no_repository),
                            singleLine = true, valueModifier = Modifier.testTag("detail-repository-name"),
                        )
                        if (plugin.publisher.isNotBlank()) {
                            MarketInfoBlock(stringResource(R.string.plugin_detail_field_publisher), plugin.publisher)
                        }
                        plugin.sourceRepo?.takeIf { it.isNotBlank() }?.let { slug ->
                            AppOutlinedButton(onClick = { onOpenRepo(repo?.htmlUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/$slug") }) {
                                Text(stringResource(R.string.plugin_repo_action_open_github))
                            }
                        }
                    }
                }
                if (plugin.compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
                    item(key = "compatibility") {
                        DetailSection(stringResource(R.string.plugin_catalog_incompatible)) {
                            Text(compatibilityMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item(key = "technical-toggle") {
                    MarketDetailsToggle(technicalExpanded, { technicalExpanded = !technicalExpanded }, Modifier.testTag("detail-technical-toggle"))
                }
                if (technicalExpanded) {
                    item(key = "technical-basic") {
                        DetailSection(stringResource(R.string.plugin_detail_section_basic)) {
                            val undeclared = stringResource(R.string.plugin_detail_value_undeclared)
                            DetailRow(stringResource(R.string.plugin_detail_field_plugin_id), plugin.pluginId)
                            DetailRow(stringResource(R.string.plugin_detail_field_api), plugin.apiVersion?.toString() ?: undeclared)
                            DetailRow(stringResource(R.string.plugin_detail_field_entry), plugin.entry.ifBlank { undeclared })
                            DetailRow(
                                stringResource(R.string.plugin_detail_field_compatibility),
                                stringResource(if (plugin.compatibilityStatus == PluginCompatibilityStatus.Compatible) R.string.plugin_catalog_compatible else R.string.plugin_catalog_incompatible),
                            )
                            compatibilityMessage?.let { DetailRow(stringResource(R.string.plugin_detail_field_compatibility_message), it) }
                        }
                    }
                    item(key = "technical-permissions") {
                        DetailSection(stringResource(R.string.plugin_detail_section_permissions)) {
                            context.pluginPermissionListText(pluginPermissionList(plugin.permissions)).forEach { label ->
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item(key = "technical-web-engine") {
                        DetailSection(stringResource(R.string.plugin_detail_section_web_engine)) {
                            DetailRow(stringResource(R.string.plugin_detail_field_preferred), plugin.webEngine.preferred)
                            DetailRow(stringResource(R.string.plugin_detail_field_allow_chromium), stringResource(if (plugin.webEngine.allowChromium) R.string.plugin_detail_value_yes else R.string.plugin_detail_value_no))
                            plugin.webEngine.chromiumComponent?.takeIf { it.isNotBlank() }?.let {
                                DetailRow(stringResource(R.string.plugin_detail_field_chromium_component), it)
                            }
                        }
                    }
                    item(key = "technical-components") {
                        DetailSection(stringResource(R.string.plugin_detail_section_components)) {
                            if (plugin.components.isEmpty()) Text(stringResource(R.string.plugin_detail_value_none))
                            else plugin.components.forEach { Text(componentRequirementText(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    item(key = "technical-hosts") {
                        DetailSection(stringResource(R.string.plugin_detail_section_allowed_hosts)) {
                            if (plugin.allowedHosts.isEmpty()) Text(stringResource(R.string.plugin_detail_value_none))
                            else plugin.allowedHosts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }

        if (showRemoveConfirm) {
            AppConfirmationDialog(
                title = stringResource(R.string.plugin_remove_dialog_title),
                message = stringResource(R.string.plugin_remove_dialog_message),
                confirmLabel = stringResource(R.string.plugin_remove_dialog_confirm),
                cancelLabel = stringResource(R.string.plugin_action_cancel),
                onConfirm = {
                    showRemoveConfirm = false
                    onRemove()
                },
                onDismiss = { showRemoveConfirm = false },
            )
        }
    }
}

@Composable
internal fun InstallPreviewDialog(
    preview: PluginInstallPreview,
    origin: PluginInstallOrigin?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    packageSizeBytes: Long? = null,
) {
    val context = LocalContext.current
    val manifest = preview.manifest
    val canInstall = canConfirmPluginInstall(preview)
    val allowedHosts = manifest.allowedHosts.filter { it.isNotBlank() }
    var technicalExpanded by rememberSaveable(manifest.id, manifest.version, origin?.repoSlug) { mutableStateOf(false) }
    val source = origin?.registrySource?.takeIf { it.isNotBlank() }
        ?: origin?.repoSlug?.takeIf { it.isNotBlank() }
    val sourceLabel = source?.let {
        if (isPublicMarketSource(it)) stringResource(R.string.plugin_catalog_public_source) else it
    } ?: context.pluginInstallOriginText(pluginInstallOriginLabel(preview.source, origin))
    val size = packageSizeBytes?.takeIf { it >= 0 } ?: origin?.sizeBytes?.takeIf { it >= 0 }
    // 高度只跟可用屏幕大小有关，展开技术信息时只增加内部滚动内容。
    val dialogHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.82f).coerceAtMost(680.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.height(dialogHeight).testTag("install-preview"),
        title = { Text(stringResource(if (manifest.isExtension) R.string.extension_install_dialog_title else R.string.plugin_install_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("preview-scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MarketDetailHeading(
                    manifest.name.ifBlank { origin?.displayName.orEmpty() },
                    Modifier.testTag("preview-name"),
                )
                VersionPill(manifest.version)
                DetailSection(stringResource(R.string.plugin_detail_intro)) {
                    Text(
                        origin?.description?.takeIf { it.isNotBlank() }
                            ?: manifest.description.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.plugin_detail_no_description),
                        modifier = Modifier.testTag("preview-description"),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MarketInfoBlock(
                    stringResource(R.string.plugin_install_section_source), sourceLabel,
                    singleLine = true, valueModifier = Modifier.testTag("preview-source"),
                )
                MarketInfoBlock(
                    stringResource(R.string.plugin_install_field_size),
                    size?.let { Formatter.formatShortFileSize(context, it) } ?: stringResource(R.string.plugin_install_size_unknown),
                    valueModifier = Modifier.testTag("preview-size"),
                )
                // 阻止安装的原因始终展示，折叠权限与校验不会改变确认按钮的校验条件。
                pluginInstallBlockReason(preview)?.let { reason ->
                    Text(
                        context.pluginInstallBlockReasonText(reason),
                        modifier = Modifier.testTag("preview-block-reason"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                MarketDetailsToggle(
                    technicalExpanded, { technicalExpanded = !technicalExpanded },
                    Modifier.testTag("preview-technical-toggle"),
                )
                if (technicalExpanded) {
                    Column(
                        modifier = Modifier.testTag("preview-technical-content"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailSection(stringResource(R.string.plugin_install_section_permissions)) {
                            context.pluginPermissionListText(pluginPermissionList(manifest.permissions)).forEach { label ->
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(stringResource(R.string.plugin_install_permission_scope_note), style = MaterialTheme.typography.bodySmall)
                        }
                        DetailSection(stringResource(R.string.plugin_install_section_allowed_hosts)) {
                            if (allowedHosts.isEmpty()) Text(stringResource(R.string.plugin_install_allowed_hosts_empty))
                            else allowedHosts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                        DetailSection(stringResource(R.string.plugin_install_section_integrity)) {
                            DetailRow(stringResource(R.string.plugin_install_field_checksum), stringResource(pluginChecksumLabelRes(preview.checksumVerified)))
                            DetailRow(
                                stringResource(R.string.plugin_install_field_signature),
                                context.pluginSignatureText(pluginSignatureLabel(preview.signatureStatus, preview.signerFingerprint)),
                            )
                            Text(stringResource(R.string.plugin_install_integrity_trust_note), style = MaterialTheme.typography.bodySmall)
                        }
                        DetailSection(stringResource(R.string.plugin_detail_section_basic)) {
                            val undeclared = stringResource(R.string.plugin_detail_value_undeclared)
                            DetailRow(stringResource(R.string.plugin_detail_field_plugin_id), manifest.id)
                            DetailRow(stringResource(R.string.plugin_detail_field_publisher), manifest.publisher.ifBlank { undeclared })
                            DetailRow(stringResource(R.string.plugin_detail_field_api), manifest.apiVersion?.toString() ?: undeclared)
                            DetailRow(stringResource(R.string.plugin_detail_field_entry), manifest.entry)
                        }
                        origin?.let {
                            DetailSection(stringResource(R.string.plugin_repo_section_repository)) {
                                MarketInfoBlock(stringResource(R.string.plugin_repo_field_full_name), it.repoSlug, singleLine = true)
                                MarketInfoBlock(stringResource(R.string.plugin_install_field_download_url), it.downloadUrl, singleLine = true)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = canInstall && !isLoading, modifier = Modifier.testTag("preview-confirm")) {
                Text(stringResource(if (isLoading) R.string.plugin_install_action_installing else R.string.plugin_install_action_install))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("preview-cancel")) {
                Text(stringResource(R.string.plugin_action_cancel))
            }
        },
    )
}

@Composable
internal fun WebSessionOverlay(
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
    MarketItemSurface {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    MarketInfoBlock(label, value)
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

internal enum class PluginPlatformTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Plugins(R.string.plugin_market_tab_plugins, Icons.Rounded.Extension),
    /** 通知类的扩展组件 */
    Extensions(R.string.plugin_market_tab_extensions, Icons.Rounded.Widgets),
    /** 仅兼容旧版保存的页面状态；运行环境入口暂不开放。 */
    Components(R.string.plugin_market_tab_components, Icons.Rounded.Memory);

    companion object {
        val visibleTabs = listOf(Plugins, Extensions)
    }
}

internal fun installedPluginKey(plugin: InstalledPluginRecord): String =
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

internal const val MARKET_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L

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
        PluginRepoInstallState.NotInstalled -> R.string.plugin_catalog_not_installed
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
}

/** 详情页左上角的返回，带边框以便和旁边的标题区分开。 */
@Composable
private fun DetailBackButton(onBack: () -> Unit) {
    AppOutlinedButton(
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


/** 包里 manifest.json 带 entry / apiVersion 的是插件包（学校插件或扩展组件），不是运行环境包 */
private fun looksLikePluginPackage(bytes: ByteArray): Boolean = runCatching {
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }
            .firstOrNull { it.name == "manifest.json" }
            ?.let {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                "\"apiVersion\"" in text || "\"entry\"" in text
            } ?: false
    }
}.getOrDefault(false)
