package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetWeekUtilTest {
    private val termStart: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `week index stays below one before the term starts`() {
        assertEquals(0, resolveWeekIndex(LocalDate.of(2026, 9, 1), termStart))
        assertEquals(0, resolveWeekIndex(LocalDate.of(2026, 9, 6), termStart))
        assertEquals(-1, resolveWeekIndex(LocalDate.of(2026, 8, 25), termStart))
        assertEquals(-3, resolveWeekIndex(LocalDate.of(2026, 8, 10), termStart))
    }

    @Test
    fun `week index counts from one once the term starts`() {
        assertEquals(1, resolveWeekIndex(termStart, termStart))
        assertEquals(1, resolveWeekIndex(LocalDate.of(2026, 9, 13), termStart))
        assertEquals(2, resolveWeekIndex(LocalDate.of(2026, 9, 14), termStart))
        assertEquals(5, resolveWeekIndex(LocalDate.of(2026, 10, 8), termStart))
    }

    @Test
    fun `week index is unknown without a term start date`() {
        assertNull(resolveWeekIndex(LocalDate.of(2026, 9, 1), null))
    }

    @Test
    fun `before term start only covers known week indexes below one`() {
        assertTrue(isBeforeTermStart(0))
        assertTrue(isBeforeTermStart(-2))
        assertFalse(isBeforeTermStart(1))
        assertFalse(isBeforeTermStart(null))
    }

    @Test
    fun `no course is active before the term starts`() {
        val weeklyCourse = course("weekly", weeks = emptyList())
        val rangedCourse = course("ranged", weeks = listOf(1, 2, 3))

        listOf(0, -1, -5).forEach { weekIndex ->
            assertFalse(weeklyCourse.activeOnWeek(weekIndex))
            assertFalse(rangedCourse.activeOnWeek(weekIndex))
        }
    }

    @Test
    fun `courses stay filtered by week once the term starts`() {
        val weeklyCourse = course("weekly", weeks = emptyList())
        val rangedCourse = course("ranged", weeks = listOf(2, 4))

        assertTrue(weeklyCourse.activeOnWeek(1))
        assertTrue(weeklyCourse.activeOnWeek(20))
        assertFalse(rangedCourse.activeOnWeek(1))
        assertTrue(rangedCourse.activeOnWeek(2))
        assertFalse(rangedCourse.activeOnWeek(3))
    }

    @Test
    fun `unknown week index hides every course`() {
        assertFalse(course("weekly", weeks = emptyList()).activeOnWeek(null))
        assertFalse(course("ranged", weeks = listOf(1)).activeOnWeek(null))
    }

    @Test
    fun `empty label tells the user the term has not started`() {
        assertEquals(
            ScheduleWidgetEmptyLabel.BeforeTermStart(termStart),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = true,
                termStartDate = termStart,
                offset = 0,
            ),
        )
        assertEquals(
            ScheduleWidgetEmptyLabel.BeforeTermStart(null),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = true,
                termStartDate = null,
                offset = 0,
            ),
        )
    }

    @Test
    fun `empty label separates a missing term start date from a free day`() {
        assertEquals(
            ScheduleWidgetEmptyLabel.TermStartMissing,
            scheduleWidgetEmptyLabel(
                termStartMissing = true,
                beforeTermStart = false,
                termStartDate = null,
                offset = 0,
            ),
        )
        assertEquals(
            ScheduleWidgetEmptyLabel.BeforeTermStart(termStart),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = true,
                termStartDate = termStart,
                offset = 0,
            ),
        )
        assertEquals(
            ScheduleWidgetEmptyLabel.NoCourses(0),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = false,
                termStartDate = termStart,
                offset = 0,
            ),
        )
    }

    @Test
    fun `subtitle marks a missing term start date`() {
        assertEquals(
            ScheduleWidgetSubtitle.TermStartMissing(MONDAY),
            scheduleWidgetSubtitle(MONDAY, termStartMissing = true, beforeTermStart = false, sourceDate = null),
        )
        assertEquals(
            ScheduleWidgetSubtitle.BeforeTermStart(MONDAY),
            scheduleWidgetSubtitle(MONDAY, termStartMissing = false, beforeTermStart = true, sourceDate = null),
        )
        assertEquals(
            ScheduleWidgetSubtitle.TemporarySource(MONDAY, termStart),
            scheduleWidgetSubtitle(MONDAY, termStartMissing = false, beforeTermStart = false, sourceDate = termStart),
        )
        assertEquals(
            ScheduleWidgetSubtitle.Weekday(MONDAY),
            scheduleWidgetSubtitle(MONDAY, termStartMissing = false, beforeTermStart = false, sourceDate = null),
        )
    }

    @Test
    fun `next course empty title separates a missing term start date from a free day`() {
        val today = LocalDate.of(2026, 9, 21)

        assertEquals(
            NextCourseEmptyLabel.TermStartMissing,
            nextCourseEmptyLabel(
                weekIndex = null,
                termStartDate = null,
                targetDate = today,
                today = today,
                hasCourses = false,
            ),
        )
        assertEquals(
            NextCourseEmptyLabel.BeforeTermStart(termStart),
            nextCourseEmptyLabel(
                weekIndex = 0,
                termStartDate = termStart,
                targetDate = today,
                today = today,
                hasCourses = false,
            ),
        )
        assertEquals(
            NextCourseEmptyLabel.NoneToday,
            nextCourseEmptyLabel(
                weekIndex = 3,
                termStartDate = termStart,
                targetDate = today,
                today = today,
                hasCourses = false,
            ),
        )
        assertEquals(
            NextCourseEmptyLabel.NoMoreToday,
            nextCourseEmptyLabel(
                weekIndex = 3,
                termStartDate = termStart,
                targetDate = today,
                today = today,
                hasCourses = true,
            ),
        )
        assertEquals(
            NextCourseEmptyLabel.NoneTomorrow,
            nextCourseEmptyLabel(
                weekIndex = 3,
                termStartDate = termStart,
                targetDate = today.plusDays(1),
                today = today,
                hasCourses = false,
            ),
        )
    }

    @Test
    fun `holiday wording outranks the term state on both widgets`() {
        val today = LocalDate.of(2026, 10, 1)
        val nationalDay = WidgetHolidayLabel.Named("国庆节")

        assertEquals(
            ScheduleWidgetEmptyLabel.Holiday(nationalDay),
            scheduleWidgetEmptyLabel(
                termStartMissing = true,
                beforeTermStart = true,
                termStartDate = termStart,
                offset = 0,
                holidayLabel = nationalDay,
            ),
        )
        assertEquals(
            ScheduleWidgetSubtitle.Holiday(THURSDAY, nationalDay),
            scheduleWidgetSubtitle(
                THURSDAY,
                termStartMissing = true,
                beforeTermStart = true,
                sourceDate = LocalDate.of(2026, 9, 28),
                holidayLabel = nationalDay,
            ),
        )
        assertEquals(
            NextCourseEmptyLabel.Holiday(nationalDay),
            nextCourseEmptyLabel(
                weekIndex = null,
                termStartDate = termStart,
                targetDate = today,
                today = today,
                hasCourses = false,
                holidayLabel = nationalDay,
            ),
        )
    }

    private fun course(id: String, weeks: List<Int>): CourseItem =
        CourseItem(
            id = id,
            title = id,
            weeks = weeks,
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )

    private companion object {
        const val MONDAY = 1
        const val THURSDAY = 4
    }
}
