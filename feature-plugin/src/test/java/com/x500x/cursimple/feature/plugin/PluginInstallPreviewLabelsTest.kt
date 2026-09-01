package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "GitHub 仓库 owner/plugin-repo",
            pluginInstallOriginLabel(PluginInstallSource.Remote, origin),
        )
        assertEquals("本地文件", pluginInstallOriginLabel(PluginInstallSource.Local, null))
        assertEquals("应用内置", pluginInstallOriginLabel(PluginInstallSource.Bundled, null))
        assertEquals("远程下载", pluginInstallOriginLabel(PluginInstallSource.Remote, null))
    }

    @Test
    fun `checksum label states what the digest actually covers`() {
        assertEquals("通过（包内文件与 checksums.json 一致）", pluginChecksumLabel(true))
        assertEquals("未通过", pluginChecksumLabel(false))
    }

    @Test
    fun `signature label separates unsigned from verified`() {
        assertEquals("未签名", pluginSignatureLabel(PluginSignatureStatus.Absent, null))
        assertEquals(
            "有效，公钥指纹 AB:CD",
            pluginSignatureLabel(PluginSignatureStatus.Valid, "AB:CD"),
        )
        assertEquals("有效", pluginSignatureLabel(PluginSignatureStatus.Valid, null))
        assertEquals("无效", pluginSignatureLabel(PluginSignatureStatus.Invalid, null))
    }

    @Test
    fun `permission labels pair a description with the raw id`() {
        val labels = pluginPermissionLabels(
            listOf(
                PluginPermission.WebReadCookies,
                PluginPermission.NetworkFetch,
                PluginPermission.WebReadCookies,
            ),
        )

        assertEquals(2, labels.size)
        assertEquals("读取网页登录 Cookie（web.read_cookies）", labels.first())
        assertTrue(labels.last().contains("network.fetch"))
    }

    @Test
    fun `permission labels cover every declared permission`() {
        PluginPermission.entries.forEach { permission ->
            val label = pluginPermissionLabels(listOf(permission)).single()
            assertTrue(label.contains(permission.id))
            assertFalse(label.startsWith("（"))
        }
    }

    @Test
    fun `empty permission list is spelled out`() {
        assertEquals(listOf("未声明任何权限"), pluginPermissionLabels(emptyList()))
    }

    @Test
    fun `empty host allowlist explains that the plugin cannot navigate`() {
        assertTrue(pluginAllowedHostsEmptyLabel().contains("未声明"))
    }

    @Test
    fun `unsigned package with a valid checksum stays installable`() {
        val preview = preview(checksumVerified = true, signature = PluginSignatureStatus.Absent)

        assertTrue(canConfirmPluginInstall(preview))
        assertNull(pluginInstallBlockReason(preview))
        assertTrue(installPreviewStatusMessage(preview).contains("已通过完整性预检"))
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
        assertTrue(pluginInstallBlockReason(preview).orEmpty().contains("checksums.json"))
        assertTrue(installPreviewStatusMessage(preview).contains("摘要校验未通过"))
    }

    @Test
    fun `broken signature blocks the confirm button`() {
        val preview = preview(
            checksumVerified = true,
            signature = PluginSignatureStatus.Invalid,
            signatureMessage = "插件签名与包内摘要清单不匹配",
        )

        assertFalse(canConfirmPluginInstall(preview))
        assertEquals(
            "签名校验未通过：插件签名与包内摘要清单不匹配",
            pluginInstallBlockReason(preview),
        )
        assertTrue(installPreviewStatusMessage(preview).contains("签名校验未通过"))
    }

    private fun preview(
        checksumVerified: Boolean,
        signature: PluginSignatureStatus,
        signatureMessage: String? = null,
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
            signatureMessage = signatureMessage,
        )
    }
}
