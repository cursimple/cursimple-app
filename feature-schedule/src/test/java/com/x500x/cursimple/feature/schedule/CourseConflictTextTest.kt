package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseConflict
import com.x500x.cursimple.core.kernel.model.CourseConflictKind
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ExamCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CourseConflictTextTest {

    private fun course(
        id: String,
        title: String = id,
        dayOfWeek: Int = 1,
        startNode: Int = 1,
        endNode: Int = 2,
        weeks: List<Int> = emptyList(),
        category: CourseCategory = CourseCategory.Course,
    ) = CourseItem(
        id = id,
        title = title,
        weeks = weeks,
        category = category,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun conflict(
        firstTitle: String,
        secondTitle: String,
        dayOfWeek: Int = 1,
        nodes: IntRange = 1..2,
        weeks: List<Int> = listOf(1, 2, 3),
        secondCategory: CourseCategory = CourseCategory.Course,
    ) = CourseConflict(
        first = course("a", title = firstTitle, dayOfWeek = dayOfWeek),
        second = course("b", title = secondTitle, dayOfWeek = dayOfWeek, category = secondCategory),
        dayOfWeek = dayOfWeek,
        overlappingNodes = nodes,
        overlappingWeeks = weeks,
    )

    @Test
    fun `node range text collapses a single node`() {
        assertEquals("第 3 节", describeNodeRange(3..3))
        assertEquals("第 3-5 节", describeNodeRange(3..5))
    }

    @Test
    fun `consecutive weeks collapse into one range`() {
        assertEquals("1-3 周", compactWeekRanges(listOf(1, 2, 3)))
        assertEquals("5 周", compactWeekRanges(listOf(5)))
        assertEquals("1-2、5-6 周", compactWeekRanges(listOf(1, 2, 5, 6)))
    }

    @Test
    fun `too many separate week segments fall back to a summary`() {
        assertEquals("1-9 周内共 5 周", compactWeekRanges(listOf(1, 3, 5, 7, 9)))
    }

    @Test
    fun `an empty week list has no text`() {
        assertEquals("", compactWeekRanges(emptyList()))
    }

    @Test
    fun `week text is deduplicated and sorted`() {
        assertEquals("1-3 周", compactWeekRanges(listOf(3, 1, 2, 3)))
    }

    @Test
    fun `conflict scope names the weekday nodes and weeks`() {
        assertEquals(
            "周三 · 第 3-4 节 · 5-8 周",
            conflictScopeText(conflict("甲", "乙", dayOfWeek = 3, nodes = 3..4, weeks = (5..8).toList())),
        )
    }

    @Test
    fun `conflict title lists both courses`() {
        assertEquals("高等数学 × 大学英语", conflictPairTitle(conflict("高等数学", "大学英语")))
    }

    @Test
    fun `conflict kind labels distinguish exams`() {
        assertEquals("两门课程", conflictKindLabel(CourseConflictKind.CourseVsCourse))
        assertEquals("考试与课程", conflictKindLabel(CourseConflictKind.ExamVsCourse))
        assertEquals("两场考试", conflictKindLabel(CourseConflictKind.ExamVsExam))
    }

    @Test
    fun `no conflicts means no warning`() {
        assertNull(addCourseConflictWarning(emptyList()))
    }

    @Test
    fun `the warning names the courses that clash`() {
        val warning = addCourseConflictWarning(listOf(conflict("新课", "大学英语", nodes = 1..2, weeks = listOf(1, 2))))

        assertTrue(warning!!.contains("大学英语"))
        assertTrue(warning.contains("第 1-2 节"))
        assertTrue(warning.contains("1-2 周"))
    }

    @Test
    fun `the warning lists at most three courses and counts the rest`() {
        val conflicts = (1..5).map { conflict("新课", "撞课$it") }

        val warning = addCourseConflictWarning(conflicts)!!

        assertTrue(warning.contains("撞课1"))
        assertTrue(warning.contains("撞课3"))
        assertTrue(!warning.contains("撞课4"))
        assertTrue(warning.contains("等 5 门"))
    }

    @Test
    fun `an incomplete form reports no conflict yet`() {
        val existing = listOf(course("existing", weeks = listOf(1, 2, 3)))

        assertTrue(
            draftCourseConflicts(
                existingCourses = existing,
                dayOfWeek = 1,
                startNode = null,
                endNode = 2,
                weeks = listOf(1),
                category = CourseCategory.Course,
            ).isEmpty(),
        )
        assertTrue(
            draftCourseConflicts(
                existingCourses = existing,
                dayOfWeek = 1,
                startNode = 1,
                endNode = 2,
                weeks = null,
                category = CourseCategory.Course,
            ).isEmpty(),
        )
    }

    @Test
    fun `a draft outside the allowed node range reports no conflict`() {
        val existing = listOf(course("existing", startNode = 1, endNode = 2, weeks = listOf(1)))

        assertTrue(
            draftCourseConflicts(
                existingCourses = existing,
                dayOfWeek = 1,
                startNode = 2,
                endNode = 1,
                weeks = listOf(1),
                category = CourseCategory.Course,
                maxNodeCount = 12,
            ).isEmpty(),
        )
    }

    @Test
    fun `a complete draft finds the courses it overlaps`() {
        val existing = listOf(
            course("hit", title = "撞的课", startNode = 2, endNode = 3, weeks = listOf(1, 2)),
            course("odd", title = "单周课", startNode = 1, endNode = 2, weeks = listOf(1, 3, 5)),
            course("miss", title = "别天的课", dayOfWeek = 4, weeks = listOf(2)),
        )

        val conflicts = draftCourseConflicts(
            existingCourses = existing,
            dayOfWeek = 1,
            startNode = 1,
            endNode = 2,
            weeks = listOf(2, 4),
            category = CourseCategory.Course,
            maxNodeCount = 12,
            maxWeekCount = 30,
        )

        assertEquals(listOf("撞的课"), conflicts.map { it.second.title })
        assertTrue(conflicts.all { it.first.id == DRAFT_COURSE_ID })
    }

    @Test
    fun `exam countdown text covers today tomorrow and later`() {
        val exam = course("exam", title = "期末考", category = CourseCategory.Exam)
        val date = LocalDate.parse("2026-06-17")

        assertEquals("就是今天", examCountdownLabel(ExamCountdown(exam, date, 0)))
        assertEquals("还有 1 天", examCountdownLabel(ExamCountdown(exam, date, 1)))
        assertEquals("还有 12 天", examCountdownLabel(ExamCountdown(exam, date, 12)))
    }
}
