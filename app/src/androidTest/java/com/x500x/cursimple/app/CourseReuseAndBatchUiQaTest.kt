package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.LocalCourseReuseCatalog
import com.x500x.cursimple.feature.schedule.QuickAddCourseDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** In-memory course drafts only; no device preferences, accounts or stored courses change. */
class CourseReuseAndBatchUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val history = (0..29).map { index ->
        CourseItem("template-$index", "Course $index", teacher = "Teacher $index", location = "Room $index",
            weeks = listOf(10, 11), time = CourseTimeSlot(6, 9, 10))
    }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun setContent(dark: Boolean = false, onSave: (CourseItem) -> Unit = {}) {
        compose.setContent {
            ClassScheduleTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                CompositionLocalProvider(LocalCourseReuseCatalog provides history,
                    LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    QuickAddCourseDialog(2, 3, 4, maxWeekCount = 30, onDismiss = {}, onConfirm = onSave)
                }
            }
        }
    }
    private fun weekRange() {
        for ((label, value) in listOf(ScheduleR.string.schedule_week_start_label to "1", ScheduleR.string.schedule_week_end_label to "5")) {
            compose.onNode(hasSetTextAction() and hasText(text(label))).performScrollTo().performTextReplacement(value)
        }
    }
    private fun saveCourse() = compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_save))).performClick()

    @Test fun scrollingSavedCoursesPrefillsMetadataAndKeepsDestinationSlotAndWeeks() {
        var saved: CourseItem? = null
        setContent(onSave = { saved = it })
        compose.onNodeWithTag("course-title-reuse").performClick()
        compose.onNodeWithTag("course-title-reuse-menu").assertIsDisplayed()
        compose.onNodeWithTag("course-title-reuse-option-29").performScrollTo().performClick()
        compose.onNodeWithTag("course-title-field").assertTextContains("Course 29")
        compose.onNodeWithTag("course-teacher-field").performScrollTo().assertTextContains("Teacher 29")
        compose.onNodeWithTag("course-location-field").performScrollTo().assertTextContains("Room 29")
        compose.onNodeWithTag("course-teacher-reuse").performClick()
        compose.onNodeWithText("Teacher 0").performScrollTo().performClick()
        compose.onNodeWithTag("course-teacher-field").assertTextContains("Teacher 0")
        weekRange()
        saveCourse()
        compose.runOnIdle {
            assertEquals("Teacher 0", saved?.teacher)
            assertEquals("Room 29", saved?.location)
            assertEquals(CourseTimeSlot(2, 3, 4), saved?.time)
            assertEquals((1..5).toList(), saved?.weeks)
            assertNotEquals(history.last().id, saved?.id)
        }
    }

    @Test fun onlyTheArrowOpensSavedValues() {
        setContent()
        compose.onNodeWithTag("course-teacher-field").performScrollTo().performClick()
        compose.onNodeWithTag("course-teacher-reuse-menu").assertDoesNotExist()
        compose.onNodeWithTag("course-teacher-field").performTextInput("Teacher")
        compose.onNodeWithTag("course-teacher-reuse-menu").assertDoesNotExist()
        compose.onNodeWithTag("course-teacher-reuse").performClick()
        compose.onNodeWithTag("course-teacher-reuse-menu").assertIsDisplayed()
    }

    @Test fun fieldActionsLookSmallButKeepTheirTouchTarget() {
        setContent()
        for (tag in listOf("course-teacher-reuse", "course-teacher-weeks")) {
            val node = compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode()
            with(compose.density) {
                assertTrue("$tag 画成了 ${node.size.width.toDp()}", node.size.width.toDp() <= 36.dp)
                assertTrue("$tag 只能点 ${node.touchBoundsInRoot}", node.touchBoundsInRoot.width.toDp() >= 48.dp &&
                    node.touchBoundsInRoot.height.toDp() >= 48.dp)
            }
        }
    }

    @Test fun draggingWeeksAppliesOneTeacherToTheWholeRange() {
        var saved: CourseItem? = null
        setContent(dark = true, onSave = { saved = it })
        compose.onNodeWithTag("course-title-field").performTextReplacement("Batch course")
        compose.onNodeWithTag("course-teacher-weeks").performScrollTo().performClick()
        val grid = compose.onNodeWithTag("week-selection-grid").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("week-select-2").fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithTag("week-select-4").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("week-selection-grid").performTouchInput {
            down(Offset(start.x - grid.left, start.y - grid.top))
            moveTo(Offset(end.x - grid.left, end.y - grid.top), delayMillis = 240)
            up()
        }
        (2..4).forEach { compose.onNodeWithTag("week-select-$it").assertIsOn() }
        compose.onNodeWithTag("week-select-1").assertIsOff()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Batch Teacher")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-save").performClick()
        weekRange()
        saveCourse()
        compose.runOnIdle { assertEquals(mapOf(2 to "Batch Teacher", 3 to "Batch Teacher", 4 to "Batch Teacher"), saved?.weekTeachers) }
    }

    @Test fun tappingSeveralWeeksThenTypingSavesEveryTappedWeek() {
        var saved: CourseItem? = null
        setContent(onSave = { saved = it })
        compose.onNodeWithTag("course-title-field").performTextReplacement("Tap course")
        compose.onNodeWithTag("course-location-field").performScrollTo().performTextReplacement("Default room")
        compose.onNodeWithTag("course-location-weeks").performClick()
        compose.onNodeWithTag("week-select-2").performClick()
        compose.onNodeWithTag("week-select-3").performClick()
        compose.onNodeWithText(text(ScheduleR.string.schedule_week_location_value_label)).assertIsDisplayed()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Lab 305")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-save").performClick()
        weekRange()
        saveCourse()
        compose.runOnIdle {
            assertEquals("Default room", saved?.location)
            assertEquals(mapOf(2 to "Lab 305", 3 to "Lab 305"), saved?.weekLocations)
        }
    }

    @Test fun typingOnceAndApplyingToAllWeeksKeepsLaterOverrides() {
        var saved: CourseItem? = null
        setContent(onSave = { saved = it })
        compose.onNodeWithTag("course-title-field").performTextReplacement("All course")
        compose.onNodeWithTag("course-teacher-weeks").performScrollTo().performClick()
        compose.onNodeWithTag("week-pick-all").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("One Teacher")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-save").performClick()
        compose.onNodeWithTag("course-teacher-weeks").performScrollTo().performClick()
        compose.onNodeWithTag("week-select-3").performClick()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Only week 3")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        compose.onNodeWithTag("week-batch-save").performClick()
        weekRange()
        saveCourse()
        compose.runOnIdle {
            assertEquals(mapOf(1 to "One Teacher", 2 to "One Teacher", 3 to "Only week 3",
                4 to "One Teacher", 5 to "One Teacher"), saved?.weekTeachers)
        }
    }

    @Test fun cancellingBatchLocationsLeavesTheCourseDraftUnchanged() {
        var saved: CourseItem? = null
        setContent(onSave = { saved = it })
        compose.onNodeWithTag("course-title-field").performTextReplacement("Cancel course")
        compose.onNodeWithTag("course-location-field").performScrollTo().performTextReplacement("Default room")
        compose.onNodeWithTag("course-location-weeks").performClick()
        compose.onNodeWithTag("week-select-2").performClick()
        compose.onNodeWithTag("week-select-3").performClick()
        compose.onNodeWithTag("week-batch-value-field").performScrollTo().performTextReplacement("Changed room")
        compose.onNodeWithTag("week-batch-apply").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText(text(ScheduleR.string.schedule_action_cancel)) and
            hasAnyAncestor(hasTestTag("course-week-values-dialog"))).performClick()
        weekRange()
        saveCourse()
        compose.runOnIdle { assertEquals("Default room", saved?.location); assertTrue(saved!!.weekLocations.isEmpty()) }
    }
}
