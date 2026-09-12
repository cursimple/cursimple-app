package com.x500x.cursimple.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 把裁好的课表背景写进应用私有目录。
 *
 * 裁切在选图时一次完成并另存，渲染端仍按普通图片读取，
 * 也就不再依赖对相册那张原图的长期访问授权。
 */
object ScheduleBackgroundImageStore {

    private const val FILE_NAME = "schedule-background.png"
    private const val MAX_EDGE = 2048

    // 裁切前先把原图降到这个长边，够裁出 MAX_EDGE 的结果又不至于把上亿像素整张解进内存
    private const val DECODE_MAX_EDGE = 4096

    /** 读取原图尺寸，不解码像素。 */
    fun readSize(context: Context, uri: Uri): Pair<Int, Int>? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(requireNotNull(input), null, options)
        }
        options.outWidth.takeIf { it > 0 }?.let { it to options.outHeight }
    }.getOrNull()

    /** 按 [rect] 裁切并另存，返回可直接用于显示的 Uri。 */
    fun saveCropped(context: Context, source: Uri, rect: CropSourceRect): Uri? = runCatching {
        val (srcWidth, srcHeight) = readSize(context, source) ?: return null
        // rect 是按原图像素算的；降采样后按实际解码尺寸等比映射，避免超大图整张解码 OOM
        val full = decodeSampledBitmap(context, source, DECODE_MAX_EDGE) ?: return null
        val scaleX = full.width.toFloat() / srcWidth
        val scaleY = full.height.toFloat() / srcHeight
        val left = (rect.left * scaleX).toInt().coerceIn(0, full.width - 1)
        val top = (rect.top * scaleY).toInt().coerceIn(0, full.height - 1)
        val width = (rect.width * scaleX).toInt().coerceAtLeast(1).coerceAtMost(full.width - left)
        val height = (rect.height * scaleY).toInt().coerceAtLeast(1).coerceAtMost(full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        if (cropped != full) full.recycle()
        val scaled = cropped.downscaledToMaxEdge()
        if (scaled != cropped) cropped.recycle()
        val target = File(context.filesDir, FILE_NAME)
        target.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        scaled.recycle()
        Uri.fromFile(target)
    }.getOrNull()

    /** 超大图缩到长边上限，避免课表每次重画都解码一张巨图。 */
    private fun Bitmap.downscaledToMaxEdge(): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= MAX_EDGE) return this
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
