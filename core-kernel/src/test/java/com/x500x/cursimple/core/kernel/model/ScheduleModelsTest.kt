package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleModelsTest {

    @Test
    fun coursesOfDay_returnsMatchedCourses() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-25T08:00:00Z",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "c1",
                            title = "高等数学",
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, schedule.coursesOfDay(1).size)
        assertEquals(0, schedule.coursesOfDay(2).size)
    }

    @Test
    fun placeholderCourseRoundTripsAndCanBeFilteredForDisplay() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val course = CourseItem(
            id = "placeholder",
            title = "早自习",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            reminderOnly = true,
            slotLabelOverride = "早自习",
            reminderStartTime = "07:10",
            reminderEndTime = "07:50",
        )

        val decoded = json.decodeFromString(CourseItem.serializer(), json.encodeToString(CourseItem.serializer(), course))

        assertEquals(true, decoded.isReminderOnly())
        assertEquals("早自习", decoded.slotLabelOverride)
        assertEquals(emptyList<CourseItem>(), listOf(decoded).visibleScheduleCourses())
        assertEquals(listOf(decoded), listOf(decoded))
    }

    @Test
    fun reminderSlotLabelUsesOverrideBeforeTimingProfileLabel() {
        val timingProfile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(ClassSlotTime(1, 1, "08:00", "08:45", "第一节课")),
        )
        val course = CourseItem(
            id = "placeholder",
            title = "早自习",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            reminderOnly = true,
            slotLabelOverride = "早自习",
        )

        assertEquals("早自习", course.reminderSlotLabel(timingProfile))
    }
}

