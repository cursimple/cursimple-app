package com.x500x.cursimple.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.UUID
import com.x500x.cursimple.feature.schedule.R as ScheduleR

class MemoSearchUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "qa-memo-search-${UUID.randomUUID()}"
    private val notes = listOf(
        MemoNote("$prefix-title", title = "QA Memo 标题定位", body = "正文里面写着 PAPAYA_TOKEN。"),
        MemoNote("$prefix-done", title = "QA Memo 已完成笔记", completed = true,
            courseKey = memoCourseKey("查找课程 QACOURSE"), courseTitle = "查找课程 QACOURSE",
            body = (1..20).joinToString("\n") { "第 $it 行普通正文" } + "\n- [x] DEEP_SEARCH_TOKEN"),
    )
    private fun enabled() = InstrumentationRegistry.getArguments().getString("memoSearchQa") == "true"
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                }
                notes.forEach { DataStoreMemoRepository(context).upsert(it) }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun removeFixtures() {
        if (enabled()) runBlocking { notes.forEach { DataStoreMemoRepository(context).remove(it.id) } }
    }

    @Test fun toolbarSearchFindsTitleFullBodyAndCompletedCourseNotesAndRestoresFilter() {
        assumeTrue(enabled())
        val context = compose.activity
        compose.onNodeWithContentDescription(context.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(context.getString(R.string.screen_memos)).performClick()
        compose.onNodeWithText(context.getString(ScheduleR.string.memo_stat_todo)).performClick()
        compose.onNodeWithTag("memo-search-action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("memo-search-field").performTextInput("标题定位")
        compose.onNodeWithText(notes[0].title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memo-search-field").performTextReplacement("papaya_token")
        compose.onNodeWithText(notes[0].title).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(context.getString(ScheduleR.string.memo_editor_edit)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(ScheduleR.string.schedule_action_cancel)).performClick()
        compose.onNodeWithTag("memo-search-field").performTextReplacement("deep_search_token")
        compose.onNodeWithText(notes[1].title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memo-search-field").performTextReplacement("qacourse")
        compose.onNodeWithText(notes[1].title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memo-search-field").performTextReplacement("$prefix-no-match")
        compose.onNodeWithTag("memo-search-empty").assertIsDisplayed()
        compose.onNodeWithTag("memo-search-clear").performClick()
        compose.onNodeWithText(notes[1].title).assertDoesNotExist()
        compose.onNodeWithTag("memo-search-close").performClick()
        compose.onNodeWithTag("memo-search-field").assertDoesNotExist()
        compose.onNodeWithTag("memo-search-action").assertIsDisplayed()
    }
}
