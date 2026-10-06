package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType

/**
 * Match course-scoped reminder IDs and bounds; unbound first-course rules cannot identify a
 * badge in advance.
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
        // Prefer the course's own timing coverage before profile fallback.
        val slotLabel = timingProfile?.let { course.reminderSlotLabel(it) }
            ?: course.slotLabelOverride
        slotLabel != null && labelActions.any {
            it.action == ReminderLabelActionType.Remind && it.slotLabel == slotLabel
        }
    }
}
