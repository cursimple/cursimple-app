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

class CropPanBoundsTest {
    @Test
    fun `a wide photo in a tall frame can be panned across its whole width`() {
        // 竖长取景框配横图：图片按高度填满，左右各溢出一半
        val bounds = cropPanBounds(
            frameWidth = 310f,
            frameHeight = 500f,
            imageWidth = 4000,
            imageHeight = 3000,
            zoom = 1f,
        )

        // 填满高度后图片宽 4000 * (500/3000) = 666.7，溢出 356.7，两侧各 178.3
        assertEquals(178.3f, bounds.maxX, 0.5f)
        assertEquals(0f, bounds.maxY, 0.01f)
    }

    @Test
    fun `an image matching the frame ratio has no slack until zoomed`() {
        val flush = cropPanBounds(310f, 500f, 620, 1000, zoom = 1f)

        assertEquals(0f, flush.maxX, 0.01f)
        assertEquals(0f, flush.maxY, 0.01f)

        val zoomed = cropPanBounds(310f, 500f, 620, 1000, zoom = 2f)

        assertEquals(155f, zoomed.maxX, 0.5f)
        assertEquals(250f, zoomed.maxY, 0.5f)
    }

    @Test
    fun `zooming widens the slack proportionally`() {
        val single = cropPanBounds(300f, 500f, 2000, 1000, zoom = 1f)
        val double = cropPanBounds(300f, 500f, 2000, 1000, zoom = 2f)

        assertTrue(double.maxX > single.maxX * 1.9f)
    }

    @Test
    fun `a degenerate frame or image yields no slack`() {
        assertEquals(0f, cropPanBounds(0f, 500f, 100, 100, 1f).maxX, 0.01f)
        assertEquals(0f, cropPanBounds(300f, 500f, 0, 100, 1f).maxX, 0.01f)
    }
}

class CropOffsetFractionTest {
    @Test
    fun `panning the image right takes the left of the photo`() {
        assertEquals(-1f, cropOffsetFraction(translation = 120f, maxPan = 120f), 0.001f)
        assertEquals(1f, cropOffsetFraction(translation = -120f, maxPan = 120f), 0.001f)
    }

    @Test
    fun `no pan means centred`() {
        assertEquals(0f, cropOffsetFraction(translation = 0f, maxPan = 120f), 0.001f)
    }

    @Test
    fun `without slack the offset stays centred`() {
        assertEquals(0f, cropOffsetFraction(translation = 50f, maxPan = 0f), 0.001f)
    }

    @Test
    fun `an overshoot is clamped`() {
        assertEquals(-1f, cropOffsetFraction(translation = 500f, maxPan = 120f), 0.001f)
    }

    @Test
    fun `the preview offset lands on the same area the crop takes`() {
        // 4000x3000 的横图放进 0.62 的竖框，把图片拖到最右端
        val frameWidth = 310f
        val frameHeight = frameWidth / 0.62f
        val bounds = cropPanBounds(frameWidth, frameHeight, 4000, 3000, zoom = 1f)
        val fraction = cropOffsetFraction(translation = bounds.maxX, maxPan = bounds.maxX)

        val rect = cropSourceRect(4000, 3000, frameAspect = 0.62f, offsetXFraction = fraction)!!

        assertEquals(0, rect.left)
        assertEquals(3000, rect.height)
    }
}
