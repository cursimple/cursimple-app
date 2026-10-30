package com.x500x.cursimple.app

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

    private val courses = listOf(
        // Monday: A, B and C overlap at 09:00-09:35, D follows. At 09:45 B and C are ongoing.
        course("a", "汇编语言", 1, 1, 2),
        course("b", "组成原理", 1, 1, 2, from = "08:30", to = "10:00"),
        course("c", "数值分析", 1, 1, 4, from = "09:00", to = "11:00"),
        course("d", "操作系统", 1, 3, 4),
        // Tuesday: two overlapping courses plus one that follows on the same lane.
        course("e", "数据库", 2, 1, 2),
        course("f", "编译原理", 2, 1, 2, from = "09:00", to = "10:30"),
        course("g", "大学物理", 2, 5, 6),
        // Wednesday: four at once.
        course("h1", "课程一", 3, 5, 6, from = "14:00", to = "15:30"),
        course("h2", "课程二", 3, 5, 6, from = "14:10", to = "15:35"),
        course("h3", "课程三", 3, 5, 6, from = "14:20", to = "15:40"),
        course("h4", "课程四", 3, 5, 6, from = "14:30", to = "15:45"),
    )

    @Test fun crowdedDaysKeepTwoLanesAndFoldTheRest() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show()
        save("week")

        // Two overlapping courses sit side by side; the later one stays on its own lane.
        val e = bounds("e")
        val f = bounds("f")
        assertTrue("overlapping courses take two lanes: $e / $f", f.left >= e.right - 1f)
        assertTrue("a following course returns to the full day width: ${bounds("g")}", bounds("g").width > e.width * 1.5f)

        // Three at once: the two nearest sit side by side, a later non-overlapping course follows on the
        // left, and the course that finished stays behind the chip under the right-hand card.
        assertTrue("b stays", drawn("b"))
        assertTrue("c stays", drawn("c"))
        assertTrue("d stays", drawn("d"))
        assertTrue("a folds away", !drawn("a"))
        val b = bounds("b")
        val c = bounds("c")
        assertTrue("two nearest take two lanes: $b / $c", c.left >= b.right - 1f)
        val chips = compose.onAllNodes(
            hasTestTag("schedule-course-more") and hasAnyAncestor(hasTestTag("schedule-current-week")),
            useUnmergedTree = true,
        )
        assertTrue("Monday needs one chip and Wednesday one", chips.fetchSemanticsNodes().size >= 2)
        val chip = chips[0].fetchSemanticsNode().boundsInRoot
        assertTrue("chip sits on the right lane: $c / $chip", chip.left >= b.right - 1f)
        assertTrue("chip sits below the right-hand card: $c / $chip", chip.top >= c.bottom - 1f || chip.top >= c.top)

        chips[0].performClick()
        compose.waitForIdle()
        save("expanded")
        // The dialog lists exactly what the chip folded away.
        compose.onNode(hasText("汇编语言") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        // Each folded course shows when it runs and where it stands against the clock.
        compose.onNode(hasText("08:00–09:35", substring = true) and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Finished") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("数值分析") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("组成原理") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun wednesdayKeepsTwoOfFourSimultaneousCourses() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show()
        val visible = listOf("h1", "h2", "h3", "h4").count { drawn(it) }
        assertEquals("the two nearest stay drawn", 2, visible)
    }

    @Test fun morningKeepsTheCourseInProgressAndTheNextOne() {
        assumeTrue(args.getString("crowdedQa") == "true")
        // 08:10: A is in progress and B starts next, so C is the one behind the chip.
        show(now = java.time.LocalTime.of(8, 10))
        assertTrue("a is in progress", drawn("a"))
        assertTrue("b is next", drawn("b"))
        assertTrue("c waits behind the chip", !drawn("c"))
    }

    @Test fun wideGridGivesBusyDaysThreeLanes() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(scale = 0.4f)
        save("wide")
        // Wednesday's four simultaneous courses: three side by side, one behind the chip.
        val visible = listOf("h1", "h2", "h3", "h4").filter { drawn(it) }
        assertEquals("three nearest stay drawn", 3, visible.size)
        val lefts = visible.map { bounds(it).left }.distinct()
        assertEquals("on three separate lanes", 3, lefts.size)
    }

    @Test fun darkThemeWithLargeFontStaysReadable() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false, dark = true, fontScale = 1.3f)
        save("dark-large")
        val density = context.resources.displayMetrics.density
        val ids = listOf("a", "b", "c", "d", "e", "f", "g", "h1", "h2", "h3", "h4").filter { drawn(it) }
        assertTrue("something is drawn", ids.isNotEmpty())
        ids.forEach { id -> assertTrue("$id is a strip", bounds(id).width / density >= 34f) }
    }

    @Test fun theSavedPeriodColumnWidthMovesTheGrid() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false)
        val density = context.resources.displayMetrics.density
        val automatic = bounds("e").left / density
        display.value = display.value.copy(timeColumnWidthDp = 24)
        compose.waitForIdle()
        val narrow = bounds("e").left / density
        display.value = display.value.copy(timeColumnWidthDp = 60)
        compose.waitForIdle()
        val wide = bounds("e").left / density
        save("time-column-60")
        assertTrue("24dp ($narrow) is slimmer than automatic ($automatic)", narrow < automatic - 2f)
        assertTrue("60dp ($wide) is wider than automatic ($automatic)", wide > automatic + 10f)
        assertEquals("the grid shifts by the width difference", 36f, wide - narrow, 4f)
    }

    @Test fun narrowPhoneNeverDrawsStripCards() {
        assumeTrue(args.getString("crowdedQa") == "true")
        show(widen = false)
        save("narrow")
        val density = context.resources.displayMetrics.density
        val ids = listOf("a", "b", "c", "d", "e", "f", "g", "h1", "h2", "h3", "h4").filter { drawn(it) }
        assertTrue("something is drawn", ids.isNotEmpty())
        ids.forEach { id ->
            val width = bounds(id).width / density
            assertTrue("$id is a strip: ${width}dp wide", width >= 34f)
        }
        // Whatever is not drawn sits behind a chip.
        assertTrue("crowded days keep a chip", compose.onAllNodes(
            hasTestTag("schedule-course-more") and hasAnyAncestor(hasTestTag("schedule-current-week")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty())
    }

    private fun drawn(id: String) = compose.onAllNodes(
        hasTestTag("schedule-course-$id") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(id: String) = compose.onNode(
        hasTestTag("schedule-course-$id") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private val display = androidx.compose.runtime.mutableStateOf(com.x500x.cursimple.core.data.ScheduleDisplayPreferences())

    private fun show(widen: Boolean = true, scale: Float = 0.72f, dark: Boolean = false, fontScale: Float? = null, now: java.time.LocalTime = java.time.LocalTime.of(9, 45)) {
        // The app resets the debug clock from its saved preference shortly after start; set it again.
        Thread.sleep(1500)
        BeijingTime.setForcedNow(monday.atTime(now))
        compose.setContent {
            // A shrunken density gives every day room for two lanes whatever the emulator's screen is.
            val base = LocalDensity.current
            val density = if (widen) Density(base.density * scale, fontScale ?: base.fontScale) else if (fontScale != null) Density(base.density, fontScale) else base
            CompositionLocalProvider(LocalDensity provides density) {
            ClassScheduleTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses),
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
