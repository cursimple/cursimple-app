package com.x500x.cursimple.app.ai

import android.content.Context

/** Pure parsing carries resource IDs and arguments; the UI localizes errors. */
class AiImportException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

class AiImportTextArg(val res: Int)

fun Context.aiImportErrorText(error: Throwable): String? {
    val cause = error as? AiImportException ?: return null
    val args = cause.formatArgs.map { if (it is AiImportTextArg) getString(it.res) else it }
    return getString(cause.messageRes, *args.toTypedArray())
}

internal fun aiImportError(messageRes: Int, vararg formatArgs: Any): Nothing =
    throw AiImportException(messageRes, formatArgs.toList())

internal fun aiImportRequire(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) aiImportError(messageRes, *formatArgs)
}
