package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 日期表头的高度要跟着字号与系统字体缩放长，否则大字号下星期或调休那行会被切掉。 */
class DayHeaderHeightTest {

    private val density = Density(density = 3f, fontScale = 1f)

    @Test
    fun `small text keeps the header at its floor height`() {
        val height = dayHeaderHeight(density, headerTextSizeSp = 10, hasExtraLine = false)

        assertEquals(52.dp, height)
    }

    @Test
    fun `the makeup day line adds height instead of squeezing the date`() {
        val plain = dayHeaderHeight(density, headerTextSizeSp = 16, hasExtraLine = false)
        val withOverride = dayHeaderHeight(density, headerTextSizeSp = 16, hasExtraLine = true)

        assertTrue("调休那行要多占高度", withOverride > plain)
    }

    @Test
    fun `system font scaling grows the header too`() {
        val normal = dayHeaderHeight(density, headerTextSizeSp = 16, hasExtraLine = true)
        val scaled = dayHeaderHeight(
            Density(density = 3f, fontScale = 1.5f),
            headerTextSizeSp = 16,
            hasExtraLine = true,
        )

        assertTrue("字体放大后表头跟着长高", scaled > normal)
    }
}
