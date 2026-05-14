package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.model.targetDates
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal data class DailyReminderObject(
    val slotLabel: String,
    val course: CourseItem,
    val date: LocalDate,
    val slot: ClassSlotTime,
)

internal data class LabelRuleDecision(
    val remindLabels: Set<String>,
    val skipLabels: Set<String>,
)

internal class LabelReminderRuleEvaluator {
    fun expand(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<ReminderPlan> = expandAll(
        rules = listOf(rule),
        schedule = schedule,
        timingProfile = timingProfile,
        fromDate = fromDate,
        temporaryScheduleOverrides = temporaryScheduleOverrides,
    )

    fun expandAll(
        rules: List<ReminderRule>,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<ReminderPlan> {
        val zone = ZoneId.systemDefault()
        return candidateDates(schedule, timingProfile, fromDate, temporaryScheduleOverrides)
            .flatMap { date ->
                val dailyObjects = dailyReminderObjects(
                    schedule = schedule,
                    timingProfile = timingProfile,
                    targetDate = date,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                )
                val decision = evaluate(rules, dailyObjects)
                rules
                    .filter { it.enabled && it.matches(dailyObjects) }
                    .flatMap { rule ->
                        val targetLabels = rule.labelActions
                            .filter { it.action == ReminderLabelActionType.Remind }
                            .mapTo(mutableSetOf()) { it.slotLabel.trim() }
                            .filter { it.isNotBlank() && it !in decision.skipLabels }
                        dailyObjects
                            .filter { it.slotLabel in targetLabels }
                            .map { daily -> buildPlan(daily, rule, zone) }
                    }
            }
            .distinctBy { it.systemAlarmKey() }
            .sortedBy { it.triggerAtMillis }
    }

    fun dailyReminderObjects(
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        targetDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<DailyReminderObject> {
        val termStart = timingProfile.termStartLocalDate()
        val sourceDate = resolveTemporaryScheduleSourceDate(targetDate, temporaryScheduleOverrides)
        val sourceWeek = resolveTermWeek(termStart, sourceDate)
        val dayOfWeek = sourceDate.dayOfWeek.value
        return schedule.dailySchedules
            .flatMap { it.courses }
            .asSequence()
            .filter { it.time.dayOfWeek == dayOfWeek }
            .filter { it.isActiveOnSourceDate(termStart, sourceDate, sourceWeek) }
            .filterNot { isCourseTemporarilyCancelled(targetDate, it, temporaryScheduleOverrides) }
            .mapNotNull { course ->
                val label = course.reminderSlotLabel(timingProfile)?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val slot = timingProfile.findSlot(course.time.startNode, course.time.endNode)
                    ?: placeholderSlot(course, label)
                    ?: return@mapNotNull null
                DailyReminderObject(
                    slotLabel = label,
                    course = course,
                    date = targetDate,
                    slot = slot,
                )
            }
            .sortedWith(compareBy<DailyReminderObject> { it.slot.startTime }.thenBy { it.course.title })
            .toList()
    }

    fun evaluate(
        rules: List<ReminderRule>,
        dailyObjects: List<DailyReminderObject>,
    ): LabelRuleDecision {
        val presentLabels = dailyObjects.mapTo(mutableSetOf()) { it.slotLabel }
        val remind = mutableSetOf<String>()
        val skip = mutableSetOf<String>()
        rules.filter { it.enabled }.forEach { rule ->
            val matched = rule.labelConditions.all { condition ->
                val exists = condition.slotLabel in presentLabels
                when (condition.presence) {
                    ReminderLabelPresence.Exists -> exists
                    ReminderLabelPresence.Absent -> !exists
                }
            }
            if (matched) {
                rule.labelActions.forEach { action ->
                    val label = action.slotLabel.trim()
                    if (label.isBlank()) return@forEach
                    when (action.action) {
                        ReminderLabelActionType.Remind -> remind += label
                        ReminderLabelActionType.Skip -> skip += label
                    }
                }
            }
        }
        return LabelRuleDecision(remindLabels = remind, skipLabels = skip)
    }

    private fun ReminderRule.matches(dailyObjects: List<DailyReminderObject>): Boolean {
        val presentLabels = dailyObjects.mapTo(mutableSetOf()) { it.slotLabel }
        return labelConditions.all { condition ->
            val exists = condition.slotLabel in presentLabels
            when (condition.presence) {
                ReminderLabelPresence.Exists -> exists
                ReminderLabelPresence.Absent -> !exists
            }
        }
    }

    private fun candidateDates(
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<LocalDate> {
        val termStart = timingProfile.termStartLocalDate()
        val regularDates = schedule.dailySchedules
            .flatMap { it.courses }
            .flatMap { course ->
                val weeks = course.weeks.ifEmpty { (1..60).toList() }
                weeks.map { week ->
                    termStart
                        .plusWeeks((week - 1).toLong())
                        .plusDays((course.time.dayOfWeek - 1).toLong())
                }
            }
        val overrideTargetDates = temporaryScheduleOverrides.flatMap { it.targetDates() }
        return (regularDates + overrideTargetDates)
            .distinct()
            .filterNot { it.isBefore(fromDate) }
            .sorted()
    }

    private fun buildPlan(
        daily: DailyReminderObject,
        rule: ReminderRule,
        zone: ZoneId,
    ): ReminderPlan {
        val classStart = LocalDateTime.of(daily.date, daily.slot.startLocalTime())
        val trigger = classStart
            .minusMinutes(rule.advanceMinutes.toLong())
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return ReminderPlan(
            planId = "${rule.ruleId}_${daily.course.id}_$trigger",
            ruleId = rule.ruleId,
            pluginId = rule.pluginId,
            title = buildTitle(daily, rule.advanceMinutes),
            message = buildMessage(daily),
            triggerAtMillis = trigger,
            ringtoneUri = rule.ringtoneUri,
            courseId = daily.course.id,
        )
    }

    private fun buildTitle(
        daily: DailyReminderObject,
        advanceMinutes: Int,
    ): String {
        val weekday = weekdayName(daily.date.dayOfWeek.value)
        val advance = if (advanceMinutes > 0) "（提前${advanceMinutes}分钟）" else ""
        return "${weekday} ${daily.slot.startTime} ${daily.course.title}$advance"
    }

    private fun buildMessage(daily: DailyReminderObject): String {
        val date = "${daily.date.monthValue}月${daily.date.dayOfMonth}日"
        val weekday = weekdayName(daily.date.dayOfWeek.value)
        val timeRange = "${daily.slot.startTime}-${daily.slot.endTime}"
        val nodes = "第${daily.course.time.startNode}-${daily.course.time.endNode}节"
        val location = daily.course.location.ifBlank { "待定教室" }
        return "$date $weekday $timeRange · $nodes · $location"
    }

    private fun placeholderSlot(course: CourseItem, label: String): ClassSlotTime? {
        val start = course.reminderStartTime?.takeIf { it.isNotBlank() } ?: return null
        val end = course.reminderEndTime?.takeIf { it.isNotBlank() } ?: return null
        return ClassSlotTime(
            startNode = course.time.startNode,
            endNode = course.time.endNode,
            startTime = start,
            endTime = end,
            label = label,
        )
    }

    private fun CourseItem.isActiveOnSourceDate(
        termStart: LocalDate,
        sourceDate: LocalDate,
        sourceWeek: Int = resolveTermWeek(termStart, sourceDate),
    ): Boolean {
        if (weeks.isEmpty()) return true
        return sourceWeek in weeks
    }

    private fun resolveTermWeek(termStart: LocalDate, date: LocalDate): Int =
        ChronoUnit.WEEKS.between(
            termStart.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
            date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
        ).toInt() + 1

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
}
