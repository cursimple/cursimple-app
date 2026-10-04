package com.x500x.cursimple.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import com.x500x.cursimple.feature.schedule.ScheduleSettingsScreen
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/**
 * 本地公告截图：主统一 build/run；须传 instrumentation 参数 releaseCaptureQa=true。
 * 不写高级日期、课表或学期。备忘录仅插入本次 UUID 笔记，结束/失败时按 ID 清理。
 * 输出在 targetContext.getExternalFilesDir(null)/release-capture/，包含真实系统栏。
 */
class ReleasePreviewCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixtureZone = ZoneId.of("Asia/Shanghai")
    private val fixtureNow = LocalDateTime.of(2026, 9, 7, 8, 30)
    private val fixturePrefix = "release-preview-${UUID.randomUUID()}"
    private val insertedNoteIds = linkedSetOf<String>()
    private val fixtureNotes = listOf(
        MemoNote(
            id = "$fixturePrefix-math",
            courseKey = memoCourseKey("高等数学"), courseTitle = "高等数学",
            title = "期末复习重点", body = "极限、导数与定积分，整理典型题。",
            priority = MemoPriority.High, pinned = true, updatedAt = 3L,
        ),
        MemoNote(
            id = "$fixturePrefix-algebra",
            courseKey = memoCourseKey("线性代数"), courseTitle = "线性代数",
            title = "期末习题整理", body = "矩阵与特征值，复习课堂例题。",
            priority = MemoPriority.Medium, updatedAt = 2L,
        ),
        MemoNote(
            id = "$fixturePrefix-english",
            courseKey = memoCourseKey("英语"), courseTitle = "英语",
            title = "期末词汇整理", body = "已整理阅读词汇与作文句型。",
            completed = true, updatedAt = 1L,
        ),
    )

    // 只临时覆盖截图所需的键；恢复时保留其他键在运行期间的现值。
    private val temporaryPreferenceKeys = setOf(
        "theme_mode", "app_language", "disclaimer_accepted", "first_run_guide_completed",
        "last_seen_version_code", "auto_update_enabled",
        "notification_permission_startup_asked", "island_startup_prompt_shown",
    )
    private var savedPreferences: PreferencesStoreSnapshot? = null
    private var previousForcedNow: LocalDateTime? = null
    private var previousLocale: Locale? = null
    private var previousLanguageCache: String? = null
    private var previousPromptPending: Boolean? = null
    private var setupStarted = false

    private val captureSession = TestRule { base: Statement, description: Description ->
        object : Statement() {
            override fun evaluate() {
                // 外层 gate：未显式启用时，连 MainActivity 都不启动。
                assumeTrue(InstrumentationRegistry.getArguments().getString("releaseCaptureQa") == "true")
                try {
                    prepareSession(description.methodName == "captureMemoSearch")
                    base.evaluate()
                } finally {
                    restoreSession()
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(captureSession).around(compose)

    /** Demo records are passed to the real screen; no alarms or Clock requests are created. */
    @Test fun captureSystemClock() {
        val activity = compose.activity
        val fixtureContext = AppLocale.wrap(activity, AppLanguage.Chinese)
        val now = System.currentTimeMillis()
        val tomorrow = Instant.ofEpochMilli(now).atZone(fixtureZone).toLocalDate().plusDays(1)
        val records = listOf("07:45" to "高等数学 · 08:00 上课", "09:45" to "大学英语 · 10:00 上课").mapIndexed { index, (time, title) ->
            SystemAlarmRecord(
                alarmKey = "$fixturePrefix-clock-$index", ruleId = "$fixturePrefix-rule-$index",
                pluginId = "manual", planId = "$fixturePrefix-plan-$index",
                triggerAtMillis = tomorrow.atTime(LocalTime.parse(time)).atZone(fixtureZone).toInstant().toEpochMilli(),
                message = title, displayTitle = title, backend = ReminderAlarmBackend.AppAlarmClock,
                enabled = true, manualAlarm = true, createdAtMillis = now,
            )
        }
        compose.runOnUiThread {
            activity.setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides activity,
                    LocalContext provides fixtureContext,
                    LocalConfiguration provides fixtureContext.resources.configuration,
                    LocalAppZone provides fixtureZone,
                ) {
                    ClassScheduleTheme(ThemeMode.Light) {
                        SideEffect {
                            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                                isAppearanceLightStatusBars = true
                                isAppearanceLightNavigationBars = true
                            }
                        }
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                                Text("提醒", modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.titleMedium)
                                ScheduleSettingsScreen(
                                    state = ScheduleUiState(initialized = true, systemAlarmRecords = records),
                                    alarmRingtoneUri = null, alarmAlertMode = AlarmAlertMode.RingAndVibrate,
                                    alarmRingDurationSeconds = 120, alarmRepeatIntervalSeconds = 300, alarmRepeatCount = 5,
                                    onAlarmRingtoneUriChange = {}, onAlarmAlertModeChange = {},
                                    onAlarmRingDurationSecondsChange = {}, onAlarmRepeatIntervalSecondsChange = {}, onAlarmRepeatCountChange = {},
                                    onPickSystemRingtone = {}, onPickLocalAudio = {},
                                    onSaveRule = { _, _, _, _, _, _, _ -> }, onSetRuleEnabled = { _, _ -> }, onRemoveRule = {},
                                    onSavePlaceholder = { _, _, _, _, _, _, _ -> }, onDeletePlaceholder = {},
                                    onSaveExamReminder = { _, _, _ -> }, onRefreshAlarms = {}, onDeleteAlarm = { _, _ -> },
                                    onSetAppAlarmEnabled = { _, _ -> }, onUpdateAppAlarm = { _, _ -> },
                                    onCreateManualAlarm = { _, _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("写入系统时钟").performClick()
        compose.onAllNodes(hasText("07:45", substring = true) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().also { check(it.isNotEmpty()) }
        compose.onAllNodes(hasText("09:45", substring = true) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().also { check(it.isNotEmpty()) }
        // Keep the screenshot focused on the real pre-export summary, without sending any request.
        saveScreenshot("system-clock.png")
    }

    @Test fun captureAgenda() {
        val activity = compose.activity
        val fixtureContext = AppLocale.wrap(activity, AppLanguage.Chinese)
        val termStart = fixtureNow.toLocalDate()
        val profile = TermTimingProfile(
            termStart.toString(),
            listOf(ClassSlotTime(1, 2, "08:00", "09:00"), ClassSlotTime(3, 4, "09:30", "10:30")),
        )
        val courses = listOf(
            CourseItem("$fixturePrefix-math", "高等数学", location = "A101", time = CourseTimeSlot(1, 1, 2)),
            CourseItem("$fixturePrefix-algebra", "线性代数", location = "A203", time = CourseTimeSlot(1, 1, 2)),
            CourseItem("$fixturePrefix-english", "大学英语", location = "B202", time = CourseTimeSlot(1, 3, 4)),
        )
        compose.waitForIdle()
        compose.runOnUiThread {
            // 仅当前 process；finally 恢复原高级时间，不持久化 fixture 日期。
            BeijingTime.setForcedNow(fixtureNow)
            activity.setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides activity,
                    LocalContext provides fixtureContext,
                    LocalConfiguration provides fixtureContext.resources.configuration,
                    LocalAppZone provides fixtureZone,
                ) {
                    ClassScheduleTheme(ThemeMode.Light) {
                        val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                        SideEffect {
                            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                                isAppearanceLightStatusBars = lightBars
                                isAppearanceLightNavigationBars = lightBars
                            }
                        }
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            ScheduleScreen(
                                state = ScheduleUiState(initialized = true, manualCourses = courses, timingProfile = profile),
                                overrideTermStart = termStart,
                                holidayCalendar = HolidayCalendarSettings.NONE,
                                scheduleDisplay = ScheduleDisplayPreferences(todayOverviewEnabled = true),
                                viewMode = ScheduleViewMode.Day, dayOffset = 0,
                                minWeekOffset = 0, maxWeekOffset = 20,
                                onCreateCourseReminder = { _, _, _ -> },
                                onMuteExamReminder = {}, onRestoreExamReminder = {},
                                onRemoveReminderRule = {}, onRemoveManualCourse = {},
                                onCreateBulkReminder = { _, _, _ -> },
                                onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {},
                                onResetDay = {}, onOpenPluginMarket = {},
                                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("today-overview-card").assertIsDisplayed().performClick()
        compose.onNodeWithTag("today-overview-sheet").assertIsDisplayed()
        compose.onNodeWithText(fixtureContext.getString(ScheduleR.string.schedule_today_count, 3, 1)).assertIsDisplayed()
        awaitVisibleText(fixtureContext.getString(ScheduleR.string.schedule_today_minutes_to_end, 30L))
        awaitVisibleText(fixtureContext.getString(ScheduleR.string.schedule_today_next_brief, "大学英语", "09:30"))
        saveScreenshot("agenda.png")
    }

    @Test fun captureMemoSearch() {
        val activity = compose.activity
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(activity.getString(R.string.main_open_drawer))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 没有开学日期时关闭真实提示，不为截图写开学日期。
        compose.waitForIdle()
        val missingTerm = compose.onAllNodesWithText(activity.getString(R.string.main_term_start_missing_title))
        if (missingTerm.fetchSemanticsNodes().indices.any { missingTerm[it].isDisplayed() }) {
            compose.onNodeWithText(activity.getString(R.string.main_later)).performClick()
        }
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_memos)).performClick()
        // 真实 MainActivity 右上角搜索按钮，保留产品的导航栏和系统栏适配。
        compose.onNodeWithTag("memo-search-action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("memo-search-field").performTextReplacement("期末")
        compose.onNodeWithTag("memo-search-field").performImeAction()
        compose.runOnUiThread {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            var hidden = false
            instrumentation.runOnMainSync {
                hidden = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            hidden
        }
        fixtureNotes.forEach { awaitVisibleText(it.title) }
        compose.onNode(
            hasContentDescription(activity.getString(ScheduleR.string.memo_mark_undone)) and
                hasAnyAncestor(hasText(fixtureNotes.last().title)),
        ).assertIsDisplayed()
        saveScreenshot("memo-search.png")
    }

    private fun prepareSession(seedMemos: Boolean) {
        runBlocking {
            (context.applicationContext as ClassScheduleApplication).appContainer.bootstrapJob.join()
            val preferences = DataStoreUserPreferencesRepository(context)
            previousForcedNow = preferences.preferencesFlow.first().debugForcedDateTime
            savedPreferences = preferences.exportBackupSnapshot()
            previousLocale = Locale.getDefault()
            previousLanguageCache = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).getString("app_language", null)
            val promptPrefs = context.getSharedPreferences("force_stop_monitor", Context.MODE_PRIVATE)
            previousPromptPending = if (promptPrefs.contains("prompt_pending")) promptPrefs.getBoolean("prompt_pending", false) else null
            setupStarted = true
            preferences.setThemeMode(ThemeMode.Light)
            preferences.setAppLanguage(AppLanguage.Chinese)
            preferences.setDisclaimerAccepted(true)
            preferences.setFirstRunGuideCompleted(true)
            preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            preferences.setAutoUpdateEnabled(false)
            preferences.markNotificationPermissionStartupAsked()
            preferences.markIslandStartupPromptShown()
            AppLocale.cache(context, AppLanguage.Chinese)
            promptPrefs.edit().putBoolean("prompt_pending", false).commit()
            if (seedMemos) {
                val repository = DataStoreMemoRepository(context)
                val existingIds = repository.notesFlow.first().mapTo(hashSetOf()) { it.id }
                check(fixtureNotes.none { it.id in existingIds }) { "Fixture ID collision" }
                fixtureNotes.forEach {
                    insertedNoteIds += it.id
                    repository.upsert(it)
                }
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun restoreSession() {
        if (!setupStarted) return
        try {
            runBlocking {
                val repository = DataStoreMemoRepository(context)
                insertedNoteIds.forEach { repository.remove(it) }
            }
        } finally {
            try {
                savedPreferences?.let { original ->
                    runBlocking {
                        val repository = DataStoreUserPreferencesRepository(context)
                        val current = repository.exportBackupSnapshot()
                        repository.restoreBackupSnapshot(current.copy(entries =
                            current.entries.filterNot { it.name in temporaryPreferenceKeys } +
                                original.entries.filter { it.name in temporaryPreferenceKeys },
                        ))
                    }
                }
            } finally {
                val languageEditor = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit()
                previousLanguageCache?.let { languageEditor.putString("app_language", it) }
                    ?: languageEditor.remove("app_language")
                languageEditor.commit()
                val promptEditor = context.getSharedPreferences("force_stop_monitor", Context.MODE_PRIVATE).edit()
                previousPromptPending?.let { promptEditor.putBoolean("prompt_pending", it) }
                    ?: promptEditor.remove("prompt_pending")
                promptEditor.commit()
                previousLocale?.let { Locale.setDefault(it) }
                BeijingTime.setForcedNow(previousForcedNow)
            }
        }
    }

    private fun awaitVisibleText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            val nodes = compose.onAllNodesWithText(text)
            nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        awaitTwoDrawnFrames()
        instrumentation.waitForIdleSync()
        val visibleWindow = instrumentation.uiAutomation.rootInActiveWindow
        check(visibleWindow == null || visibleWindow.packageName?.toString() == context.packageName) {
            "${visibleWindow?.packageName} is covering the preview; dismiss it before capturing $name"
        }
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "release-capture")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable: $name" }
        try {
            File(directory, name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $name" }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 等真实 View 绘制两帧；Dialog 优先于 Activity，不能只推进 Compose 测试时钟。 */
    private fun awaitTwoDrawnFrames() {
        val drawn = CountDownLatch(1)
        lateinit var root: View
        lateinit var listener: ViewTreeObserver.OnDrawListener
        instrumentation.runOnMainSync {
            root = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.hasWindowFocus() }
                    ?: compose.activity.window.decorView
            } else compose.activity.window.decorView
            check(root.isAttachedToWindow && root.width > 0 && root.height > 0) { "Capture window not laid out" }
            var frames = 0
            listener = ViewTreeObserver.OnDrawListener {
                frames++
                if (frames == 2) {
                    // onDraw 回调内不能移除监听；post 后这帧已完成绘制。
                    root.post {
                        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(listener)
                        drawn.countDown()
                    }
                } else if (frames == 1) {
                    root.postOnAnimation { root.invalidate() }
                }
            }
            root.viewTreeObserver.addOnDrawListener(listener)
            root.invalidate()
        }
        try {
            check(drawn.await(10, TimeUnit.SECONDS)) { "Timed out waiting for two real drawn frames" }
        } finally {
            instrumentation.runOnMainSync {
                if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(listener)
            }
        }
    }
}
