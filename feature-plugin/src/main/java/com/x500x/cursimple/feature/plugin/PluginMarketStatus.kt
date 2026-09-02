package com.x500x.cursimple.feature.plugin

import android.content.Context
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.pluginErrorText
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus

/**
 * 插件市场页顶部状态条要展示的内容。
 * 带 detail/message 字段的成员承载底层抛出的原文，为空时由界面层换成兜底文案。
 */
sealed interface PluginMarketStatus {
    /** 注册表仓库还没有配置。 */
    data object RegistryNotConfigured : PluginMarketStatus

    /** 正在拉取注册表。 */
    data object LoadingMarket : PluginMarketStatus

    /** 注册表里一个插件也没有。 */
    data object MarketEmpty : PluginMarketStatus

    /** 注册表拉取完成，共 [count] 个插件。 */
    data class MarketLoaded(val count: Int) : PluginMarketStatus

    /** 拉取注册表失败，[error] 由界面层渲染。 */
    data class MarketLoadFailed(val error: Throwable?) : PluginMarketStatus

    /** 仓库 [repoSlug] 没有带 ZIP 资产的 Release。 */
    data class ReleaseAssetMissing(val repoSlug: String) : PluginMarketStatus

    /** 正在下载 [tagName] 版本里的资产 [assetName]。 */
    data class DownloadingAsset(val assetName: String, val tagName: String) : PluginMarketStatus

    /** 下载插件包失败，[error] 由界面层渲染。 */
    data class DownloadFailed(val error: Throwable?) : PluginMarketStatus

    /** 正在解析插件包。 */
    data object ParsingPackage : PluginMarketStatus

    /** 解析插件包失败，[error] 由界面层渲染。 */
    data class ParsePackageFailed(val error: Throwable?) : PluginMarketStatus

    /** 预检发现摘要校验未通过。 */
    data object PreviewChecksumRejected : PluginMarketStatus

    /** 预检发现签名校验未通过。 */
    data object PreviewSignatureRejected : PluginMarketStatus

    /** 预检通过，等待确认权限与站点。 */
    data object PreviewReady : PluginMarketStatus

    /** 正在安装插件。 */
    data object Installing : PluginMarketStatus

    /** 已安装名为 [name] 的插件。 */
    data class Installed(val name: String) : PluginMarketStatus

    /** 安装失败，[error] 由界面层渲染。 */
    data class InstallFailed(val error: Throwable) : PluginMarketStatus

    /** 已移除安装标识为 [pluginKey] 的插件。 */
    data class Removed(val pluginKey: String) : PluginMarketStatus

    /** 移除插件失败，[detail] 为异常原文。 */
    data class RemoveFailed(val detail: String?) : PluginMarketStatus

    /** 读取本地插件包失败，[detail] 为异常原文。 */
    data class ReadPackageFailed(val detail: String?) : PluginMarketStatus

    /** 本地插件包超过 [limitBytes] 字节的上限。 */
    data class PackageTooLarge(val limitBytes: Long) : PluginMarketStatus
}

/** 预检结束后展示在市场页顶部的状态。 */
internal fun installPreviewStatus(preview: PluginInstallPreview): PluginMarketStatus = when {
    !preview.checksumVerified -> PluginMarketStatus.PreviewChecksumRejected
    preview.signatureStatus == PluginSignatureStatus.Invalid -> PluginMarketStatus.PreviewSignatureRejected
    else -> PluginMarketStatus.PreviewReady
}

internal fun Context.pluginMarketStatusText(status: PluginMarketStatus): String = when (status) {
    PluginMarketStatus.RegistryNotConfigured ->
        getString(R.string.plugin_market_status_registry_not_configured)

    PluginMarketStatus.LoadingMarket -> getString(R.string.plugin_market_status_loading)
    PluginMarketStatus.MarketEmpty -> getString(R.string.plugin_market_status_market_empty)
    is PluginMarketStatus.MarketLoaded ->
        getString(R.string.plugin_market_status_market_loaded, status.count)

    is PluginMarketStatus.MarketLoadFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_market_status_load_failed)

    is PluginMarketStatus.ReleaseAssetMissing ->
        getString(R.string.plugin_market_status_release_asset_missing, status.repoSlug)

    is PluginMarketStatus.DownloadingAsset -> getString(
        R.string.plugin_market_status_downloading_asset,
        status.assetName,
        status.tagName,
    )

    is PluginMarketStatus.DownloadFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_market_status_download_failed)

    PluginMarketStatus.ParsingPackage -> getString(R.string.plugin_market_status_parsing_package)
    is PluginMarketStatus.ParsePackageFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_market_status_parse_package_failed)

    PluginMarketStatus.PreviewChecksumRejected ->
        getString(R.string.plugin_market_status_preview_checksum_rejected)

    PluginMarketStatus.PreviewSignatureRejected ->
        getString(R.string.plugin_market_status_preview_signature_rejected)

    PluginMarketStatus.PreviewReady -> getString(R.string.plugin_market_status_preview_ready)
    PluginMarketStatus.Installing -> getString(R.string.plugin_market_status_installing)
    is PluginMarketStatus.Installed ->
        getString(R.string.plugin_market_status_installed, status.name)

    is PluginMarketStatus.InstallFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_market_status_install_failed)
    is PluginMarketStatus.Removed -> getString(R.string.plugin_market_status_removed, status.pluginKey)
    is PluginMarketStatus.RemoveFailed ->
        status.detail ?: getString(R.string.plugin_market_status_remove_failed)

    is PluginMarketStatus.ReadPackageFailed ->
        status.detail ?: getString(R.string.plugin_market_read_plugin_package_failed)

    is PluginMarketStatus.PackageTooLarge ->
        getString(R.string.plugin_package_too_large, status.limitBytes / BYTES_PER_MEGABYTE)
}

/** 插件层的异常先按当前语言渲染，其余异常退回原文。 */
internal fun Context.pluginErrorDetail(error: Throwable?): String? {
    val cause = error ?: return null
    return pluginErrorText(cause) ?: cause.message?.takeIf(String::isNotBlank)
}

/** 本地选包失败时对应的状态，包体超限单独成一类，其余带上异常原文。 */
internal fun pluginPackageReadFailure(error: Throwable): PluginMarketStatus =
    when (error) {
        is PluginPackageTooLargeException -> PluginMarketStatus.PackageTooLarge(error.limitBytes)
        else -> PluginMarketStatus.ReadPackageFailed(error.message)
    }
