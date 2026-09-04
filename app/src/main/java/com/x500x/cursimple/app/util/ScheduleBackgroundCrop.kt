package com.x500x.cursimple.app.util

/** 裁切时取用的原图区域，单位是原图像素。 */
data class CropSourceRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 算出按课表比例裁切时该取原图的哪一块。
 *
 * 先在原图里取一块符合 [frameAspect] 且尽可能大的区域，再按 [zoom] 收缩，
 * 最后按 [offsetXFraction] / [offsetYFraction] 在剩余空间里平移。
 * 偏移取 -1 到 1，0 是居中，超出范围会被夹回，保证裁切框始终落在原图内。
 */
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
    // 先取满足目标比例的最大区域：原图更宽就以高为准，更高就以宽为准
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
