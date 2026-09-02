package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLibraryTest {

    private fun course(
        id: String,
        title: String = id,
        dayOfWeek: Int = 1,
        startNode: Int = 1,
        teacher: String = "",
        location: String = "",
        reminderOnly: Boolean = false,
    ) = CourseItem(
        id = id,
        title = title,
        teacher = teacher,
        location = location,
        reminderOnly = reminderOnly,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = startNode + 1),
    )

    @Test
    fun `courses from both sources are merged and tagged`() {
        val library = buildCourseLibrary(
            pluginCourses = listOf(course("p1")),
            manualCourses = listOf(course("m1")),
        )

        assertEquals(2, library.size)
        assertEquals(CourseSource.Plugin, library.first { it.course.id == "p1" }.source)
        assertEquals(CourseSource.Manual, library.first { it.course.id == "m1" }.source)
    }

    @Test
    fun `only manual courses are editable`() {
        val library = buildCourseLibrary(listOf(course("p1")), listOf(course("m1")))

        assertFalse(library.first { it.course.id == "p1" }.editable)
        assertTrue(library.first { it.course.id == "m1" }.editable)
    }

    @Test
    fun `reminder placeholders never reach the library`() {
        val library = buildCourseLibrary(
            pluginCourses = listOf(course("p1", reminderOnly = true)),
            manualCourses = listOf(course("m1", reminderOnly = true), course("m2")),
        )

        assertEquals(listOf("m2"), library.map { it.course.id })
    }

    @Test
    fun `a manual course wins over a plugin course with the same id`() {
        val library = buildCourseLibrary(
            pluginCourses = listOf(course("same", title = "插件版")),
            manualCourses = listOf(course("same", title = "手动版")),
        )

        assertEquals(1, library.size)
        assertEquals("手动版", library.single().course.title)
        assertEquals(CourseSource.Manual, library.single().source)
    }

    @Test
    fun `the query matches title teacher and location`() {
        val target = course("c", title = "高数", teacher = "张三", location = "东13")

        assertTrue(matchesCourseQuery(target, "高数"))
        assertTrue(matchesCourseQuery(target, "张三"))
        assertTrue(matchesCourseQuery(target, "东13"))
        assertTrue(matchesCourseQuery(target, ""))
        assertTrue(matchesCourseQuery(target, "   "))
        assertFalse(matchesCourseQuery(target, "英语"))
    }

    @Test
    fun `the query ignores case`() {
        val target = course("c", title = "Linear Algebra")

        assertTrue(matchesCourseQuery(target, "linear"))
        assertTrue(matchesCourseQuery(target, "ALGEBRA"))
    }

    @Test
    fun `sorting by weekday orders by day then period`() {
        val entries = buildCourseLibrary(
            emptyList(),
            listOf(
                course("c", dayOfWeek = 3, startNode = 1),
                course("a", dayOfWeek = 1, startNode = 5),
                course("b", dayOfWeek = 1, startNode = 1),
            ),
        )

        assertEquals(listOf("b", "a", "c"), sortCourseLibrary(entries, CourseSortMode.ByWeekday).map { it.course.id })
    }

    @Test
    fun `sorting by title falls back to weekday for equal titles`() {
        val entries = buildCourseLibrary(
            emptyList(),
            listOf(
                course("late", title = "同名", dayOfWeek = 5),
                course("early", title = "同名", dayOfWeek = 2),
                course("first", title = "AAA", dayOfWeek = 7),
            ),
        )

        assertEquals(
            listOf("first", "early", "late"),
            sortCourseLibrary(entries, CourseSortMode.ByTitle).map { it.course.id },
        )
    }

    @Test
    fun `sorting by source puts plugin courses first`() {
        val entries = buildCourseLibrary(listOf(course("p", dayOfWeek = 7)), listOf(course("m", dayOfWeek = 1)))

        assertEquals(listOf("p", "m"), sortCourseLibrary(entries, CourseSortMode.BySource).map { it.course.id })
    }

    @Test
    fun `grouping follows the timetable column order`() {
        val entries = buildCourseLibrary(
            emptyList(),
            listOf(course("mon", dayOfWeek = 1), course("sun", dayOfWeek = 7)),
        )

        // 周日起时课程库也要先列周日，否则两处显示的星期顺序对不上
        assertEquals(
            listOf(7, 1),
            groupCourseLibraryByWeekday(entries, listOf(7, 1, 2, 3, 4, 5, 6)).map { it.first },
        )
        assertEquals(
            listOf(1, 7),
            groupCourseLibraryByWeekday(entries, listOf(1, 2, 3, 4, 5, 6, 7)).map { it.first },
        )
    }

    @Test
    fun `a day hidden from the timetable still shows its courses at the end`() {
        val entries = buildCourseLibrary(emptyList(), listOf(course("sat", dayOfWeek = 6)))

        assertEquals(listOf(6), groupCourseLibraryByWeekday(entries, listOf(1, 2, 3, 4, 5)).map { it.first })
    }
}
