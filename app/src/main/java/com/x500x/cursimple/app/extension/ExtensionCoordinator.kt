package com.x500x.cursimple.app.extension

import android.content.Context
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.event.ScheduleEventRepository
import com.x500x.cursimple.core.data.widget.PendingTaskFeed
import com.x500x.cursimple.feature.widget.ComponentWidgetAvailability
import com.x500x.cursimple.feature.widget.PendingTaskWidgetReceiver
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 扩展组件在 App 这一层的总调度：同步之后的「产出」都在这里办——
 * 新内容通知、截止提醒闹钟、写进课表的事务。
 *
 * 界面上的「立即同步」、后台任务、截止提醒的接收器都走这里，产出的规则只写一遍。
 */
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
                    // 组件小组件跟着组件走：有启用中的组件才上架
                    runCatching { ComponentWidgetAvailability.update(context, enabledExtensions().isNotEmpty()) }
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

    /** 同步一个组件，并把通知、提醒、课表事务都办掉 */
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

    /**
     * 静默守护闹钟唤醒时调：联网才发起，到没到点由 [syncDue] 按各组件自己的间隔判断。
     * 立即返回，真正的同步在后台跑，不占着守护闹钟的广播。
     */
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

    /** 后台任务和守护闹钟都会调：到了各自同步间隔的组件逐个跑一遍；已经在跑就不重复跑 */
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
            // 守护闹钟每 5 分钟就来问一次：离上次不到最短间隔的，连组件包都不用读
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

    /** 设置变了、登录状态变了、组件被移除：不重新同步，只把提醒和课表事务按现状重铺 */
    fun onDataChanged(pluginId: String) {
        scope.launch {
            runCatching { applyOutputs(pluginId) }
                .onFailure { ReminderLogger.warn("extension.outputs.failed", mapOf("pluginId" to pluginId), it) }
        }
    }

    /** 截止提醒闹钟响了：到了提醒窗口的逐条发，再把下一个闹钟挂上 */
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
                    ExtensionNotifier.notifyNewItems(context, record.pluginId, titleOf(outcome.manifest, record), outcome.newItems)
                }
                applyOutputs(record.pluginId)
            }
            is ExtensionSyncOutcome.LoginRequired -> {
                if (outcome.firstTime) {
                    ExtensionNotifier.notifyLoginExpired(context, record.pluginId, titleOf(outcome.manifest, record))
                }
                applyOutputs(record.pluginId)
            }
            // 失败/跳过不用管：通知和事务保持上一次同步的样子
            is ExtensionSyncOutcome.Failed, is ExtensionSyncOutcome.Skipped -> Unit
        }
    }

    private suspend fun applyOutputs(pluginId: String) = outputsLock.withLock {
        store.ensureLoaded()
        val data = store.all.value[pluginId]
        val record = enabledExtensions().firstOrNull { it.pluginId == pluginId }
        // 组件被移除或登出：它加的事务全部撤掉；只是登录过期的，上次同步的截止时间照样有用，留着
        val keep = data != null && record != null &&
            (data.loggedIn || data.loginState == ExtensionLoginState.Expired)
        val feedTypes = record?.let { runCatching { pluginManager.loadExtensionPackage(it).first.extension?.feedTypes }.getOrNull() }.orEmpty()
        ExtensionScheduleBridge.apply(
            events = events,
            pluginId = pluginId,
            data = data?.takeIf { keep && it.host.addToSchedule },
            now = System.currentTimeMillis(),
            feedTypes = feedTypes,
        )
        if (data == null || record == null || data.loginState == ExtensionLoginState.LoggedOut || data.loginState == ExtensionLoginState.Never) ExtensionNotifier.cancelAll(context, pluginId)
        ExtensionDueScheduler.reschedule(context, activeData().values, System.currentTimeMillis())
        publishPendingTasks()
    }

    /** 把所有启用组件的待完成任务整份写给桌面小组件；内容没变就不刷新 */
    private suspend fun publishPendingTasks() {
        val tasks = enabledExtensions().flatMap { record ->
            val data = store.all.value[record.pluginId] ?: return@flatMap emptyList()
            val manifest = runCatching { pluginManager.loadExtensionPackage(record).first }.getOrNull()
            pendingTasksOf(record.pluginId, titleOf(manifest, record), data, manifest?.extension?.feedTypes.orEmpty())
        }
        if (PendingTaskFeed.write(context, tasks)) {
            runCatching { PendingTaskWidgetReceiver.updateWidgets(context) }
                .onFailure { ReminderLogger.warn("extension.pending_widget.refresh_failed", emptyMap(), it) }
        }
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
        /** WorkManager 的周期会有几分钟漂移，差一点点也算到点，免得每次都晚一整个周期 */
        private const val SYNC_SLACK_MS = 5 * 60_000L

        fun titleOf(manifest: PluginManifest?, record: InstalledPluginRecord): String =
            manifest?.extension?.title?.takeIf(String::isNotBlank) ?: record.name

        fun effectiveIntervalMinutes(data: ExtensionData, manifest: PluginManifest): Int =
            (data.host.syncIntervalMinutes ?: manifest.extension?.syncIntervalMinutes ?: 60)
                .coerceAtLeast(ExtensionHostSettings.MIN_SYNC_INTERVAL_MINUTES)

        /** 已经进了「截止前 N 小时」窗口、还没截止、还没做完、这一版截止时间还没提醒过的 */
        fun dueWindowItems(data: ExtensionData, now: Long) = data.items.filter { item ->
            val due = item.dueAt ?: return@filter false
            val remindAt = due - data.host.dueReminderHours * 3_600_000L
            !item.done && now in remindAt until due && "${item.id}@$due" !in data.remindedKeys
        }
    }
}

internal fun activeExtensionRecords(installed: List<InstalledPluginRecord>, enabled: Set<String>): List<InstalledPluginRecord> =
    installed.filter { it.isExtension && it.compatibilityStatus == PluginCompatibilityStatus.Compatible && isPluginInstallEnabled(it, enabled, installed) }
        .groupBy { it.pluginId }.values.map { records -> records.maxWith(compareBy<InstalledPluginRecord> { it.versionCode }.thenBy { it.installedAt }) }
