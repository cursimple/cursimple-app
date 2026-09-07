package com.x500x.cursimple.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldShowFirstRunGuideTest {
    @Test
    fun `a fresh install shows the guide once the disclaimer is accepted`() {
        assertTrue(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `nothing shows before preferences are loaded`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = false,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `the disclaimer comes first`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = false,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `the term start prompt is not covered by the guide`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = true,
            ),
        )
    }

    @Test
    fun `a finished guide never comes back on its own`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = true,
                blockingDialogVisible = false,
            ),
        )
    }
}

class GuideNavigationTest {
    @Test
    fun `next walks forward through the steps`() {
        assertEquals(1, nextGuideIndex(current = 0, total = 3))
        assertEquals(2, nextGuideIndex(current = 1, total = 3))
    }

    @Test
    fun `next past the last step ends the guide`() {
        assertNull(nextGuideIndex(current = 2, total = 3))
    }

    @Test
    fun `previous walks back but stops at the first step`() {
        assertEquals(1, previousGuideIndex(current = 2))
        assertEquals(0, previousGuideIndex(current = 1))
        assertEquals(0, previousGuideIndex(current = 0))
    }
}

class GuideStepsTest {
    @Test
    fun `the guide has steps to show`() {
        assertTrue(FIRST_RUN_GUIDE_STEPS.isNotEmpty())
    }

    @Test
    fun `steps that point somewhere name a real element`() {
        FIRST_RUN_GUIDE_STEPS.mapNotNull { it.anchor }.forEach { anchor ->
            assertTrue(anchor in GuideAnchor.entries)
        }
    }
}

class GuideCardPlacementTest {
    @Test
    fun `a spotlight up top puts the card at the bottom`() {
        assertEquals(
            GuideCardPlacement.Bottom,
            guideCardPlacement(anchorCenterY = 120f, containerHeight = 2400f),
        )
    }

    @Test
    fun `a spotlight down low puts the card up top`() {
        assertEquals(
            GuideCardPlacement.Top,
            guideCardPlacement(anchorCenterY = 1800f, containerHeight = 2400f),
        )
    }

    @Test
    fun `a step with nothing to circle centres the card`() {
        assertEquals(
            GuideCardPlacement.Center,
            guideCardPlacement(anchorCenterY = null, containerHeight = 2400f),
        )
        assertEquals(
            GuideCardPlacement.Center,
            guideCardPlacement(anchorCenterY = 100f, containerHeight = 0f),
        )
    }
}
