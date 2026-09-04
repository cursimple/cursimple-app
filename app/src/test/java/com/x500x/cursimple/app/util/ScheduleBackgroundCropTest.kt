package com.x500x.cursimple.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBackgroundCropTest {

    @Test
    fun `an unusable image or frame yields nothing`() {
        assertNull(cropSourceRect(0, 100, 1f))
        assertNull(cropSourceRect(100, 0, 1f))
        assertNull(cropSourceRect(100, 100, 0f))
        assertNull(cropSourceRect(100, 100, Float.NaN))
    }

    @Test
    fun `a wide image is cropped on the sides to match a tall frame`() {
        // 原图 200x100，目标比例 1:1，应取中间的 100x100
        val rect = cropSourceRect(200, 100, frameAspect = 1f)!!

        assertEquals(100, rect.width)
        assertEquals(100, rect.height)
        assertEquals(50, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun `a tall image is cropped top and bottom to match a wide frame`() {
        val rect = cropSourceRect(100, 400, frameAspect = 1f)!!

        assertEquals(100, rect.width)
        assertEquals(100, rect.height)
        assertEquals(0, rect.left)
        assertEquals(150, rect.top)
    }

    @Test
    fun `an already matching image is taken whole`() {
        val rect = cropSourceRect(300, 600, frameAspect = 0.5f)!!

        assertEquals(300, rect.width)
        assertEquals(600, rect.height)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun `zooming in shrinks the taken area and keeps it centred`() {
        val rect = cropSourceRect(200, 200, frameAspect = 1f, zoom = 2f)!!

        assertEquals(100, rect.width)
        assertEquals(100, rect.height)
        assertEquals(50, rect.left)
        assertEquals(50, rect.top)
    }

    @Test
    fun `zoom below one is treated as no zoom`() {
        val plain = cropSourceRect(200, 200, frameAspect = 1f)!!
        val shrunk = cropSourceRect(200, 200, frameAspect = 1f, zoom = 0.2f)!!

        assertEquals(plain, shrunk)
    }

    @Test
    fun `offsets move the taken area within the image`() {
        val left = cropSourceRect(200, 100, frameAspect = 1f, offsetXFraction = -1f)!!
        val right = cropSourceRect(200, 100, frameAspect = 1f, offsetXFraction = 1f)!!

        assertEquals(0, left.left)
        assertEquals(100, right.left)
    }

    @Test
    fun `offsets beyond the range are clamped inside the image`() {
        val rect = cropSourceRect(200, 100, frameAspect = 1f, offsetXFraction = 9f)!!

        assertEquals(100, rect.left)
        assertTrue(rect.left + rect.width <= 200)
    }

    @Test
    fun `the taken area never leaves the image`() {
        val sizes = listOf(200 to 100, 100 to 400, 640 to 640, 1080 to 1920)
        val aspects = listOf(0.4f, 1f, 2.5f)
        val zooms = listOf(1f, 1.7f, 4f)
        val offsets = listOf(-1f, -0.3f, 0f, 0.6f, 1f)
        for ((w, h) in sizes) for (a in aspects) for (z in zooms) for (ox in offsets) for (oy in offsets) {
            val rect = cropSourceRect(w, h, a, z, ox, oy)!!
            assertTrue("$w x $h a=$a z=$z", rect.left >= 0 && rect.top >= 0)
            assertTrue("$w x $h a=$a z=$z", rect.left + rect.width <= w)
            assertTrue("$w x $h a=$a z=$z", rect.top + rect.height <= h)
            assertTrue("$w x $h a=$a z=$z", rect.width >= 1 && rect.height >= 1)
        }
    }
}
