package com.x500x.cursimple.feature.schedule

import android.content.Context

/**
 * 插件课表校验失败的原因。
 * 校验跑在没有 Context 的纯函数里，这里只携带资源 id 与参数，文字由界面层按当前语言渲染。
 */
class ScheduleValidationException(
    val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
) : IllegalArgumentException()

/** 能识别出原因时返回本地化文案，其余异常返回 null 交给调用方兜底。 */
fun Context.scheduleValidationErrorText(error: Throwable): String? {
    val cause = error as? ScheduleValidationException ?: return null
    return getString(cause.messageRes, *cause.formatArgs.toTypedArray())
}

internal fun scheduleValidationRequire(value: Boolean, messageRes: Int, vararg formatArgs: Any) {
    if (!value) throw ScheduleValidationException(messageRes, formatArgs.toList())
}
