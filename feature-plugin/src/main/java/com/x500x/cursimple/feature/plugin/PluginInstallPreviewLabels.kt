package com.x500x.cursimple.feature.plugin

import android.content.Context
import androidx.annotation.StringRes
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginCompatibility
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.install.pluginCompatibilityText
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus

internal fun canConfirmPluginInstall(preview: PluginInstallPreview): Boolean = preview.installable

internal sealed interface PluginInstallBlockReason {
    data object ChecksumMismatch : PluginInstallBlockReason

    data class SignatureRejected(val error: Throwable?) : PluginInstallBlockReason
    data class Incompatible(val compatibility: PluginCompatibility) : PluginInstallBlockReason
}

internal fun pluginInstallBlockReason(preview: PluginInstallPreview): PluginInstallBlockReason? = when {
    !preview.checksumVerified -> PluginInstallBlockReason.ChecksumMismatch
    preview.signatureStatus == PluginSignatureStatus.Invalid ->
        PluginInstallBlockReason.SignatureRejected(preview.signatureError)

    preview.compatibility.status == PluginCompatibilityStatus.Incompatible ->
        PluginInstallBlockReason.Incompatible(preview.compatibility)

    else -> null
}

internal fun Context.pluginInstallBlockReasonText(reason: PluginInstallBlockReason): String = when (reason) {
    PluginInstallBlockReason.ChecksumMismatch -> getString(R.string.plugin_install_block_checksum)
    is PluginInstallBlockReason.SignatureRejected -> pluginErrorDetail(reason.error)
        ?.let { getString(R.string.plugin_install_block_signature_detail, it) }
        ?: getString(R.string.plugin_install_block_signature)
    is PluginInstallBlockReason.Incompatible -> pluginCompatibilityText(reason.compatibility)
        ?: getString(R.string.plugin_catalog_incompatible)
}

internal sealed interface PluginInstallOriginLabel {
    data class GitHubRepo(val repoSlug: String) : PluginInstallOriginLabel

    data object LocalFile : PluginInstallOriginLabel

    data object Bundled : PluginInstallOriginLabel

    data object Remote : PluginInstallOriginLabel
}

internal fun pluginInstallOriginLabel(
    source: PluginInstallSource,
    origin: PluginInstallOrigin?,
): PluginInstallOriginLabel = when {
    origin != null -> PluginInstallOriginLabel.GitHubRepo(origin.repoSlug)
    source == PluginInstallSource.Local -> PluginInstallOriginLabel.LocalFile
    source == PluginInstallSource.Bundled -> PluginInstallOriginLabel.Bundled
    else -> PluginInstallOriginLabel.Remote
}

internal fun Context.pluginInstallOriginText(label: PluginInstallOriginLabel): String = when (label) {
    is PluginInstallOriginLabel.GitHubRepo ->
        getString(R.string.plugin_install_origin_github, label.repoSlug)

    PluginInstallOriginLabel.LocalFile -> getString(R.string.plugin_install_origin_local)
    PluginInstallOriginLabel.Bundled -> getString(R.string.plugin_install_origin_bundled)
    PluginInstallOriginLabel.Remote -> getString(R.string.plugin_install_origin_remote)
}

@StringRes
internal fun pluginChecksumLabelRes(verified: Boolean): Int =
    if (verified) R.string.plugin_install_checksum_verified else R.string.plugin_install_checksum_failed

internal sealed interface PluginSignatureLabel {
    data object Unsigned : PluginSignatureLabel

    /** Valid signature with [fingerprint]. */
    data class Valid(val fingerprint: String) : PluginSignatureLabel

    /** Valid signature without a fingerprint. */
    data object ValidWithoutFingerprint : PluginSignatureLabel

    data object Invalid : PluginSignatureLabel
}

internal fun pluginSignatureLabel(
    status: PluginSignatureStatus,
    fingerprint: String?,
): PluginSignatureLabel = when (status) {
    PluginSignatureStatus.Absent -> PluginSignatureLabel.Unsigned
    PluginSignatureStatus.Valid -> fingerprint
        ?.let { PluginSignatureLabel.Valid(it) }
        ?: PluginSignatureLabel.ValidWithoutFingerprint

    PluginSignatureStatus.Invalid -> PluginSignatureLabel.Invalid
}

internal fun Context.pluginSignatureText(label: PluginSignatureLabel): String = when (label) {
    PluginSignatureLabel.Unsigned -> getString(R.string.plugin_install_signature_unsigned)
    is PluginSignatureLabel.Valid ->
        getString(R.string.plugin_install_signature_valid_fingerprint, label.fingerprint)

    PluginSignatureLabel.ValidWithoutFingerprint ->
        getString(R.string.plugin_install_signature_valid)

    PluginSignatureLabel.Invalid -> getString(R.string.plugin_install_signature_invalid)
}

/** Permission summary for installation preview. */
internal sealed interface PluginPermissionList {
    /** No declared permissions. */
    data object Empty : PluginPermissionList

    /** Deduplicated declared permissions. */
    data class Declared(val permissions: List<PluginPermission>) : PluginPermissionList
}

internal fun pluginPermissionList(permissions: List<PluginPermission>): PluginPermissionList =
    if (permissions.isEmpty()) {
        PluginPermissionList.Empty
    } else {
        PluginPermissionList.Declared(permissions.distinct())
    }

/** Localized description resource for each permission. */
@StringRes
internal fun pluginPermissionNameRes(permission: PluginPermission): Int = when (permission) {
    PluginPermission.WebNavigate -> R.string.plugin_permission_web_navigate
    PluginPermission.WebReadDom -> R.string.plugin_permission_web_read_dom
    PluginPermission.WebReadCookies -> R.string.plugin_permission_web_read_cookies
    PluginPermission.WebInjectScript -> R.string.plugin_permission_web_inject_script
    PluginPermission.WebCapturePacket -> R.string.plugin_permission_web_capture_packet
    PluginPermission.NetworkFetch -> R.string.plugin_permission_network_fetch
    PluginPermission.ScheduleWrite -> R.string.plugin_permission_schedule_write
    PluginPermission.StoragePlugin -> R.string.plugin_permission_storage_plugin
    PluginPermission.ComponentUse -> R.string.plugin_permission_component_use
    PluginPermission.FeedWrite -> R.string.plugin_permission_feed_write
    PluginPermission.NotificationReceive -> R.string.plugin_permission_notification_receive
    PluginPermission.SecureStorage -> R.string.plugin_permission_secure_storage
    PluginPermission.NetworkProxy -> R.string.plugin_permission_network_proxy
}

/** Render each permission with its description and original ID. */
internal fun Context.pluginPermissionListText(list: PluginPermissionList): List<String> = when (list) {
    PluginPermissionList.Empty -> listOf(getString(R.string.plugin_install_permission_none))
    is PluginPermissionList.Declared -> list.permissions.map { permission ->
        getString(
            R.string.plugin_install_permission_entry,
            getString(pluginPermissionNameRes(permission)),
            permission.id,
        )
    }
}
