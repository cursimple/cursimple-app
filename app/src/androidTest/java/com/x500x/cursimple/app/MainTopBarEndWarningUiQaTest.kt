package com.x500x.cursimple.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseEndWarningMode
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Device QA for the real MainActivity top bar on a narrow timetable. */
class MainTopBarEndWarningUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val preferences by lazy { DataStoreUserPreferencesRepository(context) }
    private val termRepository by lazy { DataStoreTermProfileRepository(context) }
    private val manualRepository by lazy { DataStoreManualCourseRepository(context, termRepository) }
    private val widgetPreferences by lazy { DataStoreWidgetPreferencesRepository(context) }

    private lateinit var activeTermId: String
    private lateinit var originalTermStart: String
    private lateinit var originalCourses: List<CourseItem>
    private var originalTiming: TermTimingProfile? = null
    private var originalWarningMode = CourseEndWarningMode.Off

    private val setup = object : ExternalResource() {
        override fun before() {
            assumeTrue(args.getString("mainTopBarEndWarningQa") == "true")
            runBlocking {
            activeTermId = termRepository.activeTermId()
            check(activeTermId.isNotBlank()) { "QA requires an active term" }
            val term = termRepository.termsFlow.first().first { it.id == activeTermId }
            originalTermStart = term.termStartDate.orEmpty()
            originalCourses = manualRepository.manualCoursesFlow.first()
            originalTiming = widgetPreferences.timingProfileFlow.first()
            originalWarningMode = preferences.preferencesFlow.first().scheduleDisplay.courseEndWarningMode

            termRepository.setTermStartDate(activeTermId, "2026-08-31")
            preferences.setDisclaimerAccepted(true)
            preferences.setFirstRunGuideCompleted(true)
            preferences.setTermStartDate(LocalDate.of(2026, 8, 31))
            preferences.setTermStartUserDecided(true)
            preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 12, 7, 7, 0))
            preferences.setCourseEndWarningMode(CourseEndWarningMode.TwoWeeks)
            widgetPreferences.saveManualTimingProfile(
                TermTimingProfile(
                    termStartDate = "2026-08-31",
                    slotTimes = listOf(
                        ClassSlotTime(1, 2, "08:00", "09:35", label = "第1节"),
                        ClassSlotTime(3, 4, "10:05", "11:40", label = "第2节"),
                        ClassSlotTime(5, 6, "14:00", "15:35", label = "第3节"),
                    ),
                ),
            )
            manualRepository.replaceAll(
                listOf(
                    CourseItem("qa-top-near", "高等数学", location = "A101", weeks = (1..16).toList(), time = CourseTimeSlot(1, 1, 2)),
                    CourseItem("qa-top-final", "大学英语", location = "B202", weeks = (1..15).toList(), time = CourseTimeSlot(2, 1, 2)),
                ),
            )
            }
            AppLocale.cache(context, com.x500x.cursimple.core.data.AppLanguage.Chinese)
        }
        override fun after() {
            if (!::activeTermId.isInitialized) return
            runBlocking {
            manualRepository.replaceAll(originalCourses)
            termRepository.setTermStartDate(activeTermId, originalTermStart.ifBlank { null })
            originalTiming?.let { widgetPreferences.saveManualTimingProfile(it) }
            preferences.setCourseEndWarningMode(originalWarningMode)
            preferences.setDebugForcedDateTime(null)
            }
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @Test
    fun topBarKeepsBothWarningLabelsVisible() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("末周", substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                compose.onAllNodesWithText("倒二周", substring = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("末周", substring = true).assertIsDisplayed()
        compose.onNodeWithText("倒二周", substring = true).assertIsDisplayed()
        ScreenshotCapture.save(
            ScreenshotCapture.take(),
            File(context.filesDir, "main-top-bar-end-warning.png"),
        )
    }
}
