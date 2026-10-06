package com.x500x.cursimple.core.plugin

import android.content.Context

/** Pure validation errors carry resource IDs and arguments for UI localization. */
class PluginArgumentException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/** State error with the same payload contract as [PluginArgumentException]. */
class PluginStateException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalStateException()

class PluginTextArg(val res: Int)

fun Context.pluginErrorText(error: Throwable): String? {
    val messageRes: Int
    val formatArgs: List<Any>
    when (error) {
        is PluginArgumentException -> {
            messageRes = error.messageRes
            formatArgs = error.formatArgs
        }

        is PluginStateException -> {
            messageRes = error.messageRes
            formatArgs = error.formatArgs
        }

        else -> return null
    }
    val args = formatArgs.map { renderArg(it) }
    return getString(messageRes, *args.toTypedArray())
}

private fun Context.renderArg(arg: Any): Any = when (arg) {
    is PluginTextArg -> getString(arg.res)
    is Throwable -> pluginErrorText(arg) ?: arg.message.orEmpty()
    else -> arg
}

internal fun Context.pluginErrorTextOr(error: Throwable, fallbackRes: Int): String =
    pluginErrorText(error)
        ?: error.message?.takeIf(String::isNotBlank)
        ?: getString(fallbackRes)

internal fun pluginReasonOr(error: Throwable, fallbackRes: Int): Throwable = when {
    error is PluginArgumentException || error is PluginStateException -> error
    !error.message.isNullOrBlank() -> error
    else -> PluginArgumentException(fallbackRes)
}

internal fun pluginError(messageRes: Int, vararg formatArgs: Any): Nothing =
    throw PluginArgumentException(messageRes, formatArgs.toList())

internal fun pluginRequire(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) pluginError(messageRes, *formatArgs)
}

internal fun <T : Any> pluginRequireNotNull(value: T?, messageRes: Int, vararg formatArgs: Any): T {
    if (value == null) pluginError(messageRes, *formatArgs)
    return value
}

internal fun pluginStateError(messageRes: Int, vararg formatArgs: Any): Nothing =
    throw PluginStateException(messageRes, formatArgs.toList())

internal fun pluginCheck(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) pluginStateError(messageRes, *formatArgs)
}
