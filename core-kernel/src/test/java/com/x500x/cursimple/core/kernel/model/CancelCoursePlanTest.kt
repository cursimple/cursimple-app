package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 临时取消改成选一天、逐门点停课后，停课与恢复对调课列表的改动。 */
class CancelCoursePlanTest {

    private val tue = LocalDate.of(2026, 9, 22)
    private val math = course("math", start = 1, end = 2)
    private val physics = course("physics", start = 1, end = 2)
    private val web = course("web", start = 6, end = 6)
    private val dayCourses = listOf(math, physics, web)

    private fun course(id: String, start: Int, end: Int) = CourseItem(
        id = id,
        title = id,
        weeks = (1..16).toList(),
        time = CourseTimeSlot(dayOfWeek = 2, startNode = start, endNode = end),
    )

    private fun rangeRule(id: String, start: Int, end: Int) = TemporaryScheduleOverride(
        id = id,
        type = TemporaryScheduleOverrideType.CancelCourse,
        targetDate = tue.toString(),
        cancelStartNode = start,
        cancelEndNode = end,
    )

    private fun apply(overrides: List<TemporaryScheduleOverride>, plan: CancelCoursePlan) =
        overrides.filterNot { it.id in plan.removeIds } + plan.upserts

    @Test
    fun `停一门课只停它自己，同一时段的别的课照上`() {
        val plan = planCancelCourse(tue, math, newId = { "new" })

        val rule = plan.upserts.single()
        assertEquals("math", rule.cancelCourseId)
        assertEquals(1, rule.cancelStartNode)
        assertEquals(2, rule.cancelEndNode)
        assertTrue(isCourseTemporarilyCancelled(tue, math, plan.upserts))
        assertFalse(isCourseTemporarilyCancelled(tue, physics, plan.upserts))
        assertFalse(isCourseTemporarilyCancelled(tue.plusDays(7), math, plan.upserts))
    }

    @Test
    fun `恢复时删掉只针对它的那条规则`() {
        val existing = planCancelCourse(tue, math, newId = { "c1" }).upserts

        val plan = planRestoreCourse(tue, math, dayCourses, existing, newId = { "new" })!!

        assertEquals(listOf("c1"), plan.removeIds)
        assertTrue(plan.upserts.isEmpty())
    }

    @Test
    fun `旧版按节次停课时，恢复一门不会把同一时段的另一门也放出来`() {
        val existing = listOf(rangeRule("range", 1, 2))
        var next = 0

        val plan = planRestoreCourse(tue, math, dayCourses, existing, newId = { "n${next++}" })!!
        val after = apply(existing, plan)

        assertFalse(isCourseTemporarilyCancelled(tue, math, after))
        assertTrue(isCourseTemporarilyCancelled(tue, physics, after))
        assertFalse(isCourseTemporarilyCancelled(tue, web, after))
    }

    @Test
    fun `已经被别的规则停着的课不重复补规则`() {
        val existing = listOf(
            rangeRule("range", 1, 2),
            planCancelCourse(tue, physics, newId = { "own" }).upserts.single(),
        )

        val plan = planRestoreCourse(tue, math, dayCourses, existing, newId = { "new" })!!

        assertEquals(listOf("range"), plan.removeIds)
        assertTrue(plan.upserts.isEmpty())
    }

    @Test
    fun `跨好几天的旧规则拆不开，不给逐门恢复`() {
        val legacy = TemporaryScheduleOverride(
            id = "legacy",
            type = TemporaryScheduleOverrideType.CancelCourse,
            startDate = tue.toString(),
            endDate = tue.plusDays(2).toString(),
            cancelStartNode = 1,
            cancelEndNode = 2,
        )

        assertNull(planRestoreCourse(tue, math, dayCourses, listOf(legacy), newId = { "new" }))
    }

    @Test
    fun `当天的课单子里带着已停的课，也能只拿没停的`() {
        val overrides = planCancelCourse(tue, math, newId = { "c1" }).upserts

        val all = coursesScheduledOn(tue, dayCourses, overrides, HolidayCalendarSettings.NONE, null, includeCancelled = true)
        val active = coursesScheduledOn(tue, dayCourses, overrides, HolidayCalendarSettings.NONE, null)

        assertEquals(listOf("math", "physics", "web"), all.map { it.id })
        assertEquals(listOf("physics", "web"), active.map { it.id })
    }

    @Test
    fun `按周次过滤：这周不上的课不列出来`() {
        val termStart = tue.minusWeeks(16)
        val lateCourse = course("late", start = 3, end = 4).copy(weeks = listOf(17))
        val early = course("early", start = 3, end = 4).copy(weeks = listOf(1))

        val listed = coursesScheduledOn(tue, listOf(lateCourse, early), emptyList(), HolidayCalendarSettings.NONE, termStart)

        assertEquals(listOf("late"), listed.map { it.id })
    }
}
