package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.resolveTemporaryScheduleDayOfWeek
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.kernel.time.BeijingTime
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
        fromDate: LocalDate = BeijingTime.today(),
        temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
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
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
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
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<ReminderPlan> {
        val slot = timingProfile.findSlot(course.time.startNode, course.time.endNode) ?: return emptyList()
        val termStart = timingProfile.termStartLocalDate()
        val zone = ZoneId.of(timingProfile.timezone)
        return courseOccurrenceDates(
            course = course,
            termStart = termStart,
            fromDate = fromDate,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
        ).map { courseDate ->
            val classStart = LocalDateTime.of(courseDate, slot.startLocalTime())
            val trigger = classStart
                .minusMinutes(rule.advanceMinutes.toLong())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            ReminderPlan(
                planId = "${rule.ruleId}_${course.id}_$trigger",
                ruleId = rule.ruleId,
                pluginId = rule.pluginId,
                title = buildTitle(course, courseDate, slot, rule.advanceMinutes),
                message = buildMessage(course, courseDate, slot),
                triggerAtMillis = trigger,
                ringtoneUri = rule.ringtoneUri,
                courseId = course.id,
            )
        }
    }

    private fun courseOccurrenceDates(
        course: CourseItem,
        termStart: LocalDate,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<LocalDate> {
        return course.weeks
            .flatMap { week ->
                val weekStart = termStart.plusWeeks((week - 1).toLong())
                (0..6).map { offset -> weekStart.plusDays(offset.toLong()) }
            }
            .filterNot { it.isBefore(fromDate) }
            .filter { date ->
                resolveTemporaryScheduleDayOfWeek(date, temporaryScheduleOverrides) == course.time.dayOfWeek
            }
    }

    private fun buildTitle(
        course: CourseItem,
        courseDate: LocalDate,
        slot: ClassSlotTime,
        advanceMinutes: Int,
    ): String {
        val weekday = weekdayName(course.time.dayOfWeek)
        val startTime = slot.startTime
        val advance = if (advanceMinutes > 0) "（提前${advanceMinutes}分钟）" else ""
        return "${weekday} ${startTime} ${course.title}$advance"
    }

    private fun buildMessage(
        course: CourseItem,
        courseDate: LocalDate,
        slot: ClassSlotTime,
    ): String {
        val date = "${courseDate.monthValue}月${courseDate.dayOfMonth}日"
        val weekday = weekdayName(course.time.dayOfWeek)
        val timeRange = "${slot.startTime}-${slot.endTime}"
        val nodes = "第${course.time.startNode}-${course.time.endNode}节"
        val location = course.location.ifBlank { "待定教室" }
        return "$date $weekday $timeRange · $nodes · $location"
    }

    private fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周$dayOfWeek"
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
