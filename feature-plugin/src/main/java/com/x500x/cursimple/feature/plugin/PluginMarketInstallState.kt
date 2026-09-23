package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord

/** 市场里一个仓库相对本机的安装状态。 */
internal sealed interface PluginRepoInstallState {
    data object NotInstalled : PluginRepoInstallState

    /** 已装且与市场版本一致。 */
    data class Installed(val record: InstalledPluginRecord) : PluginRepoInstallState

    /** 已装但市场上有更新的版本。 */
    data class Updatable(val record: InstalledPluginRecord, val latestTag: String) : PluginRepoInstallState
}

internal val PluginRepoInstallState.installedRecord: InstalledPluginRecord?
    get() = when (this) {
        is PluginRepoInstallState.Installed -> record
        is PluginRepoInstallState.Updatable -> record
        PluginRepoInstallState.NotInstalled -> null
    }

/**
 * 按安装时记下的来源仓库匹配已装插件。
 *
 * 本次改动之前装的插件没有记来源，会被当成未安装；重装或更新一次即可正常显示。
 */
internal fun resolveRepoInstallState(
    repoSlug: String,
    latestTag: String?,
    installed: List<InstalledPluginRecord>,
): PluginRepoInstallState {
    val slug = repoSlug.trim().lowercase()
    val record = installed.firstOrNull { it.sourceRepo?.trim()?.lowercase() == slug }
        ?: return PluginRepoInstallState.NotInstalled
    val tag = latestTag?.trim().orEmpty()
    return if (tag.isNotEmpty() && isNewerVersion(tag, record.version)) {
        PluginRepoInstallState.Updatable(record, tag)
    } else {
        PluginRepoInstallState.Installed(record)
    }
}

/**
 * [candidate] 是不是比 [installed] 新。
 *
 * 市场的版本号带 v 前缀、插件清单里不带，先去掉；再按点分的数字逐段比，1.0.10 比 1.0.9 新。
 * 以前只比字符串是否相同，市场上的版本比本机旧（比如本地装了测试版）也会提示「可更新」，
 * 一点就降级了。解析不出数字时退回「不相同就算新」。
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    if (a == null || b == null) return normalizeVersion(candidate) != normalizeVersion(installed)
    for (index in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(index) { 0 }
        val right = b.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionParts(value: String): List<Int>? {
    val core = normalizeVersion(value).substringBefore('-').substringBefore('+')
    if (core.isBlank()) return null
    return core.split('.').map { it.toIntOrNull() ?: return null }
}

private fun normalizeVersion(value: String): String =
    value.trim().removePrefix("v").removePrefix("V")
