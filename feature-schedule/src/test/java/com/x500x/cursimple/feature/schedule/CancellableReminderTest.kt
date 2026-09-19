package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 课程详情页上那个「取消提醒」按钮，判定的是哪些规则能就地撤掉。 */
class CancellableReminderTest {

    private val math = CourseItem(
        id = "course-math",
        title = "高等数学",
        weeks = (1..4).toList(),
        category = CourseCategory.Course,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    private fun rule(
        ruleId: String,
        scopeType: ReminderScopeType,
        courseId: String? = null,
        enabled: Boolean = true,
        displayName: String = "课程提醒·高等数学",
    ) = ReminderRule(
        ruleId = ruleId,
        pluginId = "demo",
        scopeType = scopeType,
        courseId = courseId,
        displayName = displayName,
        advanceMinutes = 20,
        enabled = enabled,
        createdAt = "2026-03-02T08:00:00",
        updatedAt = "2026-03-02T08:00:00",
    )

    @Test
    fun `single course rules can be cancelled from the detail dialog`() {
        val rules = listOf(
            rule("r1", ReminderScopeType.FirstCourseOfPeriod, courseId = math.id),
            rule("r2", ReminderScopeType.SingleCourse, courseId = math.id),
        )

        assertEquals(listOf("r1", "r2"), cancellableReminderRuleIds(math, rules))
    }

    @Test
    fun `rules covering other courses are left alone`() {
        val rules = listOf(
            // 别的课的规则
            rule("other", ReminderScopeType.FirstCourseOfPeriod, courseId = "course-english"),
            // 按节次与按 label 的规则同时管着好几门课
            rule("slot", ReminderScopeType.TimeSlot),
            rule("label", ReminderScopeType.LabelRule),
            // 已经停用的规则本来就不提醒
            rule("disabled", ReminderScopeType.SingleCourse, courseId = math.id, enabled = false),
        )

        assertEquals(emptyList<String>(), cancellableReminderRuleIds(math, rules))
    }
}
