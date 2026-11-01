package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.school.SchoolPortalEntry
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

/** Search schools, install a matching plugin and sign in without leaving the import page. */
private const val MAX_SCHOOL_MATCHES = 60

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
    onOpenSchoolPortal: (SchoolPortalEntry) -> Unit,
    modifier: Modifier = Modifier,
    accountKey: String? = null,
) {
    val context = LocalContext.current
    val uiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val catalog by produceState(initialValue = emptyList<SchoolPortalEntry>(), context) {
        value = withContext(Dispatchers.IO) { runCatching { SchoolPortalCatalog.load(context) }.getOrDefault(emptyList()) }
    }
    // Blank query shows the well-known schools and plugin-bound schools; a query searches the full catalog.
    val schoolRows = remember(catalog, query) {
        val all = SchoolPortalCatalog.search(catalog, query)
        // Plugin-bound schools come first: their import is the recommended path.
        val shown = if (query.isBlank()) all.filter { it.tiers.isNotEmpty() || it.usesPlugin } else all.take(MAX_SCHOOL_MATCHES)
        shown.sortedByDescending { it.usesPlugin }
    }
    val searchHits = remember(catalog, query) { SchoolPortalCatalog.search(catalog, query).size }
    val catalogRepoNames = remember(catalog) { catalog.mapNotNull { it.pluginRepo?.lowercase() }.toSet() }

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

            // Count the schools in the catalog, not the plugin repositories.
            Text(
                text = if (query.isBlank()) {
                    pluralStringResource(R.plurals.school_portal_count_total, catalog.size, catalog.size)
                } else {
                    pluralStringResource(R.plurals.school_portal_count_match, searchHits, searchHits)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "school-header") {
                    Text(
                        text = stringResource(R.string.school_portal_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(schoolRows, key = { "school-${it.id}" }) { school ->
                    Box(appItemMotion()) {
                        if (school.usesPlugin) {
                            val repo = uiState.marketRepos.firstOrNull { it.fullName.equals(school.pluginRepo, ignoreCase = true) }
                            // General import stays available so a broken plugin can be tested through the portal.
                            var showGeneral by rememberSaveable(school.id) { mutableStateOf(false) }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (repo != null) {
                                    val installed = installedByRepo[repo.fullName]
                                    SchoolPluginRow(
                                        repo = repo,
                                        installed = installed,
                                        upgrade = installed?.let { availableUpgrade(it, uiState) },
                                        busy = uiState.isLoading,
                                        checking = uiState.processingRepo(repo.fullName) || (installed != null &&
                                            (uiState.checkingUpdateKey == installed.installKey || uiState.upgradingKey == installed.installKey)),
                                        syncingPluginId = syncingPluginId,
                                        importLabel = stringResource(R.string.school_portal_badge_plugin),
                                        onInstall = { pluginMarketViewModel.installFromGitHub(repo) },
                                        onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                                        onUpgrade = { record, latest -> pluginMarketViewModel.upgradeThenSync(record, latest) },
                                    )
                                } else {
                                    SchoolPortalRow(
                                        school = school,
                                        unavailableText = stringResource(R.string.school_portal_plugin_unavailable),
                                        onOpen = {},
                                    )
                                }
                                // Collapsed by default; expanding shows the portal address field.
                                AppOutlinedButton(
                                    onClick = { showGeneral = !showGeneral },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.school_portal_action_open))
                                    Spacer(Modifier.size(6.dp))
                                    Icon(
                                        imageVector = if (showGeneral) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (showGeneral) {
                                    // General import always uses the fixed address from the school list, never the plugin.
                                    if (!school.portalUrl.isNullOrBlank()) {
                                        Button(
                                            onClick = { onOpenSchoolPortal(school) },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) {
                                            Text(stringResource(R.string.school_portal_action_open))
                                        }
                                    } else {
                                        // No fixed address in the list: the user enters the portal address.
                                        SchoolUrlFallback(
                                            hint = stringResource(R.string.school_portal_general_alt_hint),
                                            onOpen = { url ->
                                                onOpenSchoolPortal(SchoolPortalEntry(id = school.id, name = school.name, portalUrl = url))
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            SchoolPortalRow(school = school, unavailableText = null, onOpen = { onOpenSchoolPortal(school) })
                        }
                    }
                }
                if (query.isNotBlank() && schoolRows.isEmpty()) {
                    item(key = "school-no-match") {
                        Box(appItemMotion()) {
                            SchoolUrlFallback(onOpen = { url ->
                                onOpenSchoolPortal(SchoolPortalEntry(id = "custom", name = query.trim(), portalUrl = url))
                            })
                        }
                    }
                }

                // Plugin repositories that no catalog school claims.
                val marketOnly = matched.filterNot { it.fullName.lowercase() in catalogRepoNames }
                if (uiState.marketRepos.isEmpty() && uiState.isLoading) {
                    item(key = "market-busy") { SchoolImportBusy() }
                } else if (uiState.marketRepos.isEmpty()) {
                    item(key = "market-empty") {
                        SchoolImportEmpty(
                            text = stringResource(R.string.school_import_market_empty),
                            actionText = stringResource(R.string.school_import_retry),
                            onAction = { pluginMarketViewModel.loadRegistry() },
                        )
                    }
                } else if (marketOnly.isNotEmpty()) {
                    item(key = "market-header") {
                        Text(
                            text = pluralStringResource(R.plurals.school_import_catalog_header, marketOnly.size, marketOnly.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(marketOnly, key = { "repo-${it.fullName}" }) { repo ->
                        Box(appItemMotion()) {
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
                    }
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

/** Shown when no catalogued school matches: the user enters a portal address for general import. */
@Composable
private fun SchoolUrlFallback(hint: String? = null, onOpen: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = hint ?: stringResource(R.string.school_portal_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
                label = { Text(stringResource(R.string.school_portal_url_label)) },
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.school_portal_url_invalid)) }
                } else {
                    null
                },
            )
            Button(
                onClick = {
                    val trimmed = url.trim()
                    if (SchoolPortalCatalog.isOpenablePortal(trimmed)) onOpen(trimmed) else invalid = true
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.school_portal_action_open))
            }
        }
    }
}

@Composable
private fun SchoolPortalRow(
    school: SchoolPortalEntry,
    unavailableText: String?,
    onOpen: () -> Unit,
) {
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
                    text = school.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(
                        if (school.usesPlugin) R.string.school_portal_badge_plugin else R.string.school_portal_badge_portal,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                unavailableText?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (unavailableText == null) {
                Button(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.school_portal_action_open), maxLines = 1)
                }
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
    /** Button text for the import action when the row comes from the school catalog. */
    importLabel: String? = null,
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
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(importLabel ?: stringResource(R.string.school_import_action_sync), maxLines = 1)
                }

                importLabel != null -> Button(
                    onClick = onInstall,
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(importLabel, maxLines = 1)
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
