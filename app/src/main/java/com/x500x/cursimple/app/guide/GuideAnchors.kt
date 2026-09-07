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

/** 引导要圈出来的界面元素。 */
enum class GuideAnchor {
    /** 顶栏左侧打开侧边栏的按钮。 */
    Drawer,

    /** 顶栏右侧添加课程的按钮。 */
    Add,

    /** 顶栏中间显示周次、点开切换周次的区域。 */
    WeekTitle,
}

/**
 * 各元素在屏幕上的实际位置。
 *
 * 按屏幕比例估位置在不同机型、字号和系统栏高度下都会偏，
 * 改由元素自己在布局完成后上报，圈出来的框才落在它身上。
 */
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

/** 把这个元素的位置报给引导。 */
fun Modifier.guideAnchor(anchor: GuideAnchor): Modifier = composed {
    val anchors = LocalGuideAnchors.current
    onGloballyPositioned { coordinates ->
        anchors.update(anchor, coordinates.boundsInRoot())
    }
}
