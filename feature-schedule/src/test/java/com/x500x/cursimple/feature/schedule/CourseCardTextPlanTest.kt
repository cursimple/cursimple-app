package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程卡片里几行文字的取舍。
 *
 * 按优先级从上往下填：课名 > 地点 > 附注，能放几行放几行，放不全的截断。
 * 行底由 TextMeasurer 量出，这里直接给出每行的底边位置（像素）。
 */
class CourseCardTextPlanTest {

    /** 行高 [lineHeight]、共 [lines] 行时各行的行底。 */
    private fun bottoms(lines: Int, lineHeight: Float) = List(lines) { (it + 1) * lineHeight }

    private fun plan(
        height: Float,
        titleLines: Int = 1,
        locationLines: Int = 1,
        badgeHeight: Float = 0f,
    ) = courseCardTextPlan(
        availableHeight = height,
        titleLineBottoms = bottoms(titleLines, 40f),
        locationLineBottoms = bottoms(locationLines, 30f),
        badgeHeight = badgeHeight,
    )

    @Test
    fun `空间够时课名与地点都完整显示`() {
        val result = plan(height = 200f, titleLines = 2, locationLines = 2)

        assertTrue(result.titleComplete)
        assertEquals(2, result.titleLines)
        assertTrue(result.showLocation)
        assertTrue(result.locationComplete)
    }

    @Test
    fun `放得下几行课名就显示几行，一行都不浪费`() {
        // 202 能放下 5 行 40 的课名；以前按字号估算只给 4 行，下面空着一大截
        val result = plan(height = 202f, titleLines = 6)

        assertEquals(5, result.titleLines)
        assertFalse(result.titleComplete)
    }

    @Test
    fun `课名没显示完就不再排地点`() {
        val result = plan(height = 100f, titleLines = 4)

        assertFalse(result.titleComplete)
        assertFalse(result.showLocation)
    }

    @Test
    fun `地点放不全时显示放得下的那几行`() {
        // 课名 2 行占 80，剩 60，地点 3 行只放得下 2 行
        val result = plan(height = 140f, titleLines = 2, locationLines = 3)

        assertTrue(result.showLocation)
        assertEquals(2, result.locationLines)
        assertFalse(result.locationComplete)
    }

    @Test
    fun `剩余高度不够一行地点时不显示地点`() {
        val result = plan(height = 100f, titleLines = 2, locationLines = 1)

        assertFalse(result.showLocation)
    }

    @Test
    fun `正好放得下时不因像素误差丢掉一行`() {
        val result = plan(height = 119.7f, titleLines = 3)

        assertEquals(3, result.titleLines)
        assertTrue(result.titleComplete)
    }

    @Test
    fun `附注排在地点之后，前面没显示完就不显示`() {
        val cramped = plan(height = 140f, titleLines = 2, locationLines = 3, badgeHeight = 30f)
        assertFalse(cramped.showBadges)

        val roomy = plan(height = 200f, titleLines = 2, locationLines = 1, badgeHeight = 30f)
        assertTrue(roomy.showBadges)
    }

    @Test
    fun `不显示地点时附注直接排在课名下面`() {
        val result = courseCardTextPlan(
            availableHeight = 80f,
            titleLineBottoms = bottoms(1, 40f),
            locationLineBottoms = emptyList(),
            badgeHeight = 30f,
        )

        assertFalse(result.showLocation)
        assertTrue(result.locationComplete)
        assertTrue(result.showBadges)
    }

    @Test
    fun `再挤也要给课名留一行`() {
        val result = plan(height = 4f, titleLines = 5)

        assertEquals(1, result.titleLines)
    }

    @Test
    fun `格子里的地点在楼名和房间号之间补上连字符`() {
        assertEquals("@东-16-B-103", cardLocationText("@东16-B-103"))
        assertEquals("@实验东-5教-101", cardLocationText("@实验东5教101"))
        // 本来就隔开的不重复补
        assertEquals("@东-16-B-103", cardLocationText("@东-16-B-103"))
        assertEquals("@LA BB203", cardLocationText("@LA BB203"))
    }

    /** 假定每个字宽 10。 */
    private fun wrap(text: String, maxWidth: Int) =
        wrapByCharacter(text, maxWidth) { it.codePointCount(0, it.length) * 10 }

    @Test
    fun `地点按字符塞满一行再换行，不按词整块挪`() {
        // 一行放 6 个字：不会把「16-B-103」整块挪走、只留「@东-」在第一行
        assertEquals("@东-16-\nB-103", wrap("@东-16-B-103", maxWidth = 60))
    }

    @Test
    fun `一行放得下时原样返回`() {
        assertEquals("@东-16", wrap("@东-16", maxWidth = 60))
    }

    @Test
    fun `连一个字都放不下时每行至少一个字`() {
        assertEquals("东\n1", wrap("东1", maxWidth = 5))
    }

    @Test
    fun `高度不受限时全部显示`() {
        val result = plan(height = Float.MAX_VALUE, titleLines = 4, locationLines = 2, badgeHeight = 30f)

        assertTrue(result.titleComplete)
        assertTrue(result.locationComplete)
        assertTrue(result.showBadges)
    }
}
