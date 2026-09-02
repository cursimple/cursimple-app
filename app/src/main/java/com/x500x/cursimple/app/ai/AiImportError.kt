package com.x500x.cursimple.app.ai

import android.content.Context

/**
 * 识图导入失败的原因。
 * 解析过程跑在没有 Context 的纯函数里，这里只携带资源 id 与参数，文字由界面层按当前语言渲染。
 */
class AiImportException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/** 参数本身也是一条待渲染的文案，例如出错位置来自内置分组名而不是模型返回的键。 */
class AiImportTextArg(val res: Int)

/** 能识别出原因时返回本地化文案，其余异常返回 null 交给调用方兜底。 */
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
