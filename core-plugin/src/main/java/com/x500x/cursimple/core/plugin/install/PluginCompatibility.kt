package com.x500x.cursimple.core.plugin.install

import android.content.Context
import com.x500x.cursimple.core.plugin.PluginApiVersion
import com.x500x.cursimple.core.plugin.R

/** 按插件声明的接口版本判定的兼容性结论，[messageRes] 为空表示兼容。 */
data class PluginCompatibility(
    val status: PluginCompatibilityStatus,
    val messageRes: Int?,
    val messageArgs: List<Any> = emptyList(),
)

/** 兼容时返回 null。 */
fun Context.pluginCompatibilityText(compatibility: PluginCompatibility): String? {
    val messageRes = compatibility.messageRes ?: return null
    return getString(messageRes, *compatibility.messageArgs.toTypedArray())
}

/**
 * [declaredApiVersion] 为 null 表示插件包没有声明接口版本，无法确认它是为当前平台构建的。
 * 声明的版本高于当前平台同样不能运行；低于当前平台的仍然受支持。
 */
fun resolvePluginCompatibility(declaredApiVersion: Int?): PluginCompatibility = when {
    declaredApiVersion == null -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        R.string.plugin_error_compatibility_api_undeclared,
    )
    declaredApiVersion <= 0 -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        R.string.plugin_error_compatibility_api_invalid,
    )
    declaredApiVersion > PluginApiVersion.CURRENT -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        R.string.plugin_error_compatibility_api_too_new,
        listOf(declaredApiVersion, PluginApiVersion.CURRENT),
    )
    else -> PluginCompatibility(PluginCompatibilityStatus.Compatible, null)
}
