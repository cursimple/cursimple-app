package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.model.targetDates
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlanner {
    private val firstCourseEvaluator = FirstCourseRuleEvaluator()

    fun expandRule(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate = BeijingTime.today(),
        temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
        customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
    ): List<ReminderPlan> {
        if (rule.scopeType == ReminderScopeType.FirstCourseOfPeriod) {
            val zone = ZoneId.systemDefault()
            return firstCourseEvaluator.expand(
                rule = rule,
                schedule = schedule,
                timingProfile = timingProfile,
                fromDate = fromDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                customOccupancies = customOccupancies,
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
        val zone = ZoneId.systemDefault()
        return courseOccurrenceDates(
            course = course,
            termStart = termStart,
            fromDate = fromDate,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
        ).map { courseDate ->
            buildPlan(rule, course, courseDate, slot, zone)
        }
    }

    private fun courseOccurrenceDates(
        course: CourseItem,
        termStart: LocalDate,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<LocalDate> {
        val weeks = course.weeks.ifEmpty { (1..60).toList() }
        val regularDates = weeks.map { week ->
            termStart
                .plusWeeks((week - 1).toLong())
                .plusDays((course.time.dayOfWeek - 1).toLong())
        }
        val overrideTargetDates = temporaryScheduleOverrides.flatMap { it.targetDates() }
        return (regularDates + overrideTargetDates)
            .distinct()
            .filterNot { it.isBefore(fromDate) }
            .filter { date ->
                val sourceDate = resolveTemporaryScheduleSourceDate(date, temporaryScheduleOverrides)
                sourceDate.dayOfWeek.value == course.time.dayOfWeek &&
                    course.isActiveOnSourceDate(termStart, sourceDate) &&
                    !isCourseTemporarilyCancelled(date, course, temporaryScheduleOverrides)
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
        return ReminderPlan(
            planId = "${rule.ruleId}_${course.id}_$trigger",
            ruleId = rule.ruleId,
            pluginId = rule.pluginId,
            title = buildTitle(course, courseDate, slot, rule.advanceMinutes, titlePeriod),
            message = buildMessage(course, courseDate, slot),
            triggerAtMillis = trigger,
            ringtoneUri = rule.ringtoneUri,
            courseId = course.id,
        )
    }

    private fun buildTitle(
        course: CourseItem,
        courseDate: LocalDate,
        slot: ClassSlotTime,
        advanceMinutes: Int,
        period: ReminderDayPeriod? = null,
    ): String {
        val weekday = weekdayName(course.time.dayOfWeek)
        val startTime = slot.startTime
        val advance = if (advanceMinutes > 0) "（提前${advanceMinutes}分钟）" else ""
        val prefix = when (period) {
            ReminderDayPeriod.Morning -> "上午首次课："
            ReminderDayPeriod.Afternoon -> "下午首次课："
            ReminderDayPeriod.Evening -> "晚上首次课："
            null -> ""
        }
        val title = if (course.category == CourseCategory.Exam) "考试：${course.title}" else course.title
        return "${weekday} ${startTime} $prefix$title$advance"
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
            ReminderScopeType.Exam -> course.category == CourseCategory.Exam && course.id !in mutedCourseIds
            ReminderScopeType.FirstCourseOfPeriod -> false
        }
    }

    private fun CourseItem.isActiveOnSourceDate(termStart: LocalDate, sourceDate: LocalDate): Boolean {
        if (weeks.isEmpty()) return true
        return resolveTermWeek(termStart, sourceDate) in weeks
    }

    private fun resolveTermWeek(termStart: LocalDate, date: LocalDate): Int =
        java.time.temporal.ChronoUnit.WEEKS.between(
            termStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
            date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
        ).toInt() + 1
}
