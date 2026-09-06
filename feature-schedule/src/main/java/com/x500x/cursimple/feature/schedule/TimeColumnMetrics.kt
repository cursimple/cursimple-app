package com.x500x.cursimple.feature.schedule

/** 节次列标签最多按几个字宽来定列宽，再长的标签走省略号。 */
const val TIME_COLUMN_MAX_LABEL_CHARS: Float = 5f

/** 节次列标签至少按几个字宽来定列宽，标签为空时也不至于窄到看不出是一列。 */
const val TIME_COLUMN_MIN_LABEL_CHARS: Float = 3f

/**
 * 一个标签占多少个字宽。
 *
 * 汉字与全角符号按一个字宽算，ASCII 字符窄得多，按六成算。
 */
fun labelWidthInChars(label: String): Float =
    label.sumOf { char -> if (char.code < 0x2E80) 6 else 10 } / 10f

/**
 * 一列节次标签需要多少个字宽才能不折行。
 *
 * 取最长的那个标签，夹在 [TIME_COLUMN_MIN_LABEL_CHARS] 与 [TIME_COLUMN_MAX_LABEL_CHARS] 之间：
 * 太窄会把「第一节」折成每行一个字，把下面的起止时间挤出行高；
 * 太宽则挤占课程列，超出上限的标签宁可省略。
 */
fun timeColumnLabelChars(labels: List<String>): Float = labels
    .maxOfOrNull(::labelWidthInChars)
    ?.coerceIn(TIME_COLUMN_MIN_LABEL_CHARS, TIME_COLUMN_MAX_LABEL_CHARS)
    ?: TIME_COLUMN_MIN_LABEL_CHARS
