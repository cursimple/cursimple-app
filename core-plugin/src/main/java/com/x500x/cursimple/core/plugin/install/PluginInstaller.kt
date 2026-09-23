package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.R
import java.io.File
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageReader
import com.x500x.cursimple.core.plugin.pluginReasonOr
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.security.PluginChecksumVerifier
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus
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
            val preview = previewPackageFromLayout(layout, source)
            PluginLogger.info(
                "plugin.install.preview.success",
                mapOf(
                    "source" to source,
                    "bytes" to bytes.size,
                    "pluginId" to preview.manifest.pluginId,
                    "version" to preview.manifest.version,
                    "versionCode" to preview.manifest.versionCode,
                    "checksumVerified" to preview.checksumVerified,
                    "signatureStatus" to preview.signatureStatus,
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

    suspend fun installPackage(
        bytes: ByteArray,
        source: PluginInstallSource,
        sourceRepo: String? = null,
    ): PluginInstallResult {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.install.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
        return runCatching {
            val layout = packageReader.read(bytes)
            val preview = verifyLayout(layout, source)
            val targetDir = fileStore.writeLayout(preview.manifest, layout, source)
            val record = preview.manifest.toInstalledRecord(
                source = source,
                storagePath = targetDir.absolutePath,
                bundled = false,
            ).copy(sourceRepo = sourceRepo?.trim()?.takeIf { it.isNotBlank() })
            val previous = registryRepository.findByInstallKey(record.installKey)
            registryRepository.saveInstalledPlugin(record)
            removeReplacedVersion(previous, targetDir)
            PluginLogger.info(
                "plugin.install.success",
                mapOf(
                    "source" to source,
                    "bytes" to bytes.size,
                    "pluginId" to record.pluginId,
                    "version" to record.version,
                    "versionCode" to record.versionCode,
                    "checksumVerified" to preview.checksumVerified,
                    "signatureStatus" to preview.signatureStatus,
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
            PluginInstallResult.Failure(pluginReasonOr(it, R.string.plugin_error_install_failed))
        }
    }

    /**
     * 升级后删掉旧版本的目录。
     *
     * 目录名带着版本号，新版装在新目录里、记录也指过去了，旧目录就再也用不上；
     * 不删的话每升级一次就多留一份。只删和新目录同在插件根目录下的那个，
     * 内置插件与路径对不上的一概不碰。删不掉也不影响这次安装。
     */
    private fun removeReplacedVersion(previous: InstalledPluginRecord?, targetDir: java.io.File) {
        if (previous == null || previous.isBundled) return
        val oldDir = previous.storagePath.takeIf { it.isNotBlank() }?.let(::File) ?: return
        if (oldDir.canonicalPath == targetDir.canonicalPath) return
        if (oldDir.parentFile?.canonicalPath != targetDir.parentFile?.canonicalPath) return
        runCatching { oldDir.deleteRecursively() }
            .onFailure { error ->
                PluginLogger.warn(
                    "plugin.install.old_version_cleanup_failed",
                    mapOf("pluginId" to previous.pluginId),
                    error,
                )
            }
    }

    private fun verifyLayout(layout: PluginPackageLayout, source: PluginInstallSource): PluginInstallPreview {
        val preview = previewPackageFromLayout(layout, source)
        pluginRequire(preview.checksumVerified, R.string.plugin_error_install_checksum_rejected)
        if (preview.signatureStatus == PluginSignatureStatus.Invalid) {
            throw signatureRejected(preview.signatureError)
        }
        return preview
    }

    private fun previewPackageFromLayout(layout: PluginPackageLayout, source: PluginInstallSource): PluginInstallPreview {
        val manifest = layout.decodeValidatedManifest(json)
        val checksums = layout.decodeChecksums(json)
        val signature = signatureVerifier.resolve(layout, json)
        return PluginInstallPreview(
            manifest = manifest,
            checksumVerified = checksumVerifier.verify(layout, checksums),
            source = source,
            signatureStatus = signature.status,
            signerFingerprint = signature.signerFingerprint,
            signatureError = signature.error,
        )
    }

    /** 拿不到具体原因时用不带占位符的那条文案。 */
    private fun signatureRejected(detail: Throwable?): PluginArgumentException {
        val reason = detail ?: return PluginArgumentException(R.string.plugin_error_install_signature_rejected)
        return PluginArgumentException(
            R.string.plugin_error_install_signature_rejected_detail,
            listOf(reason),
        )
    }

    private fun PluginManifest.toInstalledRecord(
        source: PluginInstallSource,
        storagePath: String,
        bundled: Boolean,
    ): InstalledPluginRecord {
        val compatibility = resolvePluginCompatibility(apiVersion)
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
            compatibilityStatus = compatibility.status,
            isBundled = bundled,
        )
    }

    private fun elapsedSince(startedAt: Long): Long {
        return System.currentTimeMillis() - startedAt
    }
}
