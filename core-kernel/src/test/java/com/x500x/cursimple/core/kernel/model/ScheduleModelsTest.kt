package com.x500x.cursimple.core.kernel.model

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
}

