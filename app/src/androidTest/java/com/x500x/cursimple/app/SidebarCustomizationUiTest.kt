package com.x500x.cursimple.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Sidebar customization on a dedicated emulator; restores the default sidebar afterward. */
class SidebarCustomizationUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("layoutQa") == "true"
    private val repository get() = DataStoreUserPreferencesRepository(context)

    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                repository.apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                    setAppLayout(null)
                    setDrawerLayout(
                        DrawerLayout.DEFAULT.copy(hidden = NavDestination.entries.filter { it.inDrawer && !it.locked }.toSet()),
                    )
                }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun restore() {
        if (!enabled()) return
        runBlocking { repository.setDrawerLayout(DrawerLayout.DEFAULT) }
    }

    @Test fun hiddenItemsStayReachableThroughMore() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.main_drawer_more, 5)).assertIsDisplayed()
        compose.onNode(hasText(activity.getString(R.string.screen_courses))).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).assertIsDisplayed()

        compose.onNodeWithTag("drawer-more").performClick()
        compose.onNodeWithTag("drawer-more-sheet").assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.screen_about)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(activity.getString(R.string.about_tagline))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun lockedItemsHaveNoSwitch() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_sidebar_title)).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("sidebar-item-schedule")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("sidebar-item-schedule")).assert(hasAnyDescendant(isToggleable()).not())
        compose.onNode(hasTestTag("sidebar-item-settings")).assert(hasAnyDescendant(isToggleable()).not())
        compose.onNode(hasTestTag("sidebar-item-courses")).performScrollTo().assert(hasAnyDescendant(isToggleable()))
    }
}
