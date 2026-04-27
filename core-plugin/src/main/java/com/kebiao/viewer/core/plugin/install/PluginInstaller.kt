package com.kebiao.viewer.core.plugin.install

import com.kebiao.viewer.core.plugin.manifest.PluginManifest
import com.kebiao.viewer.core.plugin.packageformat.PluginPackageLayout
import com.kebiao.viewer.core.plugin.packageformat.PluginPackageReader
import com.kebiao.viewer.core.plugin.security.PluginChecksumVerifier
import com.kebiao.viewer.core.plugin.security.PluginSignatureVerifier
import com.kebiao.viewer.core.plugin.storage.PluginFileStore
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

class PluginInstaller(
    private val registryRepository: PluginRegistryRepository,
    private val fileStore: PluginFileStore,
    private val packageReader: PluginPackageReader = PluginPackageReader(),
    private val checksumVerifier: PluginChecksumVerifier = PluginChecksumVerifier(),
    private val signatureVerifier: PluginSignatureVerifier = PluginSignatureVerifier(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    fun previewPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallPreview {
        val layout = packageReader.read(bytes)
        val manifest = layout.decodeManifest(json)
        val checksums = layout.decodeChecksums(json)
        val signatureInfo = layout.decodeSignatureInfo(json)
        return PluginInstallPreview(
            manifest = manifest,
            checksumVerified = checksumVerifier.verify(layout, checksums),
            signatureVerified = signatureVerifier.verify(layout, signatureInfo),
            source = source,
        )
    }

    suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallResult {
        return runCatching {
            val layout = packageReader.read(bytes)
            val preview = verifyLayout(layout, source)
            val targetDir = fileStore.writeLayout(preview.manifest, layout)
            val record = preview.manifest.toInstalledRecord(
                source = source,
                storagePath = targetDir.absolutePath,
                bundled = false,
            )
            registryRepository.saveInstalledPlugin(record)
            PluginInstallResult.Success(record)
        }.getOrElse {
            PluginInstallResult.Failure(it.message ?: "安装插件失败")
        }
    }

    suspend fun installBundledAssetDirectory(assetRoot: String): InstalledPluginRecord {
        val targetDir = fileStore.copyBundledAssetDirectory(assetRoot)
        val manifest = json.decodeFromString<PluginManifest>(fileStore.loadAssetText("$assetRoot/manifest.json"))
        val record = manifest.toInstalledRecord(
            source = PluginInstallSource.Bundled,
            storagePath = targetDir.absolutePath,
            bundled = true,
        )
        registryRepository.saveInstalledPlugin(record)
        return record
    }

    private fun verifyLayout(layout: PluginPackageLayout, source: PluginInstallSource): PluginInstallPreview {
        val preview = previewPackageFromLayout(layout, source)
        require(preview.checksumVerified) { "插件摘要校验失败" }
        require(preview.signatureVerified) { "插件签名校验失败" }
        return preview
    }

    private fun previewPackageFromLayout(layout: PluginPackageLayout, source: PluginInstallSource): PluginInstallPreview {
        val manifest = layout.decodeManifest(json)
        val checksums = layout.decodeChecksums(json)
        val signatureInfo = layout.decodeSignatureInfo(json)
        return PluginInstallPreview(
            manifest = manifest,
            checksumVerified = checksumVerifier.verify(layout, checksums),
            signatureVerified = signatureVerifier.verify(layout, signatureInfo),
            source = source,
        )
    }

    private fun PluginManifest.toInstalledRecord(
        source: PluginInstallSource,
        storagePath: String,
        bundled: Boolean,
    ): InstalledPluginRecord {
        return InstalledPluginRecord(
            pluginId = pluginId,
            name = name,
            publisher = publisher,
            version = version,
            versionCode = versionCode,
            storagePath = storagePath,
            installedAt = OffsetDateTime.now().toString(),
            source = source,
            declaredPermissions = declaredPermissions,
            allowedHosts = allowedHosts,
            isBundled = bundled,
        )
    }
}
