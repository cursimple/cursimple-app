package com.x500x.cursimple.app.util

import android.content.Context
import android.webkit.WebView
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Compatibility parser and runtime for XiaoAi Schedule (小爱课程表 / AISchedule) rules & outputs.
 */
object AiScheduleCompat {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseJson(jsonText: String): ScheduleSharePayload {
        val element = runCatching { jsonParser.parseToJsonElement(jsonText.trim()) }.getOrNull()
            ?: return ScheduleSharePayload(schedule = emptySchedule())

        val rootArray = element as? JsonArray
        val rootObj = element as? JsonObject

        val coursesArray: JsonArray = when {
            rootArray != null -> rootArray
            rootObj != null -> {
                (rootObj["courses"] as? JsonArray)
                    ?: (rootObj["data"] as? JsonArray)
                    ?: (rootObj["result"] as? JsonArray)
                    ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }

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

            val title = optStr("name", "title").ifBlank { "未命名课程" }
            val location = optStr("position", "location", "room")
            val teacher = optStr("teacher")
            val day = optInt("day", 1).coerceIn(1, 7)

            // Parse sections
            val sectionNumbers = mutableListOf<Int>()
            val sectionsArray = item["sections"] as? JsonArray
            if (sectionsArray != null) {
                for (s in sectionsArray) {
                    val secObj = s as? JsonObject
                    if (secObj != null) {
                        val num = secObj["section"]?.jsonPrimitive?.intOrNull
                        if (num != null && num > 0) sectionNumbers.add(num)
                    } else {
                        val num = s.jsonPrimitive.intOrNull
                        if (num != null && num > 0) sectionNumbers.add(num)
                    }
                }
            }
            val startNode = sectionNumbers.minOrNull() ?: optInt("startNode", 1).coerceAtLeast(1)
            val endNode = sectionNumbers.maxOrNull() ?: optInt("endNode", startNode).coerceAtLeast(startNode)

            // Parse weeks
            val weeks = mutableListOf<Int>()
            val weeksArray = item["weeks"] as? JsonArray
            if (weeksArray != null) {
                for (w in weeksArray) {
                    val weekNum = w.jsonPrimitive.intOrNull
                    if (weekNum != null && weekNum in 1..40) weeks.add(weekNum)
                }
            }

            val isExam = title.contains("考试") || title.contains("期末")

            courses.add(
                CourseItem(
                    id = "aischedule-${UUID.randomUUID()}",
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
            termId = "term-aischedule-${System.currentTimeMillis()}",
            updatedAt = LocalDate.now().toString(),
            dailySchedules = dailySchedules,
            externalImport = true,
        )

        return ScheduleSharePayload(
            termName = "小爱课程表导入学期",
            termStartDate = null,
            schedule = schedule,
            manualCourses = emptyList(),
        )
    }

    private fun emptySchedule(): TermSchedule {
        return TermSchedule(
            termId = "term-aischedule-${System.currentTimeMillis()}",
            updatedAt = LocalDate.now().toString(),
            dailySchedules = (1..7).map { DailySchedule(it, emptyList()) },
            externalImport = true,
        )
    }

    /**
     * Executes an AISchedule parser script `scheduleHtmlParser(html)` inside a headless WebView.
     */
    suspend fun executeJsParser(
        context: Context,
        jsCode: String,
        htmlContent: String,
    ): Result<ScheduleSharePayload> = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            val webView = WebView(context.applicationContext)
            webView.settings.javaScriptEnabled = true

            // Escape htmlContent into valid JS string literal
            val escapedHtml = org.json.JSONObject.quote(htmlContent)
            val script = """
                (function() {
                    try {
                        $jsCode
                        if (typeof scheduleHtmlParser === 'function') {
                            var result = scheduleHtmlParser($escapedHtml);
                            return JSON.stringify(result);
                        } else {
                            return JSON.stringify({ error: "scheduleHtmlParser function not found" });
                        }
                    } catch (e) {
                        return JSON.stringify({ error: e.toString() });
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script) { result ->
                webView.destroy()
                if (result == null || result == "null" || result == "\"null\"") {
                    continuation.resume(Result.failure(IllegalArgumentException("小爱课程表脚本解析无返回")))
                    return@evaluateJavascript
                }
                val rawJson = if (result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                } else {
                    result
                }

                if (rawJson.contains("\"error\":")) {
                    continuation.resume(Result.failure(IllegalArgumentException("脚本执行出错: $rawJson")))
                } else {
                    runCatching {
                        parseJson(rawJson)
                    }.fold(
                        onSuccess = { continuation.resume(Result.success(it)) },
                        onFailure = { continuation.resume(Result.failure(it)) },
                    )
                }
            }
        }
    }
}
