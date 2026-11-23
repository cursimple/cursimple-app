package com.x500x.cursimple.app.guide

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun GuideAnimation(
    @RawRes resource: Int,
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, resource)).also {
                            (it as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        }
                    } else {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        resources.openRawResource(resource).use { LegacyGuideGif(Movie.decodeStream(it)) }
                    },
                )
            }
        },
        onRelease = { view -> (view.drawable as? Animatable)?.stop() },
        update = { view ->
            view.contentDescription = description
            val motionEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ValueAnimator.areAnimatorsEnabled()
            } else {
                Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }
            val animation = view.drawable as? Animatable
            if (active && motionEnabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) animation?.start()
            else animation?.stop()
        },
    )
}

@Suppress("DEPRECATION")
private class LegacyGuideGif(private val movie: Movie) : Drawable(), Animatable {
    private var startedAt: Long? = null
    private val nextFrame = Runnable { invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val elapsed = startedAt?.let { now - it } ?: 0L
        movie.setTime((elapsed % movie.duration().coerceAtLeast(1)).toInt())
        val saveCount = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
        movie.draw(canvas, 0f, 0f)
        canvas.restoreToCount(saveCount)
        if (isRunning()) scheduleSelf(nextFrame, now + 33)
    }

    override fun start() {
        if (startedAt != null) return
        startedAt = SystemClock.uptimeMillis()
        invalidateSelf()
    }

    override fun stop() {
        if (startedAt == null) return
        startedAt = null
        unscheduleSelf(nextFrame)
        invalidateSelf()
    }

    override fun isRunning(): Boolean = startedAt != null
    override fun getIntrinsicWidth(): Int = movie.width()
    override fun getIntrinsicHeight(): Int = movie.height()
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
