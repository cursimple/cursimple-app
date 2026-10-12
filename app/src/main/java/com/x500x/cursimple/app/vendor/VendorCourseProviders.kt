package com.x500x.cursimple.app.vendor

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

internal object VendorCallerTrust {
    val vivo = setOf("com.vivo.aiengine", "com.vivo.assistant")
    val oppo = setOf("com.oplus.metis", "com.oplus.pantanal.ums")
    val honor = setOf("com.hihonor.brain")
    fun requireCaller(context: Context, allowed: Set<String>) {
        val uid = Binder.getCallingUid()
        val valid = context.packageManager.getPackagesForUid(uid).orEmpty().any { name ->
            name in allowed && isSystemPackage(context, name)
        }
        if (!valid) throw SecurityException("Untrusted course-service caller")
    }
    fun isSystemPackage(context: Context, name: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(name, 0).flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)
    fun trustedAuthority(context: Context, authority: String, allowed: Set<String>): Boolean =
        context.packageManager.resolveContentProvider(authority, 0)?.packageName?.let { it in allowed && isSystemPackage(context, it) } == true
}

abstract class ReadOnlyCourseProvider : ContentProvider() {
    override fun onCreate() = true
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String = "application/json"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    protected fun freshSnapshot(app: Context): VendorCourseSnapshot {
        val snapshot = VendorCourseStore.snapshot(app)
        val age = System.currentTimeMillis() - snapshot.generatedAt
        if (age !in 0..(48*60*60*1000L)) { VendorCourseSync.enqueue(app); return VendorCourseSnapshot() }
        return snapshot
    }
}

class VivoCourseProvider : ReadOnlyCourseProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        VendorCallerTrust.requireCaller(app, VendorCallerTrust.vivo)
        val input = extras?.getString("intentParams").orEmpty()
        require(input.length <= 32_768)
        val request = runCatching { vendorJson.parseToJsonElement(input).jsonObject["requestId"]?.jsonPrimitive?.content }.getOrNull().orEmpty().take(256)
        val enabled = VendorCourseStore.config(app).enabled
        val supported = method == "syncQuerySuperClassData"
        val json = buildJsonObject {
            put("requestId", request)
            put("code", if (enabled && supported) 0 else 20303003)
            put("message", if (!supported) "Unsupported method" else if (!enabled) "Disabled" else "Success")
            if (enabled && supported) put("data", vivoCourseData(freshSnapshot(app), app.packageName))
        }
        if (enabled && supported) VendorCourseStore.status(app, "vivo", "queried")
        return Bundle().apply { putString("result", json.toString()) }
    }
}

class OppoCourseProvider : ReadOnlyCourseProvider() {
    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.${requireNotNull(context).packageName}.provider.oppodonateprovider.course"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val app = requireNotNull(context)
        VendorCallerTrust.requireCaller(app, VendorCallerTrust.oppo)
        val result = MatrixCursor(arrayOf("json"))
        val offset = when (uri.path) { "/course_list" -> 0L; "/next_course_list" -> 1L; "/refresh" -> return result; else -> throw IllegalArgumentException("Unknown course path") }
        val snapshot = freshSnapshot(app)
        val date = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of(snapshot.zone)).toLocalDate().plusDays(offset)
        val enabled = VendorCourseStore.config(app).enabled
        result.addRow(arrayOf(oppoCourseData(snapshot, date, app.packageName, enabled).toString()))
        if (enabled) VendorCourseStore.status(app, "oppo", "queried")
        return result
    }
}

class HonorCourseProvider : ReadOnlyCourseProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        VendorCallerTrust.requireCaller(app, VendorCallerTrust.honor)
        val accepted = when (method) {
            "feedback" -> HonorCourseTransport.feedback(extras?.getString("reqId"), extras?.getInt("resultCode", -1) ?: -1)
            "wakeup", "refresh" -> { VendorCourseSync.enqueue(app); true }
            else -> false
        }
        return Bundle().apply { putBoolean("result", accepted) }
    }
}
