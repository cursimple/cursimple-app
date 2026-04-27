package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlanner {
    fun expandRule(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate = LocalDate.now(),
    ): List<ReminderPlan> {
        return schedule.dailySchedules
            .flatMap { it.courses }
            .filter { rule.matches(it) }
            .flatMap { course ->
                expandCourseOccurrences(
                    rule = rule,
                    course = course,
                    timingProfile = timingProfile,
                    fromDate = fromDate,
                )
            }
            .distinctBy { it.planId }
            .sortedBy { it.triggerAtMillis }
    }

    private fun expandCourseOccurrences(
        rule: ReminderRule,
        course: CourseItem,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
    ): List<ReminderPlan> {
        val slot = timingProfile.findSlot(course.time.startNode, course.time.endNode) ?: return emptyList()
        val termStart = timingProfile.termStartLocalDate()
        val zone = ZoneId.of(timingProfile.timezone)
        return course.weeks.mapNotNull { week ->
            val courseDate = termStart
                .plusWeeks((week - 1).toLong())
                .plusDays((course.time.dayOfWeek - 1).toLong())
            if (courseDate.isBefore(fromDate)) {
                return@mapNotNull null
            }
            val trigger = LocalDateTime.of(courseDate, slot.startLocalTime())
                .minusMinutes(rule.advanceMinutes.toLong())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            ReminderPlan(
                planId = "${rule.ruleId}_${course.id}_$trigger",
                ruleId = rule.ruleId,
                pluginId = rule.pluginId,
                title = "${course.title} 即将开始",
                message = "${course.time.startNode}-${course.time.endNode}节 ${course.location.ifBlank { "待定教室" }}",
                triggerAtMillis = trigger,
                ringtoneUri = rule.ringtoneUri,
                courseId = course.id,
            )
        }
    }

    private fun ReminderRule.matches(course: CourseItem): Boolean {
        return when (scopeType) {
            ReminderScopeType.SingleCourse -> course.id == courseId
            ReminderScopeType.TimeSlot -> {
                course.time.dayOfWeek in 1..7 &&
                    course.time.startNode == startNode &&
                    course.time.endNode == endNode
            }
        }
    }
}
