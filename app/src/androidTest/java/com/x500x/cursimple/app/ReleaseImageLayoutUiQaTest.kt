package com.x500x.cursimple.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.update.AppUpdateAsset
import com.x500x.cursimple.app.update.AppUpdateInfo
import com.x500x.cursimple.app.update.ReleaseNoteImage
import com.x500x.cursimple.app.update.releaseImageLoader
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in check (`-e releaseImageQa true`) that tall release-note screenshots stay between their paragraphs. */
class ReleaseImageLayoutUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun tallImageStaysBetweenItsParagraphs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseImageQa") == "true")
        val dir = File(context.cacheDir, "release-image-qa").apply { mkdirs() }
        // A phone screenshot: far taller than the 320dp cap allows at full width.
        val shot = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply { Canvas(this).drawColor(0xFF3C8D6A.toInt()) }
        File(dir, "tall.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val loader = releaseImageLoader(context, localDir = dir)
        val viewportHeight = mutableIntStateOf(1000)
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                Column(Modifier.heightIn(max = viewportHeight.intValue.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Text("图片上方的说明文字")
                    ReleaseImage(ReleaseNoteImage("https://example.invalid/tall.png", "竖屏截图"), loader,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
                    Text("图片下方的说明文字")
                }
            }
        }
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithContentDescription("竖屏截图").fetchSemanticsNode() }.isSuccess
        }
        val image = compose.onNodeWithContentDescription("竖屏截图").fetchSemanticsNode().boundsInRoot
        val above = compose.onNodeWithText("图片上方的说明文字").fetchSemanticsNode().boundsInRoot
        val below = compose.onNodeWithText("图片下方的说明文字").fetchSemanticsNode().boundsInRoot
        assertTrue("image overlaps the text above: $image / $above", image.top >= above.bottom - 1f)
        assertTrue("image overlaps the text below: $image / $below", image.bottom <= below.top + 1f)
        assertTrue("image must keep its 320dp cap: $image", image.height <= 320 * context.resources.displayMetrics.density + 1f)

        // In the update dialog the notes viewport is shorter than a single tall image.
        compose.runOnIdle { viewportHeight.intValue = 220 }
        compose.onNodeWithText("图片下方的说明文字").performScrollTo().assertIsDisplayed()
        val scrolledImage = compose.onNodeWithContentDescription("竖屏截图").fetchSemanticsNode().boundsInRoot
        val scrolledBelow = compose.onNodeWithText("图片下方的说明文字").fetchSemanticsNode().boundsInRoot
        assertTrue("image overlaps text after scrolling: $scrolledImage / $scrolledBelow", scrolledImage.bottom <= scrolledBelow.top + 1f)
        val bitmap = ScreenshotCapture.take()
        ScreenshotCapture.save(bitmap, File(context.filesDir, "release-image-qa/tall.png"))
    }

    @Test fun realReleaseNotesStayInsideTheUpdateDialog() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("releaseDialogQa") == "true")
        val markdown = File(context.cacheDir, "release-dialog-qa/v0.8.2.md").readText()
        val visible = mutableStateOf(true)
        compose.setContent {
            ClassScheduleTheme(
                themeMode = if (args.getString("releaseDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("releaseDark") == "true") ThemeAccent.Orange else ThemeAccent.Green,
            ) {
                if (visible.value) UpdateAvailableDialog(
                    info = AppUpdateInfo(43, "0.8.2", "v0.8.2", "", markdown,
                        AppUpdateAsset("", "", "", ""), emptyList()),
                    downloading = false,
                    downloadedApk = null,
                    onUpdate = {},
                    onIgnore = {},
                    onDismiss = { visible.value = false },
                )
            }
        }
        val image = compose.onNodeWithContentDescription("从教务系统导课的学校列表")
        compose.waitUntil(10_000) { runCatching { image.fetchSemanticsNode() }.isSuccess }
        image.performScrollTo().assertIsDisplayed()
        val update = compose.onNodeWithText(context.getString(R.string.update_dialog_update)).assertIsDisplayed()
        val updateBeforeScroll = update.fetchSemanticsNode().boundsInRoot
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "release-image-qa/dialog-image.png"))

        val heading = compose.onNodeWithText("插件主页：成绩、培养计划、学分一处查看")
        heading.performScrollTo().assertIsDisplayed()
        assertTrue("image overlaps the next section", image.fetchSemanticsNode().boundsInRoot.bottom <=
            heading.fetchSemanticsNode().boundsInRoot.top + 1f)
        assertTrue("scrolling the notes moves the update button",
            update.fetchSemanticsNode().boundsInRoot == updateBeforeScroll)
        val dismiss = compose.onNodeWithText(context.getString(R.string.update_dialog_later)).assertIsDisplayed()
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "release-image-qa/dialog-scrolled.png"))
        dismiss.performClick()
        dismiss.assertDoesNotExist()
    }
}
