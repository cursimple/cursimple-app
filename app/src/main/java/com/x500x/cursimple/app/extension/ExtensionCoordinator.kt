package com.x500x.cursimple.app.extension

import android.content.Context
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.event.ScheduleEventRepository
import com.x500x.cursimple.feature.widget.ComponentWidgetAvailability
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import com.x500x.cursimple.feature.plugin.extension.ExtensionHostSettings
import com.x500x.cursimple.feature.plugin.extension.ExtensionLoginState
import com.x500x.cursimple.feature.plugin.extension.ExtensionStore
import com.x500x.cursimple.feature.plugin.extension.ExtensionSyncEngine
import com.x500x.cursimple.feature.plugin.extension.ExtensionSyncOutcome
import com.x500x.cursimple.feature.plugin.extension.isIgnored
import com.x500x.cursimple.feature.plugin.extension.withItemIgnored
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared coordinator for sync, new-content notices, deadline alarms and timetable events. */
class ExtensionCoordinator(
    private val context: Context,
    private val pluginManager: PluginManager,
    private val preferences: UserPreferencesRepository,
    private val events: ScheduleEventRepository,
    private val scope: CoroutineScope,
) {
    val store: ExtensionStore = ExtensionStore.get(context)
    private val engine = ExtensionSyncEngine(context, pluginManager, store)
    private val outputsLock = Mutex()
    private val syncDueLock = Mutex()
    private var knownOwners = emptySet<String>()

    init {
        scope.launch {
            store.ensureLoaded()
            combine(
                pluginManager.installedPluginsFlow,
                preferences.preferencesFlow.map { it.enabledPluginIds }.distinctUntilChanged(),
                store.all,
            ) { records, _, data -> records.filter { it.isExtension }.map { it.pluginId }.toSet() to data.keys }
                .collect { (installed, saved) ->
                    runCatching { publishWidgetDefinitions() }
                        .onFailure { ReminderLogger.warn("extension.component_widgets.availability_failed", emptyMap(), it) }
                    val orphanOwners = events.eventsFlow.first().mapNotNull { it.source?.componentId }.toSet()
                    val owners = knownOwners + installed + saved + orphanOwners
                    knownOwners = installed
                    for (id in saved - installed) store.remove(id)
                    owners.forEach { id ->
                        runCatching { applyOutputs(id) }
                            .onFailure { ReminderLogger.warn("extension.lifecycle.outputs_failed", mapOf("pluginId" to id), it) }
                    }
                }
        }
    }

    suspend fun syncNow(record: InstalledPluginRecord): ExtensionSyncOutcome {
        if (!isActive(record)) return ExtensionSyncOutcome.Skipped(record.pluginId, "disabled_or_removed")
        val outcome = engine.sync(record)
        if (!isActive(record)) {
            applyOutputs(record.pluginId)
            return ExtensionSyncOutcome.Skipped(record.pluginId, "disabled_or_removed")
        }
        dispatch(record, outcome)
        return outcome
    }

    suspend fun markRead(record: InstalledPluginRecord, itemId: String): ExtensionData {
        check(isActive(record)) { "组件未启用或已移除" }
        val saved = engine.markRead(record, itemId)
        ExtensionNotifier.cancelItem(context, record.pluginId, itemId)
        com.x500x.cursimple.core.data.notification.NotificationOutbox.get(context).cancelSource(record.pluginId, itemId)
        applyOutputs(record.pluginId)
        return saved
    }

    suspend fun setItemIgnored(record: InstalledPluginRecord, itemId: String, ignored: Boolean): ExtensionData {
        check(isActive(record)) { "组件未启用或已移除" }
        val saved = store.updateIfPresent(record.pluginId) { it.withItemIgnored(itemId, ignored) }
            ?: error("组件已移除")
        if (ignored) {
            ExtensionNotifier.cancelItem(context, record.pluginId, itemId)
            com.x500x.cursimple.core.data.notification.NotificationOutbox.get(context).cancelSource(record.pluginId, itemId)
        }
        applyOutputs(record.pluginId)
        return saved
    }

    /** Launch due sync asynchronously when online without holding the guard broadcast open. */
    fun syncDueInBackground(reason: String) {
        if (!hasInternet()) return
        scope.launch {
            runCatching { syncDue() }
                .onFailure { ReminderLogger.warn("extension.sync.guard_failed", mapOf("reason" to reason), it) }
        }
    }

    private fun hasInternet(): Boolean = runCatching {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /** Run due components once, skipping those already syncing. */
    suspend fun syncDue(now: Long = System.currentTimeMillis()) {
        if (!syncDueLock.tryLock()) return
        try {
            syncDueLocked(now)
        } finally {
            syncDueLock.unlock()
        }
    }

    private suspend fun syncDueLocked(now: Long) {
        store.ensureLoaded()
        for (record in enabledExtensions()) {
            val data = store.get(record.pluginId)
            if (!data.loggedIn) continue
            val sinceLast = now - (data.lastAttemptAt ?: 0L)
            if (sinceLast < ExtensionHostSettings.MIN_SYNC_INTERVAL_MINUTES * 60_000L - SYNC_SLACK_MS) continue
            val manifest = runCatching { pluginManager.loadExtensionPackage(record).first }.getOrNull() ?: continue
            val interval = effectiveIntervalMinutes(data, manifest)
            val last = data.lastAttemptAt ?: 0L
            if (now - last < interval * 60_000L - SYNC_SLACK_MS) continue
            runCatching { syncNow(record) }
                .onFailure { ReminderLogger.warn("extension.sync.background_failed", mapOf("pluginId" to record.pluginId), it) }
        }
    }

    /**
     * Reconcile reminders and timetable events after settings, login or removal without
     * fetching again.
     */
    fun onDataChanged(pluginId: String) {
        scope.launch {
            runCatching { applyOutputs(pluginId) }
                .onFailure { ReminderLogger.warn("extension.outputs.failed", mapOf("pluginId" to pluginId), it) }
        }
    }

    suspend fun fireDueReminders(now: Long = System.currentTimeMillis()) {
        store.ensureLoaded()
        val titles = extensionTitles()
        for ((pluginId, data) in activeData()) {
            if (!data.loggedIn || data.host.dueReminderHours <= 0) continue
            val due = dueWindowItems(data, now)
            if (due.isEmpty()) continue
            val title = titles[pluginId] ?: pluginId
            due.forEach { item -> ExtensionNotifier.notifyDue(context, pluginId, title, item, now) }
            store.updateIfPresent(pluginId) { current ->
                if (current.sessionRevision != data.sessionRevision || !current.loggedIn) return@updateIfPresent null
                current.copy(remindedKeys = current.remindedKeys + due.map { "${it.id}@${it.dueAt}" })
            }
        }
        ExtensionDueScheduler.reschedule(context, activeData().values, now)
    }

    private suspend fun dispatch(record: InstalledPluginRecord, outcome: ExtensionSyncOutcome) {
        when (outcome) {
            is ExtensionSyncOutcome.Synced -> {
                if (outcome.data.host.notifyNew && outcome.newItems.isNotEmpty()) {
                    val current = store.get(record.pluginId)
                    ExtensionNotifier.notifyNewItems(context, record.pluginId, titleOf(outcome.manifest, record),
                        outcome.newItems.filter { !it.done && !it.historical && !current.isIgnored(it, System.currentTimeMillis()) })
                }
                applyOutputs(record.pluginId)
            }
            is ExtensionSyncOutcome.LoginRequired -> {
                if (outcome.firstTime) {
                    ExtensionNotifier.notifyLoginExpired(context, record.pluginId, titleOf(outcome.manifest, record))
                }
                applyOutputs(record.pluginId)
            }
            // Failed or skipped sync preserves prior notifications and events.
            is ExtensionSyncOutcome.Failed, is ExtensionSyncOutcome.Skipped -> Unit
        }
    }

    private suspend fun applyOutputs(pluginId: String) = outputsLock.withLock {
        store.ensureLoaded()
        val data = store.all.value[pluginId]
        val record = enabledExtensions().firstOrNull { it.pluginId == pluginId }
        // Remove events on logout or removal; retain useful cached deadlines after session expiry.
        val keep = data != null && record != null &&
            (data.loggedIn || data.loginState == ExtensionLoginState.Expired)
        val feedTypes = record?.let { runCatching { pluginManager.loadExtensionPackage(it).first.extension?.feedTypes }.getOrNull() }.orEmpty()
        data?.items?.filter { it.done || data.isIgnored(it, System.currentTimeMillis()) }?.forEach {
            ExtensionNotifier.cancelItem(context, pluginId, it.id)
            com.x500x.cursimple.core.data.notification.NotificationOutbox.get(context).cancelSource(pluginId, it.id)
        }
        ExtensionScheduleBridge.apply(
            events = events,
            pluginId = pluginId,
            data = data?.takeIf { keep && it.host.addToSchedule },
            now = System.currentTimeMillis(),
            feedTypes = feedTypes,
        )
        if (data == null || record == null || data.loginState == ExtensionLoginState.LoggedOut || data.loginState == ExtensionLoginState.Never) ExtensionNotifier.cancelAll(context, pluginId)
        ExtensionDueScheduler.reschedule(context, activeData().values, System.currentTimeMillis())
        refreshOwnedWidgets(pluginId)
    }

    private suspend fun publishWidgetDefinitions() {
        val definitions = enabledExtensions().flatMap { record ->
            val manifest = pluginManager.loadExtensionPackage(record).first
            manifest.extension?.widgets.orEmpty().map { spec ->
                com.x500x.cursimple.core.data.widget.ComponentWidgetDefinition(record.pluginId, record.packageRevision, spec)
            }
        }
        val changed = com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry.write(context, definitions)
        ComponentWidgetAvailability.update(context, definitions.isNotEmpty())
        if (changed) {
            com.x500x.cursimple.feature.widget.WidgetCatalog.notifyInstalledChanged(context)
            com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context)
        }
    }

    suspend fun renderWidget(definition: com.x500x.cursimple.core.data.widget.ComponentWidgetDefinition, width: Float, height: Float): com.x500x.cursimple.feature.widget.ComponentWidgetRender {
        val record = enabledExtensions().firstOrNull { it.pluginId == definition.componentId && it.packageRevision == definition.revision }
            ?: error("Widget owner unavailable")
        val manifest = pluginManager.loadExtensionPackage(record).first
        require(manifest.extension?.widgets?.any { it == definition.spec } == true)
        val rendered = ComponentWidgetRenderer.render(com.x500x.cursimple.core.data.AppLocale.wrap(context), record, manifest,
            store.all.value[record.pluginId] ?: ExtensionData(record.pluginId), definition, width, height)
        require(isActive(record)) { "Widget owner changed" }
        return rendered
    }

    private suspend fun refreshOwnedWidgets(pluginId: String) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val entry = com.x500x.cursimple.feature.widget.WidgetCatalog.entries(context).first { it.fromComponents }
        val ids = (listOf(entry.provider) + entry.vendorProviders).flatMap { manager.getAppWidgetIds(it).toList() }
            .filter { com.x500x.cursimple.core.data.widget.ComponentWidgetBindings.get(context, it)?.startsWith("$pluginId/") == true }
        if (ids.isNotEmpty()) com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, ids.toIntArray())
    }

    private suspend fun enabledExtensions(): List<InstalledPluginRecord> {
        val installed = pluginManager.getInstalledPlugins()
        val enabled = preferences.preferencesFlow.first().enabledPluginIds
        return activeExtensionRecords(installed, enabled)
    }

    private suspend fun isActive(record: InstalledPluginRecord): Boolean =
        enabledExtensions().any { it.packageRevision == record.packageRevision }

    private suspend fun activeData(): Map<String, ExtensionData> = enabledExtensions().mapNotNull { record ->
        store.all.value[record.pluginId]?.let { record.pluginId to it }
    }.toMap()

    private suspend fun extensionTitles(): Map<String, String> =
        pluginManager.getInstalledPlugins().filter { it.isExtension }.associate { record ->
            val manifest = runCatching { pluginManager.loadExtensionPackage(record).first }.getOrNull()
            record.pluginId to titleOf(manifest, record)
        }

    companion object {
        private const val SYNC_SLACK_MS = 5 * 60_000L

        fun titleOf(manifest: PluginManifest?, record: InstalledPluginRecord): String =
            manifest?.extension?.title?.takeIf(String::isNotBlank) ?: record.name

        fun effectiveIntervalMinutes(data: ExtensionData, manifest: PluginManifest): Int =
            (data.host.syncIntervalMinutes ?: manifest.extension?.syncIntervalMinutes ?: 60)
                .coerceAtLeast(ExtensionHostSettings.MIN_SYNC_INTERVAL_MINUTES)

        fun dueWindowItems(data: ExtensionData, now: Long) = data.items.filter { item ->
            val due = item.dueAt ?: return@filter false
            val remindAt = due - data.host.dueReminderHours * 3_600_000L
            !item.done && !item.historical && !data.isIgnored(item, now) && now in remindAt until due && "${item.id}@$due" !in data.remindedKeys
        }
    }
}

internal fun activeExtensionRecords(installed: List<InstalledPluginRecord>, enabled: Set<String>): List<InstalledPluginRecord> =
    installed.filter { it.isExtension && it.compatibilityStatus == PluginCompatibilityStatus.Compatible && isPluginInstallEnabled(it, enabled, installed) }
        .groupBy { it.pluginId }.values.map { records -> records.maxWith(compareBy<InstalledPluginRecord> { it.versionCode }.thenBy { it.installedAt }) }
