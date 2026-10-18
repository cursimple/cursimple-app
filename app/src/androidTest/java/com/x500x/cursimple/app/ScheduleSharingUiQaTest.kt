package com.x500x.cursimple.app

import com.x500x.cursimple.feature.schedule.sharing.QrCodeCodec

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.ai.*
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.util.*
import com.x500x.cursimple.app.webdav.*
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.GZIPOutputStream

/** Explicit, isolated QA for real exported files, import confirmation and responsive layouts. */
class ScheduleSharingUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("scheduleSharingQa") == "true"
    private val language get() = when (args.getString("qaLanguage")) {
        "en" -> AppLanguage.English
        "zh-TW" -> AppLanguage.TraditionalChinese
        else -> AppLanguage.Chinese
    }
    private val start = LocalDate.of(2026, 10, 5)
    private val courses = listOf(
        CourseItem("sharing-math", "高等数学", teacher = "王老师", location = "教学楼 201", weeks = listOf(1, 2, 3),
            time = CourseTimeSlot(2, 1, 2), weekTeachers = mapOf(2 to "李老师"), weekLocations = mapOf(2 to "实验楼 302")),
        CourseItem("sharing-physics", "大学物理实验", teacher = "陈老师", location = "实验楼 108", weeks = listOf(1, 2, 3), time = CourseTimeSlot(5, 3, 4)),
        CourseItem("sharing-english", "大学英语", teacher = "刘老师", location = "教学楼 305", weeks = listOf(1, 2, 3), time = CourseTimeSlot(3, 5, 6)),
    )
    private val payload get() = ScheduleSharePayload(termName = "2026 秋季", termStartDate = start.toString(), manualCourses = courses)
    private val timing = TermTimingProfile(start.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:40", "第1节"), ClassSlotTime(3, 4, "10:10", "11:50", "第2节"),
        ClassSlotTime(5, 6, "14:00", "15:40", "第3节"), ClassSlotTime(7, 8, "16:00", "17:40", "第4节"),
    ))
    private var applied: ScheduleSharePayload? = null
    private var appliedStart: LocalDate? = null
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            instrumentation.runOnMainSync { android.graphics.HardwareRenderer.setDrawingEnabled(true) }
            runBlocking { DataStoreUserPreferencesRepository(context).apply {
                setDisclaimerAccepted(true); setFirstRunGuideCompleted(true); setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                markIslandStartupPromptShown(); markNotificationPermissionStartupAsked(); setTermStartUserDecided(true)
                setAppLanguage(language)
            } }
            AppLocale.cache(context, language)
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
            BeijingTime.setForcedNow(LocalDateTime.of(2026, 10, 10, 12, 0))
        }
        override fun after() { if (enabled()) BeijingTime.setForcedNow(null) }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)
    private fun text(res: Int) = compose.activity.getString(res)
    private fun show(timingProfile: TermTimingProfile? = timing) {
        compose.activity.setContent {
            ClassScheduleTheme(themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    ImportExportScreen(schedule = null, manualCourses = courses, termName = payload.termName, termStartDate = start,
                        timingProfile = timingProfile, temporaryScheduleOverrides = emptyList(),
                        webDavConfig = WebDavConfig("", "", ""), webDavClient = WebDavClient(),
                        aiImportConfig = AiImportConfig("", "", ""), aiImportClient = AiScheduleImportClient(),
                        onApplyImport = { schedule, manual, term, callback ->
                            applied = ScheduleSharePayload(termName = term, schedule = schedule, manualCourses = manual)
                            callback(Result.success(0 to manual.size))
                        }, onApplyTermStartDate = { appliedStart = it },
                        onCreateAppBackup = { error("Not used") }, onRestoreAppBackup = {},
                        onOpenWebDavSettings = {}, onOpenAiImportSettings = {}, onBack = {})
                }
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(context.filesDir, "schedule-sharing/${args.getString("qaVariant") ?: "normal"}/$name.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }
    private fun waitTag(tag: String) {
        try { compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        } } catch (error: Exception) {
            capture("timeout-$tag")
            compose.onRoot(useUnmergedTree = true).printToLog("ScheduleSharingQA")
            throw error
        }
    }
    private fun row(res: Int) = compose.onNode(hasText(text(res)) and hasClickAction()).performScrollTo().performClick()
    private fun singleLine(res: Int) {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(text(res), useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertTrue("Missing layout for ${text(res)}", layouts.isNotEmpty())
        Assert.assertEquals("Wrapped ${text(res)}", 1, layouts.single().lineCount)
        Assert.assertFalse("Clipped ${text(res)}", layouts.single().hasVisualOverflow)
    }

    @Test fun codeCopyPasteAndConfirmPreservesCourses() {
        Assume.assumeTrue(enabled())
        show()
        capture("import-export")
        singleLine(R.string.ie_screen_title)
        singleLine(R.string.ie_scan)
        singleLine(R.string.ie_pick_from_gallery)
        row(R.string.ie_text_share_title)
        waitTag("schedule-text-copy")
        capture("share-code")
        singleLine(R.string.ie_text_copy)
        compose.onNodeWithTag("schedule-text-copy").performScrollTo().performClick()
        var copied = ""
        compose.runOnIdle { copied = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip!!.getItemAt(0).coerceToText(context).toString() }
        Assert.assertEquals(payload, ScheduleShareCodec.decode(copied).getOrThrow())
        compose.onNodeWithText(text(R.string.ie_close)).performClick()
        row(R.string.ie_text_import_title)
        compose.onNodeWithTag("schedule-text-input").performTextReplacement("CSV1:bad")
        compose.onNodeWithTag("schedule-text-import").performClick()
        waitTag("schedule-text-error")
        Assert.assertNull(applied)
        capture("import-code-invalid")
        compose.onNodeWithTag("schedule-text-paste").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-text-import").performClick()
        compose.onNodeWithText(text(R.string.ie_import_confirm_title)).assertIsDisplayed()
        Assert.assertNull(applied)
        capture("import-code-confirm")
        compose.onNode(hasText(text(R.string.ie_import_confirm_action)) and hasClickAction()).performClick()
        compose.runOnIdle {
            Assert.assertEquals(courses, applied?.manualCourses)
            Assert.assertEquals(payload.termName, applied?.termName)
            Assert.assertEquals(start, appliedStart)
        }
    }

    @Test fun savedQrIsReadableAndMatchesShareCharacters() {
        Assume.assumeTrue(enabled())
        show()
        row(R.string.ie_qr_export_title)
        waitTag("schedule-qr-image")
        capture("share-qr")
        singleLine(R.string.ie_save_image)
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        fun ownImages(): List<Pair<Long, String>> = context.contentResolver.query(collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("cursimple-qr-%"), null)!!.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1)) }
        }
        val before = ownImages().map { it.first }.toSet()
        compose.onNodeWithTag("schedule-qr-save").performScrollTo().performClick()
        var saved: Long? = null
        compose.waitUntil(10_000) { saved = ownImages().firstOrNull { it.first !in before }?.first; saved != null }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("schedule-qr-save") and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.waitForIdle()
        val uri = android.content.ContentUris.withAppendedId(collection, saved!!)
        try {
            val bitmap = checkNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) })
            try { Assert.assertEquals(payload, ScheduleShareCodec.decode(QrCodeCodec.decodeBitmap(bitmap)!!).getOrThrow()) }
            finally { bitmap.recycle() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun weekThumbnailPreviewAndShareUseSameFile() {
        Assume.assumeTrue(enabled())
        show()
        compose.onNodeWithText(text(R.string.ie_image_panel_title)).performScrollTo()
        singleLine(R.string.ie_image_panel_title)
        row(R.string.ie_image_panel_title)
        waitTag("schedule-image-thumbnail")
        capture("image-all-weeks")
        compose.onNodeWithText(text(R.string.ie_image_scope_single_week)).performClick()
        waitTag("schedule-image-thumbnail")
        compose.onNodeWithContentDescription(text(R.string.ie_next_week)).performClick()
        waitTag("schedule-image-thumbnail")
        compose.onNodeWithText(compose.activity.getString(R.string.ie_week_label, 2)).assertIsDisplayed()
        capture("image-single-week")
        singleLine(R.string.ie_share_image)
        compose.onNodeWithTag("schedule-image-thumbnail").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-image-preview").assertIsDisplayed()
        capture("image-preview")
        compose.onNodeWithContentDescription(text(R.string.ie_share_image)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("schedule-image-preview").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        capture("image-system-share")
        val newest = File(context.cacheDir, "images").listFiles()!!.filter { it.name.contains("week2") }.maxBy { it.lastModified() }
        val pixels = BitmapFactory.decodeFile(newest.path)
        Assert.assertTrue(pixels.width > 500 && pixels.height > 500)
        pixels.recycle()
        // Restore the app to the foreground before the next ActivityScenario test.
        instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

    @Test fun previewFailureAndScannerLayoutRemainUsable() {
        Assume.assumeTrue(enabled())
        show(timingProfile = null)
        row(R.string.ie_image_panel_title)
        waitTag("schedule-image-error")
        compose.onNodeWithTag("schedule-image-share").assertIsNotEnabled()
        capture("image-preview-failure")
        compose.onNodeWithText(text(R.string.ie_retry)).performClick()
        waitTag("schedule-image-error")
        compose.onNodeWithText(text(R.string.ie_cancel)).performClick()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA").close()
        row(R.string.ie_scan)
        compose.onNodeWithTag("schedule-scan-frame").assertIsDisplayed()
        val frame = compose.onNodeWithTag("schedule-scan-frame").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithTag("schedule-scan-hint").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(hint.top > frame.bottom)
        Assert.assertEquals(frame.width, frame.height, 1f)
        capture("scan-frame")
        compose.onNodeWithContentDescription(text(R.string.ie_back)).performClick()
        compose.onNodeWithText(text(R.string.ie_screen_title)).assertIsDisplayed()
    }

    @Test fun previewSharesThePreparedFileWithoutAnotherRender() {
        Assume.assumeTrue(enabled())
        val week = androidx.compose.runtime.mutableIntStateOf(1)
        val generated = java.util.concurrent.ConcurrentHashMap<Int, android.content.Intent>()
        var shared: android.content.Intent? = null
        compose.activity.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                val selectedWeek = week.intValue
                ScheduleImageDialog(weekNumber = selectedWeek, maxWeekNumber = 3, allWeeks = false,
                    generationKey = courses,
                    generateImage = { _, _ ->
                        ScheduleImageExporter.export(context = compose.activity, termName = payload.termName,
                            termStartDate = start, weekNumber = selectedWeek, schedule = null, manualCourses = courses,
                            timingProfile = timing, overrides = emptyList(), holidayCalendar = HolidayCalendarSettings()).also {
                            generated[selectedWeek] = checkNotNull(it.intent)
                        }
                    }, onAllWeeksChange = {}, onWeekChange = { week.intValue = it },
                    onShare = { shared = it }, onDismiss = {})
            }
        }
        waitTag("schedule-image-thumbnail")
        compose.onNodeWithContentDescription(text(R.string.ie_next_week)).performClick()
        waitTag("schedule-image-thumbnail")
        compose.onNodeWithTag("schedule-image-thumbnail").performClick()
        compose.onNodeWithContentDescription(text(R.string.ie_share_image)).performClick()
        compose.runOnIdle {
            Assert.assertSame(generated[2], shared)
            Assert.assertEquals(2, generated.size)
            @Suppress("DEPRECATION")
            val first = generated[1]!!.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            @Suppress("DEPRECATION")
            val second = shared!!.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            Assert.assertNotEquals(first, second)
        }
    }

    @Test fun codecHandlesWrappingRejectsBadVersionsAndBoundsDecompression() {
        Assume.assumeTrue(enabled())
        val encoded = ScheduleShareCodec.encode(payload)
        Assert.assertEquals(payload, ScheduleShareCodec.decode(encoded.chunked(37).joinToString("\n ")).getOrThrow())
        Assert.assertTrue(ScheduleShareCodec.decode("not a timetable").isFailure)
        Assert.assertTrue(ScheduleShareCodec.decode(ScheduleShareCodec.encode(payload.copy(v = 100))).isFailure)
        val gzip = ByteArrayOutputStream().also { sink -> GZIPOutputStream(sink).use { gz ->
            repeat(513) { gz.write(ByteArray(8192) { 65 }) }
        } }.toByteArray()
        val bomb = "CSV1:" + android.util.Base64.encodeToString(gzip, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        Assert.assertEquals(ScheduleShareDecodeReason.TooLarge, (ScheduleShareCodec.decode(bomb).exceptionOrNull() as ScheduleShareDecodeException).reason)
        val image = QrCodeCodec.encodeToBitmap(encoded)
        try { Assert.assertEquals(encoded, QrCodeCodec.decodeBitmap(image)) } finally { image.recycle() }
    }
}
