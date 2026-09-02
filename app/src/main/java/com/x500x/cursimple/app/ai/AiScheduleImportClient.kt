package com.x500x.cursimple.app.ai

import android.content.Context
import com.x500x.cursimple.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.x500x.cursimple.core.data.coerceAiImportTimeoutSeconds
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Duration

class AiScheduleImportClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = AiImportJson,
) {
    fun importFromImage(context: Context, imageUri: Uri, config: AiImportConfig): AiScheduleImportPayload {
        aiImportRequire(config.isComplete, R.string.ai_error_config_incomplete)
        val dataUrl = context.imageDataUrl(imageUri)
        val endpoint = normalizeAiEndpoint(config.apiUrl)
        val requestJson = buildRequestJson(endpoint, config, dataUrl).toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val requestClient = client.withAiImportTimeout(config.timeoutSeconds)
        val bodyText = requestClient.newCall(request).execute().use { response ->
            aiImportRequire(response.isSuccessful, R.string.ai_error_request_failed, response.code)
            response.body.string()
        }
        return parseAiScheduleImportContent(extractAiTextContent(bodyText, json), json)
    }

    private fun buildRequestJson(endpoint: String, config: AiImportConfig, dataUrl: String): JsonObject {
        val model = config.model.trim().ifBlank { "gpt-4o-mini" }
        return if (endpoint.contains("/responses", ignoreCase = true)) {
            buildJsonObject {
                put("model", model)
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(buildJsonObject {
                                            put("type", "input_text")
                                            put("text", PROMPT)
                                        })
                                        add(buildJsonObject {
                                            put("type", "input_image")
                                            put("image_url", dataUrl)
                                        })
                                    },
                                )
                            },
                        )
                    },
                )
            }
        } else {
            buildJsonObject {
                put("model", model)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", PROMPT)
                                        })
                                        add(buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject { put("url", dataUrl) },
                                            )
                                        })
                                    },
                                )
                            },
                        )
                    },
                )
                put("temperature", 0)
            }
        }
    }

    private fun Context.imageDataUrl(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) aiImportError(R.string.ai_error_open_image)
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = aiImageSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE)
        }
        val bitmap = contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) aiImportError(R.string.ai_error_open_image)
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: aiImportError(R.string.ai_error_image_format)
        val scaled = bitmap.scaleDown(MAX_IMAGE_SIDE)
        val bytes = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun Bitmap.scaleDown(maxSide: Int): Bitmap {
        val side = width.coerceAtLeast(height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 1800
        const val JPEG_QUALITY = 90
        const val PROMPT = """
            请从这张课程表图片中识别课表，严格只返回 JSON，不要 Markdown。
            JSON 格式：
            {
              "schedule": {
                "termId": "ai-image-import",
                "updatedAt": "当前 ISO 字符串或空字符串",
                "dailySchedules": [
                  {
                    "dayOfWeek": 1,
                    "courses": [
                      {
                        "id": "稳定唯一 ID",
                        "title": "课程名",
                        "teacher": "教师，没有则空字符串",
                        "location": "地点，没有则空字符串",
                        "weeks": [1,2,3],
                        "category": "course",
                        "time": {"dayOfWeek": 1, "startNode": 1, "endNode": 2}
                      }
                    ]
                  }
                ]
              },
              "manualCourses": []
            }
            dayOfWeek 使用 1-7 表示周一到周日。第几节用 startNode/endNode 表示。
            每条上课记录的 id 必须唯一；同一课程出现在不同星期或节次时，也要用不同 id。
            周次如 1-16 转成完整整数数组；单周/双周只列实际周次。
            不确定教师或地点时填空字符串，不要编造。
        """
    }
}

private val AiImportJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun OkHttpClient.withAiImportTimeout(timeoutSeconds: Int): OkHttpClient {
    val timeout = Duration.ofSeconds(coerceAiImportTimeoutSeconds(timeoutSeconds).toLong())
    return newBuilder()
        .callTimeout(timeout)
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .writeTimeout(timeout)
        .build()
}

internal fun extractAiTextContent(
    bodyText: String,
    json: Json = AiImportJson,
): String {
    val root = json.parseToJsonElement(bodyText).asObjectOrNull()
        ?: aiImportError(R.string.ai_error_not_json_object)
    root["output_text"]?.asTextOrNull()?.takeIf(String::isNotBlank)?.let { return it }
    root["choices"]?.asArrayOrNull()
        ?.firstOrNull()
        ?.asObjectOrNull()
        ?.let { choice ->
            choice["text"]?.asTextOrNull()?.takeIf(String::isNotBlank)?.let { return it }
            choice["message"]?.asObjectOrNull()
                ?.get("content")
                ?.asTextOrNull()
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
        }
    root["output"]?.asArrayOrNull()?.forEach { item ->
        item.asObjectOrNull()?.get("content")?.asArrayOrNull()?.forEach { content ->
            content.asTextOrNull()?.takeIf(String::isNotBlank)?.let { return it }
        }
    }
    root["content"]?.asTextOrNull()?.takeIf(String::isNotBlank)?.let { return it }
    aiImportError(R.string.ai_error_no_text)
}

internal fun parseAiScheduleImportContent(
    text: String,
    json: Json = AiImportJson,
): AiScheduleImportPayload {
    val rootElement = json.parseToJsonElement(extractAiJsonPayload(text))
    val root = when (rootElement) {
        is JsonObject -> rootElement
        is JsonArray -> buildJsonObject { put("courses", rootElement) }
        else -> aiImportError(R.string.ai_error_not_schedule)
    }
    val schedule = parseSchedule(root)
    val manualCourses = parseCourseArray(
        root.getAny("manualCourses", "manual_courses", "manual", "手动课程")?.asArrayOrNull(),
        dailyDayOfWeek = null,
        sourceName = AiImportTextArg(R.string.ai_source_manual_courses),
    )
    aiImportRequire(schedule != null || manualCourses.isNotEmpty(), R.string.ai_error_no_courses)
    schedule?.let(::validateImportedSchedule)
    return AiScheduleImportPayload(schedule = schedule, manualCourses = manualCourses)
}

private fun extractAiJsonPayload(text: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
    val candidate = fenced ?: text
    val objectStart = candidate.indexOf('{')
    val objectEnd = candidate.lastIndexOf('}')
    if (objectStart >= 0 && objectEnd > objectStart) {
        return candidate.substring(objectStart, objectEnd + 1)
    }
    val arrayStart = candidate.indexOf('[')
    val arrayEnd = candidate.lastIndexOf(']')
    aiImportRequire(arrayStart >= 0 && arrayEnd > arrayStart, R.string.ai_error_no_json)
    return candidate.substring(arrayStart, arrayEnd + 1)
}

private fun parseSchedule(root: JsonObject): TermSchedule? {
    val scheduleElement = root.getAny("schedule", "termSchedule", "课表")
    val source = when (scheduleElement) {
        is JsonObject -> scheduleElement
        is JsonArray -> buildJsonObject { put("courses", scheduleElement) }
        else -> root
    }
    val dailySchedules = parseDailySchedules(source).ifEmpty {
        groupCourses(parseFlatCourses(source))
    }
    if (dailySchedules.isEmpty()) return null
    val uniqueDailySchedules = ensureUniqueScheduleCourseIds(dailySchedules)
    return TermSchedule(
        termId = source.getAny("termId", "term", "学期")?.asTextOrNull()?.takeIf(String::isNotBlank)
            ?: "ai-image-import",
        updatedAt = source.getAny("updatedAt", "更新时间")?.asTextOrNull().orEmpty(),
        dailySchedules = uniqueDailySchedules,
    )
}

private fun parseDailySchedules(source: JsonObject): List<DailySchedule> {
    val dailyArray = source.getAny("dailySchedules", "days", "daySchedules", "weeklySchedule", "日程", "每日课程")
        ?.asArrayOrNull()
    if (dailyArray != null) {
        return dailyArray
            .flatMap { daily ->
                val dailyObject = daily.asObjectOrNull() ?: return@flatMap emptyList()
                val dailyDay = parseDayOfWeek(
                    dailyObject.getAny("dayOfWeek", "weekday", "day", "星期", "周几"),
                )
                val courses = parseCourseArray(
                    dailyObject.getAny("courses", "classes", "lessons", "items", "课程", "课程列表")
                        ?.asArrayOrNull(),
                    dailyDayOfWeek = dailyDay,
                    sourceName = AiImportTextArg(R.string.ai_source_daily_courses),
                )
                groupCourses(courses)
            }
    }
    return source.entries.flatMap { (key, value) ->
        val day = parseDayOfWeekText(key) ?: return@flatMap emptyList()
        val coursesArray = when (value) {
            is JsonArray -> value
            is JsonObject -> value.getAny("courses", "classes", "lessons", "items", "课程", "课程列表")
                ?.asArrayOrNull()
            else -> null
        } ?: return@flatMap emptyList()
        groupCourses(parseCourseArray(coursesArray, dailyDayOfWeek = day, sourceName = key))
    }
}

private fun parseFlatCourses(source: JsonObject): List<CourseItem> {
    val courses = source.getAny("courses", "classes", "lessons", "items", "courseItems", "课程", "课程列表")
        ?.asArrayOrNull()
        ?: return emptyList()
    return parseCourseArray(courses, dailyDayOfWeek = null, sourceName = AiImportTextArg(R.string.ai_source_courses))
}

private fun parseCourseArray(
    courses: JsonArray?,
    dailyDayOfWeek: Int?,
    sourceName: Any,
): List<CourseItem> {
    if (courses == null) return emptyList()
    return ensureUniqueCourseIds(
        courses.mapIndexed { index, element ->
            val courseObject = element.asObjectOrNull()
                ?: aiImportError(R.string.ai_error_item_not_course, sourceName, index + 1)
            parseCourse(courseObject, dailyDayOfWeek, index)
        },
    )
}

private fun parseCourse(
    course: JsonObject,
    dailyDayOfWeek: Int?,
    index: Int,
): CourseItem {
    val title = course.getAny("title", "name", "courseName", "课程名", "课程名称", "课程")
        ?.asTextOrNull()
        ?.takeIf(String::isNotBlank)
        ?: aiImportError(R.string.ai_error_missing_title, index + 1)
    val time = course.getAny("time", "slot", "courseTime", "上课时间", "时间")?.asObjectOrNull()
    val dayOfWeek = parseDayOfWeek(
        time?.getAny("dayOfWeek", "weekday", "day", "星期", "周几")
            ?: course.getAny("dayOfWeek", "weekday", "day", "星期", "周几"),
    ) ?: dailyDayOfWeek ?: aiImportError(R.string.ai_error_missing_weekday, title)
    val nodeRange = parseNodeRange(
        time?.getAny("nodes", "sections", "periods", "period", "node", "节次", "上课节次")
            ?: course.getAny("nodes", "sections", "periods", "period", "node", "节次", "上课节次"),
    )
    val startNode = parseNode(
        time?.getAny("startNode", "startSection", "startPeriod", "sectionStart", "periodStart", "start", "开始节次")
            ?: course.getAny("startNode", "startSection", "startPeriod", "sectionStart", "periodStart", "start", "开始节次"),
    ) ?: nodeRange?.first ?: aiImportError(R.string.ai_error_missing_start_node, title)
    val endNode = parseNode(
        time?.getAny("endNode", "endSection", "endPeriod", "sectionEnd", "periodEnd", "end", "结束节次")
            ?: course.getAny("endNode", "endSection", "endPeriod", "sectionEnd", "periodEnd", "end", "结束节次"),
    ) ?: nodeRange?.second ?: startNode
    val teacher = course.getAny("teacher", "teachers", "instructor", "教师", "老师")
        ?.asTextOrNull()
        .orEmpty()
    val location = course.getAny("location", "room", "classroom", "place", "地点", "教室")
        ?.asTextOrNull()
        .orEmpty()
    val weeks = parseWeeks(
        course.getAny("weeks", "week", "weekRange", "周次", "教学周"),
        time?.getAny("weeks", "week", "weekRange", "周次", "教学周"),
    )
    return CourseItem(
        id = course.getAny("id", "courseId", "课程ID")
            ?.asTextOrNull()
            ?.takeIf(String::isNotBlank)
            ?: buildCourseId(title, teacher, location, dayOfWeek, startNode, endNode, weeks, index),
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        category = parseCourseCategory(course.getAny("category", "type", "类别", "类型")),
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )
}

private fun groupCourses(courses: List<CourseItem>): List<DailySchedule> {
    return courses
        .groupBy { it.time.dayOfWeek }
        .toSortedMap()
        .map { (day, dayCourses) ->
            DailySchedule(
                dayOfWeek = day,
                courses = dayCourses.sortedWith(
                    compareBy<CourseItem> { it.time.startNode }
                        .thenBy { it.time.endNode }
                        .thenBy { it.title },
                ),
            )
        }
}

private fun ensureUniqueCourseIds(courses: List<CourseItem>): List<CourseItem> {
    val seen = mutableMapOf<String, Int>()
    return courses.map { course ->
        val previousCount = seen[course.id] ?: 0
        seen[course.id] = previousCount + 1
        if (previousCount == 0) course else course.copy(id = course.uniqueId(previousCount + 1))
    }
}

private fun ensureUniqueScheduleCourseIds(dailySchedules: List<DailySchedule>): List<DailySchedule> {
    val seen = mutableMapOf<String, Int>()
    return dailySchedules.map { daily ->
        daily.copy(
            courses = daily.courses.map { course ->
                val previousCount = seen[course.id] ?: 0
                seen[course.id] = previousCount + 1
                if (previousCount == 0) course else course.copy(id = course.uniqueId(previousCount + 1))
            },
        )
    }
}

private fun CourseItem.uniqueId(occurrence: Int): String =
    "$id-${time.dayOfWeek}-${time.startNode}-${time.endNode}-$occurrence"

private fun JsonObject.getAny(vararg keys: String): JsonElement? {
    keys.forEach { key -> this[key]?.let { return it } }
    keys.forEach { key ->
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let { return it }
    }
    return null
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asTextOrNull(): String? {
    return when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> contentOrNull?.trim()
        is JsonArray -> mapNotNull { it.asTextOrNull()?.takeIf(String::isNotBlank) }
            .joinToString("、")
            .takeIf(String::isNotBlank)
        is JsonObject -> getAny("text", "output_text", "value", "name", "content")?.asTextOrNull()
    }
}

private fun parseDayOfWeek(element: JsonElement?): Int? {
    val text = element.asTextOrNull() ?: return null
    return parseDayOfWeekText(text)
}

private fun parseDayOfWeekText(text: String): Int? {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return null
    if (normalized.contains("sun") || normalized.contains("周日") || normalized.contains("星期日") ||
        normalized.contains("礼拜日") || normalized.contains("周天") || normalized.contains("星期天")
    ) {
        return 7
    }
    listOf(
        "mon" to 1,
        "tue" to 2,
        "wed" to 3,
        "thu" to 4,
        "fri" to 5,
        "sat" to 6,
        "一" to 1,
        "二" to 2,
        "三" to 3,
        "四" to 4,
        "五" to 5,
        "六" to 6,
    ).firstOrNull { (token, _) -> normalized.contains(token) }?.let { return it.second }
    return Regex("\\d").find(normalized)?.value?.toIntOrNull()?.takeIf { it in 1..7 }
}

private fun parseNode(element: JsonElement?): Int? {
    return when (element) {
        is JsonArray -> element.firstOrNull()?.let(::parseNode)
        else -> element.asTextOrNull()
            ?.takeUnless { it.contains(":") }
            ?.let { Regex("\\d{1,2}").find(it)?.value?.toIntOrNull() }
            ?.takeIf { it in 1..32 }
    }
}

private fun parseNodeRange(element: JsonElement?): Pair<Int, Int>? {
    return when (element) {
        is JsonArray -> {
            val nodes = element.mapNotNull(::parseNode)
            if (nodes.isEmpty()) null else nodes.first() to nodes.last()
        }
        else -> {
            val text = element.asTextOrNull()?.takeUnless { it.contains(":") } ?: return null
            val range = WEEK_OR_NODE_RANGE_REGEX.find(text)
            if (range != null) {
                val start = range.groupValues[1].toIntOrNull()
                val end = range.groupValues[2].toIntOrNull()
                if (start != null && end != null && start in 1..32 && end in start..32) return start to end
            }
            val nodes = Regex("\\d{1,2}").findAll(text)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 1..32 }
                .toList()
            if (nodes.isEmpty()) null else nodes.first() to nodes.last()
        }
    }
}

private fun parseWeeks(vararg elements: JsonElement?): List<Int> {
    elements.forEach { element ->
        parseWeeksElement(element)?.let { return it }
    }
    return emptyList()
}

private fun parseWeeksElement(element: JsonElement?): List<Int>? {
    return when (element) {
        null, JsonNull -> null
        is JsonArray -> element
            .flatMap { parseWeeksElement(it).orEmpty() }
            .filter { it in 1..60 }
            .distinct()
            .sorted()
        is JsonObject -> {
            element.getAny("weeks", "week", "weekRange", "周次", "教学周")
                ?.let(::parseWeeksElement)
                ?: parseWeekRangeObject(element)
        }
        else -> parseWeeksText(element.asTextOrNull())
    }
}

private fun parseWeekRangeObject(obj: JsonObject): List<Int>? {
    val start = obj.getAny("start", "from", "begin", "开始周")?.asTextOrNull()?.firstNumber()
    val end = obj.getAny("end", "to", "结束周")?.asTextOrNull()?.firstNumber() ?: start
    if (start == null || end == null) return null
    val text = obj.asTextOrNull().orEmpty()
    return filterOddEven((start..end).toList(), text)
}

private fun parseWeeksText(rawText: String?): List<Int>? {
    val text = rawText?.trim() ?: return null
    if (text.isBlank()) return emptyList()
    if (text.contains("全") || text.contains("每周") || text.contains("all", ignoreCase = true)) {
        return emptyList()
    }
    val normalized = text
        .replace("第", "")
        .replace("周", "")
        .replace(" ", "")
    val weeks = mutableListOf<Int>()
    WEEK_OR_NODE_RANGE_REGEX.findAll(normalized).forEach { match ->
        val start = match.groupValues[1].toIntOrNull()
        val end = match.groupValues[2].toIntOrNull()
        if (start != null && end != null) weeks += start..end
    }
    val withoutRanges = WEEK_OR_NODE_RANGE_REGEX.replace(normalized, " ")
    Regex("\\d{1,2}").findAll(withoutRanges).forEach { match ->
        match.value.toIntOrNull()?.let { weeks += it }
    }
    return filterOddEven(weeks, text)
        .filter { it in 1..60 }
        .distinct()
        .sorted()
}

private fun filterOddEven(weeks: List<Int>, text: String): List<Int> {
    val oddOnly = text.contains("单") || text.contains("odd", ignoreCase = true)
    val evenOnly = text.contains("双") || text.contains("even", ignoreCase = true)
    return weeks.filter { week ->
        (!oddOnly || week % 2 == 1) && (!evenOnly || week % 2 == 0)
    }
}

private fun String.firstNumber(): Int? = Regex("\\d{1,2}").find(this)?.value?.toIntOrNull()

private fun parseCourseCategory(element: JsonElement?): CourseCategory {
    val text = element.asTextOrNull()?.lowercase().orEmpty()
    return if (text.contains("exam") || text.contains("考试") || text.contains("测验")) {
        CourseCategory.Exam
    } else {
        CourseCategory.Course
    }
}

private fun buildCourseId(
    title: String,
    teacher: String,
    location: String,
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    weeks: List<Int>,
    index: Int,
): String {
    val raw = listOf(title, teacher, location, dayOfWeek, startNode, endNode, weeks.joinToString(","))
        .joinToString("|")
    val hash = MessageDigest.getInstance("SHA-1")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "ai-$dayOfWeek-$startNode-$endNode-$index-$hash"
}

private fun validateImportedSchedule(schedule: TermSchedule) {
    val courses = schedule.dailySchedules.flatMap { daily ->
        aiImportRequire(daily.dayOfWeek in 1..7, R.string.ai_error_invalid_weekday, daily.dayOfWeek)
        daily.courses.onEach { course ->
            aiImportRequire(course.id.isNotBlank(), R.string.ai_error_empty_course_id)
            aiImportRequire(course.title.isNotBlank(), R.string.ai_error_empty_course_title)
            aiImportRequire(course.time.dayOfWeek in 1..7, R.string.ai_error_invalid_course_weekday, course.time.dayOfWeek)
            aiImportRequire(course.time.dayOfWeek == daily.dayOfWeek, R.string.ai_error_weekday_mismatch)
            aiImportRequire(course.time.startNode in 1..32, R.string.ai_error_invalid_start_node, course.time.startNode)
            aiImportRequire(course.time.endNode in course.time.startNode..32, R.string.ai_error_invalid_end_node, course.time.endNode)
            aiImportRequire(course.weeks.all { it in 1..60 }, R.string.ai_error_invalid_week)
        }
    }
    aiImportRequire(courses.size <= 1000, R.string.ai_error_too_many_courses)
}

private val WEEK_OR_NODE_RANGE_REGEX = Regex("(\\d{1,2})\\s*(?:-|~|—|–|至|到)\\s*(\\d{1,2})")

private val KNOWN_AI_ENDPOINT_SUFFIXES = listOf(
    "/chat/completions",
    "/completions",
    "/responses",
    "/messages",
)
private val AI_VERSION_SEGMENT = Regex("/v\\d+$", RegexOption.IGNORE_CASE)

internal fun normalizeAiEndpoint(rawUrl: String): String {
    val trimmedRaw = rawUrl.trim().trimEnd('/')
    if (trimmedRaw.isEmpty()) return trimmedRaw
    val trimmed = requireHttpsAiEndpoint(
        if (trimmedRaw.contains("://")) trimmedRaw else "https://$trimmedRaw",
    )
    val schemeEnd = trimmed.indexOf("://") + 3
    val pathStart = trimmed.indexOf('/', startIndex = schemeEnd)
    val lowerPath = if (pathStart >= 0) trimmed.substring(pathStart).lowercase() else ""
    if (KNOWN_AI_ENDPOINT_SUFFIXES.any { lowerPath.endsWith(it) }) return trimmed
    if (lowerPath.isEmpty() || lowerPath == "/") return "$trimmed/v1/chat/completions"
    if (AI_VERSION_SEGMENT.containsMatchIn(lowerPath)) return "$trimmed/chat/completions"
    return "$trimmed/v1/chat/completions"
}

// 解码前按原图尺寸算出 BitmapFactory 的降采样倍率，长边超过 maxSide 两倍时逐级折半
internal fun aiImageSampleSize(width: Int, height: Int, maxSide: Int): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var side = width.coerceAtLeast(height)
    var sampleSize = 1
    while (side / 2 >= maxSide) {
        side /= 2
        sampleSize *= 2
    }
    return sampleSize
}

internal fun requireHttpsAiEndpoint(url: String): String {
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    aiImportRequire(scheme == "https", R.string.ai_error_https_required)
    return url
}
