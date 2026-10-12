package com.x500x.cursimple.feature.plugin.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal data class PdfViewportTransform(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {
    fun bounded(viewport: Size, page: Size): PdfViewportTransform {
        if (viewport.width <= 0f || viewport.height <= 0f || page.width <= 0f || page.height <= 0f) return PdfViewportTransform()
        val fit = min(viewport.width / page.width, viewport.height / page.height)
        val x = ((page.width * fit * zoom - viewport.width) / 2f).coerceAtLeast(0f)
        val y = ((page.height * fit * zoom - viewport.height) / 2f).coerceAtLeast(0f)
        return copy(pan = Offset(if (x == 0f) 0f else pan.x.coerceIn(-x, x), if (y == 0f) 0f else pan.y.coerceIn(-y, y)))
    }

    /** Keep the page point under the gesture centroid stationary while scaling. */
    fun transform(centroid: Offset, delta: Offset, factor: Float, viewport: Size, page: Size): PdfViewportTransform {
        if (!factor.isFinite() || factor <= 0f) return this
        val next = (zoom * factor).coerceIn(1f, 4f)
        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        return PdfViewportTransform(next, focus + (pan - focus) * (next / zoom) + delta).bounded(viewport, page)
    }
}
