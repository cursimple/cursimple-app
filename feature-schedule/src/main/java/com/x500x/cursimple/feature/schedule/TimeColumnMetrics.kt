package com.x500x.cursimple.feature.schedule

const val TIME_COLUMN_MAX_LABEL_CHARS: Float = 5f

const val TIME_COLUMN_MIN_LABEL_CHARS: Float = 3f

/** Estimate CJK/full-width glyphs at one unit and ASCII at 0.6 units. */
fun labelWidthInChars(label: String): Float =
    label.sumOf { char -> if (char.code < 0x2E80) 6 else 10 } / 10f

/** Bound the longest label width to preserve period text without consuming course columns. */
fun timeColumnLabelChars(labels: List<String>): Float = labels
    .maxOfOrNull(::labelWidthInChars)
    ?.coerceIn(TIME_COLUMN_MIN_LABEL_CHARS, TIME_COLUMN_MAX_LABEL_CHARS)
    ?: TIME_COLUMN_MIN_LABEL_CHARS
