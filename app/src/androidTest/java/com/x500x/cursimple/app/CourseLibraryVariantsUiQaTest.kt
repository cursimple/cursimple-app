package com.x500x.cursimple.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.CourseLibraryRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.LocalDate

/** Opt-in captures of course-library cards that fold per-week changes into variants. */
class CourseLibraryVariantsUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seedRule = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            seed()
            AppLocale.cache(context, language)
        }
        override fun after() { if (enabled()) AppLocale.cache(context, AppLanguage.System) }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seedRule).around(compose)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("libraryVariantsQa") == "true"
    private val language: AppLanguage = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val suffix = buildString {
        args.getString("qaLanguage")?.takeIf { it == "en" || it == "zh-TW" }?.let { append(".$it") }
        if (args.getString("qaDark") == "true") append(".dark")
        args.getString("qaFontScale")?.let { append(".x$it") }
    }

    private fun tr(zh: String, tw: String, en: String) = when (language) {
        AppLanguage.English -> en
        AppLanguage.TraditionalChinese -> tw
        else -> zh
    }

    private fun seed() = runBlocking {
        val container = (context.applicationContext as ClassScheduleApplication).appContainer
        container.bootstrapJob.join()
        val preferences = container.userPreferencesRepository
        preferences.setDisclaimerAccepted(true)
        preferences.setFirstRunGuideCompleted(true)
        preferences.setTermStartDate(LocalDate.of(2026, 9, 7))
        preferences.setTermStartUserDecided(true)
        fun course(id: String, title: String, day: Int, node: Int, teacher: String, location: String) = CourseItem(
            id, title, teacher = teacher, location = location, weeks = (1..16).toList(), time = CourseTimeSlot(day, node, node + 1),
        )
        val math = tr("高等数学", "高等數學", "Calculus")
        val wang = tr("王老师", "王老師", "Prof. Wang")
        val hallA = tr("教学楼 A101", "教學樓 A101", "Hall A101")
        container.manualCourseRepository.replaceAll(listOf(
            course("qa-math-mon", math, 1, 1, wang, hallA).copy(
                weekLocations = (5..8).associateWith { tr("实验楼 B202", "實驗樓 B202", "Lab B202") },
                weekTeachers = mapOf(12 to tr("李老师", "李老師", "Prof. Li")),
            ),
            course("qa-math-mon-2", math, 1, 5, wang, hallA).copy(weeks = (1..8).toList()),
            course("qa-english", tr("大学英语", "大學英語", "College English"), 1, 3, tr("刘老师", "劉老師", "Ms. Liu"), tr("教学楼 305", "教學樓 305", "Hall 305")),
            course("qa-physics", tr("大学物理", "大學物理", "Physics"), 2, 5, tr("陈老师", "陳老師", "Dr. Chen"), tr("教学楼 B305", "教學樓 B305", "Hall B305")).copy(
                weekTitles = mapOf(9 to tr("大学物理实验", "大學物理實驗", "Physics Lab")),
                weekLocations = mapOf(9 to tr("实验楼 108", "實驗樓 108", "Lab 108")),
            ),
        ))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "library-variants/$name$suffix.png"))
    }

    @Test fun variantsExpandWithHighlightedChanges() {
        assumeTrue(enabled())
        compose.waitForIdle()
        val viewModel = ViewModelProvider(compose.activity)[ScheduleViewModel::class.java]
        compose.activity.setContent {
            val fixtureContext = AppLocale.wrap(context, language)
            ClassScheduleTheme(
                themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = ThemeAccent.Green,
            ) {
                CompositionLocalProvider(
                    LocalContext provides fixtureContext,
                    LocalConfiguration provides fixtureContext.resources.configuration,
                    LocalDensity provides Density(LocalDensity.current.density, args.getString("qaFontScale")?.toFloatOrNull() ?: 1f),
                ) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                        CourseLibraryRoute(viewModel = viewModel, modifier = Modifier.fillMaxSize().safeDrawingPadding())
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("course-library-variants-toggle").fetchSemanticsNodes().isNotEmpty() }
        capture("collapsed")
        compose.onAllNodesWithTag("course-library-variants-toggle")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("course-library-variant").fetchSemanticsNodes().size >= 4 }
        capture("expanded")
        compose.onAllNodesWithTag("course-library-variant").onLast().performScrollTo()
        capture("expanded-end")
    }
}
