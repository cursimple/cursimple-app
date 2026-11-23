package com.x500x.cursimple.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.app.notice.NoticeTheme
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.centerX
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

/**
 * Requires siteShotsQa=true and a dedicated emulator: it replaces the whole app state with a demo
 * term, then restores the full backup taken beforehand. Screenshots go to
 * externalFilesDir/site-shots and are used for the website.
 */
class SiteShotsCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as ClassScheduleApplication).appContainer

    private var backup: AppBackupPayload? = null
    private var previousLocale: Locale? = null
    private var previousLanguageCache: String? = null
    private var previousPromptPending: Boolean? = null
    private var setupStarted = false

    private val session = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(InstrumentationRegistry.getArguments().getString("siteShotsQa") == "true")
                try {
                    prepare()
                    base.evaluate()
                } finally {
                    restore()
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(session).around(compose)

    @Test fun captureSiteShots() {
        val activity = compose.activity
        waitForText(activity.getString(R.string.main_open_drawer), description = true)
        dismissStartupDialogs()
        save("week")

        // Day view, then back to week for the zoom shot.
        compose.onNodeWithText("周").performClick()
        compose.waitForIdle()
        waitForText("日")
        save("day")
        compose.onNodeWithText("日").performClick()
        compose.waitForIdle()
        waitForText("周")

        // Pinch zoom on the week grid (needs the preference, restored with the backup).
        runBlocking { container.userPreferencesRepository.setSchedulePinchZoomEnabled(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("schedule-week-pager").performTouchInput {
            val y = centerY
            pinch(
                start0 = Offset(centerX - width * 0.1f, y), end0 = Offset(centerX - width * 0.2f, y),
                start1 = Offset(centerX + width * 0.1f, y), end1 = Offset(centerX + width * 0.2f, y),
                durationMillis = 600,
            )
        }
        compose.waitForIdle()
        save("zoom")
        runBlocking { container.userPreferencesRepository.setSchedulePinchZoomEnabled(false) }
        compose.waitForIdle()

        // Drawer screens.
        openScreen(R.string.screen_courses)
        waitForText("事务 8")
        compose.onNodeWithText("事务 8").performClick()
        compose.waitForIdle()
        save("library")

        openScreen(R.string.screen_settings)
        waitForText("课程与时间")
        save("settings")
    }

    /** The custom text-color picker, opened from the manual add-course form. */
    @Test fun captureColorPicker() {
        val activity = compose.activity
        waitForText(activity.getString(R.string.main_open_drawer), description = true)
        dismissStartupDialogs()
        compose.onNodeWithContentDescription(activity.getString(R.string.main_add_to_schedule)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(activity.getString(R.string.main_add_course_manually)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("course-weeks-fill-term").performClick()
        compose.onNodeWithTag("appearance-fill-custom").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("color-picker-hex").assertIsDisplayed()
        save("color-picker")
    }

    /** The in-app class notice (overlay skin) floating above the week grid. */
    @Test fun captureClassNotice() {
        val activity = compose.activity
        waitForText(activity.getString(R.string.main_open_drawer), description = true)
        dismissStartupDialogs()
        instrumentation.runOnMainSync {
            ClassNoticeOverlay.dismiss()
            ClassNoticeNotifier.notify(
                context,
                ClassNoticeNotifier.Content("大学物理", "格物楼D210", "10:00-11:40", 50),
                ClassNoticePreferences(
                    skin = ClassNoticeSkin.Overlay,
                    animation = ClassNoticeAnimation.None,
                    focusNotificationEnabled = false,
                    bannerDurationSeconds = 60,
                ),
                NoticeTheme.of(ThemeAccent.Green, dark = false),
            )
        }
        runBlocking { ClassNoticeOverlay.awaitShown() }
        Thread.sleep(800)
        try {
            save("notice")
        } finally {
            instrumentation.runOnMainSync {
                ClassNoticeOverlay.dismiss()
                ClassNoticeNotifier.cancel(context)
            }
        }
    }

    private fun openScreen(labelRes: Int) {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(activity.getString(labelRes)).performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String, description: Boolean = false) {
        compose.waitUntil(timeoutMillis = 15_000) {
            val nodes = if (description) compose.onAllNodesWithContentDescription(text) else compose.onAllNodesWithText(text)
            nodes.fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun dismissStartupDialogs() {
        val later = compose.activity.getString(R.string.main_later)
        val dialogs = compose.onAllNodesWithText(compose.activity.getString(R.string.main_term_start_missing_title))
        if (dialogs.fetchSemanticsNodes().indices.any { dialogs[it].isDisplayed() }) {
            compose.onNodeWithText(later).performClick()
        }
    }

    private fun prepare() {
        runBlocking {
            val app = container
            app.bootstrapJob.join()
            backup = app.exportAppBackup()
            val preferences = app.userPreferencesRepository
            previousLocale = Locale.getDefault()
            previousLanguageCache = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).getString("app_language", null)
            val promptPrefs = context.getSharedPreferences("force_stop_monitor", Context.MODE_PRIVATE)
            previousPromptPending = if (promptPrefs.contains("prompt_pending")) promptPrefs.getBoolean("prompt_pending", false) else null
            setupStarted = true

            preferences.setThemeMode(ThemeMode.Light)
            preferences.setThemeAccent(ThemeAccent.Green)
            preferences.setAppLanguage(AppLanguage.Chinese)
            preferences.setDisclaimerAccepted(true)
            preferences.setFirstRunGuideCompleted(true)
            preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            preferences.setAutoUpdateEnabled(false)
            preferences.markNotificationPermissionStartupAsked()
            preferences.markIslandStartupPromptShown()
            AppLocale.cache(context, AppLanguage.Chinese)
            promptPrefs.edit().putBoolean("prompt_pending", false).commit()

            val terms = app.termProfileRepository
            val termId = terms.activeTermId()
            terms.renameTerm(termId, "2026-2027 第1学期")
            terms.setTermStartDate(termId, TERM_START)
            preferences.setTermStartDate(java.time.LocalDate.parse(TERM_START))
            preferences.setDebugForcedDateTime(NOW)
            app.widgetPreferencesRepository.createTimingProfile("默认", SLOTS)
            app.manualCourseRepository.replaceAll(COURSES)
            app.scheduleEventRepository.allEvents().forEach { app.scheduleEventRepository.remove(it.id) }
            EVENTS.forEach { app.scheduleEventRepository.upsert(it) }
            BeijingTime.setForcedNow(NOW)
            app.refreshScheduleOutputs()
        }
        instrumentation.waitForIdleSync()
    }

    private fun restore() {
        if (!setupStarted) return
        try {
            backup?.let { original -> runBlocking { container.restoreAppBackup(original) } }
            runBlocking { container.userPreferencesRepository.setDebugForcedDateTime(null) }
        } finally {
            val languageEditor = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit()
            previousLanguageCache?.let { languageEditor.putString("app_language", it) } ?: languageEditor.remove("app_language")
            languageEditor.commit()
            val promptEditor = context.getSharedPreferences("force_stop_monitor", Context.MODE_PRIVATE).edit()
            previousPromptPending?.let { promptEditor.putBoolean("prompt_pending", it) } ?: promptEditor.remove("prompt_pending")
            promptEditor.commit()
            previousLocale?.let { Locale.setDefault(it) }
            BeijingTime.setForcedNow(null)
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(600)
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "site-shots")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable: $name" }
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $name" }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "SiteShots"
        const val TERM_START = "2026-09-07"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 13, 9, 10)
        const val PREFIX = "site-shot"

        val SLOTS = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40",
            "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40",
            "19:00" to "19:45", "19:55" to "20:40",
        ).mapIndexed { index, (start, end) -> ClassSlotTime(index + 1, index + 1, start, end) }

        private val allWeeks = (1..16).toList()
        private fun course(key: String, title: String, day: Int, from: Int, location: String, teacher: String = "") = CourseItem(
            id = "$PREFIX-$key", title = title, teacher = teacher, location = location, weeks = allWeeks,
            time = CourseTimeSlot(day, from, from + 1),
        )

        val COURSES = listOf(
            course("mon1", "高等数学A", 1, 1, "明德楼-A201"),
            course("mon3", "大学英语", 1, 3, "博文楼-B305"),
            course("mon5", "线性代数", 1, 5, "明德楼-A105"),
            course("mon7", "体育（羽毛球）", 1, 7, "体育馆-2楼"),
            course("tue1", "数据结构", 2, 1, "信息楼-C204", "周明"),
            course("tue3", "大学物理", 2, 3, "格物楼-D210", "孙立"),
            course("tue5", "思想道德与法治", 2, 5, "博文楼-B101", "吴静"),
            course("wed1", "高等数学A", 3, 1, "明德楼-A201"),
            course("wed3", "计算机网络", 3, 3, "信息楼-C301"),
            course("wed7", "大学物理实验", 3, 7, "格物楼实验室-3"),
            course("thu1", "线性代数", 4, 1, "明德楼-A105"),
            course("thu3", "数据结构", 4, 3, "信息楼-C204"),
            course("thu5", "程序设计基础", 4, 5, "信息楼机房-2"),
            course("fri1", "大学英语", 5, 1, "博文楼-B305"),
            course("fri5", "计算机网络", 5, 5, "信息楼-C301"),
        )

        private const val BLUE = 0xFFD9E6FAL
        private const val PINK = 0xFFFADCE4L
        private const val YELLOW = 0xFFFBEBC8L
        private const val PURPLE = 0xFFE4DCF7L
        private const val GREEN = 0xFFD5EBD2L
        private fun event(key: String, title: String, date: String, start: String, end: String, location: String, color: Long) =
            ScheduleEvent(id = "$PREFIX-ev-$key", title = title, date = date, startTime = start, endTime = end, location = location, colorArgb = color)

        val EVENTS = listOf(
            event("run", "晨跑", "2026-10-12", "07:00", "07:30", "", BLUE),
            event("meeting", "组会", "2026-10-13", "10:20", "11:10", "信息楼C410", BLUE),
            event("print", "打印材料", "2026-10-13", "10:40", "10:55", "图书馆一楼", PINK),
            event("advisor", "导师面谈", "2026-10-13", "11:00", "11:30", "信息楼C502", YELLOW),
            event("club", "社团例会", "2026-10-14", "12:20", "13:10", "学生活动中心", YELLOW),
            event("report", "交报告", "2026-10-15", "15:00", "15:30", "信息楼C204", YELLOW),
            event("badminton", "羽毛球", "2026-10-16", "18:00", "19:00", "体育馆", PURPLE),
            event("movie", "看电影", "2026-10-17", "19:00", "20:30", "万达影城", GREEN),
        )
    }
}
