package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.*
import org.junit.Test

class CourseReuseValuesTest {
    private val course = CourseItem("a", "算法", teacher = " 陈老师 ", location = " 教室 101 ",
        weekTeachers = mapOf(2 to "李老师", 3 to "陈老师", 4 to " "), weekLocations = mapOf(3 to "实验室 202"),
        time = CourseTimeSlot(2, 1, 2))

    @Test fun defaultAndWeeklyValuesAreTrimmedAndDeduplicated() {
        val values = courseReuseValues(listOf(course, course.copy(id = "b")))
        assertEquals(setOf("陈老师", "李老师"), values.teachers.toSet())
        assertEquals(setOf("教室 101", "实验室 202"), values.locations.toSet())
        assertEquals(listOf(course), values.courses)
    }

    @Test fun sameNamedCoursesWithDifferentMetadataStaySelectable() {
        val other = course.copy(id = "b", teacher = "赵老师", location = "303")
        assertEquals(listOf(course, other), courseReuseValues(listOf(course, other)).courses)
    }

    @Test fun hiddenCoursesAndReminderOnlyPlaceholdersAreExcluded() {
        val values = courseReuseValues(listOf(course.copy(hidden = true), course.copy(reminderOnly = true)))
        assertTrue(values.courses.isEmpty())
        assertTrue(values.teachers.isEmpty())
        assertTrue(values.locations.isEmpty())
    }

    @Test fun autocompleteSearchFindsMetadataAndArrowCanUseTheFullList() {
        val choices = listOf(CourseReuseOption("Algorithms", "Teacher Chen · Room 101"), CourseReuseOption("Physics", "Lab 202"))
        assertEquals(choices.take(1), filterCourseReuseOptions(choices, " teacher chen "))
        assertEquals(choices.takeLast(1), filterCourseReuseOptions(choices, "202"))
        assertEquals(choices, filterCourseReuseOptions(choices, ""))
        assertTrue(filterCourseReuseOptions(choices, "missing").isEmpty())
    }
}
