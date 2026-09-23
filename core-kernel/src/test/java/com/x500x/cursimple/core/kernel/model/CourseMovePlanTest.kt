package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 拖动调课对调课列表的改动。
 *
 * 曾经每拖一次都新建一条挪课记录：把已经挪过去的课再拖走时，挪进来的那份不会被隐藏，
 * 课越拖越多，原位置也一直还在。
 */
class CourseMovePlanTest {

    private val tue = LocalDate.of(2026, 9, 22)
    private val wed = LocalDate.of(2026, 9, 23)
    private val sun = LocalDate.of(2026, 9, 20)

    private fun plan(
        overrides: List<TemporaryScheduleOverride>,
        from: LocalDate,
        to: LocalDate,
        start: Int,
        end: Int = start,
    ) = planCourseMove(
        overrides = overrides,
        courseId = "web",
        from = from,
        to = to,
        toStartNode = start,
        toEndNode = end,
        naturalStartNode = 2,
        naturalEndNode = 2,
        newId = { "new" },
    )

    private fun moved(from: LocalDate, to: LocalDate, start: Int) = TemporaryScheduleOverride(
        id = "old",
        type = TemporaryScheduleOverrideType.MoveCourse,
        sourceDate = from.toString(),
        targetDate = to.toString(),
        moveCourseId = "web",
        moveToStartNode = start,
        moveToEndNode = start,
    )

    @Test
    fun `从原本那天拖走时新建一条挪课记录`() {
        val result = plan(emptyList(), from = tue, to = wed, start = 3)

        assertTrue(result.removeIds.isEmpty())
        val upsert = result.upsert!!
        assertEquals("new", upsert.id)
        assertEquals(tue.toString(), upsert.sourceDate)
        assertEquals(wed.toString(), upsert.targetDate)
        assertEquals(3, upsert.moveToStartNode)
    }

    @Test
    fun `再拖已经挪过去的课时改写原记录而不是叠一条`() {
        val existing = moved(tue, wed, 3)
        val result = plan(listOf(existing), from = wed, to = sun, start = 4)

        assertTrue(result.removeIds.isEmpty())
        val upsert = result.upsert!!
        assertEquals("沿用原记录，不会多出一份", "old", upsert.id)
        assertEquals("仍从课程原本那天挪出", tue.toString(), upsert.sourceDate)
        assertEquals(sun.toString(), upsert.targetDate)
        assertEquals(4, upsert.moveToStartNode)
    }

    @Test
    fun `挪回原本那天的原本节次等于撤销`() {
        val existing = moved(tue, wed, 3)
        val result = plan(listOf(existing), from = wed, to = tue, start = 2)

        assertEquals(listOf("old"), result.removeIds)
        assertNull(result.upsert)
    }

    @Test
    fun `挪回原本那天但换了节次时改成同一天内挪节次`() {
        val existing = moved(tue, wed, 3)
        val result = plan(listOf(existing), from = wed, to = tue, start = 5)

        val upsert = result.upsert!!
        assertEquals(tue.toString(), upsert.sourceDate)
        assertEquals(tue.toString(), upsert.targetDate)
        assertEquals(5, upsert.moveToStartNode)
    }

    @Test
    fun `别的课挪到这天的记录不受影响`() {
        val other = moved(tue, wed, 3).copy(id = "other", moveCourseId = "math")
        val result = plan(listOf(other), from = tue, to = wed, start = 4)

        assertEquals("new", result.upsert!!.id)
        assertTrue(result.removeIds.isEmpty())
    }
}
