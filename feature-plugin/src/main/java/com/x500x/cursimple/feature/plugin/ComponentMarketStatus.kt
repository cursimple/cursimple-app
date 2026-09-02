package com.x500x.cursimple.feature.plugin

import android.content.Context

/**
 * 组件市场页顶部状态条要展示的内容。
 * 带 detail/message 字段的成员承载底层抛出的原文，为空时由界面层换成兜底文案。
 */
sealed interface ComponentMarketStatus {
    /** 组件市场索引地址还没有配置。 */
    data object IndexUrlNotConfigured : ComponentMarketStatus

    /** 正在拉取远程组件索引。 */
    data object LoadingRemote : ComponentMarketStatus

    /** 索引拉取完成，共 [count] 个组件。 */
    data class RemoteLoaded(val count: Int) : ComponentMarketStatus

    /** 拉取远程组件索引失败，[error] 由界面层渲染。 */
    data class RemoteLoadFailed(val error: Throwable?) : ComponentMarketStatus

    /** 正在安装本地组件包。 */
    data object InstallingLocal : ComponentMarketStatus

    /** 选中的组件没有可下载地址。 */
    data object DownloadUrlMissing : ComponentMarketStatus

    /** 正在下载组件包。 */
    data object DownloadingPackage : ComponentMarketStatus

    /** 下载组件包失败，[error] 由界面层渲染。 */
    data class DownloadFailed(val error: Throwable?) : ComponentMarketStatus

    /** 正在安装下载回来的组件包。 */
    data object InstallingRemote : ComponentMarketStatus

    /** 已安装标识为 [componentId] 的组件。 */
    data class Installed(val componentId: String) : ComponentMarketStatus

    /** 安装失败，[error] 由界面层渲染。 */
    data class InstallFailed(val error: Throwable) : ComponentMarketStatus

    /** 读取本地组件包失败，[detail] 为异常原文。 */
    data class ReadPackageFailed(val detail: String?) : ComponentMarketStatus

    /** 本地组件包超过 [limitBytes] 字节的上限。 */
    data class PackageTooLarge(val limitBytes: Long) : ComponentMarketStatus
}

internal fun Context.componentMarketStatusText(status: ComponentMarketStatus): String = when (status) {
    ComponentMarketStatus.IndexUrlNotConfigured ->
        getString(R.string.plugin_component_market_index_not_configured)

    ComponentMarketStatus.LoadingRemote -> getString(R.string.plugin_component_market_loading)
    is ComponentMarketStatus.RemoteLoaded ->
        getString(R.string.plugin_component_market_loaded, status.count)

    is ComponentMarketStatus.RemoteLoadFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_component_market_load_failed)

    ComponentMarketStatus.InstallingLocal ->
        getString(R.string.plugin_component_market_installing_local)

    ComponentMarketStatus.DownloadUrlMissing ->
        getString(R.string.plugin_component_market_download_url_missing)

    ComponentMarketStatus.DownloadingPackage ->
        getString(R.string.plugin_component_market_downloading)

    is ComponentMarketStatus.DownloadFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_component_market_download_failed)

    ComponentMarketStatus.InstallingRemote ->
        getString(R.string.plugin_component_market_installing_remote)

    is ComponentMarketStatus.Installed ->
        getString(R.string.plugin_component_market_installed, status.componentId)

    is ComponentMarketStatus.InstallFailed ->
        pluginErrorDetail(status.error) ?: getString(R.string.plugin_component_market_install_failed)
    is ComponentMarketStatus.ReadPackageFailed ->
        status.detail ?: getString(R.string.plugin_market_read_component_package_failed)

    is ComponentMarketStatus.PackageTooLarge ->
        getString(R.string.plugin_package_too_large, status.limitBytes / BYTES_PER_MEGABYTE)
}

/** 本地选包失败时对应的状态，包体超限单独成一类，其余带上异常原文。 */
internal fun componentPackageReadFailure(error: Throwable): ComponentMarketStatus =
    when (error) {
        is PluginPackageTooLargeException -> ComponentMarketStatus.PackageTooLarge(error.limitBytes)
        else -> ComponentMarketStatus.ReadPackageFailed(error.message)
    }
