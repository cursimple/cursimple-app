package com.x500x.cursimple.app.vendor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VendorCourseModelsTest {
    private val course = VendorCourse("math", "2026-10-08", "数学", "教学楼 301", "李老师", 1791460800000, 1791465600000, 1, 2, 4)
    private val snapshot = VendorCourseSnapshot(generatedAt = 1, courses = listOf(course))
    @Test fun movedCoursesRetainTheirSourceWeekLocationAndTeacher() {
        val term = LocalDate.of(2026, 9, 7)
        val course = com.x500x.cursimple.core.kernel.model.CourseItem("lab", "Lab", teacher = "Teacher", weeks = listOf(1),
            weekLocations = mapOf(1 to "Source lab", 2 to "Other lab"),
            weekTeachers = mapOf(1 to "Source teacher", 2 to "Other teacher"),
            time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(1, 1, 2))
        val move = com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride("move", com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = "2026-09-07", targetDate = "2026-09-14", moveCourseId = "lab", moveToStartNode = 3, moveToEndNode = 4)
        val timing = com.x500x.cursimple.core.kernel.model.TermTimingProfile(termStartDate = term.toString(), slotTimes = listOf(com.x500x.cursimple.core.kernel.model.ClassSlotTime(3, 4, "10:00", "11:30")))
        val result = vendorCourses(LocalDate.of(2026, 9, 14), listOf(course), timing, term, listOf(move), com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings.NONE, java.time.ZoneId.of("Asia/Shanghai")).single()
        assertEquals("Source lab", result.location)
        assertEquals("Source teacher", result.teacher)
        assertEquals(1, result.week)
        assertEquals(10, java.time.Instant.ofEpochMilli(result.startAt).atZone(java.time.ZoneId.of("Asia/Shanghai")).hour)
    }
    @Test fun providersUseTheirOwnSchemasAndRealTimestamps() {
        val vivo = vivoCourseData(snapshot, "com.example.timetable")
        val item = vivo["courseInfoList"]!!.jsonArray[0].jsonObject["classBeanList"]!!.jsonArray[0].jsonObject
        assertEquals(course.startAt, item["classStartTime"]!!.jsonPrimitive.long)
        assertEquals(course.location, item["position"]!!.jsonPrimitive.content)
        assertEquals(course.teacher, item["teacherName"]!!.jsonPrimitive.content)
        val oppo = oppoCourseData(snapshot, LocalDate.parse(course.date), "com.example.timetable", true)
        assertEquals(course.startAt.toString(), oppo["data"]!!.jsonObject["courseData"]!!.jsonArray[0].jsonObject["startTime"]!!.jsonPrimitive.content)
        assertEquals(-1, oppoCourseData(snapshot, LocalDate.parse(course.date), "com.example.timetable", false)["code"]!!.jsonPrimitive.int)
    }
    @Test fun honorUsesOwnIdentityBoundedDatesAndOptionalAodDetails() {
        val config = VendorCourseConfig(enabled = true, aodEnabled = true, honorCapsuleType = "123", honorCardType = "456", honorAodIconUrl = "https://example.com/icon.png")
        val result = honorCoursePlans(snapshot, "com.example.timetable", config, config.honorCapsuleType, 1)
        assertEquals("com.example.timetable", result["packageName"]!!.jsonPrimitive.content)
        val plan = result["planFeedbackDatas"]!!.jsonArray[0].jsonObject
        assertEquals(course.endAt, plan["recurrentEnd"]!!.jsonPrimitive.long)
        val extra = vendorJson.parseToJsonElement(plan["feedbackExtra1"]!!.jsonPrimitive.content).jsonObject
        val aod = vendorJson.parseToJsonElement(extra["aodText"]!!.jsonPrimitive.content).jsonObject
        assertTrue(aod["aodText"]!!.jsonPrimitive.content.contains(course.title))
        assertEquals(config.honorAodIconUrl, aod["aodIconUrl"]!!.jsonPrimitive.content)
        assertTrue(honorCoursePlans(snapshot, "own", config, "123", course.endAt)["planFeedbackDatas"]!!.jsonArray.isEmpty())
    }
    @Test fun fingerprintsIgnoreRefreshTimeButRespectSettingsAndCourseEdits() {
        assertEquals(courseFingerprint(snapshot, VendorCourseConfig()), courseFingerprint(snapshot.copy(generatedAt = 9000), VendorCourseConfig()))
        assertNotEquals(courseFingerprint(snapshot, VendorCourseConfig()), courseFingerprint(snapshot.copy(courses = listOf(course.copy(location = "另一教室"))), VendorCourseConfig()))
        assertNotEquals(courseFingerprint(snapshot, VendorCourseConfig()), courseFingerprint(snapshot, VendorCourseConfig(aodEnabled = true)))
    }
    @Test fun onlyOwnWellFormedScheduleLinksAreConsumed() {
        assertEquals(course.date, vendorScheduleDate(vendorCourseLink("com.example.timetable", course), "com.example.timetable"))
        assertNull(vendorScheduleDate("cursimple://other/schedule?date=2026-10-08", "com.example.timetable"))
        assertNull(vendorScheduleDate("cursimple://com.example.timetable/schedule?date=2026-99-99", "com.example.timetable"))
    }
}
