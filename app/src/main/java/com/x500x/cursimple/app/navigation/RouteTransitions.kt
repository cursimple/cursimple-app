package com.x500x.cursimple.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The app's page change: a short fade with a slight slide, reversed when going back. */
fun routeTransition(forward: Boolean = true): ContentTransform {
    val sign = if (forward) 1 else -1
    return (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { sign * it / 8 }) togetherWith
        (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -sign * it / 8 })
}

/** A full-screen page over the current route; opening and closing it use [routeTransition]. */
@Composable
fun <T : Any> AnimatedPage(target: T?, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val closing = targetState == null
            (if (closing) routeTransition(forward = false) else routeTransition()).using(null)
        },
        label = "page_transition",
        modifier = Modifier.fillMaxSize(),
    ) { page -> page?.let { content(it) } }
}
