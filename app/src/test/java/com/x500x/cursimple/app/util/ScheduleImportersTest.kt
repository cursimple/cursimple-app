package com.x500x.cursimple.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import com.x500x.cursimple.core.kernel.time.BeijingTime
import java.time.ZoneId

class ScheduleImportersTest {
    @Before fun setZone() { BeijingTime.setOverrideZone(ZoneId.of("Asia/Shanghai")) }
    @After fun restoreZone() { BeijingTime.setOverrideZone(null) }

    private fun icsEvent(body: String) = ScheduleIcsImporter.parse(
        "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:测试课\n$body\nEND:VEVENT\nEND:VCALENDAR",
    ).schedule!!.dailySchedules.flatMap { it.courses }

    @Test fun `UTC weekly BYDAY is evaluated before timezone day conversion`() {
        val course = icsEvent("DTSTART:20260907T170000Z\nDTEND:20260907T180000Z\nRRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=2").single()
        assertEquals(2, course.time.dayOfWeek)
        assertEquals("01:00", course.reminderStartTime)
        assertEquals(listOf(1, 2), course.weeks)
    }

    @Test fun `weekly TZID recurrence preserves source wall time across DST`() {
        val courses = icsEvent("DTSTART;TZID=America/New_York:20261026T090000\nDTEND;TZID=America/New_York:20261026T100000\nRRULE:FREQ=WEEKLY;COUNT=2")
        assertEquals(setOf("21:00", "22:00"), courses.map { it.reminderStartTime }.toSet())
        assertEquals(listOf(1), courses.single { it.reminderStartTime == "21:00" }.weeks)
        assertEquals(listOf(2), courses.single { it.reminderStartTime == "22:00" }.weeks)
    }

    @Test fun `multi weekday COUNT over forty is not silently truncated`() {
        val courses = icsEvent("DTSTART:20260907T080000\nDTEND:20260907T090000\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=60")
        assertEquals(60, courses.sumOf { it.weeks.size })
        assertTrue(courses.all { it.weeks.last() == 30 })
    }

    @Test fun `UNTIL clock is inclusive and EXDATE removes the exact instant`() {
        val courses = icsEvent("DTSTART:20260907T080000\nDURATION:PT1H\nRRULE:FREQ=WEEKLY;UNTIL=20260921T075959\nEXDATE:20260914T080000")
        assertEquals(listOf(1), courses.single().weeks)
        assertEquals("09:00", courses.single().reminderEndTime)
    }

    @Test fun `malformed empty and unsupported ICS report failure`() {
        for (body in listOf("garbage", "BEGIN:VCALENDAR\nEND:VCALENDAR",
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART:bad\nEND:VEVENT\nEND:VCALENDAR",
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART:20260907T080000\nRRULE:FREQ=DAILY\nEND:VEVENT\nEND:VCALENDAR")) {
            assertTrue(body, runCatching { ScheduleIcsImporter.parse(body) }.isFailure)
        }
    }

    @Test fun `nested alarm content cannot replace the course metadata`() {
        val course = icsEvent("DTSTART:20260907T080000\nDTEND:20260907T090000\nDESCRIPTION:教师:张老师\nBEGIN:VALARM\nSUMMARY:错误标题\nDESCRIPTION:提醒\nEND:VALARM").single()
        assertEquals("测试课", course.title)
        assertEquals("张老师", course.teacher)
    }

    @Test fun `ICS UTC and TZID clocks are converted to the timetable timezone`() {
        for (start in listOf("DTSTART:20260907T000000Z", "DTSTART;TZID=Europe/London:20260907T010000")) {
            val payload = ScheduleIcsImporter.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:时区课\n$start\nDTEND:20260907T020000Z\nEND:VEVENT\nEND:VCALENDAR")
            val course = payload.schedule!!.dailySchedules.flatMap { it.courses }.single()
            assertEquals("08:00", course.reminderStartTime)
            assertEquals("10:00", course.reminderEndTime)
        }
    }

    @Test fun `weekly BYDAY count counts occurrences across multiple weekdays`() {
        val payload = ScheduleIcsImporter.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:双日课\nDTSTART:20260907T080000\nDTEND:20260907T090000\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4\nEND:VEVENT\nEND:VCALENDAR")
        val courses = payload.schedule!!.dailySchedules.flatMap { it.courses }
        assertEquals(setOf(1, 3), courses.map { it.time.dayOfWeek }.toSet())
        assertTrue(courses.all { it.weeks == listOf(1, 2) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid WakeUp input reports failure rather than a successful empty timetable`() {
        ScheduleWakeUpImporter.parseJson("not a course package")
    }

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
