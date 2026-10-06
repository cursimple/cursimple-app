package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Measured fit tests use synthetic layouts with scale-dependent wrapping thresholds. */
class CourseCardFitScaleTest {

    private fun bottoms(lines: Int, lineHeight: Float) = (1..lines).map { it * lineHeight }

    /** Title wrap count changes at [titleWrapAt]; location retains three scaled lines. */
    private fun layout(titleWrapAt: Float = 0.8f, locationLines: Int = 3): (Float) -> Pair<List<Float>, List<Float>> = { scale ->
        val titleLines = if (scale >= titleWrapAt) 3 else 2
        bottoms(titleLines, 14f * scale) to bottoms(locationLines, 11f * scale)
    }

    private fun fitsAll(available: Float, scale: Float, measure: (Float) -> Pair<List<Float>, List<Float>>): Boolean {
        val (title, location) = measure(scale)
        val plan = courseCardTextPlan(available, title, location, badgeHeight = 0f)
        return plan.titleComplete && plan.locationComplete
    }

    @Test
    fun keepsSizeWhenEverythingFits() {
        assertEquals(1f, fitCourseCardTextScale(80f, minScale = 0.6f, measure = layout()), 0f)
    }

    @Test
    fun shrinksUntilTitleAndLocationBothFit() {
        // Smaller text removes one wrapped title line and leaves room for location.
        val measure = layout()
        val scale = fitCourseCardTextScale(60f, minScale = 0.6f, measure = measure)
        assertTrue(scale < 1f)
        assertTrue(fitsAll(60f, scale, measure))
        assertTrue(!fitsAll(60f, scale + 0.02f, measure))
    }

    @Test
    fun showsAsMuchLocationAsPossibleWhenItCannotAllFit() {
        val measure = layout(titleWrapAt = 0.8f, locationLines = 10)
        fun locationLines(scale: Float) = measure(scale).let { (title, location) ->
            courseCardTextPlan(42f, title, location, badgeHeight = 0f)
        }
        val scale = fitCourseCardTextScale(42f, minScale = 0.6f, measure = measure)
        val plan = locationLines(scale)
        assertTrue(plan.titleComplete)
        assertEquals(locationLines(0.6f).locationLines, plan.locationLines)
        assertTrue(locationLines(scale + 0.02f).locationLines < plan.locationLines)
    }

    @Test
    fun doesNotShrinkWhenNotEvenOneLocationLineCanAppear() {
        // Avoid shrinking if even minimum scale gains no location line.
        val measure: (Float) -> Pair<List<Float>, List<Float>> = { scale ->
            bottoms(3, 14f * scale) to bottoms(3, 30f)
        }
        assertEquals(1f, fitCourseCardTextScale(42f, minScale = 0.6f, measure = measure), 0f)
    }

    @Test
    fun noLocationOnlyNeedsTitleToFit() {
        val measure: (Float) -> Pair<List<Float>, List<Float>> = { scale -> bottoms(3, 14f * scale) to emptyList() }
        assertEquals(1f, fitCourseCardTextScale(42f, minScale = 0.6f, measure = measure), 0f)
        val scale = fitCourseCardTextScale(35f, minScale = 0.6f, measure = measure)
        assertTrue(scale < 1f)
        assertTrue(fitsAll(35f, scale, measure))
    }

    @Test
    fun minimumScaleOfOneMeansNoShrinking() {
        assertEquals(1f, fitCourseCardTextScale(10f, minScale = 1f, measure = layout()), 0f)
    }
}
