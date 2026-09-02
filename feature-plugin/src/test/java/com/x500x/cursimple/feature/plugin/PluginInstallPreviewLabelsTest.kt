package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallPreviewLabelsTest {
    @Test
    fun `github origin is shown instead of the generic remote channel`() {
        val origin = PluginInstallOrigin(
            repoSlug = "owner/plugin-repo",
            downloadUrl = "https://github.com/owner/plugin-repo/releases/latest/download/plugin.zip",
        )

        assertEquals(
            PluginInstallOriginLabel.GitHubRepo("owner/plugin-repo"),
            pluginInstallOriginLabel(PluginInstallSource.Remote, origin),
        )
        assertEquals(
            PluginInstallOriginLabel.LocalFile,
            pluginInstallOriginLabel(PluginInstallSource.Local, null),
        )
        assertEquals(
            PluginInstallOriginLabel.Bundled,
            pluginInstallOriginLabel(PluginInstallSource.Bundled, null),
        )
        assertEquals(
            PluginInstallOriginLabel.Remote,
            pluginInstallOriginLabel(PluginInstallSource.Remote, null),
        )
    }

    @Test
    fun `checksum label separates a verified digest from a failed one`() {
        assertEquals(R.string.plugin_install_checksum_verified, pluginChecksumLabelRes(true))
        assertEquals(R.string.plugin_install_checksum_failed, pluginChecksumLabelRes(false))
    }

    @Test
    fun `signature label separates unsigned from verified`() {
        assertEquals(
            PluginSignatureLabel.Unsigned,
            pluginSignatureLabel(PluginSignatureStatus.Absent, null),
        )
        assertEquals(
            PluginSignatureLabel.Valid("AB:CD"),
            pluginSignatureLabel(PluginSignatureStatus.Valid, "AB:CD"),
        )
        assertEquals(
            PluginSignatureLabel.ValidWithoutFingerprint,
            pluginSignatureLabel(PluginSignatureStatus.Valid, null),
        )
        assertEquals(
            PluginSignatureLabel.Invalid,
            pluginSignatureLabel(PluginSignatureStatus.Invalid, null),
        )
    }

    @Test
    fun `permission list keeps the declaration order and drops duplicates`() {
        val list = pluginPermissionList(
            listOf(
                PluginPermission.WebReadCookies,
                PluginPermission.NetworkFetch,
                PluginPermission.WebReadCookies,
            ),
        )

        assertEquals(
            PluginPermissionList.Declared(
                listOf(PluginPermission.WebReadCookies, PluginPermission.NetworkFetch),
            ),
            list,
        )
        assertEquals(
            R.string.plugin_permission_web_read_cookies,
            pluginPermissionNameRes(PluginPermission.WebReadCookies),
        )
        assertEquals(
            R.string.plugin_permission_network_fetch,
            pluginPermissionNameRes(PluginPermission.NetworkFetch),
        )
    }

    @Test
    fun `every declared permission gets its own description`() {
        val resIds = PluginPermission.entries.map(::pluginPermissionNameRes)

        resIds.forEach { assertNotEquals(0, it) }
        assertEquals(PluginPermission.entries.size, resIds.toSet().size)
    }

    @Test
    fun `empty permission list is spelled out`() {
        assertEquals(PluginPermissionList.Empty, pluginPermissionList(emptyList()))
    }

    @Test
    fun `unsigned package with a valid checksum stays installable`() {
        val preview = preview(checksumVerified = true, signature = PluginSignatureStatus.Absent)

        assertTrue(canConfirmPluginInstall(preview))
        assertNull(pluginInstallBlockReason(preview))
        assertEquals(PluginMarketStatus.PreviewReady, installPreviewStatus(preview))
    }

    @Test
    fun `signed package with a valid signature stays installable`() {
        val preview = preview(checksumVerified = true, signature = PluginSignatureStatus.Valid)

        assertTrue(canConfirmPluginInstall(preview))
        assertNull(pluginInstallBlockReason(preview))
    }

    @Test
    fun `broken checksum blocks the confirm button`() {
        val preview = preview(checksumVerified = false, signature = PluginSignatureStatus.Absent)

        assertFalse(canConfirmPluginInstall(preview))
        assertEquals(PluginInstallBlockReason.ChecksumMismatch, pluginInstallBlockReason(preview))
        assertEquals(PluginMarketStatus.PreviewChecksumRejected, installPreviewStatus(preview))
    }

    @Test
    fun `broken signature blocks the confirm button`() {
        val signatureError = PluginArgumentException(R.string.plugin_install_block_signature)
        val preview = preview(
            checksumVerified = true,
            signature = PluginSignatureStatus.Invalid,
            signatureError = signatureError,
        )

        assertFalse(canConfirmPluginInstall(preview))
        assertEquals(
            PluginInstallBlockReason.SignatureRejected(signatureError),
            pluginInstallBlockReason(preview),
        )
        assertEquals(PluginMarketStatus.PreviewSignatureRejected, installPreviewStatus(preview))

        val withoutDetail = preview(checksumVerified = true, signature = PluginSignatureStatus.Invalid)

        assertEquals(
            PluginInstallBlockReason.SignatureRejected(null),
            pluginInstallBlockReason(withoutDetail),
        )
    }

    private fun preview(
        checksumVerified: Boolean,
        signature: PluginSignatureStatus,
        signatureError: Throwable? = null,
    ): PluginInstallPreview {
        return PluginInstallPreview(
            manifest = PluginManifest(
                id = "edu.demo",
                name = "Demo",
                version = "1.0.0",
                versionCode = 1,
                entry = "main.js",
                permissions = listOf(PluginPermission.ScheduleWrite),
                allowedHosts = listOf("jw.demo.edu.cn"),
            ),
            checksumVerified = checksumVerified,
            source = PluginInstallSource.Remote,
            signatureStatus = signature,
            signerFingerprint = if (signature == PluginSignatureStatus.Valid) "AB:CD" else null,
            signatureError = signatureError,
        )
    }
}
