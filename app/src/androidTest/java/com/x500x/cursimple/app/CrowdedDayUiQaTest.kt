package com.x500x.cursimple.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR
import java.time.LocalDate

/**
 * Opt-in QA for crowded days: two overlapping courses take two lanes, three or more at once keep the
 * nearest one and fold the rest behind a "more" chip. Run with `-e crowdedQa true`.
 */
class CrowdedDayUiQaTest {
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

    private fun course(id: String, title: String, day: Int, start: Int, end: Int, from: String? = null, to: String? = null) =
        CourseItem(id, title, location = "东13-C-315", weeks = all, time = CourseTimeSlot(day, start, end),
            reminderStartTime = from, reminderEndTime = to)

    private val wednesday = listOf(
        course("w1", "课程一", 3, 1, 2), course("w2", "课程二", 3, 1, 3), course("w3", "课程三", 3, 1, 4),
        course("w4", "课程四", 3, 1, 5), course("w5", "课程五", 3, 1, 6), course("w6", "课程六", 3, 1, 7),
        course("w7", "课程七", 3, 1, 8), course("w8", "课程八", 3, 2, 3), course("w9", "课程九", 3, 2, 4),
        course("w10", "课程十", 3, 2, 5),
    )

    private val courses = listOf(
        // Monday: A, B and C all start in period 1 but end in different periods, so they are three cells.
        course("a", "汇编语言", 1, 1, 2),
        course("b", "组成原理", 1, 1, 4, from = "08:30", to = "10:30"),
        course("c", "数值分析", 1, 1, 6, from = "09:00", to = "11:00"),
        course("d", "操作系统", 1, 7, 8),
        // Tuesday: a pair that must stay side by side, plus one that follows on the full width.
        course("e", "数据库", 2, 1, 2),
        course("f", "编译原理", 2, 1, 4),
        course("g", "大学物理", 2, 5, 6),
        // Wednesday: ten distinct cells that all overlap in period 2, the most one spot can hold.
    ) + wednesday

    private val wednesdayIds = (1..10).map { "w$it" }

    private fun chips() = compose.onAllNodes(
        hasTestTag("schedule-course-more") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    )

    @Test fun crowdedDaysKeepTheNearestAndFoldTheRest() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show()
        save("week")

        // Two overlapping courses sit side by side and never fold; a following one returns to full width.
        val e = bounds("e")
        val f = bounds("f")
        assertTrue("a pair takes two lanes: $e / $f", f.left >= e.right - 1f || e.left >= f.right - 1f)
        assertTrue("a following course returns to the full day width: ${bounds("g")}", bounds("g").width > e.width * 1.5f)

        // Monday has three at once: the nearest stay on the left, the rest sit behind one chip on the right.
        val mondayDrawn = listOf("a", "b", "c").filter { drawn(it) }
        assertTrue("some Monday course stays: $mondayDrawn", mondayDrawn.isNotEmpty())
        assertTrue("some Monday course folds: $mondayDrawn", mondayDrawn.size < 3)
        assertTrue("a later course keeps its own row", drawn("d"))
        val chipList = chips()
        assertTrue("Monday and Wednesday each need a chip", chipList.fetchSemanticsNodes().size >= 2)
        val mondayChip = chipList[0].fetchSemanticsNode().boundsInRoot
        mondayDrawn.forEach { id ->
            assertTrue("the chip sits right of $id: ${bounds(id)} / $mondayChip", mondayChip.left >= bounds(id).right - 1f)
        }

        chipList[0].performClick()
        compose.waitForIdle()
        save("expanded")
        // The dialog lists what the chip folded and nothing that is still drawn.
        listOf("a", "b", "c").forEach { id ->
            val title = courses.first { it.id == id }.title
            val inDialog = compose.onAllNodes(hasText(title) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
            assertTrue("$title is in the dialog exactly when it is folded", inDialog == !drawn(id))
        }
    }

    /** Ten in one spot is the limit: the nearest stay on the left, the others fold into one chip. */
    @Test fun tenCoursesInOneSpotFoldIntoOneChip() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(courseList = wednesday)
        save("ten")
        val cards = wednesdayIds.count { drawn(it) }
        assertTrue("one or two cards stay on the left: $cards", cards in 1..2)
        val chipList = chips()
        assertEquals("one chip carries the rest", 1, chipList.fetchSemanticsNodes().size)
        chipList[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        save("ten-expanded")
        val rows = wednesdayIds.count { id ->
            compose.onAllNodes(hasTestTag("schedule-overflow-row-$id") and hasAnyAncestor(isDialog()), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("the dialog lists everything that is not a card", 10 - cards, rows)
    }

    /**
     * A course that does not run this week must not borrow today's clock: the folded list greys it,
     * labels it "not this week" instead of a countdown, names the weeks it does run, and keeps it
     * below the courses that do run.
     */
    @Test fun foldedCoursesFromOtherWeeksAreGreyAndSortLast() {
        assumeTrue(args.getString("crowdedQa") == "true")
        val crowdedMorning = listOf(
            course("run1", "汇编语言", 1, 1, 2),
            course("run2", "组成原理", 1, 1, 4),
            course("run3", "数值分析", 1, 1, 6),
            CourseItem(
                "x1", "编译原理", location = "东13-C-315",
                weeks = (10..12).toList(), time = CourseTimeSlot(1, 1, 8),
            ),
        )
        val otherWeekOnly = crowdedMorning.map { item ->
            item.copy(id = "other-${item.id}", weeks = (10..12).toList(), time = item.time.copy(dayOfWeek = 3))
        }
        val narrow = args.getString("overflowNarrow") == "true"
        show(
            widen = !narrow,
            dark = args.getString("overflowDark") == "true",
            accent = if (args.getString("overflowOrange") == "true") ThemeAccent.Orange else ThemeAccent.Green,
            courseList = crowdedMorning + otherWeekOnly,
        )
        val chipList = chips()
        assertEquals("the mixed and other-week-only groups each fold into a chip", 2, chipList.fetchSemanticsNodes().size)
        save("mixed-week-chips")
        if (narrow) chipList[0].performTouchInput { click() } else chipList[0].performClick()
        compose.waitForIdle()
        save("other-week")

        val otherNode = compose.onNode(
            hasTestTag("schedule-overflow-row-x1") and hasAnyAncestor(isDialog()),
            useUnmergedTree = false,
        ).fetchSemanticsNode()
        val otherRow = otherNode.boundsInRoot
        // The row reads title, "not this week", the weeks line and the time/room/teacher line, and nothing
        // else: a countdown would add a fifth text.
        val rowTexts = otherNode.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        assertTrue(
            "the row says it is another week: $rowTexts",
            text(ScheduleR.string.schedule_status_other_week) in rowTexts,
        )
        assertEquals("no countdown for another week's course: $rowTexts", 4, rowTexts.size)
        // "Not this week" is not enough on its own: the row names the exact weeks it does run.
        val weeksLine = context.resources.getQuantityString(ScheduleR.plurals.schedule_overflow_runs_weeks, 3, "10-12")
        assertTrue("the row names its weeks: $rowTexts", weeksLine in rowTexts)
        // A course running this week says so outright.
        val runningNode = compose.onNode(
            hasTestTag("schedule-overflow-row-run1") and hasAnyAncestor(isDialog()),
            useUnmergedTree = false,
        )
        val runningTexts = runningNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        assertTrue(
            "this week's course says so: $runningTexts",
            text(ScheduleR.string.schedule_overflow_this_week) in runningTexts,
        )
        val runningWeeks = context.resources.getQuantityString(ScheduleR.plurals.schedule_overflow_runs_weeks, 18, "1-18")
        assertTrue("this week's course also names its weeks: $runningTexts", runningWeeks in runningTexts)
        // The course that runs this week sits above it.
        val runningRow = runningNode.fetchSemanticsNode().boundsInRoot
        assertTrue("other weeks come last: $runningRow / $otherRow", otherRow.top >= runningRow.bottom - 1f)
        compose.onNode(hasText(text(ScheduleR.string.schedule_event_group_close)) and hasAnyAncestor(isDialog()))
            .performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    /** Language-neutral shots for the release notes: the week, then the folded courses behind the chip. */
    @Test fun announcementShots() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show()
        save("week")
        // A semantic click works wherever the chip sits, even partly off-screen at this density.
        chips()[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        save("expanded")
    }

    @Test fun wednesdayKeepsOnlyTheNearestAndFoldsTheRest() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show()
        val visible = wednesdayIds.count { drawn(it) }
        assertTrue("one or two nearest stay drawn: $visible", visible in 1..2)
    }

    @Test fun morningKeepsTheCourseInProgress() {
        assumeTrue(args.getString("crowdedQa") == "true")
        // 08:10: A is in progress, B and C start later; only the nearest stays beside the chip.
        val morning = listOf(
            course("a", "汇编语言", 1, 1, 2),
            course("b", "组成原理", 1, 1, 4, from = "09:00", to = "11:40"),
            course("c", "数值分析", 1, 1, 6, from = "09:20", to = "15:35"),
        )
        show(now = java.time.LocalTime.of(8, 10), courseList = morning)
        assertTrue("a is in progress", drawn("a"))
        assertTrue("b waits behind the chip", !drawn("b"))
        assertTrue("c waits behind the chip", !drawn("c"))
        assertTrue("a chip holds them", chips().fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun wideGridGivesBusyDaysTwoCardLanesAndTheChip() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(scale = 0.4f)
        save("wide")
        // A roomy grid allows three lanes: two cards side by side and the chip on the third.
        val visible = wednesdayIds.filter { drawn(it) }
        assertEquals("two nearest stay drawn", 2, visible.size)
        val lefts = visible.map { bounds(it).left }.distinct()
        assertEquals("on two separate lanes", 2, lefts.size)
        assertTrue("the rest sits behind a chip", chips().fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun darkThemeWithLargeFontStaysReadable() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false, dark = true, fontScale = 1.3f)
        save("dark-large")
        val ids = listOf("a", "b", "c", "d", "e", "f", "g") + wednesdayIds
        assertTrue("something is drawn", ids.any { drawn(it) })
        // Whatever the width, nothing is drawn as an unreadable strip.
        val density = context.resources.displayMetrics.density
        ids.filter { drawn(it) }.forEach { id ->
            val width = bounds(id).width / density
            assertTrue("$id is a strip: ${width}dp wide", width >= 30f)
        }
    }

    @Test fun theSavedPeriodColumnWidthIsHonouredAndTheDaysStartAfterIt() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false)
        val density = context.resources.displayMetrics.density
        fun gutterWidthAt(setting: Int): Float {
            display.value = display.value.copy(timeColumnWidthDp = setting)
            compose.waitForIdle()
            val gutter = compose.onNode(
                hasTestTag("schedule-time-column") and hasAnyAncestor(hasTestTag("schedule-current-week")),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertTrue("the days start after a $setting dp gutter", bounds("e").left >= gutter.right - 1f)
            return gutter.width / density
        }
        val narrow = gutterWidthAt(24)
        val wide = gutterWidthAt(60)
        save("time-column-60")
        assertTrue("24dp setting gives a ${narrow}dp gutter", kotlin.math.abs(narrow - 24f) < 3f)
        assertTrue("60dp setting gives a ${wide}dp gutter", kotlin.math.abs(wide - 60f) < 3f)
    }

    @Test fun narrowPhoneNeverDrawsStripCards() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false)
        save("narrow")
        val density = context.resources.displayMetrics.density
        val ids = listOf("a", "b", "c", "d", "e", "f", "g") + wednesdayIds
        assertTrue("something is drawn", ids.any { drawn(it) })
        ids.filter { drawn(it) }.forEach { id ->
            val width = bounds(id).width / density
            assertTrue("$id is a strip: ${width}dp wide", width >= 34f)
        }
        // Whatever is not drawn sits behind a chip.
        assertTrue("crowded days keep a chip", chips().fetchSemanticsNodes().isNotEmpty())
    }

    private fun text(id: Int) = context.getString(id)

    private fun drawn(id: String) = compose.onAllNodes(
        hasTestTag("schedule-course-$id") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(id: String) = compose.onNode(
        hasTestTag("schedule-course-$id") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private val display = androidx.compose.runtime.mutableStateOf(com.x500x.cursimple.core.data.ScheduleDisplayPreferences())

    private fun show(widen: Boolean = true, scale: Float = 0.72f, dark: Boolean = false, fontScale: Float? = null, now: java.time.LocalTime = java.time.LocalTime.of(9, 45), courseList: List<CourseItem> = courses, accent: ThemeAccent = ThemeAccent.Green) {
        // The app resets the debug clock from its saved preference shortly after start; set it again.
        Thread.sleep(1500)
        BeijingTime.setForcedNow(monday.atTime(now))
        compose.setContent {
            // A shrunken density gives every day room for two lanes whatever the emulator's screen is.
            val base = LocalDensity.current
            val density = if (widen) Density(base.density * scale, fontScale ?: base.fontScale) else if (fontScale != null) Density(base.density, fontScale) else base
            CompositionLocalProvider(LocalDensity provides density) {
            ClassScheduleTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, themeAccent = accent) {
                ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courseList),
                    onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {}, overrideTermStart = termStart, scheduleDisplay = display.value,
                    weekOffset = 0, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18)
            }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = ScreenshotCapture.take()
        ScreenshotCapture.save(bitmap, File(context.filesDir, "crowded-qa/$name.png"))
    }
}
