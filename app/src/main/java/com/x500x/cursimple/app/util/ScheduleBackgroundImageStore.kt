package com.x500x.cursimple.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Save cropped backgrounds privately so rendering does not require continued gallery
 * permission.
 */
object ScheduleBackgroundImageStore {

    private const val FILE_NAME = "schedule-background.png"
    private const val MAX_EDGE = 2048

    // Bound source decoding before creating the final crop.
    private const val DECODE_MAX_EDGE = 4096

    fun readSize(context: Context, uri: Uri): Pair<Int, Int>? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(requireNotNull(input), null, options)
        }
        options.outWidth.takeIf { it > 0 }?.let { it to options.outHeight }
    }.getOrNull()

    fun saveCropped(context: Context, source: Uri, rect: CropSourceRect): Uri? = runCatching {
        val (srcWidth, srcHeight) = readSize(context, source) ?: return null
        // Map original crop coordinates to decoded dimensions after downsampling.
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

    /** Limit oversized backgrounds before repeated timetable decoding. */
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
