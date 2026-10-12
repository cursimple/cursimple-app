package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.data.memo.MemoTransferCodec
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in device checks for the redesigned pages and safe note editing. */
class RequestedUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("requestedUiQa") == "true"
    private val language get() = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val course = CourseItem("qa-transfer-course", "高等数学", time = CourseTimeSlot(2, 1, 2))
    private val note = MemoNote("requested-ui-note", title = "QA 复习计划", priority = MemoPriority.High, pinned = true,
        courseKey = "qa-course", courseTitle = "高等数学", body = "## 本周安排\n\n**重点复习** · 保留原始内容\n\n1. 整理课堂笔记\n\n- [ ] 完成习题\n- [x] 已读讲义\n\n> 每天复习半小时。\n\n`公式与代码`\n\n普通段落可以正常换行。")
    private val second = MemoNote("requested-ui-done", title = "QA 已完成笔记", completed = true, body = "第二个笔记本的内容", priority = MemoPriority.Medium)
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            // ATD images disable drawing globally; enable it for real screenshot checks.
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                instrumentation.runOnMainSync { android.graphics.HardwareRenderer.setDrawingEnabled(true) }
            }
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDisclaimerAccepted(true); setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE); markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                    setThemeMode(if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light)
                    setThemeAccent(if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green)
                    setAppLanguage(language)
                    setTermStartUserDecided(true)
                    setTermStartDate(java.time.LocalDate.now())
                }
                DataStoreMemoRepository(context).apply { upsert(note); upsert(second) }
                DataStoreManualCourseRepository(context, DataStoreTermProfileRepository(context)).apply {
                    removeCourse(course.id); addCourse(course)
                }
            }
            AppLocale.cache(context, language)
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)
    @After fun cleanup() { if (enabled()) runBlocking {
        DataStoreMemoRepository(context).apply {
            notesFlow.first().filter { it.id == note.id || it.id == second.id || it.sharedOriginId == "qa-sender-origin" }.forEach { remove(it.id) }
        }
        DataStoreManualCourseRepository(context, DataStoreTermProfileRepository(context)).removeCourse(course.id)
    } }
    private fun screen(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val file = File(context.filesDir, "requested-ui/${args.getString("qaVariant") ?: "normal"}/$name.png")
        file.parentFile!!.mkdirs()
        var bitmap: Bitmap? = null
        repeat(6) {
            if (bitmap == null) {
                bitmap = instrumentation.uiAutomation.takeScreenshot()
                if (bitmap == null) Thread.sleep(200)
            }
        }
        val captured = checkNotNull(bitmap) { "Device returned no screenshot for $name" }
        file.outputStream().use { captured.compress(Bitmap.CompressFormat.PNG, 100, it) }
        captured.recycle()
    }
    private fun drawer(res: Int) {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(compose.activity.getString(res)).performClick()
    }
    private fun back() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }
    private fun stored() = runBlocking { DataStoreMemoRepository(context).notesFlow.first().first { it.id == note.id } }
    private fun returnFromShare() {
        context.startActivity(android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.x500x.cursimple.feature.widget.WidgetDeepLinks.EXTRA_OPEN_MEMO, "")
        })
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("memo-share-action")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test fun noteReadOnlyConfirmationAndAllFormats() {
        Assume.assumeTrue(enabled())
        val activity = compose.activity
        drawer(R.string.screen_memos)
        compose.onNodeWithText(note.title).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_editor_view)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        screen("memo-read")
        Assert.assertEquals(note.body, stored().body)
        compose.onNodeWithContentDescription(activity.getString(ScheduleR.string.schedule_action_edit)).performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_editor_edit)).assertIsDisplayed()
        screen("memo-edit")
        compose.onNode(hasSetTextAction() and hasText(note.title)).performTextReplacement("QA 确认后保存")
        back()
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_action_save)).performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_editor_save_confirm_title)).assertIsDisplayed()
        Assert.assertEquals(note.title, stored().title)
        screen("memo-save-confirm")
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_action_cancel)).performClick()
        Assert.assertEquals(note.title, stored().title)
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_action_save)).performClick()
        compose.onNode(
            hasText(activity.getString(ScheduleR.string.schedule_action_save)) and hasAnyAncestor(
                isDialog() and hasAnyDescendant(hasText(activity.getString(ScheduleR.string.memo_editor_save_confirm_title))),
            ),
        ).performClick()
        compose.waitUntil(10_000) { stored().title == "QA 确认后保存" }
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_editor_view)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.getString(ScheduleR.string.schedule_action_close)).performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_stat_todo)).performClick()
        for (format in listOf("cursimple", "word", "markdown", "image", "plaintext")) {
            compose.onNodeWithTag("memo-share-action").performClick()
            compose.onNodeWithTag("memo-share-sheet").assertIsDisplayed()
            // The completed fixture is included even with the todo chip selected.
            screen("memo-share-$format")
            compose.onNodeWithTag("memo-share-$format").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("memo-share-sheet").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
            returnFromShare()
        }
        compose.onNodeWithText("QA 确认后保存").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithContentDescription(activity.getString(ScheduleR.string.memo_more)).onFirst().performClick()
        compose.onNodeWithTag("memo-share-note").performClick()
        compose.onNodeWithTag("memo-share-sheet").assertIsDisplayed()
        screen("memo-share-single")
        compose.onNodeWithTag("memo-share-word").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("memo-share-sheet").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        returnFromShare()
    }

    @Test fun portableNotesExportReceiveAndRepeatedImport() {
        Assume.assumeTrue(enabled())
        drawer(R.string.screen_memos)
        compose.onNodeWithTag("memo-share-action").performClick()
        compose.onNodeWithTag("memo-import-action").performScrollTo().assertIsDisplayed()
        screen("memo-transfer")
        compose.onNodeWithTag("memo-share-cursimple").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("memo-share-sheet").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        val exported = File(context.cacheDir, "memos").walkTopDown().filter { it.isFile && it.extension == "csnotes" && it.name != "qa-incoming.csnotes" }.maxBy { it.lastModified() }
        Assert.assertTrue(MemoTransferCodec.decode(exported.readBytes()).notes.any { it.title == note.title && it.pinned })
        returnFromShare()
        // Simulate a file received from another installation with unrelated timetable IDs.
        val incoming = note.copy(id = "qa-sender-origin", title = "同学分享 · 极限与连续", courseKey = "sender-course",
            courseTitle = "  高等数学  ", body = "## 课堂重点\n\n**极限与连续**\n\n课后整理例题，周末完成复习。")
        val file = File(context.cacheDir, "memos/qa-incoming.csnotes").apply { writeBytes(MemoTransferCodec.encode(listOf(incoming))) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        fun receive(action: String) {
            context.startActivity(android.content.Intent(context, MainActivity::class.java).apply {
                this.action = action
                if (action == android.content.Intent.ACTION_SEND) { type = MemoTransferCodec.MIME; putExtra(android.content.Intent.EXTRA_STREAM, uri) }
                else setDataAndType(uri, MemoTransferCodec.MIME)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("memo-import-confirm").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        }
        receive(android.content.Intent.ACTION_SEND)
        screen("memo-import-preview")
        compose.onNodeWithTag("memo-import-confirm").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("memo-import-success").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val repository = DataStoreMemoRepository(context)
        val imported = runBlocking { repository.notesFlow.first().single { it.sharedOriginId == incoming.id } }
        Assert.assertEquals(memoCourseKey(course.title), imported.courseKey)
        Assert.assertEquals(course.title, imported.courseTitle)
        Assert.assertTrue(imported.pinned)
        Assert.assertEquals(incoming.priority, imported.priority)
        screen("memo-import-success")
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.schedule_action_close)).performClick()
        compose.onNodeWithText(incoming.title).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        screen("memo-imported-readonly")
        compose.onNodeWithContentDescription(compose.activity.getString(ScheduleR.string.schedule_action_close)).performClick()
        runBlocking { repository.upsert(imported.copy(body = "接收者已补充自己的笔记")) }
        receive(android.content.Intent.ACTION_VIEW)
        compose.onNodeWithTag("memo-import-confirm").assertIsNotEnabled()
        Assert.assertEquals("接收者已补充自己的笔记", runBlocking { repository.notesFlow.first().single { it.id == imported.id }.body })
        Assert.assertEquals(1, runBlocking { repository.notesFlow.first().count { it.sharedOriginId == incoming.id } })
        screen("memo-import-duplicate")
    }

    @Test fun announcementUsesSelectedAppLanguage() {
        Assume.assumeTrue(enabled())
        val preview = runBlocking { com.x500x.cursimple.app.update.loadLocalReleasePreview(compose.activity) }!!
        val expected = when (language) {
            AppLanguage.English -> "Share course notes"
            AppLanguage.TraditionalChinese -> "筆記分享與匯入"
            else -> "笔记分享与导入"
        }
        Assert.assertTrue(preview.markdown.contains(expected))
        compose.runOnUiThread { compose.activity.setContent {
            com.x500x.cursimple.app.theme.ClassScheduleTheme(
                if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                ReleaseAnnouncementDialog(preview.versionName, preview.markdown,
                    com.x500x.cursimple.app.update.rememberReleaseImageLoader(preview.localDir, preview.localAssetDir)) {}
            }
        } }
        compose.onNodeWithText(expected).assertIsDisplayed()
        screen("announcement-${args.getString("qaLanguage") ?: "zh-CN"}")
    }

    @Test fun importReminderAndPlaceholderLayouts() {
        Assume.assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_add_to_schedule)).performClick()
        compose.onNodeWithText(activity.getString(R.string.main_import_export)).performClick()
        compose.onNodeWithText(activity.getString(R.string.ie_screen_title)).assertIsDisplayed()
        screen("import-export-top")
        compose.onNodeWithText(activity.getString(R.string.ie_calendar_row_title)).performScrollTo().assertIsDisplayed()
        screen("import-export-bottom")
        compose.onNodeWithText(activity.getString(R.string.ie_calendar_row_title)).performClick()
        screen("calendar-export")
        compose.onNodeWithText(activity.getString(R.string.ie_close)).performClick()
        compose.onNodeWithContentDescription(activity.getString(R.string.ie_back)).performClick()
        drawer(R.string.screen_reminders)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(activity.getString(R.string.reminders_tab_alarms))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) }
        }
        screen("alarms-top")
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_more_title)).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_placeholder_card_title)).performScrollTo().assertIsDisplayed()
        screen("alarms-more")
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_placeholder_add_title)).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_placeholder_days_label)).assertIsDisplayed()
        compose.onNodeWithText("07:50").performScrollTo()
        screen("placeholder-days")
        for (time in listOf("07:10", "07:50")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(time, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            Assert.assertTrue(layouts.isNotEmpty())
            Assert.assertEquals(1, layouts.single().lineCount)
            val result = layouts.single()
            Assert.assertFalse(result.isLineEllipsized(0))
            Assert.assertTrue("$time is clipped horizontally", result.getLineRight(0) <= result.size.width + 1f)
            Assert.assertTrue("$time is clipped vertically", result.getLineBottom(0) <= result.size.height + 1f)
        }
        compose.onNodeWithText("07:50").performScrollTo()
        screen("placeholder-days")
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_placeholder_more)).performScrollTo().performClick()
        screen("placeholder-more")
        compose.onNodeWithText(activity.getString(ScheduleR.string.schedule_action_cancel)).performClick()
        compose.onNode(hasText(activity.getString(R.string.reminders_tab_notice)) and isNotSelected()).performClick()
        screen("reminders-top")
        compose.onNodeWithText(activity.getString(R.string.settings_class_notice_advanced_title)).performScrollTo().performClick()
        screen("reminders-advanced")
    }
}
