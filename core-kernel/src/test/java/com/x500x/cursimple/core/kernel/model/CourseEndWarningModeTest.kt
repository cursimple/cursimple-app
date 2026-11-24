package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEndWarningModeTest {
    @Test fun offDoesNotMarkEitherFinalWeek() {
        assertEquals(CourseEndWarning.None, courseEndWarning(15, listOf(1, 15), CourseEndWarningMode.Off))
    }

    @Test fun finalWeekModeOnlyMarksTheLastWeek() {
        assertEquals(CourseEndWarning.None, courseEndWarning(14, listOf(1, 15), CourseEndWarningMode.FinalWeek))
        assertEquals(CourseEndWarning.FinalWeek, courseEndWarning(15, listOf(1, 15), CourseEndWarningMode.FinalWeek))
    }

    @Test fun twoWeekModeMarksBothFinalWeeks() {
        assertEquals(CourseEndWarning.NearEnd, courseEndWarning(14, listOf(1, 15), CourseEndWarningMode.TwoWeeks))
        assertEquals(CourseEndWarning.FinalWeek, courseEndWarning(15, listOf(1, 15), CourseEndWarningMode.TwoWeeks))
    }
}
