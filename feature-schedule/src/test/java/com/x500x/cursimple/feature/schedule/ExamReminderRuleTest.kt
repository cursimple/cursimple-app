package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.reminder.ReminderPlanner
import com.x500x.cursimple.core.reminder.model.ReminderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamReminderRuleTest {

    private val termStart = LocalDate.of(2026, 3, 2)

    private val timingProfile = TermTimingProfile(
        termStartDate = termStart.toString(),
        slotTimes = listOf(
            ClassSlotTime(startNode = 1, endNode = 2, startTime = "08:00", endTime = "09:40", label = "第一节课"),
            ClassSlotTime(startNode = 3, endNode = 4, startTime = "10:00", endTime = "11:40", label = "第二节课"),
        ),
    )

    private val morningCourse = CourseItem(
        id = "course-math",
        title = "高等数学",
        weeks = (1..16).toList(),
        category = CourseCategory.Course,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    private val morningExam = CourseItem(
        id = "exam-math",
        title = "高等数学期末考",
        weeks = listOf(8),
        category = CourseCategory.Exam,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
    )

    private val afternoonExam = CourseItem(
        id = "exam-english",
        title = "大学英语期末考",
        weeks = listOf(8),
        category = CourseCategory.Exam,
        time = CourseTimeSlot(dayOfWeek = 3, startNode = 3, endNode = 4),
    )

    private fun scheduleOf(vararg courses: CourseItem): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-03-01T00:00:00+08:00",
        dailySchedules = courses.groupBy { it.time.dayOfWeek }.map { (day, items) ->
            DailySchedule(dayOfWeek = day, courses = items)
        },
    )

    private fun examRule(course: CourseItem, advanceMinutes: Int = 40): ReminderRule =
        buildExamReminderRule(
            existing = null,
            course = course,
            pluginId = "plugin-a",
            advanceMinutes = advanceMinutes,
            ringtoneUri = null,
            now = "2026-03-01T00:00:00+08:00",
            newRuleId = "rule-${course.id}",
        )

    private fun expand(rules: List<ReminderRule>, schedule: TermSchedule) =
        ReminderPlanner().expandRules(
            rules = rules,
            schedule = schedule,
            timingProfile = timingProfile,
            fromDate = termStart,
        )

    @Test
    fun `exam reminder never fires for an ordinary course sharing the same slot`() {
        val schedule = scheduleOf(morningCourse, morningExam)

        val plans = expand(listOf(examRule(morningExam)), schedule)

        assertTrue(plans.isNotEmpty())
        assertFalse(plans.any { it.courseId == morningCourse.id })
        assertTrue(plans.all { it.courseId == morningExam.id })
    }

    @Test
    fun `exam reminder only fires on the exam date`() {
        val schedule = scheduleOf(morningCourse, morningExam)

        val plans = expand(listOf(examRule(morningExam)), schedule)

        // 第 8 周周三：2026-03-02 起第 8 周的周一是 2026-04-20
        assertEquals(1, plans.size)
        assertEquals(
            LocalDate.of(2026, 4, 22),
            java.time.Instant.ofEpochMilli(plans.single().triggerAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate(),
        )
    }

    @Test
    fun `two exams on the same day each get their own reminder`() {
        val schedule = scheduleOf(morningCourse, morningExam, afternoonExam)

        val plans = expand(listOf(examRule(morningExam), examRule(afternoonExam)), schedule)

        assertEquals(
            setOf(morningExam.id, afternoonExam.id),
            plans.mapNotNull { it.courseId }.toSet(),
        )
    }

    @Test
    fun `muted exam keeps its mute across a re-save and produces no plan`() {
        val schedule = scheduleOf(morningCourse, morningExam)
        val muted = examRule(morningExam).copy(
            mutedCourseIds = listOf(morningExam.id),
            enabled = false,
        )

        val resaved = buildExamReminderRule(
            existing = muted,
            course = morningExam,
            pluginId = "plugin-a",
            advanceMinutes = 30,
            ringtoneUri = null,
            now = "2026-03-05T00:00:00+08:00",
            newRuleId = "unused",
        )

        assertEquals(listOf(morningExam.id), resaved.mutedCourseIds)
        assertFalse(resaved.enabled)
        assertEquals(30, resaved.advanceMinutes)
        assertTrue(expand(listOf(resaved), schedule).isEmpty())
    }

    @Test
    fun `exam rule carries the course id and is recognised as an exam rule`() {
        val rule = examRule(morningExam)

        assertEquals(morningExam.id, rule.courseId)
        assertTrue(rule.isExamReminderRule())
        assertFalse(rule.isLegacyExamLabelRule())
        assertTrue(examReminderEnabled(listOf(rule)))
        assertFalse(examReminderEnabled(emptyList()))
    }

    @Test
    fun `exam status message reports covered and unresolved exams`() {
        assertEquals("已关闭考试提醒", examReminderStatusMessage(false, 0, listOf("被忽略")))
        assertEquals("已为 2 场考试开启提醒", examReminderStatusMessage(true, 2, emptyList()))
        assertEquals("已开启考试提醒；课表里暂时没有考试", examReminderStatusMessage(true, 0, emptyList()))
        assertTrue(examReminderStatusMessage(true, 0, listOf("物理")).startsWith("考试提醒开启失败："))
        assertTrue(examReminderStatusMessage(true, 1, listOf("物理")).contains("物理"))
    }
}
