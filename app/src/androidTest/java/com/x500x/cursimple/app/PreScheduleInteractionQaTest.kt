package com.x500x.cursimple.app

import android.app.UiAutomation
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Opt-in, in-memory interaction checks for the features introduced on pre. */
class PreScheduleInteractionQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val monday = LocalDate.of(2026, 9, 14)
    private val offset = mutableIntStateOf(0)
    private val indicator = mutableStateOf(false)
    private val display = mutableStateOf(ScheduleDisplayPreferences())

    @Before fun prepare() {
        assumeTrue(args.getString("preScheduleQa") == "true")
        instrumentation.uiAutomation.setRotation(if (args.getString("qaVariant").orEmpty().contains("land"))
            UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        Thread.sleep(1000)
        BeijingTime.setForcedNow(monday.atTime(8, 47, 30))
    }

    @After fun restore() { BeijingTime.setForcedNow(null) }

    private fun show() {
        val timing = TermTimingProfile(monday.toString(), listOf(
            ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40"),
            ClassSlotTime(5, 6, "14:00", "15:35"), ClassSlotTime(7, 8, "16:05", "17:40"),
            ClassSlotTime(9, 10, "19:00", "20:35"), ClassSlotTime(11, 12, "20:45", "22:20")))
        val courses = listOf(CourseItem("pre-math", "高等数学", location = "一教101",
            weeks = (1..3).toList(), time = CourseTimeSlot(1, 1, 2)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppZone provides ZoneId.of("Asia/Shanghai"),
                LocalDensity provides Density(density.density, args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                ClassScheduleTheme(themeMode = if (args.getString("qaTheme") == "dark") ThemeMode.Dark else ThemeMode.Light,
                    themeAccent = if (args.getString("qaAccent") == "Blue") ThemeAccent.Blue else ThemeAccent.Green) {
                    ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses),
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {}, onOpenPluginMarket = {},
                        onWeekOffsetChange = { offset.intValue = it }, weekOffset = offset.intValue,
                        minWeekOffset = 0, maxWeekOffset = 2, overrideTermStart = monday, termWeekCount = 3,
                        scheduleDisplay = display.value.copy(timelineIndicatorEnabled = indicator.value))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun horizontalWeekSwipesRespectBoundariesAndVerticalScrolling() {
        show()
        val grid = compose.onNodeWithTag("schedule-week-pager")
        grid.performTouchInput { swipeLeft(durationMillis = 450) }
        compose.waitUntil(5000) { offset.intValue == 1 }
        compose.waitForIdle()
        grid.performTouchInput { swipeUp(durationMillis = 450) }
        compose.waitForIdle()
        assertEquals("Vertical scrolling must not switch weeks", 1, offset.intValue)
        grid.performTouchInput { swipeLeft(durationMillis = 450) }
        compose.waitUntil(5000) { offset.intValue == 2 }
        grid.performTouchInput { swipeLeft(durationMillis = 450) }
        compose.waitForIdle()
        assertEquals(2, offset.intValue)
        repeat(2) {
            grid.performTouchInput { swipeRight(durationMillis = 450) }
            compose.waitForIdle()
        }
        assertEquals(0, offset.intValue)
        grid.performTouchInput { swipeRight(durationMillis = 450) }
        compose.waitForIdle()
        assertEquals(0, offset.intValue)
        save("week-swipes")
    }

    @Test fun timelineUsesTheSharedClockAndDrawsOverTheCurrentCourse() {
        show()
        val grid = compose.onNodeWithTag("schedule-week-pager")
        fun redPixels(): Int {
            val image = grid.captureToImage().asAndroidBitmap()
            var count = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (image.getPixel(x, y) == 0xFFE53935.toInt()) count++
            }
            return count
        }
        assertEquals("Indicator defaults to off", 0, redPixels())
        compose.runOnIdle { indicator.value = true }
        compose.waitForIdle()
        save("timeline-on")
        val cards = compose.onAllNodesWithTag("schedule-course-pre-math", useUnmergedTree = true)
        val card = (0 until cards.fetchSemanticsNodes().size).map { cards[it] }.first { it.isDisplayed() }.fetchSemanticsNode().boundsInRoot
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val image = grid.captureToImage().asAndroidBitmap()
        val middle = ((card.top + card.bottom) / 2f - gridBounds.top).toInt()
        val left = (card.left - gridBounds.left).toInt().coerceAtLeast(0)
        val right = (card.right - gridBounds.left).toInt().coerceAtMost(image.width - 1)
        var correctPixels = 0
        for (y in (middle - 8).coerceAtLeast(0)..(middle + 8).coerceAtMost(image.height - 1)) {
            for (x in left..right) if (image.getPixel(x, y) == 0xFFE53935.toInt()) correctPixels++
        }
        assertTrue("Shared 08:47 clock must draw halfway through the 08:00-09:35 course", correctPixels > 20)
        grid.performTouchInput { swipeLeft(durationMillis = 450) }
        compose.waitUntil(5000) { offset.intValue == 1 }
        compose.waitForIdle()
        assertEquals("Other weeks must not show today's line", 0, redPixels())
    }

    @Test fun courseTapOpensDetailsWithoutSwitchingWeek() {
        show()
        val cards = compose.onAllNodesWithTag("schedule-course-pre-math", useUnmergedTree = true)
        (0 until cards.fetchSemanticsNodes().size).map { cards[it] }.first { it.isDisplayed() }.performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        assertEquals(0, offset.intValue)
        save("course-details")
    }

    private fun save(name: String) {
        val file = File(instrumentation.targetContext.filesDir,
            "pre-regression/${args.getString("qaVariant", "portrait")}/$name.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }
}
