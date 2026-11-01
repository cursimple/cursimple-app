package com.x500x.cursimple.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertTextEquals
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in (`-e memoImageQa true`): real Notes screen, one throw-away note and one throw-away image file. */
class MemoImageAndCopyUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val imageId = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").take(64)
    private val note = MemoNote(
        "qa-memo-image-${UUID.randomUUID()}", title = "QA Memo 图片",
        body = "${MemoImageRef.markdown(imageId, "sample")}\n\nHello **world**\n- [ ] item one",
    )
    private fun enabled() = InstrumentationRegistry.getArguments().getString("memoImageQa") == "true"
    private val imageFile get() = File(context.filesDir, "memo-images/$imageId.img")
    // A second note with two images, for the swipeable preview.
    private val galleryIds = List(2) { (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").take(64) }
    private val galleryNote = MemoNote(
        "qa-memo-gallery-${UUID.randomUUID()}", title = "QA Memo 双图",
        body = galleryIds.mapIndexed { i, id -> MemoImageRef.markdown(id, "pic${i + 1}") }.joinToString("\n\n"),
    )

    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            imageFile.parentFile!!.mkdirs()
            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.rgb(230, 126, 34))
            imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            galleryIds.forEachIndexed { index, id ->
                val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(if (index == 0) Color.rgb(52, 152, 219) else Color.rgb(46, 204, 113))
                File(context.filesDir, "memo-images/$id.img").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                }
                DataStoreMemoRepository(context).upsert(note)
                DataStoreMemoRepository(context).upsert(galleryNote)
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun cleanUp() {
        if (!enabled()) return
        runBlocking {
            DataStoreMemoRepository(context).remove(note.id)
            DataStoreMemoRepository(context).remove(galleryNote.id)
        }
        imageFile.delete()
        galleryIds.forEach { File(context.filesDir, "memo-images/$it.img").delete() }
        context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("cursimple-note-%"))
    }

    private fun galleryCount() = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("cursimple-note-%"), null)
        ?.use { it.count } ?: 0

    private fun clipText(): String? {
        var text: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            text = clip?.getItemAt(0)?.text?.toString()
        }
        return text
    }

    private fun clipUri(): String? {
        var uri: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            uri = clip?.getItemAt(0)?.uri?.toString()
        }
        return uri
    }

    private fun openNote(target: MemoNote = note) {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_memos)).performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_stat_todo)).performClick()
        compose.onNodeWithText(target.title).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(ScheduleR.string.memo_editor_view)).assertIsDisplayed()
    }

    @Test fun longPressingAnImageOffersShareSaveAndCopy() {
        assumeTrue(enabled())
        openNote()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("memo-image-thumbnail") > 0 }
        compose.onNodeWithTag("memo-image-thumbnail").assertIsDisplayed().performTouchInput { longClick() }
        compose.onNodeWithTag("memo-image-share").assertIsDisplayed()
        compose.onNodeWithTag("memo-image-save").assertIsDisplayed()
        compose.onNodeWithTag("memo-image-copy").assertIsDisplayed()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "memo-qa/image-menu.png"))

        val before = galleryCount()
        compose.onNodeWithTag("memo-image-save").performClick()
        compose.waitUntil(5_000) { galleryCount() == before + 1 }

        compose.onNodeWithTag("memo-image-thumbnail").performTouchInput { longClick() }
        compose.onNodeWithTag("memo-image-copy").performClick()
        compose.waitUntil(5_000) { clipUri() != null }
        assertTrue("the clip points at a served image: ${clipUri()}", clipUri()!!.contains("fileprovider"))
    }

    @Test fun theFullScreenPreviewHasTheSameActionsInTheTopRight() {
        assumeTrue(enabled())
        openNote()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("memo-image-thumbnail") > 0 }
        compose.onNodeWithTag("memo-image-thumbnail").performClick()
        compose.onNodeWithTag("memo-image-viewer").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("memo-image-viewer-share") > 0 }
        compose.onNodeWithTag("memo-image-viewer-copy").assertIsDisplayed()
        compose.onNodeWithTag("memo-image-viewer-share").assertIsDisplayed()
        compose.onNodeWithTag("memo-image-viewer-save").assertIsDisplayed()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "memo-qa/viewer.png"))

        val before = galleryCount()
        compose.onNodeWithTag("memo-image-viewer-save").performClick()
        compose.waitUntil(5_000) { galleryCount() == before + 1 }

        compose.onNodeWithTag("memo-image-viewer-share").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var chooser = false
        val deadline = System.currentTimeMillis() + 6_000
        while (!chooser && System.currentTimeMillis() < deadline) {
            val root: AccessibilityNodeInfo? = automation.rootInActiveWindow
            chooser = root?.packageName?.toString()?.let { it != context.packageName } == true
            if (!chooser) Thread.sleep(250)
        }
        assertTrue("the system share sheet opened over the viewer", chooser)
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test fun readModeCopiesTheNoteAsPlainTextOrMarkdown() {
        assumeTrue(enabled())
        openNote()
        compose.onNodeWithTag("memo-copy-note").assertIsDisplayed().performClick()
        compose.onNodeWithTag("memo-copy-markdown").assertIsDisplayed()
        compose.onNodeWithTag("memo-copy-plain").assertIsDisplayed()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "memo-qa/copy-menu.png"))

        compose.onNodeWithTag("memo-copy-markdown").performClick()
        Thread.sleep(800)
        val markdown = clipText()
        assertTrue("markdown copied, clipboard was: $markdown", markdown?.contains("Hello **world**") == true)
        assertTrue("title heading kept: $markdown", markdown!!.startsWith("# QA Memo 图片"))

        compose.onNodeWithTag("memo-copy-note").performClick()
        compose.onNodeWithTag("memo-copy-plain").performClick()
        Thread.sleep(800)
        val plain = clipText()
        assertTrue("plain text copied, clipboard was: $plain", plain?.contains("Hello world") == true)
        assertFalse("no markdown markers in plain text", plain!!.contains("**"))
        assertTrue(plain.contains("item one"))
    }

    @Test fun theFullScreenPreviewSwipesBetweenTheNotesImages() {
        assumeTrue(enabled())
        openNote(galleryNote)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("memo-image-thumbnail") >= 2 }
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag("memo-image-thumbnail"))[1].performClick()
        compose.onNodeWithTag("memo-image-viewer").assertIsDisplayed()
        // Opened on the second image, so the counter says 2 / 2 and a swipe back reaches the first.
        compose.onNodeWithTag("memo-image-counter").assertTextEquals("2 / 2")
        compose.onNodeWithTag("memo-image-viewer").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("memo-image-counter").assertTextEquals("1 / 2") }.isSuccess }
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "memo-qa/gallery.png"))
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
