package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.PluginApiVersion

/** 按插件声明的接口版本判定的兼容性结论。 */
data class PluginCompatibility(
    val status: PluginCompatibilityStatus,
    val message: String?,
)

/**
 * [declaredApiVersion] 为 null 表示插件包没有声明接口版本，无法确认它是为当前平台构建的。
 * 声明的版本高于当前平台同样不能运行；低于当前平台的仍然受支持。
 */
fun resolvePluginCompatibility(declaredApiVersion: Int?): PluginCompatibility = when {
    declaredApiVersion == null -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        "插件包没有声明接口版本，无法确认与当前版本兼容",
    )
    declaredApiVersion <= 0 -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        "插件声明的接口版本无效",
    )
    declaredApiVersion > PluginApiVersion.CURRENT -> PluginCompatibility(
        PluginCompatibilityStatus.Incompatible,
        "插件需要接口版本 $declaredApiVersion，当前应用只支持到 ${PluginApiVersion.CURRENT}",
    )
    else -> PluginCompatibility(PluginCompatibilityStatus.Compatible, null)
}
