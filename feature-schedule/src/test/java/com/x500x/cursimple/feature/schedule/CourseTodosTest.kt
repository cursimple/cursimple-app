package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CourseTodosTest {
    private val math = CourseItem("m", "高等数学 ", time = CourseTimeSlot(1, 1, 2))
    private fun memo(id: String, course: String?, done: Boolean = false, due: String? = null) =
        MemoNote(id, courseKey = course?.let(::memoCourseKey), courseTitle = course.orEmpty(), title = id, completed = done, dueAt = due)

    @Test
    fun onlyTheCoursesOwnNotebookIsListedOpenOnesFirstBySoonestDeadline() {
        val memos = listOf(
            memo("later", "高等数学", due = "2026-10-20T23:59"),
            memo("done", "高等数学", done = true),
            memo("soon", "高等数学", due = "2026-10-10T23:59"),
            memo("other", "大学英语"),
            memo("loose", null),
        )
        assertEquals(listOf("soon", "later", "done"), courseTodosFor(memos, math).map { it.id })
    }

    @Test
    fun cardCountsAreOpenTodosPerNotebook() {
        val counts = pendingTodoCountsByCourse(
            listOf(memo("a", "高等数学"), memo("b", "高等数学"), memo("c", "高等数学", done = true), memo("d", "大学英语"), memo("e", null)),
        )
        assertEquals(2, counts[memoCourseKey("高等数学")])
        assertEquals(1, counts[memoCourseKey("大学英语")])
        assertEquals(2, counts.size)
    }

    @Test
    fun aNewTodoLandsInTheCoursesNotebookWithAnEndOfDayDeadline() {
        val todo = newCourseTodo(math, "  交实验报告 ", LocalDate.of(2026, 10, 12), nowMillis = 42L)
        assertEquals(memoCourseKey("高等数学"), todo.courseKey)
        assertEquals("高等数学", todo.courseTitle)
        assertEquals("交实验报告", todo.title)
        assertEquals("2026-10-12T23:59", todo.dueAt)
        assertEquals(42L, todo.createdAt)
        assertTrue(todo.id.startsWith("memo-"))
        assertEquals(null, newCourseTodo(math, "读书", null).dueAt)
    }
}
