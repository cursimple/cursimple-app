package com.x500x.cursimple.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleImportersTest {

    @Test
    fun testIcsImporterParsesEventsAndWeeks() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-1@cursimple
            SUMMARY:高等数学
            LOCATION:教学楼 A101
            DESCRIPTION:教师: 张教授
            DTSTART:20260907T080000
            DTEND:20260907T094000
            RRULE:FREQ=WEEKLY;COUNT=16;INTERVAL=1
            END:VEVENT
            BEGIN:VEVENT
            UID:test-2@cursimple
            SUMMARY:大学英语
            LOCATION:逸夫楼 B202
            DESCRIPTION:任课教师: 李老师
            DTSTART:20260908T100000
            DTEND:20260908T114000
            RRULE:FREQ=WEEKLY;COUNT=8;INTERVAL=2
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val payload = ScheduleIcsImporter.parse(ics)
        assertNotNull(payload.schedule)
        assertEquals("2026-09-07", payload.termStartDate)

        val mondayCourses = payload.schedule!!.dailySchedules.first { it.dayOfWeek == 1 }.courses
        assertEquals(1, mondayCourses.size)
        val math = mondayCourses[0]
        assertEquals("高等数学", math.title)
        assertEquals("教学楼 A101", math.location)
        assertEquals("张教授", math.teacher)
        assertEquals(16, math.weeks.size)
        assertEquals((1..16).toList(), math.weeks)

        val tuesdayCourses = payload.schedule!!.dailySchedules.first { it.dayOfWeek == 2 }.courses
        assertEquals(1, tuesdayCourses.size)
        val english = tuesdayCourses[0]
        assertEquals("大学英语", english.title)
        assertEquals("逸夫楼 B202", english.location)
        assertEquals("李老师", english.teacher)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), english.weeks)
    }

    @Test
    fun testWakeUpImporterParsesJson() {
        val json = """
            {
              "courses": [
                {
                  "name": "大学物理",
                  "room": "实验楼 105",
                  "teacher": "王教授",
                  "day": 3,
                  "startNode": 3,
                  "step": 2,
                  "startWeek": 1,
                  "endWeek": 16,
                  "type": 1
                }
              ],
              "setting": {
                "tableName": "2026秋季学期",
                "startDate": "2026-09-01"
              }
            }
        """.trimIndent()

        val payload = ScheduleWakeUpImporter.parseJson(json)
        assertNotNull(payload.schedule)
        assertEquals("2026秋季学期", payload.termName)
        assertEquals("2026-09-01", payload.termStartDate)

        val wedCourses = payload.schedule!!.dailySchedules.first { it.dayOfWeek == 3 }.courses
        assertEquals(1, wedCourses.size)
        val physics = wedCourses[0]
        assertEquals("大学物理", physics.title)
        assertEquals("实验楼 105", physics.location)
        assertEquals("王教授", physics.teacher)
        assertEquals(3, physics.time.startNode)
        assertEquals(4, physics.time.endNode)
        // type 1 = odd weeks
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), physics.weeks)
    }

    @Test
    fun testAiScheduleImporterParsesArray() {
        val json = """
            [
              {
                "name": "数据结构",
                "position": "信工楼 301",
                "teacher": "赵老师",
                "weeks": [1, 2, 3, 4, 5, 6],
                "day": 4,
                "sections": [5, 6]
              }
            ]
        """.trimIndent()

        val payload = AiScheduleCompat.parseJson(json)
        assertNotNull(payload.schedule)

        val thuCourses = payload.schedule!!.dailySchedules.first { it.dayOfWeek == 4 }.courses
        assertEquals(1, thuCourses.size)
        val ds = thuCourses[0]
        assertEquals("数据结构", ds.title)
        assertEquals("信工楼 301", ds.location)
        assertEquals("赵老师", ds.teacher)
        assertEquals(5, ds.time.startNode)
        assertEquals(6, ds.time.endNode)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ds.weeks)
    }
}
