package com.x500x.cursimple.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiScheduleImportClientTest {

    @Test
    fun `extracts chat completion message content and normalizes duplicate course ids`() {
        val content = """
            {
              "schedule": {
                "termId": "ai-image-import",
                "updatedAt": "",
                "dailySchedules": [
                  {
                    "dayOfWeek": 1,
                    "courses": [
                      {
                        "id": "776246",
                        "title": "高等数学A（下）",
                        "teacher": "黄晓娟",
                        "location": "西7-402c",
                        "weeks": [1, 2, 3, 4],
                        "category": "course",
                        "time": {"dayOfWeek": 1, "startNode": 1, "endNode": 1}
                      }
                    ]
                  },
                  {
                    "dayOfWeek": 3,
                    "courses": [
                      {
                        "id": "776246",
                        "title": "高等数学A（下）",
                        "teacher": "黄晓娟",
                        "location": "西7-402c",
                        "weeks": [1, 2, 3, 4],
                        "category": "course",
                        "time": {"dayOfWeek": 3, "startNode": 2, "endNode": 2}
                      }
                    ]
                  }
                ]
              },
              "manualCourses": []
            }
        """.trimIndent()
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": ${quoteJson(content)}
                  }
                }
              ]
            }
        """.trimIndent()

        val extracted = extractAiTextContent(response)
        val payload = parseAiScheduleImportContent(extracted)
        val courses = payload.schedule!!.dailySchedules.flatMap { it.courses }

        assertEquals(2, courses.size)
        assertEquals("776246", courses[0].id)
        assertNotEquals(courses[0].id, courses[1].id)
        assertEquals(3, courses[1].time.dayOfWeek)
        assertEquals(2, courses[1].time.startNode)
    }

    @Test
    fun `parses flexible Chinese course list with odd and even weeks`() {
        val payload = parseAiScheduleImportContent(
            """
            ```json
            {
              "courses": [
                {
                  "课程名": "综合英语（发展）2",
                  "教师": "刘静",
                  "地点": "S2",
                  "周次": "单1-15周",
                  "星期": "星期四",
                  "节次": "第1节"
                },
                {
                  "课程名": "大学英语视听说（发展）2",
                  "教师": "刘静",
                  "地点": "西6-307c",
                  "周次": "双2-16周",
                  "星期": "星期四",
                  "节次": "1"
                },
                {
                  "课程名": "形势与政策 2",
                  "教师": "郭俊",
                  "地点": "西7-501c",
                  "周次": "8-12周",
                  "星期": "周四",
                  "节次": "晚间课8"
                }
              ]
            }
            ```
            """.trimIndent(),
        )

        val courses = payload.schedule!!.dailySchedules.single().courses
        val comprehensiveEnglish = courses.first { it.title == "综合英语（发展）2" }
        val listening = courses.first { it.title == "大学英语视听说（发展）2" }
        val policy = courses.first { it.title == "形势与政策 2" }

        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), comprehensiveEnglish.weeks)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), listening.weeks)
        assertEquals(listOf(8, 9, 10, 11, 12), policy.weeks)
        assertTrue(courses.all { it.time.dayOfWeek == 4 })
        assertEquals(8, policy.time.startNode)
    }

    private fun quoteJson(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
