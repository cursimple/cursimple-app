package com.x500x.cursimple.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 界面上显示图片时的长边上限，超过就降采样。 */
const val PREVIEW_MAX_EDGE_PX = 1600

/** 按长边上限算出 inSampleSize（2 的幂），使解出的长边不超过 [maxEdgePx]。 */
fun sampleSizeForMaxEdge(width: Int, height: Int, maxEdgePx: Int): Int {
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / sample > maxEdgePx) sample *= 2
    return sample
}

/**
 * 按长边上限降采样解码为原始 [Bitmap]（调用方负责回收）。
 *
 * 相册里的照片动辄几千万像素，整张解出来只为显示一个几百像素的框，
 * 既慢又容易把内存吃光。
 */
fun decodeSampledBitmap(context: Context, uri: Uri, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(requireNotNull(input), null, bounds)
    }
    if (maxOf(bounds.outWidth, bounds.outHeight) <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeForMaxEdge(bounds.outWidth, bounds.outHeight, maxEdgePx)
    }
    return context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(requireNotNull(input), null, options)
    }
}

/** 同 [decodeSampledBitmap]，但返回 Compose 用的 [ImageBitmap]。 */
fun decodeSampledImage(context: Context, uri: Uri, maxEdgePx: Int): ImageBitmap? =
    decodeSampledBitmap(context, uri, maxEdgePx)?.asImageBitmap()
