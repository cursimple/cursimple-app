package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in layout checks for a dedicated QA emulator. */
class TodayOverviewUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val previewLanguage = object : ExternalResource() {
        override fun before() {
            val arguments = InstrumentationRegistry.getArguments()
            if (arguments.getString("todayOverviewQa") != "true") return
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (arguments.getString("todayOverviewLanguage") == "zh-CN") {
                runBlocking { DataStoreUserPreferencesRepository(context).setAppLanguage(AppLanguage.Chinese) }
                AppLocale.cache(context, AppLanguage.Chinese)
            }
            if (arguments.getString("todayOverviewHomeQa") == "true") seedMainTimetable(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(previewLanguage).around(compose)

    @Test fun countdownConflictAndAgendaAreVisible() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("todayOverviewQa") == "true")
        val activity = compose.activity
        val chinese = InstrumentationRegistry.getArguments().getString("todayOverviewLanguage") == "zh-CN"
        val context = activity
        val now = LocalDateTime.of(2026, 9, 7, 8, 30)
        runBlocking {
            val preferences = DataStoreUserPreferencesRepository(context)
            preferences.setDisclaimerAccepted(true)
            preferences.setFirstRunGuideCompleted(true)
            preferences.setDebugForcedDateTime(now)
        }
        val termStart = LocalDate.of(2026, 9, 7)
        val profile = TermTimingProfile(termStart.toString(), listOf(ClassSlotTime(1, 2, "08:00", "09:00"), ClassSlotTime(3, 4, "09:30", "10:30")))
        val courses = listOf(
            CourseItem("qa-a", "测试课程A", location = "A101", time = CourseTimeSlot(1, 1, 2)),
            CourseItem("qa-b", "测试课程B", location = "B202", time = CourseTimeSlot(1, 3, 4)),
            CourseItem("qa-c", "冲突课程C", location = "C303", time = CourseTimeSlot(1, 1, 2)),
        )
        val overviewEnabled = mutableStateOf(true)
        val viewMode = mutableStateOf(com.x500x.cursimple.feature.schedule.ScheduleViewMode.Day)
        compose.runOnUiThread {
            BeijingTime.setForcedNow(now)
            activity.setContent {
                ClassScheduleTheme(ThemeMode.Light) {
                    CompositionLocalProvider(LocalAppZone provides ZoneId.of("Asia/Shanghai")) {
                        ScheduleScreen(
                            state = ScheduleUiState(initialized = true, manualCourses = courses, timingProfile = profile),
                            overrideTermStart = termStart, holidayCalendar = HolidayCalendarSettings.NONE,
                            scheduleDisplay = ScheduleDisplayPreferences(todayOverviewEnabled = overviewEnabled.value),
                            viewMode = viewMode.value, dayOffset = 0,
                            minWeekOffset = 0, maxWeekOffset = 20,
                            onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                            onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                            onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {},
                            onResetDay = {}, onOpenPluginMarket = {}, modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("today-overview-card").assertIsDisplayed()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_today_minutes_to_end, 30L)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_today_conflict_short, 1)).assertIsDisplayed()
        saveScreenshot(if (chinese) "today-card-zh.png" else "today-card.png")
        compose.onNodeWithTag("today-overview-card").performClick()
        compose.onNodeWithTag("today-overview-sheet").assertIsDisplayed()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_conflict_pair_title, "测试课程A", "冲突课程C")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_today_count, 3, 1)).assertIsDisplayed()
        compose.waitForIdle(); Thread.sleep(900); saveScreenshot(if (chinese) "today-agenda-zh.png" else "today-agenda.png")
        compose.onNode(hasText("测试课程B") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription(context.getString(ScheduleR.string.schedule_action_close)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("today-overview-sheet").assertDoesNotExist()
        compose.runOnIdle { viewMode.value = com.x500x.cursimple.feature.schedule.ScheduleViewMode.Week }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("today-overview-card").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { viewMode.value = com.x500x.cursimple.feature.schedule.ScheduleViewMode.Day; overviewEnabled.value = false }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("today-overview-card").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun defaultHomeShowsFullTimetable() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("todayOverviewQa") == "true" && arguments.getString("todayOverviewHomeQa") == "true")
        val context = compose.activity
        compose.waitUntil(timeoutMillis = 10_000) {
            val courses = compose.onAllNodesWithText("大学英语")
            courses.fetchSemanticsNodes().indices.any { courses[it].isDisplayed() }
        }
        compose.onNodeWithText(context.getString(ScheduleR.string.schedule_today_details)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(com.x500x.cursimple.R.string.main_today_overview)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(com.x500x.cursimple.R.string.force_stop_prompt_title)).assertDoesNotExist()
        saveScreenshot("main-timetable-zh.png")
    }

    @Test fun dayViewShowsTodayCardAndTitleHidesTermStartText() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("todayOverviewQa") == "true" && arguments.getString("todayOverviewHomeQa") == "true")
        val context = AppLocale.wrap(compose.activity, AppLanguage.Chinese)
        val preferences = DataStoreUserPreferencesRepository(compose.activity)
        compose.waitUntil(timeoutMillis = 10_000) {
            val courses = compose.onAllNodesWithText("大学英语")
            courses.fetchSemanticsNodes().indices.any { courses[it].isDisplayed() }
        }
        compose.onNodeWithContentDescription(context.getString(com.x500x.cursimple.R.string.main_today_overview)).assertDoesNotExist()
        compose.onAllNodesWithTag("today-overview-card").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithText(context.getString(com.x500x.cursimple.R.string.schedule_view_mode_week)).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithTag("today-overview-card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.waitForIdle(); Thread.sleep(900); saveScreenshot("main-day-today-card-zh.png")
        compose.onNodeWithTag("today-overview-card").performClick()
        compose.onNodeWithTag("today-overview-sheet").assertIsDisplayed()
        compose.waitForIdle()
        compose.waitForIdle(); Thread.sleep(900); saveScreenshot("main-day-today-agenda-zh.png")
        pressBack()
        compose.onNodeWithTag("today-overview-sheet").assertDoesNotExist()
        runBlocking { preferences.setTermStartDate(null) }
        val missing = context.getString(com.x500x.cursimple.core.kernel.R.string.kernel_week_term_start_missing)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(context.getString(com.x500x.cursimple.R.string.main_set_term_start)).fetchSemanticsNodes().isNotEmpty()
        }
        // The hidden drawer remains in the semantics tree; match visible nodes only.
        val visibleMissing = compose.onAllNodesWithText(missing).let { nodes -> nodes.fetchSemanticsNodes().indices.count { nodes[it].isDisplayed() } }
        assertEquals(0, visibleMissing)
        compose.waitForIdle()
        compose.waitForIdle(); Thread.sleep(900); saveScreenshot("main-no-term-start-zh.png")
    }

    private fun seedMainTimetable(context: android.content.Context) = runBlocking {
        val app = context.applicationContext as ClassScheduleApplication
        val container = app.appContainer
        container.bootstrapJob.join()
        val preferences = container.userPreferencesRepository
        val termStart = LocalDate.of(2026, 9, 7)
        val term = container.termProfileRepository.createTerm("2026 秋季", termStart.toString())
        container.termProfileRepository.setActiveTerm(term.id)
        preferences.setTermStartDate(termStart)
        preferences.setTermStartUserDecided(true)
        preferences.setDisclaimerAccepted(true)
        preferences.setFirstRunGuideCompleted(true)
        preferences.markNotificationPermissionStartupAsked()
        preferences.markIslandStartupPromptShown()
        com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        preferences.setLastSeenVersionCode(com.x500x.cursimple.BuildConfig.VERSION_CODE)
        preferences.setAutoUpdateEnabled(false)
        preferences.setClassNoticeEnabled(false)
        preferences.setThemeMode(ThemeMode.Light)
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 9, 7, 8, 30))
        preferences.resetScheduleAppearanceAndDisplay()
        preferences.setHolidayCalendarBuiltInEnabled(false)
        container.widgetPreferencesRepository.saveManualTimingProfile(TermTimingProfile(
            termStart.toString(), listOf(
                ClassSlotTime(1, 1, "08:00", "08:45"), ClassSlotTime(2, 2, "08:50", "09:35"),
                ClassSlotTime(3, 3, "09:55", "10:40"), ClassSlotTime(4, 4, "10:45", "11:30"),
                ClassSlotTime(5, 5, "11:35", "12:20"), ClassSlotTime(6, 6, "12:25", "13:10"),
                ClassSlotTime(7, 7, "14:00", "14:45"), ClassSlotTime(8, 8, "14:50", "15:35"),
                ClassSlotTime(9, 9, "15:55", "16:40"), ClassSlotTime(10, 10, "16:45", "17:30"),
                ClassSlotTime(11, 11, "19:00", "19:45"), ClassSlotTime(12, 12, "19:50", "20:35"),
            ),
        ))
        fun course(id: String, title: String, day: Int, node: Int, location: String) = CourseItem(
            id, title, teacher = "张老师", location = location, weeks = (1..16).toList(), time = CourseTimeSlot(day, node, node + 1),
        )
        container.manualCourseRepository.replaceAll(listOf(
            course("qa-home-math-mon", "高等数学", 1, 1, "A101"),
            course("qa-home-english", "大学英语", 1, 3, "B202"),
            course("qa-home-sport", "体育", 1, 7, "体育馆"),
            course("qa-home-algebra", "线性代数", 2, 1, "A203"),
            course("qa-home-programming", "程序设计", 2, 7, "机房"),
            course("qa-home-history", "近代史", 3, 3, "C301"),
            course("qa-home-math-thu", "高等数学", 4, 3, "A101"),
            course("qa-home-physics", "大学物理", 5, 5, "B305"),
        ))
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
