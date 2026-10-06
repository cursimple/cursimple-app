package com.x500x.cursimple.feature.plugin.extension

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSetting
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage
import com.x500x.cursimple.feature.plugin.R
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter

/** App-layer facade for sync, package reads, reminder reconciliation and navigation. */
interface ExtensionHostActions {
    suspend fun loadPackage(record: InstalledPluginRecord): Pair<PluginManifest, String>

    /** Null owned-UI content requests the host fallback. */
    suspend fun loadUi(record: InstalledPluginRecord): String?

    suspend fun loadUi(record: InstalledPluginRecord, page: PluginExtensionUiPage): String? =
        if (page == PluginExtensionUiPage.Feed) loadUi(record) else null

    suspend fun isCurrent(record: InstalledPluginRecord): Boolean = true

    suspend fun syncNow(record: InstalledPluginRecord): ExtensionSyncOutcome

    suspend fun markRead(record: InstalledPluginRecord, itemId: String): ExtensionData = error("请更新应用以支持已读同步")

    suspend fun setItemIgnored(record: InstalledPluginRecord, itemId: String, ignored: Boolean): ExtensionData =
        error("请更新应用以支持忽略内容")

    suspend fun notificationCommand(record: InstalledPluginRecord, command: String, payload: JsonObject): JsonElement =
        error("请更新应用以支持通知出口组件")

    /** Reconcile reminders and events after host or login changes without fetching again. */
    fun onDataChanged(pluginId: String)

    fun openFeed(pluginId: String)

    fun openFeed(record: InstalledPluginRecord) = openFeed(record.pluginId)
}

@Composable
fun ExtensionSettingsScreen(
    record: InstalledPluginRecord,
    actions: ExtensionHostActions,
    onBack: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.x500x.cursimple.feature.plugin.ui.OwnEmbeddedPageGestures()
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { ExtensionStore.get(context) }
    val scope = rememberCoroutineScope()
    val data by store.flow(record.pluginId).collectAsState(initial = ExtensionData(record.pluginId))
    var manifest by remember(record.packageRevision) { mutableStateOf<PluginManifest?>(null) }
    var entry by remember(record.packageRevision) { mutableStateOf("") }
    var settingsUi by remember(record.packageRevision) { mutableStateOf<String?>(null) }
    var loadError by remember(record.packageRevision) { mutableStateOf<String?>(null) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncFeedback by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showScheduleSettings by rememberSaveable { mutableStateOf(false) }
    var showFeedSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(record.packageRevision) {
        store.ensureLoaded()
        runCatching { actions.loadPackage(record) }
            .onSuccess { (loaded, source) ->
                manifest = loaded
                entry = source
                settingsUi = actions.loadUi(record, PluginExtensionUiPage.Settings)
            }
            .onFailure { loadError = it.message ?: it.javaClass.simpleName }
    }

    fun runSync() {
        if (syncing) return
        syncing = true
        syncFeedback = null
        scope.launch {
            val outcome = runCatching { actions.syncNow(record) }.getOrElse {
                ExtensionSyncOutcome.Failed(record.pluginId, it.message ?: "")
            }
            syncing = false
            syncFeedback = when (outcome) {
                is ExtensionSyncOutcome.Synced -> resources.getQuantityString(
                    R.plurals.extension_sync_done,
                    outcome.data.items.size,
                    outcome.data.items.size,
                )
                is ExtensionSyncOutcome.LoginRequired -> resources.getString(R.string.extension_sync_login_required)
                is ExtensionSyncOutcome.Failed -> resources.getString(R.string.extension_sync_failed, outcome.message)
                is ExtensionSyncOutcome.Skipped -> null
            }
        }
    }

    val spec = manifest?.extension
    val title = spec?.title?.takeIf(String::isNotBlank) ?: record.name
    val effectivePluginSettings = spec?.let { ExtensionUrls.effectiveSettings(it, data.settings) }.orEmpty()
    settingsUi?.let { html ->
        manifest?.let { loaded ->
            ExtensionOwnedPage(
                record = record, manifest = loaded, data = data, html = html,
                page = PluginExtensionUiPage.Settings, actions = actions,
                onBack = onBack, onOpenSettings = {}, onRemove = onRemove,
                modifier = modifier,
            )
            return
        }
    }
    if (showFeedSettings) {
        ExtensionFeedSettingsScreen(
            data = data,
            types = spec?.feedTypes.orEmpty(),
            onSave = { settings ->
                scope.launch {
                    store.update(record.pluginId) { it.copy(host = it.host.copy(feed = settings)) }
                    showFeedSettings = false
                }
            },
            onBack = { showFeedSettings = false },
            modifier = modifier,
        )
        return
    }
    if (showScheduleSettings) {
        ExtensionScheduleSettingsScreen(
            data = data,
            types = spec?.feedTypes.orEmpty(),
            onSave = { settings ->
                scope.launch {
                    store.update(record.pluginId) { it.copy(host = it.host.copy(schedule = settings)) }
                    actions.onDataChanged(record.pluginId)
                    showScheduleSettings = false
                }
            },
            onBack = { showScheduleSettings = false },
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plugin_action_back)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.extension_settings_subtitle, record.version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            loadError?.let { message ->
                item { SettingsCard { Text(stringResource(R.string.extension_load_failed, message), color = MaterialTheme.colorScheme.error) } }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                  Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
                            Icon(if (data.loggedIn) Icons.Rounded.Person else Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(26.dp))
                        }
                        Column(Modifier.weight(1f)) { AccountBlock(data = data, title = title) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showLogin = true }, enabled = spec != null) {
                            Text(
                                stringResource(
                                    if (data.loggedIn) R.string.extension_action_relogin else R.string.extension_action_login,
                                ),
                            )
                        }
                        if (data.loginState == ExtensionLoginState.LoggedIn || data.loginState == ExtensionLoginState.Expired) {
                            AppOutlinedButton(onClick = { showLogoutConfirm = true }) {
                                Text(stringResource(R.string.extension_action_logout))
                            }
                        }
                    }
                  }
                }
            }

            if (data.loginState == ExtensionLoginState.LoggedIn || data.loginState == ExtensionLoginState.Expired) {
                item {
                    SectionTitle(stringResource(R.string.extension_section_sync))
                    SettingsCard {
                        Text(syncStatusText(data), style = MaterialTheme.typography.bodyMedium)
                        data.lastError?.let {
                            Text(
                                stringResource(R.string.extension_sync_last_error, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        data.lastMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        syncFeedback?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = ::runSync, enabled = !syncing && manifest != null) {
                                if (syncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.extension_action_sync_now))
                            }
                            AppOutlinedButton(onClick = { actions.openFeed(record.pluginId) }) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.extension_action_open_feed))
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        ChoiceRow(
                            title = stringResource(R.string.extension_sync_interval),
                            value = data.host.syncIntervalMinutes ?: spec?.syncIntervalMinutes
                                ?: ExtensionHostSettings.SYNC_INTERVAL_CHOICES.first(),
                            choices = ExtensionHostSettings.SYNC_INTERVAL_CHOICES,
                            label = { minutes -> intervalLabel(minutes) },
                            onSelect = { minutes ->
                                scope.launch {
                                    store.update(record.pluginId) { it.copy(host = it.host.copy(syncIntervalMinutes = minutes)) }
                                    actions.onDataChanged(record.pluginId)
                                }
                            },
                        )
                    }
                }

            }

                item {
                    SectionTitle(stringResource(R.string.extension_section_host))
                    SettingsCard {
                        SwitchRow(
                            title = stringResource(R.string.extension_host_sidebar),
                            subtitle = stringResource(R.string.extension_host_sidebar_desc, title),
                            checked = data.host.showInSidebar,
                        ) { checked -> updateHost(scope, store, actions, record) { it.copy(showInSidebar = checked) } }
                        AnimatedVisibility(visible = data.host.showInSidebar) {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { showFeedSettings = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.extension_feed_settings_title), style = MaterialTheme.typography.titleSmall)
                                        val labels = spec?.feedTypes.orEmpty().filter { data.host.feed.includes(it.id) }.map { it.label }
                                        val viewName = stringResource(R.string.extension_feed_mode_month)
                                        val typeNames = if (data.host.feed.includedTypes == null) stringResource(R.string.extension_schedule_all) else labels.joinToString(" · ").ifBlank { stringResource(R.string.extension_feed_settings_none) }
                                        Text("$viewName · $typeNames", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        SwitchRow(
                            title = stringResource(R.string.extension_host_notify),
                            subtitle = stringResource(R.string.extension_host_notify_desc),
                            checked = data.host.notifyNew,
                        ) { checked -> updateHost(scope, store, actions, record) { it.copy(notifyNew = checked) } }
                        SwitchRow(
                            title = stringResource(R.string.extension_ignore_overdue),
                            subtitle = stringResource(R.string.extension_ignore_overdue_desc),
                            checked = data.host.ignoreOverdue,
                        ) { checked -> updateHost(scope, store, actions, record) { it.copy(ignoreOverdue = checked) } }
                        ChoiceRow(
                            title = stringResource(R.string.extension_host_due),
                            value = data.host.dueReminderHours,
                            choices = ExtensionHostSettings.DUE_REMINDER_CHOICES,
                            label = { hours -> dueLabel(hours) },
                            onSelect = { hours -> updateHost(scope, store, actions, record) { it.copy(dueReminderHours = hours) } },
                        )
                    }
                }

            item {
                SectionTitle(stringResource(R.string.extension_schedule_section))
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.extension_host_schedule),
                        subtitle = stringResource(R.string.extension_host_schedule_desc),
                        checked = data.host.addToSchedule,
                    ) { checked -> updateHost(scope, store, actions, record) { it.copy(addToSchedule = checked) } }
                    AnimatedVisibility(visible = data.host.addToSchedule) {
                        Column {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth().clickable { showScheduleSettings = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extension_schedule_details), style = MaterialTheme.typography.titleSmall)
                                    val selected = data.host.schedule.includedTypes
                                    val labels = spec?.feedTypes.orEmpty().filter { selected?.contains(it.id) ?: true }.map { it.label }
                                    Text(if (selected == null) stringResource(R.string.extension_schedule_all) else labels.joinToString(" · ").ifBlank { stringResource(R.string.extension_schedule_none) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.extension_schedule_independent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (spec != null && spec.settings.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.extension_section_plugin, title))
                    SettingsCard {
                        val effective = ExtensionUrls.effectiveSettings(spec, data.settings)
                        spec.settings.forEachIndexed { index, setting ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            PluginSettingRow(setting = setting, value = effective[setting.key]) { value ->
                                scope.launch {
                                    val changed = effective[setting.key] != value
                                    store.update(record.pluginId) { current ->
                                        val next = current.copy(settings = current.settings + (setting.key to value))
                                        if (changed && setting.requiresRelogin && current.loginState == ExtensionLoginState.LoggedIn) {
                                            next.copy(
                                                sessionRevision = current.sessionRevision + 1,
                                                loginState = ExtensionLoginState.Expired,
                                                items = emptyList(),
                                                state = emptyMap(),
                                                baselineReady = false,
                                                ignoredItemIds = emptySet(), restoredItemIds = emptySet(),
                                            )
                                        } else {
                                            next
                                        }
                                    }
                                    actions.onDataChanged(record.pluginId)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.extension_section_manage))
                SettingsCard {
                    record.sourceRepo?.let {
                        Text(stringResource(R.string.extension_source_repo, it), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.extension_hosts, record.allowedHosts.joinToString("、")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { showRemoveConfirm = true }) {
                        Text(stringResource(R.string.extension_action_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }

    val loadedManifest = manifest
    if (showLogin && loadedManifest != null) {
        Dialog(
            onDismissRequest = { showLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ExtensionDialogSystemBars()
            run {
                ExtensionLoginScreen(
                    title = title,
                    buildRequest = { ExtensionRunRequest.from(loadedManifest, entry, data, ExtensionRunMode.CheckLogin) },
                    viewportWidth = loadedManifest.extension?.loginViewportWidth,
                    onLoggedIn = { account ->
                    showLogin = false
                    scope.launch {
                        store.update(record.pluginId) {
                            it.copy(
                                sessionRevision = it.sessionRevision + 1,
                                loginState = ExtensionLoginState.LoggedIn,
                                account = account ?: it.account,
                                // Discard the previous account's synchronization baseline on account change.
                                items = if (account != null && account.id != it.account?.id) emptyList() else it.items,
                                baselineReady = if (account != null && account.id != it.account?.id) false else it.baselineReady,
                                ignoredItemIds = if (account != null && account.id != it.account?.id) emptySet() else it.ignoredItemIds,
                                restoredItemIds = if (account != null && account.id != it.account?.id) emptySet() else it.restoredItemIds,
                                expiredNotified = false,
                                lastError = null,
                            )
                        }
                        actions.onDataChanged(record.pluginId)
                        runSync()
                    }
                    },
                    onCancel = { showLogin = false },
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.extension_logout_confirm_title)) },
            text = { Text(stringResource(R.string.extension_logout_confirm_body, title)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        clearExtensionCookies(record.allowedHosts)
                        store.update(record.pluginId, ::loggedOutComponent)
                        actions.onDataChanged(record.pluginId)
                    }
                }) { Text(stringResource(R.string.extension_action_logout)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.extension_remove_confirm_title, record.name)) },
            text = { Text(stringResource(R.string.extension_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    scope.launch {
                        clearExtensionCookies(record.allowedHosts)
                        store.remove(record.pluginId)
                        actions.onDataChanged(record.pluginId)
                        onRemove()
                    }
                }) { Text(stringResource(R.string.extension_action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
internal fun ExtensionDialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(window, light) {
        val controller = WindowCompat.getInsetsController(window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
}

private fun updateHost(
    scope: kotlinx.coroutines.CoroutineScope,
    store: ExtensionStore,
    actions: ExtensionHostActions,
    record: InstalledPluginRecord,
    transform: (ExtensionHostSettings) -> ExtensionHostSettings,
) {
    scope.launch {
        store.update(record.pluginId) { it.copy(host = transform(it.host)) }
        actions.onDataChanged(record.pluginId)
    }
}

@Composable
private fun AccountBlock(data: ExtensionData, title: String) {
    val account = data.account
    when (data.loginState) {
        ExtensionLoginState.LoggedIn -> {
            Text(
                stringResource(R.string.extension_account_logged_in, account?.name?.ifBlank { null } ?: title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            listOfNotNull(account?.school?.ifBlank { null }, account?.number?.ifBlank { null })
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        ExtensionLoginState.Expired -> {
            Text(stringResource(R.string.extension_account_expired), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.extension_account_expired_desc, title), style = MaterialTheme.typography.bodySmall)
        }
        ExtensionLoginState.Never, ExtensionLoginState.LoggedOut -> {
            Text(stringResource(R.string.extension_account_none, title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.extension_account_none_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun syncStatusText(data: ExtensionData): String {
    val last = data.lastSyncAt ?: return stringResource(R.string.extension_sync_never)
    val text = Instant.ofEpochMilli(last).atZone(BeijingTime.zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    return pluralStringResource(R.plurals.extension_sync_status, data.items.size, text, data.items.size)
}

@Composable
private fun intervalLabel(minutes: Int): String = if (minutes % 60 == 0) {
    pluralStringResource(R.plurals.extension_interval_hours, minutes / 60, minutes / 60)
} else {
    stringResource(R.string.extension_interval_minutes, minutes)
}

@Composable
private fun dueLabel(hours: Int): String = if (hours <= 0) {
    stringResource(R.string.extension_due_off)
} else {
    pluralStringResource(R.plurals.extension_due_hours, hours, hours)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun <T> ChoiceRow(
    title: String,
    value: T,
    choices: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String = "",
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(max = 140.dp))
        Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                open = false
                                onSelect(choice)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == value, onClick = {
                                open = false
                                onSelect(choice)
                            })
                            Text(label(choice))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun PluginSettingRow(setting: PluginExtensionSetting, value: JsonElement?, onChange: (JsonElement) -> Unit) {
    val primitive = value as? JsonPrimitive
    when (setting.type) {
        PluginExtensionSetting.TYPE_SWITCH -> SwitchRow(
            title = setting.label,
            subtitle = setting.description,
            checked = primitive?.booleanOrNull ?: false,
        ) { onChange(JsonPrimitive(it)) }

        PluginExtensionSetting.TYPE_SELECT -> {
            val current = primitive?.contentOrNull ?: setting.options.firstOrNull()?.value.orEmpty()
            ChoiceRow(
                title = setting.label,
                subtitle = setting.description,
                value = current,
                choices = setting.options.map { it.value },
                label = { v -> setting.options.firstOrNull { it.value == v }?.label ?: v },
                onSelect = { onChange(JsonPrimitive(it)) },
            )
        }

        PluginExtensionSetting.TYPE_NUMBER -> {
            var text by remember(value) { mutableStateOf(primitive?.doubleOrNull?.let(::formatNumber).orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toDoubleOrNull()?.let { number ->
                        val min = setting.min ?: Double.NEGATIVE_INFINITY
                        val max = setting.max ?: Double.POSITIVE_INFINITY
                        onChange(JsonPrimitive(number.coerceIn(min, max)))
                    }
                },
                label = { Text(setting.label) },
                supportingText = setting.description.takeIf(String::isNotBlank)?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            var text by remember(value) { mutableStateOf(primitive?.contentOrNull.orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.take(500)
                    onChange(JsonPrimitive(text))
                },
                label = { Text(setting.label) },
                supportingText = setting.description.takeIf(String::isNotBlank)?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
