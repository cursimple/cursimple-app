package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetScheduleDayTest {
    private val termStart = LocalDate.of(2026, 9, 7)

    /** 2026-10-01 是内置法定假日，2026-09-28 是普通的星期一。 */
    private val nationalDay = LocalDate.of(2026, 10, 1)
    private val monday = LocalDate.of(2026, 9, 28)

    @Test
    fun `a built-in holiday clears the courses and names the day`() {
        val day = resolveDay(nationalDay, holidayCalendar = HolidayCalendarSettings())

        assertEquals(emptyList<CourseItem>(), day.courses)
        assertEquals("国庆节", day.holidayLabel)
        assertEquals(nationalDay, day.sourceDate)
    }

    @Test
    fun `an ordinary day still lists its courses`() {
        val day = resolveDay(monday, holidayCalendar = HolidayCalendarSettings())

        assertEquals(listOf("周1-1", "周1-2"), day.courses.map { it.id })
        assertNull(day.holidayLabel)
    }

    @Test
    fun `switching the built-in calendar off keeps the holiday courses`() {
        val day = resolveDay(nationalDay, holidayCalendar = HolidayCalendarSettings.NONE)

        assertTrue(day.courses.isNotEmpty())
        assertNull(day.holidayLabel)
    }

    @Test
    fun `a user workday entry overrides the built-in holiday`() {
        val day = resolveDay(
            nationalDay,
            holidayCalendar = HolidayCalendarSettings(
                entries = listOf(
                    HolidayCalendarEntry(nationalDay.toString(), HolidayEntryKind.Workday, "调休上课"),
                ),
            ),
        )

        assertTrue(day.courses.isNotEmpty())
        assertNull(day.holidayLabel)
    }

    @Test
    fun `a make-up override on a built-in holiday brings the source day courses back`() {
        val day = resolveDay(
            nationalDay,
            holidayCalendar = HolidayCalendarSettings(),
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    type = TemporaryScheduleOverrideType.MakeUp,
                    targetDate = nationalDay.toString(),
                    sourceDate = monday.toString(),
                ),
            ),
        )

        assertEquals(monday, day.sourceDate)
        assertEquals(listOf("周1-1", "周1-2"), day.courses.map { it.id })
        assertNull(day.holidayLabel)
    }

    @Test
    fun `a user holiday entry wins over a make-up override`() {
        val day = resolveDay(
            nationalDay,
            holidayCalendar = HolidayCalendarSettings(
                entries = listOf(
                    HolidayCalendarEntry(nationalDay.toString(), HolidayEntryKind.Holiday, "校庆"),
                ),
            ),
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    type = TemporaryScheduleOverrideType.MakeUp,
                    targetDate = nationalDay.toString(),
                    sourceDate = monday.toString(),
                ),
            ),
        )

        assertEquals(emptyList<CourseItem>(), day.courses)
        assertEquals("校庆", day.holidayLabel)
        assertEquals(nationalDay, day.sourceDate)
    }

    @Test
    fun `an unnamed holiday falls back to a generic label`() {
        val day = resolveDay(
            monday,
            holidayCalendar = HolidayCalendarSettings(
                entries = listOf(HolidayCalendarEntry(monday.toString(), HolidayEntryKind.Holiday, "")),
            ),
        )

        assertEquals("放假", day.holidayLabel)
    }

    private fun resolveDay(
        targetDate: LocalDate,
        holidayCalendar: HolidayCalendarSettings,
        overrides: List<TemporaryScheduleOverride> = emptyList(),
    ): WidgetScheduleDay = resolveWidgetScheduleDay(
        targetDate = targetDate,
        termStart = termStart,
        temporaryScheduleOverrides = overrides,
        holidayCalendar = holidayCalendar,
        coursesOfDayOfWeek = { dayOfWeek ->
            listOf(
                course("周$dayOfWeek-1", dayOfWeek, startNode = 1, endNode = 2),
                course("周$dayOfWeek-2", dayOfWeek, startNode = 3, endNode = 4),
            )
        },
    )

    private fun course(id: String, dayOfWeek: Int, startNode: Int, endNode: Int): CourseItem =
        CourseItem(
            id = id,
            title = id,
            time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
        )
}
