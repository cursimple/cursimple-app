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

/** 预览框里图片可平移的像素余量，超出这个范围就会露出空白。 */
data class CropPanBounds(val maxX: Float, val maxY: Float)

/**
 * 算出预览框里图片还能平移多少像素。
 *
 * 图片先按填满取景框缩放，再乘 [zoom]，溢出取景框的部分对半分到两侧，
 * 这个余量与 [cropSourceRect] 在原图里留出的空间是同一块，二者口径一致。
 */
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
 * 把预览里的像素平移折算成 [cropSourceRect] 用的偏移。
 *
 * 图片向右移意味着取的是原图左侧，所以两者符号相反；余量为零时只能居中。
 */
fun cropOffsetFraction(translation: Float, maxPan: Float): Float =
    if (maxPan <= 0f) 0f else (-translation / maxPan).coerceIn(-1f, 1f)
