package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TemporaryScheduleOverrideTest {
    @Test
    fun `explicit date override resolves source date`() {
        val target = LocalDate.of(2026, 5, 6)
        val source = LocalDate.of(2026, 5, 11)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                targetDate = target.toString(),
                sourceDate = source.toString(),
            ),
        )

        assertEquals(source, resolveTemporaryScheduleSourceDate(target, overrides))
        assertEquals(1, resolveTemporaryScheduleSourceDate(target, overrides).dayOfWeek.value)
        assertTrue(resolveTemporaryScheduleSourceDate(target, overrides) != target)
    }

    @Test
    fun `single day override resolves source weekday`() {
        val date = LocalDate.of(2026, 5, 6)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                startDate = "2026-05-06",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
        )

        assertEquals(1, resolveTemporaryScheduleSourceDate(date, overrides).dayOfWeek.value)
        assertTrue(resolveTemporaryScheduleSourceDate(date, overrides) != date)
    }

    @Test
    fun `date range override covers every date in range`() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "holiday",
                startDate = "2026-05-04",
                endDate = "2026-05-06",
                sourceDayOfWeek = 5,
            ),
        )

        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 4)))
        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 5)))
        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 6)))
        assertFalse(overrides.first().containsDate(LocalDate.of(2026, 5, 7)))
    }

    @Test
    fun `overlapping overrides prefer the last rule`() {
        val date = LocalDate.of(2026, 5, 5)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "first",
                startDate = "2026-05-04",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
            TemporaryScheduleOverride(
                id = "second",
                startDate = "2026-05-05",
                endDate = "2026-05-05",
                sourceDayOfWeek = 3,
            ),
        )

        assertEquals("second", matchingTemporaryScheduleOverride(date, overrides)?.id)
        assertEquals(3, resolveTemporaryScheduleSourceDate(date, overrides).dayOfWeek.value)
    }

    @Test
    fun `invalid override dates and weekdays are ignored`() {
        val date = LocalDate.of(2026, 5, 6)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "bad-date",
                startDate = "not-a-date",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
            TemporaryScheduleOverride(
                id = "bad-weekday",
                startDate = "2026-05-06",
                endDate = "2026-05-06",
                sourceDayOfWeek = 9,
            ),
        )

        assertNull(matchingTemporaryScheduleOverride(date, overrides))
        assertEquals(date.dayOfWeek.value, resolveTemporaryScheduleSourceDate(date, overrides).dayOfWeek.value)
    }

    @Test
    fun `cancel override cancels matching node range on target date`() {
        val date = LocalDate.of(2026, 5, 6)
        val course = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        val override = TemporaryScheduleOverride(
            id = "cancel",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-05-06",
            cancelStartNode = 2,
            cancelEndNode = 2,
        )

        assertTrue(isCourseTemporarilyCancelled(date, course, listOf(override)))
        assertFalse(isCourseTemporarilyCancelled(date.plusDays(1), course, listOf(override)))
    }

    @Test
    fun `cancel override can target a single course id`() {
        val date = LocalDate.of(2026, 5, 6)
        val target = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        val other = target.copy(id = "english", title = "大学英语")
        val override = TemporaryScheduleOverride(
            id = "cancel",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-05-06",
            cancelStartNode = 1,
            cancelEndNode = 2,
            cancelCourseId = "math",
        )

        assertEquals(listOf(other), filterTemporaryCancelledCourses(date, listOf(target, other), listOf(override)))
    }

    @Test
    fun `整天调课时全天的课都来自来源日`() {
        // 2026-05-06 周三，按 2026-05-11 周一的课上
        val date = LocalDate.of(2026, 5, 6)
        val source = LocalDate.of(2026, 5, 11)
        val overrides = listOf(
            TemporaryScheduleOverride(id = "makeup", targetDate = date.toString(), sourceDate = source.toString()),
        )
        val mondayCourse = CourseItem(
            id = "mon",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val wednesdayCourse = mondayCourse.copy(
            id = "wed",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )

        assertEquals(source, temporaryScheduleCourseSourceDate(date, mondayCourse, source, overrides))
        assertNull(temporaryScheduleCourseSourceDate(date, wednesdayCourse, source, overrides))
    }

    @Test
    fun `只调指定节次时区间外的课仍按本日`() {
        val date = LocalDate.of(2026, 5, 6)
        val source = LocalDate.of(2026, 5, 11)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                targetDate = date.toString(),
                sourceDate = source.toString(),
                makeUpStartNode = 3,
                makeUpEndNode = 4,
            ),
        )
        fun course(id: String, dayOfWeek: Int, startNode: Int, endNode: Int) = CourseItem(
            id = id,
            title = id,
            time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
        )

        // 区间内换成周一的课
        assertEquals(source, temporaryScheduleCourseSourceDate(date, course("mon34", 1, 3, 4), source, overrides))
        // 区间外周三自己的课留在原地
        assertEquals(date, temporaryScheduleCourseSourceDate(date, course("wed12", 3, 1, 2), source, overrides))
        // 周一区间外的课不该被搬过来
        assertNull(temporaryScheduleCourseSourceDate(date, course("mon12", 1, 1, 2), source, overrides))
        // 周三区间内的课被让位
        assertNull(temporaryScheduleCourseSourceDate(date, course("wed34", 3, 3, 4), source, overrides))
    }

    @Test
    fun `只调指定节次按区间相交判断而不是完全落入`() {
        val date = LocalDate.of(2026, 5, 6)
        val source = LocalDate.of(2026, 5, 11)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                targetDate = date.toString(),
                sourceDate = source.toString(),
                makeUpStartNode = 3,
                makeUpEndNode = 4,
            ),
        )
        val spanning = CourseItem(
            id = "mon",
            title = "连堂",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 3),
        )

        assertEquals(source, temporaryScheduleCourseSourceDate(date, spanning, source, overrides))
    }

    @Test
    fun `没有调课时课程按本日的星期几取`() {
        val date = LocalDate.of(2026, 5, 6)
        val wednesdayCourse = CourseItem(
            id = "wed",
            title = "大学英语",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        val mondayCourse = wednesdayCourse.copy(
            id = "mon",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )

        assertEquals(date, temporaryScheduleCourseSourceDate(date, wednesdayCourse, date, emptyList()))
        assertNull(temporaryScheduleCourseSourceDate(date, mondayCourse, date, emptyList()))
    }

    @Test
    fun `节次区间写反也能归位整天调课没有区间`() {
        val whole = TemporaryScheduleOverride(id = "a", targetDate = "2026-05-06", sourceDate = "2026-05-11")
        val reversed = whole.copy(id = "b", makeUpStartNode = 6, makeUpEndNode = 3)
        val single = whole.copy(id = "c", makeUpStartNode = 5)

        assertNull(whole.makeUpNodeRange())
        assertEquals(3..6, reversed.makeUpNodeRange())
        assertEquals(5..5, single.makeUpNodeRange())
    }

    @Test
    fun `旧版落盘的调课记录照旧按整天生效`() {
        // 新增 makeUpStartNode/makeUpEndNode 之前写下的 JSON，字段里没有这两个键
        val legacyExplicit = """[{"id":"a","type":"make_up","targetDate":"2026-05-06","sourceDate":"2026-05-11"}]"""
        val legacyWeekday = """[{"id":"b","type":"make_up","startDate":"2026-05-06","endDate":"2026-05-06","sourceDayOfWeek":1}]"""
        val legacyCancel =
            """[{"id":"c","type":"cancel_course","targetDate":"2026-05-06","cancelStartNode":1,"cancelEndNode":2}]"""
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val date = LocalDate.of(2026, 5, 6)

        val explicit = json.decodeFromString<List<TemporaryScheduleOverride>>(legacyExplicit)
        assertNull(explicit.single().makeUpNodeRange())
        assertEquals(LocalDate.of(2026, 5, 11), resolveTemporaryScheduleSourceDate(date, explicit))

        val weekday = json.decodeFromString<List<TemporaryScheduleOverride>>(legacyWeekday)
        assertNull(weekday.single().makeUpNodeRange())
        assertEquals(1, resolveTemporaryScheduleSourceDate(date, weekday).dayOfWeek.value)

        val cancel = json.decodeFromString<List<TemporaryScheduleOverride>>(legacyCancel)
        val course = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        assertTrue(isCourseTemporarilyCancelled(date, course, cancel))

        // 旧记录重新写回去也不会丢字段
        val reEncoded = json.decodeFromString<List<TemporaryScheduleOverride>>(
            json.encodeToString(explicit),
        )
        assertEquals(explicit, reEncoded)
    }
}
