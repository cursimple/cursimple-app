package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseConflictTest {

    private fun course(
        id: String,
        title: String = id,
        dayOfWeek: Int = 1,
        startNode: Int = 1,
        endNode: Int = 2,
        weeks: List<Int> = emptyList(),
        category: CourseCategory = CourseCategory.Course,
        reminderOnly: Boolean = false,
    ) = CourseItem(
        id = id,
        title = title,
        weeks = weeks,
        category = category,
        reminderOnly = reminderOnly,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    @Test
    fun `partially overlapping node ranges conflict`() {
        val conflict = courseConflictOrNull(
            course("a", startNode = 1, endNode = 3, weeks = listOf(1, 2, 3)),
            course("b", startNode = 3, endNode = 4, weeks = listOf(1, 2, 3)),
        )

        assertNotNull(conflict)
        assertEquals(3..3, conflict!!.overlappingNodes)
    }

    @Test
    fun `a node range fully containing another conflicts`() {
        val conflict = courseConflictOrNull(
            course("a", startNode = 1, endNode = 8, weeks = listOf(5)),
            course("b", startNode = 3, endNode = 4, weeks = listOf(5)),
        )

        assertNotNull(conflict)
        assertEquals(3..4, conflict!!.overlappingNodes)
        assertEquals(listOf(5), conflict.overlappingWeeks)
    }

    @Test
    fun `identical node ranges conflict`() {
        val conflict = courseConflictOrNull(
            course("a", startNode = 3, endNode = 4, weeks = listOf(2)),
            course("b", startNode = 3, endNode = 4, weeks = listOf(2)),
        )

        assertEquals(3..4, conflict?.overlappingNodes)
    }

    @Test
    fun `adjacent node ranges do not conflict`() {
        assertNull(
            courseConflictOrNull(
                course("a", startNode = 1, endNode = 2, weeks = listOf(1)),
                course("b", startNode = 3, endNode = 4, weeks = listOf(1)),
            ),
        )
    }

    @Test
    fun `courses on different weekdays do not conflict`() {
        assertNull(
            courseConflictOrNull(
                course("a", dayOfWeek = 1, weeks = listOf(1, 2, 3)),
                course("b", dayOfWeek = 2, weeks = listOf(1, 2, 3)),
            ),
        )
    }

    @Test
    fun `odd and even week courses in the same slot do not conflict`() {
        val odd = course("odd", weeks = (1..16).filter { it % 2 == 1 })
        val even = course("even", weeks = (1..16).filter { it % 2 == 0 })

        assertNull(courseConflictOrNull(odd, even))
    }

    @Test
    fun `week ranges that never meet do not conflict`() {
        assertNull(
            courseConflictOrNull(
                course("a", weeks = (1..8).toList()),
                course("b", weeks = (9..16).toList()),
            ),
        )
    }

    @Test
    fun `overlapping week ranges conflict only on the shared weeks`() {
        val conflict = courseConflictOrNull(
            course("a", weeks = (1..8).toList()),
            course("b", weeks = (7..16).toList()),
        )

        assertEquals(listOf(7, 8), conflict?.overlappingWeeks)
    }

    @Test
    fun `an all-weeks course conflicts with an odd-week course`() {
        val everyWeek = course("all", weeks = emptyList())
        val odd = course("odd", weeks = listOf(1, 3, 5))

        val conflict = courseConflictOrNull(everyWeek, odd)

        assertEquals(listOf(1, 3, 5), conflict?.overlappingWeeks)
    }

    @Test
    fun `two all-weeks courses conflict across the whole term`() {
        val conflict = courseConflictOrNull(
            course("a", weeks = emptyList()),
            course("b", weeks = emptyList()),
            maxWeekCount = 20,
        )

        assertEquals((1..20).toList(), conflict?.overlappingWeeks)
    }

    @Test
    fun `an all-weeks course still covers weeks beyond the default term length`() {
        val conflict = courseConflictOrNull(
            course("all", weeks = emptyList()),
            course("late", weeks = listOf(35)),
            maxWeekCount = 30,
        )

        assertEquals(listOf(35), conflict?.overlappingWeeks)
    }

    @Test
    fun `weeks below one are not part of any term week`() {
        assertNull(
            courseConflictOrNull(
                course("a", weeks = listOf(0, -1)),
                course("b", weeks = listOf(0, -1)),
            ),
        )
    }

    @Test
    fun `a course does not conflict with itself`() {
        val single = course("same", weeks = listOf(1, 2, 3))

        assertNull(courseConflictOrNull(single, single.copy(title = "改了名字")))
        assertTrue(findCourseConflicts(listOf(single, single)).isEmpty())
    }

    @Test
    fun `reminder only placeholders never conflict`() {
        assertNull(
            courseConflictOrNull(
                course("placeholder", reminderOnly = true, weeks = listOf(1)),
                course("real", weeks = listOf(1)),
            ),
        )
        assertTrue(
            findCourseConflicts(
                listOf(
                    course("placeholder", reminderOnly = true, weeks = listOf(1)),
                    course("real", weeks = listOf(1)),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `reversed node bounds are normalised before comparing`() {
        val conflict = courseConflictOrNull(
            course("a", startNode = 4, endNode = 2, weeks = listOf(1)),
            course("b", startNode = 3, endNode = 3, weeks = listOf(1)),
        )

        assertEquals(3..3, conflict?.overlappingNodes)
    }

    @Test
    fun `an exam overlapping a regular course is reported and tagged`() {
        val conflict = courseConflictOrNull(
            course("exam", category = CourseCategory.Exam, weeks = listOf(17)),
            course("class", weeks = emptyList()),
        )

        assertNotNull(conflict)
        assertEquals(CourseConflictKind.ExamVsCourse, conflict!!.kind)
        assertEquals(listOf(17), conflict.overlappingWeeks)
    }

    @Test
    fun `two exams in the same slot are tagged as exam versus exam`() {
        val conflict = courseConflictOrNull(
            course("e1", category = CourseCategory.Exam, weeks = listOf(17)),
            course("e2", category = CourseCategory.Exam, weeks = listOf(17)),
        )

        assertEquals(CourseConflictKind.ExamVsExam, conflict?.kind)
    }

    @Test
    fun `two regular courses are tagged as course versus course`() {
        val conflict = courseConflictOrNull(
            course("a", weeks = listOf(3)),
            course("b", weeks = listOf(3)),
        )

        assertEquals(CourseConflictKind.CourseVsCourse, conflict?.kind)
    }

    @Test
    fun `each conflicting pair is reported once and sorted by day then node`() {
        val courses = listOf(
            course("c1", title = "周三课", dayOfWeek = 3, startNode = 5, endNode = 6, weeks = listOf(1)),
            course("c2", title = "周三撞课", dayOfWeek = 3, startNode = 5, endNode = 6, weeks = listOf(1)),
            course("a1", title = "周一课", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1)),
            course("a2", title = "周一撞课", dayOfWeek = 1, startNode = 2, endNode = 3, weeks = listOf(1)),
        )

        val conflicts = findCourseConflicts(courses)

        assertEquals(2, conflicts.size)
        assertEquals(1, conflicts[0].dayOfWeek)
        assertEquals(3, conflicts[1].dayOfWeek)
    }

    @Test
    fun `duplicate ids are collapsed before pairing`() {
        val courses = listOf(
            course("a", weeks = listOf(1)),
            course("a", weeks = listOf(1)),
            course("b", weeks = listOf(1)),
        )

        assertEquals(1, findCourseConflicts(courses).size)
    }

    @Test
    fun `a candidate is matched against every existing course`() {
        val candidate = course("draft", title = "新课", weeks = listOf(2, 3))
        val existing = listOf(
            course("hit1", title = "撞一", weeks = listOf(3)),
            course("hit2", title = "撞二", weeks = emptyList()),
            course("miss", title = "不撞", weeks = listOf(9)),
            course("otherDay", title = "别天", dayOfWeek = 5, weeks = listOf(2)),
        )

        val conflicts = conflictsWithCourse(candidate, existing)

        assertEquals(listOf("撞一", "撞二"), conflicts.map { it.second.title }.sorted())
        assertTrue(conflicts.all { it.first.id == "draft" })
    }

    @Test
    fun `a candidate that is already saved is not matched against itself`() {
        val saved = course("saved", weeks = listOf(1))

        assertTrue(conflictsWithCourse(saved, listOf(saved)).isEmpty())
    }

    @Test
    fun `term week intersection treats an empty list as every week`() {
        assertEquals((1..4).toList(), termWeekIntersection(emptyList(), (1..4).toList()))
        assertEquals((1..4).toList(), termWeekIntersection((1..4).toList(), emptyList()))
        assertEquals(listOf(2, 3), termWeekIntersection(listOf(1, 2, 3), listOf(2, 3, 9)).sorted())
        assertTrue(termWeekIntersection(listOf(1, 3), listOf(2, 4)).isEmpty())
    }

    @Test
    fun `node range normalises reversed bounds`() {
        assertEquals(2..5, CourseTimeSlot(dayOfWeek = 1, startNode = 5, endNode = 2).nodeRange())
        assertEquals(2..5, CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 5).nodeRange())
    }
}
