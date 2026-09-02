package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.model.targetDates
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderNotificationMessage
import com.x500x.cursimple.core.reminder.model.ReminderNotificationTitle
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.stableText
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
    ): List<ReminderPlan> = expandAll(
        rules = listOf(rule),
        schedule = schedule,
        timingProfile = timingProfile,
        fromDate = fromDate,
        temporaryScheduleOverrides = temporaryScheduleOverrides,
        holidayCalendar = holidayCalendar,
        dayPolicy = dayPolicy,
    )

    fun expandAll(
        rules: List<ReminderRule>,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
    ): List<ReminderPlan> {
        val zone = ZoneId.systemDefault()
        return candidateDates(
            schedule = schedule,
            timingProfile = timingProfile,
            fromDate = fromDate,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
            holidayCalendar = holidayCalendar,
            dayPolicy = dayPolicy,
        )
            .flatMap { date ->
                val dailyObjects = dailyReminderObjects(
                    schedule = schedule,
                    timingProfile = timingProfile,
                    targetDate = date,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                    holidayCalendar = holidayCalendar,
                    dayPolicy = dayPolicy,
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
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
    ): List<DailyReminderObject> {
        // 没有开学日期就换算不出教学周，无法判断课程哪天上，不下发任何提醒
        val termStart = timingProfile.termStartLocalDate() ?: return emptyList()
        val day = resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar)
        if (dayPolicy.suppresses(targetDate, day)) return emptyList()
        val sourceDate = day.sourceDate
        val sourceWeek = resolveTermWeek(termStart, sourceDate)
        val dayOfWeek = sourceDate.dayOfWeek.value
        return schedule.dailySchedules
            .flatMap { it.courses }
            .asSequence()
            .filter { it.time.dayOfWeek == dayOfWeek }
            .filter { it.isActiveInTermWeek(sourceWeek) }
            .filterNot { isCourseTemporarilyCancelled(targetDate, it, temporaryScheduleOverrides) }
            .mapNotNull { course ->
                val label = course.reminderSlotLabel(timingProfile)?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val slot = reminderSlot(course, timingProfile, label)
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
        holidayCalendar: HolidayCalendarSettings,
        dayPolicy: ReminderDayPolicy,
    ): List<LocalDate> {
        val termStart = timingProfile.termStartLocalDate()
        // 没有开学日期时只剩临时调课这类带具体日期的安排，常规课程排不出日期
        val regularDates = if (termStart == null) {
            emptyList()
        } else {
            schedule.dailySchedules
                .flatMap { it.courses }
                .flatMap { course ->
                    course.termWeekNumbers().map { week ->
                        termWeekDate(termStart, week, course.time.dayOfWeek)
                    }
                }
        }
        val overrideTargetDates = temporaryScheduleOverrides.flatMap { it.targetDates() }
        return (regularDates + overrideTargetDates)
            .distinct()
            .filterNot { it.isBefore(fromDate) }
            .filterNot { date ->
                dayPolicy.suppresses(date, resolveScheduleDay(date, temporaryScheduleOverrides, holidayCalendar))
            }
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
        val titleContent = buildTitleContent(daily, rule.advanceMinutes)
        val messageContent = buildMessageContent(daily)
        return ReminderPlan(
            planId = "${rule.ruleId}_${daily.course.id}_$trigger",
            ruleId = rule.ruleId,
            pluginId = rule.pluginId,
            title = titleContent.stableText(),
            message = messageContent.stableText(),
            titleContent = titleContent,
            messageContent = messageContent,
            triggerAtMillis = trigger,
            ringtoneUri = rule.ringtoneUri,
            courseId = daily.course.id,
        )
    }

    private fun buildTitleContent(
        daily: DailyReminderObject,
        advanceMinutes: Int,
    ): ReminderNotificationTitle = ReminderNotificationTitle(
        dayOfWeek = daily.date.dayOfWeek.value,
        startTime = daily.slot.startTime,
        courseTitle = daily.course.title,
        advanceMinutes = advanceMinutes,
    )

    private fun buildMessageContent(daily: DailyReminderObject): ReminderNotificationMessage =
        ReminderNotificationMessage(
            month = daily.date.monthValue,
            dayOfMonth = daily.date.dayOfMonth,
            dayOfWeek = daily.date.dayOfWeek.value,
            startTime = daily.slot.startTime,
            endTime = daily.slot.endTime,
            startNode = daily.course.time.startNode,
            endNode = daily.course.time.endNode,
            location = daily.course.location,
        )

    private fun reminderSlot(
        course: CourseItem,
        timingProfile: TermTimingProfile,
        label: String,
    ): ClassSlotTime? {
        val placeholder = placeholderSlot(course, label)
        return if (course.reminderOnly) {
            placeholder ?: timingProfile.findSlot(course.time.startNode, course.time.endNode)
        } else {
            timingProfile.findSlot(course.time.startNode, course.time.endNode) ?: placeholder
        }
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
}
