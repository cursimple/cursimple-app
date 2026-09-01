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
        assertEquals(NodeRangeLabel.Single(3), describeNodeRange(3..3))
        assertEquals(NodeRangeLabel.Range(3, 5), describeNodeRange(3..5))
    }

    @Test
    fun `consecutive weeks collapse into one range`() {
        assertEquals(WeekRangesLabel.Segments(listOf(1..3)), compactWeekRanges(listOf(1, 2, 3)))
        assertEquals(WeekRangesLabel.Segments(listOf(5..5)), compactWeekRanges(listOf(5)))
        assertEquals(WeekRangesLabel.Segments(listOf(1..2, 5..6)), compactWeekRanges(listOf(1, 2, 5, 6)))
    }

    @Test
    fun `too many separate week segments fall back to a summary`() {
        assertEquals(WeekRangesLabel.Summary(1, 9, 5), compactWeekRanges(listOf(1, 3, 5, 7, 9)))
    }

    @Test
    fun `an empty week list has no text`() {
        assertEquals(WeekRangesLabel.Empty, compactWeekRanges(emptyList()))
    }

    @Test
    fun `week text is deduplicated and sorted`() {
        assertEquals(WeekRangesLabel.Segments(listOf(1..3)), compactWeekRanges(listOf(3, 1, 2, 3)))
    }

    @Test
    fun `conflict scope names the weekday nodes and weeks`() {
        assertEquals(
            ConflictScope(
                dayOfWeek = 3,
                nodes = NodeRangeLabel.Range(3, 4),
                weeks = WeekRangesLabel.Segments(listOf(5..8)),
            ),
            conflictScope(conflict("甲", "乙", dayOfWeek = 3, nodes = 3..4, weeks = (5..8).toList())),
        )
    }

    @Test
    fun `conflict title lists both courses`() {
        assertEquals(
            ConflictPair(firstTitle = "高等数学", secondTitle = "大学英语"),
            conflictPairTitle(conflict("高等数学", "大学英语")),
        )
    }

    @Test
    fun `conflict kind labels distinguish exams`() {
        assertEquals(
            R.string.schedule_conflict_kind_course_vs_course,
            conflictKindNameRes(CourseConflictKind.CourseVsCourse),
        )
        assertEquals(
            R.string.schedule_conflict_kind_exam_vs_course,
            conflictKindNameRes(CourseConflictKind.ExamVsCourse),
        )
        assertEquals(
            R.string.schedule_conflict_kind_exam_vs_exam,
            conflictKindNameRes(CourseConflictKind.ExamVsExam),
        )
    }

    @Test
    fun `no conflicts means no warning`() {
        assertNull(addCourseConflictWarning(emptyList()))
    }

    @Test
    fun `the warning names the courses that clash`() {
        val warning = addCourseConflictWarning(listOf(conflict("新课", "大学英语", nodes = 1..2, weeks = listOf(1, 2))))

        assertEquals(
            listOf(
                ConflictPreviewItem(
                    title = "大学英语",
                    nodes = NodeRangeLabel.Range(1, 2),
                    weeks = WeekRangesLabel.Segments(listOf(1..2)),
                ),
            ),
            warning!!.previewed,
        )
        assertEquals(1, warning.totalCount)
    }

    @Test
    fun `the warning lists at most three courses and counts the rest`() {
        val conflicts = (1..5).map { conflict("新课", "撞课$it") }

        val warning = addCourseConflictWarning(conflicts)!!

        assertEquals(listOf("撞课1", "撞课2", "撞课3"), warning.previewed.map { it.title })
        assertEquals(5, warning.totalCount)
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

        assertEquals(ExamCountdownLabel.Today, examCountdownLabel(ExamCountdown(exam, date, 0)))
        assertEquals(ExamCountdownLabel.Tomorrow, examCountdownLabel(ExamCountdown(exam, date, 1)))
        assertEquals(ExamCountdownLabel.DaysRemaining(12), examCountdownLabel(ExamCountdown(exam, date, 12)))
    }
}
