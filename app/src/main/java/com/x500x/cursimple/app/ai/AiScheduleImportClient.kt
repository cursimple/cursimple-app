package com.x500x.cursimple.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.Duration

class AiScheduleImportClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(60))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    fun importFromImage(context: Context, imageUri: Uri, config: AiImportConfig): AiScheduleImportPayload {
        require(config.isComplete) { "请先在设置页配置 AI API URL 和 Key" }
        val dataUrl = context.imageDataUrl(imageUri)
        val requestJson = buildRequestJson(config, dataUrl).toString()
        val request = Request.Builder()
            .url(config.apiUrl.trim())
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val bodyText = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "AI 请求失败：HTTP ${response.code}" }
            response.body.string()
        }
        val content = extractTextContent(bodyText)
        val payload = json.decodeFromString(AiScheduleImportPayload.serializer(), extractJsonObject(content))
        require(payload.schedule != null || payload.manualCourses.isNotEmpty()) { "AI 未识别到课程数据" }
        payload.schedule?.let(::validateImportedSchedule)
        return payload
    }

    private fun buildRequestJson(config: AiImportConfig, dataUrl: String): JsonObject {
        val model = config.model.trim().ifBlank { "gpt-4o-mini" }
        return if (config.apiUrl.contains("/responses", ignoreCase = true)) {
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

    private fun extractTextContent(bodyText: String): String {
        val root = json.parseToJsonElement(bodyText).jsonObject
        root["output_text"]?.jsonPrimitive?.contentOrNull?.let { return it }
        root["choices"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.let { return it.asText() }
        root["output"]?.jsonArray?.forEach { item ->
            item.jsonObject["content"]?.jsonArray?.forEach { content ->
                val obj = content.jsonObject
                val text = obj["text"]?.jsonPrimitive?.contentOrNull
                    ?: obj["output_text"]?.jsonPrimitive?.contentOrNull
                if (!text.isNullOrBlank()) return text
            }
        }
        error("AI 响应中没有可用文本")
    }

    private fun JsonElement.asText(): String {
        return when (this) {
            is JsonPrimitive -> contentOrNull.orEmpty()
            else -> jsonArray.mapNotNull { part ->
                part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.joinToString("\n")
        }
    }

    private fun extractJsonObject(text: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)?.groupValues?.getOrNull(1)
        val candidate = fenced ?: text
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 未返回 JSON 数据" }
        return candidate.substring(start, end + 1)
    }

    private fun validateImportedSchedule(schedule: TermSchedule) {
        val courses = schedule.dailySchedules.flatMap { daily ->
            require(daily.dayOfWeek in 1..7) { "AI 返回了无效星期: ${daily.dayOfWeek}" }
            daily.courses.onEach { course ->
                require(course.id.isNotBlank()) { "AI 返回了空课程 ID" }
                require(course.title.isNotBlank()) { "AI 返回了空课程名称" }
                require(course.time.dayOfWeek in 1..7) { "AI 返回了无效课程星期: ${course.time.dayOfWeek}" }
                require(course.time.dayOfWeek == daily.dayOfWeek) { "AI 课程星期与日程分组不一致" }
                require(course.time.startNode in 1..32) { "AI 返回了无效开始节次: ${course.time.startNode}" }
                require(course.time.endNode in course.time.startNode..32) { "AI 返回了无效结束节次: ${course.time.endNode}" }
                require(course.weeks.all { it in 1..60 }) { "AI 返回了无效教学周" }
            }
        }
        require(courses.size <= 1000) { "AI 返回的课程数量过多" }
    }

    private fun Context.imageDataUrl(uri: Uri): String {
        val bitmap = contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "无法打开图片" }
            BitmapFactory.decodeStream(stream)
        } ?: error("图片格式不支持")
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
            周次如 1-16 转成完整整数数组；单周/双周只列实际周次。
            不确定教师或地点时填空字符串，不要编造。
        """
    }
}
