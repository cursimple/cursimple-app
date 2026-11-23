package com.x500x.cursimple.feature.plugin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.school.SchoolPortalEntry
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val loadedCatalog by produceState<List<SchoolPortalEntry>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { runCatching { SchoolPortalCatalog.load(context) }.getOrDefault(emptyList()) }
    }
    val catalog = loadedCatalog.orEmpty()
    val matchedSchools = remember(catalog, query) { SchoolPortalCatalog.search(catalog, query) }
    // Schools whose plugin is already installed come first: that is the import the user most likely wants.
    val installedPluginIds = remember(uiState.installedPlugins) { uiState.installedPlugins.map { it.pluginId }.toSet() }
    val installedSchools = remember(matchedSchools, installedPluginIds) {
        matchedSchools.filter { it.pluginId in installedPluginIds }
    }
    val schoolSections = remember(matchedSchools) { SchoolPortalCatalog.sections(matchedSchools) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var manualPortalSchoolName by rememberSaveable { mutableStateOf<String?>(null) }
    val sectionIndices = remember(schoolSections) {
        buildMap {
            var index = 0
            schoolSections.forEach { (letter, schools) ->
                put(letter, index)
                index += 1 + schools.size
            }
        }
    }
    val showIndex = sectionIndices.size > 1 || '#' in sectionIndices
    val currentLetter by remember(sectionIndices, listState) {
        derivedStateOf {
            if (listState.canScrollBackward && !listState.canScrollForward) {
                sectionIndices.keys.lastOrNull()
            } else {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                sectionIndices.entries.lastOrNull { it.value <= firstVisibleIndex }?.key
            }
        }
    }
    val searchHits = matchedSchools.size
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
    LaunchedEffect(query, loadedCatalog, uiState.marketRepos, uiState.isLoading) {
        if (loadedCatalog == null || query.isBlank() || matchedSchools.isNotEmpty() || matched.isNotEmpty() ||
            uiState.isLoading || refreshedForMiss
        ) {
            return@LaunchedEffect
        }
        if (pluginSources.isEmpty()) return@LaunchedEffect
        refreshedForMiss = true
        pluginMarketViewModel.loadRegistry()
    }
    val installedByRepo = remember(uiState.installedPlugins) {
        uiState.installedPlugins
            .filter { !it.sourceRepo.isNullOrBlank() }
            .associateBy { it.sourceRepo.orEmpty() }
    }
    val marketOnly = remember(matched, catalogRepoNames) {
        matched.filterNot { it.fullName.lowercase() in catalogRepoNames }
    }
    val openCustomPortal: (String) -> Unit = { url ->
        onOpenSchoolPortal(SchoolPortalEntry(id = "custom", name = query.trim(), portalUrl = url))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.school_import_title)) },
                    navigationIcon = {
                        AppCircleIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.school_import_back),
                            )
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppCircleIconButton(onClick = { manualPortalSchoolName = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.school_portal_add_address),
                                )
                            }
                            AppCircleIconButton(
                                onClick = { pluginMarketViewModel.loadRegistry() },
                                enabled = !uiState.isLoading,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.school_import_refresh),
                                )
                            }
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
                AppOutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        listState.requestScrollToItem(0)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.school_import_search_label)) },
                    placeholder = { Text(stringResource(R.string.school_portal_search_hint)) },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Count the schools in the catalog, not the plugin repositories.
                    Text(
                        text = if (loadedCatalog == null) {
                            ""
                        } else if (query.isBlank()) {
                            pluralStringResource(R.plurals.school_portal_count_total, catalog.size, catalog.size)
                        } else {
                            pluralStringResource(R.plurals.school_portal_count_match, searchHits, searchHits)
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppOutlinedButton(onClick = onBrowseAllPlugins, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.school_import_browse_all))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = if (showIndex) 40.dp else 0.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (loadedCatalog == null) {
                            item(key = "school-loading") { SchoolImportBusy() }
                        } else if (schoolSections.isEmpty()) {
                            item(key = "school-no-results") {
                                SchoolUrlFallback(onOpen = openCustomPortal)
                            }
                        }

                        if (installedSchools.isNotEmpty()) {
                            item(key = "school-section-installed-header") {
                                SchoolShelfHeader(text = stringResource(R.string.school_import_installed_section))
                            }
                            items(installedSchools, key = { "school-installed-${it.id}" }) { school ->
                                val repo = uiState.marketRepos.firstOrNull {
                                    it.fullName.equals(school.pluginRepo, ignoreCase = true)
                                }
                                val installed = repo?.let { installedByRepo[it.fullName] }
                                Box(appItemMotion()) {
                                    SchoolCatalogEntryItem(
                                        school = school,
                                        repo = repo,
                                        installed = installed,
                                        upgrade = installed?.let { availableUpgrade(it, uiState) },
                                        busy = uiState.isLoading,
                                        checking = repo != null &&
                                            (uiState.processingRepo(repo.fullName) || (installed != null &&
                                                (uiState.checkingUpdateKey == installed.installKey ||
                                                    uiState.upgradingKey == installed.installKey))),
                                        syncingPluginId = syncingPluginId,
                                        onInstall = { repo?.let { pluginMarketViewModel.installFromGitHub(it) } },
                                        onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                                        onUpgrade = { record, latest ->
                                            pluginMarketViewModel.upgradeThenSync(record, latest)
                                        },
                                        onOpenSchoolPortal = onOpenSchoolPortal,
                                    )
                                }
                            }
                        }
                        schoolSections.forEach { (letter, schools) ->
                            stickyHeader(key = "school-section-$letter") {
                                SchoolShelfHeader(letter = letter)
                            }
                            items(schools, key = { "school-${it.id}" }) { school ->
                                val repo = uiState.marketRepos.firstOrNull {
                                    it.fullName.equals(school.pluginRepo, ignoreCase = true)
                                }
                                val installed = repo?.let { installedByRepo[it.fullName] }
                                Box(appItemMotion()) {
                                    SchoolCatalogEntryItem(
                                        school = school,
                                        repo = repo,
                                        installed = installed,
                                        upgrade = installed?.let { availableUpgrade(it, uiState) },
                                        busy = uiState.isLoading,
                                        checking = repo != null &&
                                            (uiState.processingRepo(repo.fullName) || (installed != null &&
                                                (uiState.checkingUpdateKey == installed.installKey ||
                                                    uiState.upgradingKey == installed.installKey))),
                                        syncingPluginId = syncingPluginId,
                                        onInstall = { repo?.let { pluginMarketViewModel.installFromGitHub(it) } },
                                        onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                                        onUpgrade = { record, latest ->
                                            pluginMarketViewModel.upgradeThenSync(record, latest)
                                        },
                                        onOpenSchoolPortal = onOpenSchoolPortal,
                                    )
                                }
                            }
                        }

                        if (marketOnly.isNotEmpty()) {
                            item(key = "market-header") {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.school_import_catalog_header,
                                        marketOnly.size,
                                        marketOnly.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            (uiState.checkingUpdateKey == installed.installKey ||
                                                uiState.upgradingKey == installed.installKey)),
                                        syncingPluginId = syncingPluginId,
                                        onInstall = { pluginMarketViewModel.installFromGitHub(repo) },
                                        onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                                        onUpgrade = { record, latest ->
                                            pluginMarketViewModel.upgradeThenSync(record, latest)
                                        },
                                    )
                                }
                            }
                        }

                        if (schoolSections.isNotEmpty()) {
                            item(key = "school-not-listed") {
                                AppOutlinedButton(
                                    onClick = { manualPortalSchoolName = query.trim() },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.school_portal_not_listed), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    if (showIndex) {
                        SchoolShelfIndexBar(
                            letters = sectionIndices.keys,
                            currentLetter = currentLetter,
                            onJump = { letter ->
                                scope.launch { listState.scrollToItem(sectionIndices.getValue(letter)) }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
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

        manualPortalSchoolName?.let { schoolName ->
            AppAlertDialog(
                onDismissRequest = { manualPortalSchoolName = null },
                title = { Text(stringResource(R.string.school_portal_manual_title)) },
                text = {
                    SchoolUrlFallback(
                        hint = stringResource(R.string.school_portal_manual_hint),
                        framed = false,
                        onOpen = { url ->
                            manualPortalSchoolName = null
                            onOpenSchoolPortal(SchoolPortalEntry(id = "custom", name = schoolName, portalUrl = url))
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { manualPortalSchoolName = null }) {
                        Text(stringResource(R.string.school_portal_manual_close))
                    }
                },
            )
        }
    }
}

/** Sticky shelf letter above each A–Z group. */
@Composable
private fun SchoolShelfHeader(letter: Char) = SchoolShelfHeader(letter.toString())

@Composable
private fun SchoolShelfHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SchoolShelfIndexBar(
    letters: Set<Char>,
    currentLetter: Char?,
    onJump: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barHint = stringResource(R.string.school_portal_index_hint)
    Surface(
        modifier = modifier.heightIn(max = (letters.size * 24 + 8).dp).fillMaxHeight()
            .semantics { contentDescription = barHint },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .selectableGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            letters.forEach { letter ->
                val selected = letter == currentLetter
                val jumpLabel = stringResource(R.string.school_portal_index_jump, letter.toString())
                val bringIntoView = remember(letter) { BringIntoViewRequester() }
                LaunchedEffect(selected) {
                    if (selected) bringIntoView.bringIntoView()
                }
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 24.dp)
                        .bringIntoViewRequester(bringIntoView)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, onClick = { onJump(letter) })
                        .semantics { contentDescription = jumpLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * One catalog row: a plugin row for plugin-bound schools, otherwise a portal row the whole row
 * opens. Plugin schools keep a collapsible general import under the plugin action.
 */
@Composable
private fun SchoolCatalogEntryItem(
    school: SchoolPortalEntry,
    repo: GitHubRepoSummary?,
    installed: InstalledPluginRecord?,
    upgrade: GitHubRepoSummary?,
    busy: Boolean,
    checking: Boolean,
    syncingPluginId: String?,
    onInstall: () -> Unit,
    onSync: (InstalledPluginRecord) -> Unit,
    onUpgrade: (InstalledPluginRecord, GitHubRepoSummary) -> Unit,
    onOpenSchoolPortal: (SchoolPortalEntry) -> Unit,
) {
    if (!school.usesPlugin) {
        SchoolPortalRow(school = school, unavailableText = null, onClick = { onOpenSchoolPortal(school) })
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repo != null) {
            SchoolPluginRow(
                repo = repo,
                installed = installed,
                upgrade = upgrade,
                busy = busy,
                checking = checking,
                syncingPluginId = syncingPluginId,
                importLabel = stringResource(R.string.school_portal_badge_plugin),
                onInstall = onInstall,
                onSync = onSync,
                onUpgrade = onUpgrade,
            )
        } else {
            SchoolPortalRow(
                school = school,
                unavailableText = stringResource(R.string.school_portal_plugin_unavailable),
                onClick = null,
            )
        }
        SchoolGeneralImportSection(school = school, onOpenSchoolPortal = onOpenSchoolPortal)
    }
}

/** Collapsed by default; expanding shows the fixed portal address or the address field. */
@Composable
private fun SchoolGeneralImportSection(
    school: SchoolPortalEntry,
    onOpenSchoolPortal: (SchoolPortalEntry) -> Unit,
) {
    var expanded by rememberSaveable(school.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppOutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.school_portal_action_open))
            Spacer(Modifier.size(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
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
                    onOpen = { url -> onOpenSchoolPortal(school.copy(portalUrl = url)) },
                )
            }
        }
    }
}

/**
 * Portal address field. [framed] draws the tinted card used inside the list; dialogs pass false so
 * the field sits directly on the dialog surface instead of getting a second frame.
 */
@Composable
private fun SchoolUrlFallback(
    hint: String? = null,
    framed: Boolean = true,
    onOpen: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (framed) 14.dp else 0.dp, vertical = if (framed) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = hint ?: stringResource(R.string.school_portal_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppOutlinedTextField(
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
    if (framed) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            content()
        }
    } else {
        content()
    }
}

/** A whole row is the tap target; [onClick] null means the row only reports why it cannot open. */
@Composable
private fun SchoolPortalRow(
    school: SchoolPortalEntry,
    unavailableText: String?,
    onClick: (() -> Unit)?,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
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
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
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
