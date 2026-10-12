package com.x500x.cursimple.app.vendor

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object HonorCourseTransport {
    private const val AUTHORITY = "com.hihonor.brain.kitservice.provider.KitProvider"
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Int>>()
    fun feedback(id: String?, code: Int): Boolean = id?.let { pending[it]?.complete(code) } ?: false

    private suspend fun step(context: Context, operation: String, data: JsonObject? = null): Boolean {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<Int>()
        pending[id] = result
        try {
            val response = BoundedVendorCall.call(context, AUTHORITY, VendorCallerTrust.honor, "feedback", operation, Bundle().apply {
                putString("reqId", id); putString("packageName", context.packageName)
                putString("uri", "content://${context.packageName}.provider.honordonateprovider")
                data?.let { putString("data", it.toString()) }
            })
            if (response?.getBoolean("result", false) != true) { VendorCourseStore.status(context, "honor", "rejected"); return false }
            VendorCourseStore.status(context, "honor", "accepted")
            val confirmed = withTimeoutOrNull(8_000) { result.await() }
            VendorCourseStore.status(context, "honor", when (confirmed) { 0 -> "confirmed"; null -> "unconfirmed"; else -> "rejected" }, "$operation resultCode=${confirmed ?: "timeout"}")
            return confirmed == 0
        } finally { pending.remove(id); result.cancel() }
    }

    suspend fun clearIfNeeded(context: Context): Boolean {
        if (!VendorCourseStore.hasSubmitted(context)) { VendorCourseStore.status(context, "honor", "disabled"); return true }
        if (!VendorCallerTrust.trustedAuthority(context, AUTHORITY, VendorCallerTrust.honor)) {
            VendorCourseStore.status(context, "honor", "cleanup_pending"); return false
        }
        val cleared = step(context, "deleteFeedbackAll")
        if (cleared) { VendorCourseStore.markSubmitted(context, false); VendorCourseStore.confirmed(context, null); VendorCourseStore.status(context, "honor", "disabled") }
        return cleared
    }

    suspend fun synchronize(context: Context, config: VendorCourseConfig, snapshot: VendorCourseSnapshot): Boolean {
        if (!VendorCallerTrust.trustedAuthority(context, AUTHORITY, VendorCallerTrust.honor)) {
            VendorCourseStore.status(context, "honor", "unavailable"); return true
        }
        val supported = runCatching { context.packageManager.getApplicationInfo("com.hihonor.brain", android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getBoolean("com.hihonor.brain.supportFeedbackPlan.provider", false) == true }.getOrDefault(false)
        if (!supported) { VendorCourseStore.status(context, "honor", "unavailable"); return true }
        if (config.honorCapsuleType.isBlank() || config.honorCardType.isBlank() || (config.aodEnabled && config.honorAodIconUrl.isBlank())) {
            VendorCourseStore.status(context, "honor", "configuration"); return true
        }
        val fingerprint = courseFingerprint(snapshot, config)
        if (VendorCourseStore.lastFingerprint(context) == fingerprint) return true
        if (!step(context, "deleteFeedbackAll")) return false
        VendorCourseStore.confirmed(context, null); VendorCourseStore.markSubmitted(context, false)
        if (!VendorCourseStore.config(context).enabled) return true
        val now = System.currentTimeMillis()
        for (type in listOf(config.honorCapsuleType, config.honorCardType)) {
            if (VendorCourseStore.config(context) != config) return false
            VendorCourseStore.markSubmitted(context, true)
            if (!step(context, "feedbackPlan", honorCoursePlans(snapshot, context.packageName, config, type, now, context.getString(com.x500x.cursimple.R.string.app_name), context.getString(com.x500x.cursimple.R.string.class_notice_action_open)))) return false
        }
        VendorCourseStore.confirmed(context, fingerprint)
        return true
    }
}

internal fun honorCoursePlans(snapshot: VendorCourseSnapshot, packageName: String, config: VendorCourseConfig, intentType: String, now: Long, appName: String = "CurSimple", openLabel: String = "Open course"): JsonObject = buildJsonObject {
    put("intentType", intentType); put("confidence", 100); put("packageName", packageName); put("createTime", now)
    val zone = ZoneId.of(snapshot.zone)
    put("planFeedbackDatas", buildJsonArray { snapshot.courses.filter { it.endAt > now }.forEach { c -> add(buildJsonObject {
        val beginAt = c.startAt - snapshot.advanceMinutes * 60_000L
        val begin = Instant.ofEpochMilli(beginAt).atZone(zone)
        val end = Instant.ofEpochMilli(c.endAt).atZone(zone)
        val startClock = Instant.ofEpochMilli(c.startAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        val time = "$startClock–${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        put("planName", "${packageName}:${c.id}:${c.startAt}")
        put("recurrentFrequency", 2)
        // Bound each occurrence to its real interval, including holidays and moved classes.
        put("recurrentBegin", beginAt); put("recurrentEnd", c.endAt)
        put("daysOfTheWeek", JsonArray((1..7).map(::JsonPrimitive)))
        listOf("daysOfTheMonth", "daysOfTheYear", "specifiedDays").forEach { put(it, JsonArray(emptyList())) }
        put("beginTime", buildJsonObject { put("hour", begin.hour); put("minute", begin.minute) })
        put("endTime", buildJsonObject { put("hour", end.hour); put("minute", end.minute) })
        put("feedbackExtra1", buildJsonObject {
            put("version", "1.0"); put("businessType", "lesson_remind"); put("businessStatus", "ABOUT_TO_START")
            put("layout", "plan_normal"); put("orderNo", "${c.id}_${c.startAt}"); put("startTime", c.startAt)
            put("notificationText", buildJsonObject {
                put("title", c.title); put("content", "$time ${c.location}".trim()); put("subContent", appName)
                put("buttonText", openLabel); put("iconUrl", ""); put("illustrationUrl", "")
            }.toString())
            put("capsuleText", buildJsonObject { put("content", "$startClock ${c.title}"); put("businessName", appName) }.toString())
            put("aodText", buildJsonObject {
                put("aodText", if (config.aodEnabled) "$startClock ${c.title} ${c.location}".trim().take(60) else "")
                put("aodIconUrl", if (config.aodEnabled) config.honorAodIconUrl else "")
            }.toString())
            put("color", buildJsonObject { put("templateColor", "1") }.toString())
            put("homePage", buildJsonObject { put("packageName", packageName); put("url", vendorCourseLink(packageName, c)) })
        }.toString())
        val details = buildJsonObject { put("teacher", c.teacher); put("room", c.location); put("week", c.week.toString())
            put("session", "${c.startSection}-${c.endSection}"); put("dp", vendorCourseLink(packageName, c)) }.toString()
        put("feedbackExtra2", details); put("feedbackExtra3", details)
    }) } })
}
