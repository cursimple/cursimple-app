package com.x500x.cursimple.core.data.notification

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class OutboundNotification(
    val id: String, val kind: String, val title: String, val body: String,
    val sourceId: String, val sourceName: String,
    val itemId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 24 * 3_600_000L,
)

@Serializable
data class NotificationTarget(
    val id: String, val name: String, val enabled: Boolean = true,
    val kinds: Set<String> = emptySet(),
) {
    fun accepts(kind: String): Boolean = enabled && (kinds.isEmpty() || kind in kinds)
}

@Serializable
data class NotificationReceipt(
    val targetId: String, val status: String, val error: String = "", val at: Long = System.currentTimeMillis(),
)

@Serializable
data class NotificationDelivery(
    val receiverId: String, val notification: OutboundNotification,
    val targetIds: Set<String>, val receipts: List<NotificationReceipt> = emptyList(),
    val attempts: Int = 0, val nextAttemptAt: Long = 0L, val cancelled: Boolean = false,
    val manualRetry: Boolean = false,
) {
    fun pendingTargets(now: Long): Set<String> {
        if (cancelled || notification.expiresAt <= now) return emptySet()
        val pending = targetIds - receipts.filter { it.status in setOf("sent", "accepted", "skipped", "unknown") }.map { it.targetId }.toSet()
        return if (attempts < 6) pending else pending.filter { id -> receipts.any { it.targetId == id && it.status in setOf("queued", "querying") } }.toSet()
    }
    fun ready(now: Long): Boolean = nextAttemptAt <= now && pendingTargets(now).isNotEmpty()
}

/** Persist each confirmed target immediately so partial retries skip successful deliveries. */
class NotificationOutbox internal constructor(private val file: File) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(NotificationDelivery.serializer())

    suspend fun list(receiverId: String? = null): List<NotificationDelivery> = mutex.withLock {
        read().filter { receiverId == null || it.receiverId == receiverId }
    }

    suspend fun enqueue(receiverId: String, notification: OutboundNotification, targets: List<NotificationTarget>): Boolean = mutate { rows ->
        val ids = targets.filter { it.accepts(notification.kind) }.map { it.id }.toSet()
        if (ids.isEmpty() || rows.any { it.receiverId == receiverId && it.notification.id == notification.id }) rows
        else rows + NotificationDelivery(receiverId, notification.copy(title = notification.title.take(200), body = notification.body.take(4000)), ids)
    }

    suspend fun begin(receiverId: String, id: String, now: Long): NotificationDelivery? {
        var started: NotificationDelivery? = null
        mutate { rows -> rows.map { row ->
            if (row.receiverId == receiverId && row.notification.id == id && row.ready(now)) {
                val queriesOnly = row.pendingTargets(now).all { id -> row.receipts.any { it.targetId == id && it.status in setOf("queued", "querying") } }
                row.copy(attempts = if (queriesOnly) row.attempts else row.attempts + 1,
                    nextAttemptAt = now + if (queriesOnly) 60_000L else retryDelay(row.attempts)).also { started = it }
            } else row
        } }
        return started
    }

    suspend fun receipt(receiverId: String, id: String, receipt: NotificationReceipt) = mutate { rows -> rows.map { row ->
        if (row.receiverId == receiverId && row.notification.id == id && !row.cancelled && receipt.targetId in row.targetIds) {
            val previous = row.receipts.firstOrNull { it.targetId == receipt.targetId }
            if (previous?.status == "sent") row else row.copy(receipts = row.receipts.filterNot { it.targetId == receipt.targetId } + receipt.copy(error = receipt.error.take(300)))
        } else row
    } }

    suspend fun retry(receiverId: String, id: String? = null) = mutate { rows -> rows.map { row ->
        if (row.receiverId == receiverId && (id == null || row.notification.id == id) && !row.cancelled && row.notification.expiresAt > System.currentTimeMillis())
            row.copy(attempts = 0, nextAttemptAt = 0, manualRetry = true, receipts = row.receipts.mapNotNull {
                when (it.status) {
                    "sent", "accepted", "skipped", "queued", "querying" -> it
                    else -> null
                }
            }) else row
    } }

    suspend fun cancelSource(sourceId: String, itemId: String? = null) = mutate { rows -> rows.map {
        if (it.notification.sourceId == sourceId && (itemId == null || it.notification.itemId == itemId)) it.copy(cancelled = true) else it
    } }
    suspend fun cancelMessage(receiverId: String, messageId: String) = mutate { rows -> rows.map {
        if (it.receiverId == receiverId && it.notification.id == messageId) it.copy(cancelled = true) else it
    } }
    suspend fun retainTargets(receiverId: String, enabledIds: Set<String>) = mutate { rows -> rows.map {
        if (it.receiverId == receiverId) {
            val finished = it.receipts.filter { receipt -> receipt.status in setOf("sent", "accepted", "skipped") }.map { receipt -> receipt.targetId }.toSet()
            val removed = it.targetIds - enabledIds - finished
            it.copy(receipts = it.receipts.filterNot { receipt -> receipt.targetId in removed } + removed.map { id -> NotificationReceipt(id, "skipped", "目标已停用或移除") })
        } else it
    } }
    suspend fun removeReceiver(receiverId: String) = mutate { rows -> rows.filterNot { it.receiverId == receiverId } }
    suspend fun recoverUnconfirmed(receiverId: String) = mutate { rows -> rows.map { row ->
        if (row.receiverId == receiverId) row.copy(receipts = row.receipts.map {
            when (it.status) {
                "sending" -> it.copy(status = "unknown", error = "上次发送中断，请先在目标平台核对")
                "querying" -> it.copy(status = "queued", error = "结果查询中断，等待继续核对")
                else -> it
            }
        }) else row
    } }

    private suspend fun mutate(transform: (List<NotificationDelivery>) -> List<NotificationDelivery>): Boolean = mutex.withLock {
        val before = read()
        val after = transform(before).takeLast(400)
        if (before == after) return@withLock false
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(json.encodeToString(serializer, after))
            check(temporary.renameTo(file)) { "通知记录保存失败" }
        }
        true
    }

    private suspend fun read(): List<NotificationDelivery> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, file.readText())
    }

    companion object {
        @Volatile private var instance: NotificationOutbox? = null
        fun get(context: Context): NotificationOutbox = instance ?: synchronized(this) {
            instance ?: NotificationOutbox(File(context.applicationContext.filesDir, "notification-outbox/messages.json")).also { instance = it }
        }
        internal fun retryDelay(attempt: Int): Long = (60_000L * (1L shl attempt.coerceIn(0, 6))).coerceAtMost(3_600_000L)
    }
}

object OutboundNotificationHooks {
    @Volatile var publish: ((OutboundNotification) -> Unit)? = null
    fun emit(notification: OutboundNotification) { publish?.invoke(notification) }
}
