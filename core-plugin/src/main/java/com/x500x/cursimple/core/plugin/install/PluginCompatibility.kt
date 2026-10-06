package com.x500x.cursimple.core.plugin.install

import android.content.Context
import com.x500x.cursimple.core.plugin.PluginApiVersion
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.manifest.PluginManifest

/** Null [messageRes] indicates API compatibility. */
data class PluginCompatibility(
    val status: PluginCompatibilityStatus,
    val messageRes: Int?,
    val messageArgs: List<Any> = emptyList(),
)

/** Null when compatible. */
fun Context.pluginCompatibilityText(compatibility: PluginCompatibility): String? {
    val messageRes = compatibility.messageRes ?: return null
    return getString(messageRes, *compatibility.messageArgs.toTypedArray())
}

/** Reject undeclared or newer API versions; older supported versions remain compatible. */
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

/** Read the installed APK version, never a value supplied by a bundle. */
@Suppress("DEPRECATION")
fun Context.pluginHostVersion(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}.getOrDefault("")

fun resolvePluginCompatibility(manifest: PluginManifest, hostVersion: String): PluginCompatibility {
    val api = resolvePluginCompatibility(manifest.apiVersion)
    return if (api.status != PluginCompatibilityStatus.Compatible) api
        else resolveHostVersionCompatibility(manifest.minHostVersion, hostVersion)
}

fun resolveHostVersionCompatibility(minHostVersion: String, hostVersion: String): PluginCompatibility {
    val minimum = HostVersion.parse(minHostVersion) ?: return PluginCompatibility(
        PluginCompatibilityStatus.Incompatible, R.string.plugin_error_compatibility_host_invalid,
    )
    val current = HostVersion.parse(hostVersion.removeSuffix("-ci")) ?: return PluginCompatibility(
        PluginCompatibilityStatus.Incompatible, R.string.plugin_error_compatibility_host_unknown,
    )
    return if (current < minimum) PluginCompatibility(
        PluginCompatibilityStatus.Incompatible, R.string.plugin_error_compatibility_host_too_old,
        listOf(minHostVersion, hostVersion),
    ) else PluginCompatibility(PluginCompatibilityStatus.Compatible, null)
}

internal fun requirePluginCompatibility(compatibility: PluginCompatibility) {
    if (compatibility.status != PluginCompatibilityStatus.Compatible) throw PluginArgumentException(
        compatibility.messageRes ?: R.string.plugin_error_incompatible_platform, compatibility.messageArgs,
    )
}

private data class HostVersion(val numbers: List<Long>, val prerelease: List<String>) : Comparable<HostVersion> {
    override fun compareTo(other: HostVersion): Int {
        numbers.indices.forEach { index -> numbers[index].compareTo(other.numbers[index]).takeIf { it != 0 }?.let { return it } }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) return when {
            prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
            prerelease.isEmpty() -> 1
            else -> -1
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[index]; val b = other.prerelease[index]
            val numericA = a.all(Char::isDigit); val numericB = b.all(Char::isDigit)
            val compared = when {
                numericA && numericB -> {
                    val left = a.trimStart('0').ifEmpty { "0" }; val right = b.trimStart('0').ifEmpty { "0" }
                    left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)
                }
                numericA -> -1
                numericB -> 1
                else -> a.compareTo(b)
            }
            if (compared != 0) return compared
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        fun parse(value: String): HostVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val numbers = (1..3).map { index -> (match.groupValues[index].ifEmpty { "0" }).toLongOrNull() ?: return null }
            return HostVersion(numbers, match.groupValues[4].takeIf(String::isNotEmpty)?.split('.').orEmpty())
        }
    }
}
