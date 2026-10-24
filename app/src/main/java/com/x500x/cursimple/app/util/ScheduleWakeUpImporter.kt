package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Parses WakeUp five-line exports, JSON exports and zip packages into [ScheduleSharePayload].
 */
object ScheduleWakeUpImporter {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class WakeUpPackage(
        val courses: JsonArray,
        val setting: JsonObject?,
        val times: Map<Int, Pair<LocalTime, LocalTime>> = emptyMap(),
    )

    fun parse(stream: InputStream): ScheduleSharePayload {
        val bytes = ScheduleImportLimits.read(stream)
        val jsonText = tryExtractZipContent(bytes) ?: String(bytes, Charsets.UTF_8)
        return parseJson(jsonText)
    }

    private fun tryExtractZipContent(bytes: ByteArray): String? {
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) return null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var total = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 128) { "课程包条目过多" }
                val content = ScheduleImportLimits.read(zip, ScheduleImportLimits.MAX_BYTES - total)
                total += content.size
                if (!entry.isDirectory && (entry.name.endsWith(".json", ignoreCase = true) ||
                        entry.name.endsWith(".wakeup_schedule", ignoreCase = true) || entry.name == "schedule")) {
                    return String(content, Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        throw IllegalArgumentException("课程包缺少 JSON 数据")
    }

    fun parseJson(jsonText: String): ScheduleSharePayload {
        ScheduleImportLimits.validateText(jsonText, json = true)
        val data = parsePackage(jsonText.trim().removePrefix("\uFEFF").trim())
        val coursesArray = data.courses
        require(coursesArray.size <= ScheduleImportLimits.MAX_COURSES) { "课程数量过多" }
        val settingObj = data.setting
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
            val startNode = optInt("startNode", optInt("startSection", 1)).coerceIn(1, 48)
            val step = optInt("step", optInt("nodeCount", 1)).coerceIn(1, 48)
            val endNode = if (item.containsKey("endNode")) {
                optInt("endNode", startNode).coerceIn(startNode, 48)
            } else {
                (startNode + step - 1).coerceAtMost(48)
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
            val startTime = data.times[startNode]?.first
            val endTime = data.times[endNode]?.second
            val hasClock = startTime != null && endTime != null && startTime.isBefore(endTime)

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
                    reminderStartTime = startTime?.takeIf { hasClock }?.toString(),
                    reminderEndTime = endTime?.takeIf { hasClock }?.toString(),
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

    private fun parsePackage(text: String): WakeUpPackage {
        val element = runCatching { jsonParser.parseToJsonElement(text) }.getOrNull()
            ?: return parseOfficialLines(text)
        val root = element as? JsonObject
        val courses = (element as? JsonArray)
            ?: (root?.get("courses") as? JsonArray)
            ?: (root?.get("courseList") as? JsonArray)
            ?: (root?.get("data") as? JsonArray)
            ?: (root?.get("schedule") as? JsonArray)
            ?: throw IllegalArgumentException("课程包缺少课程列表")
        return WakeUpPackage(courses, (root?.get("setting") as? JsonObject) ?: (root?.get("table") as? JsonObject))
    }

    private fun parseOfficialLines(text: String): WakeUpPackage {
        // WakeUp 3.612 ScheduleViewModel.exportData / ImportViewModel.importFromFile:
        // timetable, time details, table, course bases, course details (one JSON value per line).
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(6).toList()
        require(lines.size == 5) { "无效的 WakeUp 多行课程包" }
        val values = lines.map { jsonParser.parseToJsonElement(it) }
        require(values[0] is JsonObject && values[1] is JsonArray && values[2] is JsonObject &&
            values[3] is JsonArray && values[4] is JsonArray) { "无效的 WakeUp 多行课程包" }
        val setting = values[2] as JsonObject
        val bases = values[3] as JsonArray
        val details = values[4] as JsonArray
        val timeDetails = values[1] as JsonArray
        require(bases.size <= ScheduleImportLimits.MAX_COURSES &&
            details.size <= ScheduleImportLimits.MAX_COURSES &&
            timeDetails.size <= ScheduleImportLimits.MAX_COURSES) { "课程数量过多" }

        fun JsonObject.courseKey(): Pair<Int, Int> {
            val id = (get("id") as? JsonPrimitive)?.intOrNull
            val tableId = (get("tableId") as? JsonPrimitive)?.intOrNull
            require(id != null && tableId != null) { "课程包缺少课程标识" }
            return id to tableId
        }
        val names = bases.associate { value ->
            val base = value as? JsonObject ?: throw IllegalArgumentException("无效的课程基础信息")
            val name = base["courseName"] as? JsonPrimitive
            require(name != null && name.isString && name.content.isNotBlank()) { "课程包缺少课程名称" }
            base.courseKey() to name
        }
        var expandedNameBytes = 0L
        val courses = details.map { value ->
            val detail = value as? JsonObject ?: throw IllegalArgumentException("无效的课程时间信息")
            val name = names[detail.courseKey()] ?: throw IllegalArgumentException("课程时间缺少对应课程")
            expandedNameBytes += name.content.toByteArray(Charsets.UTF_8).size
            require(expandedNameBytes <= ScheduleImportLimits.MAX_BYTES) { "导入文件过大" }
            JsonObject(detail + ("name" to name))
        }
        val times = timeDetails.map { value ->
            val time = value as? JsonObject ?: throw IllegalArgumentException("无效的节次时间")
            val node = (time["node"] as? JsonPrimitive)?.intOrNull
            val start = (time["startTime"] as? JsonPrimitive)?.content?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val end = (time["endTime"] as? JsonPrimitive)?.content?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            require(node != null && start != null && end != null && start.isBefore(end)) { "无效的节次时间" }
            node to (start to end)
        }.toMap()
        return WakeUpPackage(JsonArray(courses), setting, times)
    }

}
