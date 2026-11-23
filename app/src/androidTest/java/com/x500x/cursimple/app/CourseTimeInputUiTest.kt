package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR

@OptIn(ExperimentalTestApi::class)
class CourseTimeInputUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun text(id: Int) = instrumentation.targetContext.getString(id)

    @Test
    fun backspaceInMinutesDoesNotEraseTheHour() {
        val dark = InstrumentationRegistry.getArguments().getString("timeInputDark") == "true"
        compose.setContent {
            ClassScheduleTheme(
                themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (dark) ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                AddCourseDialog(onDismiss = {}, onConfirm = {})
            }
        }
        compose.onNodeWithTag("course-time-by-clock").performScrollTo().performClick()
        compose.onNodeWithTag("course-clock-end").performScrollTo().performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_time_select_end)).assertIsDisplayed()
        capture(if (dark) "time-input-dark.png" else "time-input-light.png")
        val minute = compose.onNode(hasSetTextAction() and hasText("35"))
        val minuteId = minute.fetchSemanticsNode().id
        minute.performClick()
        minute.performSemanticsAction(SemanticsActions.SetSelection) { it(2, 2, false) }
        repeat(5) {
            compose.onNode(hasSetTextAction() and isFocused()).performKeyInput { pressKey(Key.Backspace) }
            compose.waitForIdle()
        }
        compose.onNode(hasSetTextAction() and hasText("09")).assertExists()
        assertEquals("Focus remains on minutes after extra backspaces", minuteId,
            compose.onNode(hasSetTextAction() and isFocused()).fetchSemanticsNode().id)
        compose.onNodeWithTag("time-input-minute").assertIsFocused()
        val save = compose.onNodeWithTag("time-input-save")
        save.assertIsNotEnabled()
        compose.onNodeWithTag("time-input-minute").performTextInput("60")
        compose.onNodeWithText(text(ScheduleR.string.schedule_time_input_range)).assertIsDisplayed()
        save.assertIsNotEnabled()
        compose.onNodeWithTag("time-input-minute").performTextClearance()
        compose.onNodeWithTag("time-input-minute").performTextInput("7")
        save.assertIsEnabled().performClick()
        compose.onNodeWithTag("course-clock-end").assertTextContains("09:07")

        compose.onNodeWithTag("course-clock-end").performClick()
        compose.onNodeWithTag("time-input-minute").performTextReplacement("51")
        compose.onNodeWithText(text(ScheduleR.string.schedule_action_cancel)).performClick()
        compose.onNodeWithTag("course-clock-end").assertTextContains("09:07")
    }

    private fun capture(filename: String) {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, filename).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
