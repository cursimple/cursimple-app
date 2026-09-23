package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.data.theme.AccentColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 自选主题色的取色计算：HSL 来回换算、推出的各档颜色、旧系统上找最接近的内置色。 */
class AccentColorsTest {

    private fun channelsClose(a: Int, b: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 1 }

    @Test
    fun `HSL 来回换算不走样`() {
        listOf(0xFF3FA277, 0xFF3F6FB5, 0xFFD0763B, 0xFF123456, 0xFFFFFFFF, 0xFF000000).forEach { raw ->
            val argb = raw.toInt()
            val (h, s, l) = AccentColors.toHsl(argb)
            assertTrue("%08X".format(argb), channelsClose(argb, AccentColors.fromHsl(h, s, l)))
        }
    }

    @Test
    fun `推出的颜色按要求的明度走，色相不变`() {
        val seed = 0xFF3F6FB5.toInt()
        val light = AccentColors.tone(seed, 0.90f, maxSaturation = 0.45f)
        val (h0) = AccentColors.toHsl(seed)
        val (h1, s1, l1) = AccentColors.toHsl(light)
        assertEquals(h0, h1, 2f)
        assertEquals(0.90f, l1, 0.01f)
        assertTrue(s1 <= 0.46f)
    }

    @Test
    fun `主色明度拉进白字看得清的区间`() {
        val pale = 0xFFB8E0FF.toInt()
        val (_, _, lightL) = AccentColors.toHsl(AccentColors.primary(pale, dark = false))
        val (_, _, darkL) = AccentColors.toHsl(AccentColors.primary(0xFF102040.toInt(), dark = true))
        assertTrue(lightL <= 0.51f)
        assertTrue(darkL >= 0.65f)
    }

    @Test
    fun `旧系统上按色相找最接近的内置色`() {
        assertEquals(ThemeAccent.Green, AccentColors.nearestPreset(0xFF2E9E6A.toInt()))
        assertEquals(ThemeAccent.Blue, AccentColors.nearestPreset(0xFF1E88E5.toInt()))
        assertEquals(ThemeAccent.Orange, AccentColors.nearestPreset(0xFFE67E22.toInt()))
        assertEquals(ThemeAccent.Pink, AccentColors.nearestPreset(0xFFD81B60.toInt()))
        // 几乎没有色相的灰，退回蓝色
        assertEquals(ThemeAccent.Blue, AccentColors.nearestPreset(0xFF808080.toInt()))
    }

    @Test
    fun `挑的颜色一律按不透明存`() {
        assertEquals(0xFF123456.toInt(), AccentColors.opaque(0x00123456))
    }
}
