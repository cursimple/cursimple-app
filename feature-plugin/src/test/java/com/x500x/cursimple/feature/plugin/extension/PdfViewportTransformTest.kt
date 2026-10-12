package com.x500x.cursimple.feature.plugin.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class PdfViewportTransformTest {
    @Test fun scalingKeepsTheTouchedPointUnderTheSameFinger() {
        val viewport = Size(400f, 600f)
        val focus = Offset(260f, 350f)
        val transformed = PdfViewportTransform().transform(focus, Offset.Zero, 2f, viewport, viewport)
        assertEquals(2f, transformed.zoom, .001f)
        assertEquals(Offset(-60f, -50f), transformed.pan)
        assertEquals(focus, Offset(200f, 300f) + (focus - Offset(200f, 300f)) * transformed.zoom + transformed.pan)
    }
    @Test fun draggingUsesTheRenderedPageBoundsInsteadOfEmptyMargins() {
        val transformed = PdfViewportTransform().transform(Offset(200f, 300f), Offset(1000f, -1000f), 2f, Size(400f, 600f), Size(1000f, 250f))
        assertEquals(Offset(200f, 0f), transformed.pan)
    }
    @Test fun zoomLimitsAndReturningToFitKeepThePageVisible() {
        val viewport = Size(400f, 600f)
        val enlarged = PdfViewportTransform().transform(Offset(0f, 0f), Offset.Zero, 20f, viewport, viewport)
        assertEquals(4f, enlarged.zoom, .001f)
        val reset = enlarged.transform(Offset(300f, 200f), Offset(900f, 800f), .01f, viewport, viewport)
        assertEquals(PdfViewportTransform(), reset)
    }
}
