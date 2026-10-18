package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.term.TermProfile
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/** Opt-in checks for selecting terms and confirming a merge on the term management page. */
class TermMergeUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    // The activity resolves its locale in attachBaseContext, so cache it before launch.
    private val localeRule = object : ExternalResource() {
        override fun before() { if (enabled()) AppLocale.cache(context, language) }
        override fun after() { if (enabled()) AppLocale.cache(context, AppLanguage.System) }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(localeRule).around(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("termMergeQa") == "true"
    private val language: AppLanguage = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val languageSuffix = args.getString("qaLanguage")?.takeIf { it == "en" || it == "zh-TW" }?.let { ".$it" }.orEmpty()
    /** Labels must resolve in the same locale the screen renders in. */
    private val localeContext by lazy { AppLocale.wrap(context, language) }
    private fun text(id: Int, vararg format: Any) = localeContext.getString(id, *format)

    /** Term names follow the capture language so English shots carry no Chinese names. */
    private fun tr(zh: String, tw: String, en: String) = when (language) {
        AppLanguage.English -> en
        AppLanguage.TraditionalChinese -> tw
        else -> zh
    }
    private val spring = tr("2026 春", "2026 春", "Spring 2026")
    private val labs = tr("2026 春·实验课", "2026 春·實驗課", "Spring 2026 Labs")
    private val summer = tr("暑期小学期", "暑期小學期", "Summer session")

    private val state = TermProfileUiState(
        terms = listOf(
            TermProfile("qa-a", spring, "2026-03-09", createdAt = 1L),
            TermProfile("qa-b", labs, "2026-02-23", createdAt = 2L),
            TermProfile("qa-c", summer, null, createdAt = 3L),
        ),
        activeTermId = "qa-a",
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(compose.activity.filesDir, "term-merge/${args.getString("qaVariant") ?: "normal"}/$name$languageSuffix.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }

    @Test fun selectTwoTermsAndConfirmMerge() {
        assumeTrue(enabled())
        var merged: Pair<List<String>, String>? = null
        var backed = false
        compose.activity.setContent {
            ClassScheduleTheme(
                themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    TermManagementScreen(
                        state = state, onBack = { backed = true }, onCreate = { _, _ -> },
                        onRename = { _, _ -> }, onSetStartDate = { _, _ -> }, onActivate = {}, onDelete = {},
                        onMerge = { ids, name -> merged = ids to name },
                    )
                }
            }
        }
        capture("list")
        compose.onNodeWithContentDescription(text(R.string.term_merge_action)).performClick()
        compose.onNodeWithText(text(R.string.term_merge_select_hint)).assertIsDisplayed()
        // 选择模式下隐藏行内操作，避免误触重命名/删除。
        compose.onAllNodesWithText(text(R.string.term_action_rename)).assertCountEquals(0)
        val confirm = hasText(text(R.string.term_merge_confirm)) and hasClickAction()
        compose.onNode(confirm).assertIsNotEnabled()

        compose.onNodeWithText(spring).performClick()
        compose.onNode(confirm).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.term_merge_selected_count, 1)).assertIsDisplayed()
        compose.onNodeWithText(labs).performClick()
        compose.onNode(confirm).assertIsEnabled()
        capture("selecting")

        // 返回键先退出选择模式，不离开页面。
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(false, backed)
        compose.onAllNodesWithText(text(R.string.term_merge_select_hint)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.term_action_rename)).assertCountEquals(3)

        compose.onNodeWithContentDescription(text(R.string.term_merge_action)).performClick()
        compose.onNodeWithText(spring).performClick()
        compose.onNodeWithText(labs).performClick()
        compose.onNode(confirm).performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.term_merge_message, "2026/2/23")).assertIsDisplayed()
        capture("dialog")

        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
            .assertTextContains("$spring + $labs")
            .performTextReplacement("春季合并")
        compose.onNode(hasText(text(R.string.term_merge_confirm)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(listOf("qa-a", "qa-b") to "春季合并", merged)
        compose.onAllNodesWithText(text(R.string.term_merge_select_hint)).assertCountEquals(0)
        capture("after")
    }
}
