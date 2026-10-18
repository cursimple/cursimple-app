package com.x500x.cursimple.feature.plugin.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Centralized, tactile haptic feedback provider tailored for buttons,
 * week dials, chips, toggles, and deletion confirmations.
 */
class AppHaptics(private val view: View) {
    /** Light, crisp feedback for button clicks, tabs, segmented controls, chips, and switches. */
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Subtle mechanical tick for week picking, step changes, and scrolling dials. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Positive confirmation feedback for saving or completing an item. */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Distinct, prominent feedback for long-press and destructive operations. */
    fun heavy() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/**
 * Remembers an [AppHaptics] instance bound to the current [LocalView].
 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}
