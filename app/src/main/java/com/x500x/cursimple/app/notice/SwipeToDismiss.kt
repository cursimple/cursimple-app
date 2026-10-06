package com.x500x.cursimple.app.notice

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class SwipeToDismiss(
    private val mover: Mover,
    private val touchSlop: Int,
    private val dismissDistance: Float,
    private val flingVelocity: Float,
    private val onHold: () -> Unit,
    private val onRelease: () -> Unit,
    private val onTap: () -> Unit,
    private val onDismiss: () -> Unit,
) : View.OnTouchListener, View.OnAttachStateChangeListener {
    private val homeX = mover.x
    private val homeY = mover.y
    private var startX = homeX
    private var startY = homeY
    private var downX = 0f
    private var downY = 0f
    private var axis: NoticeSwipePolicy.Axis? = null
    private var dragging = false
    private var dismissing = false
    private var disposed = false
    private var multiTouch = false
    private var tracker: VelocityTracker? = null
    private var animator: ValueAnimator? = null
    private var owner: View? = null
    private var nextX = homeX
    private var nextY = homeY
    private var framePending = false
    private val moveFrame = Runnable {
        framePending = false
        if (!disposed && !dismissing) mover.moveTo(nextX, nextY)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (disposed || dismissing) return true
        if (owner == null) {
            owner = view
            view.addOnAttachStateChangeListener(this)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelAnimation()
            flushMove()
            tracker?.recycle()
            tracker = VelocityTracker.obtain()
            downX = event.rawX
            downY = event.rawY
            startX = mover.x
            startY = mover.y
            axis = null
            dragging = false
            multiTouch = false
            onHold()
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        // Use screen coordinates for velocity because the window moves during dragging.
        val sample = MotionEvent.obtain(event)
        sample.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker?.addMovement(sample)
        sample.recycle()
        val dx = event.rawX - downX
        val dy = event.rawY - downY
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_MOVE -> if (!multiTouch) {
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    axis = if (abs(dx) >= abs(dy)) NoticeSwipePolicy.Axis.Horizontal else NoticeSwipePolicy.Axis.Vertical
                }
                if (dragging) queueMove(view, dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                if (multiTouch) {
                    returnHome()
                } else if (!dragging) {
                    view.performClick()
                    onTap()
                } else {
                    queueMove(view, dx, dy)
                    flushMove()
                    tracker?.computeCurrentVelocity(1_000)
                    val velocityX = tracker?.xVelocity ?: 0f
                    val velocityY = tracker?.yVelocity ?: 0f
                    val direction = NoticeSwipePolicy.direction(
                        axis = axis,
                        dx = mover.x - homeX,
                        dy = mover.y - homeY,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        dismissDistance = dismissDistance,
                        flingVelocity = flingVelocity,
                        minFlingDistance = touchSlop * 2f,
                    )
                    if (direction != null) slideOut(view, direction, velocityX, velocityY) else returnHome()
                }
                recycleTracker()
            }
            MotionEvent.ACTION_CANCEL -> {
                returnHome()
                recycleTracker()
            }
        }
        return true
    }

    private fun queueMove(view: View, dx: Float, dy: Float) {
        nextX = if (axis == NoticeSwipePolicy.Axis.Horizontal) startX + dx else homeX
        nextY = if (axis == NoticeSwipePolicy.Axis.Vertical) (startY + dy).coerceAtMost(homeY) else homeY
        if (!framePending) {
            framePending = true
            view.postOnAnimation(moveFrame)
        }
    }

    private fun flushMove() {
        if (!framePending) return
        owner?.removeCallbacks(moveFrame)
        framePending = false
        mover.moveTo(nextX, nextY)
    }

    private fun returnHome() {
        flushMove()
        animateTo(homeX, homeY, fade = false, durationMillis = 180L, onEnd = onRelease)
    }

    private fun slideOut(view: View, direction: NoticeSwipePolicy.Direction, velocityX: Float, velocityY: Float) {
        dismissing = true
        mover.prepareDismiss()
        val travel = (view.resources.displayMetrics.widthPixels.toFloat() + view.width) / 2f
        val targetX = when (direction) {
            NoticeSwipePolicy.Direction.Left -> homeX - travel
            NoticeSwipePolicy.Direction.Right -> homeX + travel
            NoticeSwipePolicy.Direction.Up -> homeX
        }
        val targetY = if (direction == NoticeSwipePolicy.Direction.Up) homeY - view.height - homeY.coerceAtLeast(0f) else homeY
        val velocity = if (direction == NoticeSwipePolicy.Direction.Up) abs(velocityY) else abs(velocityX)
        val remaining = hypot(targetX - mover.x, targetY - mover.y)
        val duration = if (velocity > 0f) (remaining / velocity * 1_000f).toLong().coerceIn(100L, 170L) else 170L
        animateTo(targetX, targetY, fade = true, durationMillis = duration, onEnd = onDismiss)
    }

    private fun animateTo(x: Float, y: Float, fade: Boolean, durationMillis: Long, onEnd: () -> Unit) {
        cancelAnimation()
        val fromX = mover.x
        val fromY = mover.y
        if (!fade && fromX == x && fromY == y) {
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val fraction = it.animatedValue as Float
                mover.moveTo(fromX + (x - fromX) * fraction, fromY + (y - fromY) * fraction, if (fade) 1f - fraction else 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    if (!disposed) onEnd()
                }
            })
            start()
        }
    }

    private fun cancelAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun recycleTracker() {
        tracker?.recycle()
        tracker = null
    }

    fun dispose() {
        disposed = true
        owner?.removeCallbacks(moveFrame)
        owner?.removeOnAttachStateChangeListener(this)
        owner = null
        framePending = false
        cancelAnimation()
        recycleTracker()
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = dispose()

    interface Mover {
        val x: Float
        val y: Float
        fun moveTo(x: Float, y: Float, alpha: Float = 1f)
        fun prepareDismiss() = Unit
    }

    class WindowMover(private val window: Window) : Mover {
        override val x: Float get() = window.attributes.x.toFloat()
        override val y: Float get() = window.attributes.y.toFloat()
        override fun moveTo(x: Float, y: Float, alpha: Float) {
            val attributes = window.attributes
            val nextX = x.roundToInt()
            val nextY = y.roundToInt()
            if (attributes.x == nextX && attributes.y == nextY && attributes.alpha == alpha) return
            attributes.x = nextX
            attributes.y = nextY
            attributes.alpha = alpha
            runCatching { window.attributes = attributes }
        }

        // Do not add a system exit animation after the gesture's own dismissal animation.
        override fun prepareDismiss() = window.setWindowAnimations(0)
    }

    class ViewMover(private val view: View) : Mover {
        override val x: Float get() = view.translationX
        override val y: Float get() = view.translationY
        override fun moveTo(x: Float, y: Float, alpha: Float) {
            view.translationX = x
            view.translationY = y
            view.alpha = alpha
        }
    }
}
