package com.x500x.cursimple.app.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class GuideAnchor {
    Drawer,

    Add,

    WeekTitle,
}

/** Collect actual layout bounds rather than estimating positions from screen proportions. */
class GuideAnchorBounds {
    private val bounds = mutableStateMapOf<GuideAnchor, Rect>()

    operator fun get(anchor: GuideAnchor): Rect? = bounds[anchor]

    fun update(anchor: GuideAnchor, rect: Rect) {
        if (bounds[anchor] != rect) bounds[anchor] = rect
    }
}

val LocalGuideAnchors = staticCompositionLocalOf { GuideAnchorBounds() }

@Composable
fun rememberGuideAnchorBounds(): GuideAnchorBounds = remember { GuideAnchorBounds() }

fun Modifier.guideAnchor(anchor: GuideAnchor): Modifier = composed {
    val anchors = LocalGuideAnchors.current
    onGloballyPositioned { coordinates ->
        anchors.update(anchor, coordinates.boundsInRoot())
    }
}
