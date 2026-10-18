package com.x500x.cursimple.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.navigation.AppBottomBar
import com.x500x.cursimple.app.navigation.BarTab
import com.x500x.cursimple.app.navigation.MeScreen
import com.x500x.cursimple.app.navigation.SidebarSettingsPage
import com.x500x.cursimple.app.navigation.labelRes
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.navigation.AppLayoutSelection
import com.x500x.cursimple.core.data.navigation.BottomBarSize
import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.data.navigation.SHELL_BOTTOM_BAR
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/**
 * Bottom bar shell on a dedicated emulator. Needs the layout component pushed to
 * `<external files>/layout-component.zip`; restores the drawer afterward.
 */
class BottomBarShellUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("layoutQa") == "true"
    private val repository get() = DataStoreUserPreferencesRepository(context)
    private val items = listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)

    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            val manager = (context.applicationContext as ClassScheduleApplication).appContainer.pluginManager
            runBlocking {
                val result = manager.installPackage(
                    File(context.getExternalFilesDir(null), "layout-component.zip").readBytes(),
                    PluginInstallSource.Local,
                )
                check(result is PluginInstallResult.Success) { result.toString() }
                repository.apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                    setPluginEnabled(result.record.installKey, true)
                    setAppLayout(AppLayoutSelection(result.record.pluginId, SHELL_BOTTOM_BAR, items))
                }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun restore() {
        if (!enabled()) return
        runBlocking { repository.setAppLayout(null) }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun barShowsDefaultTabsInsteadOfMenu() {
        assumeTrue(enabled())
        awaitTag("bottom-bar")
        listOf("schedule", "memos", "components", "me").forEach {
            compose.onNodeWithTag("bar-tab-$it").assertIsDisplayed()
        }
        val menu = compose.activity.getString(R.string.main_open_drawer)
        compose.onNode(hasContentDescription(menu)).assertDoesNotExist()
    }

    @Test fun settingsOpenedFromMeReturnsToMe() {
        assumeTrue(enabled())
        awaitTag("bottom-bar")
        compose.onNodeWithTag("bar-tab-me").performClick()
        awaitTag("me-item-settings")
        compose.onNodeWithTag("me-item-settings").performClick()
        awaitTag("bar-back")
        compose.onNodeWithTag("bar-tab-me").assertIsSelected()
        pressBack()
        awaitTag("me-screen")
        compose.onNodeWithTag("bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("bar-tab-me").assertIsSelected()
    }

    @Test fun backOnMemosTabGoesToSchedule() {
        assumeTrue(enabled())
        awaitTag("bottom-bar")
        compose.onNodeWithTag("bar-tab-memos").performClick()
        compose.onNodeWithTag("bar-tab-memos").assertIsSelected()
        pressBack()
        compose.onNodeWithTag("bar-tab-schedule").assertIsSelected()
        compose.onNodeWithTag("bottom-bar").assertIsDisplayed()
    }

    @Test fun clearingTheLayoutBringsBackTheMenu() {
        assumeTrue(enabled())
        awaitTag("bottom-bar")
        runBlocking { repository.setAppLayout(null) }
        val menu = compose.activity.getString(R.string.main_open_drawer)
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(menu)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("bottom-bar")).assertDoesNotExist()
    }

    @Test fun labelsDoNotOverlapAtNarrowWidthAndLargeFont() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.activityRule.scenario.onActivity {
            it.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                    ClassScheduleTheme(themeMode = ThemeMode.Light) {
                        Box(Modifier.width(320.dp)) {
                            AppBottomBar(items = items, selected = BarTab.Me, meBadge = true, onSelect = {})
                        }
                    }
                }
            }
        }
        val labels = items.map { "bar-tab-${it.id}" to activity.getString(it.labelRes) } +
            ("bar-tab-me" to activity.getString(R.string.nav_me))
        val bounds = labels.map { (tag, label) ->
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getBoundsInRoot()
        }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("labels overlap: $left / $right", left.right <= right.left)
        }
    }

    @Test fun shortcutOpenedFromMeReturnsToMe() {
        assumeTrue(enabled())
        awaitTag("bottom-bar")
        compose.onNodeWithTag("bar-tab-me").performClick()
        awaitTag("me-screen")
        val overrides = compose.activity.getString(R.string.main_drawer_overrides_shortcut)
        compose.onNode(hasContentDescription(overrides)).performClick()
        awaitTag("bar-back")
        pressBack()
        awaitTag("me-screen")
        compose.onNodeWithTag("bar-tab-me").assertIsSelected()
    }

    @Test fun barSizeChangesItsHeight() {
        assumeTrue(enabled())
        val heights = BottomBarSize.entries.associateWith { size ->
            compose.activityRule.scenario.onActivity {
                it.setContent {
                    ClassScheduleTheme(themeMode = ThemeMode.Light) {
                        AppBottomBar(items = items, selected = BarTab.Me, meBadge = false, size = size, onSelect = {})
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("bar-tab-me").getBoundsInRoot().let { it.bottom - it.top }
        }
        assertTrue("$heights", heights.getValue(BottomBarSize.Small) < heights.getValue(BottomBarSize.Medium))
        assertTrue("$heights", heights.getValue(BottomBarSize.Medium) < heights.getValue(BottomBarSize.Large))
        // Medium is the default and must be smaller than the stock 80dp Material bar.
        assertTrue("$heights", heights.getValue(BottomBarSize.Medium) < 80.dp)
    }

    @Test fun meSummaryPutsWeekAndTermOnTheirOwnLines() {
        assumeTrue(enabled())
        compose.activityRule.scenario.onActivity {
            it.setContent {
                ClassScheduleTheme(themeMode = ThemeMode.Light) {
                    MeScreen(
                        overflow = emptyList(), weekLabel = "第 6 周", termLabel = "2026-2027 第1学期",
                        versionName = "0.7.11", updateBadge = false, badgeFor = { false },
                        onOpen = {}, onShortcut = {}, onOpenUpdate = {},
                    )
                }
            }
        }
        val week = compose.onNodeWithText("第 6 周").getBoundsInRoot()
        val term = compose.onNodeWithText("2026-2027 第1学期").getBoundsInRoot()
        assertTrue("term must sit below the week: $week / $term", term.top >= week.bottom)
        // One line each: the term is no taller than the week line.
        assertTrue("term wrapped: $term", term.bottom - term.top <= (week.bottom - week.top) * 1.2f)
        compose.onAllNodesWithText("·", substring = true).assertCountEquals(0)
    }

    @Test fun sidebarSettingsOffersBarSizeWhileBarIsActive() {
        assumeTrue(enabled())
        var picked: BottomBarSize? = null
        compose.activityRule.scenario.onActivity {
            it.setContent {
                ClassScheduleTheme(themeMode = ThemeMode.Light) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SidebarSettingsPage(
                            layout = DrawerLayout.DEFAULT, componentLayoutActive = true,
                            bottomBarSize = BottomBarSize.Medium, onBottomBarSizeChange = { size -> picked = size },
                            onChange = {}, onRestoreDrawer = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("bar-size-small").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BottomBarSize.Small, picked) }
    }
}
