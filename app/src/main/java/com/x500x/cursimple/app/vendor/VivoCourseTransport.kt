package com.x500x.cursimple.app.vendor

import android.content.Context
import com.vivo.intentionkit.action.donate.DonateClient
import com.vivo.intentionkit.action.donate.DonateStatus
import com.vivo.intentionkit.action.donate.IntentEntity
import com.vivo.intentionkit.action.task.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object VivoCourseTransport {
    private const val TYPE = DonateClient.INTENT_TYPE_ACTION
    private val inline = Executor { it.run() }
    private fun available(context: Context): Boolean = listOf("com.vivo.awarecontext.awareeventprovider", "com.vivo.assistant.donate.donateDataProvider")
        .any { VendorCallerTrust.trustedAuthority(context, it, VendorCallerTrust.vivo) }
    private suspend fun Task<DonateStatus>.awaitStatus(): Int = withTimeoutOrNull(8_000) {
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener(inline) { task ->
                if (continuation.isActive) {
                    if (task.isSuccessful) continuation.resume(task.result.status)
                    else continuation.resumeWithException(task.exception ?: IllegalStateException("Vivo task cancelled"))
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    } ?: -2
    suspend fun clearIfNeeded(context: Context): Boolean {
        val id = VendorCourseStore.vivoSubmitted(context)
        if (id.isEmpty()) { VendorCourseStore.status(context, "vivo", "disabled"); return true }
        if (!available(context)) { VendorCourseStore.status(context, "vivo", "cleanup_pending"); return false }
        val cleared = DonateClient.create(context).deleteIntent(TYPE, id).awaitStatus() == DonateStatus.SUCCESS
        if (cleared) {
            VendorCourseStore.markVivoSubmitted(context, ""); VendorCourseStore.confirmVivo(context, null)
            VendorCourseStore.status(context, "vivo", "disabled")
        } else VendorCourseStore.status(context, "vivo", "cleanup_pending")
        return cleared
    }
    suspend fun synchronize(context: Context, config: VendorCourseConfig, snapshot: VendorCourseSnapshot): Boolean {
        if (!available(context)) { VendorCourseStore.status(context, "vivo", "unavailable"); return true }
        val client = DonateClient.create(context)
        val availability = client.checkAvailability(TYPE).awaitStatus()
        if (availability != DonateStatus.SUCCESS) {
            VendorCourseStore.status(context, "vivo", if (availability == DonateStatus.SERVICE_NOT_SUPPORT) "unavailable" else "rejected", "checkAvailability=$availability")
            return true
        }
        if (config.vivoIntentId.isBlank()) { VendorCourseStore.status(context, "vivo", "configuration"); return true }
        val oldId = VendorCourseStore.vivoSubmitted(context)
        if (oldId.isNotBlank() && oldId != config.vivoIntentId && !clearIfNeeded(context)) return false
        val fingerprint = courseFingerprint(snapshot, config)
        if (VendorCourseStore.vivoFingerprint(context) == fingerprint) return true
        val now = System.currentTimeMillis()
        val remaining = VendorCourseStore.vivoLastAt(context) + 300_000L - now
        if (remaining in 1..300_000L) {
            VendorCourseStore.status(context, "vivo", "throttled"); VendorCourseSync.defer(context, remaining); return true
        }
        if (VendorCourseStore.config(context) != config) return false
        val entity = IntentEntity(config.vivoIntentId).apply {
            name = config.vivoIntentName; version = 1; type = TYPE
            source = JSONObject().put("deviceType", "0301")
            this.entity = JSONObject(buildJsonObject {
                put("id", "${context.packageName}:courses")
                put("name", config.vivoEntityName); put("appType", 1)
                put("content", vivoCourseData(snapshot, context.packageName))
            }.toString())
        }
        VendorCourseStore.markVivoSubmitted(context, config.vivoIntentId)
        val status = client.donateIntent(entity, null).awaitStatus()
        VendorCourseStore.status(context, "vivo", when (status) { DonateStatus.SUCCESS -> "confirmed"; -2 -> "unconfirmed"; else -> "rejected" }, "donateIntent=$status")
        if (status == DonateStatus.SUCCESS) VendorCourseStore.confirmVivo(context, fingerprint)
        return status == DonateStatus.SUCCESS
    }
}
