package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalendarWidgetModelTest {
    private fun course(id: String, start: Int, end: Int, day: Int = 1, category: CourseCategory = CourseCategory.Course) =
        CourseItem(id = id, title = id, time = CourseTimeSlot(dayOfWeek = day, startNode = start, endNode = end), category = category)

    private fun day(date: LocalDate, vararg courses: CourseItem, events: Int = 0, holiday: Boolean = false, makeUp: Boolean = false) =
        CalendarDay(date, isToday = false, onHoliday = holiday, makeUpWorkday = makeUp, courses = courses.toList(), eventCount = events)

    private val monday = LocalDate.of(2026, 10, 5)
    private fun week(vararg perDay: List<CourseItem>) = (0 until 7).map { i -> day(monday.plusDays(i.toLong()), *perDay.getOrElse(i) { emptyList() }.toTypedArray()) }

    @Test fun `month cell anchors stack tag above date above dots and stay inside the cell`() {
        assertTrue(CalendarWidgetRenderer.MONTH_TAG_CENTER in 0f..1f)
        assertTrue(CalendarWidgetRenderer.MONTH_TAG_CENTER < CalendarWidgetRenderer.MONTH_DATE_CENTER)
        assertTrue(CalendarWidgetRenderer.MONTH_DATE_CENTER < CalendarWidgetRenderer.MONTH_DOT_CENTER)
        assertTrue(CalendarWidgetRenderer.MONTH_DOT_CENTER < 1f)
    }

    @Test fun `month grid starts on monday and covers whole weeks`() {
        val october = monthGridDates(YearMonth.of(2026, 10))
        assertEquals(DayOfWeek.MONDAY, october.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, october.last().dayOfWeek)
        assertEquals(0, october.size % 7)
        assertTrue(october.contains(LocalDate.of(2026, 10, 1)))
        assertTrue(october.contains(LocalDate.of(2026, 10, 31)))
        val february = monthGridDates(YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 1, 26), february.first())
        assertTrue(february.size / 7 in 5..6)
    }

    @Test fun `weekend is hidden only when the setting is off and the weekend is empty`() {
        val days = week()
        assertEquals(7, visibleWeekDays(days, weekendVisible = true).size)
        assertEquals(5, visibleWeekDays(days, weekendVisible = false).size)
        val busySaturday = days.toMutableList().also { it[5] = day(it[5].date, course("补课", 1, 2, day = 6)) }
        assertEquals(7, visibleWeekDays(busySaturday, weekendVisible = false).size)
        val eventSunday = days.toMutableList().also { it[6] = day(it[6].date, events = 1) }
        assertEquals(7, visibleWeekDays(eventSunday, weekendVisible = false).size)
        val makeUpSaturday = days.toMutableList().also { it[5] = day(it[5].date, makeUp = true) }
        assertEquals(7, visibleWeekDays(makeUpSaturday, weekendVisible = false).size)
    }

    private fun slot(start: Int, end: Int, time: String, label: String = "") = ClassSlotTime(start, end, time, time, label = label)
    private val labelOf: (ClassSlotTime, Int) -> String = { slot, index -> slot.label.ifBlank { "第${index}节" } }
    private val fallback: (Int) -> String = { node -> "第${node}节" }

    @Test fun `current row uses slot start inclusive and end exclusive with unhighlighted breaks`() {
        val rows = listOf(
            CalendarRow(1, 2, "第一节", "08:00", "09:40"),
            CalendarRow(3, 4, "第二节", "10:05", "11:45"),
            CalendarRow(5, 6, "夜课", "20:45", "22:20"),
        )
        val days = week().mapIndexed { index, d -> d.copy(isToday = index == 1) }
        fun at(time: String) = calendarCurrentRows(rows, days, LocalTime.parse(time))
        assertTrue(at("07:59").isEmpty())
        assertEquals(setOf(0), at("08:00"))
        assertEquals(setOf(0), at("09:39"))
        assertTrue(at("09:40").isEmpty())
        assertTrue(at("10:04").isEmpty())
        assertEquals(setOf(1), at("10:05"))
        assertEquals(setOf(2), at("21:30"))
        assertTrue(at("22:20").isEmpty())
    }

    @Test fun `other weeks holidays and incomplete times never get a current row`() {
        val rows = listOf(CalendarRow(1, 2, "第一节", "08:00", "09:40"))
        assertTrue(calendarCurrentRows(rows, week(), LocalTime.of(8, 30)).isEmpty())
        val days = week().mapIndexed { i, d -> d.copy(isToday = i == 0, onHoliday = i == 0) }
        assertTrue(calendarCurrentRows(rows, days, LocalTime.of(8, 30)).isEmpty())
        val invalid = listOf(CalendarRow(1, 1, "缺少结束时间", "08:00"), CalendarRow(2, 2, "无效", "abc", "09:40"),
            CalendarRow(3, 3, "相等", "08:00", "08:00"), CalendarRow(4, 4, "倒序", "10:00", "09:00"))
        assertTrue(calendarCurrentRows(invalid, days.map { it.copy(onHoliday = false) }, LocalTime.of(8, 30)).isEmpty())
    }

    @Test fun `current row follows sorted merged timetable slots rather than node or fixed hours`() {
        val days = week().mapIndexed { i, d -> d.copy(isToday = i == 0) }
        val slots = listOf(ClassSlotTime(3, 4, "10:05", "11:45"), ClassSlotTime(1, 2, "8:10", "09:50"))
        val rows = calendarRows(slots, days, labelOf, fallback)
        assertEquals("09:50", rows[0].endTime)
        assertEquals(setOf(0), calendarCurrentRows(rows, days, LocalTime.of(9, 45)))
        assertTrue(calendarCurrentRows(rows, days, LocalTime.of(10, 0)).isEmpty())
        assertEquals(setOf(1), calendarCurrentRows(rows, days, LocalTime.of(10, 10)))
    }

    @Test fun `rows follow the timetable names and merge multi node slots`() {
        val slots = listOf(
            slot(1, 2, "08:00", "第一大节"),
            slot(3, 4, "10:00", "第二大节"),
            slot(5, 5, "12:10", "午间课"),
            slot(6, 7, "14:00"),
        )
        val rows = calendarRows(slots, week(listOf(course("a", 6, 7))), labelOf, fallback)
        assertEquals(listOf("第一大节", "第二大节", "午间课", "第4节"), rows.map { it.label })
        assertEquals(listOf(1 to 2, 3 to 4, 5 to 5, 6 to 7), rows.map { it.startNode to it.endNode })
        assertEquals("08:00", rows.first().startTime)
    }

    @Test fun `slots are ordered by clock time, not by text`() {
        val rows = calendarRows(listOf(slot(3, 3, "10:00"), slot(1, 1, "8:00"), slot(2, 2, "9:00")), week(), labelOf, fallback)
        assertEquals(listOf(1, 2, 3), rows.map { it.startNode })
    }

    @Test fun `classes beyond the timetable get extra rows instead of disappearing`() {
        val rows = calendarRows(listOf(slot(1, 1, "08:00"), slot(2, 2, "09:00")), week(listOf(course("late", 5, 6))), labelOf, fallback)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows.map { it.startNode })
        assertEquals("第5节", rows[4].label)
        assertEquals("", rows[4].startTime)
    }

    @Test fun `long timetables are trimmed after the last class but never below it`() {
        val thirteen = (1..13).map { slot(it, it, "%02d:00".format(it + 6)) }
        assertEquals(10, calendarRows(thirteen, week(listOf(course("a", 1, 2))), labelOf, fallback).size)
        assertEquals(12, calendarRows(thirteen, week(listOf(course("night", 11, 12))), labelOf, fallback).size)
        assertEquals(4, calendarRows(thirteen.take(4), week(), labelOf, fallback).size)
        assertEquals(10, calendarRows(emptyList(), week(), labelOf, fallback).size)
    }

    @Test fun `blocks span the rows that contain their nodes`() {
        val slots = listOf(slot(1, 2, "08:00"), slot(3, 4, "10:00"), slot(5, 5, "12:00"), slot(7, 8, "14:00"))
        val days = week(listOf(course("a", 1, 2), course("b", 2, 3), course("gap", 6, 6), course("c", 4, 7)))
        val data = CalendarWeekData(days, days.map(::calendarBlocksOf), calendarRows(slots, days, labelOf, fallback))
        val blocks = data.blocks.first().associateBy { it.id }
        assertEquals(0..0, data.rowSpanOf(blocks.getValue("a")))
        assertEquals(0..1, data.rowSpanOf(blocks.getValue("b")))
        assertEquals(1..3, data.rowSpanOf(blocks.getValue("c")))
        assertEquals(null, data.rowSpanOf(blocks.getValue("gap")))
    }

    @Test fun `overlapping classes share the column while the rest of the day stays full width`() {
        val blocks = calendarBlocksOf(
            day(monday, course("A", 1, 2), course("B", 2, 3), course("C", 5, 6)),
        ).associateBy { it.id }
        assertEquals(2, blocks.getValue("A").laneCount)
        assertEquals(2, blocks.getValue("B").laneCount)
        assertEquals(setOf(0, 1), setOf(blocks.getValue("A").lane, blocks.getValue("B").lane))
        assertEquals(1, blocks.getValue("C").laneCount)
        assertEquals(0, blocks.getValue("C").lane)
    }

    @Test fun `holiday classes are drawn inactive and do not count as class days`() {
        val holiday = day(monday, course("A", 1, 2), course("期末", 3, 4, category = CourseCategory.Exam), holiday = true)
        assertTrue(calendarBlocksOf(holiday).all { it.inactive })
        assertEquals(0, holiday.classCount)
        assertFalse(holiday.hasExam)
        val normal = day(monday, course("A", 1, 2), course("期末", 3, 4, category = CourseCategory.Exam))
        assertEquals(1, normal.classCount)
        assertTrue(normal.hasExam)
        assertTrue(calendarBlocksOf(normal).single { it.id == "期末" }.isExam)
    }

    @Test fun `malformed node ranges are skipped instead of drawn upside down`() {
        val blocks = calendarBlocksOf(day(monday, course("bad", 4, 2), course("zero", 0, 1), course("ok", 1, 1)))
        assertEquals(listOf("ok"), blocks.map { it.id })
    }

    @Test fun `same title always picks the same palette entry as the timetable`() {
        assertEquals(CalendarWidgetRenderer.coursePalette("高等数学"), CalendarWidgetRenderer.coursePalette("高等数学"))
    }

    @Test fun `legend lists only the marks that appear this week`() {
        val plain = week(listOf(course("a", 1, 2)))
        val plainData = CalendarWeekData(plain, plain.map(::calendarBlocksOf), calendarRows(emptyList(), plain, labelOf, fallback))
        assertTrue(plainData.legend().isEmpty)

        val busy = week(listOf(course("期中", 1, 2, category = CourseCategory.Exam))).toMutableList()
        busy[2] = day(busy[2].date, events = 2)
        busy[5] = day(busy[5].date, makeUp = true)
        val legend = CalendarWeekData(busy, busy.map(::calendarBlocksOf), calendarRows(emptyList(), busy, labelOf, fallback)).legend()
        assertEquals(CalendarWeekLegend(events = true, exam = true, holiday = false, makeUp = true), legend)

        val holiday = week().toMutableList().also { it[0] = day(it[0].date, course("期中", 1, 2, category = CourseCategory.Exam), holiday = true) }
        val holidayLegend = CalendarWeekData(holiday, holiday.map(::calendarBlocksOf), calendarRows(emptyList(), holiday, labelOf, fallback)).legend()
        assertFalse(holidayLegend.exam)
        assertTrue(holidayLegend.holiday)
    }
}
