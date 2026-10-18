package com.x500x.cursimple.app

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.app.util.ScheduleImageExporter
import com.x500x.cursimple.app.util.ScheduleImageOptions
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.schedule.CourseDetailDialog
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Opt-in captures for the 0.8.0 announcement: conflict splitting and the 9:16 wallpaper. */
class AnnouncementFeatureCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val localeRule = object : ExternalResource() {
        override fun before() { if (enabled()) AppLocale.cache(context, language) }
        override fun after() { if (enabled()) AppLocale.cache(context, AppLanguage.System) }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(localeRule).around(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("featureCapture") == "true"
    private val language: AppLanguage = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val languageSuffix = args.getString("qaLanguage")?.takeIf { it == "en" || it == "zh-TW" }?.let { ".$it" }.orEmpty()
    private val localeContext by lazy { AppLocale.wrap(context, language) }
    private fun text(id: Int, vararg format: Any) = localeContext.getString(id, *format)

    /** Demo data follows the capture language so English shots carry no Chinese course names. */
    private fun tr(zh: String, tw: String, en: String) = when (language) {
        AppLanguage.English -> en
        AppLanguage.TraditionalChinese -> tw
        else -> zh
    }

    private val monday = LocalDate.now(ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private fun course(id: String, title: String, teacher: String, location: String, day: Int, node: Int) =
        CourseItem(id, title, teacher = teacher, location = location, weeks = (1..16).toList(), time = CourseTimeSlot(day, node, node + 1))
    private val courses = listOf(
        course("cap-a", tr("大学英语", "大學英語", "College English"), tr("刘老师", "劉老師", "Ms. Liu"), tr("教学楼 305", "教學樓 305", "Hall 305"), 3, 5),
        course("cap-b", tr("大学物理实验", "大學物理實驗", "Physics Lab"), tr("陈老师", "陳老師", "Dr. Chen"), tr("实验楼 108", "實驗樓 108", "Lab 108"), 3, 5),
        course("cap-c", tr("高等数学", "高等數學", "Calculus"), tr("王老师", "王老師", "Prof. Wang"), tr("教学楼 201", "教學樓 201", "Hall 201"), 1, 1),
        course("cap-d", tr("线性代数", "線性代數", "Linear Algebra"), tr("李老师", "李老師", "Prof. Li"), tr("教学楼 203", "教學樓 203", "Hall 203"), 2, 1),
        course("cap-e", tr("程序设计", "程式設計", "Programming"), tr("赵老师", "趙老師", "Mr. Zhao"), tr("机房 302", "電腦教室 302", "PC Lab 302"), 2, 5),
        course("cap-f", tr("中国近代史", "中國近代史", "Modern History"), tr("孙老师", "孫老師", "Dr. Sun"), tr("教学楼 101", "教學樓 101", "Hall 101"), 3, 3),
        course("cap-g", tr("高等数学", "高等數學", "Calculus"), tr("王老师", "王老師", "Prof. Wang"), tr("教学楼 201", "教學樓 201", "Hall 201"), 4, 3),
        course("cap-h", tr("体育", "體育", "PE"), tr("周老师", "周老師", "Coach Zhou"), tr("体育馆", "體育館", "Gym"), 4, 7),
        course("cap-i", tr("大学英语", "大學英語", "College English"), tr("刘老师", "劉老師", "Ms. Liu"), tr("教学楼 305", "教學樓 305", "Hall 305"), 5, 1),
        course("cap-j", tr("大学物理", "大學物理", "Physics"), tr("陈老师", "陳老師", "Dr. Chen"), tr("教学楼 B305", "教學樓 B305", "Hall B305"), 5, 5),
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(context.filesDir, "announcement-features/$name$languageSuffix.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }

    private fun fixtureLocals(base: Context) = arrayOf(
        LocalContext provides base,
        LocalConfiguration provides base.resources.configuration,
        androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,
    )

    @Test fun conflictSplitCard() {
        assumeTrue(enabled())
        var saved = 0
        val pair = courses.filter { it.id == "cap-a" || it.id == "cap-b" }
        compose.activity.setContent {
            val fixtureContext = AppLocale.wrap(context, language)
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = com.x500x.cursimple.core.data.ThemeAccent.Green) {
                CompositionLocalProvider(*fixtureLocals(fixtureContext)) {
                    CourseDetailDialog(
                        courses = pair, timingProfile = null, visibleWeekNumber = 3,
                        isManual = { true }, existingCourses = courses,
                        onSaveCourse = { saved++ }, onDismiss = {}, onSetReminder = {}, onDelete = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("course-conflict-resolution-card").assertIsDisplayed()
        capture("conflict-split")
        compose.onNodeWithTag("course-conflict-odd-even").performClick()
        compose.runOnIdle { check(saved == 2) { "expected both courses to be saved, got $saved" } }
    }

    @Test fun wallpaperExport() {
        assumeTrue(enabled())
        val wallpaper = mutableStateOf(false)
        val options = mutableStateOf(ScheduleImageOptions())
        compose.activity.setContent {
            val fixtureContext = AppLocale.wrap(context, language)
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = com.x500x.cursimple.core.data.ThemeAccent.Green) {
                CompositionLocalProvider(*fixtureLocals(fixtureContext)) {
                    ScheduleImageDialog(
                        weekNumber = 3, maxWeekNumber = 16, allWeeks = false,
                        wallpaperMode = wallpaper.value, options = options.value, generationKey = Unit,
                        generateImage = { asWallpaper, chosen ->
                            ScheduleImageExporter.export(
                                context = compose.activity, termName = tr("2026 秋季学期", "2026 秋季學期", "Fall 2026"), termStartDate = monday, weekNumber = 3,
                                schedule = null, manualCourses = courses, timingProfile = timing,
                                overrides = emptyList(), holidayCalendar = HolidayCalendarSettings(),
                                allWeeks = false, wallpaperMode = asWallpaper, events = events, options = chosen,
                            ).also { outcome -> keepImage(outcome, if (asWallpaper) "wallpaper" else "standard", chosen) }
                        },
                        onAllWeeksChange = {}, onWallpaperModeChange = { wallpaper.value = it },
                        onOptionsChange = { options.value = it },
                        onWeekChange = {}, onShare = {}, onDismiss = {},
                    )
                }
            }
        }
        awaitThumbnail()
        capture("image-standard")
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_ratio_wallpaper)).performClick()
        awaitThumbnail()
        capture("image-wallpaper")
        compose.onNodeWithTag("schedule-image-options").performClick()
        compose.onNodeWithTag("schedule-image-options-dialog").assertIsDisplayed()
        capture("image-options")
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_options_events)).performClick()
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_options_location)).performClick()
        compose.runOnIdle { check(options.value.showEvents && options.value.showLocation) }
        capture("image-options-on")
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_options_done)).performClick()
        awaitThumbnail()
        capture("image-wallpaper-custom")
        // Keep only Wednesday: a single-day lock screen.
        compose.onNodeWithTag("schedule-image-options").performClick()
        compose.onNodeWithTag("schedule-image-days").performScrollTo()
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            .forEach { day ->
                val label = day.getDisplayName(java.time.format.TextStyle.SHORT, localeContext.resources.configuration.locales[0])
                compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("schedule-image-days"))).performClick()
            }
        compose.runOnIdle { check(options.value.days == setOf(3)) { "days: ${options.value.days}" } }
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_options_days_weekdays)).performScrollTo()
        capture("image-options-days")
        compose.onNodeWithText(text(com.x500x.cursimple.R.string.ie_image_options_done)).performClick()
        awaitThumbnail()
        capture("image-wallpaper-day")
    }

    private fun awaitThumbnail() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("schedule-image-thumbnail").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        // A changed option regenerates in the background; let the new thumbnail land.
        Thread.sleep(1_500)
        compose.waitForIdle()
    }

    /** Copy each rendered PNG so the exported page itself can be inspected. */
    private fun keepImage(outcome: com.x500x.cursimple.app.util.ScheduleImageExportOutcome, mode: String, chosen: ScheduleImageOptions) {
        @Suppress("DEPRECATION")
        val uri = outcome.intent?.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM) ?: return
        val suffix = when {
            chosen == ScheduleImageOptions() -> ""
            chosen.days != ScheduleImageOptions.ALL_DAYS -> "-day"
            else -> "-custom"
        }
        val target = File(context.filesDir, "announcement-features/export-$mode$suffix$languageSuffix.png")
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private val events = listOf(
        ScheduleEvent("cap-ev", tr("社团例会", "社團例會", "Club meeting"), date = monday.plusDays(2).toString(),
            startTime = "12:10", endTime = "13:00", location = tr("学生活动中心", "學生活動中心", "Student Center"), repeatWeekly = true),
    )

    private val timing = com.x500x.cursimple.core.kernel.model.TermTimingProfile(monday.toString(), listOf(
        com.x500x.cursimple.core.kernel.model.ClassSlotTime(1, 2, "08:00", "09:40", "1–2"),
        com.x500x.cursimple.core.kernel.model.ClassSlotTime(3, 4, "10:10", "11:50", "3–4"),
        com.x500x.cursimple.core.kernel.model.ClassSlotTime(5, 6, "14:00", "15:40", "5–6"),
        com.x500x.cursimple.core.kernel.model.ClassSlotTime(7, 8, "16:10", "17:50", "7–8"),
    ))
}
