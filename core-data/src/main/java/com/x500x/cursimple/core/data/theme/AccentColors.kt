package com.x500x.cursimple.core.data.theme

import com.x500x.cursimple.core.data.ThemeAccent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 自选主题色要用到的取色计算。
 *
 * 五套内置主题是手调的配色，自选色只有用户挑的一个颜色，其余的（按钮、容器底色、
 * 小组件卡片、通知卡片）都从它按固定的明度推出来。App 界面、小组件、上课提醒三处
 * 都走这里，同一个颜色在各处才对得上。都是纯计算，不依赖 Android，好测。
 */
object AccentColors {

    /** 第一次打开调色板时的颜色：一个不和内置五色撞的青色。 */
    const val DEFAULT_CUSTOM_ARGB: Int = 0xFF2E8B9A.toInt()

    /** 内置主题的主色，用来在不支持自定义着色的旧系统上找最接近的内置色。 */
    fun presetPrimary(accent: ThemeAccent): Int = when (accent) {
        ThemeAccent.Green -> 0xFF3FA277.toInt()
        ThemeAccent.Blue -> 0xFF3F6FB5.toInt()
        ThemeAccent.Purple -> 0xFF7259B5.toInt()
        ThemeAccent.Orange -> 0xFFD0763B.toInt()
        ThemeAccent.Pink -> 0xFFC25B7D.toInt()
        ThemeAccent.Custom -> DEFAULT_CUSTOM_ARGB
    }

    /** 色相最接近 [argb] 的内置主题色；灰得几乎没有色相时退回蓝色。 */
    fun nearestPreset(argb: Int): ThemeAccent {
        val (hue, saturation) = toHsl(argb)
        if (saturation < 0.08f) return ThemeAccent.Blue
        return ThemeAccent.entries
            .filter { it != ThemeAccent.Custom }
            .minBy { hueDistance(toHsl(presetPrimary(it))[0], hue) }
    }

    /**
     * 同一色相、指定明度的颜色。
     *
     * [maxSaturation] 压住饱和度：浅色底用太艳的颜色会刺眼，容器、卡片底都要压一压；
     * [minSaturation] 让挑了很灰的颜色时底色也还带点色相，不至于一片死灰。
     */
    fun tone(argb: Int, lightness: Float, maxSaturation: Float = 1f, minSaturation: Float = 0f): Int {
        val (hue, saturation) = toHsl(argb)
        val s = saturation.coerceIn(minSaturation.coerceAtMost(maxSaturation), maxSaturation)
        return fromHsl(hue, s, lightness.coerceIn(0f, 1f))
    }

    /**
     * 当主色用的那一档：把用户挑的颜色明度拉进浅色主题上白字看得清、
     * 深色主题上又不发灰的区间。挑的本来就合适时基本不动。
     */
    fun primary(argb: Int, dark: Boolean): Int {
        val (hue, saturation, lightness) = toHsl(argb)
        val target = if (dark) lightness.coerceIn(0.66f, 0.80f) else lightness.coerceIn(0.32f, 0.50f)
        return fromHsl(hue, saturation.coerceAtLeast(0.25f), target)
    }

    /** 挑的颜色不带透明度：全部按不透明处理。 */
    fun opaque(argb: Int): Int = argb or 0xFF000000.toInt()

    /** ARGB → [色相 0..360, 饱和度 0..1, 明度 0..1]。 */
    fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        val delta = maxC - minC
        if (delta == 0f) return floatArrayOf(0f, 0f, l)
        val s = delta / (1f - abs(2f * l - 1f))
        val h = when (maxC) {
            r -> 60f * (((g - b) / delta).mod(6f))
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    /** [色相, 饱和度, 明度] → 不透明的 ARGB。 */
    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val hPrime = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hPrime.mod(2f) - 1f))
        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = lightness - c / 2f
        fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
