package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extreme stress and overflow tests for timetable grid fitting.
 * Validates that excessive manual courses or dense schedule events
 * automatically switch the display to scrolling mode when a single screen cannot fit them.
 */
class ScheduleStressOverflowTest {

    @Test
    fun standardPeriods_fitsWithinSingleScreen() {
        // Standard 8 periods on typical screen (available height 700dp, day header 52dp)
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 8,
            timelineTotalUnits = 8f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
        )

        assertTrue("Standard 8 periods should fit in single screen", resolution.fitMode)
        assertFalse("Standard 8 periods should not overflow", resolution.isOverflowing)
        // (700 - 52) / 8 = 81.0dp >= MIN_FIT_SLOT_HEIGHT (52dp)
        assertEquals(81.dp, resolution.slotHeight)
    }

    @Test
    fun extremeCoursesStress_overflowsAndEnablesScrolling() {
        // Extreme scenario: 16 course periods manually added / configured on a 650dp viewport
        // (650 - 52) / 16 = 37.375dp < MIN_FIT_SLOT_HEIGHT (52dp)
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 16,
            timelineTotalUnits = 16f,
            availableHeight = 650.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
        )

        assertFalse("Excessive courses cannot fit in single screen", resolution.fitMode)
        assertTrue("Excessive courses must report overflow", resolution.isOverflowing)
        assertEquals("Fallback to standard card height for vertical scrolling", 64.dp, resolution.slotHeight)
    }

    @Test
    fun extremeEventsStress_denseEventsExpandTimelineAndTriggerOverflow() {
        // Extreme scenario: 6 periods, but 12 non-overlapping schedule events inserted throughout the day
        // Expanding timeline.totalUnits to 18 units on a 700dp screen
        // (700 - 52) / 18 = 36.0dp < MIN_FIT_SLOT_HEIGHT (52dp)
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 6,
            timelineTotalUnits = 18f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            hasInsertedRows = true,
        )

        assertFalse("Dense events cannot fit in single screen", resolution.fitMode)
        assertTrue("Dense events must report overflow", resolution.isOverflowing)
        // With hasInsertedRows, slotHeight uses MIN_FIT_SLOT_HEIGHT when slotCount alone would fit
        assertEquals("Maintains compact row height under event squeeze", MIN_FIT_SLOT_HEIGHT, resolution.slotHeight)
    }

    @Test
    fun excessiveEventsAndExcessiveCourses_overflowsToDefaultCardHeight() {
        // Extreme combined scenario: 14 periods + 10 inserted events = 24 units on 600dp screen
        // Neither periods nor events can fit
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 14,
            timelineTotalUnits = 24f,
            availableHeight = 600.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            hasInsertedRows = true,
        )

        assertFalse("Combined extreme load cannot fit", resolution.fitMode)
        assertTrue("Combined extreme load must report overflow", resolution.isOverflowing)
        assertEquals(64.dp, resolution.slotHeight)
    }

    @Test
    fun squeezedTitles_scrollEvenThoughPeriodsFit() {
        // 8 periods fit at 81dp, but a crowded week needs 100dp per period to show whole course names.
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 8,
            timelineTotalUnits = 8f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            titleSlotHeight = 100.dp,
            autoScrollWhenCrowded = true,
        )

        assertFalse(resolution.fitMode)
        assertTrue(resolution.isOverflowing)
        assertEquals(100.dp, resolution.slotHeight)
    }

    @Test
    fun crowdedWeek_keepsOnePageUnlessAutoScrollEnabled() {
        // Same crowded week: the default keeps one page; the setting opts into scrolling.
        val crowded = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 8,
            timelineTotalUnits = 8f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            titleSlotHeight = 100.dp,
            autoScrollWhenCrowded = false,
        )

        assertTrue(crowded.fitMode)
        assertFalse(crowded.isOverflowing)
        assertEquals(81.dp, crowded.slotHeight)
    }

    @Test
    fun twoCourses_splitCellTopAndBottom() {
        val stack = stackCellItems(listOf("A", "B"), 81.dp)
        assertEquals(listOf("A", "B"), stack.pieces.map { it.entry })
        assertEquals(39.dp, stack.pieces[0].height)
        assertEquals(42.dp, stack.pieces[1].top)
        assertEquals(0.dp, stack.moreHeight)
        assertFalse(stack.whole)
    }

    @Test
    fun threeCourses_showFirstAboveExpander() {
        val stack = stackCellItems(listOf("A", "B", "C"), 81.dp)
        assertEquals(listOf("A"), stack.pieces.map { it.entry })
        assertEquals(56.dp, stack.pieces[0].height)
        assertEquals(22.dp, stack.moreHeight)
    }

    @Test
    fun shortCell_fallsBackToCountBadge() {
        val stack = stackCellItems(listOf("A", "B", "C"), 40.dp)
        assertTrue(stack.whole)
        assertEquals(40.dp, stack.pieces.single().height)
        // Two courses too short to split still get the expander when it fits.
        assertEquals(22.dp, stackCellItems(listOf("A", "B"), 54.dp).moreHeight)
    }

    @Test
    fun tooManyPeriods_keepOnePageWhenAutoScrollOff() {
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 16,
            timelineTotalUnits = 16f,
            availableHeight = 652.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            autoScrollWhenCrowded = false,
        )

        assertTrue(resolution.fitMode)
        assertFalse(resolution.isOverflowing)
        assertEquals(37.5.dp, resolution.slotHeight)
    }

    @Test
    fun readableTitles_keepOnePage() {
        // Deleting the extra events lowers the need again, so the same preference fits one page.
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 8,
            timelineTotalUnits = 8f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
            titleSlotHeight = 60.dp,
        )

        assertTrue(resolution.fitMode)
        assertFalse(resolution.isOverflowing)
        assertEquals(81.dp, resolution.slotHeight)
    }

    @Test
    fun titleHeight_growsAsCardsNarrow() {
        // 14dp em, 15dp lines: a 50dp card holds 2 characters per line, a 24dp lane only 1.
        val wide = courseTitleHeightDp("汇编语言程序设计", cardWidthDp = 50f, emDp = 14f, lineHeightDp = 15f)
        val narrow = courseTitleHeightDp("汇编语言程序设计", cardWidthDp = 24f, emDp = 14f, lineHeightDp = 15f)
        assertEquals(4 * 15f + 9f, wide)
        assertEquals(8 * 15f + 9f, narrow)
        // Only the head of the title counts toward keeping one page.
        assertEquals(6 * 15f + 9f, courseTitleHeightDp("汇编语言程序设计", 24f, 14f, 15f, maxEms = 6f))
    }

    @Test
    fun scrollMode_doesNotTriggerOverflowAndAlwaysEnablesScrolling() {
        // User already selected vertical scrolling mode
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Scroll,
            slotCount = 16,
            timelineTotalUnits = 16f,
            availableHeight = 650.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
        )

        assertFalse("Scroll mode is never fitMode", resolution.fitMode)
        assertFalse("Scroll mode does not report overflow", resolution.isOverflowing)
        assertEquals(64.dp, resolution.slotHeight)
    }

    @Test
    fun boundaryCase_emptySlots_safeDefaults() {
        val resolution = resolveScheduleGridFit(
            rowFitMode = ScheduleRowFitMode.Fit,
            slotCount = 0,
            timelineTotalUnits = 0f,
            availableHeight = 700.dp,
            dayHeaderHeight = 52.dp,
            defaultCardHeight = 64.dp,
        )

        assertFalse(resolution.fitMode)
        assertFalse(resolution.isOverflowing)
        assertEquals(64.dp, resolution.slotHeight)
    }
}
