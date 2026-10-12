package com.x500x.cursimple.app.vendor

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

@Serializable
data class VendorCourseConfig(
    val enabled: Boolean = false,
    val aodEnabled: Boolean = false,
    val vivoIntentId: String = "",
    val vivoIntentName: String = "查看课程表",
    val vivoEntityName: String = "cursimple_course_entity",
    val honorCapsuleType: String = "",
    val honorCardType: String = "",
    val honorAodIconUrl: String = "",
)

object VendorCourseStore {
    val changes = MutableStateFlow(0L)
    private fun prefs(context: Context) = context.getSharedPreferences("vendor_course_services", Context.MODE_PRIVATE)
    fun config(context: Context): VendorCourseConfig = runCatching {
        vendorJson.decodeFromString(VendorCourseConfig.serializer(), prefs(context).getString("config", "{}")!!)
    }.getOrDefault(VendorCourseConfig())
    fun save(context: Context, config: VendorCourseConfig) {
        require(config.vivoIntentId.isEmpty() || config.vivoIntentId.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
        require(config.vivoIntentName.length in 1..80 && config.vivoEntityName.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
        require(listOf(config.honorCapsuleType, config.honorCardType).all { it.isEmpty() || it.matches(Regex("[0-9]{1,32}")) })
        require(config.honorAodIconUrl.isEmpty() || android.net.Uri.parse(config.honorAodIconUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null })
        prefs(context).edit().putString("config", vendorJson.encodeToString(VendorCourseConfig.serializer(), config)).apply()
        if (!config.enabled) writeSnapshot(context, VendorCourseSnapshot())
        changes.value++
        VendorCourseSync.enqueue(context, force = true)
    }
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "vendor-courses/snapshot.json").also { it.parentFile?.mkdirs() })
    @Synchronized fun snapshot(context: Context): VendorCourseSnapshot = runCatching {
        file(context).openRead().use { vendorJson.decodeFromString(VendorCourseSnapshot.serializer(), it.reader().readText()) }
    }.getOrDefault(VendorCourseSnapshot())
    @Synchronized fun writeSnapshot(context: Context, value: VendorCourseSnapshot) {
        val file = file(context)
        val stream = file.startWrite()
        try { stream.write(vendorJson.encodeToString(VendorCourseSnapshot.serializer(), value).toByteArray()); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    fun status(context: Context, vendor: String, value: String, detail: String = "") {
        prefs(context).edit().putString("status.$vendor", value).putString("detail.$vendor", detail.take(256)).putLong("time.$vendor", System.currentTimeMillis()).apply()
        changes.value++
    }
    fun statuses(context: Context): List<Pair<String,String>> = listOf("android", "vivo", "oppo", "honor").map {
        it to prefs(context).getString("status.$it", "unchecked").orEmpty()
    }
    fun diagnosticDetails(context: Context): List<Pair<String, String>> = listOf("vivo", "oppo", "honor").flatMap { vendor ->
        listOf("$vendor.last_result" to prefs(context).getString("detail.$vendor", "").orEmpty(), "$vendor.last_event_at" to prefs(context).getLong("time.$vendor", 0).toString())
    }
    internal fun vivoSubmitted(context: Context) = prefs(context).getString("vivo.submitted_id", "").orEmpty()
    internal fun markVivoSubmitted(context: Context, id: String) { prefs(context).edit().putString("vivo.submitted_id", id).apply() }
    internal fun vivoFingerprint(context: Context) = prefs(context).getString("vivo.fingerprint", null)
    internal fun vivoLastAt(context: Context) = prefs(context).getLong("vivo.last_at", 0)
    internal fun confirmVivo(context: Context, fingerprint: String?) {
        prefs(context).edit().putString("vivo.fingerprint", fingerprint).putLong("vivo.last_at", if (fingerprint == null) 0 else System.currentTimeMillis()).apply()
    }
    internal fun markSubmitted(context: Context, value: Boolean) { prefs(context).edit().putBoolean("honor.submitted", value).apply() }
    internal fun hasSubmitted(context: Context) = prefs(context).getBoolean("honor.submitted", false)
    internal fun lastFingerprint(context: Context) = prefs(context).getString("honor.fingerprint", null)
    internal fun confirmed(context: Context, fingerprint: String?) {
        prefs(context).edit().putString("honor.fingerprint", fingerprint).apply()
    }
}
