package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in interaction QA using in-memory courses; never writes user course data. */
class CourseAddAtPositionUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun weekViewAddsANewCourseBesideTheOriginal() = exercise(ScheduleViewMode.Week)
    @Test fun dayViewAddsANewCourseBesideTheOriginal() = exercise(ScheduleViewMode.Day)

    private fun exercise(mode: ScheduleViewMode) {
        assumeTrue(args.getString("courseAddHereQa") == "true")
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val source = CourseItem("original", "Source course", teacher = "Original teacher", location = "Original room",
            weeks = listOf(1), time = CourseTimeSlot(2, 3, 5))
        val courses = mutableStateOf(listOf(source))
        val timing = TermTimingProfile(monday.toString(), listOf(
            ClassSlotTime(1, 2, "08:00", "09:40", "1–2"), ClassSlotTime(3, 4, "10:10", "11:50", "3–4"),
            ClassSlotTime(5, 6, "14:00", "15:40", "5–6")))
        var added: CourseItem? = null
        compose.setContent {
            ClassScheduleTheme(themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = courses.value),
                        onAddManualCourse = { added = it; courses.value = courses.value + it },
                        onSaveCourse = { fail("Quick add must not edit the original") },
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday, viewMode = mode,
                        dayOffset = ChronoUnit.DAYS.between(today, monday.plusDays(1)).toInt())
                }
            }
        }
        compose.onNodeWithText(source.title).performTouchInput { longClick() }
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_action_add_at_position)).performScrollTo().performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_quick_add_title)).assertIsDisplayed()
        for ((label, value) in listOf(ScheduleR.string.schedule_course_name_label to "New course",
            ScheduleR.string.schedule_week_start_label to "1", ScheduleR.string.schedule_week_end_label to "1")) {
            compose.onNode(hasSetTextAction() and hasText(text(label)))
                .performScrollTo().performTextReplacement(value)
        }
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_save)).performClick()
        compose.runOnIdle {
            val saved = requireNotNull(added)
            assertNotEquals(source.id, saved.id)
            assertEquals(source.time, saved.time)
            assertEquals("New course", saved.title)
            assertEquals("", saved.teacher)
            assertEquals("", saved.location)
            assertEquals(source, courses.value.first())
            assertEquals(2, courses.value.size)
        }
    }
}
