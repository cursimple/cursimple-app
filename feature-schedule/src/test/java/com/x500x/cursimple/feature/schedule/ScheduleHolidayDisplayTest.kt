package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.time.BeijingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 节假日在课表显示上的表现。
 * 用例统一落在 2026-05-04 这一周：周一 5 月 4 日是内置的劳动节补假，周三 5 月 6 日不是内置假日。
 */
class ScheduleHolidayDisplayTest {
    private val weekStart = LocalDate.of(2026, 5, 4)
    private val mondayDate = LocalDate.of(2026, 5, 4)
    private val wednesdayDate = LocalDate.of(2026, 5, 6)
    private val termStart = LocalDate.of(2026, 4, 13)

    @Test
    fun `built-in holiday hides that day's courses`() {
        val entries = renderEntries(holidayCalendar = HolidayCalendarSettings())

        assertFalse(entries.any { it.placement.dayIndex == 0 })
        assertTrue(entries.any { it.course.id == "wednesday" && it.placement.dayIndex == 2 })
    }

    @Test
    fun `courses still render when the holiday calendar is off`() {
        val entries = renderEntries(holidayCalendar = HolidayCalendarSettings.NONE)

        assertTrue(entries.any { it.course.id == "monday" && it.placement.dayIndex == 0 })
    }

    @Test
    fun `a user workday entry overrides the built-in holiday and keeps courses`() {
        val entries = renderEntries(
            holidayCalendar = HolidayCalendarSettings(
                entries = listOf(
                    HolidayCalendarEntry(mondayDate.toString(), HolidayEntryKind.Workday, "劳动节调休"),
                ),
            ),
        )

        assertTrue(entries.any { it.course.id == "monday" && it.placement.dayIndex == 0 })
    }

    @Test
    fun `a user holiday entry hides courses on a normal working day`() {
        val entries = renderEntries(
            holidayCalendar = HolidayCalendarSettings(
                builtInEnabled = false,
                entries = listOf(
                    HolidayCalendarEntry(wednesdayDate.toString(), HolidayEntryKind.Holiday, "校庆"),
                ),
            ),
        )

        assertTrue(entries.any { it.course.id == "monday" && it.placement.dayIndex == 0 })
        assertFalse(entries.any { it.placement.dayIndex == 2 })
    }

    @Test
    fun `total schedule display still hides courses on a holiday`() {
        val entries = renderEntries(
            holidayCalendar = HolidayCalendarSettings(),
            totalScheduleDisplayEnabled = true,
        )

        assertFalse(entries.any { it.placement.dayIndex == 0 })
        assertTrue(entries.any { it.course.id == "wednesday" && it.placement.dayIndex == 2 })
    }

    @Test
    fun `a temporary make-up on a built-in holiday brings the source day's courses back`() {
        val entries = renderEntries(
            holidayCalendar = HolidayCalendarSettings(),
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    targetDate = mondayDate.toString(),
                    sourceDate = wednesdayDate.toString(),
                ),
            ),
        )

        assertTrue(entries.any { it.course.id == "wednesday" && it.placement.dayIndex == 0 })
    }

    @Test
    fun `a user holiday entry wins over a temporary make-up on the same day`() {
        val entries = renderEntries(
            holidayCalendar = HolidayCalendarSettings(
                builtInEnabled = false,
                entries = listOf(
                    HolidayCalendarEntry(wednesdayDate.toString(), HolidayEntryKind.Holiday, "校庆"),
                ),
            ),
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    targetDate = wednesdayDate.toString(),
                    sourceDate = mondayDate.toString(),
                ),
            ),
        )

        assertFalse(entries.any { it.placement.dayIndex == 2 })
    }

    @Test
    fun `week header shows the holiday name instead of the make-up label`() {
        val monday = BeijingTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val wednesday = monday.plusDays(2)

        val week = buildWeekModel(
            weekOffset = 0,
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    targetDate = wednesday.toString(),
                    sourceDate = monday.toString(),
                ),
            ),
            holidayCalendar = HolidayCalendarSettings(
                builtInEnabled = false,
                entries = listOf(
                    HolidayCalendarEntry(wednesday.toString(), HolidayEntryKind.Holiday, "校庆"),
                ),
            ),
        )

        assertEquals(HolidayLabel.Named("校庆"), week.days[2].holidayLabel)
        assertNull(week.days[2].overrideLabel)
        assertNull(week.days[0].holidayLabel)
    }

    @Test
    fun `week header keeps the make-up label on a non-holiday`() {
        val monday = BeijingTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val wednesday = monday.plusDays(2)

        val week = buildWeekModel(
            weekOffset = 0,
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    targetDate = wednesday.toString(),
                    sourceDate = monday.toString(),
                ),
            ),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertNull(week.days[2].holidayLabel)
        assertEquals("按${monday.monthValue}/${monday.dayOfMonth}周一", week.days[2].overrideLabel)
    }

    @Test
    fun `holiday label falls back when the entry has no name`() {
        assertEquals(HolidayLabel.Named("国庆节"), holidayDisplayLabel("国庆节"))
        assertEquals(HolidayLabel.Unnamed, holidayDisplayLabel(null))
        assertEquals(HolidayLabel.Unnamed, holidayDisplayLabel("  "))
    }

    @Test
    fun `detail week number on a holiday follows the day itself`() {
        val holiday = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(wednesdayDate.toString(), HolidayEntryKind.Holiday, "校庆"),
            ),
        )
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                targetDate = wednesdayDate.toString(),
                sourceDate = LocalDate.of(2026, 4, 29).toString(),
            ),
        )

        assertEquals(3, detailWeekNumber(wednesdayDate, termStart, overrides, HolidayCalendarSettings.NONE))
        assertEquals(4, detailWeekNumber(wednesdayDate, termStart, overrides, holiday))
    }

    @Test
    fun `empty state explains the holiday before anything else`() {
        val state = scheduleEmptyState(
            hasSchedule = false,
            notStarted = true,
            termStartDate = termStart,
            holidayLabel = HolidayLabel.Named("国庆节"),
        )

        assertEquals(ScheduleEmptyState.Holiday(HolidayLabel.Named("国庆节")), state)
    }

    @Test
    fun `empty state prefers not-started over a missing schedule`() {
        val state = scheduleEmptyState(
            hasSchedule = false,
            notStarted = true,
            termStartDate = termStart,
        )

        assertEquals(ScheduleEmptyState.NotStarted(termStartMonth = 4, termStartDay = 13), state)
    }

    @Test
    fun `empty state before the term keeps quiet about the date it does not have`() {
        val state = scheduleEmptyState(
            hasSchedule = false,
            notStarted = true,
            termStartDate = null,
        )

        assertEquals(ScheduleEmptyState.NotStartedWithoutDate, state)
    }

    @Test
    fun `empty state falls back to the sync and empty-week hints`() {
        assertEquals(ScheduleEmptyState.NoSchedule, scheduleEmptyState(hasSchedule = false))
        assertEquals(ScheduleEmptyState.EmptyWeek, scheduleEmptyState(hasSchedule = true))
    }

    private fun renderEntries(
        holidayCalendar: HolidayCalendarSettings,
        overrides: List<TemporaryScheduleOverride> = emptyList(),
        totalScheduleDisplayEnabled: Boolean = false,
    ): List<CourseRenderEntry> = buildWeekRenderEntries(
        allCourses = listOf(
            course(id = "monday", dayOfWeek = 1),
            course(id = "wednesday", dayOfWeek = 3),
        ),
        slots = listOf(
            DisplaySlot(startNode = 1, endNode = 2, label = "第一节", startTime = "08:00", endTime = "09:35"),
        ),
        weekIndex = 4,
        totalScheduleDisplayEnabled = totalScheduleDisplayEnabled,
        weekStart = weekStart,
        termStart = termStart,
        temporaryScheduleOverrides = overrides,
        holidayCalendar = holidayCalendar,
    )

    private fun course(id: String, dayOfWeek: Int): CourseItem = CourseItem(
        id = id,
        title = id,
        weeks = listOf(4),
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = 1, endNode = 2),
    )
}
