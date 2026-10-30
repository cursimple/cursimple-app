package com.x500x.cursimple.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import com.x500x.cursimple.feature.schedule.QuickAddCourseDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** In-memory drafts only: the week picker of both new-course editors. */
class CourseWeeksSectionUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun showQuickAdd(onSave: (CourseItem) -> Unit = {}) = compose.setContent {
        ClassScheduleTheme(ThemeMode.Light) { QuickAddCourseDialog(2, 3, 4, maxWeekCount = 18, onDismiss = {}, onConfirm = onSave) }
    }

    private fun showAdd(onSave: (CourseItem) -> Unit = {}) = compose.setContent {
        ClassScheduleTheme(ThemeMode.Light) { AddCourseDialog(onDismiss = {}, onConfirm = onSave, maxWeekCount = 18) }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "weeks-qa/$name.png"))
    }

    private fun title(value: String) =
        compose.onNode(hasSetTextAction() and hasText(text(ScheduleR.string.schedule_course_name_label))).performTextReplacement(value)

    private fun saveButton() = compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save)))

    @Test fun quickAddOffersParityAndCustomWeeksWithoutScrolling() {
        var saved: CourseItem? = null
        showQuickAdd { saved = it }
        // The picker sits near the top of the form: shown before any scrolling.
        compose.onNodeWithTag("course-weeks-section").assertIsDisplayed()
        compose.onNodeWithTag("course-weeks-parity-Custom").assertIsDisplayed()
        compose.onNodeWithTag("course-weeks-summary").assertTextContains(text(ScheduleR.string.schedule_weeks_unset), substring = true)
        save("quick-add")

        compose.onNodeWithTag("course-weeks-parity-Custom").performClick()
        compose.onNodeWithTag("week-select-1").performClick()
        compose.onNodeWithTag("week-select-3").performClick()
        compose.onNodeWithTag("week-select-5").performClick()
        save("quick-add-matrix")
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save)) and hasAnyAncestor(isDialog() and hasAnyDescendant(hasTestTag("week-selection-grid")))).performClick()
        compose.onNodeWithTag("course-weeks-summary").assertTextContains("3", substring = true)
        title("单周课")
        saveButton().performClick()
        compose.runOnIdle {
            assertEquals(listOf(1, 3, 5), saved?.weeks)
            assertEquals(CourseTimeSlot(2, 3, 4), saved?.time)
        }
    }

    @Test fun quickAddOddWeeksAndWholeTermShortcut() {
        var saved: CourseItem? = null
        showQuickAdd { saved = it }
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        compose.onNodeWithTag("course-weeks-parity-Odd").performClick()
        compose.onNodeWithTag("course-weeks-summary").assertTextContains("9", substring = true)
        title("奇数周")
        saveButton().performClick()
        compose.runOnIdle { assertEquals((1..18 step 2).toList(), saved?.weeks) }
    }

    @Test fun newCourseDialogShowsWeeksBeforeLocationAndTeacher() {
        showAdd()
        compose.onNodeWithTag("course-weeks-section").assertIsDisplayed()
        val weeksTop = compose.onNodeWithTag("course-weeks-section").fetchSemanticsNode().positionInRoot.y
        val locationTop = compose.onNodeWithTag("course-location-field").fetchSemanticsNode().positionInRoot.y
        assertTrue("weeks ($weeksTop) come before the room ($locationTop)", weeksTop < locationTop)
        save("add-dialog")
        compose.onNodeWithTag("course-weeks-parity-Custom").performClick()
        compose.onNodeWithTag("week-selection-grid").assertIsDisplayed()
    }

    @Test fun newCourseDialogPicksTheWeekdayFromOneRowAndKeepsTheFormInReadingOrder() {
        var saved: CourseItem? = null
        showAdd { saved = it }
        compose.onNodeWithTag("course-weekday-selector").assertIsDisplayed()
        compose.onNodeWithTag("course-day-5").performClick()
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        title("周五的课")
        // Name, weeks, then time: each section starts below the previous one.
        val tops = listOf("course-title-field", "course-weeks-section", "course-weekday-selector", "course-location-field")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.y }
        assertTrue("sections run top to bottom: $tops", tops == tops.sorted())
        save("add-dialog-compact")
        saveButton().performClick()
        compose.runOnIdle {
            assertEquals(5, saved?.time?.dayOfWeek)
            assertEquals((1..18).toList(), saved?.weeks)
        }
    }

    @Test fun theFormLiftsAboveTheKeyboardAndKeepsSaveInTheTopBar() {
        showAdd()
        compose.onNodeWithTag("add-course-page").assertIsDisplayed()
        val screenHeight = context.resources.displayMetrics.heightPixels
        // The save button lives in the top bar, so it is on screen before, and while, the keyboard is up.
        compose.onNodeWithTag("add-course-save").assertIsDisplayed()
        compose.onNodeWithTag("course-location-field").performScrollTo().performClick()
        Thread.sleep(1200)
        val ime = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { w -> android.graphics.Rect().also { w.getBoundsInScreen(it) } }
        val fieldBottom = compose.onNodeWithTag("course-location-field").fetchSemanticsNode().boundsInWindow.bottom
        save("add-page-keyboard")
        compose.onNodeWithTag("add-course-save").assertIsDisplayed()
        if (ime != null) assertTrue("the field being typed in ($fieldBottom) is above the keyboard (${ime.top})", fieldBottom <= ime.top + 2)
        assertTrue("sanity: screen $screenHeight", fieldBottom < screenHeight)
    }
}
