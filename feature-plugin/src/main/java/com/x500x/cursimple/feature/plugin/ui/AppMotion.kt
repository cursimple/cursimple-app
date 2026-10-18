package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/**
 * One motion language for page, tab and pushed-page changes. Nothing scales: scaling a whole page
 * re-rasterises its text every frame, which is what made the old transitions shimmer and stutter.
 */
object AppMotion {
    /** Material 3 emphasized curves: most of the travel happens early, then it settles softly. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SWITCH_MILLIS = 250
    const val PUSH_MILLIS = 300
    const val POP_MILLIS = 240
    private const val FADE_OUT_MILLIS = 80

    /**
     * Shared-axis change between sibling pages: both drift the same way, the old one fades out first
     * so the two never sit on top of each other at half opacity.
     */
    fun sharedAxisX(forward: Boolean, distanceFraction: Float = 0.08f): ContentTransform {
        val sign = if (forward) 1 else -1
        val enter = slideInHorizontally(tween(SWITCH_MILLIS, easing = EmphasizedDecelerate)) { (sign * it * distanceFraction).toInt() } +
            fadeIn(tween(SWITCH_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))
        val exit = slideOutHorizontally(tween(SWITCH_MILLIS, easing = EmphasizedDecelerate)) { (-sign * it * distanceFraction).toInt() } +
            fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))
        return ContentTransform(enter, exit, sizeTransform = null)
    }

    /** A page laid over a still screen slides fully in from the trailing edge; no fade, so nothing shows through. */
    val pushEnter: EnterTransition = slideInHorizontally(tween(PUSH_MILLIS, easing = EmphasizedDecelerate)) { it }

    /** Closing slides the page back out, a little quicker than it came in. */
    val popExit: ExitTransition = slideOutHorizontally(tween(POP_MILLIS, easing = EmphasizedAccelerate)) { it }

    /** A section a switch opens grows down from its top edge while fading in. */
    val revealEnter: EnterTransition =
        expandVertically(tween(240, easing = EmphasizedDecelerate), expandFrom = Alignment.Top) +
            fadeIn(tween(200, delayMillis = 40, easing = LinearOutSlowInEasing))

    /** Closing folds it back up, slightly faster, fading first so the text never squashes. */
    val revealExit: ExitTransition =
        fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            shrinkVertically(tween(200, easing = EmphasizedAccelerate), shrinkTowards = Alignment.Top)
}

/**
 * A touched card dips a little and springs back, so a tap feels physical. Pass the same
 * [interactionSource] the card's click uses: inside a scrolling list the press only registers once
 * the touch is clearly a tap, so flinging through a list never makes the cards wobble.
 */
fun Modifier.pressFeedback(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) tween(120, easing = FastOutSlowInEasing)
        else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press_feedback",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * List rows fade in, fade out and glide to their new slot when the list is added to, filtered or
 * reordered, instead of jumping. Needs a stable item key.
 */
fun LazyItemScope.appItemMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(220, easing = LinearOutSlowInEasing),
    placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
    fadeOutSpec = tween(140, easing = FastOutLinearInEasing),
)
