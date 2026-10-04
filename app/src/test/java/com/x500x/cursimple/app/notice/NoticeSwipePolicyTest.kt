package com.x500x.cursimple.app.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeSwipePolicyTest {
    private fun direction(dx: Float = 0f, dy: Float = 0f, vx: Float = 0f, vy: Float = 0f,
        axis: NoticeSwipePolicy.Axis? = NoticeSwipePolicy.Axis.Horizontal,
    ) = NoticeSwipePolicy.direction(axis, dx, dy, vx, vy, 48f, 650f, 16f)

    @Test fun `short fast flick dismisses in either horizontal direction`() {
        assertEquals(NoticeSwipePolicy.Direction.Left, direction(dx = -20f, vx = -900f))
        assertEquals(NoticeSwipePolicy.Direction.Right, direction(dx = 20f, vx = 900f))
    }

    @Test fun `slow short drag and touch jitter return home`() {
        assertNull(direction(dx = 20f, vx = 80f))
        assertNull(direction(dx = 3f, vx = 1_400f))
    }

    @Test fun `opposite flick does not dismiss short drag`() {
        assertNull(direction(dx = 25f, vx = -900f))
        assertNull(direction(dx = -25f, vx = 900f))
    }

    @Test fun `long drag dismisses without velocity`() {
        assertEquals(NoticeSwipePolicy.Direction.Left, direction(dx = -48f))
        assertEquals(NoticeSwipePolicy.Direction.Right, direction(dx = 48f))
    }

    @Test fun `upward flick dismisses but downward drag does not`() {
        assertEquals(NoticeSwipePolicy.Direction.Up, direction(dy = -20f, vy = -900f, axis = NoticeSwipePolicy.Axis.Vertical))
        assertNull(direction(dy = 100f, vy = 900f, axis = NoticeSwipePolicy.Axis.Vertical))
    }

    @Test fun `locked axis ignores movement and velocity on other axis`() {
        assertNull(direction(dx = 10f, dy = -90f, vy = -900f))
        assertNull(direction(dx = 90f, dy = -10f, vx = 900f, axis = NoticeSwipePolicy.Axis.Vertical))
        assertNull(direction(dx = 100f, axis = null))
    }
}
