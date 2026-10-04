package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/** 宿主导航只关心是否有嵌入页面，不关心组件的业务或页面结构。 */
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

/** 按页面存续期占用手势；转场时新旧页面重叠也不会提前恢复抽屉拖动。 */
@Composable
internal fun OwnEmbeddedPageGestures() {
    val gestures = LocalEmbeddedPageGestures.current
    DisposableEffect(gestures) {
        val release = gestures?.acquire()
        onDispose { release?.invoke() }
    }
}
