package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTitleFontSizeTest {

    @Test
    fun `short titles keep the configured size`() {
        assertEquals(13f, courseTitleFontSizeSp(13, "高数".length), 0.01f)
        assertEquals(13f, courseTitleFontSizeSp(13, "数据结构".length), 0.01f)
    }

    @Test
    fun `longer titles shrink step by step`() {
        val base = 13
        val seven = courseTitleFontSizeSp(base, "高级可编程逻辑".length)
        val eight = courseTitleFontSizeSp(base, "数据库原理及应用".length)
        val ten = courseTitleFontSizeSp(base, "计算机组成与系统结构".length)

        assertTrue("7 个字应比原字号小", seven < base)
        assertTrue("8 个字应比 7 个字更小", eight < seven)
        assertTrue("10 个字应比 8 个字更小", ten < eight)
    }

    @Test
    fun `shrinking stops before the text becomes unreadable`() {
        // 用户可以把字号调到很小，再按比例缩就糊了，得有下限
        assertEquals(9f, courseTitleFontSizeSp(9, 30), 0.01f)
        assertTrue(courseTitleFontSizeSp(10, 30) >= 9f)
    }

    @Test
    fun `a bigger configured size still yields a bigger result`() {
        val long = "计算机组成与系统结构".length
        assertTrue(courseTitleFontSizeSp(18, long) > courseTitleFontSizeSp(13, long))
    }
}
