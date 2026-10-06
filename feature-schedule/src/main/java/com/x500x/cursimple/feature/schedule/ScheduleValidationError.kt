package com.x500x.cursimple.feature.schedule

import android.content.Context

/** Pure schedule validation carries resource IDs and arguments for UI localization. */
class ScheduleValidationException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

fun Context.scheduleValidationErrorText(error: Throwable): String? {
    val cause = error as? ScheduleValidationException ?: return null
    return getString(cause.messageRes, *cause.formatArgs.toTypedArray())
}

internal fun scheduleValidationRequire(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) throw ScheduleValidationException(messageRes, formatArgs.toList())
}
