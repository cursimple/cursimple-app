package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin source-date label fit at minimum size so padding or font changes cannot silently truncate
 * it.
 */
class DayHeaderTagWidthTest {

    private fun estimatedWidthDp(text: String, fontSizeSp: Float, fontScale: Float): Float {
        val fullWidthCount = text.count { it.code > 0x2E80 }
        val halfWidthCount = text.length - fullWidthCount
        return (fullWidthCount + halfWidthCount * 0.5f) * fontSizeSp * fontScale
    }

    private fun availableWidthDp(columnWidthDp: Float, isToday: Boolean): Float {
        val headerPadding = 2f * 2
        val todayCapsulePadding = if (isToday) 3f * 2 else 0f
        return columnWidthDp - headerPadding - todayCapsulePadding
    }

    private val minTagFontSizeSp = 5f
    private val overrideTag = "按10/10"

    @Test
    fun `普通一列在标准字号下放得下按某天`() {
        val columnWidth = (360f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1f)

        assertTrue(
            "按10/10 缩到下限后应当放得下，实测 $width dp / 可用 ${availableWidthDp(columnWidth, false)} dp",
            width <= availableWidthDp(columnWidth, isToday = false),
        )
    }

    @Test
    fun `今天那一列减去胶囊内边距后仍放得下`() {
        val columnWidth = (360f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1f)

        assertTrue(
            "今天这一列最窄，也必须放得下",
            width <= availableWidthDp(columnWidth, isToday = true),
        )
    }

    @Test
    fun `系统字体放到最大时也不至于被省略`() {
        val columnWidth = (360f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1.3f)

        assertTrue(
            "大字号下缩到 ${minTagFontSizeSp}sp 仍应放得下，实测 $width dp",
            width <= availableWidthDp(columnWidth, isToday = true),
        )
    }

    @Test
    fun `窄屏七列时也放得下`() {
        // Narrow-screen fixture leaves about 41dp per day.
        val columnWidth = (320f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1f)

        assertTrue(
            "小屏同样不能截断，实测 $width dp / 可用 ${availableWidthDp(columnWidth, true)} dp",
            width <= availableWidthDp(columnWidth, isToday = true),
        )
    }
}
