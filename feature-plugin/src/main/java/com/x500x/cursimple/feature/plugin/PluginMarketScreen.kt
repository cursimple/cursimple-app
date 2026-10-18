package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppMotion
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
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
    /** Schedule-sync progress and final result from the timetable workflow. */
    syncStatusMessage: String? = null,
    extensionActions: com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions? = null,
    openExtensionPluginId: String? = null,
    onExtensionOpenConsumed: () -> Unit = {},
    /** Opens the component tab once, e.g. from the components page in the bottom bar. */
    openExtensionsTab: Boolean = false,
    onExtensionsTabConsumed: () -> Unit = {},
    /** Account identity and refresh generation only; never pass access tokens. */
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
    LaunchedEffect(openExtensionsTab) {
        if (openExtensionsTab) {
            selectedTab = PluginPlatformTab.Extensions
            onExtensionsTabConsumed()
        }
    }

    // Refresh on entry and kind changes; hide versions while checking.
    LaunchedEffect(pluginSources, componentSources, accountKey, selectedTab) {
        pluginMarketViewModel.setSources(pluginSources, componentSources)
        pluginMarketViewModel.onAccountChanged(accountKey)
        pluginMarketViewModel.refreshOnEnter()
    }

    PluginMarketScreen(
        uiState = pluginUiState,
        // Map legacy runtime-selection state back to the plugin page.
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
        onRefreshMarket = { pluginMarketViewModel.loadRegistry(); pluginMarketViewModel.refreshInstalledPluginVersions(force = true) },
        onOpenRepo = { url -> context.openExternalUrl(url) },
        onInstallFromGitHub = pluginMarketViewModel::installFromGitHub,
        onConfirmInstall = pluginMarketViewModel::confirmInstall,
        onDismissInstallPreview = pluginMarketViewModel::dismissInstallPreview,
        onRemovePlugin = { installKey ->
            pluginUiState.installedPlugins.firstOrNull { it.installKey == installKey }?.let { record ->
                WebLoginCredentialStore(context).clear(record.pluginId)
            }
            pluginMarketViewModel.removePlugin(installKey)
        },
        onSetPluginEnabled = onSetPluginEnabled,
        // Check for an upgrade before importing; [PluginUpgradeGate] continues sync after approval.
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
        onUpdateOptions = { auto, badge, hours -> pluginMarketViewModel.setUpdateOptions(auto, badge, hours) },
        onCheckUpdates = { pluginMarketViewModel.refreshInstalledPluginVersions(force = true) },
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
    onUpdateOptions: (Boolean, Boolean, Int) -> Unit = { _, _, _ -> },
    onCheckUpdates: () -> Unit = {},
) {
    val catalogState = rememberSaveableStateHolder()
    var detailVisible by remember(selectedTab) { mutableStateOf(false) }
    var updateSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<GitHubRepoSummary?>(null) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!detailVisible) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PluginPlatformTabs(selected = selectedTab, onSelect = onSelectTab,
                    updateKeys = if (uiState.showUpdateBadge) uiState.availableUpdateKeys() else emptySet(),
                    installed = uiState.installedPlugins, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                AppOutlinedButton(
                    onClick = { updateSettingsVisible = true },
                    modifier = Modifier.size(48.dp).testTag("plugin-update-settings"),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.plugin_update_settings_title), modifier = Modifier.size(22.dp))
                }
            }
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    AppMotion.sharedAxisX(forward)
                },
                modifier = Modifier.weight(1f),
                label = "PluginPlatformTabTransition",
            ) { tab ->
                catalogState.SaveableStateProvider(tab.name) {
                    val extensionMode = tab == PluginPlatformTab.Extensions
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (updateSettingsVisible) PluginUpdateSettingsSheet(uiState, onUpdateOptions, onCheckUpdates,
            onDismiss = { updateSettingsVisible = false })

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

private sealed interface PluginCatalogViewTarget {
    data object List : PluginCatalogViewTarget
    data class InstalledDetail(val plugin: InstalledPluginRecord) : PluginCatalogViewTarget
    data class RepoDetail(val repo: GitHubRepoSummary) : PluginCatalogViewTarget
    data class PluginScheduleSettings(val plugin: InstalledPluginRecord) : PluginCatalogViewTarget
    data class ExtensionSettings(val plugin: InstalledPluginRecord) : PluginCatalogViewTarget

    val rank: Int
        get() = when (this) {
            List -> 0
            is InstalledDetail, is RepoDetail -> 1
            is PluginScheduleSettings, is ExtensionSettings -> 2
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
    /** True filters extension components; false filters school schedule plugins. */
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
    // Keep host-owned installation details separate from component-owned settings.
    var settingsPluginKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Share the list while filtering by plugin kind.
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
        settingsPluginKey = installedPluginKey(record)
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
    val settingsPlugin = settingsPluginKey?.let { key ->
        uiState.installedPlugins.firstOrNull { installedPluginKey(it) == key }
    }
    val detailRepo = detailRepoSlug?.let { slug -> catalogRepos.firstOrNull { it.fullName == slug } }
    SideEffect { onDetailVisibilityChange(detailPlugin != null || detailRepo != null || settingsPlugin != null) }

    // Back from settings returns one level; Back from details returns to the list.
    androidx.activity.compose.BackHandler(
        enabled = settingsPluginKey != null || detailPluginKey != null || detailRepoSlug != null,
    ) {
        if (settingsPluginKey != null) {
            settingsPluginKey = null
        } else {
            detailPluginKey = null
            detailRepoSlug = null
        }
    }

    val currentTarget = when {
        settingsPlugin != null && !settingsPlugin.isExtension -> PluginCatalogViewTarget.PluginScheduleSettings(settingsPlugin)
        settingsPlugin != null && extensionActions != null -> PluginCatalogViewTarget.ExtensionSettings(settingsPlugin)
        detailPlugin != null -> PluginCatalogViewTarget.InstalledDetail(detailPlugin)
        detailRepo != null -> PluginCatalogViewTarget.RepoDetail(detailRepo)
        else -> PluginCatalogViewTarget.List
    }

    AnimatedContent(
        targetState = currentTarget,
        transitionSpec = {
            val forward = targetState.rank >= initialState.rank
            AppMotion.sharedAxisX(forward)
        },
        modifier = modifier.fillMaxSize(),
        label = "PluginCatalogTransition",
    ) { target ->
        when (target) {
            is PluginCatalogViewTarget.PluginScheduleSettings -> {
                SchedulePluginSettingsScreen(target.plugin, onBack = { settingsPluginKey = null }, modifier = Modifier.fillMaxSize())
            }
            is PluginCatalogViewTarget.ExtensionSettings -> {
                com.x500x.cursimple.feature.plugin.extension.ExtensionSettingsScreen(
                    record = target.plugin,
                    actions = extensionActions!!,
                    onBack = { settingsPluginKey = null },
                    onRemove = {
                        onRemovePlugin(target.plugin.installKey)
                        settingsPluginKey = null
                        detailPluginKey = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is PluginCatalogViewTarget.InstalledDetail -> {
                PluginDetailScreen(
                    plugin = target.plugin,
                    repo = catalogRepos.firstOrNull { it.fullName.equals(target.plugin.sourceRepo?.trim(), ignoreCase = true) },
                    registrySource = target.plugin.registrySourceFor(catalogRepos, pluginRegistryRepo),
                    isEnabled = isPluginInstallEnabled(target.plugin, enabledPluginIds, uiState.installedPlugins),
                    isSyncing = syncingPluginId == target.plugin.pluginId || syncingPluginId == target.plugin.installKey ||
                        uiState.checkingUpdateKey == target.plugin.installKey || uiState.upgradingKey == target.plugin.installKey || uiState.processingPlugin(target.plugin),
                    upgrade = availableUpgrade(target.plugin, uiState.copy(marketRepos = catalogRepos)),
                    onBack = { detailPluginKey = null },
                    onSetEnabled = { onSetPluginEnabled(target.plugin.installKey, it) },
                    onSync = { onSyncPlugin(target.plugin.installKey) },
                    onUpgrade = { latest ->
                        if (target.plugin.isExtension || !isPluginInstallEnabled(target.plugin, enabledPluginIds, uiState.installedPlugins)) {
                            onInstallFromGitHub(latest)
                        } else onUpgradePlugin(target.plugin, latest)
                    },
                    onOpenRepo = onOpenRepo,
                    onOpenComponent = if (target.plugin.isExtension) {
                        extensionActions?.let { actions -> { actions.openFeed(target.plugin) } }
                    } else null,
                    onOpenScheduleSettings = if (!target.plugin.isExtension) ({ settingsPluginKey = installedPluginKey(target.plugin) }) else null,
                    onRemove = {
                        onRemovePlugin(target.plugin.installKey)
                        detailPluginKey = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is PluginCatalogViewTarget.RepoDetail -> {
                GitHubRepoDetailScreen(
                    repo = target.repo,
                    installState = resolveRepoInstallState(
                        repoSlug = target.repo.fullName,
                        latestTag = target.repo.latestRelease?.tagName,
                        installed = uiState.installedPlugins,
                    ),
                    isLoading = uiState.isLoading,
                    isCheckingVersions = uiState.isRefreshingReleases || uiState.versionsChecking,
                    isProcessing = uiState.processingRepo(target.repo.fullName),
                    isCheckingInstall = uiState.processingRepo(target.repo.fullName) && uiState.status is PluginMarketStatus.CheckingInstallRelease,
                    registryRepo = pluginRegistryRepo,
                    onBack = { detailRepoSlug = null },
                    onOpenRepo = { onOpenRepo(target.repo.htmlUrl) },
                    onInstall = { onInstallFromGitHub(target.repo) },
                    onUninstall = onRemovePlugin,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PluginCatalogViewTarget.List -> {
                val context = LocalContext.current
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("catalog-list"),
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
                            installedCount = shownInstalled.size,
                            marketCount = shownRepos.size,
                        )
                    }
                    if (missingComponents.isNotEmpty()) {
                        item {
                            MissingComponentsCard(
                                components = missingComponents,
                            )
                        }
                    }

                    // Keep active operations and failures visible while omitting routine loaded-count messages.
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
                        Box(appItemMotion()) {
                            MarketSourceErrorCard(failure, uiState.isLoading, onRefreshMarket)
                        }
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
                            Box(appItemMotion()) {
                                // Centered in the free space below the search and tabs.
                                Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
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
                                        icon = if (marketSelected) Icons.Rounded.Search
                                            else if (extensionMode) Icons.Rounded.Widgets
                                            else Icons.Rounded.Extension,
                                        isLoading = marketSelected && uiState.isLoading,
                                        action = if (!marketSelected && query.isBlank()) {
                                            {
                                                Button(
                                                    onClick = { catalogTab = MarketCatalogTab.Market },
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.padding(top = 4.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.plugin_catalog_browse_market), fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        } else null,
                                    )
                                }
                            }
                        }
                    } else if (marketSelected) {
                        items(filteredRepos, key = { "repo:${it.fullName}" }) { repo ->
                            Box(appItemMotion()) {
                                GitHubRepoCard(
                                    repo = repo,
                                    registryRepo = pluginRegistryRepo,
                                    installState = resolveRepoInstallState(repo.fullName, repo.latestRelease?.tagName, uiState.installedPlugins),
                                    isLoading = uiState.isLoading,
                                    isRefreshingReleases = uiState.isRefreshingReleases || uiState.versionsChecking,
                                    isProcessing = uiState.processingRepo(repo.fullName),
                                    isCheckingInstall = uiState.processingRepo(repo.fullName) && uiState.status is PluginMarketStatus.CheckingInstallRelease,
                                    onInstall = { onInstallFromGitHub(repo) },
                                    onOpenDetail = { detailRepoSlug = repo.fullName },
                                )
                            }
                        }
                    } else {
                        items(filteredInstalled, key = { "installed:${installedPluginKey(it)}" }) { plugin ->
                            Box(appItemMotion()) {
                                PluginCard(
                                    plugin = plugin,
                                    registrySource = plugin.registrySourceFor(shownRepos, pluginRegistryRepo),
                                    isEnabled = isPluginInstallEnabled(plugin, enabledPluginIds, uiState.installedPlugins),
                                    isSyncing = syncingPluginId == plugin.pluginId || syncingPluginId == plugin.installKey ||
                                        uiState.checkingUpdateKey == plugin.installKey || uiState.upgradingKey == plugin.installKey,
                                    isLoading = uiState.isLoading,
                                    isProcessing = uiState.processingPlugin(plugin),
                                    upgrade = availableUpgrade(plugin, uiState.copy(marketRepos = catalogRepos)),
                                    onSetEnabled = { onSetPluginEnabled(plugin.installKey, it) },
                                    onSync = { onSyncPlugin(plugin.installKey) },
                                    onUpgrade = { latest -> onUpgradePlugin(plugin, latest) },
                                    onInstallUpgrade = { latest -> onInstallFromGitHub(latest) },
                                    onOpenDetail = { detailPluginKey = installedPluginKey(plugin) },
                                    onOpenComponent = extensionActions?.let { actions -> { actions.openFeed(plugin) } },
                                    onOpenScheduleSettings = if (!plugin.isExtension) ({ settingsPluginKey = installedPluginKey(plugin) }) else null,
                                    onRemove = { onRemovePlugin(plugin.installKey) },
                                )
                            }
                        }
                    }
                }
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
    isProcessing: Boolean = false,
    isCheckingInstall: Boolean = false,
) {
    MarketItemSurface(Modifier.testTag("repo:${repo.fullName}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (repo.isExtension) Icons.Rounded.Widgets else Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionPill(repo.latestRelease?.tagName)
            InstallStatePill(installState)
            if (repo.stars > 0) {
                StarCountPill(repo.stars)
            }
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
            RepoInstallButton(repo, installState, isLoading, isRefreshingReleases, onInstall, isProcessing, isCheckingInstall)
            AppOutlinedButton(onClick = onOpenDetail, shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.plugin_catalog_details))
            }
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
    isProcessing: Boolean = false,
    isCheckingInstall: Boolean = false,
) {
    val hasRelease = repo.latestRelease?.tagName?.isNotBlank() == true
    Button(
        onClick = onInstall,
        enabled = hasRelease && !isLoading && installState !is PluginRepoInstallState.Installed,
        modifier = Modifier.testTag("install:${repo.fullName}"),
        shape = RoundedCornerShape(50),
    ) {
        if (installState !is PluginRepoInstallState.Installed) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            when {
                isCheckingInstall -> stringResource(R.string.plugin_repo_action_checking)
                isProcessing -> stringResource(R.string.plugin_repo_action_processing)
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
private fun StarCountPill(stars: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stars.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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

/** Prefer installed names and school aliases; normalize separators only in fallback titles. */
private fun GitHubRepoSummary.detailDisplayTitle(): String =
    (if (isExtension) displayTitle else schoolDisplayTitle()).replace('_', ' ').replace('-', ' ')

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubRepoDetailScreen(
    repo: GitHubRepoSummary,
    installState: PluginRepoInstallState,
    isLoading: Boolean,
    isCheckingVersions: Boolean,
    isProcessing: Boolean,
    isCheckingInstall: Boolean,
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
                        if (repo.stars > 0) {
                            StarCountPill(repo.stars)
                        }
                    }
                    MarketSourceLabel(repo.marketSource(registryRepo))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepoInstallButton(repo, installState, isLoading, isCheckingVersions, onInstall, isProcessing, isCheckingInstall)
                        installState.installedRecord?.let { installed ->
                            AppOutlinedButton(
                                onClick = { onUninstall(installed.installKey) },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(50),
                            ) {
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
    updateKeys: Set<String> = emptySet(),
    installed: List<InstalledPluginRecord> = emptyList(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PluginPlatformTab.visibleTabs.forEach { tab ->
                PlatformTabChip(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    hasUpdates = installed.any { it.installKey in updateKeys && it.isExtension == (tab == PluginPlatformTab.Extensions) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlatformTabChip(
    tab: PluginPlatformTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUpdates: Boolean = false,
) {
    val haptics = com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics()
    Surface(
        onClick = {
            haptics.click()
            onClick()
        },
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag("platform-${tab.name.lowercase()}")
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (hasUpdates) {
                Spacer(Modifier.width(6.dp))
                PluginUpdateDot()
            }
        }
    }
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
    isProcessing: Boolean,
    upgrade: GitHubRepoSummary?,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onUpgrade: (GitHubRepoSummary) -> Unit,
    onInstallUpgrade: (GitHubRepoSummary) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenComponent: (() -> Unit)?,
    onOpenScheduleSettings: (() -> Unit)? = null,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val declaredRows by produceState<List<com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting>>(emptyList(), plugin.packageRevision) {
        if (!plugin.isExtension) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.x500x.cursimple.core.data.validPluginTimetableRows(
                com.x500x.cursimple.core.plugin.storage.PluginFileStore(context).loadUiSchema(plugin).timetableRows) }.getOrDefault(emptyList())
        }
    }
    var showRemoveConfirm by rememberSaveable(plugin.installKey) { mutableStateOf(false) }
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
    MarketItemSurface(Modifier.testTag("installed:${plugin.installKey}")) {
        // Separate enablement switch from primary, details and removal actions.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (plugin.isExtension) Icons.Rounded.Widgets else Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                plugin.sourceRepo?.takeIf { it.isNotBlank() }?.let { sourceRepo ->
                    Text(
                        text = sourceRepo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onSetEnabled,
                enabled = !isLoading && !isSyncing,
                modifier = Modifier.testTag("toggle:${plugin.installKey}"),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionPill(displayVersion(plugin.version))
            if (!isEnabled) MarketStatusBadge(stringResource(R.string.plugin_catalog_disabled))
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
        // Component upgrades use install preview without entering school-import sync.
        if (upgrade != null && (plugin.isExtension || !isEnabled)) {
            Button(
                onClick = { onInstallUpgrade(upgrade) },
                enabled = !isLoading && !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.plugin_repo_action_update, displayVersion(upgrade.latestRelease!!.tagName)))
            }
        }
        val hasPrimary = plugin.isExtension || isEnabled
        if (declaredRows.isNotEmpty() && onOpenScheduleSettings != null) {
            AppOutlinedButton(onClick = onOpenScheduleSettings,
                modifier = Modifier.fillMaxWidth().testTag("plugin-settings:${plugin.installKey}")) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.plugin_timetable_settings_title), maxLines = 1, softWrap = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (plugin.isExtension) {
                Button(
                    onClick = { onOpenComponent?.invoke() },
                    enabled = onOpenComponent != null,
                    modifier = Modifier.weight(1f).testTag("component-open:${plugin.installKey}"),
                ) {
                    Text(stringResource(R.string.plugin_card_action_extension_open))
                }
            } else if (isEnabled) {
                Box(Modifier.weight(1f)) {
                    PluginSyncOrUpgradeButton(plugin, isSyncing || isProcessing, upgrade, onSync, onUpgrade, fill = true, blocked = isLoading)
                }
            }
            AppOutlinedButton(
                onClick = onOpenDetail,
                modifier = (if (hasPrimary) Modifier else Modifier.weight(1f)).testTag("details:${plugin.installKey}"),
            ) {
                Text(stringResource(R.string.plugin_catalog_details))
            }
            RemoveIconButton(
                label = stringResource(if (plugin.isExtension) R.string.extension_action_remove else R.string.plugin_detail_action_remove),
                enabled = !isLoading && !isSyncing,
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.testTag("remove:${plugin.installKey}"),
            )
        }
    }
}

/** Distinct removal action with confirmation. */
@Composable
private fun RemoveIconButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    AppOutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) 0.55f else 0.12f)),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

/** An available upgrade replaces sync; successful installation then continues the import. */
@Composable
private fun PluginSyncOrUpgradeButton(
    plugin: InstalledPluginRecord,
    isSyncing: Boolean,
    upgrade: GitHubRepoSummary?,
    onSync: () -> Unit,
    onUpgrade: (GitHubRepoSummary) -> Unit,
    fill: Boolean = false,
    blocked: Boolean = false,
) {
    val newVersion = upgrade?.latestRelease?.tagName
    val buttonModifier = if (fill) Modifier.fillMaxWidth() else Modifier
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
            Button(onClick = { onUpgrade(upgrade) }, modifier = buttonModifier, enabled = !blocked) {
                Text(stringResource(R.string.plugin_upgrade_action_version, displayVersion(newVersion)))
            }
        }
        return
    }
    Button(
        onClick = onSync,
        enabled = !isSyncing && !blocked,
        modifier = buttonModifier,
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
    onOpenComponent: (() -> Unit)? = null,
    onOpenScheduleSettings: (() -> Unit)? = null,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    var technicalExpanded by rememberSaveable(plugin.installKey) { mutableStateOf(false) }
    val context = LocalContext.current
    val credentialStore = remember { WebLoginCredentialStore(context) }
    val declaredRows by produceState<List<com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting>>(emptyList(), plugin.packageRevision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.x500x.cursimple.core.data.validPluginTimetableRows(
                com.x500x.cursimple.core.plugin.storage.PluginFileStore(context).loadUiSchema(plugin).timetableRows) }.getOrDefault(emptyList())
        }
    }
    var hasSavedPasswords by remember(plugin.pluginId) { mutableStateOf(credentialStore.hasAny(plugin.pluginId)) }
    val compatibilityMessage = plugin.compatibilityMessage?.takeIf { it.isNotBlank() }
        ?: context.pluginCompatibilityText(resolvePluginCompatibility(plugin.apiVersion))
    val usage by produceState<PluginStorageUsage?>(null, plugin.packageRevision) {
        value = measurePluginStorage(context.filesDir, plugin)
    }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MarketDetailHeading(plugin.name, Modifier.weight(1f).testTag("detail-name"))
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = onSetEnabled,
                                enabled = !isSyncing,
                                modifier = Modifier.testTag("detail-toggle"),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VersionPill(plugin.version)
                            if (!isEnabled) MarketStatusBadge(stringResource(R.string.plugin_catalog_disabled))
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
                            onOpenComponent?.let { open ->
                                Button(onClick = open, modifier = Modifier.testTag("detail-open-component")) {
                                    Text(stringResource(R.string.plugin_card_action_extension_open))
                                }
                            }
                            if (declaredRows.isNotEmpty()) onOpenScheduleSettings?.let { open ->
                                AppOutlinedButton(onClick = open, modifier = Modifier.testTag("detail-schedule-settings")) {
                                    Text(stringResource(R.string.plugin_timetable_settings_title))
                                }
                            }
                            AppOutlinedButton(
                                onClick = { showRemoveConfirm = true },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isSyncing) 0.12f else 0.55f)),
                            ) {
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
                item(key = "install-info") {
                    DetailSection(stringResource(R.string.plugin_detail_section_install)) {
                        Column(Modifier.testTag("detail-install-info"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val measuring = stringResource(R.string.plugin_detail_size_measuring)
                            fun size(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) } ?: measuring
                            InstallInfoRow(stringResource(R.string.plugin_detail_field_installed_at), formatInstalledAt(plugin.installedAt))
                            InstallInfoRow(stringResource(R.string.plugin_detail_field_package_size), size(usage?.packageBytes))
                            if (plugin.isExtension) {
                                InstallInfoRow(stringResource(R.string.plugin_detail_field_data_size), size(usage?.dataBytes))
                                InstallInfoRow(stringResource(R.string.plugin_detail_field_total_size), size(usage?.totalBytes))
                                Text(
                                    text = stringResource(R.string.plugin_detail_data_size_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
    // Keep dialog height fixed; expanded details scroll internally.
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
                // Blocking errors remain visible independently of collapsed technical sections.
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
private fun InstallInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
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
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.8.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

internal enum class PluginPlatformTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Plugins(R.string.plugin_market_tab_plugins, Icons.Rounded.Extension),

    Extensions(R.string.plugin_market_tab_extensions, Icons.Rounded.Widgets),
    /** Reserved for legacy saved page state; runtime installation is unavailable. */
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

private val PACKAGE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
    "*/*",
)

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
