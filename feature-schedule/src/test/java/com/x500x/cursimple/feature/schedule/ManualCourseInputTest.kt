package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCourseInputTest {
    @Test
    fun `parity that filters out every week is rejected instead of meaning all weeks`() {
        assertNull(manualCourseWeeksOrNull(2, 2, WeekParity.Odd, maxWeekCount = 30))
        assertNull(manualCourseWeeksOrNull(3, 3, WeekParity.Even, maxWeekCount = 30))
    }

    @Test
    fun `parity filtering keeps the matching weeks`() {
        assertEquals(listOf(1, 2, 3, 4), manualCourseWeeksOrNull(1, 4, WeekParity.All, 30))
        assertEquals(listOf(1, 3, 5), manualCourseWeeksOrNull(1, 5, WeekParity.Odd, 30))
        assertEquals(listOf(2, 4), manualCourseWeeksOrNull(1, 5, WeekParity.Even, 30))
    }

    @Test
    fun `invalid week ranges are rejected`() {
        assertNull(manualCourseWeeksOrNull(null, 4, WeekParity.All, 30))
        assertNull(manualCourseWeeksOrNull(1, null, WeekParity.All, 30))
        assertNull(manualCourseWeeksOrNull(0, 4, WeekParity.All, 30))
        assertNull(manualCourseWeeksOrNull(5, 4, WeekParity.All, 30))
        assertNull(manualCourseWeeksOrNull(1, 31, WeekParity.All, 30))
        assertNotNull(manualCourseWeeksOrNull(1, 30, WeekParity.All, 30))
    }

    @Test
    fun `a course built from an empty week list would show up in every week`() {
        val course = CourseItem(
            id = "all-weeks",
            title = "全周课",
            weeks = emptyList(),
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )

        assertTrue(course.isActiveInWeek(1))
        assertTrue(course.isActiveInWeek(20))
    }
}
