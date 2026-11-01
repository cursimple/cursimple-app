package com.x500x.cursimple.app

import android.graphics.Color as AColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** In-memory drafts only: colors, blend and border of courses and events. */
class ItemAppearanceUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private val monday = LocalDate.of(2026, 9, 14)
    @Before fun clock() { BeijingTime.setForcedNow(monday.atTime(9, 45)) }
    @After fun restore() { BeijingTime.setForcedNow(null) }

    private fun showAdd(
        onSave: (CourseItem) -> Unit = {},
        onEvent: ((ScheduleEvent) -> Unit)? = null,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) = compose.setContent {
        val base = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
        ) {
            ClassScheduleTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                AddCourseDialog(onDismiss = {}, onConfirm = onSave, onConfirmEvent = onEvent, maxWeekCount = 18)
            }
        }
    }

    private fun title(value: String) =
        compose.onNode(hasSetTextAction() and hasText(text(ScheduleR.string.schedule_course_name_label))).performTextReplacement(value)

    private fun clickTag(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun aCourseCanBeSavedWithADarkFillABlendAndABorder() {
        var saved: CourseItem? = null
        showAdd(onSave = { saved = it })
        title("深色课")
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        compose.onNodeWithTag("item-appearance-section").performScrollTo().assertIsDisplayed()
        clickTag("appearance-fill-1E3A5F")
        clickTag("appearance-blend-switch")
        clickTag("appearance-border-FFB74D")
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "appearance-qa/form.png"))
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()
        compose.runOnIdle {
            assertEquals(0xFF1E3A5FL, saved?.colorArgb)
            assertNotNull("a blend color was picked for the user", saved?.colorEndArgb)
            assertEquals(0xFFFFB74DL, saved?.borderArgb)
        }
    }

    @Test fun anyColorCanBeTypedInTheCustomPicker() {
        var saved: CourseItem? = null
        showAdd(onSave = { saved = it })
        title("自定义")
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        clickTag("appearance-fill-custom")
        compose.onNodeWithTag("color-picker-hex").performTextReplacement("#336699")
        compose.onNodeWithTag("color-picker-confirm").performClick()
        compose.onNodeWithTag("appearance-fill-custom-value").performScrollTo().assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()
        compose.runOnIdle { assertEquals(0xFF336699L, saved?.colorArgb) }
    }

    @Test fun anEventGetsTheSameColorsAtTheBottomOfItsForm() {
        var event: ScheduleEvent? = null
        showAdd(onEvent = { event = it })
        compose.onNodeWithTag("add-dialog-type-event").performClick()
        compose.onNode(hasSetTextAction() and hasText(text(ScheduleR.string.schedule_event_field_title))).performTextReplacement("社团活动")
        clickTag("appearance-fill-4A148C")
        clickTag("appearance-border-E57373")
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()
        compose.runOnIdle {
            assertEquals(0xFF4A148CL, event?.colorArgb)
            assertEquals(0xFFE57373L, event?.borderArgb)
        }
    }

    @Test fun theTimetableDrawsTheBlendAndTheBorder() {
        val termStart = monday.minusWeeks(6)
        val timing = TermTimingProfile(termStart.toString(), listOf(
            ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40")))
        val styled = CourseItem("styled", "渐变课", weeks = (1..18).toList(), time = CourseTimeSlot(2, 1, 2),
            colorArgb = 0xFFFFFF00L, colorEndArgb = 0xFF0000FFL, borderArgb = 0xFFFF0000L)
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing, manualCourses = listOf(styled)),
                    onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {}, overrideTermStart = termStart,
                    weekOffset = 0, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18)
            }
        }
        compose.waitForIdle()
        val card: Rect = compose.onNode(
            hasTestTag("schedule-course-styled") and hasAnyAncestor(hasTestTag("schedule-current-week")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInWindow
        Thread.sleep(500)
        val shot = ScreenshotCapture.take()
        val x = (card.right - card.width * 0.12f).toInt()
        val topPixel = shot.getPixel(x, (card.top + card.height * 0.24f).toInt())
        val bottomPixel = shot.getPixel(x, (card.bottom - card.height * 0.12f).toInt())
        val edgePixel = shot.getPixel((card.left + 2f).toInt(), (card.top + card.height / 2f).toInt())
        ScreenshotCapture.save(shot, File(context.filesDir, "appearance-qa/timetable.png"))
        fun distance(a: Int, b: Int) = abs(AColor.red(a) - AColor.red(b)) + abs(AColor.green(a) - AColor.green(b)) + abs(AColor.blue(a) - AColor.blue(b))
        assertTrue("the top is the yellow end (#${Integer.toHexString(topPixel)})", AColor.blue(topPixel) < AColor.red(topPixel))
        assertTrue("the bottom has moved toward the blue end (#${Integer.toHexString(bottomPixel)})", AColor.blue(bottomPixel) > AColor.blue(topPixel) + 40)
        assertTrue("the outline is red (#${Integer.toHexString(edgePixel)})", distance(edgePixel, AColor.RED) < 120)
    }

    @Test fun darkThemeAndLargeFontKeepTheColorSectionUsable() {
        var saved: CourseItem? = null
        showAdd(onSave = { saved = it }, dark = true, fontScale = 1.3f)
        title("深色大字")
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        compose.onNodeWithTag("item-appearance-section").performScrollTo().assertIsDisplayed()
        clickTag("appearance-preset-4")
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "appearance-qa/dark-large.png"))
        compose.onNodeWithTag("add-course-save").performClick()
        compose.runOnIdle {
            assertEquals(0xFF263238L, saved?.colorArgb)
            assertEquals(0xFF546E7AL, saved?.colorEndArgb)
            assertEquals(0xFFB0BEC5L, saved?.borderArgb)
        }
    }

    @Test fun aNewCourseNamedLikeAnExistingOneStartsWithItsColors() {
        var saved: CourseItem? = null
        val existing = CourseItem("old", "高等数学", weeks = listOf(1, 2), time = CourseTimeSlot(1, 1, 2),
            colorArgb = 0xFF7F1D1DL, borderArgb = 0xFFFFB74DL)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                AddCourseDialog(onDismiss = {}, onConfirm = { saved = it }, existingCourses = listOf(existing), maxWeekCount = 18)
            }
        }
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        title("高等数学")
        compose.waitForIdle()
        compose.onNodeWithTag("add-course-save").performClick()
        compose.runOnIdle {
            assertEquals(0xFF7F1D1DL, saved?.colorArgb)
            assertEquals(0xFFFFB74DL, saved?.borderArgb)
        }
    }

    @Test fun anEmptyWeekExplainsHowToAddACourse() {
        val termStart = monday.minusWeeks(6)
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                ScheduleScreen(state = ScheduleUiState(initialized = true,
                    timingProfile = TermTimingProfile(termStart.toString(), listOf(ClassSlotTime(1, 2, "08:00", "09:35"))), manualCourses = emptyList()),
                    onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {}, overrideTermStart = termStart,
                    weekOffset = 0, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18)
            }
        }
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("schedule-week-empty-hint") and hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertTrue("the empty-week hint is shown", it.isNotEmpty()) }
    }
}
