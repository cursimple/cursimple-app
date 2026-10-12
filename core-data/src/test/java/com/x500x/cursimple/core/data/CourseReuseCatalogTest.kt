package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test

class CourseReuseCatalogTest {
    private val a = CourseItem("reused-id", "算法", teacher = "A", time = CourseTimeSlot(1, 1, 2))
    private fun schedule(course: CourseItem) = TermSchedule("term", "2026-10-11", listOf(DailySchedule(1, listOf(course))))

    @Test fun manualOverridesMergeWithinEachTermWithoutHidingOtherTerms() {
        val second = a.copy(teacher = "B")
        val edited = a.copy(location = "Edited")
        assertEquals(listOf(edited, second), courseReuseCatalog(mapOf("first" to schedule(a), "second" to schedule(second)),
            mapOf("first" to listOf(edited))))
    }

    @Test fun deletedAndReminderOnlyCoursesCannotBecomeTemplates() {
        assertTrue(courseReuseCatalog(mapOf("one" to schedule(a)), mapOf("one" to listOf(a.copy(hidden = true),
            a.copy(id = "placeholder", reminderOnly = true)))).isEmpty())
    }

    @Test fun manualOnlyAndEmptyTermsAreSupportedWithoutChangingTheirCourses() {
        val originals = mapOf("one" to listOf(a))
        assertEquals(listOf(a), courseReuseCatalog(mapOf("empty" to null), originals))
        assertEquals(mapOf("one" to listOf(a)), originals)
    }
}
