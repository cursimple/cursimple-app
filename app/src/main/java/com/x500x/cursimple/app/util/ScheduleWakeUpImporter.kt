package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Parses WakeUp (.wakeup_schedule zip packages or WakeUp JSON exports) into [ScheduleSharePayload].
 */
object ScheduleWakeUpImporter {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(stream: InputStream): ScheduleSharePayload {
        val bytes = stream.readBytes()
        val jsonText = tryExtractZipJson(bytes) ?: String(bytes, Charsets.UTF_8)
        return parseJson(jsonText)
    }

    private fun tryExtractZipJson(bytes: ByteArray): String? {
        return runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name.endsWith(".json", ignoreCase = true) || entry.name == "schedule")) {
                        return@use zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    entry = zis.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    fun parseJson(jsonText: String): ScheduleSharePayload {
        val element = runCatching { jsonParser.parseToJsonElement(jsonText.trim()) }.getOrNull()
            ?: return ScheduleSharePayload(schedule = emptySchedule("WakeUp 导入失败"))

        val rootObj = element as? JsonObject
        val rootArray = element as? JsonArray

        val coursesArray: JsonArray = when {
            rootArray != null -> rootArray
            rootObj != null -> {
                (rootObj["courses"] as? JsonArray)
                    ?: (rootObj["courseList"] as? JsonArray)
                    ?: (rootObj["data"] as? JsonArray)
                    ?: (rootObj["schedule"] as? JsonArray)
                    ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }

        val settingObj = (rootObj?.get("setting") as? JsonObject) ?: (rootObj?.get("table") as? JsonObject)
        val termName = settingObj?.get("tableName")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "WakeUp 导入学期"
        val termStartDate = settingObj?.get("startDate")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val courses = mutableListOf<CourseItem>()

        for (itemElem in coursesArray) {
            val item = itemElem as? JsonObject ?: continue

            fun optStr(vararg keys: String): String {
                for (k in keys) {
                    val v = item[k]?.jsonPrimitive?.content
                    if (!v.isNullOrBlank()) return v
                }
                return ""
            }

            fun optInt(key: String, fallback: Int): Int {
                return item[key]?.jsonPrimitive?.intOrNull ?: fallback
            }

            val title = optStr("name", "courseName", "title").ifBlank { "未命名课程" }
            val location = optStr("room", "position", "location", "classroom")
            val teacher = optStr("teacher", "teacherName")

            val day = optInt("day", optInt("dayOfWeek", 1)).coerceIn(1, 7)
            val startNode = optInt("startNode", optInt("startSection", 1)).coerceAtLeast(1)
            val step = optInt("step", optInt("nodeCount", 1)).coerceAtLeast(1)
            val endNode = if (item.containsKey("endNode")) {
                optInt("endNode", startNode).coerceAtLeast(startNode)
            } else {
                startNode + step - 1
            }

            // Weeks calculation
            val weeks = mutableListOf<Int>()
            val weeksArr = item["weeks"] as? JsonArray
            if (weeksArr != null && weeksArr.isNotEmpty()) {
                for (w in weeksArr) {
                    val num = w.jsonPrimitive.intOrNull
                    if (num != null && num in 1..40) weeks.add(num)
                }
            } else {
                val startWeek = optInt("startWeek", 1).coerceIn(1, 40)
                val endWeek = optInt("endWeek", startWeek).coerceIn(startWeek, 40)
                val type = optInt("type", 0) // 0: all, 1: odd, 2: even

                for (w in startWeek..endWeek) {
                    when (type) {
                        1 -> if (w % 2 == 1) weeks.add(w)
                        2 -> if (w % 2 == 0) weeks.add(w)
                        else -> weeks.add(w)
                    }
                }
            }

            val isExam = title.contains("考试") || title.contains("期末")

            courses.add(
                CourseItem(
                    id = "wakeup-${UUID.randomUUID()}",
                    title = title,
                    teacher = teacher,
                    location = location,
                    weeks = weeks.distinct().sorted(),
                    category = if (isExam) CourseCategory.Exam else CourseCategory.Course,
                    time = CourseTimeSlot(
                        dayOfWeek = day,
                        startNode = startNode,
                        endNode = endNode,
                    ),
                ),
            )
        }

        val dailySchedules = (1..7).map { day ->
            DailySchedule(
                dayOfWeek = day,
                courses = courses.filter { it.time.dayOfWeek == day },
            )
        }

        val schedule = TermSchedule(
            termId = "term-wakeup-${System.currentTimeMillis()}",
            updatedAt = LocalDate.now().toString(),
            dailySchedules = dailySchedules,
            externalImport = true,
        )

        return ScheduleSharePayload(
            termName = termName,
            termStartDate = termStartDate,
            schedule = schedule,
            manualCourses = emptyList(),
        )
    }

    private fun emptySchedule(name: String): TermSchedule {
        return TermSchedule(
            termId = "term-wakeup-${System.currentTimeMillis()}",
            updatedAt = LocalDate.now().toString(),
            dailySchedules = (1..7).map { DailySchedule(it, emptyList()) },
            externalImport = true,
        )
    }
}
