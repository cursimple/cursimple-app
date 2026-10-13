package com.x500x.cursimple.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.navigation.AppBottomBar
import com.x500x.cursimple.app.navigation.BarTab
import com.x500x.cursimple.app.navigation.ComponentsHubScreen
import com.x500x.cursimple.app.navigation.MeScreen
import com.x500x.cursimple.app.navigation.SidebarSettingsPage
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in Chinese-UI captures for the release announcement; demo data only. */
class AnnouncementCaptureTest {
    // The dialog windows take their locale from the app's base context, so cache it before launch.
    private val chineseLocale = object : ExternalResource() {
        private var previous: String? = null
        override fun before() {
            if (args.getString("announcementQa") != "true") return
            val prefs = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            previous = prefs.getString("app_language", null)
            AppLocale.cache(context, language)
        }
        override fun after() {
            val prefs = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit()
            previous?.let { prefs.putString("app_language", it) } ?: prefs.remove("app_language")
            prefs.apply()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(chineseLocale).around(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private val language: AppLanguage = when (args.getString("announcementLang")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val languageSuffix = args.getString("announcementLang")?.takeIf { it != "zh-CN" }?.let { ".$it" }.orEmpty()

    /** Labels must resolve in the same locale the screens render in. */
    private val localeContext by lazy { AppLocale.wrap(context, language) }
    private fun text(id: Int, vararg formatArgs: Any) = localeContext.getString(id, *formatArgs)

    private val monday = LocalDate.now(ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val timing = TermTimingProfile(monday.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:40", "1–2"), ClassSlotTime(3, 4, "10:10", "11:50", "3–4"),
        ClassSlotTime(5, 6, "14:00", "15:40", "5–6"), ClassSlotTime(7, 8, "16:10", "17:50", "7–8")))
    private val regular = CourseItem("math", "高等数学", teacher = "王老师", location = "A101",
        weeks = (1..16).toList(), time = CourseTimeSlot(1, 1, 2))
    private val lunch = CourseItem("lunch", "午间讲座", teacher = "李老师", location = "B202",
        weeks = (1..16).toList(), time = CourseTimeSlot(1, 3, 4),
        reminderStartTime = "12:10", reminderEndTime = "13:30")
    private val event = ScheduleEvent("meet", "组会", date = monday.plusDays(2).toString(),
        startTime = "12:30", endTime = "13:15", location = "线上",
        weekDates = listOf(monday.plusDays(2), monday.plusDays(16)).map { it.toString() })

    @Test fun sidebarCustomization() {
        assumeTrue(args.getString("announcementQa") == "true")
        val layout = mutableStateOf(DrawerLayout.DEFAULT.copy(hidden = setOf(NavDestination.About, NavDestination.Reminders)))
        show { fixtureContext ->
            CompositionLocalProvider(*chinese(fixtureContext)) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Text("侧边栏", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                    SidebarSettingsPage(
                        layout = layout.value,
                        componentLayoutActive = false,
                        onChange = { layout.value = it },
                        onRestoreDrawer = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        capture("sidebar")
    }

    @Test fun bottomBarAndMePage() {
        assumeTrue(args.getString("announcementQa") == "true")
        val barItems = listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)
        val courses = mutableStateOf(listOf(regular, lunch))
        show { fixtureContext ->
            CompositionLocalProvider(*chinese(fixtureContext)) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    ScheduleScreen(
                        state = ScheduleUiState(initialized = true, timingProfile = timing,
                            manualCourses = courses.value, events = listOf(event)),
                        onAddManualCourse = { courses.value = courses.value + it }, onSaveCourse = {},
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday, viewMode = ScheduleViewMode.Week,
                        weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 16, termWeekCount = 16,
                        onWeekOffsetChange = {}, onAddWeek = {}, dayOffset = 0,
                        modifier = Modifier.weight(1f),
                    )
                    AppBottomBar(items = barItems, selected = BarTab.Destination(NavDestination.Schedule),
                        meBadge = true, onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        capture("bottom-bar")
    }

    @Test fun mePage() {
        assumeTrue(args.getString("announcementQa") == "true")
        show { fixtureContext ->
            CompositionLocalProvider(*chinese(fixtureContext)) {
                Column(Modifier.fillMaxSize()) {
                    MeScreen(
                        overflow = NavDestination.entries.filterNot {
                            it in listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)
                        },
                        termLabel = "2026 秋季 · 第 6 周", versionName = BuildConfig.VERSION_NAME,
                        updateBadge = true, badgeFor = { it == NavDestination.Settings },
                        onOpen = {}, onShortcut = {}, onOpenUpdate = {},
                        modifier = Modifier.weight(1f),
                    )
                    AppBottomBar(items = listOf(NavDestination.Schedule, NavDestination.Memos,
                        NavDestination.Components), selected = BarTab.Me, meBadge = true, onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        capture("me-page")
    }

    @Test fun weekGridOffsetForTypedCourse() {
        assumeTrue(args.getString("announcementQa") == "true")
        showSchedule()
        capture("grid")
    }

    @Test fun addCourseByClockTime() {
        assumeTrue(args.getString("announcementQa") == "true")
        showSchedule()
        node(regular.title).performTouchInput { longClick() }
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_action_add_at_position)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(ScheduleR.string.schedule_course_name_label)))
            .performScrollTo().performTextReplacement("高等数学")
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_time_by_clock)).performScrollTo().performClick()
        compose.waitForIdle()
        hideIme()
        capture("course-clock")
    }

    @Test fun eventPickedWeeks() {
        assumeTrue(args.getString("announcementQa") == "true")
        showSchedule()
        node(event.title).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction() and hasText(text(ScheduleR.string.schedule_event_edit)))
            .onFirst().performClick()
        compose.onNodeWithTag("event-repeat-weeks").performScrollTo().assertIsSelected()
        compose.waitForIdle()
        hideIme()
        capture("event-weeks")
    }

    @Test fun pickedWeeksMatrix() {
        assumeTrue(args.getString("announcementQa") == "true")
        showSchedule()
        node(event.title).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction() and hasText(text(ScheduleR.string.schedule_event_edit)))
            .onFirst().performClick()
        compose.onAllNodes(hasClickAction() and hasText(text(ScheduleR.string.schedule_week_custom_edit)))
            .onFirst()
            .performScrollTo().performClick()
        compose.waitForIdle()
        capture("event-weeks-matrix")
    }

    private fun showSchedule() {
        val courses = mutableStateOf(listOf(regular, lunch))
        show { fixtureContext ->
            CompositionLocalProvider(*chinese(fixtureContext)) {
                ScheduleScreen(
                    state = ScheduleUiState(initialized = true, timingProfile = timing,
                        manualCourses = courses.value, events = listOf(event)),
                    onAddManualCourse = { courses.value = courses.value + it }, onSaveCourse = {},
                    onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {}, overrideTermStart = monday, viewMode = ScheduleViewMode.Week,
                    weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 16, termWeekCount = 16,
                    onWeekOffsetChange = {}, onAddWeek = {}, dayOffset = 0,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun chinese(base: android.content.Context) = arrayOf(
        LocalContext provides base,
        LocalConfiguration provides base.resources.configuration,
        LocalActivityResultRegistryOwner provides compose.activity,
    )

    private fun show(content: @androidx.compose.runtime.Composable (android.content.Context) -> Unit) {
        val fixtureContext = AppLocale.wrap(context, language)
        // Reuse the running activity's window so system bars are part of the capture.
        compose.runOnUiThread {
            compose.activity.setContent {
                ClassScheduleTheme(themeMode = ThemeMode.Light) {
                    Box(Modifier.fillMaxSize()) { content(fixtureContext) }
                }
            }
        }
    }

    /** The pager also composes the neighbouring week, so pick the copy that is on screen. */
    private fun node(title: String): SemanticsNodeInteraction {
        val all = compose.onAllNodesWithText(title, substring = true)
        return (0 until all.fetchSemanticsNodes().size).map { all[it] }.firstOrNull { it.isDisplayed() } ?: all.onFirst()
    }

    /** Hides the soft keyboard in every window, including the dialog's own. */
    private fun hideIme() {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val manager = context.getSystemService(InputMethodManager::class.java) ?: return@runOnMainSync
            val views = mutableListOf(compose.activity.window.decorView)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WindowInspector.getGlobalWindowViews().forEach { if (it.isShown) views += it }
            }
            views.forEach { manager.hideSoftInputFromWindow(it.windowToken, 0) }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val bitmap = checkNotNull((1..5).firstNotNullOfOrNull {
            instrumentation.uiAutomation.takeScreenshot() ?: run { Thread.sleep(300); null }
        })
        val dir = File(checkNotNull(context.getExternalFilesDir(null)), "announcement")
        dir.mkdirs()
        File(dir, "$name$languageSuffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
