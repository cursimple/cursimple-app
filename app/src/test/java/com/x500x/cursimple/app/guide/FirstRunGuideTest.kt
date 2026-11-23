package com.x500x.cursimple.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldShowFirstRunGuideTest {
    @Test
    fun `a fresh install shows permissions before the guide`() {
        assertTrue(shouldReviewFirstRunPermissions(guideCompleted = false, permissionsReviewed = false))
        assertFalse(shouldReviewFirstRunPermissions(guideCompleted = true, permissionsReviewed = false))
        assertFalse(shouldReviewFirstRunPermissions(guideCompleted = false, permissionsReviewed = true))
    }

    @Test
    fun `the guide follows permission review`() {
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
    fun `every tour page has a distinct demo and explanation`() {
        val pages = firstRunGuideSteps()
        assertEquals(4, pages.size)
        assertEquals(pages.size, pages.map { it.animationRes }.toSet().size)
        assertEquals(pages.size, pages.map { it.bodyRes }.toSet().size)
    }

}
