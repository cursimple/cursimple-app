package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ClassNoticePlannerTest {

    @Test fun `overlapping courses must not schedule departure after the next class starts`() {
        val first = course("长课", 1, 1, 2, "一教101", listOf(1))
        val second = course("重叠课", 1, 1, 2, "西区实验楼", listOf(1))
            .copy(reminderStartTime = "09:00", reminderEndTime = "10:00")
        assertNull(ClassNoticePlanner.nextTransit(
            LocalDateTime.of(2026, 9, 7, 8, 0), listOf(first, second), profile, termStart,
            emptyList(), HolidayCalendarSettings.NONE,
        ))
    }
    @Test
    fun `moved course retains location for the source teaching week`() {
        val moved = course("实验课", 1, 1, 2, weeks = listOf(1)).copy(weekLocations = mapOf(1 to "原周实验室", 2 to "目标周实验室"))
        val move = TemporaryScheduleOverride(
            id = "move", type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = "2026-09-07", targetDate = "2026-09-14", moveCourseId = "实验课",
            moveToStartNode = 3, moveToEndNode = 4,
        )
        val result = nextClass(LocalDateTime.of(2026, 9, 14, 7, 0), listOf(moved), overrides = listOf(move))
        assertEquals("原周实验室", result?.displayLocation())
        assertEquals(1, result?.weekNumber)
    }

    private val termStart = LocalDate.of(2026, 9, 7)

    private val profile = TermTimingProfile(
        termStartDate = termStart.toString(),
        slotTimes = listOf(
            ClassSlotTime(1, 2, "08:00", "09:35"),
            ClassSlotTime(3, 4, "10:05", "11:40"),
            ClassSlotTime(5, 6, "14:00", "15:35"),
        ),
    )

    private fun course(
        id: String,
        dayOfWeek: Int,
        startNode: Int,
        endNode: Int,
        location: String = "东13-A-101",
        weeks: List<Int> = emptyList(),
    ) = CourseItem(
        id = id,
        title = id,
        location = location,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun nextClass(
        now: LocalDateTime,
        courses: List<CourseItem>,
        overrides: List<TemporaryScheduleOverride> = emptyList(),
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        advanceMinutes: Int = 0,
    ) = ClassNoticePlanner.nextClass(
        now = now,
        allCourses = courses,
        timingProfile = profile,
        termStartDate = termStart,
        overrides = overrides,
        holidayCalendar = holidayCalendar,
        advanceMinutes = advanceMinutes,
    )

    @Test
    fun `取当天还没上的最近一节`() {
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 7, 0),
            courses = listOf(
                course("下午课", dayOfWeek = 1, startNode = 5, endNode = 6),
                course("上午课", dayOfWeek = 1, startNode = 1, endNode = 2),
            ),
        )

        assertEquals("上午课", result?.course?.title)
        assertEquals(LocalDateTime.of(2026, 9, 7, 8, 0), result?.startAt)
        assertEquals(LocalDateTime.of(2026, 9, 7, 9, 35), result?.endAt)
    }

    @Test
    fun `已经开始的课不再提示，顺延到下一节`() {
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 8, 30),
            courses = listOf(
                course("上午课", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("下午课", dayOfWeek = 1, startNode = 5, endNode = 6),
            ),
        )

        assertEquals("下午课", result?.course?.title)
    }

    @Test
    fun `今天没课就往后找，跨天也算`() {
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 20, 0),
            courses = listOf(course("周三课", dayOfWeek = 3, startNode = 1, endNode = 2)),
        )

        assertEquals("周三课", result?.course?.title)
        assertEquals(LocalDateTime.of(2026, 9, 9, 8, 0), result?.startAt)
    }

    @Test
    fun `放假日跳过，但调过去的课照常提示`() {
        val holiday = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(date = "2026-09-07", kind = HolidayEntryKind.Holiday, name = "校庆"),
            ),
        )
        val courses = listOf(course("周一课", dayOfWeek = 1, startNode = 1, endNode = 2))

        assertEquals(
            LocalDateTime.of(2026, 9, 14, 8, 0),
            nextClass(LocalDateTime.of(2026, 9, 7, 6, 0), courses, holidayCalendar = holiday)?.startAt,
        )

        val moved = TemporaryScheduleOverride(
            id = "mv",
            type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = "2026-09-09",
            targetDate = "2026-09-07",
            moveCourseId = "周三课",
            moveToStartNode = 5,
            moveToEndNode = 6,
        )
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 6, 0),
            courses = courses + course("周三课", dayOfWeek = 3, startNode = 1, endNode = 2),
            overrides = listOf(moved),
            holidayCalendar = holiday,
        )
        assertEquals("周三课", result?.course?.title)
        assertEquals(LocalDateTime.of(2026, 9, 7, 14, 0), result?.startAt)
    }

    @Test
    fun `临时停课的那节不提示`() {
        val cancel = TemporaryScheduleOverride(
            id = "c",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-09-07",
            cancelStartNode = 1,
            cancelEndNode = 2,
        )
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 6, 0),
            courses = listOf(
                course("上午课", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("下午课", dayOfWeek = 1, startNode = 5, endNode = 6),
            ),
            overrides = listOf(cancel),
        )

        assertEquals("下午课", result?.course?.title)
    }

    @Test
    fun `没有作息或没有课时给不出下一节`() {
        assertNull(
            ClassNoticePlanner.nextClass(
                now = LocalDateTime.of(2026, 9, 7, 6, 0),
                allCourses = listOf(course("课", dayOfWeek = 1, startNode = 1, endNode = 2)),
                timingProfile = null,
                termStartDate = termStart,
                overrides = emptyList(),
                holidayCalendar = HolidayCalendarSettings.NONE,
            ),
        )
        assertNull(nextClass(LocalDateTime.of(2026, 9, 7, 6, 0), emptyList()))
    }

    @Test
    fun `该周单独设置的地点会带进通知`() {
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 6, 0),
            courses = listOf(
                course("物理实验", dayOfWeek = 1, startNode = 1, endNode = 2)
                    .copy(weekLocations = mapOf(1 to "实验楼B203")),
            ),
        )

        assertEquals("实验楼B203", result?.displayLocation())
    }

    @Test
    fun `刚发完这节的提醒，下一节要排到后面那节课`() {
        val courses = listOf(
            course("上午课", dayOfWeek = 1, startNode = 1, endNode = 2),
            course("下午课", dayOfWeek = 1, startNode = 5, endNode = 6),
        )
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 7, 50),
            courses = courses,
            advanceMinutes = 10,
        )

        assertEquals("下午课", result?.course?.title)
    }

    @Test
    fun `提醒点还没到的课照常排上`() {
        val result = nextClass(
            now = LocalDateTime.of(2026, 9, 7, 7, 49),
            courses = listOf(course("上午课", dayOfWeek = 1, startNode = 1, endNode = 2)),
            advanceMinutes = 10,
        )

        assertEquals("上午课", result?.course?.title)
    }

    @Test
    fun `classesOnDate returns sorted classes on specified date`() {
        val list = ClassNoticePlanner.classesOnDate(
            date = LocalDate.of(2026, 9, 7),
            allCourses = listOf(
                course("课2", dayOfWeek = 1, startNode = 3, endNode = 4, weeks = listOf(1)),
                course("课1", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1)),
            ),
            timingProfile = profile,
            termStartDate = termStart,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )
        assertEquals(2, list.size)
        assertEquals("课1", list[0].course.title)
        assertEquals("课2", list[1].course.title)
    }

    @Test
    fun `nextTransit triggers 5 minutes before end of preceding course with different location`() {
        val transit = ClassNoticePlanner.nextTransit(
            now = LocalDateTime.of(2026, 9, 7, 9, 0),
            allCourses = listOf(
                course("课1", dayOfWeek = 1, startNode = 1, endNode = 2, location = "一教101", weeks = listOf(1)),
                course("课2", dayOfWeek = 1, startNode = 3, endNode = 4, location = "西区实验楼", weeks = listOf(1)),
            ),
            timingProfile = profile,
            termStartDate = termStart,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )
        // 课1 ends at 09:35, so transit alert triggers at 09:30
        assertEquals(LocalDateTime.of(2026, 9, 7, 9, 30), transit?.triggerAt)
        assertEquals("课2", transit?.nextTitle)
        assertEquals("西区实验楼", transit?.nextLocation)
    }

    @Test
    fun `nextTransit ignores courses with identical locations`() {
        val transit = ClassNoticePlanner.nextTransit(
            now = LocalDateTime.of(2026, 9, 7, 9, 0),
            allCourses = listOf(
                course("课1", dayOfWeek = 1, startNode = 1, endNode = 2, location = "一教101", weeks = listOf(1)),
                course("课2", dayOfWeek = 1, startNode = 3, endNode = 4, location = "一教101", weeks = listOf(1)),
            ),
            timingProfile = profile,
            termStartDate = termStart,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )
        assertNull(transit)
    }

    @Test
    fun `nextExam finds upcoming exam course with advance alert time`() {
        val examCourse = course("期末考", dayOfWeek = 1, startNode = 3, endNode = 4, weeks = listOf(1))
            .copy(category = com.x500x.cursimple.core.kernel.model.CourseCategory.Exam)
        val regularCourse = course("普通课", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1))

        // 课3 starts at 10:05. With advanceMinutes = 45, trigger is 09:20.
        // At 09:10, exam alert is upcoming.
        val upcoming = ClassNoticePlanner.nextExam(
            now = LocalDateTime.of(2026, 9, 7, 9, 10),
            allCourses = listOf(regularCourse, examCourse),
            timingProfile = profile,
            termStartDate = termStart,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
            advanceMinutes = 45,
        )
        assertEquals("期末考", upcoming?.course?.title)
    }
}
