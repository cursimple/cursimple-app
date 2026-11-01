package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
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
import java.io.File
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in visual and collision checks: -e overlapBadgeQa true. */
class OverlapCountBadgeUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After fun restoreClock() = BeijingTime.setForcedNow(null)

    @Test fun regularLightGreen() = checkBadge(411, false, ThemeAccent.Green, 1f)
    @Test fun regularDarkBlue() = checkBadge(411, true, ThemeAccent.Blue, 1f)
    @Test fun narrowLightLargeFont() = checkBadge(320, false, ThemeAccent.Green, 1.3f)
    @Test fun narrowDarkLargeFont() = checkBadge(320, true, ThemeAccent.Blue, 1.3f)

    private fun checkBadge(widthDp: Int, dark: Boolean, accent: ThemeAccent, fontScale: Float) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("overlapBadgeQa") == "true")
        Thread.sleep(1500)
        BeijingTime.setForcedNow(LocalDate.of(2026, 10, 26).atTime(9, 45))
        val density = context.resources.displayMetrics.widthPixels / widthDp.toFloat()
        val all = (1..18).toList()
        fun course(id: String, title: String, day: Int, node: Int, weeks: List<Int> = all) =
            CourseItem(id, title, location = "教学楼101", weeks = weeks, time = CourseTimeSlot(day, node, node))
        val courses = listOf(
            course("badge-main", "学术英语阅读与写作", 4, 2),
            course("badge-hidden", "高级编程实验", 4, 2, listOf(14, 15)),
            course("badge-left", "数据库原理", 3, 2),
            course("badge-above", "计算机组成", 4, 1),
        )
        val timing = TermTimingProfile(
            "2026-09-07",
            listOf(
                ClassSlotTime(1, 1, "08:00", "09:35"),
                ClassSlotTime(2, 2, "10:05", "11:40"),
                ClassSlotTime(3, 3, "12:00", "13:35"),
                ClassSlotTime(4, 4, "14:00", "15:35"),
                ClassSlotTime(5, 5, "16:05", "17:40"),
                ClassSlotTime(6, 6, "18:00", "18:45"),
                ClassSlotTime(7, 7, "19:00", "20:35"),
                ClassSlotTime(8, 8, "20:45", "22:20"),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                ClassScheduleTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, themeAccent = accent) {
                    ScheduleScreen(
                        state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses),
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {},
                        onRestoreExamReminder = {}, onRemoveReminderRule = {}, onRemoveManualCourse = {},
                        onCreateBulkReminder = { _, _, _ -> }, onPrevWeek = {}, onNextWeek = {},
                        onPrevDay = {}, onNextDay = {}, onResetDay = {}, onOpenPluginMarket = {},
                        overrideTermStart = LocalDate.of(2026, 9, 7), weekOffset = 0,
                        minWeekOffset = -7, maxWeekOffset = 10, termWeekCount = 18,
                    )
                }
            }
        }
        compose.waitForIdle()
        fun bounds(tag: String): Rect = compose.onNode(
            hasTestTag(tag) and hasAnyAncestor(hasTestTag("schedule-current-week")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val badge = bounds("schedule-course-count-badge-main")
        val card = bounds("schedule-course-badge-main")
        val title = bounds("schedule-course-title-badge-main")
        val left = bounds("schedule-course-badge-left")
        val above = bounds("schedule-course-badge-above")
        assertTrue("Badge must be larger than the old 10dp: $badge", badge.height / density >= 11.5f)
        assertTrue("Badge must stay within the card's right edge", badge.right <= card.right + 1f)
        assertTrue("Badge must not cover the title: $badge / $title", title.top >= badge.bottom - 1f)
        assertTrue("Badge must not cover the left card: $badge / $left", badge.left >= left.right - 1f)
        assertTrue("Badge must not cover the course above: $badge / $above", badge.top >= above.bottom - 1f)
        ScreenshotCapture.save(
            ScreenshotCapture.take(),
            File(context.filesDir, "overlap-count-qa/$widthDp-$dark-$fontScale.png"),
        )
    }
}
