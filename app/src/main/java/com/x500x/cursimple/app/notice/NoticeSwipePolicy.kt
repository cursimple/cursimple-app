package com.x500x.cursimple.app.notice

import kotlin.math.abs

/** 距离足够时拖走，距离较短时允许顺着手势的快速轻甩；反向甩动和触摸抖动不关闭。 */
internal object NoticeSwipePolicy {
    enum class Axis { Horizontal, Vertical }
    enum class Direction { Left, Right, Up }

    fun direction(
        axis: Axis?,
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        dismissDistance: Float,
        flingVelocity: Float,
        minFlingDistance: Float,
    ): Direction? = when (axis) {
        Axis.Horizontal -> {
            val far = abs(dx) >= dismissDistance
            val fling = abs(dx) >= minFlingDistance && abs(velocityX) >= flingVelocity && dx * velocityX > 0f
            if (!far && !fling) null else if (dx < 0f) Direction.Left else Direction.Right
        }
        Axis.Vertical -> {
            val far = -dy >= dismissDistance
            val fling = -dy >= minFlingDistance && -velocityY >= flingVelocity
            if (far || fling) Direction.Up else null
        }
        null -> null
    }
}
