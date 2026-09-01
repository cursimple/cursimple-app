package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetReminderBadgeTest {
    private val math = course("math", startNode = 1, endNode = 2)
    private val physics = course("physics", startNode = 3, endNode = 4)
    private val exam = course("final", startNode = 5, endNode = 6, category = CourseCategory.Exam)

    @Test
    fun `a course reminder lights up the badge on its own course`() {
        val rule = courseScopedRule(courseId = "math", categories = listOf(CourseCategory.Course))

        assertTrue(rule.matchesWidgetCourse(math, timingProfile = null))
        assertFalse(rule.matchesWidgetCourse(physics, timingProfile = null))
    }

    @Test
    fun `an exam reminder lights up the badge on its own exam`() {
        val rule = courseScopedRule(courseId = "final", categories = listOf(CourseCategory.Exam))

        assertTrue(rule.matchesWidgetCourse(exam, timingProfile = null))
        assertFalse(rule.matchesWidgetCourse(math, timingProfile = null))
    }

    @Test
    fun `a disabled course reminder leaves the badge off`() {
        val rule = courseScopedRule(courseId = "math", categories = listOf(CourseCategory.Course))
            .copy(enabled = false)

        assertFalse(rule.matchesWidgetCourse(math, timingProfile = null))
    }

    @Test
    fun `a first course rule without a course lights up nothing`() {
        val rule = baseRule().copy(
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            periodStartNode = 1,
            periodEndNode = 4,
        )

        assertFalse(rule.matchesWidgetCourse(math, timingProfile = null))
        assertFalse(rule.matchesWidgetCourse(physics, timingProfile = null))
    }

    @Test
    fun `single course and time slot rules keep working`() {
        val single = baseRule().copy(scopeType = ReminderScopeType.SingleCourse, courseId = "math")
        val slot = baseRule().copy(scopeType = ReminderScopeType.TimeSlot, startNode = 3, endNode = 4)

        assertTrue(single.matchesWidgetCourse(math, timingProfile = null))
        assertFalse(single.matchesWidgetCourse(physics, timingProfile = null))
        assertTrue(slot.matchesWidgetCourse(physics, timingProfile = null))
        assertFalse(slot.matchesWidgetCourse(math, timingProfile = null))
    }

    @Test
    fun `an exam scoped rule skips the muted exam`() {
        val rule = baseRule().copy(scopeType = ReminderScopeType.Exam)

        assertTrue(rule.matchesWidgetCourse(exam, timingProfile = null))
        assertFalse(rule.copy(mutedCourseIds = listOf("final")).matchesWidgetCourse(exam, timingProfile = null))
        assertFalse(rule.matchesWidgetCourse(math, timingProfile = null))
    }

    @Test
    fun `a label rule falls back to the slot label written on the course`() {
        val rule = baseRule().copy(
            scopeType = ReminderScopeType.LabelRule,
            labelActions = listOf(ReminderLabelAction("上午第一节", ReminderLabelActionType.Remind)),
        )
        val labelled = math.copy(slotLabelOverride = "上午第一节")

        assertTrue(rule.matchesWidgetCourse(labelled, timingProfile = null))
        assertFalse(rule.matchesWidgetCourse(math, timingProfile = null))
    }

    /** 单课与考试提醒的规则形状：首课候选范围锁定到课程自身，并带上课程 id。 */
    private fun courseScopedRule(courseId: String, categories: List<CourseCategory>): ReminderRule =
        baseRule().copy(
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            courseId = courseId,
            displayName = "课程提醒：$courseId",
            firstCourseCandidate = FirstCourseCandidateScope(
                nodeRange = ReminderNodeRange(1, 2),
                categories = categories,
                titleContains = courseId,
            ),
        )

    private fun baseRule(): ReminderRule = ReminderRule(
        ruleId = "rule",
        pluginId = "plugin",
        scopeType = ReminderScopeType.SingleCourse,
        advanceMinutes = 10,
        createdAt = "2026-09-01T08:00:00+08:00",
        updatedAt = "2026-09-01T08:00:00+08:00",
    )

    private fun course(
        id: String,
        startNode: Int,
        endNode: Int,
        category: CourseCategory = CourseCategory.Course,
    ): CourseItem = CourseItem(
        id = id,
        title = id,
        category = category,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = startNode, endNode = endNode),
    )
}
