package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.reminder.ReminderPlanner
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CourseReminderRuleTest {

    private val termStart = LocalDate.of(2026, 3, 2)

    private val timingProfile = TermTimingProfile(
        termStartDate = termStart.toString(),
        slotTimes = listOf(
            ClassSlotTime(startNode = 1, endNode = 2, startTime = "08:00", endTime = "09:40", label = "第一节课"),
            ClassSlotTime(startNode = 3, endNode = 4, startTime = "10:00", endTime = "11:40", label = "第二节课"),
        ),
    )

    private val math = CourseItem(
        id = "course-math",
        title = "高等数学",
        weeks = (1..4).toList(),
        category = CourseCategory.Course,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    /** 与高数同一天同节次的另一门课。 */
    private val physics = CourseItem(
        id = "course-physics",
        title = "大学物理",
        weeks = (1..4).toList(),
        category = CourseCategory.Course,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    /** 另一天但共用同一个节次名的课。 */
    private val english = CourseItem(
        id = "course-english",
        title = "大学英语",
        weeks = (1..4).toList(),
        category = CourseCategory.Course,
        time = CourseTimeSlot(dayOfWeek = 5, startNode = 1, endNode = 2),
    )

    private val mathExam = CourseItem(
        id = "exam-math",
        title = "高等数学",
        weeks = listOf(4),
        category = CourseCategory.Exam,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    private fun scheduleOf(vararg courses: CourseItem): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-03-01T00:00:00+08:00",
        dailySchedules = courses.groupBy { it.time.dayOfWeek }.map { (day, items) ->
            DailySchedule(dayOfWeek = day, courses = items)
        },
    )

    private fun courseRule(course: CourseItem, advanceMinutes: Int = 20): ReminderRule =
        buildCourseReminderRule(
            existing = null,
            course = course,
            pluginId = "plugin-a",
            advanceMinutes = advanceMinutes,
            ringtoneUri = null,
            now = "2026-03-01T00:00:00+08:00",
            newRuleId = "rule-${course.id}",
        )

    /** 修复前 createLabelRuleForCourse 产出的规则形状。 */
    private fun legacyLabelRule(course: CourseItem, slotLabel: String): ReminderRule = ReminderRule(
        ruleId = "legacy-${course.id}",
        pluginId = "plugin-a",
        scopeType = ReminderScopeType.LabelRule,
        displayName = "提醒 ${course.title}",
        advanceMinutes = 20,
        labelConditions = listOf(ReminderLabelCondition(slotLabel, ReminderLabelPresence.Exists)),
        labelActions = listOf(ReminderLabelAction(slotLabel, ReminderLabelActionType.Remind)),
        createdAt = "2026-03-01T00:00:00+08:00",
        updatedAt = "2026-03-01T00:00:00+08:00",
    )

    private fun expand(rules: List<ReminderRule>, schedule: TermSchedule) =
        ReminderPlanner().expandRules(
            rules = rules,
            schedule = schedule,
            timingProfile = timingProfile,
            fromDate = termStart,
        )

    @Test
    fun `course reminder never fires for another course in the same slot on the same day`() {
        val schedule = scheduleOf(math, physics)

        val plans = expand(listOf(courseRule(math)), schedule)

        assertTrue(plans.isNotEmpty())
        assertFalse(plans.any { it.courseId == physics.id })
        assertTrue(plans.all { it.courseId == math.id })
    }

    @Test
    fun `legacy slot label rule is what dragged the other course in`() {
        val schedule = scheduleOf(math, physics)

        val plans = expand(listOf(legacyLabelRule(math, "第一节课")), schedule)

        assertTrue(plans.any { it.courseId == physics.id })
    }

    @Test
    fun `course reminder skips other weekdays sharing the slot label`() {
        val schedule = scheduleOf(math, english)

        val plans = expand(listOf(courseRule(math)), schedule)

        assertTrue(plans.isNotEmpty())
        assertTrue(plans.all { it.courseId == math.id })
    }

    @Test
    fun `course reminder fires on every week the course occurs`() {
        val schedule = scheduleOf(math, physics)

        val plans = expand(listOf(courseRule(math)), schedule)

        assertEquals(math.weeks.size, plans.size)
    }

    @Test
    fun `course reminder does not fire for an exam sharing the title and slot`() {
        val schedule = scheduleOf(math, mathExam)

        val plans = expand(listOf(courseRule(math)), schedule)

        assertTrue(plans.all { it.courseId == math.id })
    }

    @Test
    fun `course rule carries the course id and stays out of the exam bucket`() {
        val rule = courseRule(math)

        assertEquals(math.id, rule.courseId)
        assertEquals(ReminderScopeType.FirstCourseOfPeriod, rule.scopeType)
        assertTrue(rule.isCourseReminderRule())
        assertFalse(rule.isExamReminderRule())
        assertFalse(examReminderEnabled(listOf(rule)))
        assertTrue(rule.firstCourseCandidate?.daysOfWeek.orEmpty().isEmpty())
        assertTrue(rule.firstCourseCandidate?.weeks.orEmpty().isEmpty())
    }

    @Test
    fun `a course reminder on an exam item is not mistaken for the exam reminder`() {
        val rule = courseRule(mathExam)

        assertTrue(rule.isCourseReminderRule())
        assertFalse(rule.isExamReminderRule())
    }

    @Test
    fun `re-saving a course reminder keeps the rule id and updates the advance`() {
        val existing = courseRule(math)

        val resaved = buildCourseReminderRule(
            existing = existing,
            course = math,
            pluginId = "plugin-a",
            advanceMinutes = 45,
            ringtoneUri = "content://ringtone",
            now = "2026-03-05T00:00:00+08:00",
            newRuleId = "unused",
        )

        assertEquals(existing.ruleId, resaved.ruleId)
        assertEquals(existing.createdAt, resaved.createdAt)
        assertEquals(45, resaved.advanceMinutes)
        assertEquals("content://ringtone", resaved.ringtoneUri)
    }

    @Test
    fun `legacy course rule is upgraded in place and stops hitting the other course`() {
        val schedule = scheduleOf(math, physics)
        val legacy = legacyLabelRule(math, "第一节课").copy(ringtoneUri = "content://ringtone")

        val migrated = planLegacyCourseReminderMigration(
            rules = listOf(legacy),
            courses = listOf(math, physics),
            timingProfile = timingProfile,
            now = "2026-03-05T00:00:00+08:00",
        )

        assertEquals(1, migrated.size)
        val rule = migrated.single()
        assertEquals(legacy.ruleId, rule.ruleId)
        assertEquals(legacy.createdAt, rule.createdAt)
        assertEquals(legacy.advanceMinutes, rule.advanceMinutes)
        assertEquals("content://ringtone", rule.ringtoneUri)
        assertTrue(rule.isCourseReminderRule())
        assertTrue(rule.labelActions.isEmpty())

        val plans = expand(listOf(rule), schedule)
        assertTrue(plans.isNotEmpty())
        assertTrue(plans.all { it.courseId == math.id })
    }

    @Test
    fun `migration keeps a disabled legacy rule disabled`() {
        val legacy = legacyLabelRule(math, "第一节课").copy(enabled = false)

        val migrated = planLegacyCourseReminderMigration(
            rules = listOf(legacy),
            courses = listOf(math),
            timingProfile = timingProfile,
            now = "2026-03-05T00:00:00+08:00",
        )

        assertFalse(migrated.single().enabled)
    }

    @Test
    fun `migration leaves slot rules and unmatched rules alone`() {
        val slotRule = legacyLabelRule(math, "第一节课").copy(displayName = "提醒 第一节课")
        val orphan = legacyLabelRule(math, "第一节课").copy(displayName = "提醒 已退选的课")

        assertNull(slotRule.legacyCourseReminderTarget(listOf(math, physics), timingProfile))
        assertNull(orphan.legacyCourseReminderTarget(listOf(math, physics), timingProfile))
        assertTrue(
            planLegacyCourseReminderMigration(
                rules = listOf(slotRule, orphan),
                courses = listOf(math, physics),
                timingProfile = timingProfile,
                now = "2026-03-05T00:00:00+08:00",
            ).isEmpty(),
        )
    }

    @Test
    fun `migration ignores legacy exam rules`() {
        val legacyExam = legacyLabelRule(math, "第一节课")
            .copy(displayName = "$EXAM_RULE_PREFIX${math.title}")

        assertTrue(legacyExam.isLegacyExamLabelRule())
        assertNull(legacyExam.legacyCourseReminderTarget(listOf(math), timingProfile))
    }

    @Test
    fun `migration is idempotent`() {
        val legacy = legacyLabelRule(math, "第一节课")

        val once = planLegacyCourseReminderMigration(
            rules = listOf(legacy),
            courses = listOf(math),
            timingProfile = timingProfile,
            now = "2026-03-05T00:00:00+08:00",
        )
        val twice = planLegacyCourseReminderMigration(
            rules = once,
            courses = listOf(math),
            timingProfile = timingProfile,
            now = "2026-03-06T00:00:00+08:00",
        )

        assertTrue(twice.isEmpty())
    }
}
