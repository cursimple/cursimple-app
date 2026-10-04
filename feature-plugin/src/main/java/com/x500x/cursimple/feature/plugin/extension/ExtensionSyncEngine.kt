package com.x500x.cursimple.feature.plugin.extension

import android.content.Context
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** 一次同步的结果；[Synced] 带出这次新冒出来的条目，宿主据此发通知 */
sealed interface ExtensionSyncOutcome {
    val pluginId: String

    data class Synced(
        override val pluginId: String,
        val manifest: PluginManifest,
        val data: ExtensionData,
        val newItems: List<ExtensionFeedItem>,
    ) : ExtensionSyncOutcome

    /** 登录失效；[firstTime] 表示这是刚发现的，宿主只在这一次发「请重新登录」 */
    data class LoginRequired(
        override val pluginId: String,
        val manifest: PluginManifest,
        val firstTime: Boolean,
    ) : ExtensionSyncOutcome

    data class Failed(override val pluginId: String, val message: String) : ExtensionSyncOutcome

    /** 没登录过、被禁用、不兼容，这次不跑 */
    data class Skipped(override val pluginId: String, val reason: String) : ExtensionSyncOutcome
}

/**
 * 扩展组件的同步：跑入口脚本的 sync，把结果并进 [ExtensionStore]。
 *
 * 通知、截止提醒、写课表这些「产出」不在这里做，由 App 层拿 [ExtensionSyncOutcome] 去办——
 * 这一层只管数据，界面上的「立即同步」和后台任务共用它。
 */
class ExtensionSyncEngine(
    context: Context,
    private val pluginManager: PluginManager,
    private val store: ExtensionStore = ExtensionStore.get(context),
    private val runtime: ExtensionRuntime = ExtensionRuntime(context),
) {
    /** 同一个组件同一时刻只跑一份：后台任务和用户点的「立即同步」撞上时，后来的排队 */
    private val locks = mutableMapOf<String, Mutex>()

    suspend fun sync(record: InstalledPluginRecord, now: Long = System.currentTimeMillis()): ExtensionSyncOutcome {
        val pluginId = record.pluginId
        if (!record.isExtension) return ExtensionSyncOutcome.Skipped(pluginId, "not_extension")
        if (record.compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
            return ExtensionSyncOutcome.Skipped(pluginId, "incompatible")
        }
        return lockOf(pluginId).withLock { syncLocked(record, now) }
    }

    private suspend fun syncLocked(record: InstalledPluginRecord, now: Long): ExtensionSyncOutcome {
        val pluginId = record.pluginId
        if (!isCurrent(record)) return ExtensionSyncOutcome.Skipped(pluginId, "removed_or_updated")
        val before = store.get(pluginId)
        if (before.loginState == ExtensionLoginState.Never || before.loginState == ExtensionLoginState.LoggedOut) {
            return ExtensionSyncOutcome.Skipped(pluginId, "not_logged_in")
        }
        val (manifest, entry) = runCatching { pluginManager.loadExtensionPackage(record) }.getOrElse { error ->
            return fail(pluginId, now, "读不到组件包：${error.message}")
        }
        if (manifest.extension == null) return fail(pluginId, now, "组件包缺少 extension 段")

        val request = ExtensionRunRequest.from(manifest, entry, before, ExtensionRunMode.Sync)
        PluginLogger.info("extension.sync.start", mapOf("pluginId" to pluginId))
        val run = runtime.run(request)
        if (!isCurrent(record)) return ExtensionSyncOutcome.Skipped(pluginId, "removed_or_updated")
        return when (run) {
            is ExtensionRunResult.Failed -> fail(pluginId, now, run.message, before)
            is ExtensionRunResult.Completed -> {
                if (run.result.boolean("loginRequired") == true) {
                    val firstTime = before.loginState != ExtensionLoginState.Expired || !before.expiredNotified
                    val saved = store.updateIfPresent(pluginId) {
                        if (!canAcceptSync(before, it)) return@updateIfPresent null
                        it.copy(
                            loginState = ExtensionLoginState.Expired,
                            state = run.state,
                            lastAttemptAt = now,
                            lastError = null,
                            expiredNotified = true,
                        )
                    } ?: return ExtensionSyncOutcome.Skipped(pluginId, "session_changed")
                    PluginLogger.warn("extension.sync.login_required", mapOf("pluginId" to pluginId))
                    ExtensionSyncOutcome.LoginRequired(pluginId, manifest, firstTime)
                } else {
                    val merged = mergeSyncResult(before, run, now, manifest)
                    val saved = store.updateIfPresent(pluginId) {
                        if (!canAcceptSync(before, it)) null else merged.data.copy(
                            host = it.host, settings = it.settings, sessionRevision = it.sessionRevision,
                        )
                    } ?: return ExtensionSyncOutcome.Skipped(pluginId, "session_changed")
                    PluginLogger.info(
                        "extension.sync.finish",
                        mapOf("pluginId" to pluginId, "items" to saved.items.size, "new" to merged.newItems.size),
                    )
                    ExtensionSyncOutcome.Synced(pluginId, manifest, saved, merged.newItems)
                }
            }
        }
    }

    private suspend fun fail(pluginId: String, now: Long, message: String, before: ExtensionData? = null): ExtensionSyncOutcome.Failed {
        PluginLogger.warn("extension.sync.failure", mapOf("pluginId" to pluginId, "message" to message))
        store.updateIfPresent(pluginId) { if (before != null && !canAcceptSync(before, it)) null else it.copy(lastAttemptAt = now, lastError = message) }
        return ExtensionSyncOutcome.Failed(pluginId, message)
    }

    private fun lockOf(pluginId: String): Mutex = synchronized(locks) { locks.getOrPut(pluginId) { Mutex() } }

    private suspend fun isCurrent(record: InstalledPluginRecord): Boolean =
        pluginManager.getInstalledPlugins().any { it.packageRevision == record.packageRevision }
}

internal fun canAcceptSync(before: ExtensionData, current: ExtensionData): Boolean =
    before.sessionRevision == current.sessionRevision && current.loginState != ExtensionLoginState.LoggedOut && current.loginState != ExtensionLoginState.Never

internal data class MergedSync(val data: ExtensionData, val newItems: List<ExtensionFeedItem>)

/**
 * 把这次拿到的整份条目并进存档。条目是「快照」语义：这次没返回的就是没了（老师删了、学期过了）。
 *
 * 新内容 = 这次有、上次没有的 id。登录后第一次同步只建基线，不算新内容。
 */
internal fun mergeSyncResult(
    before: ExtensionData,
    run: ExtensionRunResult.Completed,
    now: Long,
    manifest: PluginManifest? = null,
): MergedSync {
    val previous = before.items.associateBy { it.id }
    val declared = manifest?.let { applyDeclaredKinds(run.items, it) } ?: run.items
    val items = declared.map { item ->
        item.copy(firstSeenAt = previous[item.id]?.firstSeenAt?.takeIf { it > 0 } ?: now)
    }
    val newItems = if (before.baselineReady) items.filter { it.id !in previous } else emptyList()
    val account = (run.result["account"] as? JsonObject)?.let { obj ->
        runCatching { extensionJson.decodeFromJsonElement(ExtensionAccount.serializer(), obj) }.getOrNull()
    }
    // 截止提醒的记录只留还在的条目，免得越攒越多
    val liveKeys = items.mapNotNull { item -> item.dueAt?.let { "${item.id}@$it" } }.toSet()
    return MergedSync(
        data = before.copy(
            loginState = ExtensionLoginState.LoggedIn,
            account = account ?: before.account,
            state = run.state,
            items = items,
            lastSyncAt = now,
            lastAttemptAt = now,
            lastError = null,
            lastMessage = run.result.string("message"),
            baselineReady = true,
            remindedKeys = before.remindedKeys intersect liveKeys,
            expiredNotified = false,
        ),
        newItems = newItems,
    )
}

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
