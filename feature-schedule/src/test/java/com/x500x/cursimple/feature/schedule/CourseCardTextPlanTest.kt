package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程卡片里几行文字的取舍。
 *
 * 规矩只有一条：按优先级从上往下填，上一项没显示完就不显示下一项。
 * 课名被省略成「汇编语言与微型…」却还在下面挂着「@东…」，是两头不讨好——
 * 最要紧的没看全，又多出一行看不懂的残句。
 */
class CourseCardTextPlanTest {

    private fun plan(
        heightDp: Float,
        widthDp: Float = 48f,
        title: String = "高等数学",
        location: String = "东13-C-315",
        locationVisible: Boolean = true,
        hasBadges: Boolean = false,
    ) = courseCardTextPlan(
        availableHeightDp = heightDp,
        contentWidthDp = widthDp,
        title = title,
        titleFontSizeDp = 12f,
        titleLineHeightDp = 13f,
        location = location,
        locationFontSizeDp = 10f,
        locationLineHeightDp = 11f,
        locationVisible = locationVisible,
        hasBadges = hasBadges,
        badgeLineHeightDp = 11f,
    )

    @Test
    fun `空间够时课名与地点都显示`() {
        val result = plan(heightDp = 80f)

        assertTrue(result.titleComplete)
        assertTrue(result.showLocation)
    }

    @Test
    fun `课名没显示完就不显示地点`() {
        // 「计算机组成与系统结构」在窄格子里要折好几行，高度只够一部分
        val result = plan(heightDp = 30f, title = "计算机组成与系统结构")

        assertFalse("课名没显示完", result.titleComplete)
        assertFalse("这时候不该再显示地点", result.showLocation)
    }

    @Test
    fun `地点整段放不下就不显示，而不是露半截`() {
        // 课名一行放得下（13dp），剩 17dp；地点要两行 22dp，差一截
        val result = plan(heightDp = 30f, title = "高数", location = "东13-C-315教学楼")

        assertTrue(result.titleComplete)
        assertFalse("宁可不显示，也不要「@东…」这种残句", result.showLocation)
    }

    @Test
    fun `课名占满全部高度时一行地点都不给`() {
        val result = plan(heightDp = 26f, title = "计算机组成与系统结构")

        assertEquals(2, result.titleLines)
        assertFalse(result.showLocation)
    }

    @Test
    fun `关掉地点显示时只排课名`() {
        val result = plan(heightDp = 80f, locationVisible = false)

        assertTrue(result.titleComplete)
        assertFalse(result.showLocation)
    }

    @Test
    fun `附注排在地点之后，课名没显示完也不显示`() {
        val truncated = plan(heightDp = 30f, title = "计算机组成与系统结构", hasBadges = true)
        assertFalse(truncated.showBadges)

        val roomy = plan(heightDp = 100f, title = "高数", hasBadges = true)
        assertTrue(roomy.showBadges)
    }

    @Test
    fun `再挤也要给课名留一行`() {
        val result = plan(heightDp = 4f, title = "计算机组成与系统结构")

        assertEquals(1, result.titleLines)
    }

    @Test
    fun `高度未知时不做限制`() {
        val result = courseCardTextPlan(
            availableHeightDp = 0f,
            contentWidthDp = 48f,
            title = "高数",
            titleFontSizeDp = 12f,
            titleLineHeightDp = 13f,
            location = "东13",
            locationFontSizeDp = 10f,
            locationLineHeightDp = 11f,
            locationVisible = true,
            hasBadges = true,
            badgeLineHeightDp = 11f,
        )

        assertEquals(Int.MAX_VALUE, result.titleLines)
        assertTrue(result.showLocation)
    }

    @Test
    fun `中文按一个字宽、英数按半个字宽估行数`() {
        // 48dp 宽、12dp 字号：中文一行 4 个字
        assertEquals(1, estimatedTextLines("高等数学", fontSizeDp = 12f, widthDp = 48f))
        assertEquals(2, estimatedTextLines("高等数学分析", fontSizeDp = 12f, widthDp = 48f))
        // 英数各算半个字宽，一行放得下 8 个
        assertEquals(1, estimatedTextLines("MATLAB", fontSizeDp = 12f, widthDp = 48f))
    }
}
