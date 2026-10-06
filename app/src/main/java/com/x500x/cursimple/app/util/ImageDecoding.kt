package com.x500x.cursimple.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Maximum decoded image edge for UI display. */
const val PREVIEW_MAX_EDGE_PX = 1600

/** Power-of-two sampling that respects [maxEdgePx]. */
fun sampleSizeForMaxEdge(width: Int, height: Int, maxEdgePx: Int): Int {
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / sample > maxEdgePx) sample *= 2
    return sample
}

/** Downsample before bitmap allocation; the caller owns [Bitmap] recycling. */
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

/** Compose [ImageBitmap] equivalent of [decodeSampledBitmap]. */
fun decodeSampledImage(context: Context, uri: Uri, maxEdgePx: Int): ImageBitmap? =
    decodeSampledBitmap(context, uri, maxEdgePx)?.asImageBitmap()
