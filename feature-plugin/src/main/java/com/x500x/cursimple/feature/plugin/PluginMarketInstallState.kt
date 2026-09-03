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
    return when {
        tag.isEmpty() -> PluginRepoInstallState.Installed(record)
        isSameVersion(record.version, tag) -> PluginRepoInstallState.Installed(record)
        else -> PluginRepoInstallState.Updatable(record, tag)
    }
}

/** 市场的版本号带 v 前缀，插件清单里不带，比较前统一去掉。 */
private fun isSameVersion(installedVersion: String, marketTag: String): Boolean =
    normalizeVersion(installedVersion) == normalizeVersion(marketTag)

private fun normalizeVersion(value: String): String =
    value.trim().removePrefix("v").removePrefix("V")
