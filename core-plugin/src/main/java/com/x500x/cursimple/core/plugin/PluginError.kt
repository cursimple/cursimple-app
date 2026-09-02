package com.x500x.cursimple.core.plugin

import android.content.Context

/**
 * 插件流程里不合法的输入。
 * 校验跑在拿不到 Context 的纯函数里，这里只携带资源 id 与参数，文字由界面层按当前语言渲染。
 */
class PluginArgumentException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/** 插件流程里不合法的状态，携带的内容与 [PluginArgumentException] 相同。 */
class PluginStateException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalStateException()

/** 参数本身也是一条待渲染的文案。 */
class PluginTextArg(val res: Int)

/** 能识别出原因时返回本地化文案，其余异常返回 null 交给调用方兜底。 */
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

/** 嵌套的资源 id 与异常先渲染成文字，其余参数原样交给格式化。 */
private fun Context.renderArg(arg: Any): Any = when (arg) {
    is PluginTextArg -> getString(arg.res)
    is Throwable -> pluginErrorText(arg) ?: arg.message.orEmpty()
    else -> arg
}

/** 先取本地化文案，再退回异常原文，最后用 [fallbackRes] 兜底。 */
internal fun Context.pluginErrorTextOr(error: Throwable, fallbackRes: Int): String =
    pluginErrorText(error)
        ?: error.message?.takeIf(String::isNotBlank)
        ?: getString(fallbackRes)

/** 异常自身没有可读原因时换成 [fallbackRes] 对应的文案。 */
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
