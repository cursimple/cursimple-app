package com.x500x.cursimple.feature.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.feature.plugin.home.*
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Without a usable network the school is not at fault; unknown state counts as online. */
private fun isDeviceOnline(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(true)

internal fun localizedPluginHomeText(default: String, titles: Map<String, String>, language: String): String {
    val key = when {
        language.startsWith("zh", true) && listOf("TW", "HK", "Hant").any { language.contains(it, true) } -> "zh-TW"
        language.startsWith("zh", true) -> "zh-CN"
        else -> "en"
    }
    return titles[key]?.takeIf(String::isNotBlank) ?: default
}

/** All queries are explicit; entering or returning to this page reads only the persistent cache. */
@Composable
internal fun PluginHomeScreen(
    record: InstalledPluginRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    queryRunner: (suspend (PluginManifest, String, String?, String?) -> JsonObject)? = null,
) {
    val context = LocalContext.current
    val language = LocalResources.current.configuration.locales[0].toLanguageTag()
    val scope = rememberCoroutineScope()
    val cache = remember(record.installKey) { PluginHomeCache(context, record.installKey) }
    val credentialStore = remember { WebLoginCredentialStore(context) }
    var manifest by remember(record.packageRevision) { mutableStateOf<PluginManifest?>(null) }
    var script by remember(record.packageRevision) { mutableStateOf("") }
    var data by remember(record.packageRevision) { mutableStateOf(PluginHomeCacheState()) }
    var selected by remember(record.packageRevision) { mutableStateOf("") }
    // Null shows the menu; otherwise the id of the section page that is open.
    var openSection by remember(record.packageRevision) { mutableStateOf<String?>(null) }
    var loaded by remember(record.packageRevision) { mutableStateOf(false) }
    var error by remember(record.packageRevision) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loginState by remember { mutableStateOf<Boolean?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var portalSection by remember { mutableStateOf<String?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showClearCredentials by remember { mutableStateOf(false) }
    var credentialsCleared by remember { mutableStateOf(false) }
    var credentialRevision by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val latestRunner by rememberUpdatedState(queryRunner)
    val loadFailed = stringResource(R.string.plugin_home_load_failed)
    val queryFailed = stringResource(R.string.plugin_home_query_failed)
    val loginRequired = stringResource(R.string.plugin_home_login_required)
    val loginExpiredSaved = stringResource(R.string.plugin_home_login_expired_saved)
    val schoolUnreachable = stringResource(R.string.plugin_home_school_unreachable)
    val deviceOffline = stringResource(R.string.plugin_home_device_offline)
    val invalidResult = stringResource(R.string.plugin_home_invalid_result)
    fun query(sectionId: String?, renderedHtml: String? = null) {
        if (busy) return
        val owner = manifest ?: return
        busy = true
        error = null
        job = scope.launch {
            try {
                val result = latestRunner?.invoke(owner, script, sectionId, renderedHtml)
                    ?: runPluginHomeQuery(context, owner, script, sectionId, language, renderedHtml)
                val account = parseHomeAccount(result)
                if (account == null) {
                    loginState = false
                    // Saved credentials fill the login form themselves; only the SMS code is left to the user.
                    error = if (credentialStore.hasSavedCredentials(owner.id)) loginExpiredSaved else loginRequired
                } else {
                    val now = System.currentTimeMillis()
                    val section = if (sectionId == null) null else try {
                        parseHomeSection(result, sectionId, now)
                    } catch (_: Exception) { throw IllegalArgumentException(invalidResult) }
                    data = withContext(Dispatchers.IO) {
                        cache.update {
                            if (section == null) it.withAccount(account, now)
                            else it.withSection(account, section, if (renderedHtml == null) now else null)
                        }
                    }
                    loginState = if (renderedHtml == null) true else null
                    credentialRevision++
                    if (showLogin) showLogin = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Saved results stay untouched on every failure; only the explanation depends on whose fault it is.
                error = when {
                    !isDeviceOnline(context) -> deviceOffline
                    classifyHomeFailure(failure) == HomeFailureKind.SchoolUnreachable -> schoolUnreachable
                    else -> "$queryFailed ${failure.message.orEmpty().take(500)}".trim()
                }
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(record.packageRevision, reload) {
        loaded = false
        error = null
        try {
            val packageData = withContext(Dispatchers.IO) {
                val store = PluginFileStore(context)
                val declaration = store.loadManifest(record)
                val home = requireNotNull(declaration.home)
                Triple(declaration, store.readText(record, home.entry), cache.read())
            }
            manifest = packageData.first
            script = packageData.second
            data = packageData.third
            val sections = packageData.first.home!!.sections
            selected = packageData.third.active?.lastSectionId?.takeIf { id -> sections.any { it.id == id } }
                ?: sections.first().id
        } catch (_: Exception) {
            error = loadFailed
        }
        loaded = true
    }
    DisposableEffect(record.packageRevision) { onDispose { job?.cancel() } }
    BackHandler { if (showLogin) showLogin = false else if (openSection != null) openSection = null else onBack() }
    val spec = manifest?.home
    val account = data.active
    val section = account?.sections?.get(selected)
    // Search and filter state is scoped to the visible section, so tabs keep independent queries.
    var searchQuery by remember(selected, data.activeAccountId) { mutableStateOf("") }
    var filterColumn by remember(selected, data.activeAccountId) { mutableStateOf<Int?>(null) }
    var filterValue by remember(selected, data.activeAccountId) { mutableStateOf<String?>(null) }
    val expandedRows = remember(selected, data.activeAccountId) { mutableStateListOf<Int>() }
    val filterColumns = remember(section) { section?.let(::homeFilterColumns).orEmpty() }
    val activeFilterColumn = filterColumn?.takeIf { it in filterColumns } ?: filterColumns.firstOrNull()
    val filterOptions = remember(section, activeFilterColumn) {
        if (section == null || activeFilterColumn == null) emptyList() else homeFilterValues(section, activeFilterColumn)
    }
    val badgeColumn = remember(section) { section?.let(::homeBadgeColumn) }
    val visibleRows = remember(section, searchQuery, activeFilterColumn, filterValue) {
        section?.rows?.withIndex()?.filter { homeRowMatches(it.value, searchQuery, activeFilterColumn, filterValue) }.orEmpty()
    }
    fun timestamp(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag(language))
        .format(Date(time))
    // Surface supplies the default content color, so header text stays readable in dark themes.
    Surface(modifier.fillMaxSize().testTag("plugin-home-screen"), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppCircleIconButton(onClick = { if (openSection != null) openSection = null else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plugin_home_back))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val openTab = spec?.sections?.firstOrNull { it.id == openSection }
                Text(
                    openTab?.let { localizedPluginHomeText(it.title, it.titles, language) } ?: stringResource(R.string.plugin_home_title),
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(record.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (openSection != null) AppCircleIconButton(onClick = { query(selected) }, enabled = loaded && spec != null && !busy) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.plugin_home_refresh))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("plugin-home-busy"))
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(
            Modifier.fillMaxSize().testTag("plugin-home-results"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (openSection == null) item {
                val saved = remember(record.pluginId, credentialRevision) { credentialStore.hasSavedCredentials(record.pluginId) }
                val canClearCredentials = remember(record.pluginId, credentialRevision) { credentialStore.hasAny(record.pluginId) }
                HomeCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val info = account?.account
                        val label = info?.let { it.name.ifBlank { it.number } }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            val initial = label?.trim()?.firstOrNull()?.uppercase()
                            if (initial == null) Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            else Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(label ?: stringResource(R.string.plugin_home_no_account), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            info?.let {
                                val detail = listOf(it.school, it.number).filter(String::isNotBlank).joinToString(" · ")
                                if (detail.isNotBlank()) Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (data.accounts.size > 1) AppCircleIconButton(
                                onClick = { showAccounts = true }, enabled = !busy, size = 40.dp,
                                modifier = Modifier.testTag("plugin-home-accounts"),
                            ) { Icon(Icons.Rounded.SwitchAccount, stringResource(R.string.plugin_home_cached_accounts), Modifier.size(20.dp)) }
                            AppCircleIconButton(
                                onClick = { query(null) }, enabled = spec != null && !busy, size = 40.dp,
                                modifier = Modifier.testTag("plugin-home-check"),
                            ) { Icon(Icons.Rounded.VerifiedUser, stringResource(R.string.plugin_home_check_login), Modifier.size(20.dp)) }
                            if (canClearCredentials) AppCircleIconButton(
                                onClick = { showClearCredentials = true }, enabled = !busy, size = 40.dp,
                                modifier = Modifier.testTag("plugin-home-clear-credentials"),
                                contentColor = MaterialTheme.colorScheme.error,
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                            ) { Icon(Icons.Rounded.Delete, stringResource(R.string.plugin_home_clear_credentials), Modifier.size(20.dp)) }
                        }
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (statusText, statusColor) = when (loginState) {
                            true -> R.string.plugin_home_logged_in to MaterialTheme.colorScheme.primary
                            false -> R.string.plugin_home_login_expired to MaterialTheme.colorScheme.error
                            null -> (if (account == null) R.string.plugin_home_login_required else R.string.plugin_home_login_not_checked) to
                                MaterialTheme.colorScheme.outline
                        }
                        HomeStatusPill(stringResource(statusText), statusColor)
                        HomeStatusPill(
                            stringResource(if (saved) R.string.plugin_home_credentials_saved else R.string.plugin_home_credentials_not_saved),
                            if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            Modifier.testTag("plugin-home-credential-status"),
                        )
                        if (account != null && account.verifiedAt > 0) HomeStatusPill(
                            stringResource(R.string.plugin_home_verified_at, timestamp(account.verifiedAt)),
                            MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (credentialsCleared) Text(stringResource(R.string.plugin_home_credentials_cleared),
                        modifier = Modifier.testTag("plugin-home-credentials-cleared"), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { portalSection = null; showLogin = true }, enabled = spec != null && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plugin-home-login"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.plugin_home_login), fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.plugin_home_credentials_shared),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (error != null) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error.orEmpty(), Modifier.testTag("plugin-home-error"),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        if (error == loginRequired || error == loginExpiredSaved) Button(
                            onClick = { portalSection = null; showLogin = true }, enabled = spec != null && !busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("plugin-home-error-login"),
                        ) { Text(stringResource(R.string.plugin_home_login), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            if (spec != null && openSection == null) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.plugin_home_menu_title), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppCircleIconButton(
                            onClick = { showClear = true }, enabled = !busy, size = 40.dp,
                            modifier = Modifier.testTag("plugin-home-clear-cache"),
                            contentColor = MaterialTheme.colorScheme.error,
                            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                        ) { Icon(Icons.Rounded.Delete, stringResource(R.string.plugin_home_clear_cache), Modifier.size(20.dp)) }
                    }
                }
                items(spec.sections, key = { "menu:${it.id}" }) { tab ->
                    val cached = account?.sections?.get(tab.id)
                    HomeMenuEntry(
                        title = localizedPluginHomeText(tab.title, tab.titles, language),
                        subtitle = if (cached == null) stringResource(R.string.plugin_home_menu_not_queried)
                        else stringResource(R.string.plugin_home_menu_cached, cached.rows.size, timestamp(cached.savedAt)),
                        icon = homeSectionIcon(tab.id, tab.title),
                        onClick = { selected = tab.id; openSection = tab.id; error = null },
                        modifier = Modifier.testTag("plugin-home-tab-${tab.id}"),
                    )
                }
            }
            if (openSection != null) item {
                HomeCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (section == null) stringResource(R.string.plugin_home_no_cache)
                            else stringResource(R.string.plugin_home_saved_at, timestamp(section.savedAt)),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (section != null) AppCircleIconButton(
                            onClick = { query(selected) }, enabled = spec != null && !busy, size = 40.dp,
                            modifier = Modifier.testTag("plugin-home-query"),
                        ) { Icon(Icons.Rounded.Refresh, stringResource(R.string.plugin_home_refresh), Modifier.size(20.dp)) }
                        if (spec?.sections?.any { it.id == selected && it.url != null } == true) AppCircleIconButton(
                            onClick = { portalSection = selected; showLogin = true }, enabled = !busy, size = 40.dp,
                        ) { Icon(Icons.Rounded.OpenInBrowser, stringResource(R.string.plugin_home_open_portal), Modifier.size(20.dp)) }
                    }
                    if (section == null) Button(
                        onClick = { query(selected) }, enabled = spec != null && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plugin-home-query"),
                    ) { Text(stringResource(R.string.plugin_home_query), fontWeight = FontWeight.SemiBold) }
                }
            }
            if (openSection != null && section != null) {
                if (section.summary.isNotEmpty()) item {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2,
                    ) {
                        section.summary.forEach { value -> HomeStatTile(value.label, value.value, Modifier.weight(1f)) }
                    }
                }
                if (section.message.isNotBlank()) item {
                    Text(section.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (section.rows.isEmpty()) item { HomeCard { Text(stringResource(R.string.plugin_home_empty_result)) } }
                else {
                    if (section.rows.size >= HOME_SEARCH_MIN_ROWS) item {
                        AppSearchField(
                            query = searchQuery, onQueryChange = { searchQuery = it },
                            hint = stringResource(R.string.plugin_home_search_hint),
                            clearLabel = stringResource(R.string.plugin_home_search_clear),
                            onClear = { searchQuery = "" },
                            fieldModifier = Modifier.testTag("plugin-home-search"),
                        )
                    }
                    if (filterColumns.isNotEmpty() && activeFilterColumn != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (filterColumns.size > 1) Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.FilterList, stringResource(R.string.plugin_home_filter_field),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                filterColumns.forEach { column ->
                                    AppFilterChip(
                                        selected = column == activeFilterColumn,
                                        onClick = { filterColumn = column; filterValue = null },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        label = { Text(section.columns[column], maxLines = 1, softWrap = false) },
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (filterColumns.size == 1) Icon(Icons.Rounded.FilterList, stringResource(R.string.plugin_home_filter_field),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppFilterChip(
                                    selected = filterValue == null, onClick = { filterValue = null },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(stringResource(R.string.plugin_home_filter_all), maxLines = 1, softWrap = false) },
                                )
                                filterOptions.forEach { option ->
                                    AppFilterChip(
                                        selected = filterValue == option, onClick = { filterValue = option },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("plugin-home-filter-$option"),
                                        label = { Text(option, maxLines = 1, softWrap = false) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            if (visibleRows.size == section.rows.size) stringResource(R.string.plugin_home_result_count, section.rows.size)
                            else stringResource(R.string.plugin_home_result_count_filtered, visibleRows.size, section.rows.size),
                            Modifier.testTag("plugin-home-count"),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (visibleRows.isEmpty()) item {
                        HomeCard {
                            Text(stringResource(R.string.plugin_home_no_match), Modifier.testTag("plugin-home-no-match"))
                            AppOutlinedButton(onClick = { searchQuery = ""; filterValue = null }) {
                                Text(stringResource(R.string.plugin_home_reset_filters))
                            }
                        }
                    }
                    items(visibleRows, key = { "$selected:${it.index}" }) { (index, row) ->
                        HomeRowCard(
                            section, row, badgeColumn, expanded = index in expandedRows,
                            onToggle = { if (index in expandedRows) expandedRows.remove(index) else expandedRows.add(index) },
                            modifier = Modifier.testTag("plugin-home-row-$index"),
                        )
                    }
                }
            }
            if (openSection == null) item {
                Text(stringResource(R.string.plugin_home_cache_policy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
    if (showAccounts) AlertDialog(
        onDismissRequest = { showAccounts = false },
        title = { Text(stringResource(R.string.plugin_home_cached_accounts)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.accounts.values.forEach { cached ->
                AppOutlinedButton(onClick = {
                    showAccounts = false
                    busy = true
                    scope.launch {
                        try {
                            data = withContext(Dispatchers.IO) { cache.update { it.copy(activeAccountId = cached.account.id) } }
                            data.active?.lastSectionId?.takeIf { id -> spec?.sections?.any { it.id == id } == true }
                                ?.let { selected = it }
                            loginState = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = loadFailed }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("plugin-home-account-${cached.account.id}")) {
                    Text(listOf(cached.account.name, cached.account.number).filter(String::isNotBlank).joinToString(" · "))
                }
            }
        } },
        confirmButton = { AppOutlinedButton(onClick = { showAccounts = false }) { Text(stringResource(R.string.plugin_home_close)) } },
    )
    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false },
        title = { Text(stringResource(R.string.plugin_home_clear_cache)) },
        text = { Text(stringResource(R.string.plugin_home_clear_confirm)) },
        confirmButton = { AppOutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { cache.clear() }
                    openSection = null
                    data = PluginHomeCacheState()
                    loginState = null
                    showClear = false
                    error = null
                    reload++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = loadFailed; showClear = false }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.plugin_home_clear_cache), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { AppOutlinedButton(onClick = { showClear = false }) { Text(stringResource(R.string.plugin_home_cancel)) } },
    )
    if (showClearCredentials) AlertDialog(
        onDismissRequest = { showClearCredentials = false },
        title = { Text(stringResource(R.string.plugin_home_clear_credentials)) },
        text = { Text(stringResource(R.string.plugin_home_clear_credentials_confirm)) },
        confirmButton = {
            AppOutlinedButton(
                onClick = {
                    credentialStore.clear(record.pluginId)
                    credentialRevision++
                    credentialsCleared = true
                    showClearCredentials = false
                },
                modifier = Modifier.testTag("plugin-home-confirm-clear-credentials"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
            ) { Text(stringResource(R.string.plugin_home_clear_credentials)) }
        },
        dismissButton = { AppOutlinedButton(onClick = { showClearCredentials = false }) {
            Text(stringResource(R.string.plugin_home_cancel))
        } },
    )
    if (showLogin && manifest != null) PluginHomeLogin(
        record, manifest!!, portalSection, busy, error, onCheck = { query(null) },
        onSavePage = { html -> portalSection?.let { query(it, html) } },
        onDismiss = { showLogin = false; credentialsCleared = false; credentialRevision++ },
        onAccountChanged = {
            job?.cancel()
            loginState = null
        },
    )
}

private const val HOME_SEARCH_MIN_ROWS = 4

private fun homeSectionIcon(id: String, title: String): ImageVector {
    val key = "$id $title".lowercase()
    return when {
        Regex("grade|score|成绩|成績").containsMatchIn(key) -> Icons.Rounded.Assessment
        Regex("plan|program|培养|培養").containsMatchIn(key) -> Icons.Rounded.AccountTree
        Regex("credit|学分|學分").containsMatchIn(key) -> Icons.Rounded.WorkspacePremium
        Regex("profile|info|学籍|學籍").containsMatchIn(key) -> Icons.Rounded.Badge
        else -> Icons.Rounded.Dataset
    }
}

@Composable
private fun HomeMenuEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
private val homePreferredFilter = Regex("学期|类型|性质|状态|类别|年级|term|semester|type|status|category|kind", RegexOption.IGNORE_CASE)
private val homeBadgeHeader = Regex("成绩|成績|分数|分數|总评|總評|最终|最終|期末|平时|平時|总分|總分|绩点|績點|score|grade|gpa|final", RegexOption.IGNORE_CASE)

/** Columns holding a small set of repeated values make useful filters; the title column never does. */
internal fun homeFilterColumns(section: PluginHomeSection): List<Int> {
    if (section.rows.size < HOME_SEARCH_MIN_ROWS) return emptyList()
    return (1 until section.columns.size).filter { column ->
        // Score-like columns have too many distinct values to be useful as filters.
        if (homeBadgeHeader.containsMatchIn(section.columns[column])) return@filter false
        val distinct = section.rows.mapNotNull { it.getOrNull(column)?.trim()?.takeIf(String::isNotEmpty) }.toSet()
        distinct.size in 2..16 && distinct.size < section.rows.size
    }.sortedBy { if (homePreferredFilter.containsMatchIn(section.columns[it])) 0 else 1 }
}

internal fun homeFilterValues(section: PluginHomeSection, column: Int): List<String> =
    section.rows.mapNotNull { it.getOrNull(column)?.trim()?.takeIf(String::isNotEmpty) }.distinct()

/** The score-like column is promoted to a badge beside the row title. */
internal fun homeBadgeColumn(section: PluginHomeSection): Int? =
    (1 until section.columns.size).firstOrNull { homeBadgeHeader.containsMatchIn(section.columns[it]) }

internal fun homeRowMatches(row: List<String>, query: String, column: Int?, value: String?): Boolean {
    if (column != null && value != null && row.getOrNull(column)?.trim() != value) return false
    val needle = query.trim()
    return needle.isEmpty() || row.any { it.contains(needle, ignoreCase = true) }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun HomeStatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HomeStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeRowCard(
    section: PluginHomeSection,
    row: List<String>,
    badgeColumn: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fields = (1 until row.size).filter { it != badgeColumn }
    val shown = if (expanded) fields else fields.filter { row[it].isNotBlank() }.take(HOME_COLLAPSED_FIELDS)
    val expandable = expanded || shown.size < fields.size
    Surface(
        onClick = onToggle, enabled = expandable, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.first(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                badgeColumn?.let { column ->
                    row[column].takeIf(String::isNotBlank)?.let { badge ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(badge, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (shown.isNotEmpty()) FlowRow(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2,
            ) {
                shown.forEach { field ->
                    Column(Modifier.weight(1f)) {
                        Text(section.columns[field], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row[field].ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (expandable) Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                stringResource(if (expanded) R.string.plugin_home_collapse else R.string.plugin_home_expand),
                Modifier.align(Alignment.CenterHorizontally).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HOME_COLLAPSED_FIELDS = 4

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PluginHomeLogin(
    record: InstalledPluginRecord,
    manifest: PluginManifest,
    sectionId: String?,
    busy: Boolean,
    message: String?,
    onCheck: () -> Unit,
    onSavePage: (String) -> Unit,
    onDismiss: () -> Unit,
    onAccountChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val home = requireNotNull(manifest.home)
    val language = resources.configuration.locales[0].toLanguageTag()
    var captured by remember { mutableStateOf<WebLoginCredential?>(null) }
    val currentAccountChanged by rememberUpdatedState(onAccountChanged)
    val store = remember { WebLoginCredentialStore(context) }
    val view = remember(record.packageRevision) { WebView(context) }
    val assist = remember(record.packageRevision) {
        WebLoginAssist(record.pluginId, manifest.allowedHosts, store, {
            if (store.find(record.pluginId, it.host) != it) captured = it
        }, { currentAccountChanged() })
    }
    var progress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    val popups = remember { mutableListOf<WebView>() }
    DisposableEffect(view) {
        view.applyPluginBrowserSettings(manifest.userAgent.orEmpty())
        // Same legacy-site handling as the import session, so the academic home page is never a bare fragment.
        val legacySiteCompat = LegacySiteCompat(context.applicationContext, manifest.allowedHosts) { manifest.userAgent.orEmpty() }
        legacySiteCompat.install(view)
        assist.install(view)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, value: Int) { progress = value }
            override fun onCreateWindow(parent: WebView?, dialog: Boolean, gesture: Boolean, result: android.os.Message?): Boolean {
                if (result == null || popups.size >= 4) return false
                val popup = WebView(context)
                popups += popup
                popup.hardenLocalAccess()
                popup.suppressRequestedWithHeader()
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                manifest.userAgent?.let { popup.settings.userAgentString = it }
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(child: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (isAllowedHost(url, manifest.allowedHosts)) view.loadUrl(url)
                        return url != "about:blank"
                    }
                }
                (result.obj as? WebView.WebViewTransport)?.webView = popup
                result.sendToTarget()
                return true
            }
        }
        view.settings.setSupportMultipleWindows(true)
        view.settings.javaScriptCanOpenWindowsAutomatically = true
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? =
                request?.let { legacySiteCompat.interceptDocument(it) } ?: super.shouldInterceptRequest(view, request)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                val blocked = !isAllowedHost(url, manifest.allowedHosts)
                if (blocked) pageError = resources.getString(R.string.plugin_web_status_blocked,
                    runCatching { java.net.URI(url).host }.getOrNull().orEmpty())
                return blocked
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.let { assist.onPageFinished(it, url) }
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) pageError = failure?.description?.toString()
            }
        }
        onDispose {
            view.stopLoading()
            CookieManager.getInstance().flush()
            view.removeJavascriptInterface(WebLoginAssist.BRIDGE_NAME)
            view.destroy()
            popups.forEach { it.destroy() }
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.plugin_home_close)) }
                    Text(stringResource(R.string.plugin_home_login), Modifier.weight(1f).padding(horizontal = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(onClick = onCheck, enabled = !busy) { Text(stringResource(R.string.plugin_home_check_login)) }
                    if (sectionId != null) AppOutlinedButton(onClick = {
                        if (isAllowedHost(view.url.orEmpty(), manifest.allowedHosts)) {
                            view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                val html = runCatching {
                                    (kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonPrimitive)?.content
                                }.getOrNull()
                                if (html != null) onSavePage(html)
                            }
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.plugin_home_save_page)) }
                }
                val help = localizedPluginHomeText("", home.loginHelp, language)
                if (help.isNotBlank()) Text(help, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
                (message ?: pageError)?.let { failure ->
                    Text(failure, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (busy || progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(
                    factory = { hostContext ->
                        createPluginHomeWebViewHost(
                            hostContext, view,
                            home.sections.firstOrNull { it.id == sectionId }?.url ?: home.loginUrl,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
    captured?.let { credential ->
        AlertDialog(
            onDismissRequest = { captured = null },
            title = { Text(stringResource(R.string.plugin_home_save_login_title)) },
            text = { Text(stringResource(R.string.plugin_home_save_login_message)) },
            confirmButton = { AppOutlinedButton(onClick = { store.save(record.pluginId, credential); captured = null }) {
                Text(stringResource(R.string.plugin_home_save_login))
            } },
            dismissButton = { AppOutlinedButton(onClick = { captured = null }) { Text(stringResource(R.string.plugin_home_cancel)) } },
        )
    }
}

internal fun createPluginHomeWebViewHost(context: Context, view: WebView, startUrl: String): FrameLayout =
    FrameLayout(context).apply {
        addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // Percentage-height portals must load after their native viewport has a real size.
        doOnLayout { view.loadUrl(startUrl) }
    }
