package com.x500x.cursimple.app

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Opt-in in-memory QA: no real notes, alarms, preferences or accounts are touched. */
class CalendarMonthSwipeUiQaTest {
    @get:Rule val compose = createComposeRule()
    private fun month(year: Int, value: Int): String = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(com.x500x.cursimple.feature.schedule.R.string.schedule_date_picker_month, year, value)

    @Test fun monthSwipesKeepSelectionAndStillAllowDateTapsAndArrows() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("calendarSwipeQa") == "true")
        var selected by mutableStateOf(LocalDate.of(2026, 9, 7))
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Surface(Modifier.width(288.dp)) {
                        CalendarMonthPicker(selected, onSelect = { selected = it })
                    }
                }
            }
        }
        val grid = compose.onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot
        val day = compose.onNodeWithTag("calendar-day-2026-09-10").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("calendar-month-grid").performTouchInput {
            val start = Offset(day.x - grid.left, day.y - grid.top)
            down(start); moveTo(Offset(8f, start.y), delayMillis = 180); up()
        }
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 10))
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 9, 7), selected) }
        compose.onNodeWithTag("calendar-day-2026-10-10").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 10), selected) }
        compose.onNodeWithTag("calendar-month-grid").performTouchInput { swipeRight() }
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 9))
        compose.onNodeWithTag("calendar-next-month").performClick()
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 10))
        compose.onNodeWithTag("calendar-prev-month").performClick()
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 9))
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 10), selected) }
    }

    @Test fun cancelledAndVerticalDragsLeaveMonthAndDateIntact() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("calendarSwipeQa") == "true")
        var selected by mutableStateOf(LocalDate.of(2026, 9, 7))
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Dark) {
                Surface(Modifier.width(288.dp)) { CalendarMonthPicker(selected, onSelect = { selected = it }) }
            }
        }
        compose.onNodeWithTag("calendar-month-grid").performTouchInput {
            down(center); moveTo(center + Offset(-8f, 0f), delayMillis = 200); cancel()
        }
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 9))
        compose.onNodeWithTag("calendar-month-grid").performTouchInput { swipeUp() }
        compose.onNodeWithTag("calendar-visible-month").assertTextEquals(month(2026, 9))
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 9, 7), selected) }
    }
}
