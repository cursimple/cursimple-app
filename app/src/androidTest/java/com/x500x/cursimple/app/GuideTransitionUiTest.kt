package com.x500x.cursimple.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.guide.FirstRunGuideOverlay
import com.x500x.cursimple.app.guide.GuideStep
import com.x500x.cursimple.app.navigation.AnimatedPage
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Guide steps and the pages they open change with a transition instead of swapping in one frame. */
class GuideTransitionUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun nextStepCrossfadesTheCard() {
        val steps = listOf(
            GuideStep(R.string.guide_week_title, R.string.guide_week_body, null),
            GuideStep(R.string.guide_grid_title, R.string.guide_grid_body, null),
        )
        compose.setContent { ClassScheduleTheme(ThemeMode.Light) { FirstRunGuideOverlay(steps = steps, onFinish = {}) } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(text(R.string.guide_next)).performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText(text(R.string.guide_week_title)).assertExists()
        compose.onNodeWithText(text(R.string.guide_grid_title)).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.guide_week_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.guide_grid_title)).assertIsDisplayed()
    }

    @Test fun pagesSlideInAndBackOut() {
        var page by mutableStateOf<String?>(null)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                AnimatedPage(page) { Text(it, Modifier.fillMaxSize().testTag("page")) }
            }
        }
        compose.mainClock.autoAdvance = false
        page = "school"
        compose.mainClock.advanceTimeBy(80)
        val entering = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.left
        assertTrue("页面直接出现，没有过渡：left=$entering", entering > 0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0f, compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.left)

        page = null
        compose.mainClock.advanceTimeBy(60)
        val leaving = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.left
        assertTrue("返回应向右退出：left=$leaving", leaving > 0f)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("page").assertDoesNotExist()
    }
}
