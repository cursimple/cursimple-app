package com.x500x.cursimple.app.notification

import android.content.Context
import com.x500x.cursimple.app.extension.activeExtensionRecords
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.memo.MemoRepository
import com.x500x.cursimple.core.data.notification.*
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.time.ZoneId
import java.util.UUID

/**
 * Only event, queue and lifecycle handling belongs here; platform adapters live in components.
 */
class NotificationDeliveryCoordinator(
    private val context: Context, private val plugins: PluginManager,
    private val preferences: UserPreferencesRepository, private val memos: MemoRepository,
    private val scope: CoroutineScope,
) {
    private val store = ExtensionStore.get(context)
    private val secure = ExtensionSecureConfig(context)
    private val outbox = NotificationOutbox.get(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val locks = mutableMapOf<String, Mutex>()
    private val configLock = Mutex()
    private fun lock(id: String): Mutex = synchronized(locks) { locks.getOrPut(id) { Mutex() } }

    init {
        OutboundNotificationHooks.publish = { event -> publish(event) }
        scope.launch {
            plugins.installedPluginsFlow.collect { records ->
                try {
                    val installed = records.map { it.pluginId }.toSet()
                    (outbox.list().map { it.receiverId }.toSet() + secure.componentIds()).filterNot { it in installed }.forEach {
                        outbox.removeReceiver(it); secure.remove(it)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { ReminderLogger.warn("notification.lifecycle.failed", emptyMap(), error) }
            }
        }
        NotificationDeliveryWorker.schedulePeriodic(context)
    }

    fun onMemosChanged() {
        scope.launch {
            try {
                if (activeRecords().any { record ->
                    isReceiver(plugins.loadExtensionPackage(record).first) &&
                        secure.read(record.pluginId).targets.any { it.accepts("memo.due") }
                }) NotificationDeliveryWorker.enqueue(context)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { ReminderLogger.warn("notification.memo.changed_failed", emptyMap(), error) }
        }
    }

    fun publish(event: OutboundNotification) {
        scope.launch {
            runCatching {
                if (event.expiresAt <= System.currentTimeMillis() || !validSource(event)) return@runCatching
                var added = false
                for (record in activeRecords()) {
                    val manifest = plugins.loadExtensionPackage(record).first
                    if (!isReceiver(manifest)) continue
                    added = outbox.enqueue(record.pluginId, event, secure.read(record.pluginId).targets) || added
                }
                if (added) NotificationDeliveryWorker.enqueue(context)
            }.onFailure { ReminderLogger.warn("notification.outbox.enqueue_failed", emptyMap(), it) }
        }
    }

    suspend fun command(record: InstalledPluginRecord, command: String, payload: JsonObject): JsonElement {
        val (manifest, entry) = checkedPackage(record)
        require(isReceiver(manifest)) { "组件未声明通知出口能力" }
        return when (command) {
            "notification.config.get" -> {
                require(PluginPermission.SecureStorage in manifest.permissions) { "组件未声明加密存储权限" }
                json.encodeToJsonElement(ExtensionSecureConfiguration.serializer(), secure.read(record.pluginId))
            }
            "notification.config.save" -> configLock.withLock {
                require(PluginPermission.SecureStorage in manifest.permissions && PluginPermission.NetworkProxy in manifest.permissions) { "组件缺少加密配置或网络权限" }
                val values = payload["values"] as? JsonObject ?: error("绑定配置无效")
                val hosts = (payload["hosts"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content.lowercase() }.distinct()
                require(hosts.size <= 24 && hosts.all { it.matches(Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) && !it.contains("..") }) { "绑定目标域名无效" }
                val targets = json.decodeFromJsonElement(ListSerializer(NotificationTarget.serializer()), payload["targets"] ?: JsonArray(emptyList()))
                require(targets.size <= 12 && targets.map { it.id }.distinct().size == targets.size && targets.all {
                    it.id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && it.name.length in 1..80 && it.kinds.all { kind -> kind in KINDS }
                }) { "通知目标无效" }
                val previous = secure.read(record.pluginId)
                fun rawTargets(configuration: JsonObject) = (configuration["targets"] as? JsonArray).orEmpty()
                    .filterIsInstance<JsonObject>().associateBy { (it["id"] as? JsonPrimitive)?.contentOrNull }
                val oldTargets = rawTargets(previous.values)
                val newTargets = rawTargets(values)
                val sessions = JsonObject(previous.sessions.filter { (id, _) -> oldTargets[id] != null && oldTargets[id] == newTargets[id] })
                secure.save(record.pluginId, ExtensionSecureConfiguration(values, hosts, targets, sessions))
                store.update(record.pluginId) { it.copy(notificationTargets = targets) }
                outbox.retainTargets(record.pluginId, targets.filter { it.enabled }.map { it.id }.toSet())
                NotificationDeliveryWorker.enqueue(context)
                JsonPrimitive(true)
            }
            "notification.history" -> json.encodeToJsonElement(ListSerializer(NotificationDelivery.serializer()), outbox.list(record.pluginId).asReversed().take(80))
            "notification.fetch" -> {
                require(PluginPermission.NetworkProxy in manifest.permissions) { "组件未声明宿主网络传输权限" }
                val configuration = secure.read(record.pluginId)
                ExtensionNativeTransport.fetch(ExtensionRunRequest.from(manifest, entry, store.get(record.pluginId), ExtensionRunMode.DeliverNotifications)
                    .copy(isolated = true, allowedHosts = manifest.allowedHosts + configuration.hosts), payload)
            }
            "notification.retry" -> {
                outbox.retry(record.pluginId, (payload["id"] as? JsonPrimitive)?.contentOrNull)
                NotificationDeliveryWorker.enqueue(context)
                JsonPrimitive(true)
            }
            "notification.test" -> lock(record.pluginId).withLock {
                val targetId = (payload["targetId"] as? JsonPrimitive)?.contentOrNull ?: error("请选择通知目标")
                val target = secure.read(record.pluginId).targets.firstOrNull { it.id == targetId } ?: error("请先保存绑定")
                val event = OutboundNotification("test/${UUID.randomUUID()}", "test", "课简通知测试", "这是一条连接测试消息。收到它表示当前目标已接入。", "cursimple", "课简")
                outbox.enqueue(record.pluginId, event, listOf(target.copy(enabled = true, kinds = emptySet())))
                val delivery = outbox.begin(record.pluginId, event.id, System.currentTimeMillis()) ?: error("测试发送未启动")
                runDelivery(record, manifest, entry, delivery)
                val latest = outbox.list(record.pluginId).first { it.notification.id == event.id }
                val receipt = latest.receipts.firstOrNull { it.targetId == targetId }
                if (receipt?.status !in setOf("sent", "accepted", "queued")) error(receipt?.error?.ifBlank { null } ?: "平台尚未确认发送，请查看发送记录")
                if (receipt?.status == "queued") NotificationDeliveryWorker.enqueue(context)
                json.encodeToJsonElement(NotificationReceipt.serializer(), requireNotNull(receipt))
            }
            else -> error("不支持的通知出口操作")
        }
    }

    suspend fun drain(): Boolean {
        scanMemoDeadlines()
        val now = System.currentTimeMillis()
        for (record in activeRecords()) lock(record.pluginId).withLock {
            if (activeRecords().none { it.packageRevision == record.packageRevision }) return@withLock
            val (manifest, entry) = checkedPackage(record)
            if (!isReceiver(manifest)) return@withLock
            val targets = secure.read(record.pluginId).targets
            outbox.recoverUnconfirmed(record.pluginId)
            outbox.retainTargets(record.pluginId, targets.filter { it.enabled }.map { it.id }.toSet())
            outbox.list(record.pluginId).filter { it.ready(now) }.take(4).forEach { row ->
                if (!validSource(row.notification)) { outbox.cancelMessage(record.pluginId, row.notification.id); return@forEach }
                val started = outbox.begin(record.pluginId, row.notification.id, now) ?: return@forEach
                runDelivery(record, manifest, entry, started)
            }
        }
        val active = activeRecords().map { it.pluginId }.toSet()
        return outbox.list().any { it.receiverId in active && it.pendingTargets(System.currentTimeMillis()).isNotEmpty() }
    }

    private suspend fun runDelivery(record: InstalledPluginRecord, manifest: PluginManifest, entry: String, delivery: NotificationDelivery) {
        val configuration = try { secure.read(record.pluginId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            delivery.targetIds.forEach { id -> outbox.receipt(record.pluginId, delivery.notification.id,
                NotificationReceipt(id, "failed", error.message ?: "绑定配置不可用，请重新绑定")) }
            return
        }
        val request = ExtensionRunRequest.from(manifest, entry, store.get(record.pluginId), ExtensionRunMode.DeliverNotifications).copy(
            isolated = true,
            allowedHosts = manifest.allowedHosts + configuration.hosts,
            secureConfiguration = configuration.values,
            secureSessions = configuration.sessions,
            notifications = JsonArray(listOf(json.encodeToJsonElement(NotificationDelivery.serializer(), delivery))),
            onReceipt = receipt@{ payload ->
                val messageId = (payload["messageId"] as? JsonPrimitive)?.contentOrNull
                val targetId = (payload["targetId"] as? JsonPrimitive)?.contentOrNull
                val status = (payload["status"] as? JsonPrimitive)?.contentOrNull
                require(messageId == delivery.notification.id && targetId in delivery.targetIds && status in RECEIPT_STATUSES) { "发送回执无效" }
                if (plugins.getInstalledPlugins().none { it.packageRevision == record.packageRevision }) return@receipt false
                if (status in setOf("sending", "querying")) {
                    val live = secure.read(record.pluginId)
                    val target = live.targets.firstOrNull { it.id == targetId }
                    val enabled = delivery.notification.kind == "test" || target?.accepts(delivery.notification.kind) == true
                    if (!enabled || !sameBinding(live, configuration) || !validSource(delivery.notification)) {
                        outbox.receipt(record.pluginId, delivery.notification.id, NotificationReceipt(requireNotNull(targetId),
                            if (enabled && !sameBinding(live, configuration)) (if (status == "querying") "unknown" else "failed") else "skipped",
                            if (enabled && !sameBinding(live, configuration)) (if (status == "querying") "原消息已提交，绑定已修改，请在平台核对" else "绑定已修改，等待按新配置发送") else "目标停用或源内容已完成"))
                        return@receipt false
                    }
                }
                outbox.receipt(record.pluginId, delivery.notification.id, NotificationReceipt(requireNotNull(targetId), requireNotNull(status),
                    (payload["error"] as? JsonPrimitive)?.contentOrNull.orEmpty()))
                true
            },
            onSession = session@{ payload -> configLock.withLock {
                val targetId = (payload["targetId"] as? JsonPrimitive)?.contentOrNull
                val value = payload["value"] as? JsonObject ?: error("会话状态无效")
                require(targetId in delivery.targetIds && value.toString().length <= 12 * 1024) { "会话状态过大或目标无效" }
                if (plugins.getInstalledPlugins().none { it.packageRevision == record.packageRevision }) return@withLock false
                val live = secure.read(record.pluginId)
                if (!sameBinding(live, configuration)) return@withLock false
                secure.save(record.pluginId, live.copy(sessions = JsonObject(live.sessions + (requireNotNull(targetId) to value))))
                true
            } },
        )
        try {
            val run = ExtensionRuntime(context).run(request)
            if (run is ExtensionRunResult.Failed) {
                ReminderLogger.warn("notification.component.delivery_failed", mapOf("pluginId" to record.pluginId))
                val current = outbox.list(record.pluginId).firstOrNull { it.notification.id == delivery.notification.id }
                val handled = current?.receipts.orEmpty().filter { it.status in setOf("sent", "accepted", "queued", "querying", "skipped", "sending", "unknown") }.map { it.targetId }.toSet()
                (delivery.targetIds - handled).forEach { id ->
                    outbox.receipt(record.pluginId, delivery.notification.id, NotificationReceipt(id, "failed", run.message.take(300)))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        finally {
            withContext(NonCancellable) {
                val latest = outbox.list(record.pluginId).firstOrNull { it.notification.id == delivery.notification.id }
                latest?.receipts?.filter { it.status in setOf("sending", "querying") }?.forEach {
                    outbox.receipt(record.pluginId, delivery.notification.id, if (it.status == "querying")
                        it.copy(status = "queued", error = "结果查询未完成，稍后继续") else
                        it.copy(status = "unknown", error = "发送结果未确认，请先在平台核对再手动重试"))
                }
            }
        }
    }

    private fun sameBinding(a: ExtensionSecureConfiguration, b: ExtensionSecureConfiguration) =
        a.values == b.values && a.hosts == b.hosts && a.targets == b.targets

    private suspend fun scanMemoDeadlines() {
        val prefs = preferences.preferencesFlow.first()
        val zone = prefs.appTimeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        for (note in memos.notesFlow.first()) {
            if (note.completed || (note.checklist.hasItems && note.checklist.done == note.checklist.total)) continue
            val due = note.dueDateTime?.atZone(zone)?.toInstant()?.toEpochMilli() ?: continue
            if (now !in (due - 24 * 3_600_000L) until due) continue
            val event = OutboundNotification("memo/${note.id}@$due", "memo.due", note.title.ifBlank { "笔记待办到期" },
                listOf(note.courseTitle, "截止：${note.dueAt}", note.body.take(1000)).filter(String::isNotBlank).joinToString("\n"), "memo", "笔记待办", note.id, expiresAt = due)
            for (record in activeRecords()) {
                val manifest = plugins.loadExtensionPackage(record).first
                if (isReceiver(manifest)) outbox.enqueue(record.pluginId, event, secure.read(record.pluginId).targets)
            }
        }
        val activeIds = memos.notesFlow.first().filter { !it.completed && !(it.checklist.hasItems && it.checklist.done == it.checklist.total) }.map { it.id }.toSet()
        outbox.list().filter { it.notification.sourceId == "memo" && it.notification.itemId !in activeIds }.forEach { outbox.cancelSource("memo", it.notification.itemId) }
    }

    private suspend fun checkedPackage(record: InstalledPluginRecord): Pair<PluginManifest, String> {
        check(plugins.getInstalledPlugins().any { it.packageRevision == record.packageRevision }) { "组件已更新或移除" }
        return plugins.loadExtensionPackage(record).also { if (it.first.extension?.notificationReceiver == true) check(isReceiver(it.first)) { "组件缺少通知出口权限" } }
    }
    private suspend fun validSource(event: OutboundNotification): Boolean {
        if (event.kind.startsWith("component.")) {
            if (activeRecords().none { it.pluginId == event.sourceId }) return false
            val data = store.get(event.sourceId)
            val item = data.items.firstOrNull { it.id == event.itemId } ?: return false
            return !item.done && !item.historical && !data.isIgnored(item, System.currentTimeMillis()) &&
                (event.kind != "component.due" || item.dueAt == event.expiresAt)
        }
        if (event.kind == "memo.due") {
            val note = memos.notesFlow.first().firstOrNull { it.id == event.itemId } ?: return false
            val prefs = preferences.preferencesFlow.first()
            val zone = prefs.appTimeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            return !note.completed && !(note.checklist.hasItems && note.checklist.done == note.checklist.total) &&
                note.dueDateTime?.atZone(zone)?.toInstant()?.toEpochMilli() == event.expiresAt
        }
        return true
    }
    private suspend fun activeRecords(): List<InstalledPluginRecord> = activeExtensionRecords(plugins.getInstalledPlugins(), preferences.preferencesFlow.first().enabledPluginIds)
    private fun isReceiver(manifest: PluginManifest): Boolean = manifest.extension?.notificationReceiver == true && PluginPermission.NotificationReceive in manifest.permissions
    companion object {
        val KINDS = setOf("class", "memo.due", "component.new", "component.due")
        private val RECEIPT_STATUSES = setOf("sending", "sent", "accepted", "queued", "querying", "failed", "unknown", "skipped")
    }
}
