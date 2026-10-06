package com.x500x.cursimple.app.term

import java.time.LocalDate

/** Explicit user term dates, including clearing, take precedence over plugin defaults. */
fun resolveCanonicalTermStart(
    userDecided: Boolean,
    termStart: LocalDate?,
    pluginTermStart: LocalDate?,
): LocalDate? = if (userDecided) termStart else termStart ?: pluginTermStart

fun isTermStartFromPlugin(userDecided: Boolean, termStart: LocalDate?): Boolean =
    !userDecided && termStart != null
