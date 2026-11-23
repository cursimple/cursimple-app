package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class GridLinesUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val monday = LocalDate.of(2026, 9, 14)
    @Before fun clock() { BeijingTime.setForcedNow(monday.atTime(9, 0)) }
    @After fun restore() { BeijingTime.setForcedNow(null) }

    private val timing = TermTimingProfile(monday.minusWeeks(1).toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40")))

    @Test fun solidLinesAreDrawn() {
        assumeTrue(args.getString("gridQa") == "true")
        render(dashed = false, name = "solid.png")
    }

    @Test fun dashedLinesAreDrawn() {
        assumeTrue(args.getString("gridQa") == "true")
        render(dashed = true, name = "dashed.png")
    }

    private fun render(dashed: Boolean, name: String) {
        Thread.sleep(1500)
        BeijingTime.setForcedNow(monday.atTime(9, 0))
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * 0.72f, base.fontScale)) {
                ClassScheduleTheme(ThemeMode.Light) {
                    ScheduleScreen(
                        state = ScheduleUiState(initialized = true, timingProfile = timing,
                            manualCourses = listOf(CourseItem("c1", "高数", location = "A1", weeks = (1..8).toList(), time = CourseTimeSlot(1, 1, 2)))),
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday.minusWeeks(1),
                        weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 2, termWeekCount = 8,
                        scheduleCardStyle = ScheduleCardStylePreferences(
                            gridBorderColorArgb = 0xFF000000, gridBorderOpacityPercent = 0,
                            gridBorderWidthDp = 1f, gridBorderDashed = dashed),
                    )
                }
            }
        }
        compose.waitForIdle(); Thread.sleep(400)
        compose.waitForIdle(); Thread.sleep(400)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "grid-qa/$name"))
    }
}
