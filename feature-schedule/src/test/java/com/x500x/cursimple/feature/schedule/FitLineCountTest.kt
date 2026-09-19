package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课名只显示放得下的整行。
 *
 * 之前不限行数、超出部分直接裁切，最后一行会被从字中间切开：露出小半个字既认不出来，
 * 又白占一行高度。
 */
class FitLineCountTest {

    @Test
    fun `正好放得下几行就是几行`() {
        assertEquals(3, fitLineCount(availableHeightDp = 36f, lineHeightDp = 12f))
    }

    @Test
    fun `放不下的那半行不算`() {
        // 42 / 12 = 3.5：第四行只露一半，不如不显示
        assertEquals(3, fitLineCount(availableHeightDp = 42f, lineHeightDp = 12f))
    }

    @Test
    fun `再挤也要留一行`() {
        // 一行都放不下时仍给一行，否则这一格什么都看不到
        assertEquals(1, fitLineCount(availableHeightDp = 5f, lineHeightDp = 12f))
        assertEquals(1, fitLineCount(availableHeightDp = 0f, lineHeightDp = 12f))
    }

    @Test
    fun `高度不受限时不限制行数`() {
        assertEquals(Int.MAX_VALUE, fitLineCount(availableHeightDp = Float.POSITIVE_INFINITY, lineHeightDp = 12f))
    }

    @Test
    fun `行高非法时不做限制而不是崩掉`() {
        assertEquals(Int.MAX_VALUE, fitLineCount(availableHeightDp = 40f, lineHeightDp = 0f))
    }
}
