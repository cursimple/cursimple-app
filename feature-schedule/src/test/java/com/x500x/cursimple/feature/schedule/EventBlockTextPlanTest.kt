package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class EventBlockTextPlanTest {
    @Test
    fun `空间够时显示时间和完整标题`() {
        val plan = eventBlockTextPlan(availableHeight = 40f, timeHeight = 12f, titleLineBottoms = listOf(13f, 26f))

        assertEquals(EventBlockTextPlan(showTime = true, titleLines = 2), plan)
    }

    @Test
    fun `时间会挤掉标题时先藏时间`() {
        // 社团例会 wraps to two lines; with the time line the second line would only half show.
        val plan = eventBlockTextPlan(availableHeight = 30f, timeHeight = 12f, titleLineBottoms = listOf(13f, 26f))

        assertEquals(EventBlockTextPlan(showTime = false, titleLines = 2), plan)
    }

    @Test
    fun `藏了时间也放不全时只画放得下的整行`() {
        val plan = eventBlockTextPlan(availableHeight = 20f, timeHeight = 12f, titleLineBottoms = listOf(13f, 26f, 39f))

        assertEquals(EventBlockTextPlan(showTime = false, titleLines = 1), plan)
    }

    @Test
    fun `再挤也保留一行标题`() {
        val plan = eventBlockTextPlan(availableHeight = 8f, timeHeight = null, titleLineBottoms = listOf(13f))

        assertEquals(EventBlockTextPlan(showTime = false, titleLines = 1), plan)
    }
}
