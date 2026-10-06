package com.x500x.cursimple.app.webdav

import android.content.Context
import java.io.IOException

class WebDavTextArg(val res: Int)

/** Invalid configuration retains the [IllegalArgumentException] contract. */
class WebDavArgumentException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/** Transport failures retain the [IOException] contract. */
class WebDavRequestException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : IOException(cause)

fun Context.webDavErrorText(error: Throwable): String? {
    val res: Int
    val rawArgs: List<Any>
    when (error) {
        is WebDavArgumentException -> {
            res = error.messageRes
            rawArgs = error.formatArgs
        }

        is WebDavRequestException -> {
            res = error.messageRes
            rawArgs = error.formatArgs
        }

        else -> return null
    }
    val args = rawArgs.map { if (it is WebDavTextArg) getString(it.res) else it }
    return getString(res, *args.toTypedArray())
}

internal fun webDavError(messageRes: Int, vararg formatArgs: Any): Nothing =
    throw WebDavArgumentException(messageRes, formatArgs.toList())

internal fun webDavRequire(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) webDavError(messageRes, *formatArgs)
}
