package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleValidationTest {
    @Test
    fun `valid plugin schedule passes through unchanged`() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-05-01T00:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "course-1",
                            title = "高等数学",
                            weeks = listOf(1, 2, 3),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )

        assertSame(schedule, validatePluginSchedule(schedule))
    }

    @Test
    fun `invalid plugin schedule is rejected before it reaches app state`() {
        val schedule = TermSchedule(
            termId = "bad",
            updatedAt = "2026-05-01T00:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 8,
                    courses = emptyList(),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            validatePluginSchedule(schedule)
        }
    }

    @Test
    fun `course with impossible node range is rejected`() {
        val schedule = TermSchedule(
            termId = "bad",
            updatedAt = "2026-05-01T00:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 2,
                    courses = listOf(
                        CourseItem(
                            id = "bad-course",
                            title = "异常课程",
                            time = CourseTimeSlot(dayOfWeek = 2, startNode = 5, endNode = 4),
                        ),
                    ),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            validatePluginSchedule(schedule)
        }
    }

    @Test
    fun `course is active only when selected week matches`() {
        val course = CourseItem(
            id = "short-course",
            title = "短期课程",
            weeks = listOf(3, 4),
            time = CourseTimeSlot(dayOfWeek = 2, startNode = 1, endNode = 2),
        )
        val allWeeksCourse = course.copy(id = "all-weeks", weeks = emptyList())

        assertTrue(course.isActiveInWeek(3))
        assertFalse(course.isActiveInWeek(5))
        assertTrue(allWeeksCourse.isActiveInWeek(30))
    }

    @Test
    fun `week render source excludes courses from other weeks`() {
        val base = CourseItem(
            id = "base",
            title = "基础课程",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val active = base.copy(id = "active", weeks = listOf(4))
        val inactive = base.copy(id = "inactive", weeks = listOf(5))
        val allWeeks = base.copy(id = "all-weeks", weeks = emptyList())

        val visibleIds = activeCoursesForWeek(listOf(active, inactive, allWeeks), weekNumber = 4)
            .map { it.id }

        assertEquals(listOf("active", "all-weeks"), visibleIds)
    }

    @Test
    fun `week render entries exclude inactive courses when total schedule display is disabled`() {
        val active = course(id = "active", weeks = listOf(4))
        val inactive = course(id = "inactive", weeks = listOf(5))
        val allWeeks = course(id = "all-weeks", weeks = emptyList())

        val entries = buildWeekRenderEntries(
            allCourses = listOf(active, inactive, allWeeks),
            slots = listOf(testSlot()),
            weekIndex = 4,
            totalScheduleDisplayEnabled = false,
        )

        assertEquals(listOf("active", "all-weeks"), entries.map { it.course.id })
        assertTrue(entries.none { it.inactive })
    }

    @Test
    fun `week render entries include inactive courses when total schedule display is enabled`() {
        val active = course(id = "active", weeks = listOf(4))
        val inactive = course(id = "inactive", weeks = listOf(5))

        val entries = buildWeekRenderEntries(
            allCourses = listOf(inactive, active),
            slots = listOf(testSlot()),
            weekIndex = 4,
            totalScheduleDisplayEnabled = true,
        )

        assertEquals(listOf("active", "inactive"), entries.map { it.course.id })
        assertFalse(entries.first { it.course.id == "active" }.inactive)
        assertTrue(entries.first { it.course.id == "inactive" }.inactive)
    }

    @Test
    fun `same cell render entries prefer current week course as main entry`() {
        val active = course(id = "active", title = "本周课", weeks = listOf(4))
        val inactive = course(id = "inactive", title = "非本周课", weeks = listOf(5))

        val entries = buildWeekRenderEntries(
            allCourses = listOf(inactive, active),
            slots = listOf(testSlot()),
            weekIndex = 4,
            totalScheduleDisplayEnabled = true,
        )

        assertEquals(2, entries.size)
        assertEquals("active", entries.first().course.id)
        assertFalse(entries.first().inactive)
        assertTrue(entries.last().inactive)
    }

    @Test
    fun `temporary override renders source weekday courses in actual date column`() {
        val monday = course(id = "monday", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val wednesday = course(id = "wednesday", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )

        val entries = buildWeekRenderEntries(
            allCourses = listOf(monday, wednesday),
            slots = listOf(testSlot()),
            weekIndex = 4,
            weekStart = LocalDate.of(2026, 5, 4),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    startDate = "2026-05-06",
                    endDate = "2026-05-06",
                    sourceDayOfWeek = 1,
                ),
            ),
        )

        assertTrue(entries.any { it.course.id == "monday" && it.placement.dayIndex == 2 })
        assertFalse(entries.any { it.course.id == "wednesday" && it.placement.dayIndex == 2 })
    }

    @Test
    fun `date override uses source date week for active courses`() {
        val sourceWeekCourse = course(id = "source-week", weeks = listOf(5)).copy(
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val targetWeekCourse = course(id = "target-week", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )

        val entries = buildWeekRenderEntries(
            allCourses = listOf(sourceWeekCourse, targetWeekCourse),
            slots = listOf(testSlot()),
            weekIndex = 4,
            weekStart = LocalDate.of(2026, 5, 4),
            termStart = LocalDate.of(2026, 4, 13),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "date-to-date",
                    targetDate = "2026-05-06",
                    sourceDate = "2026-05-11",
                ),
            ),
        )

        assertTrue(entries.any { it.course.id == "source-week" && it.placement.dayIndex == 2 && !it.inactive })
        assertFalse(entries.any { it.course.id == "target-week" && it.placement.dayIndex == 2 })
    }

    @Test
    fun `temporary cancelled course remains rendered with cancelled flag`() {
        val math = course(id = "math", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )

        val entries = buildWeekRenderEntries(
            allCourses = listOf(math),
            slots = listOf(testSlot()),
            weekIndex = 4,
            weekStart = LocalDate.of(2026, 5, 4),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "cancel",
                    type = TemporaryScheduleOverrideType.CancelCourse,
                    targetDate = "2026-05-06",
                    cancelStartNode = 1,
                    cancelEndNode = 2,
                    cancelCourseId = "math",
                ),
            ),
        )

        assertEquals(listOf("math"), entries.map { it.course.id })
        assertEquals(2, entries.single().placement.dayIndex)
        assertTrue(entries.single().temporarilyCancelled)
    }

    @Test
    fun `visible day indices follow saturday and weekend display preferences`() {
        assertEquals(
            (0..6).toList(),
            visibleDayIndices(ScheduleDisplayPreferences(saturdayVisible = false, weekendVisible = true)),
        )
        assertEquals(
            (0..5).toList(),
            visibleDayIndices(ScheduleDisplayPreferences(saturdayVisible = true, weekendVisible = false)),
        )
        assertEquals(
            (0..4).toList(),
            visibleDayIndices(ScheduleDisplayPreferences(saturdayVisible = false, weekendVisible = false)),
        )
    }

    @Test
    fun `week render entries filter hidden weekend columns`() {
        val monday = course(id = "monday", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val saturday = course(id = "saturday", weeks = listOf(4)).copy(
            time = CourseTimeSlot(dayOfWeek = 6, startNode = 1, endNode = 2),
        )

        val weekdaysOnly = buildWeekRenderEntries(
            allCourses = listOf(monday, saturday),
            slots = listOf(testSlot()),
            weekIndex = 4,
            totalScheduleDisplayEnabled = true,
            visibleDayIndices = (0..4).toList(),
        )
        val mondayToSaturday = buildWeekRenderEntries(
            allCourses = listOf(monday, saturday),
            slots = listOf(testSlot()),
            weekIndex = 4,
            totalScheduleDisplayEnabled = true,
            visibleDayIndices = (0..5).toList(),
        )

        assertEquals(listOf("monday"), weekdaysOnly.map { it.course.id })
        assertTrue(mondayToSaturday.any { it.course.id == "saturday" && it.placement.dayIndex == 5 })
    }

    private fun testSlot(): DisplaySlot = DisplaySlot(
        startNode = 1,
        endNode = 2,
        label = "第一节",
        startTime = "08:00",
        endTime = "09:35",
    )

    private fun course(
        id: String,
        title: String = id,
        weeks: List<Int>,
    ): CourseItem = CourseItem(
        id = id,
        title = title,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )
}
