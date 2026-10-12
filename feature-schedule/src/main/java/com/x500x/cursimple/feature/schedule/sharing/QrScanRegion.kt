package com.x500x.cursimple.feature.schedule.sharing

import kotlin.math.ceil
import kotlin.math.floor

/** Fractional bounds in the displayed, upright camera viewport. */
data class QrScanRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class QrPixelRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** CameraX's shared ViewPort supplies the sensor crop corresponding to PreviewView.FILL_CENTER.
 * Undo its clockwise display rotation before cropping the packed Y plane. */
fun QrScanRegion.inCameraCrop(crop: QrPixelRegion, rotationDegrees: Int): QrPixelRegion? {
    if (crop.width <= 0 || crop.height <= 0 ||
        listOf(left, top, right, bottom).any { !it.isFinite() } || left >= right || top >= bottom
    ) return null
    val l = left.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    val b = bottom.coerceIn(0f, 1f)
    val native = when (((rotationDegrees % 360) + 360) % 360) {
        0 -> QrScanRegion(l, t, r, b)
        90 -> QrScanRegion(t, 1f - r, b, 1f - l)
        180 -> QrScanRegion(1f - r, 1f - b, 1f - l, 1f - t)
        270 -> QrScanRegion(1f - b, l, 1f - t, r)
        else -> return null
    }
    // Round inward: no camera pixels outside the visible frame participate in decoding.
    return QrPixelRegion(
        crop.left + ceil(native.left * crop.width).toInt(),
        crop.top + ceil(native.top * crop.height).toInt(),
        crop.left + floor(native.right * crop.width).toInt(),
        crop.top + floor(native.bottom * crop.height).toInt(),
    ).takeIf { it.width > 0 && it.height > 0 }
}
