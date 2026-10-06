package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

/** Search schools, install a matching plugin and sign in without leaving the import page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolImportRoute(
    pluginMarketViewModel: PluginMarketViewModel,
    pluginSources: List<String>,
    componentSources: List<String>,
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
    accountKey: String? = null,
) {
    val context = LocalContext.current
    val uiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pluginSources, componentSources, accountKey) {
        pluginMarketViewModel.setSources(pluginSources, componentSources)
        pluginMarketViewModel.onAccountChanged(accountKey)
        pluginMarketViewModel.refreshOnEnter()
        pluginMarketViewModel.refreshInstalledPluginVersions()
    }

    // Back closes the login session before leaving the search workflow.
    BackHandler(enabled = pendingWebSession != null) { onCancelWebSession() }

    val matched = remember(uiState.marketRepos, query) {
        filterMarketRepos(uiState.marketRepos.filterNot { it.isExtension }, query)
    }
    // Retry the catalog once per entry when search finds no school in cached data.
    var refreshedForMiss by remember(pluginSources, componentSources, accountKey) { mutableStateOf(false) }
    LaunchedEffect(query, uiState.marketRepos, uiState.isLoading) {
        if (query.isBlank() || matched.isNotEmpty() || uiState.isLoading || refreshedForMiss) return@LaunchedEffect
        if (pluginSources.isEmpty()) return@LaunchedEffect
        refreshedForMiss = true
        pluginMarketViewModel.loadRegistry()
    }
    val installedByRepo = remember(uiState.installedPlugins) {
        uiState.installedPlugins
            .filter { !it.sourceRepo.isNullOrBlank() }
            .associateBy { it.sourceRepo.orEmpty() }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                actions = {
                    IconButton(
                        onClick = { pluginMarketViewModel.loadRegistry() },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.school_import_refresh),
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
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )

            // Expose active checks, upgrades, login and persistence progress; avoid redundant loaded-count messages.
            val busyText = when {
                uiState.checkingUpdateKey != null || uiState.upgradingKey != null ->
                    uiState.status?.let { context.pluginMarketStatusText(it) }
                        ?: stringResource(R.string.school_import_busy_default)
                syncingPluginId != null ->
                    syncStatusMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.school_import_busy_default)
                else -> null
            }
            if (busyText != null) {
                SchoolImportProgressCard(text = busyText)
            } else {
                uiState.status
                    ?.takeUnless { it is PluginMarketStatus.MarketLoaded }
                    ?.let { status ->
                        Text(
                            text = context.pluginMarketStatusText(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            if (uiState.isRefreshingReleases && busyText == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.marketRepos.isNotEmpty()) {
                Text(
                    text = if (query.isBlank()) {
                        pluralStringResource(R.plurals.school_import_catalog_count, uiState.marketRepos.size, uiState.marketRepos.size)
                    } else {
                        pluralStringResource(
                            R.plurals.school_import_match_count,
                            uiState.marketRepos.size,
                            uiState.marketRepos.size,
                            matched.size,
                        )
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
                    onAction = { pluginMarketViewModel.loadRegistry() },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (matched.isEmpty()) {
                        item(key = "no-match") {
                            SchoolImportEmpty(
                                text = stringResource(R.string.school_import_no_match, query.trim()),
                                hint = stringResource(R.string.school_import_no_match_hint),
                                actionText = stringResource(R.string.school_import_add_manually),
                                onAction = onAddCourseManually,
                                secondaryActionText = stringResource(R.string.school_import_refresh_list),
                                onSecondaryAction = { pluginMarketViewModel.loadRegistry() },
                            )
                        }
                        item(key = "catalog-header") {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.school_import_catalog_header,
                                    uiState.marketRepos.size,
                                    uiState.marketRepos.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    items(matched.ifEmpty { uiState.marketRepos }, key = { it.fullName }) { repo ->
                        val installed = installedByRepo[repo.fullName]
                        SchoolPluginRow(
                            repo = repo,
                            installed = installed,
                            upgrade = installed?.let { availableUpgrade(it, uiState) },
                            busy = uiState.isLoading,
                            checking = uiState.processingRepo(repo.fullName) || (installed != null &&
                                (uiState.checkingUpdateKey == installed.installKey || uiState.upgradingKey == installed.installKey)),
                            syncingPluginId = syncingPluginId,
                            onInstall = { pluginMarketViewModel.installFromGitHub(repo) },
                            onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                            onUpgrade = { record, latest -> pluginMarketViewModel.upgradeThenSync(record, latest) },
                        )
                    }
                    item(key = "browse-all") {
                        AppOutlinedButton(
                            onClick = onBrowseAllPlugins,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.school_import_browse_all))
                        }
                    }
                }
            }

            // Show final sync results separately from the active progress card.
            syncStatusMessage?.takeIf { it.isNotBlank() && syncingPluginId == null }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }

    PluginUpgradeGate(uiState = uiState, viewModel = pluginMarketViewModel, onSyncPlugin = onSyncPlugin)

    uiState.installPreview?.let { preview ->
        InstallPreviewDialog(
            preview = preview,
            origin = uiState.installPreviewOrigin,
            isLoading = uiState.isLoading,
            onDismiss = pluginMarketViewModel::dismissInstallPreview,
            onConfirm = pluginMarketViewModel::confirmInstall,
        )
    }

    // Keep the login session in a full-page container within school import.
    pendingWebSession?.let { request ->
        WebSessionOverlay(
            request = request,
            onFinish = onCompleteWebSession,
            onCancel = onCancelWebSession,
        )
    }
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
    /** Repository with its known newer release, or null. */
    upgrade: GitHubRepoSummary?,
    busy: Boolean,
    checking: Boolean,
    syncingPluginId: String?,
    onInstall: () -> Unit,
    onSync: (InstalledPluginRecord) -> Unit,
    onUpgrade: (InstalledPluginRecord, GitHubRepoSummary) -> Unit,
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
                    text = repo.schoolDisplayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                if (repo.description.isNotBlank()) {
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (installed != null) {
                    val newVersion = upgrade?.latestRelease?.tagName
                    Text(
                        text = if (newVersion != null) {
                            stringResource(
                                R.string.plugin_upgrade_available_line,
                                displayVersion(newVersion),
                                displayVersion(installed.version),
                            )
                        } else {
                            stringResource(R.string.school_import_installed_enabled)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (newVersion != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            when {
                syncing || checking -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)

                installed != null && upgrade != null -> Button(
                    onClick = { onUpgrade(installed, upgrade) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.plugin_upgrade_action), maxLines = 1)
                }

                installed != null -> Button(
                    onClick = { onSync(installed) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.school_import_action_sync), maxLines = 1)
                }

                else -> AppOutlinedButton(onClick = onInstall, enabled = !busy) {
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
internal fun SchoolImportProgressCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
    tertiaryActionText: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
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
            AppOutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionText) }
        }
        if (tertiaryActionText != null && onTertiaryAction != null) {
            AppOutlinedButton(onClick = onTertiaryAction) { Text(tertiaryActionText) }
        }
    }
}
