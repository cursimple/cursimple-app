package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEndWarningTest {
    @Test
    fun marksTheLastTwoDeclaredWeeks() {
        assertEquals(CourseEndWarning.None, courseEndWarning(10, listOf(10, 12, 14)))
        assertEquals(CourseEndWarning.NearEnd, courseEndWarning(13, listOf(10, 12, 14)))
        assertEquals(CourseEndWarning.FinalWeek, courseEndWarning(14, listOf(10, 12, 14)))
    }

    @Test
    fun leavesUnboundedCoursesUnmarked() {
        assertEquals(CourseEndWarning.None, courseEndWarning(14, emptyList()))
    }
}
