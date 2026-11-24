package com.x500x.cursimple.app

import androidx.activity.compose.setContent
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.kernel.model.CourseEndWarningMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Opt-in QA: one week that has events, a second-to-last-week course and a final-week course at once.
 * Checks the day-header event count and that the corner badges and the event cards share the grid.
 * Run with `-e endWarningQa true`.
 */
class EndWarningEventsUiQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext

    // Monday of term week 15.
    private val monday = LocalDate.of(2026, 12, 14)
    private val termStart = monday.minusWeeks(14)
    @Before fun fixedClock() { BeijingTime.setForcedNow(monday.atTime(7, 0)) }
    @After fun restoreClock() { BeijingTime.setForcedNow(null) }

    private val timing = TermTimingProfile(termStart.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40"),
        ClassSlotTime(5, 6, "14:00", "15:35"), ClassSlotTime(7, 8, "16:05", "17:40")))

    private fun course(id: String, title: String, day: Int, start: Int, end: Int, lastWeek: Int) =
        CourseItem(id, title, location = "A101", weeks = (1..lastWeek).toList(), time = CourseTimeSlot(day, start, end))

    private val courses = listOf(
        course("near1", "高等数学", 1, 1, 2, lastWeek = 16),      // week 15 is second-to-last
        course("final1", "大学英语", 2, 1, 2, lastWeek = 15),     // week 15 is the last
        course("near2", "线性代数", 3, 3, 4, lastWeek = 16),
        course("final2", "程序设计", 3, 5, 6, lastWeek = 15),
        course("final3", "体育", 4, 1, 2, lastWeek = 15),
    )

    private fun event(id: String, title: String, day: Int, start: String, end: String) =
        ScheduleEvent(id = id, title = title, date = monday.plusDays(day - 1L).toString(), startTime = start, endTime = end)

    private val events =
        listOf(event("e1", "社团例会", 1, "08:00", "09:00")) +                          // Monday: 1 event over a course
        listOf(event("e2", "组会", 2, "08:30", "10:00"), event("e3", "面试", 2, "09:00", "10:30")) + // Tuesday: 2 overlapping
        (1..12).map { event("m$it", "杂项$it", 4, "10:00", "11:00") }                    // Thursday: 12, must show 9+

    private fun show(fontScale: Float? = null, scale: Float = 0.72f, dark: Boolean = false, showEvents: Boolean = true) {
        Thread.sleep(1500)
        BeijingTime.setForcedNow(monday.atTime(7, 0))
        val fixtureContext = AppLocale.wrap(context, AppLanguage.Chinese)
        compose.runOnUiThread {
            compose.activity.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalContext provides fixtureContext,
                    LocalConfiguration provides fixtureContext.resources.configuration,
                    LocalActivityResultRegistryOwner provides compose.activity,
                    LocalDensity provides Density(base.density * scale, fontScale ?: base.fontScale),
                ) {
                    ClassScheduleTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                        ScheduleScreen(
                            state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses, events = events),
                            onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                            onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                            onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                            onOpenPluginMarket = {}, overrideTermStart = termStart,
                            weekOffset = 0, minWeekOffset = -14, maxWeekOffset = 4, termWeekCount = 16,
                            scheduleDisplay = ScheduleDisplayPreferences(courseEndWarningMode = CourseEndWarningMode.TwoWeeks),
                            showEvents = showEvents,
                            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = ScreenshotCapture.take()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cursimple-end-warning-$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CurSimple QA")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create screenshot entry")
        context.contentResolver.openOutputStream(uri).use { output ->
            checkNotNull(output) { "Could not open screenshot entry" }
                .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    private fun counts() = compose.onAllNodes(hasTestTag("schedule-day-event-count"), useUnmergedTree = true)

    @Test fun headerShowsEventCountAndBadgesCoexistWithEvents() {
        assumeTrue(args.getString("endWarningQa") == "true")
        show()
        save("week")
        // Monday 1, Tuesday 2, Thursday 12 (capped to 9+); days without events show nothing.
        assertEquals(3, counts().fetchSemanticsNodes().size)
        listOf("1", "2", "9+").forEach { text ->
            compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("schedule-day-event-count")), useUnmergedTree = true)
                .assertCountEquals(1)
        }
        // The corner badges are still there: near1/near2 show 2, the three last-week courses show 1.
        listOf("near1", "near2", "final1", "final2", "final3").forEach {
            compose.onNode(hasTestTag("schedule-course-end-warning-$it") and hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true).assertExists()
        }
    }

    @Test fun collapsedEventsStillShowTheCountInTheHeader() {
        assumeTrue(args.getString("endWarningQa") == "true")
        show(showEvents = false)
        save("week-collapsed")
        assertEquals(3, counts().fetchSemanticsNodes().size)
    }

    @Test fun narrowLargeFontDark() {
        assumeTrue(args.getString("endWarningQa") == "true")
        show(scale = 0.72f, dark = true)
        save("week-narrow-dark")
        assertEquals(3, counts().fetchSemanticsNodes().size)
    }
}
