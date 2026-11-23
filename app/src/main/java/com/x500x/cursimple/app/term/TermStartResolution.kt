package com.x500x.cursimple.app.term

import java.time.LocalDate
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber

/** Explicit user term dates, including clearing, take precedence over plugin defaults. */
fun resolveCanonicalTermStart(
    userDecided: Boolean,
    termStart: LocalDate?,
    pluginTermStart: LocalDate?,
    today: LocalDate? = null,
): LocalDate? {
    if (userDecided) return termStart
    // Expire legacy automatic defaults only; explicit historical terms remain untouched.
    val candidate = pluginTermStart ?: termStart
    if (candidate != null && today != null && resolveTermWeekNumber(candidate, today) > 30) return null
    if (pluginTermStart != null) return pluginTermStart
    return termStart
}

fun isTermStartFromPlugin(userDecided: Boolean, termStart: LocalDate?): Boolean =
    !userDecided && termStart != null
