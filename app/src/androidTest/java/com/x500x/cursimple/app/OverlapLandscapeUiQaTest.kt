package com.x500x.cursimple.app

import android.app.UiAutomation
import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.After
import com.x500x.cursimple.core.kernel.time.BeijingTime
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Opt-in QA for partly overlapping courses and short (landscape) viewports; in-memory data only.
 * Run with `-e overlapQa true`, once per orientation.
 */
class OverlapLandscapeUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext

    private val monday = LocalDate.of(2026, 9, 14)
    @Before fun fixedClock() { BeijingTime.setForcedNow(monday.atTime(12, 0)) }
    @After fun restoreClock() { BeijingTime.setForcedNow(null) }
    private val termStart = monday.minusWeeks(6)
    private val timing = TermTimingProfile(termStart.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40"),
        ClassSlotTime(5, 6, "14:00", "15:35"), ClassSlotTime(7, 8, "16:05", "17:40"),
        ClassSlotTime(9, 9, "18:00", "18:45"), ClassSlotTime(10, 11, "19:00", "20:35"),
        ClassSlotTime(12, 13, "20:45", "22:20")))
    private val all = (1..18).toList()

    private fun course(id: String, title: String, day: Int, start: Int, end: Int, weeks: List<Int> = all,
        from: String? = null, to: String? = null) =
        CourseItem(id, title, location = "东13-C-315", weeks = weeks, time = CourseTimeSlot(day, start, end),
            reminderStartTime = from, reminderEndTime = to)

    private val courses = listOf(
        course("asm", "汇编语言与微型计算机技术", 1, 1, 2),
        course("org", "计算机组成与系统结构", 1, 3, 4),
        course("num", "数值分析", 1, 7, 8),
        course("talk", "学术讲座", 1, 7, 9, from = "17:00", to = "18:30"),
        course("db", "数据库原理及应用", 2, 1, 2),
        course("web", "web上机", 2, 10, 13, weeks = (6..8).toList(), from = "18:30", to = "21:30"),
        course("ai", "AI创意工坊：从灵感到应用", 3, 10, 11),
        course("dblab", "数据库上机", 4, 10, 13, weeks = (6..8).toList(), from = "18:30", to = "21:30"),
        course("mllab", "机器学习上机", 4, 12, 13, weeks = (10..12).toList()),
        course("asm2", "汇编语言与微型计算机技术", 5, 10, 11),
        course("asm3", "汇编语言实验", 5, 12, 13, weeks = (10..12).toList()),
        course("ican", "ican", 6, 1, 2),
        course("lab1", "数值分析上机", 7, 1, 2, weeks = (10..12).toList()),
        course("lab2", "最优化方法", 7, 1, 2, weeks = (10..12).toList()),
    )

    @Test fun overlappingCoursesSitAtTheirOwnTimes() {
        assumeTrue(args.getString("overlapQa") == "true")
        show()
        card("mllab").performScrollTo()
        save("week")
        val db = card("dblab").fetchSemanticsNode().boundsInRoot
        val ml = card("mllab").fetchSemanticsNode().boundsInRoot
        assertTrue("Both lab cards must be visible", db.width > 0f && ml.width > 0f)
        // Side by side at their own clock positions instead of halves of one stacked cell.
        assertTrue("lab cards must share the day side by side: $db / $ml", ml.left >= db.right - 1f || db.left >= ml.right - 1f)
        assertTrue("later course must start lower: $db / $ml", ml.top > db.top + 10f)
    }

    @Test fun shortViewportScrollsToTheLastPeriod() {
        assumeTrue(args.getString("overlapQa") == "true")
        show()
        save("short-top")
        card("asm3").performScrollTo()
        compose.waitForIdle()
        save("short-bottom")
        // The last evening course must become fully visible inside the window.
        card("asm3").assertIsDisplayed()
        val bottom = card("asm3").fetchSemanticsNode().boundsInRoot.bottom
        val height = context.resources.displayMetrics.heightPixels
        assertTrue("last period must scroll into view: $bottom / $height", bottom <= height)
    }

    private fun show() {
        val landscape = args.getString("qaVariant").orEmpty().contains("land")
        instrumentation.uiAutomation.setRotation(
            if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0,
        )
        Thread.sleep(1500)
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses),
                    onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {}, overrideTermStart = termStart,
                    weekOffset = 0, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18)
            }
        }
        compose.waitForIdle()
    }

    private fun card(id: String): SemanticsNodeInteraction {
        return compose.onNode(hasTestTag("schedule-course-$id") and
            hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = ScreenshotCapture.take()
        val file = File(context.filesDir, "overlap-qa/${args.getString("qaVariant") ?: "portrait"}/$name.png")
        ScreenshotCapture.save(bitmap, file)
    }
}
