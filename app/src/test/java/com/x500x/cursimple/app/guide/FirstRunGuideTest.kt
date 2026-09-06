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
    fun `every step points at a real area of the screen`() {
        FIRST_RUN_GUIDE_STEPS.forEach { step ->
            val area = step.spotlight
            assertTrue("left < right", area.left < area.right)
            assertTrue("top < bottom", area.top < area.bottom)
            assertTrue("在屏幕内", area.left >= 0f && area.right <= 1f)
            assertTrue("在屏幕内", area.top >= 0f && area.bottom <= 1f)
        }
    }

    @Test
    fun `the guide has steps to show`() {
        assertTrue(FIRST_RUN_GUIDE_STEPS.isNotEmpty())
    }
}
