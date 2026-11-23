package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/**
 * Opt-in QA for the week grid: blank space inside a partly occupied slot takes a tap and offers
 * the add hint, and one card beside two stacked cards needs no "more" chip. Run with `-e blankLaneQa true`.
 */
class BlankLaneAddUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext

    private val monday = LocalDate.of(2026, 9, 14)
    @Before fun fixedClock() { BeijingTime.setForcedNow(monday.atTime(9, 45)) }
    @After fun restoreClock() { BeijingTime.setForcedNow(null) }
    private val termStart = monday.minusWeeks(6)
    private val timing = TermTimingProfile(termStart.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40"),
        ClassSlotTime(5, 6, "14:00", "15:35"), ClassSlotTime(7, 8, "16:05", "17:40")))
    private val all = (1..18).toList()

    private fun course(id: String, title: String, day: Int, start: Int, end: Int) =
        CourseItem(id, title, location = "东13-C-315", weeks = all, time = CourseTimeSlot(day, start, end))

    private val courses = listOf(
        // Tuesday: a long course on the left, a shorter one on the right; the right lane below it is blank.
        course("lab", "物理实验", 2, 5, 8), course("prog", "程序设计", 2, 5, 6),
        // Wednesday: one card on the left, two stacked on the right. Nothing folds.
        course("w-left", "高等数学", 3, 1, 4), course("w-top", "大学英语", 3, 1, 2), course("w-bottom", "体育", 3, 3, 4),
        // Thursday: a genuine triple in the first slot, then a lone course that must not be touched.
        course("t1", "数据结构", 4, 1, 2), course("t2", "操作系统", 4, 1, 4), course("t3", "编译原理", 4, 1, 6),
        course("t4", "计算机网络", 4, 7, 8),
        // Friday: two courses in the same period share one stacked card.
        course("f-a", "近代史纲要", 5, 3, 4), course("f-b", "fff", 5, 3, 4),
    )

    private fun week() = hasTestTag("schedule-current-week")
    private fun chips() = compose.onAllNodes(hasTestTag("schedule-course-more") and hasAnyAncestor(week()), useUnmergedTree = true)
    private fun drawn(id: String) = compose.onAllNodes(hasTestTag("schedule-course-$id") and hasAnyAncestor(week()), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    private fun bounds(id: String) = compose.onNode(hasTestTag("schedule-course-$id") and hasAnyAncestor(week()), useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot
    private fun hint() = compose.onAllNodes(hasTestTag("schedule-grid-hint-add"), useUnmergedTree = true)

    private fun show() {
        // The app resets the debug clock from its saved preference shortly after start; set it again.
        Thread.sleep(1500)
        BeijingTime.setForcedNow(monday.atTime(9, 45))
        val fontScale = args.getString("qaFontScale")?.toFloatOrNull()
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * 0.72f, fontScale ?: base.fontScale)) {
                ClassScheduleTheme(
                    themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                    themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
                ) {
                    ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses),
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = termStart,
                        weekOffset = 0, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "blank-lane-qa/$name.png"))
    }

    /** The empty right lane under the shorter course answers a tap with the add hint, and the hint opens the add dialog. */
    @Test fun blankLaneBelowAShorterCourseTakesATap() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        val lab = bounds("lab")
        val prog = bounds("prog")
        assertTrue("the two sit in parallel lanes: $lab / $prog", prog.left >= lab.right - 1f || lab.left >= prog.right - 1f)
        assertTrue("the right-hand course is the shorter one: $lab / $prog", prog.height < lab.height - 1f)
        // Thursday's triple owns the only chip; Tuesday's two cards are both drawn.
        assertEquals("only Thursday folds", 1, chips().fetchSemanticsNodes().size)
        save("blank-before")

        // Tap the middle of the empty rectangle under the shorter card.
        val x = (prog.left + prog.right) / 2f
        val y = prog.bottom + (lab.bottom - prog.bottom) / 2f
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput { click(Offset(x, y)) }
            compose.mainClock.advanceTimeBy(700)
            assertEquals("the blank lane shows one add hint", 1, hint().fetchSemanticsNodes().size)
            // The hint fills the same lane as the card above it: the plus sits under that card's centre, not
            // pushed left against the neighbouring card.
            val plus = hint()[0].fetchSemanticsNode().boundsInRoot
            val offCentre = kotlin.math.abs((plus.left + plus.right) / 2f - (prog.left + prog.right) / 2f)
            assertTrue("the hint lines up with the lane above (off by ${offCentre}px): $plus / $prog", offCentre < 2f)
            Thread.sleep(400)
            ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "blank-lane-qa/blank-hint.png"))
            hint()[0].performClick()
            compose.mainClock.advanceTimeBy(700)
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_quick_add_title)).assertIsDisplayed()
        save("blank-dialog")
    }

    /** Control: a wholly empty cell has always offered the hint. */
    @Test fun anEmptyCellShowsTheHint() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        val prog = bounds("prog")
        val top = bounds("w-left").top
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput { click(Offset((prog.left + prog.right) / 2f, top + 150f)) }
            compose.mainClock.advanceTimeBy(700)
            Thread.sleep(300)
            ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "blank-lane-qa/control.png"))
            assertEquals("an empty cell shows the hint", 1, hint().fetchSemanticsNodes().size)
        } finally { compose.mainClock.autoAdvance = true }
    }

    /** A tap on a card must not show the blank hint. */
    @Test fun tappingACardDoesNotShowTheBlankHint() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        val prog = bounds("prog")
        compose.onRoot().performTouchInput { click(Offset((prog.left + prog.right) / 2f, (prog.top + prog.bottom) / 2f)) }
        compose.waitForIdle()
        assertEquals("a card tap is not a blank tap", 0, hint().fetchSemanticsNodes().size)
    }

    /** One card on the left with two stacked on the right is fully drawn, with no chip. */
    @Test fun oneBesideTwoStackedNeedsNoChip() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        listOf("w-left", "w-top", "w-bottom").forEach { assertTrue("$it is drawn", drawn(it)) }
        val left = bounds("w-left")
        val top = bounds("w-top")
        val bottom = bounds("w-bottom")
        assertTrue("the stacked pair sits right of the long card", top.left >= left.right - 1f && bottom.left >= left.right - 1f)
        assertTrue("the pair stacks top to bottom: $top / $bottom", bottom.top >= top.bottom - 1f)
        save("one-beside-two")
    }

    /** Only the genuine triple folds; the lone course after it stays a card. */
    @Test fun onlyATrueTripleGetsAChip() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        assertTrue("the later course keeps its own card", drawn("t4"))
        assertEquals("one chip for the one crowded stretch", 1, chips().fetchSemanticsNodes().size)
        listOf("w-left", "w-top", "w-bottom", "lab", "prog").forEach { assertTrue("$it is not folded", drawn(it)) }
        save("true-triple")
    }

    /** Long-pressing a two-course card opens the bottom action sheet at once, with a switcher for the other course. */
    @Test fun longPressOnAStackedCardOpensTheSheetWithASwitcher() {
        assumeTrue(args.getString("blankLaneQa") == "true")
        show()
        val shown = listOf("f-a", "f-b").first { drawn(it) }
        val other = if (shown == "f-a") "f-b" else "f-a"
        val b = bounds(shown)
        compose.onRoot().performTouchInput {
            longClick(Offset((b.left + b.right) / 2f, (b.top + b.bottom) / 2f))
        }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_action_edit)).assertIsDisplayed()
        compose.onNodeWithTag("course-action-switcher").assertIsDisplayed()
        // The old centred picker must be gone.
        compose.onAllNodesWithText(context.getString(ScheduleR.string.schedule_overlap_pick_title, 2)).assertCountEquals(1)
        save("stack-sheet")
        // Switching retargets the sheet in place instead of closing it.
        compose.onNodeWithTag("course-action-switch-$other").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_action_edit)).assertIsDisplayed()
        compose.onAllNodesWithTag("course-action-switcher").assertCountEquals(1)
        save("stack-sheet-switched")
    }
}
