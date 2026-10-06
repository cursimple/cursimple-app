package com.x500x.cursimple.feature.widget

/** Include content digest in adapter data URI so launcher caches cannot retain stale rows. */
internal fun widgetListRevision(vararg parts: Any?): String =
    parts.joinToString(separator = "|") { it?.toString().orEmpty() }
        .hashCode()
        .toUInt()
        .toString(16)

internal const val EXTRA_WIDGET_LIST_REVISION = "widget_list_revision"
