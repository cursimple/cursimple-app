package com.x500x.cursimple.feature.widget

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan

internal const val END_WARNING_FINAL_COLOR = 0xFFD93B4B.toInt()
internal const val END_WARNING_NEAR_COLOR = 0xFFC98A12.toInt()

/**
 * Appends a "(1) Last week" style entry. The circled digit is drawn larger and bold so its disc reads as the
 * same weight as the round corner badge on the course cards instead of a thin outline at legend size.
 */
internal fun SpannableStringBuilder.appendEndWarningEntry(context: Context, final: Boolean) {
    val start = length
    append(if (final) "\u2776" else "\u2777")
    val color = if (final) END_WARNING_FINAL_COLOR else END_WARNING_NEAR_COLOR
    setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    setSpan(RelativeSizeSpan(1.45f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    append(" ").append(context.getString(if (final) R.string.widget_legend_end_final else R.string.widget_legend_end_near))
}

/** Legend explaining the course-ending markers; null when none is on screen. */
internal fun endWarningLegendText(context: Context, final: Boolean, near: Boolean): CharSequence? {
    if (!final && !near) return null
    return SpannableStringBuilder().apply {
        if (final) appendEndWarningEntry(context, true)
        if (final && near) append("   ")
        if (near) appendEndWarningEntry(context, false)
    }
}
