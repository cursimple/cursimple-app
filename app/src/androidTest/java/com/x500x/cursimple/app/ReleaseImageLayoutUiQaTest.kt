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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
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
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
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
        val bitmap = ScreenshotCapture.take()
        ScreenshotCapture.save(bitmap, File(context.filesDir, "release-image-qa/tall.png"))
    }
}
