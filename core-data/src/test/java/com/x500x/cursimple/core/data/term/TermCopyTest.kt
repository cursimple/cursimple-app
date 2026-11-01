package com.x500x.cursimple.core.data.term

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermCopyTest {
    private val math = CourseItem(
        "m1", "高等数学", teacher = "小浩", location = "理工楼110", weeks = listOf(1, 2, 3),
        time = CourseTimeSlot(1, 1, 2), colorArgb = 0xFF1E3A5FL, borderArgb = 0xFFFFB74DL,
        reminderStartTime = "08:00", reminderEndTime = "08:50",
        weekLocations = mapOf(2 to "东楼"), weekTeachers = mapOf(3 to "小邱"),
    )

    @Test
    fun theCourseKeepsItsIdentityAndLooksButLosesWeekSpecifics() {
        val plan = planTermCopy(listOf(math), schedule = null, keepReminders = false, newTermId = "newterm1")
        val copied = plan.manualCourses.single()
        assertEquals("高等数学", copied.title)
        assertEquals("小浩", copied.teacher)
        assertEquals("理工楼110", copied.location)
        assertEquals(CourseTimeSlot(1, 1, 2), copied.time)
        assertEquals(0xFF1E3A5FL, copied.colorArgb)
        assertTrue(copied.weeks.isEmpty())
        assertTrue(copied.weekLocations.isEmpty())
        assertTrue(copied.weekTeachers.isEmpty())
        assertTrue(copied.id != math.id)
    }

    @Test
    fun remindersAreDroppedByDefaultAndKeptOnRequest() {
        assertNull(planTermCopy(listOf(math), null, keepReminders = false, newTermId = "t").manualCourses.single().reminderStartTime)
        assertEquals("08:00", planTermCopy(listOf(math), null, keepReminders = true, newTermId = "t").manualCourses.single().reminderStartTime)
    }

    @Test
    fun hiddenAndPurgedCoursesAreNotCarriedOver() {
        val hidden = math.copy(id = "h", hidden = true)
        val purged = math.copy(id = "p", purged = true)
        val plan = planTermCopy(listOf(math, hidden, purged), null, keepReminders = false, newTermId = "t")
        assertEquals(1, plan.manualCourses.size)
        assertFalse(plan.manualCourses.any { it.hidden || it.purged })
    }

    @Test
    fun copiedIdsAreUniqueAcrossTerms() {
        val a = planTermCopy(listOf(math), null, false, "termA").manualCourses.single().id
        val b = planTermCopy(listOf(math), null, false, "termB").manualCourses.single().id
        assertTrue(a != b)
    }
}
