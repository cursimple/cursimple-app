package com.x500x.cursimple.core.plugin

import org.junit.Assert.assertEquals

/** Assert resource IDs and arguments from plugin-layer exceptions only. */
internal fun pluginErrorOf(error: Throwable?): Pair<Int, List<Any>> = when (error) {
    is PluginArgumentException -> error.messageRes to error.formatArgs
    is PluginStateException -> error.messageRes to error.formatArgs
    else -> throw AssertionError("期望插件层错误，实际拿到 $error")
}

internal fun assertPluginError(expectedRes: Int, error: Throwable?, vararg expectedArgs: Any) {
    val (messageRes, formatArgs) = pluginErrorOf(error)
    assertEquals(expectedRes, messageRes)
    if (expectedArgs.isNotEmpty()) {
        assertEquals(expectedArgs.toList(), formatArgs)
    }
}
