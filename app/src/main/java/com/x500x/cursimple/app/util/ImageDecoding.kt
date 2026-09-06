package com.x500x.cursimple.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 界面上显示图片时的长边上限，超过就降采样。 */
const val PREVIEW_MAX_EDGE_PX = 1600

/**
 * 按长边上限降采样解码。
 *
 * 相册里的照片动辄几千万像素，整张解出来只为显示一个几百像素的框，
 * 既慢又容易把内存吃光。
 */
fun decodeSampledImage(context: Context, uri: Uri, maxEdgePx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(requireNotNull(input), null, bounds)
    }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > maxEdgePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(requireNotNull(input), null, options)?.asImageBitmap()
    }
}
