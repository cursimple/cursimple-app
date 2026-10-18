package com.x500x.cursimple.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.x500x.cursimple.feature.plugin.ui.AppMotion

/** The app's page change: a shared-axis drift with a quick cross-fade, reversed when going back. */
fun routeTransition(forward: Boolean = true): ContentTransform = AppMotion.sharedAxisX(forward)

/**
 * A full-screen page over the current route. It slides in over the still screen and slides back out
 * on close, so the screen underneath never shows through a half-faded page.
 */
@Composable
fun <T : Any> AnimatedPage(target: T?, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            when {
                initialState == null -> AppMotion.pushEnter togetherWith ExitTransition.None
                targetState == null -> EnterTransition.None togetherWith AppMotion.popExit
                else -> routeTransition()
            }.using(null)
        },
        label = "page_transition",
        modifier = Modifier.fillMaxSize(),
    ) { page -> page?.let { content(it) } }
}
