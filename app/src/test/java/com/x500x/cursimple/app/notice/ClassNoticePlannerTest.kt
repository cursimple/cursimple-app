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

    // 2026-09-07 是周一，第 1 教学周周一
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
    ) = ClassNoticePlanner.nextClass(
        now = now,
        allCourses = courses,
        timingProfile = profile,
        termStartDate = termStart,
        overrides = overrides,
        holidayCalendar = holidayCalendar,
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

        // 放假当天不出课，顺延到下周一
        assertEquals(
            LocalDateTime.of(2026, 9, 14, 8, 0),
            nextClass(LocalDateTime.of(2026, 9, 7, 6, 0), courses, holidayCalendar = holiday)?.startAt,
        )

        // 把周三的课挪到这个放假日，就该在这天提示
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
}
