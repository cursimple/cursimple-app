package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Navigation considers embedded-page presence only, independent of business structure. */
@Stable
class EmbeddedPageGestures {
    private val owners = mutableStateMapOf<Any, Unit>()
    val ownsContentGestures: Boolean get() = owners.isNotEmpty()

    internal fun acquire(): () -> Unit {
        val owner = Any()
        owners[owner] = Unit
        return { owners.remove(owner) }
    }
}

val LocalEmbeddedPageGestures = staticCompositionLocalOf<EmbeddedPageGestures?> { null }

@Composable
internal fun OwnEmbeddedPageGestures() {
    val gestures = LocalEmbeddedPageGestures.current
    DisposableEffect(gestures) {
        val release = gestures?.acquire()
        onDispose { release?.invoke() }
    }
}
