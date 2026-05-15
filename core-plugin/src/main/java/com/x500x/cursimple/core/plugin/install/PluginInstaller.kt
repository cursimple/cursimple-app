package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageReader
import com.x500x.cursimple.core.plugin.security.PluginChecksumVerifier
import com.x500x.cursimple.core.plugin.security.PluginSignatureVerifier
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

class PluginInstaller(
    private val registryRepository: PluginRegistryRepository,
    private val fileStore: PluginFileStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val packageReader: PluginPackageReader = PluginPackageReader(json),
    private val checksumVerifier: PluginChecksumVerifier = PluginChecksumVerifier(),
    private val signatureVerifier: PluginSignatureVerifier = PluginSignatureVerifier(),
) {
    fun previewPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallPreview {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.install.preview.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
        return try {
            val layout = packageReader.read(bytes)
            val manifest = layout.decodeManifest(json)
            val checksums = layout.decodeChecksums(json)
            val signatureInfo = layout.decodeSignatureInfo(json)
            val preview = PluginInstallPreview(
                manifest = manifest,
                checksumVerified = checksumVerifier.verify(layout, checksums),
                signatureVerified = signatureVerifier.verify(layout, signatureInfo),
                source = source,
            )
            PluginLogger.info(
                "plugin.install.preview.success",
                mapOf(
                    "source" to source,
                    "bytes" to bytes.size,
                    "pluginId" to manifest.pluginId,
                    "version" to manifest.version,
                    "versionCode" to manifest.versionCode,
                    "checksumVerified" to preview.checksumVerified,
                    "signatureVerified" to preview.signatureVerified,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            preview
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.install.preview.failure",
                mapOf("source" to source, "bytes" to bytes.size, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            throw error
        }
    }

    suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallResult {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.install.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
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
            PluginLogger.info(
                "plugin.install.success",
                mapOf(
                    "source" to source,
                    "bytes" to bytes.size,
                    "pluginId" to record.pluginId,
                    "version" to record.version,
                    "versionCode" to record.versionCode,
                    "checksumVerified" to preview.checksumVerified,
                    "signatureVerified" to preview.signatureVerified,
                    "storagePathPresent" to record.storagePath.isNotBlank(),
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            PluginInstallResult.Success(record)
        }.getOrElse {
            PluginLogger.error(
                "plugin.install.failure",
                mapOf("source" to source, "bytes" to bytes.size, "elapsedMs" to elapsedSince(startedAt)),
                it,
            )
            PluginInstallResult.Failure(it.message ?: "安装插件失败")
        }
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
            apiVersion = apiVersion,
            entry = entry,
            storagePath = storagePath,
            installedAt = OffsetDateTime.now().toString(),
            source = source,
            permissions = permissions,
            allowedHosts = allowedHosts,
            webEngine = webEngine,
            components = components,
            compatibilityStatus = PluginCompatibilityStatus.Compatible,
            compatibilityMessage = null,
            isBundled = bundled,
        )
    }

    private fun elapsedSince(startedAt: Long): Long {
        return System.currentTimeMillis() - startedAt
    }
}
