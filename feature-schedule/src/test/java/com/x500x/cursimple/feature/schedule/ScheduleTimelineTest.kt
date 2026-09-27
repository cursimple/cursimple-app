package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimelineTest {

    private fun m(clock: String) = clockMinute(clock)!!

    /** 上午四节、下午两节，中间一个长午休 */
    private val day = listOf(
        SlotClock(m("08:00"), m("08:45")),
        SlotClock(m("08:55"), m("09:40")),
        SlotClock(m("10:00"), m("10:45")),
        SlotClock(m("10:55"), m("11:40")),
        SlotClock(m("14:00"), m("14:45")),
        SlotClock(m("14:55"), m("15:40")),
    )

    private fun ev(start: String, end: String) = MinuteRange(m(start), m(end))

    @Test
    fun noEvents_isTheOriginalGrid() {
        val t = GridTimeline.build(day, emptyList())
        assertFalse(t.hasInsertedRows)
        assertEquals(6f, t.totalUnits)
        (0 until 6).forEach { assertEquals(it.toFloat(), t.slotTop(it)) }
    }

    @Test
    fun eventInsideSlots_mapsByClockWithoutInserting() {
        val t = GridTimeline.build(day, listOf(ev("08:20", "09:20")))
        assertFalse(t.hasInsertedRows)
        // 08:20 在第一节 45 分钟里走了 20 分钟
        assertEquals(20f / 45f, t.yOf(m("08:20")), 0.001f)
        // 09:20 在第二节里走了 25 分钟
        assertEquals(1f + 25f / 45f, t.yOf(m("09:20")), 0.001f)
        // 十分钟的课间合拢：08:50 贴在第一节底边
        assertEquals(1f, t.yOf(m("08:50")), 0.001f)
    }

    @Test
    fun lunchEvent_insertsOnlyTheCoveredPartAndPushesAfternoonDown() {
        val t = GridTimeline.build(day, listOf(ev("12:10", "13:00")))
        assertTrue(t.hasInsertedRows)
        val gap = t.rows.filterIsInstance<TimelineRow.Gap>().single()
        assertEquals(m("12:10"), gap.startMinute)
        assertEquals(m("13:00"), gap.endMinute)
        assertEquals(4f, gap.top)
        // 50 分钟 ÷ 平均 45 分钟一节
        assertEquals(50f / 45f, gap.height, 0.001f)
        assertEquals(4f + gap.height, t.slotTop(4), 0.001f)
        assertEquals(gap.top, t.yOf(m("12:10")), 0.001f)
        assertEquals(gap.top + gap.height, t.yOf(m("13:00")), 0.001f)
        // 插出来的段里不能加课
        assertNull(t.slotIndexAt(gap.top + 0.1f))
        assertEquals(4, t.slotIndexAt(t.slotTop(4) + 0.1f))
    }

    @Test
    fun eventBarelyCrossingABreak_doesNotInsert() {
        // 只在 11:40-11:50 这十分钟跨进午休
        val t = GridTimeline.build(day, listOf(ev("11:00", "11:50")))
        assertFalse(t.hasInsertedRows)
    }

    @Test
    fun eveningAndEarlyEvents_insertAtTheEnds() {
        val t = GridTimeline.build(day, listOf(ev("07:00", "07:30"), ev("19:00", "21:00")))
        val gaps = t.rows.filterIsInstance<TimelineRow.Gap>()
        assertEquals(2, gaps.size)
        assertEquals(0f, gaps[0].top)
        assertEquals(30f / 45f, gaps[0].height, 0.001f)
        assertEquals(gaps[0].height, t.slotTop(0), 0.001f)
        // 两个小时封顶两节高
        assertEquals(GridTimeline.MAX_GAP_UNITS, gaps[1].height, 0.001f)
        assertEquals(t.totalUnits, gaps[1].top + gaps[1].height, 0.001f)
        assertEquals(m("19:00"), gaps[1].startMinute)
    }

    @Test
    fun noTimingProfile_appendsEventsAfterTheGrid() {
        val untimed = List(8) { SlotClock(null, null) }
        val t = GridTimeline.build(untimed, listOf(ev("09:00", "10:00"), ev("13:00", "14:00")))
        val gap = t.rows.last() as TimelineRow.Gap
        assertEquals(8f, gap.top)
        assertEquals(8f, t.yOf(m("09:00")), 0.001f)
        assertEquals(t.totalUnits, t.yOf(m("14:00")), 0.001f)
    }

    @Test
    fun nearestSlotIndex_snapsAcrossInsertedRows() {
        val t = GridTimeline.build(day, listOf(ev("12:00", "13:30")))
        val afternoon = t.slotTop(4)
        assertEquals(4, t.nearestSlotIndex(afternoon + 0.2f))
        assertEquals(3, t.nearestSlotIndex(3.1f))
    }

    @Test
    fun lanes_nonOverlappingBlocksKeepFullWidth() {
        val lanes = assignLanes(
            listOf(
                LaneItem("a", 0f, 2f, 0),
                LaneItem("b", 2f, 3f, 1),
            ),
        )
        assertEquals(LanePosition(0, 1), lanes["a"])
        assertEquals(LanePosition(0, 1), lanes["b"])
    }

    @Test
    fun lanes_overlapSplitsCourseLeftEventRight() {
        val lanes = assignLanes(
            listOf(
                LaneItem("event", 0.5f, 1.5f, 1),
                LaneItem("course", 0f, 2f, 0),
                LaneItem("later", 3f, 4f, 0),
            ),
        )
        assertEquals(LanePosition(0, 2), lanes["course"])
        assertEquals(LanePosition(1, 2), lanes["event"])
        assertEquals(LanePosition(0, 1), lanes["later"])
    }

    @Test
    fun lanes_reuseFreedLaneInsideACluster() {
        val lanes = assignLanes(
            listOf(
                LaneItem("long", 0f, 4f, 0),
                LaneItem("e1", 0f, 1f, 1),
                LaneItem("e2", 2f, 3f, 1),
            ),
        )
        assertEquals(LanePosition(0, 2), lanes["long"])
        assertEquals(LanePosition(1, 2), lanes["e1"])
        assertEquals(LanePosition(1, 2), lanes["e2"])
    }

    @Test
    fun laneFraction_alwaysSplitsSideBySide() {
        assertEquals(0f to 1f, laneFraction(LanePosition(0, 1)))
        assertEquals(0f to 0.5f, laneFraction(LanePosition(0, 2)))
        assertEquals(0.5f to 0.5f, laneFraction(LanePosition(1, 2)))
    }

    @Test
    fun dayColumns_widenOnlyTheDayThatNeedsLanes() {
        val uniform = DayColumns.of(List(7) { 1 })
        assertTrue(uniform.isUniform)
        assertEquals(7f, uniform.totalUnits)

        // 周二有两条道：这一列放宽成两列宽，其余不变
        val cols = DayColumns.of(listOf(1, 2, 1, 1, 1, 1, 1))
        assertFalse(cols.isUniform)
        assertEquals(8f, cols.totalUnits)
        assertEquals(1f, cols.start(1))
        assertEquals(2f, cols.width(1))
        assertEquals(3f, cols.start(2))
        // 点在放宽那一列的右半边仍算周二
        assertEquals(1, cols.indexAt(2.6f))
        assertEquals(2, cols.indexAt(3.1f))
        assertEquals(6, cols.indexAt(99f))
        assertEquals(0, cols.indexAt(0f))
        // 道再多也最多放宽到三列
        assertEquals(DayColumns.MAX_COLUMN_WEIGHT.toFloat(), DayColumns.of(listOf(5)).width(0))
    }

    @Test
    fun nextOccurrence_sortsUpcomingAndDropsFinished() {
        val today = java.time.LocalDate.of(2026, 9, 30)
        val base = com.x500x.cursimple.core.kernel.model.ScheduleEvent(
            id = "e", title = "t", date = "2026-09-29", startTime = "10:00", endTime = "11:00",
        )
        assertNull(base.nextOccurrence(today))
        val weekly = base.copy(repeatWeekly = true)
        assertEquals(java.time.LocalDate.of(2026, 10, 6), weekly.nextOccurrence(today))
        assertEquals(java.time.LocalDate.of(2026, 9, 29), weekly.nextOccurrence(java.time.LocalDate.of(2026, 9, 29)))
        assertNull(weekly.copy(repeatUntil = "2026-10-05").nextOccurrence(today))
    }

    @Test
    fun clusterByOverlap_mergesPartialOverlapsButNotTouching() {
        data class B(val id: String, val t: Float, val b: Float)
        val blocks = listOf(
            B("a", 0f, 1f),
            B("b", 0.8f, 1.5f), // 和 a 叠了一截
            B("c", 1.4f, 2f), // 和 b 叠了一截：三个连成一组
            B("d", 2f, 3f), // 和 c 首尾相接，不算叠
        )
        val groups = clusterByOverlap(blocks, { it.t }, { it.b }).map { g -> g.map { it.id } }
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d")), groups)
    }

    @Test
    fun fitSingleLineSp_shrinksOnlyWhenNeeded() {
        // 三个汉字、36dp 宽：缩到 11sp 多一点
        assertEquals(36f / (3f * 1.05f), fitSingleLineSp("交作业", 36f, 13f), 0.01f)
        // 地方够就用原字号
        assertEquals(13f, fitSingleLineSp("讲座", 80f, 13f), 0.01f)
        // 再窄也不小于 8sp
        assertEquals(8f, fitSingleLineSp("非常长的一个事务名字", 30f, 13f), 0.01f)
    }

    @Test
    fun dayColumns_fitInto_neverPushesDaysOffScreen() {
        // 七天全要两条道：等于谁都没放宽
        val allWide = DayColumns.of(List(7) { 2 }).fitInto(availableDp = 360f, minUnitDp = 36f)
        assertTrue(allWide.isUniform)
        assertEquals(7f, allWide.totalUnits)

        // 只有一天放宽，塞得下：原样放宽
        val one = DayColumns.of(listOf(1, 2, 1, 1, 1, 1, 1)).fitInto(360f, 36f)
        assertEquals(2f, one.width(1))
        assertEquals(8f, one.totalUnits)

        // 五天放宽到三列宽：360dp 最多 10 份，按比例收回
        val many = DayColumns.of(listOf(3, 3, 3, 3, 3, 1, 1)).fitInto(360f, 36f)
        assertTrue(360f / many.totalUnits >= 36f - 0.01f)
        assertTrue(many.width(0) > 1f && many.width(0) < 3f)
        assertEquals(1f, many.width(5))

        // 窄到连等宽都勉强：退回等宽，不再放宽
        val tiny = DayColumns.of(listOf(2, 1, 1, 1, 1, 1, 1)).fitInto(250f, 36f)
        assertTrue(tiny.isUniform)
    }
}
