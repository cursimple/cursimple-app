package com.x500x.cursimple.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/**
 * Opt-in (`-e termQa true`): copy a term with its course, read its statistics, archive and unarchive it.
 * The fixture term is created in the throw-away app data and the copy is removed afterwards.
 */
class TermCopyArchiveStatsUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("termQa") == "true"
    private val termRepo by lazy { DataStoreTermProfileRepository(context) }
    private val manualRepo by lazy { DataStoreManualCourseRepository(context, termRepo) }
    private val sourceName = "QA甲学期"
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    // A fixed debug clock left by another test would make "today" stale; start from the real clock.
                    setDebugForcedDateTime(null)
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                }
                val source = termRepo.createTerm(sourceName, LocalDate.of(2026, 9, 14).toString())
                termRepo.setActiveTerm(source.id)
                manualRepo.addCourse(CourseItem("qa-math", "高等数学", weeks = listOf(1, 2, 3), time = CourseTimeSlot(1, 1, 2), location = "理工楼110"))
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun removeFixtures() {
        if (!enabled()) return
        runBlocking {
            DataStoreUserPreferencesRepository(context).setDebugForcedDateTime(null)
            termRepo.termsFlow.first()
                .filter { it.name == sourceName || it.name == "QA副本学期" || it.name == "QA归档学期" }
                .forEach { termRepo.deleteTerm(it.id) }
        }
    }

    private fun shot(name: String) {
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "term-qa/$name.png"))
    }

    /** Index of a term's row among the active list, which is the order the rows (and their action chips) appear in. */
    private fun liveRowIndex(name: String): Int = runBlocking {
        termRepo.termsFlow.first().sortedBy { it.createdAt }.filterNot { it.archived }.indexOfFirst { it.name == name }
    }

    private fun openTermManagement() {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_add_to_schedule)).performClick()
        compose.onNodeWithText(activity.getString(R.string.main_manage_terms)).performClick()
        compose.waitForIdle()
    }

    @Test fun theStatisticsOfATermShowItsTotalsAndCourseShares() {
        assumeTrue(enabled())
        openTermManagement()
        compose.onAllNodesWithText(compose.activity.getString(R.string.term_action_stats))[liveRowIndex(sourceName)].performClick()
        compose.onNodeWithTag("term-stats-dialog").assertIsDisplayed()
        // Statistics load asynchronously; wait for the totals to appear.
        try {
            compose.waitUntil(8_000) {
                compose.onAllNodesWithText(compose.activity.getString(ScheduleR.string.stats_total_sessions)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            shot("stats-not-loaded")
            throw error
        }
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.stats_total_sessions)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.stats_by_course)).assertIsDisplayed()
        shot("stats")
        compose.onNodeWithContentDescription(compose.activity.getString(ScheduleR.string.schedule_action_close)).performClick()
    }

    @Test fun copyingATermBringsItsCourseIntoANewTermWithoutActivatingIt() {
        assumeTrue(enabled())
        openTermManagement()
        val activity = compose.activity
        compose.onAllNodesWithText(activity.getString(R.string.term_action_copy))[liveRowIndex(sourceName)].performClick()
        compose.onNodeWithTag("term-copy-name").performTextReplacement("QA副本学期")
        shot("copy-dialog")
        compose.onNodeWithTag("term-copy-confirm").performClick()
        compose.waitUntil(8_000) { runBlocking { termRepo.termsFlow.first().any { it.name == "QA副本学期" } } }
        val copy = runBlocking { termRepo.termsFlow.first().first { it.name == "QA副本学期" } }
        // The term appears first; its courses are written a moment later, so wait for them.
        compose.waitUntil(8_000) { runBlocking { manualRepo.coursesFor(copy.id).isNotEmpty() } }
        val source = runBlocking { termRepo.termsFlow.first().first { it.name == sourceName } }
        val copiedCourses = runBlocking { manualRepo.coursesFor(copy.id) }
        val diag = runBlocking {
            val parts = mutableListOf<String>()
            for (t in termRepo.termsFlow.first()) parts += "${t.name}[${t.id.take(6)}] courses=${manualRepo.coursesFor(t.id).size}"
            parts.joinToString("; ")
        }
        assertEquals("copied courses (terms: $diag)", listOf("高等数学"), copiedCourses.map { it.title })
        assertTrue("weeks are cleared for the new term", copiedCourses.single().weeks.isEmpty())
        assertTrue("the copy does not take over the active term", runBlocking { termRepo.activeTermIdFlow.first() } == source.id)
        assertEquals(copiedCourses.size, runBlocking { manualRepo.coursesFor(source.id) }.size)
        shot("copied")
    }

    /** Swipe the term list until [text] is on screen; the list is lazy, so far rows are not composed yet. */
    private fun scrollListUntilVisible(text: String) {
        repeat(10) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNode(isRoot()).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + height * 0.2f) }
            compose.waitForIdle()
        }
    }

    /**
     * Click the action chip that sits in the row of [termName]: the nearest one below the name. Ancestor matching
     * would also match every other row, so the position on screen decides instead.
     */
    private fun clickActionOfRow(termName: String, action: String) {
        val nameTop = compose.onNodeWithText(termName).fetchSemanticsNode().boundsInRoot.top
        val chips = compose.onAllNodesWithText(action).fetchSemanticsNodes()
        val index = chips.indices
            .filter { chips[it].boundsInRoot.top >= nameTop }
            .minByOrNull { chips[it].boundsInRoot.top - nameTop }
            ?: error("no $action below $termName")
        compose.onAllNodesWithText(action)[index].performClick()
    }

    @Test fun archivingHidesATermUnderArchivedAndCanBeUndone() {
        assumeTrue(enabled())
        val archiveName = "QA归档学期"
        runBlocking { termRepo.createTerm(archiveName, null) }
        openTermManagement()
        val activity = compose.activity
        scrollListUntilVisible(archiveName)
        clickActionOfRow(archiveName, activity.getString(R.string.term_action_archive))
        compose.waitUntil(8_000) { runBlocking { termRepo.termsFlow.first().any { it.name == archiveName && it.archived } } }
        compose.onNodeWithText(activity.getString(R.string.term_archived_title)).performScrollTo().assertIsDisplayed()
        shot("archived")
        scrollListUntilVisible(archiveName)
        clickActionOfRow(archiveName, activity.getString(R.string.term_action_unarchive))
        compose.waitUntil(8_000) { runBlocking { termRepo.termsFlow.first().any { it.name == archiveName && !it.archived } } }
    }
}
