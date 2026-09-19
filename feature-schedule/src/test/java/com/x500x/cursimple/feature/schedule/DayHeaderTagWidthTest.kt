package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表头小标签能不能在一列宽里显示完整。
 *
 * 「按10/10」是这行里最宽的一种：一个全角「按」加五个半角字符。它被省略成「按1…」
 * 就完全失去意义——看不出到底按的是哪天。这里按中日韩字体的常见宽度比例估算，
 * 把「缩到下限时应当放得下」这条约束钉住，改动字号下限或内边距时不会悄悄退化。
 */
class DayHeaderTagWidthTest {

    /** 全角字约占一个字号宽，半角数字与斜杠约占一半。 */
    private fun estimatedWidthDp(text: String, fontSizeSp: Float, fontScale: Float): Float {
        val fullWidthCount = text.count { it.code > 0x2E80 }
        val halfWidthCount = text.length - fullWidthCount
        return (fullWidthCount + halfWidthCount * 0.5f) * fontSizeSp * fontScale
    }

    /** 一列的可用宽度：列宽减去表头左右内边距；今天那一列还要再减胶囊的内边距。 */
    private fun availableWidthDp(columnWidthDp: Float, isToday: Boolean): Float {
        val headerPadding = 2f * 2
        val todayCapsulePadding = if (isToday) 3f * 2 else 0f
        return columnWidthDp - headerPadding - todayCapsulePadding
    }

    private val minTagFontSizeSp = 5f
    private val overrideTag = "按10/10"

    @Test
    fun `普通一列在标准字号下放得下按某天`() {
        // 360dp 屏、节次栏约 30dp、七列显示
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
        // 用户把系统字体拉到 1.3 倍，正是反馈里出现「按1…」的那种情形
        val columnWidth = (360f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1.3f)

        assertTrue(
            "大字号下缩到 ${minTagFontSizeSp}sp 仍应放得下，实测 $width dp",
            width <= availableWidthDp(columnWidth, isToday = true),
        )
    }

    @Test
    fun `窄屏七列时也放得下`() {
        // 320dp 的小屏，列宽只剩 41dp 出头
        val columnWidth = (320f - 30f) / 7f
        val width = estimatedWidthDp(overrideTag, minTagFontSizeSp, fontScale = 1f)

        assertTrue(
            "小屏同样不能截断，实测 $width dp / 可用 ${availableWidthDp(columnWidth, true)} dp",
            width <= availableWidthDp(columnWidth, isToday = true),
        )
    }
}
