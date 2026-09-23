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
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.startLocalTimeOrNull
import com.x500x.cursimple.core.kernel.model.targetDates
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
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
        val zone = BeijingTime.zone
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
                            .mapNotNull { daily -> buildPlan(daily, rule, zone) }
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
        val allCourses = schedule.dailySchedules.flatMap { it.courses }
        // 从别天挪到这天的课；该不该上已按它原本那天判过，不再按本周过滤
        val movedIn = coursesMovedTo(
            date = targetDate,
            overrides = temporaryScheduleOverrides,
            courseById = { id -> allCourses.firstOrNull { it.id == id } },
            isOriginallyActive = { course, from -> course.isActiveInTermWeek(resolveTermWeek(termStart, from)) },
        )
        // 放假日不上常规课，但调课可以推翻放假：只留被挪过来的那几门
        if (dayPolicy.suppresses(targetDate, day)) {
            return movedIn
                .filterNot { isCourseTemporarilyCancelled(targetDate, it, temporaryScheduleOverrides) }
                .mapNotNull { course -> course.toDailyObject(timingProfile, targetDate) }
                .sortedWith(compareBy<DailyReminderObject> { it.slot.startTime }.thenBy { it.course.title })
        }
        return allCourses
            .asSequence()
            // 被单独挪到别天的课，这天不再提醒
            .filterNot { isCourseMovedAwayFrom(targetDate, it, temporaryScheduleOverrides) }
            // 只调某几节时这天同时挂着两天的课，逐门问过来源日才知道各自算哪天、按哪周
            .mapNotNull { course ->
                temporaryScheduleCourseSourceDate(
                    date = targetDate,
                    course = course,
                    sourceDate = day.sourceDate,
                    overrides = temporaryScheduleOverrides,
                )?.let { course to it }
            }
            .filter { (course, courseSource) -> course.isActiveInTermWeek(resolveTermWeek(termStart, courseSource)) }
            .map { (course, _) -> course }
            .plus(movedIn)
            .filterNot { isCourseTemporarilyCancelled(targetDate, it, temporaryScheduleOverrides) }
            .mapNotNull { course -> course.toDailyObject(timingProfile, targetDate) }
            .sortedWith(compareBy<DailyReminderObject> { it.slot.startTime }.thenBy { it.course.title })
            .toList()
    }

    /** 课程配上它的节次标签与节次时间；没有标签或对不上节次的课不产生提醒对象。 */
    private fun CourseItem.toDailyObject(
        timingProfile: TermTimingProfile,
        date: LocalDate,
    ): DailyReminderObject? {
        val label = reminderSlotLabel(timingProfile)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val slot = reminderSlot(this, timingProfile, label) ?: return null
        return DailyReminderObject(slotLabel = label, course = this, date = date, slot = slot)
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
    ): ReminderPlan? {
        // 时间串非法时跳过这一节，不让异常掀翻整轮同步
        val startTime = daily.slot.startLocalTimeOrNull() ?: return null
        val classStart = LocalDateTime.of(daily.date, startTime)
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
            // 规则按标签匹配到的课，标签就是它所在时段的名字；横跨几个时段时只写节号
            slotLabel = daily.slotLabel.trim().takeIf {
                daily.slot.startNode <= daily.course.time.startNode && daily.slot.endNode >= daily.course.time.endNode
            }.orEmpty(),
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
