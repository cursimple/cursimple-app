package com.x500x.cursimple.feature.plugin

import android.content.Context
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginCompatibility
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.install.pluginCompatibilityText
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import com.x500x.cursimple.core.plugin.pluginErrorText
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus
import kotlinx.coroutines.CancellationException

/** Count actual bytes; null [totalBytes] denotes an unknown total. */
data class PluginDownloadProgress(val downloadedBytes: Long, val totalBytes: Long?)

/** Source errors are independent of download state and do not hide other sources. */
data class PluginMarketSourceError(
    val source: String,
    val kind: MarketSourceKind,
    val error: Throwable,
    val check: MarketSourceCheck? = null,
)

/** Typed market status with optional detail and localized fallback. */
sealed interface PluginMarketStatus {
    data object RegistryNotConfigured : PluginMarketStatus

    data object LoadingMarket : PluginMarketStatus

    data class CheckingUpdate(val name: String) : PluginMarketStatus
    data class CheckingInstallRelease(val name: String) : PluginMarketStatus

    data object MarketEmpty : PluginMarketStatus

    data class MarketLoaded(val count: Int) : PluginMarketStatus

    /** Registry fetch failure rendered by the UI. */
    data class MarketLoadFailed(val error: Throwable?) : PluginMarketStatus

    data class ReleaseAssetMissing(val repoSlug: String) : PluginMarketStatus

    data class DownloadingAsset(val assetName: String, val tagName: String) : PluginMarketStatus

    /** Plugin package download failure rendered by the UI. */
    data class DownloadFailed(val error: Throwable?) : PluginMarketStatus

    data object ParsingPackage : PluginMarketStatus

    /** Package parsing failure rendered by the UI. */
    data class ParsePackageFailed(val error: Throwable?) : PluginMarketStatus

    data object PreviewChecksumRejected : PluginMarketStatus

    data object PreviewSignatureRejected : PluginMarketStatus
    data class PreviewIncompatible(val compatibility: PluginCompatibility) : PluginMarketStatus

    /** Preview passed; awaiting permission and host confirmation. */
    data object PreviewReady : PluginMarketStatus

    data object Installing : PluginMarketStatus

    data class Installed(val name: String) : PluginMarketStatus

    /** Installation failure rendered by the UI. */
    data class InstallFailed(val error: Throwable) : PluginMarketStatus

    data class Removed(val pluginKey: String) : PluginMarketStatus

    /** Removal failure with raw [detail]. */
    data class RemoveFailed(val detail: String?) : PluginMarketStatus

    /** Local package read failure with raw [detail]. */
    data class ReadPackageFailed(val detail: String?) : PluginMarketStatus

    /** Local package exceeds [limitBytes]. */
    data class PackageTooLarge(val limitBytes: Long) : PluginMarketStatus
}

internal fun installPreviewStatus(preview: PluginInstallPreview): PluginMarketStatus = when {
    !preview.checksumVerified -> PluginMarketStatus.PreviewChecksumRejected
    preview.signatureStatus == PluginSignatureStatus.Invalid -> PluginMarketStatus.PreviewSignatureRejected
    preview.compatibility.status == PluginCompatibilityStatus.Incompatible -> PluginMarketStatus.PreviewIncompatible(preview.compatibility)
    else -> PluginMarketStatus.PreviewReady
}

internal fun Context.pluginMarketStatusText(status: PluginMarketStatus): String = when (status) {
    PluginMarketStatus.RegistryNotConfigured ->
        getString(R.string.plugin_market_status_registry_not_configured)

    PluginMarketStatus.LoadingMarket -> getString(R.string.plugin_market_status_loading)
    is PluginMarketStatus.CheckingUpdate -> getString(R.string.plugin_market_status_checking_update, status.name)
    is PluginMarketStatus.CheckingInstallRelease -> getString(R.string.plugin_market_status_checking_install, status.name)
    PluginMarketStatus.MarketEmpty -> getString(R.string.plugin_market_status_market_empty)
    is PluginMarketStatus.MarketLoaded ->
        resources.getQuantityString(
            R.plurals.plugin_market_status_market_loaded,
            status.count,
            status.count,
        )

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

    is PluginMarketStatus.PreviewIncompatible -> pluginCompatibilityText(status.compatibility)
        ?: getString(R.string.plugin_catalog_incompatible)

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

internal fun Context.pluginErrorDetail(error: Throwable?): String? {
    val cause = error ?: return null
    return pluginErrorText(cause) ?: cause.message?.takeIf(String::isNotBlank)
}

/** Separate oversized local imports from other read failures. */
internal fun pluginPackageReadFailure(error: Throwable): PluginMarketStatus =
    when (error) {
        is CancellationException -> throw error
        is PluginPackageTooLargeException -> PluginMarketStatus.PackageTooLarge(error.limitBytes)
        else -> PluginMarketStatus.ReadPackageFailed(error.message)
    }
