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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.guide.FirstRunGuideDialog
import com.x500x.cursimple.app.guide.FirstRunPermissionsPage
import com.x500x.cursimple.app.navigation.AnimatedPage
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.ClassNoticePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuideTransitionUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun animatedPagesCanBeSwipedAndSkippedWithoutOpeningAnotherScreen() {
        var visible by mutableStateOf(true)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                Text("Underlying page", Modifier.testTag("underlying"))
                if (visible) {
                    FirstRunGuideDialog(onFinish = { visible = false })
                }
            }
        }
        compose.onNodeWithTag("first-run-animation-0").assertIsDisplayed()
        compose.onNodeWithTag("first-run-pages").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("first-run-animation-1").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_next)).performClick()
        compose.onNodeWithTag("first-run-animation-2").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_previous)).performClick()
        compose.onNodeWithTag("first-run-animation-1").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_skip)).performClick()
        compose.onNodeWithTag("first-run-guide").assertDoesNotExist()
        compose.onNodeWithTag("underlying").assertIsDisplayed()
    }

    @Test fun permissionsCanBeReviewedBeforeWatchingTheDemos() {
        var permissionsReviewed by mutableStateOf(false)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                if (permissionsReviewed) {
                    FirstRunGuideDialog(onFinish = {})
                } else {
                    FirstRunPermissionsPage(
                        classNotice = ClassNoticePreferences(),
                        alarmKeepAliveEnabled = false,
                        onAlarmKeepAliveEnabledChange = {},
                        onContinue = { permissionsReviewed = true },
                    )
                }
            }
        }
        compose.onNodeWithTag("permission-overview").assertIsDisplayed()
        compose.onNodeWithTag("first-run-guide").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.guide_continue_to_demo)).performClick()
        compose.onNodeWithTag("first-run-permissions").assertDoesNotExist()
        compose.onNodeWithTag("first-run-animation-0").assertIsDisplayed()
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
