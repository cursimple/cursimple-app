package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderNotificationMessage
import com.x500x.cursimple.core.reminder.model.ReminderNotificationTitle
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.isLegacy
import com.x500x.cursimple.core.reminder.model.stableText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlanner {
    private val firstCourseEvaluator = FirstCourseRuleEvaluator()
    private val labelEvaluator = LabelReminderRuleEvaluator()

    fun expandRules(
        rules: List<ReminderRule>,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate = BeijingTime.today(),
        temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
        customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
    ): List<ReminderPlan> {
        val enabledRules = rules.filter { it.enabled }
        val legacyRules = enabledRules.filter { it.scopeType.isLegacy() }
        if (legacyRules.isNotEmpty()) {
            ReminderLogger.warn(
                "reminder.planner.legacy_scope_type.skipped",
                mapOf(
                    "ruleCount" to legacyRules.size,
                    "scopeTypes" to legacyRules.map { it.scopeType.name }.distinct().sorted().joinToString(","),
                ),
            )
        }
        val labelRules = enabledRules.filter {
            it.scopeType == ReminderScopeType.LabelRule && it.labelActions.isNotEmpty()
        }
        val labelPlans = if (labelRules.isEmpty()) {
            emptyList()
        } else {
            labelEvaluator.expandAll(
                rules = labelRules,
                schedule = schedule,
                timingProfile = timingProfile,
                fromDate = fromDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                holidayCalendar = holidayCalendar,
                dayPolicy = dayPolicy,
            )
        }
        val firstCoursePlans = enabledRules
            .filter { it.scopeType == ReminderScopeType.FirstCourseOfPeriod }
            .flatMap { rule ->
                expandRule(
                    rule = rule,
                    schedule = schedule,
                    timingProfile = timingProfile,
                    fromDate = fromDate,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                    customOccupancies = customOccupancies,
                    holidayCalendar = holidayCalendar,
                    dayPolicy = dayPolicy,
                )
            }
        return (labelPlans + firstCoursePlans)
            .distinctBy { it.planId }
            .sortedBy { it.triggerAtMillis }
    }

    fun expandRule(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate = BeijingTime.today(),
        temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
        customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
    ): List<ReminderPlan> {
        if (rule.scopeType == ReminderScopeType.LabelRule) {
            return labelEvaluator.expand(
                rule = rule,
                schedule = schedule,
                timingProfile = timingProfile,
                fromDate = fromDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                holidayCalendar = holidayCalendar,
                dayPolicy = dayPolicy,
            )
        }
        if (rule.scopeType == ReminderScopeType.FirstCourseOfPeriod) {
            val zone = ZoneId.systemDefault()
            return firstCourseEvaluator.expand(
                rule = rule,
                schedule = schedule,
                timingProfile = timingProfile,
                fromDate = fromDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                customOccupancies = customOccupancies,
                holidayCalendar = holidayCalendar,
                dayPolicy = dayPolicy,
            )
                .map { target ->
                    buildPlan(
                        rule = rule,
                        course = target.course,
                        courseDate = target.courseDate,
                        slot = target.slot,
                        zone = zone,
                        titlePeriod = target.period,
                    )
                }
                .distinctBy { it.planId }
                .sortedBy { it.triggerAtMillis }
        }
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
                    holidayCalendar = holidayCalendar,
                    dayPolicy = dayPolicy,
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
        holidayCalendar: HolidayCalendarSettings,
        dayPolicy: ReminderDayPolicy,
    ): List<ReminderPlan> {
        val slot = timingProfile.findSlot(course.time.startNode, course.time.endNode) ?: return emptyList()
        // 没有开学日期就换算不出教学周，无法判断课程哪天上，不下发任何提醒
        val termStart = timingProfile.termStartLocalDate() ?: return emptyList()
        val zone = ZoneId.systemDefault()
        return courseOccurrenceDates(
            course = course,
            termStart = termStart,
            fromDate = fromDate,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
            holidayCalendar = holidayCalendar,
            dayPolicy = dayPolicy,
        ).map { courseDate ->
            buildPlan(rule, course, courseDate, slot, zone)
        }
    }

    private fun buildPlan(
        rule: ReminderRule,
        course: CourseItem,
        courseDate: LocalDate,
        slot: ClassSlotTime,
        zone: ZoneId,
        titlePeriod: ReminderDayPeriod? = rule.period,
    ): ReminderPlan {
        val classStart = LocalDateTime.of(courseDate, slot.startLocalTime())
        val trigger = classStart
            .minusMinutes(rule.advanceMinutes.toLong())
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val titleContent = buildTitleContent(course, slot, rule.advanceMinutes, titlePeriod)
        val messageContent = buildMessageContent(course, courseDate, slot)
        return ReminderPlan(
            planId = "${rule.ruleId}_${course.id}_$trigger",
            ruleId = rule.ruleId,
            pluginId = rule.pluginId,
            title = titleContent.stableText(),
            message = messageContent.stableText(),
            titleContent = titleContent,
            messageContent = messageContent,
            triggerAtMillis = trigger,
            ringtoneUri = rule.ringtoneUri,
            courseId = course.id,
        )
    }

    private fun buildTitleContent(
        course: CourseItem,
        slot: ClassSlotTime,
        advanceMinutes: Int,
        period: ReminderDayPeriod? = null,
    ): ReminderNotificationTitle = ReminderNotificationTitle(
        dayOfWeek = course.time.dayOfWeek,
        startTime = slot.startTime,
        courseTitle = course.title,
        exam = course.category == CourseCategory.Exam,
        firstCoursePeriod = period,
        advanceMinutes = advanceMinutes,
    )

    private fun buildMessageContent(
        course: CourseItem,
        courseDate: LocalDate,
        slot: ClassSlotTime,
    ): ReminderNotificationMessage = ReminderNotificationMessage(
        month = courseDate.monthValue,
        dayOfMonth = courseDate.dayOfMonth,
        dayOfWeek = course.time.dayOfWeek,
        startTime = slot.startTime,
        endTime = slot.endTime,
        startNode = course.time.startNode,
        endNode = course.time.endNode,
        location = course.location,
    )

    private fun ReminderRule.matches(course: CourseItem): Boolean {
        return when (scopeType) {
            ReminderScopeType.SingleCourse -> course.id == courseId
            ReminderScopeType.TimeSlot -> {
                course.time.dayOfWeek in 1..7 &&
                    course.time.startNode == startNode &&
                    course.time.endNode == endNode
            }
            ReminderScopeType.Exam -> course.category == CourseCategory.Exam && course.id !in mutedCourseIds
            ReminderScopeType.FirstCourseOfPeriod -> false
            ReminderScopeType.LabelRule -> false
        }
    }
}
