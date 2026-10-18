package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.CourseDetailDialog
import com.x500x.cursimple.feature.schedule.QuickAddCourseDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in checks for the real course editor, persistence and removal of detail shortcuts. */
class CourseMetadataUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val repository get() = DataStoreManualCourseRepository(context)
    private fun enabled() = args.getString("courseMetadataQa") == "true"
    private val language get() = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val languageSuffix get() = args.getString("qaLanguage")?.takeIf { it == "en" || it == "zh-TW" }?.let { ".$it" }.orEmpty()
    private val initial = CourseItem("qa-week-teacher", "物理实验", teacher = "", location = "",
        weekLocations = mapOf(2 to "Lab 204"), weekTeachers = mapOf(2 to "Teacher A"),
        weeks = listOf(2, 3), time = CourseTimeSlot(2, 5, 7))
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            instrumentation.runOnMainSync { android.graphics.HardwareRenderer.setDrawingEnabled(true) }
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDisclaimerAccepted(true); setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE); markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked(); setTermStartUserDecided(true)
                    setAppLanguage(language)
                }
                repository.apply { removeCourse(initial.id); addCourse(initial) }
            }
            AppLocale.cache(context, language)
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
        override fun after() { if (enabled()) runBlocking { repository.removeCourse(initial.id) } }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)
    private fun text(id: Int) = compose.activity.getString(id)
    private fun saveWeekTeacher() {
        compose.onNode(hasText(text(ScheduleR.string.schedule_action_save)) and hasAnyAncestor(
            isDialog() and hasAnyDescendant(hasText(text(ScheduleR.string.schedule_week_teacher_title))),
        )).performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(context.filesDir, "course-metadata/${args.getString("qaVariant") ?: "normal"}/$name$languageSuffix.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }

    @Test fun teacherButtonLivesInEditorAndWeekValuesPersist() {
        assumeTrue(enabled())
        val state = mutableStateOf(initial)
        compose.activity.setContent {
            ClassScheduleTheme(
                themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    CourseDetailDialog(courses = listOf(state.value), timingProfile = null, visibleWeekNumber = 3,
                        isManual = { true }, onSaveCourse = { saved ->
                            runBlocking { repository.updateCourse(saved) }; state.value = saved
                        }, onDismiss = {}, onSetReminder = {}, onDelete = {})
                }
            }
        }
        compose.onAllNodesWithText("设置默认").assertCountEquals(0)
        if (language == AppLanguage.Chinese) compose.onAllNodesWithText("未设置").assertCountEquals(2)
        capture("details-before")
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_edit)).performClick()
        compose.onNodeWithTag("course-teacher-field").performScrollTo()
        compose.onNodeWithTag("course-teacher-weeks").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("course-location-weeks").assertIsDisplayed().assertIsEnabled()
        capture("course-edit")
        compose.onNodeWithTag("course-teacher-weeks").performClick()
        // Selecting a week loads what it shows now; the editor only appears once weeks are picked.
        compose.onNodeWithTag("week-select-2").performScrollTo().performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_week_teacher_value_label)).assertIsDisplayed()
        compose.onNodeWithTag("week-batch-value-field").assertTextContains("Teacher A")
        compose.onNodeWithTag("week-pick-none").performScrollTo().performClick()
        compose.onNodeWithTag("week-select-3").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-value-field").performTextReplacement("Teacher B")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        capture("week-teachers")
        saveWeekTeacher()
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_save)).performScrollTo().performClick()
        compose.onNodeWithText("Teacher B").assertIsDisplayed()
        compose.onAllNodesWithText("设置默认").assertCountEquals(0)
        val stored = runBlocking { repository.manualCoursesFlow.first().first { it.id == initial.id } }
        assertEquals(mapOf(2 to "Teacher A", 3 to "Teacher B"), stored.weekTeachers)
        assertEquals(initial.weekLocations, stored.weekLocations)
        assertEquals("", stored.teacher)
        capture("details-after")

        // Cancelling the whole editor discards default and weekly teacher drafts together.
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_edit)).performClick()
        compose.onNodeWithTag("course-teacher-field").performScrollTo().performTextReplacement("Unsaved default")
        compose.onNodeWithTag("course-teacher-weeks").performClick()
        compose.onNodeWithTag("week-select-3").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Unsaved weekly")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        saveWeekTeacher()
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_cancel)).performScrollTo().performClick()
        assertEquals(stored, runBlocking { repository.manualCoursesFlow.first().first { it.id == initial.id } })
        compose.onNodeWithText("Teacher B").assertIsDisplayed()
    }

    @Test fun newCourseTeacherButtonIsAvailableBeforeSelectingWeeks() {
        assumeTrue(enabled())
        var saved: CourseItem? = null
        compose.activity.setContent {
            ClassScheduleTheme(
                themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    QuickAddCourseDialog(dayOfWeek = 2, startNode = 5, endNode = 7, onDismiss = {}, onConfirm = { saved = it })
                }
            }
        }
        compose.onNodeWithTag("course-teacher-field")
        compose.onNodeWithTag("course-teacher-weeks").assertIsDisplayed()
        capture("quick-course-edit")
        compose.onNodeWithTag("course-teacher-weeks").performClick()
        compose.onNodeWithTag("week-select-3").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Teacher C")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        saveWeekTeacher()
        for ((label, value) in listOf(ScheduleR.string.schedule_course_name_label to "新课程",
            ScheduleR.string.schedule_week_start_label to "2", ScheduleR.string.schedule_week_end_label to "3")) {
            compose.onNode(hasSetTextAction() and hasText(text(label))).apply { performTextReplacement(value); performImeAction() }
        }
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_save)).performClick()
        compose.runOnIdle {
            assertEquals(mapOf(3 to "Teacher C"), saved?.weekTeachers)
            assertEquals(listOf(2, 3), saved?.weeks)
            assertEquals("", saved?.teacher)
        }
    }
}
