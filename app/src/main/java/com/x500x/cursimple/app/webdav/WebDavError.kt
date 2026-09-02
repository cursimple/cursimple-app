package com.x500x.cursimple.app.webdav

import android.content.Context
import java.io.IOException

/** 参数本身也是一条待渲染的文案。 */
class WebDavTextArg(val res: Int)

/**
 * 配置或参数不合法。
 * 继承 [IllegalArgumentException]，与改用资源前 require 抛出的类型一致。
 */
class WebDavArgumentException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/**
 * 请求失败。
 * 继承 [IOException]，与改用资源前包装后抛出的类型一致，调用方的 catch 分支不受影响。
 */
class WebDavRequestException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : IOException(cause)

/** 能识别出原因时返回本地化文案，其余异常返回 null 交给调用方兜底。 */
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
