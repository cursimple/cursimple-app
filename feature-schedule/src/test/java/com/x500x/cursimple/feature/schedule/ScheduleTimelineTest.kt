package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimelineTest {

    private fun m(clock: String) = clockMinute(clock)!!

    private val day = listOf(
        SlotClock(m("08:00"), m("08:45")),
        SlotClock(m("08:55"), m("09:40")),
        SlotClock(m("10:00"), m("10:45")),
        SlotClock(m("10:55"), m("11:40")),
        SlotClock(m("14:00"), m("14:45")),
        SlotClock(m("14:55"), m("15:40")),
    )

    private fun ev(start: String, end: String) = MinuteRange(m(start), m(end))

    @Test fun eveningNoticeOutsideCoursesGetsItsOwnRow() {
        val timeline = GridTimeline.build(day, listOf(ev("18:49", "19:19")))
        val row = timeline.rows.filterIsInstance<TimelineRow.Gap>().single()
        assertEquals(m("18:49"), row.startMinute)
        assertEquals(m("19:19"), row.endMinute)
        assertTrue(row.top >= timeline.slotTop(day.lastIndex) + 1f)
        assertEquals(row.top, timeline.yOf(m("18:49")), 0.001f)
    }

    @Test fun typedCourseSitsAtItsMinutesWhilePeriodCoursesFillTheirRows() {
        val lunch = ev("12:10", "13:00")
        val timeline = GridTimeline.build(day, listOf(lunch))
        val gap = timeline.rows.filterIsInstance<TimelineRow.Gap>().single()
        val (top, bottom) = timeline.courseSpan(rowIndex = 3, rowSpan = 1, clock = lunch, minUnits = 0.3f)
        assertEquals(gap.top, top, 0.001f)
        assertEquals(gap.top + gap.height, bottom, 0.001f)
        // Inside a period the typed course only covers its share of the row.
        val (innerTop, innerBottom) = timeline.courseSpan(0, 2, ev("08:20", "09:10"), 0.3f)
        assertTrue(innerTop > timeline.slotTop(0) && innerBottom < timeline.slotBottom(1))
        assertEquals(timeline.slotTop(4) to timeline.slotBottom(5), timeline.courseSpan(4, 2, null, 0.3f))
    }

    @Test fun veryShortTypedCourseKeepsAReadableHeight() {
        val timeline = GridTimeline.build(day, emptyList())
        val (top, bottom) = timeline.courseSpan(0, 1, ev("08:00", "08:05"), minUnits = 0.4f)
        assertEquals(0.4f, bottom - top, 0.001f)
    }

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
        assertEquals(20f / 45f, t.yOf(m("08:20")), 0.001f)
        assertEquals(1f + 25f / 45f, t.yOf(m("09:20")), 0.001f)
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
        assertEquals(50f / 45f, gap.height, 0.001f)
        assertEquals(4f + gap.height, t.slotTop(4), 0.001f)
        assertEquals(gap.top, t.yOf(m("12:10")), 0.001f)
        assertEquals(gap.top + gap.height, t.yOf(m("13:00")), 0.001f)
        // Inserted clock bands do not accept period-based courses.
        assertNull(t.slotIndexAt(gap.top + 0.1f))
        assertEquals(4, t.slotIndexAt(t.slotTop(4) + 0.1f))
    }

    @Test
    fun eventBarelyCrossingABreak_doesNotInsert() {
        // Only the event's occupied minutes expand the lunch gap.
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

        val cols = DayColumns.of(listOf(1, 2, 1, 1, 1, 1, 1))
        assertFalse(cols.isUniform)
        assertEquals(8f, cols.totalUnits)
        assertEquals(1f, cols.start(1))
        assertEquals(2f, cols.width(1))
        assertEquals(3f, cols.start(2))
        assertEquals(1, cols.indexAt(2.6f))
        assertEquals(2, cols.indexAt(3.1f))
        assertEquals(6, cols.indexAt(99f))
        assertEquals(0, cols.indexAt(0f))
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
    fun nextOccurrence_followsPickedWeeks() {
        val picked = com.x500x.cursimple.core.kernel.model.ScheduleEvent(
            id = "e", title = "t", date = "2026-09-15", startTime = "10:00", endTime = "11:00",
            weekDates = listOf("2026-09-15", "2026-10-13", "2026-11-03"),
        )
        assertEquals(java.time.LocalDate.of(2026, 10, 13), picked.nextOccurrence(java.time.LocalDate.of(2026, 9, 30)))
        assertNull(picked.nextOccurrence(java.time.LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun clusterByOverlap_mergesPartialOverlapsButNotTouching() {
        data class B(val id: String, val t: Float, val b: Float)
        val blocks = listOf(
            B("a", 0f, 1f),
            B("b", 0.8f, 1.5f),
            B("c", 1.4f, 2f),
            B("d", 2f, 3f),
        )
        val groups = clusterByOverlap(blocks, { it.t }, { it.b }).map { g -> g.map { it.id } }
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d")), groups)
    }

    @Test
    fun fitSingleLineSp_shrinksOnlyWhenNeeded() {
        assertEquals(36f / (3f * 1.05f), fitSingleLineSp("交作业", 36f, 13f), 0.01f)
        assertEquals(13f, fitSingleLineSp("讲座", 80f, 13f), 0.01f)
        assertEquals(8f, fitSingleLineSp("非常长的一个事务名字", 30f, 13f), 0.01f)
    }

    @Test
    fun dayColumns_fitInto_neverPushesDaysOffScreen() {
        val allWide = DayColumns.of(List(7) { 2 }).fitInto(availableDp = 360f, minUnitDp = 36f)
        assertTrue(allWide.isUniform)
        assertEquals(7f, allWide.totalUnits)

        // Preserve expanded width when it fits the full week.
        val one = DayColumns.of(listOf(1, 2, 1, 1, 1, 1, 1)).fitInto(360f, 36f)
        assertEquals(2f, one.width(1))
        assertEquals(8f, one.totalUnits)

        val many = DayColumns.of(listOf(3, 3, 3, 3, 3, 1, 1)).fitInto(360f, 36f)
        assertTrue(360f / many.totalUnits >= 36f - 0.01f)
        assertTrue(many.width(0) > 1f && many.width(0) < 3f)
        assertEquals(1f, many.width(5))

        val tiny = DayColumns.of(listOf(2, 1, 1, 1, 1, 1, 1)).fitInto(250f, 36f)
        assertTrue(tiny.isUniform)
    }

    @Test
    fun collapsing_twoOverlapsKeepTwoLanesAndNothingFolds() {
        val result = assignLanesCollapsing(
            listOf(LaneItem("a", 0f, 2f, 0), LaneItem("b", 1f, 3f, 0), LaneItem("c", 3f, 4f, 0)),
        )
        assertEquals(LanePosition(0, 2), result.positions["a"])
        assertEquals(LanePosition(1, 2), result.positions["b"])
        // c starts after both end, so it is a new cluster and takes the full width again.
        assertEquals(LanePosition(0, 1), result.positions["c"])
        assertTrue(result.overflows.isEmpty())
        assertTrue(result.trimmedBottoms.isEmpty())
    }

    @Test
    fun collapsing_threeAtOnceKeepsTwoNearestAndFoldsTheRest() {
        val result = assignLanesCollapsing(
            listOf(
                LaneItem("early", 0f, 3f, 0, priority = 5f),
                LaneItem("now", 1f, 3f, 0, priority = 0f),
                LaneItem("late", 2f, 4f, 0, priority = 9f),
            ),
        )
        // Nearest on the left, next nearest on the right, the last one behind the chip below it.
        assertEquals(LanePosition(0, MAX_DAY_LANES), result.positions["now"])
        assertEquals(LanePosition(1, MAX_DAY_LANES), result.positions["early"])
        assertEquals(2, result.positions.size)
        val chip = result.overflows.single()
        assertEquals(listOf("late"), chip.hidden)
        assertEquals(4f, chip.bottom)
        assertEquals(3.5f, chip.top)
        assertTrue(result.trimmedBottoms.isEmpty())
    }

    @Test
    fun collapsing_rightCardIsCutShortToMakeRoomForTheChip() {
        // "a" and "d" do not overlap, so both stay in the left lane; "b" takes the right lane, "c" folds.
        val result = assignLanesCollapsing(
            listOf(
                LaneItem("a", 0f, 2f, 0, priority = 0f),
                LaneItem("b", 1f, 4f, 0, priority = 3f),
                LaneItem("c", 1f, 4f, 0, priority = 4f),
                LaneItem("d", 3f, 4f, 0, priority = 1f),
            ),
        )
        assertEquals(LanePosition(0, MAX_DAY_LANES), result.positions["a"])
        assertEquals(LanePosition(0, MAX_DAY_LANES), result.positions["d"])
        assertEquals(LanePosition(1, MAX_DAY_LANES), result.positions["b"])
        assertEquals(listOf("c"), result.overflows.single().hidden)
        assertEquals(3.5f, result.trimmedBottoms["b"])
    }

    @Test
    fun collapsing_rightCardTooShortForTheChipFoldsInstead() {
        val result = assignLanesCollapsing(
            listOf(LaneItem("x1", 0f, 1f, 0), LaneItem("x2", 0f, 1f, 0), LaneItem("x3", 0f, 1f, 0), LaneItem("y", 5f, 6f, 0)),
        )
        assertEquals(LanePosition(0, 2), result.positions["x1"])
        assertEquals(LanePosition(0, 1), result.positions["y"])
        assertEquals(setOf("x2", "x3"), result.overflows.single().hidden.toSet())
    }

    @Test
    fun collapsing_singleLaneKeepsNearestFullWidthAndFoldsTheRest() {
        val result = assignLanesCollapsing(
            listOf(LaneItem("a", 0f, 3f, 0), LaneItem("b", 1f, 3f, 0), LaneItem("c", 2f, 4f, 0)),
            maxLanes = 1,
        )
        assertEquals(LanePosition(0, 1), result.positions["a"])
        assertEquals(setOf("b", "c"), result.overflows.single().hidden.toSet())
        assertEquals(LanePosition(0, 1), result.overflows.single().position)
    }

    @Test
    fun laneLimits_dropTheLeastBusyDaysFirstUntilTheWeekFits() {
        val weights = listOf(1f, 2f, 2f, 1f, 2f)
        val counts = listOf(0, 3, 5, 0, 4)
        // The week needs eight units but only six fit: the two least busy wide days give up their second lane.
        assertEquals(listOf(2, 1, 2, 2, 1), chooseLaneLimits(weights, counts, budgetUnits = 6f))
        // Plenty of room keeps every day as is.
        assertEquals(List(5) { 2 }, chooseLaneLimits(weights, counts, budgetUnits = 20f))
    }

    @Test
    fun collapsing_wideGridKeepsThreeLanesAndFoldsTheFourth() {
        val result = assignLanesCollapsing(
            listOf(
                LaneItem("a", 0f, 4f, 0), LaneItem("b", 0f, 4f, 0), LaneItem("c", 0f, 4f, 0), LaneItem("d", 0f, 4f, 0),
            ),
            maxLanes = 3,
        )
        assertEquals(setOf(0, 1, 2), result.positions.values.map { it.lane }.toSet())
        assertTrue(result.positions.values.all { it.laneCount == 3 })
        assertEquals(LanePosition(2, 3), result.overflows.single().position)
        assertEquals(listOf("d"), result.overflows.single().hidden)
    }

    @Test
    fun laneLimits_wideGridDropsOneLaneAtATime() {
        // Three busy days need nine units but only eight fit: the least busy loses one lane, not two.
        assertEquals(listOf(3, 2, 3), chooseLaneLimits(listOf(3f, 3f, 3f), listOf(5, 2, 4), budgetUnits = 8f, maxLanes = 3))
    }

    @Test
    fun lanePriority_prefersOngoingThenUpcomingThenFinished() {
        val ongoing = lanePriority(top = 2f, bottom = 4f, nowUnits = 3f)
        val upcoming = lanePriority(top = 6f, bottom = 8f, nowUnits = 3f)
        val soonerUpcoming = lanePriority(top = 4f, bottom = 5f, nowUnits = 3f)
        val finished = lanePriority(top = 0f, bottom = 1f, nowUnits = 3f)
        assertTrue(ongoing < soonerUpcoming)
        assertTrue(soonerUpcoming < upcoming)
        assertTrue(upcoming < finished)
        // Another day has no clock, so the earliest start wins; inactive courses always go last.
        assertTrue(lanePriority(1f, 2f, null) < lanePriority(2f, 3f, null))
        assertTrue(lanePriority(9f, 10f, 3f) < lanePriority(2f, 4f, 3f, inactive = true))
    }
}
