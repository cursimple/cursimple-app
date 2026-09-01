package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermWeekLabel
import com.x500x.cursimple.core.kernel.model.termWeekLabel
import com.x500x.cursimple.core.kernel.time.BeijingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
    fun `no course is active before the term starts`() {
        val course = CourseItem(
            id = "short-course",
            title = "短期课程",
            weeks = listOf(1, 2, 3),
            time = CourseTimeSlot(dayOfWeek = 2, startNode = 1, endNode = 2),
        )
        val allWeeksCourse = course.copy(id = "all-weeks", weeks = emptyList())

        assertFalse(course.isActiveInWeek(0))
        assertFalse(course.isActiveInWeek(-3))
        assertFalse(allWeeksCourse.isActiveInWeek(0))
        assertFalse(allWeeksCourse.isActiveInWeek(-3))
    }

    @Test
    fun `week render source is empty before the term starts`() {
        val normal = course(id = "normal", weeks = listOf(1))
        val allWeeks = course(id = "all-weeks", weeks = emptyList())

        assertTrue(activeCoursesForWeek(listOf(normal, allWeeks), weekNumber = 0).isEmpty())
        assertTrue(activeCoursesForWeek(listOf(normal, allWeeks), weekNumber = -1).isEmpty())
    }

    @Test
    fun `week render entries are empty before the term starts`() {
        val normal = course(id = "normal", weeks = listOf(1))
        val allWeeks = course(id = "all-weeks", weeks = emptyList())

        val entries = buildWeekRenderEntries(
            allCourses = listOf(normal, allWeeks),
            slots = listOf(testSlot()),
            weekIndex = 0,
            totalScheduleDisplayEnabled = false,
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `total schedule display still shows every course before the term starts`() {
        val normal = course(id = "normal", weeks = listOf(1))
        val allWeeks = course(id = "all-weeks", weeks = emptyList())

        val entries = buildWeekRenderEntries(
            allCourses = listOf(normal, allWeeks),
            slots = listOf(testSlot()),
            weekIndex = 0,
            totalScheduleDisplayEnabled = true,
        )

        assertEquals(setOf("normal", "all-weeks"), entries.map { it.course.id }.toSet())
        assertTrue(entries.all { it.inactive })
    }

    @Test
    fun `week number before term start is not clamped to the first week`() {
        val termStart = LocalDate.of(2026, 9, 7)

        assertEquals(0, computeWeekNumberForDate(termStart, LocalDate.of(2026, 9, 1)))
        assertEquals(-1, computeWeekNumberForDate(termStart, LocalDate.of(2026, 8, 25)))
        assertEquals(1, computeWeekNumberForDate(termStart, LocalDate.of(2026, 9, 7)))
        assertEquals(2, computeWeekNumberForDate(termStart, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun `without a term start date there is no week number to report`() {
        assertNull(computeWeekNumberForDate(null, LocalDate.of(2026, 9, 1)))
        assertNull(computeWeekNumberForDate(null, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `an unknown week number shows every course and marks none as out of week`() {
        val courses = listOf(
            course(id = "week-one", weeks = listOf(1)),
            course(id = "week-ten", weeks = listOf(10)),
        )
        val slots = listOf(testSlot())

        val entries = buildWeekRenderEntries(
            allCourses = courses,
            slots = slots,
            weekIndex = 1,
            weekNumberKnown = false,
        )

        assertEquals(setOf("week-one", "week-ten"), entries.map { it.course.id }.toSet())
        assertTrue(entries.none { it.inactive })
    }

    @Test
    fun `a known week number keeps filtering courses by week`() {
        val courses = listOf(
            course(id = "week-one", weeks = listOf(1)),
            course(id = "week-ten", weeks = listOf(10)),
        )
        val slots = listOf(testSlot())

        val entries = buildWeekRenderEntries(
            allCourses = courses,
            slots = slots,
            weekIndex = 1,
            weekNumberKnown = true,
        )

        assertEquals(listOf("week-one"), entries.map { it.course.id })
    }

    @Test
    fun `week model keeps the real week index around the term start`() {
        val thisMonday = BeijingTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        assertEquals(0, buildWeekModel(weekOffset = 0, termStart = thisMonday.plusWeeks(1)).weekIndex)
        assertEquals(-2, buildWeekModel(weekOffset = 0, termStart = thisMonday.plusWeeks(3)).weekIndex)
        assertEquals(1, buildWeekModel(weekOffset = 0, termStart = thisMonday).weekIndex)
        assertEquals(1, buildWeekModel(weekOffset = 0, termStart = null).weekIndex)
    }

    @Test
    fun `week index label hides meaningless week numbers before the term starts`() {
        assertEquals(TermWeekLabel.Week(1), termWeekLabel(1))
        assertEquals(TermWeekLabel.Week(12), termWeekLabel(12))
        assertEquals(TermWeekLabel.NotStarted, termWeekLabel(0))
        assertEquals(TermWeekLabel.NotStarted, termWeekLabel(-2))
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
