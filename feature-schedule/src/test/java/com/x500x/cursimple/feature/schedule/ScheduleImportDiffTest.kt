package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleImportDiffTest {

    private fun course(
        id: String,
        title: String,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        location: String = "A101",
        weeks: List<Int> = listOf(1, 2, 3),
        hidden: Boolean = false,
    ) = CourseItem(
        id = id,
        title = title,
        location = location,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = day, startNode = start, endNode = end),
        hidden = hidden,
    )

    private fun schedule(vararg courses: CourseItem) = TermSchedule(
        termId = "t",
        updatedAt = "2026-09-20T00:00:00Z",
        dailySchedules = courses.groupBy { it.time.dayOfWeek }
            .map { (day, list) -> DailySchedule(dayOfWeek = day, courses = list) },
    )

    @Test
    fun `没有变化时不报任何增减`() {
        val before = schedule(course("1", "高数"), course("2", "英语", day = 2))
        // id 换了但课没变：教务系统每次抓取都会换 id，不能因此判成全删全增
        val after = schedule(course("x", "高数"), course("y", "英语", day = 2))

        val diff = diffSchedules(before, after)

        assertFalse(diff.hasChanges)
        assertEquals(2, diff.keptCount)
    }

    @Test
    fun `新增的课列进 added`() {
        val diff = diffSchedules(
            schedule(course("1", "高数")),
            schedule(course("1", "高数"), course("2", "线代", day = 3)),
        )

        assertEquals(listOf("线代"), diff.added.map { it.title })
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun `减少的课列进 removed`() {
        val diff = diffSchedules(
            schedule(course("1", "高数"), course("2", "线代", day = 3)),
            schedule(course("1", "高数")),
        )

        assertEquals(listOf("线代"), diff.removed.map { it.title })
        assertTrue(diff.added.isEmpty())
    }

    @Test
    fun `换了教室算一增一删`() {
        val diff = diffSchedules(
            schedule(course("1", "高数", location = "A101")),
            schedule(course("1", "高数", location = "B202")),
        )

        assertEquals(listOf("B202"), diff.added.map { it.location })
        assertEquals(listOf("A101"), diff.removed.map { it.location })
    }

    @Test
    fun `用户删掉的课不参与比对`() {
        // hidden 是「这门课被用户删了」的墓碑，不该在导入比对里冒出来
        val diff = diffSchedules(
            schedule(course("1", "高数"), course("2", "体育", day = 4, hidden = true)),
            schedule(course("1", "高数")),
        )

        assertFalse(diff.hasChanges)
    }

    @Test
    fun `第一次导入时整张表都算新增`() {
        val diff = diffSchedules(null, schedule(course("1", "高数"), course("2", "英语", day = 2)))

        assertEquals(2, diff.added.size)
        assertEquals(0, diff.keptCount)
    }

    @Test
    fun `同一门课多条时按条数差计增减`() {
        // 单双周常常拆成两条，删掉其中一条只能算减一，不能两条都当没变
        val before = schedule(course("1", "高数"), course("2", "高数"))
        val after = schedule(course("1", "高数"))

        val diff = diffSchedules(before, after)

        assertEquals(1, diff.removed.size)
        assertTrue(diff.added.isEmpty())
        assertEquals(1, diff.keptCount)
    }
}
