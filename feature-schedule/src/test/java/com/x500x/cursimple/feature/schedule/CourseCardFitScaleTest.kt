package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「长课名自动缩小」按格子实际空间缩字号：课名加地点放不下时一起缩，缩到放得下为止。
 *
 * 这里用一个假的排版：字号按比例缩，每行高度跟着缩；课名、地点在缩到一定程度后少折一行，
 * 模拟窄格子里字小了一行能多塞几个字。
 */
class CourseCardFitScaleTest {

    private fun bottoms(lines: Int, lineHeight: Float) = (1..lines).map { it * lineHeight }

    /** 课名 14px 一行，比例 ≥ [titleWrapAt] 时折三行，更小时两行；地点 11px 一行，固定三行。 */
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
        // 3×14 + 3×11 = 75，给 80 就不用缩
        assertEquals(1f, fitCourseCardTextScale(80f, minScale = 0.6f, measure = layout()), 0f)
    }

    @Test
    fun shrinksUntilTitleAndLocationBothFit() {
        // 原字号要 75，只有 60：缩一点之后课名少折一行，地点就放得下了
        val measure = layout()
        val scale = fitCourseCardTextScale(60f, minScale = 0.6f, measure = measure)
        assertTrue(scale < 1f)
        assertTrue(fitsAll(60f, scale, measure))
        // 取的是放得下的最大比例：再大一点就放不下
        assertTrue(!fitsAll(60f, scale + 0.02f, measure))
    }

    @Test
    fun showsAsMuchLocationAsPossibleWhenItCannotAllFit() {
        // 地点折十行，缩到最小也放不全：课名保持完整，地点露出最小字号时能露出的三行，字号尽量大
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
        // 格子只够课名：缩到最小也挤不出一行地点，那就不缩
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
