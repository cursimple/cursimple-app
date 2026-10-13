package com.x500x.cursimple.app

import android.graphics.Bitmap
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
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in QA for typed course times and picked-week events; in-memory data only. */
class ClockTimeCourseUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    private val today = LocalDate.now(ZoneId.systemDefault())
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val timing = TermTimingProfile(monday.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:40", "1–2"), ClassSlotTime(3, 4, "10:10", "11:50", "3–4"),
        ClassSlotTime(5, 6, "14:00", "15:40", "5–6"), ClassSlotTime(7, 8, "16:10", "17:50", "7–8")))
    private val regular = CourseItem("regular", "Regular course", location = "A101",
        weeks = (1..16).toList(), time = CourseTimeSlot(1, 1, 2))
    private val lunch = CourseItem("lunch", "Lunch seminar", location = "B202",
        weeks = (1..16).toList(), time = CourseTimeSlot(1, 3, 4),
        reminderStartTime = "12:10", reminderEndTime = "13:30")
    private val event = ScheduleEvent("meet", "Group meeting", date = monday.plusDays(2).toString(),
        startTime = "12:30", endTime = "13:15",
        weekDates = listOf(monday.plusDays(2), monday.plusDays(16)).map { it.toString() })

    @Test fun weekViewPlacesTypedCourseAtItsClockTime() {
        assumeTrue(args.getString("clockTimeQa") == "true")
        show(ScheduleViewMode.Week)
        save("week")
        // Truncation shrinks the visible text, so match what is on screen rather than the full title.
        val lunchTop = node("Lunch").fetchSemanticsNode().boundsInRoot.top
        val regularTop = node("Regular").fetchSemanticsNode().boundsInRoot.top
        assertTrue("lunch band $lunchTop must sit below the 1–2 band $regularTop", lunchTop > regularTop)
    }

    @Test fun dayViewPlacesTypedCourseAtItsClockTime() {
        assumeTrue(args.getString("clockTimeQa") == "true")
        show(ScheduleViewMode.Day)
        save("day")
        node(lunch.title).assertIsDisplayed()
        // The rail already shows the typed clock time; a period badge would point at the wrong period.
        compose.onAllNodesWithText(context.getString(ScheduleR.string.schedule_period_compact, 1)).assertCountEquals(1)
        compose.onAllNodesWithText(context.getString(ScheduleR.string.schedule_period_compact, 3)).assertCountEquals(0)
        save("day")
    }

    @Test fun eventEditorOffersPickedWeeks() {
        assumeTrue(args.getString("clockTimeQa") == "true")
        var saved: ScheduleEvent? = null
        show(ScheduleViewMode.Week, onSaveEvent = { saved = it })
        node(event.title).performClick()
        save("event-detail")
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_edit))).performClick()
        compose.onNodeWithTag("event-repeat-weeks").performScrollTo().assertIsSelected()
        save("event-editor")
        compose.onNodeWithTag("event-repeat-weekly").performClick()
        save("event-editor-weekly")
        compose.onNodeWithTag("event-repeat-weeks").performClick()
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_week_custom_edit)))
            .performScrollTo().performClick()
        save("event-weeks-matrix")
        compose.onAllNodes(hasClickAction() and hasText("5")).onLast().performClick()
        compose.onAllNodes(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).onLast().performClick()
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(listOf(monday.plusDays(2), monday.plusDays(16), monday.plusDays(30)).map { it.toString() },
                result.weekDates)
        }
    }

    @Test fun quickAddOffersClockTime() {
        assumeTrue(args.getString("clockTimeQa") == "true")
        var added: CourseItem? = null
        show(ScheduleViewMode.Week, onAdd = { added = it })
        node(regular.title).performTouchInput { longClick() }
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_action_add_at_position)).performScrollTo().performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_time_by_clock)).performScrollTo().performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_course_clock_hint)).performScrollTo()
        save("quick-add-by-time")
        for ((label, value) in listOf(ScheduleR.string.schedule_course_name_label to "Typed course",
            ScheduleR.string.schedule_week_start_label to "1", ScheduleR.string.schedule_week_end_label to "1")) {
            compose.onNode(hasSetTextAction() and hasText(text(label))).performScrollTo().performTextReplacement(value)
        }
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()
        compose.runOnIdle {
            val result = requireNotNull(added)
            assertNotNull(result.reminderStartTime)
            assertNotNull(result.reminderEndTime)
        }
    }

    private fun show(
        mode: ScheduleViewMode,
        onAdd: (CourseItem) -> Unit = {},
        onSaveEvent: (ScheduleEvent) -> Unit = {},
    ) {
        val courses = mutableStateOf(listOf(regular, lunch))
        compose.setContent {
            ClassScheduleTheme(themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = ThemeAccent.Green) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing,
                        manualCourses = courses.value, events = listOf(event)),
                        onAddManualCourse = { onAdd(it); courses.value = courses.value + it },
                        onSaveCourse = {}, onSaveEvent = onSaveEvent,
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday, viewMode = mode,
                        weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 16, termWeekCount = 16,
                        onWeekOffsetChange = {}, onAddWeek = {},
                        dayOffset = ChronoUnit.DAYS.between(today, monday).toInt())
                }
            }
        }
        compose.waitForIdle()
    }

    // The pager also composes the neighbouring pages, so pick the copy that is on screen.
    private fun node(title: String): SemanticsNodeInteraction {
        val all = compose.onAllNodesWithText(title, substring = true)
        return (0 until all.fetchSemanticsNodes().size).map { all[it] }.firstOrNull { it.isDisplayed() } ?: all.onFirst()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = checkNotNull((1..5).firstNotNullOfOrNull {
            instrumentation.uiAutomation.takeScreenshot() ?: run { Thread.sleep(300); null }
        })
        val file = File(context.filesDir, "clock-time/${args.getString("qaVariant") ?: "normal"}/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
