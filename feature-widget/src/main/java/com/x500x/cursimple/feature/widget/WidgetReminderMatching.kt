package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType

/**
 * 课程行「有提醒」角标的判定，与应用内课表格子同一口径。
 *
 * 单课提醒与考试提醒都写成把候选范围锁死到课程自身的首课候选规则，
 * 这类规则带课程 id 与候选范围，靠这两项对上课程；
 * 不带课程 id 的首课候选规则按节次挑当天第一门课，落到哪门要到当天才知道，不出角标。
 */
internal fun ReminderRule.matchesWidgetCourse(
    course: CourseItem,
    timingProfile: TermTimingProfile?,
): Boolean = enabled && when (scopeType) {
    ReminderScopeType.SingleCourse -> courseId == course.id
    ReminderScopeType.TimeSlot ->
        startNode == course.time.startNode && endNode == course.time.endNode
    ReminderScopeType.Exam ->
        course.category == CourseCategory.Exam && course.id !in mutedCourseIds
    ReminderScopeType.FirstCourseOfPeriod ->
        !courseId.isNullOrBlank() && courseId == course.id && firstCourseCandidate != null
    ReminderScopeType.LabelRule -> {
        // 节次名优先取课程自身覆盖，其次回退到计时档案，与提醒评估器口径一致
        val slotLabel = timingProfile?.let { course.reminderSlotLabel(it) }
            ?: course.slotLabelOverride
        slotLabel != null && labelActions.any {
            it.action == ReminderLabelActionType.Remind && it.slotLabel == slotLabel
        }
    }
}
