package com.x500x.cursimple.core.plugin

import org.junit.Assert.assertEquals

/** 取出插件层错误携带的资源 id 与参数，不是插件层错误时直接判定失败。 */
internal fun pluginErrorOf(error: Throwable?): Pair<Int, List<Any>> = when (error) {
    is PluginArgumentException -> error.messageRes to error.formatArgs
    is PluginStateException -> error.messageRes to error.formatArgs
    else -> throw AssertionError("期望插件层错误，实际拿到 $error")
}

/** 断言错误用了指定的文案资源；给出 [expectedArgs] 时一并比较格式化参数。 */
internal fun assertPluginError(expectedRes: Int, error: Throwable?, vararg expectedArgs: Any) {
    val (messageRes, formatArgs) = pluginErrorOf(error)
    assertEquals(expectedRes, messageRes)
    if (expectedArgs.isNotEmpty()) {
        assertEquals(expectedArgs.toList(), formatArgs)
    }
}
