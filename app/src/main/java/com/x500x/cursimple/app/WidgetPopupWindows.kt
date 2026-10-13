package com.x500x.cursimple.app

import android.app.Activity
import android.os.Build

/**
 * Launchers animate a closing task however they like (slide, scale, return to the widget), and
 * app transition overrides do not reach those animations. So a widget popup first turns its window
 * and dim invisible, then runs [close] once that frame is on screen; whatever the launcher animates
 * is then invisible.
 */
internal fun Activity.vanishThen(close: () -> Unit) {
    window.setWindowAnimations(0)
    window.attributes = window.attributes.apply { alpha = 0f; dimAmount = 0f }
    val decor = window.decorView
    if (!decor.isAttachedToWindow) { close(); return }
    var done = false
    val once = Runnable { if (!done) { done = true; close() } }
    decor.alpha = 0f
    if (Build.VERSION.SDK_INT >= 29) decor.viewTreeObserver.registerFrameCommitCallback { decor.post(once) }
    else decor.postOnAnimation { decor.postOnAnimation(once) }
    // A stopped window draws no frame; never leave the popup open waiting for one.
    decor.postDelayed(once, VANISH_FALLBACK_MILLIS)
}

private const val VANISH_FALLBACK_MILLIS = 150L
