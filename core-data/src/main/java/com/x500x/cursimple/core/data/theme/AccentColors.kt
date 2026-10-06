package com.x500x.cursimple.core.data.theme

import com.x500x.cursimple.core.data.ThemeAccent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure shared color derivation keeps app, widget and notification palettes consistent. */
object AccentColors {

    const val DEFAULT_CUSTOM_ARGB: Int = 0xFF2E8B9A.toInt()

    /** Built-in accent references for older systems without runtime tinting. */
    fun presetPrimary(accent: ThemeAccent): Int = when (accent) {
        ThemeAccent.Green -> 0xFF3FA277.toInt()
        ThemeAccent.Blue -> 0xFF3F6FB5.toInt()
        ThemeAccent.Purple -> 0xFF7259B5.toInt()
        ThemeAccent.Orange -> 0xFFD0763B.toInt()
        ThemeAccent.Pink -> 0xFFC25B7D.toInt()
        ThemeAccent.Custom -> DEFAULT_CUSTOM_ARGB
    }

    fun nearestPreset(argb: Int): ThemeAccent {
        val (hue, saturation) = toHsl(argb)
        if (saturation < 0.08f) return ThemeAccent.Blue
        return ThemeAccent.entries
            .filter { it != ThemeAccent.Custom }
            .minBy { hueDistance(toHsl(presetPrimary(it))[0], hue) }
    }

    /**
     * Preserve hue at the requested lightness; saturation bounds maintain readable surfaces.
     */
    fun tone(argb: Int, lightness: Float, maxSaturation: Float = 1f, minSaturation: Float = 0f): Int {
        val (hue, saturation) = toHsl(argb)
        val s = saturation.coerceIn(minSaturation.coerceAtMost(maxSaturation), maxSaturation)
        return fromHsl(hue, s, lightness.coerceIn(0f, 1f))
    }

    /** Adjust primary lightness for contrast in light and dark themes. */
    fun primary(argb: Int, dark: Boolean): Int {
        val (hue, saturation, lightness) = toHsl(argb)
        val target = if (dark) lightness.coerceIn(0.66f, 0.80f) else lightness.coerceIn(0.32f, 0.50f)
        return fromHsl(hue, saturation.coerceAtLeast(0.25f), target)
    }

    fun opaque(argb: Int): Int = argb or 0xFF000000.toInt()

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
