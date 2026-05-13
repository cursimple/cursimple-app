package com.x500x.cursimple.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

data class ScheduleMetadataExportSnapshot(
    val schedule: TermSchedule?,
    val manualCourses: List<CourseItem>,
    val timingProfile: TermTimingProfile?,
    val installedPlugins: List<InstalledPluginRecord>,
    val enabledPluginIds: Set<String>,
    val selectedPluginId: String,
    val termStartDate: LocalDate?,
    val currentWeekIndex: Int,
    val displayedWeekIndex: Int,
    val isSyncing: Boolean,
    val statusMessage: String?,
    val messages: List<String>,
)

object ScheduleMetadataExporter {
    suspend fun export(context: Context, snapshot: ScheduleMetadataExportSnapshot): Intent? = withContext(Dispatchers.IO) {
        val file = writeMetadataFile(context, snapshot) ?: return@withContext null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CurSimple 课表元数据 ${file.name}")
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun writeMetadataFile(context: Context, snapshot: ScheduleMetadataExportSnapshot): File? = runCatching {
        val dir = File(context.cacheDir, "metadata").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(dir, "schedule-metadata-$timestamp.json")
        target.writeText(buildMetadataJson(snapshot, timestamp).toString(2))
        target
    }.getOrNull()

    internal fun buildMetadataJson(snapshot: ScheduleMetadataExportSnapshot, timestamp: String): JSONObject {
        return JSONObject()
            .put("exportedAt", timestamp)
            .putNullable("termStartDate", snapshot.termStartDate?.toString())
            .put("timeZone", "Asia/Shanghai")
            .put("currentWeekIndex", snapshot.currentWeekIndex)
            .put("displayedWeekIndex", snapshot.displayedWeekIndex)
            .put("isSyncing", snapshot.isSyncing)
            .putNullable("statusMessage", snapshot.statusMessage)
            .put("messages", snapshot.messages.toJsonArray())
            .put("selectedPluginId", snapshot.selectedPluginId)
            .put("installedPlugins", snapshot.installedPlugins.toPluginJsonArray(snapshot.enabledPluginIds))
            .put("timingProfile", snapshot.timingProfile?.toJson() ?: JSONObject.NULL)
            .put("schedule", snapshot.schedule?.toJson() ?: JSONObject.NULL)
            .put("manualCourses", snapshot.manualCourses.toCourseJsonArray())
    }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun TermSchedule.toJson(): JSONObject {
    return JSONObject()
        .put("termId", termId)
        .put("updatedAt", updatedAt)
        .put("dailyScheduleCount", dailySchedules.size)
        .put("courseCount", dailySchedules.sumOf { it.courses.size })
        .put("dailySchedules", dailySchedules.toDailyScheduleJsonArray())
}

private fun List<DailySchedule>.toDailyScheduleJsonArray(): JSONArray {
    return JSONArray().also { array ->
        forEach { day ->
            array.put(
                JSONObject()
                    .put("dayOfWeek", day.dayOfWeek)
                    .put("courseCount", day.courses.size)
                    .put("courses", day.courses.toCourseJsonArray()),
            )
        }
    }
}

private fun List<CourseItem>.toCourseJsonArray(): JSONArray {
    return JSONArray().also { array ->
        forEach { course -> array.put(course.toJson()) }
    }
}

private fun CourseItem.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("title", title)
        .put("teacher", teacher)
        .put("location", location)
        .put("weeks", weeks.toJsonArray())
        .put("time", time.toJson())
}

private fun CourseTimeSlot.toJson(): JSONObject {
    return JSONObject()
        .put("dayOfWeek", dayOfWeek)
        .put("startNode", startNode)
        .put("endNode", endNode)
}

private fun TermTimingProfile.toJson(): JSONObject {
    return JSONObject()
        .put("timezone", timezone)
        .put("slotTimes", JSONArray().also { array ->
            slotTimes.forEach { slot ->
                array.put(
                    JSONObject()
                        .put("startNode", slot.startNode)
                        .put("endNode", slot.endNode)
                        .put("label", slot.label)
                        .put("startTime", slot.startTime)
                        .put("endTime", slot.endTime),
                )
            }
        })
}

private fun List<InstalledPluginRecord>.toPluginJsonArray(enabledPluginIds: Set<String>): JSONArray {
    return JSONArray().also { array ->
        forEach { plugin ->
            array.put(
                JSONObject()
                    .put("pluginId", plugin.pluginId)
                    .put("name", plugin.name)
                    .put("publisher", plugin.publisher)
                    .put("version", plugin.version)
                    .put("versionCode", plugin.versionCode)
                    .put("source", plugin.source.name)
                    .put("isBundled", plugin.isBundled)
                    .put("isEnabled", plugin.pluginId in enabledPluginIds)
                    .put("declaredPermissions", plugin.declaredPermissions.map { it.name }.toJsonArray())
                    .put("allowedHosts", plugin.allowedHosts.toJsonArray()),
            )
        }
    }
}

private fun Iterable<*>.toJsonArray(): JSONArray {
    return JSONArray().also { array ->
        forEach { value -> array.put(value ?: JSONObject.NULL) }
    }
}
