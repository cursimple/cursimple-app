package com.x500x.cursimple.app.util

data class CropSourceRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** Compute an in-bounds [frameAspect] crop with [zoom] and clamped fractional offsets. */
fun cropSourceRect(
    imageWidth: Int,
    imageHeight: Int,
    frameAspect: Float,
    zoom: Float = 1f,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f,
): CropSourceRect? {
    if (imageWidth <= 0 || imageHeight <= 0 || frameAspect <= 0f || !frameAspect.isFinite()) return null
    val safeZoom = zoom.coerceAtLeast(1f)
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val baseWidth: Float
    val baseHeight: Float
    if (imageAspect > frameAspect) {
        baseHeight = imageHeight.toFloat()
        baseWidth = baseHeight * frameAspect
    } else {
        baseWidth = imageWidth.toFloat()
        baseHeight = baseWidth / frameAspect
    }
    val width = (baseWidth / safeZoom).coerceAtLeast(1f)
    val height = (baseHeight / safeZoom).coerceAtLeast(1f)
    val slackX = (imageWidth - width).coerceAtLeast(0f)
    val slackY = (imageHeight - height).coerceAtLeast(0f)
    val centerLeft = slackX / 2f
    val centerTop = slackY / 2f
    val left = (centerLeft + offsetXFraction.coerceIn(-1f, 1f) * centerLeft).coerceIn(0f, slackX)
    val top = (centerTop + offsetYFraction.coerceIn(-1f, 1f) * centerTop).coerceIn(0f, slackY)
    return CropSourceRect(
        left = left.toInt(),
        top = top.toInt(),
        width = width.toInt().coerceAtMost(imageWidth - left.toInt()).coerceAtLeast(1),
        height = height.toInt().coerceAtMost(imageHeight - top.toInt()).coerceAtLeast(1),
    )
}

data class CropPanBounds(val maxX: Float, val maxY: Float)

/** Preview pan limits use the same fill and zoom geometry as [cropSourceRect]. */
fun cropPanBounds(
    frameWidth: Float,
    frameHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
): CropPanBounds {
    if (frameWidth <= 0f || frameHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return CropPanBounds(0f, 0f)
    }
    val coverScale = maxOf(frameWidth / imageWidth, frameHeight / imageHeight)
    val displayWidth = imageWidth * coverScale * zoom.coerceAtLeast(1f)
    val displayHeight = imageHeight * coverScale * zoom.coerceAtLeast(1f)
    return CropPanBounds(
        maxX = ((displayWidth - frameWidth) / 2f).coerceAtLeast(0f),
        maxY = ((displayHeight - frameHeight) / 2f).coerceAtLeast(0f),
    )
}

/**
 * Image translation has the opposite sign to source-crop displacement; zero overflow stays
 * centered.
 */
fun cropOffsetFraction(translation: Float, maxPan: Float): Float =
    if (maxPan <= 0f) 0f else (-translation / maxPan).coerceIn(-1f, 1f)
