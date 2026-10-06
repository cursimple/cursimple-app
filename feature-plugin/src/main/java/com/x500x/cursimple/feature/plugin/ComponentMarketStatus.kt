package com.x500x.cursimple.feature.plugin

import android.content.Context

/** Typed component-market status with optional raw detail and localized fallback. */
sealed interface ComponentMarketStatus {
    data object IndexUrlNotConfigured : ComponentMarketStatus

    data object LoadingRemote : ComponentMarketStatus

    data class RemoteLoaded(val count: Int) : ComponentMarketStatus

    /** Remote index failure rendered by the UI. */
    data class RemoteLoadFailed(val error: Throwable?) : ComponentMarketStatus

    data object InstallingLocal : ComponentMarketStatus

    data object DownloadUrlMissing : ComponentMarketStatus

    data object DownloadingPackage : ComponentMarketStatus

    /** Package download failure rendered by the UI. */
    data class DownloadFailed(val error: Throwable?) : ComponentMarketStatus

    data object InstallingRemote : ComponentMarketStatus

    data class Installed(val componentId: String) : ComponentMarketStatus

    /** Installation failure rendered by the UI. */
    data class InstallFailed(val error: Throwable) : ComponentMarketStatus

    /** Local package read failure with raw [detail]. */
    data class ReadPackageFailed(val detail: String?) : ComponentMarketStatus

    /** Local package exceeds [limitBytes]. */
    data class PackageTooLarge(val limitBytes: Long) : ComponentMarketStatus
}

internal fun Context.componentMarketStatusText(status: ComponentMarketStatus): String = when (status) {
    ComponentMarketStatus.IndexUrlNotConfigured ->
        getString(R.string.plugin_component_market_index_not_configured)

    ComponentMarketStatus.LoadingRemote -> getString(R.string.plugin_component_market_loading)
    is ComponentMarketStatus.RemoteLoaded ->
        resources.getQuantityString(
            R.plurals.plugin_component_market_loaded,
            status.count,
            status.count,
        )

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

/** Classify oversized local packages separately from other read failures. */
internal fun componentPackageReadFailure(error: Throwable): ComponentMarketStatus =
    when (error) {
        is PluginPackageTooLargeException -> ComponentMarketStatus.PackageTooLarge(error.limitBytes)
        else -> ComponentMarketStatus.ReadPackageFailed(error.message)
    }
