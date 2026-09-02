package com.x500x.cursimple.feature.plugin

import android.content.Context
import androidx.annotation.StringRes
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus

internal fun canConfirmPluginInstall(preview: PluginInstallPreview): Boolean = preview.installable

/** 预检拦下安装的原因。 */
internal sealed interface PluginInstallBlockReason {
    /** 包内文件与摘要清单不一致。 */
    data object ChecksumMismatch : PluginInstallBlockReason

    /** 签名校验未通过，[error] 为校验器给出的原因。 */
    data class SignatureRejected(val error: Throwable?) : PluginInstallBlockReason
}

/** 预检结论为可安装时返回 null。 */
internal fun pluginInstallBlockReason(preview: PluginInstallPreview): PluginInstallBlockReason? = when {
    !preview.checksumVerified -> PluginInstallBlockReason.ChecksumMismatch
    preview.signatureStatus == PluginSignatureStatus.Invalid ->
        PluginInstallBlockReason.SignatureRejected(preview.signatureError)

    else -> null
}

internal fun Context.pluginInstallBlockReasonText(reason: PluginInstallBlockReason): String = when (reason) {
    PluginInstallBlockReason.ChecksumMismatch -> getString(R.string.plugin_install_block_checksum)
    is PluginInstallBlockReason.SignatureRejected -> pluginErrorDetail(reason.error)
        ?.let { getString(R.string.plugin_install_block_signature_detail, it) }
        ?: getString(R.string.plugin_install_block_signature)
}

/** 待安装包的来源渠道。 */
internal sealed interface PluginInstallOriginLabel {
    /** 从 GitHub 仓库 [repoSlug] 下载。 */
    data class GitHubRepo(val repoSlug: String) : PluginInstallOriginLabel

    /** 从本地文件导入。 */
    data object LocalFile : PluginInstallOriginLabel

    /** 随应用一起打包。 */
    data object Bundled : PluginInstallOriginLabel

    /** 远程下载，但没有具体仓库信息。 */
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

/** 摘要校验结论对应的文案资源 id。 */
@StringRes
internal fun pluginChecksumLabelRes(verified: Boolean): Int =
    if (verified) R.string.plugin_install_checksum_verified else R.string.plugin_install_checksum_failed

/** 签名校验结论。 */
internal sealed interface PluginSignatureLabel {
    /** 包里没有带签名。 */
    data object Unsigned : PluginSignatureLabel

    /** 签名有效，并拿到了公钥指纹 [fingerprint]。 */
    data class Valid(val fingerprint: String) : PluginSignatureLabel

    /** 签名有效，但没有拿到公钥指纹。 */
    data object ValidWithoutFingerprint : PluginSignatureLabel

    /** 签名无效。 */
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

/** 安装预检里的权限清单。 */
internal sealed interface PluginPermissionList {
    /** 插件没有声明任何权限。 */
    data object Empty : PluginPermissionList

    /** 去重后逐条列出的权限。 */
    data class Declared(val permissions: List<PluginPermission>) : PluginPermissionList
}

internal fun pluginPermissionList(permissions: List<PluginPermission>): PluginPermissionList =
    if (permissions.isEmpty()) {
        PluginPermissionList.Empty
    } else {
        PluginPermissionList.Declared(permissions.distinct())
    }

/** 每种权限的说明文案资源 id。 */
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
}

/** 每条权限渲染成说明加原始权限 id。 */
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
