package com.x500x.cursimple.feature.plugin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.extension.ExtensionPdfPreview
import com.x500x.cursimple.feature.plugin.extension.renderExtensionPdfPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExtensionPdfPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var file: File

    @Before fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pdfPreviewQa") == "true")
        file = File(context.cacheDir, "course-notice.pdf")
        instrumentation.context.assets.open("course-notice.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    @Test fun realPdfPagesRenderWithBoundedMemory() = runBlocking {
        val first = renderExtensionPdfPage(file, 0, 1200)
        val second = renderExtensionPdfPage(file, 1, 1200)
        assertEquals(2, first.pageCount)
        assertFalse(first.bitmap.sameAs(second.bitmap))
        assertTrue(first.bitmap.allocationByteCount <= 16_000_000)
        assertEquals(android.graphics.Color.WHITE, first.bitmap.getPixel(first.bitmap.width / 2, first.bitmap.height * 3 / 4))
        assertNotEquals(android.graphics.Color.WHITE, first.bitmap.getPixel(first.bitmap.width / 2, first.bitmap.height / 20))
        first.bitmap.recycle(); second.bitmap.recycle()
        val bounded = renderExtensionPdfPage(file, 0, 20_000)
        assertTrue(bounded.bitmap.allocationByteCount <= 16_000_000)
        bounded.bitmap.recycle()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun pagesZoomAndCloseWithoutExternalReader() {
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) ExtensionPdfPreview(file, onDismiss = { visible.value = false }) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf-page-0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_next)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf-page-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_next)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_zoom_in)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_zoom_out)).assertIsEnabled().performClick()
        compose.waitForIdle()
        lateinit var screenshot: android.graphics.Bitmap
        instrumentation.runOnMainSync {
            val root = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.isAttachedToWindow && it.width > 0 }
            screenshot = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(screenshot))
        }
        val colors = mutableSetOf<Int>()
        for (x in 0 until screenshot.width step 20) for (y in 0 until screenshot.height step 20) colors += screenshot.getPixel(x, y)
        assertTrue("PDF reader capture is blank", colors.size > 10)
        File(context.filesDir, "pdf-native-preview.png").outputStream().use { output ->
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        screenshot.recycle()
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_close)).performClick()
        compose.onAllNodesWithTag("pdf-page-1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun twoFingersZoomTogetherAndDoubleTapResets() {
        compose.setContent { MaterialTheme { ExtensionPdfPreview(file, onDismiss = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf-page-0").fetchSemanticsNodes().isNotEmpty() }
        val viewport = compose.onNodeWithTag("pdf-viewport")
        fun percent() = Regex("[0-9]+").find(viewport.fetchSemanticsNode().config[SemanticsProperties.StateDescription])!!.value.toInt()
        assertEquals(100, percent())
        viewport.performTouchInput {
            down(0, center + Offset(-30f, 0f))
            moveTo(0, center + Offset(-32f, 0f))
            down(1, center + Offset(30f, 0f))
            for (step in 1..12) {
                updatePointerTo(0, center + Offset(-32f-step*6f, 0f))
                updatePointerTo(1, center + Offset(30f+step*6f, 0f))
                move(16)
            }
            up(0); up(1)
        }
        assertTrue("Two-finger gesture did not zoom", percent() in 200..400)
        val zoomed = percent()
        viewport.performTouchInput {
            down(0, center + Offset(-60f, 0f)); down(1, center + Offset(60f, 0f))
            for (step in 1..8) {
                updatePointerTo(0, center + Offset(-60f+step*4f, -step*5f))
                updatePointerTo(1, center + Offset(60f+step*4f, -step*5f))
                move(16)
            }
            up(0); up(1)
        }
        assertTrue(kotlin.math.abs(percent()-zoomed) <= 1)
        viewport.performTouchInput { doubleClick(center) }
        assertEquals(100, percent())
        compose.onNodeWithContentDescription(context.getString(R.string.extension_pdf_next)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf-page-1").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(100, percent())
    }

    @Test fun invalidPdfShowsAnErrorInsteadOfPretendingSuccess() {
        file.writeText("Not a PDF")
        compose.setContent { MaterialTheme { ExtensionPdfPreview(file, onDismiss = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(context.getString(R.string.extension_pdf_error)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.extension_pdf_retry)).assertIsDisplayed()
        compose.onAllNodesWithTag("pdf-page-0", useUnmergedTree = true).assertCountEquals(0)
    }
}
