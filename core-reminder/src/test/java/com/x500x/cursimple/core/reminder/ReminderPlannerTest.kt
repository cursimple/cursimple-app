package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderAction
import com.x500x.cursimple.core.reminder.model.ReminderActionType
import com.x500x.cursimple.core.reminder.model.ReminderCondition
import com.x500x.cursimple.core.reminder.model.ReminderConditionMode
import com.x500x.cursimple.core.reminder.model.ReminderConditionType
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.ReminderTimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()

    @Test
    fun labelRuleRunsActionsWhenAllConditionsMatch() {
        val plans = planner.expandRule(
            rule = labelRule(
                conditions = listOf(ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists)),
                actions = listOf(ReminderLabelAction("第二节课", ReminderLabelActionType.Remind)),
            ),
            schedule = labelSchedule(),
            timingProfile = labelProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
        )

        assertEquals(listOf("physics"), plans.map { it.courseId })
    }

    @Test
    fun labelRuleDoesNotRunWhenAnyConditionFails() {
        val plans = planner.expandRule(
            rule = labelRule(
                conditions = listOf(
                    ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists),
                    ReminderLabelCondition("第二节课", ReminderLabelPresence.Absent),
                ),
                actions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
            ),
            schedule = labelSchedule(),
            timingProfile = labelProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
        )

        assertEquals(emptyList<String>(), plans.map { it.courseId })
    }

    @Test
    fun labelRulesAreOrRelatedAndSkipWins() {
        val remindFirst = labelRule(
            ruleId = "remind-first",
            conditions = listOf(ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists)),
            actions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
        )
        val skipFirst = labelRule(
            ruleId = "skip-first",
            conditions = listOf(ReminderLabelCondition("第二节课", ReminderLabelPresence.Exists)),
            actions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Skip)),
        )

        val plans = planner.expandRules(
            rules = listOf(remindFirst, skipFirst),
            schedule = labelSchedule(),
            timingProfile = labelProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
        )

        assertEquals(emptyList<String>(), plans.map { it.courseId })
    }

    @Test
    fun placeholderCourseCanSatisfyConditionAndBeReminderTarget() {
        val schedule = labelSchedule(
            CourseItem(
                id = "placeholder",
                title = "早自习",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 9, endNode = 9),
                reminderOnly = true,
                slotLabelOverride = "早自习",
                reminderStartTime = "07:10",
                reminderEndTime = "07:50",
            ),
        )
        val plans = planner.expandRule(
            rule = labelRule(
                conditions = listOf(ReminderLabelCondition("早自习", ReminderLabelPresence.Exists)),
                actions = listOf(ReminderLabelAction("早自习", ReminderLabelActionType.Remind)),
            ),
            schedule = schedule,
            timingProfile = labelProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
        )

        assertEquals(listOf("placeholder"), plans.map { it.courseId })
        assertEquals(true, plans.single().message.contains("07:10-07:50"))
    }

    @Test
    fun duplicateLabelRemindersAreDeduplicated() {
        val plans = planner.expandRules(
            rules = listOf(
                labelRule(ruleId = "a"),
                labelRule(ruleId = "b"),
            ),
            schedule = labelSchedule(),
            timingProfile = labelProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
        )

        assertEquals(1, plans.count { it.courseId == "math" })
    }

    @Test
    fun examRuleExpandsOnlyExamCourses() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            category = CourseCategory.Course,
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                        CourseItem(
                            id = "exam-math",
                            title = "高等数学期末",
                            weeks = listOf(1),
                            category = CourseCategory.Exam,
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 3, endNode = 4),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
                ClassSlotTime(3, 4, "10:00", "11:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "exam",
            pluginId = "demo",
            scopeType = ReminderScopeType.Exam,
            advanceMinutes = 40,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(listOf("exam-math"), plans.map { it.courseId })
        assertEquals(true, plans.single().title.contains("考试：高等数学期末"))
    }

    @Test
    fun examRuleSkipsMutedExamCourses() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "exam-a",
                            title = "高等数学期末",
                            weeks = listOf(1),
                            category = CourseCategory.Exam,
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                        CourseItem(
                            id = "exam-b",
                            title = "线性代数期末",
                            weeks = listOf(1),
                            category = CourseCategory.Exam,
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 3, endNode = 4),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
                ClassSlotTime(3, 4, "10:00", "11:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "exam",
            pluginId = "demo",
            scopeType = ReminderScopeType.Exam,
            mutedCourseIds = listOf("exam-a"),
            advanceMinutes = 40,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(listOf("exam-b"), plans.map { it.courseId })
    }

    @Test
    fun expandsTimeSlotRuleAcrossWeek() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1, 2),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "r1",
            pluginId = "demo",
            scopeType = ReminderScopeType.TimeSlot,
            dayOfWeek = 1,
            startNode = 1,
            endNode = 2,
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(2, plans.size)
    }

    @Test
    fun temporaryOverrideMovesReminderToActualMakeupDate() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "r1",
            pluginId = "demo",
            scopeType = ReminderScopeType.SingleCourse,
            courseId = "math",
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = profile,
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "holiday",
                    startDate = "2026-02-23",
                    endDate = "2026-02-23",
                    sourceDayOfWeek = 7,
                ),
                TemporaryScheduleOverride(
                    id = "makeup",
                    startDate = "2026-02-28",
                    endDate = "2026-02-28",
                    sourceDayOfWeek = 1,
                ),
            ),
        )

        assertEquals(1, plans.size)
        assertEquals(true, plans.single().message.startsWith("2月28日"))
    }

    @Test
    fun temporaryCancelSuppressesMatchingCourseReminder() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35")),
        )
        val rule = ReminderRule(
            ruleId = "r1",
            pluginId = "demo",
            scopeType = ReminderScopeType.SingleCourse,
            courseId = "math",
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = profile,
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "cancel",
                    type = TemporaryScheduleOverrideType.CancelCourse,
                    targetDate = "2026-02-23",
                    cancelStartNode = 1,
                    cancelEndNode = 2,
                    cancelCourseId = "math",
                ),
            ),
        )

        assertEquals(0, plans.size)
    }

    @Test
    fun firstCourseOfPeriodRuleKeepsOnlyEarliestCourseInPeriod() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "late",
                            title = "线性代数",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 3, endNode = 4),
                        ),
                        CourseItem(
                            id = "early",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
                ClassSlotTime(3, 4, "10:00", "11:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(1, plans.size)
        assertEquals("early", plans.single().courseId)
    }

    @Test
    fun firstCourseMutedPreludeSuppressesPeriodWhenPreludeCourseExists() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "study",
                            title = "早自习",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
                        ),
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 1, "07:20", "07:50"),
                ClassSlotTime(2, 2, "08:00", "08:45"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            periodStartNode = 1,
            periodEndNode = 4,
            mutedNodeRanges = listOf(ReminderNodeRange(1, 1)),
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(0, plans.size)
    }

    @Test
    fun firstCourseMutedPreludeAllowsLaterCourseWhenPreludeIsEmpty() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 1, "07:20", "07:50"),
                ClassSlotTime(2, 2, "08:00", "08:45"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            periodStartNode = 1,
            periodEndNode = 4,
            mutedNodeRanges = listOf(ReminderNodeRange(1, 1)),
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(1, plans.size)
        assertEquals("math", plans.single().courseId)
    }

    @Test
    fun flexibleFirstCourseWithOccupancyAndFirstNodeCourseSkipsMorning() {
        val schedule = morningSchedule(
            CourseItem(
                id = "first",
                title = "大学英语",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            ),
            CourseItem(
                id = "second",
                title = "高等数学",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
            ),
        )
        val rule = flexibleMorningRule(
            ruleId = "early-study-first-node-skip",
            conditions = listOf(
                ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = "early-study"),
                ReminderCondition(ReminderConditionType.CourseExistsInNodes, nodeRange = ReminderNodeRange(1, 1)),
            ),
            actions = listOf(ReminderAction(ReminderActionType.Skip)),
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = morningProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            customOccupancies = listOf(earlyStudyOccupancy()),
        )

        assertEquals(0, plans.size)
    }

    @Test
    fun flexibleFirstCourseWithoutOccupancyRemindsFirstNodeCourse() {
        val schedule = morningSchedule(
            CourseItem(
                id = "first",
                title = "大学英语",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            ),
            CourseItem(
                id = "second",
                title = "高等数学",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
            ),
        )
        val rule = flexibleMorningRule(
            ruleId = "no-early-study",
            conditions = listOf(
                ReminderCondition(ReminderConditionType.OccupancyAbsent, occupancyId = "early-study"),
            ),
            actions = listOf(ReminderAction(ReminderActionType.RemindFirstCandidate)),
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = morningProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            customOccupancies = emptyList(),
        )

        assertEquals(1, plans.size)
        assertEquals("first", plans.single().courseId)
    }

    @Test
    fun flexibleFirstCourseWithOccupancyAndEmptyFirstNodeContinuesToSecondNode() {
        val schedule = morningSchedule(
            CourseItem(
                id = "second",
                title = "高等数学",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
            ),
        )
        val rule = flexibleMorningRule(
            ruleId = "early-study-empty-first-node",
            conditions = listOf(
                ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = "early-study"),
                ReminderCondition(ReminderConditionType.CourseAbsentInNodes, nodeRange = ReminderNodeRange(1, 1)),
            ),
            actions = listOf(ReminderAction(ReminderActionType.ContinueAfterNode, afterNode = 1)),
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = morningProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            customOccupancies = listOf(earlyStudyOccupancy()),
        )

        assertEquals(1, plans.size)
        assertEquals("second", plans.single().courseId)
    }

    @Test
    fun flexibleFirstCourseOccupancyTimeOverlapUsesCustomTimeRange() {
        val schedule = morningSchedule(
            CourseItem(
                id = "first",
                title = "大学英语",
                weeks = listOf(1),
                time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            ),
        )
        val rule = flexibleMorningRule(
            ruleId = "time-overlap",
            conditions = listOf(
                ReminderCondition(ReminderConditionType.OccupancyOverlapsCourse, occupancyId = "early-study"),
            ),
            actions = listOf(ReminderAction(ReminderActionType.Skip)),
        )
        val overlappingOccupancy = earlyStudyOccupancy().copy(
            timeRange = ReminderTimeRange("08:10", "08:20"),
            linkedNodeRange = null,
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = morningProfile(),
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            customOccupancies = listOf(overlappingOccupancy),
        )

        assertEquals(0, plans.size)
    }

    private fun morningSchedule(vararg courses: CourseItem): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-04-27T08:00:00+08:00",
        dailySchedules = listOf(DailySchedule(dayOfWeek = 1, courses = courses.toList())),
    )

    private fun morningProfile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-02-23",
        slotTimes = listOf(
            ClassSlotTime(1, 1, "08:00", "08:45"),
            ClassSlotTime(2, 2, "09:00", "09:45"),
            ClassSlotTime(3, 4, "10:00", "11:35"),
        ),
    )

    private fun earlyStudyOccupancy(): ReminderCustomOccupancy = ReminderCustomOccupancy(
        occupancyId = "early-study",
        pluginId = "demo",
        name = "早自习",
        timeRange = ReminderTimeRange("07:10", "07:50"),
        daysOfWeek = listOf(1, 2, 3, 4, 5),
        linkedNodeRange = ReminderNodeRange(1, 1),
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun flexibleMorningRule(
        ruleId: String,
        conditions: List<ReminderCondition>,
        actions: List<ReminderAction>,
    ): ReminderRule = ReminderRule(
        ruleId = ruleId,
        pluginId = "demo",
        scopeType = ReminderScopeType.FirstCourseOfPeriod,
        displayName = "上午首课提醒",
        firstCourseCandidate = FirstCourseCandidateScope(nodeRange = ReminderNodeRange(1, 4)),
        conditionMode = ReminderConditionMode.All,
        conditions = conditions,
        actions = actions,
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun labelRule(
        ruleId: String = "label-rule",
        conditions: List<ReminderLabelCondition> = listOf(ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists)),
        actions: List<ReminderLabelAction> = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
    ): ReminderRule = ReminderRule(
        ruleId = ruleId,
        pluginId = "demo",
        scopeType = ReminderScopeType.LabelRule,
        displayName = "Label rule",
        labelConditions = conditions,
        labelActions = actions,
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun labelSchedule(vararg extraCourses: CourseItem): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-02-23T00:00:00+08:00",
        dailySchedules = listOf(
            DailySchedule(
                dayOfWeek = 1,
                courses = listOf(
                    CourseItem(
                        id = "math",
                        title = "高等数学",
                        weeks = listOf(1),
                        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                    ),
                    CourseItem(
                        id = "physics",
                        title = "大学物理",
                        weeks = listOf(1),
                        time = CourseTimeSlot(dayOfWeek = 1, startNode = 3, endNode = 4),
                    ),
                ) + extraCourses,
            ),
        ),
    )

    private fun labelProfile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-02-23",
        slotTimes = listOf(
            ClassSlotTime(1, 2, "08:00", "09:35", "第一节课"),
            ClassSlotTime(3, 4, "10:00", "11:35", "第二节课"),
        ),
    )
}
