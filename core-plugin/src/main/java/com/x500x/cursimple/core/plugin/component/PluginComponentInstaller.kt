package com.x500x.cursimple.core.plugin.component

import android.os.Build
import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.install.requirePluginCompatibility
import com.x500x.cursimple.core.plugin.install.resolveHostVersionCompatibility
import com.x500x.cursimple.core.plugin.packageformat.readAtMostBytes
import com.x500x.cursimple.core.plugin.packageformat.requireBoundedPluginJson
import com.x500x.cursimple.core.plugin.pluginReasonOr
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.pluginRequireNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream

class PluginComponentInstaller(
    private val componentRoot: File,
    private val repository: PluginComponentRepository,
    private val hostVersion: String,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val maxFileCount: Int = DEFAULT_MAX_FILE_COUNT,
    private val maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
) {
    suspend fun installLocalPackage(bytes: ByteArray): PluginComponentInstallResult {
        return installPackage(bytes, PluginComponentSource.Local)
    }

    suspend fun installRemotePackage(bytes: ByteArray): PluginComponentInstallResult {
        return installPackage(bytes, PluginComponentSource.Remote)
    }

    // Large component extraction and verification run on IO.
    private suspend fun installPackage(
        bytes: ByteArray,
        source: PluginComponentSource,
    ): PluginComponentInstallResult = withContext(Dispatchers.IO) {
        runCatching {
            val layout = readComponentPackage(bytes)
            val manifestText = layout.requireFile(MANIFEST_FILE).toString(Charsets.UTF_8)
            requireBoundedPluginJson(manifestText)
            // Recognize plugin bundles imported through the component-asset entry point and explain the correct route.
            val looksLikePlugin = runCatching {
                val obj = json.parseToJsonElement(manifestText) as? kotlinx.serialization.json.JsonObject
                obj != null && ("apiVersion" in obj || "entry" in obj)
            }.getOrDefault(false)
            pluginRequire(!looksLikePlugin, R.string.plugin_error_component_is_plugin_package)
            val manifest = json.decodeFromString<PluginComponentPackageManifest>(manifestText)
            validateManifest(manifest, layout)
            requirePluginCompatibility(resolveHostVersionCompatibility(manifest.minHostVersion, hostVersion))
            val target = installLayout(manifest, layout, source)
            val record = InstalledPluginComponentRecord(
                id = manifest.id,
                type = manifest.type,
                version = manifest.version,
                abi = manifest.abi,
                storagePath = target.absolutePath,
                sha256 = manifest.sha256,
                source = source,
                installedAt = OffsetDateTime.now().toString(),
            )
            val previous = try {
                val old = repository.getInstalledComponents().firstOrNull { it.id == record.id && it.source == record.source }
                repository.save(record)
                old
            } catch (error: Exception) {
                target.deleteRecursively()
                throw error
            }
            runCatching {
                previous?.storagePath?.let(::File)?.canonicalFile?.takeIf {
                    it != target && it.parentFile == target.parentFile
                }?.deleteRecursively()
            }
            PluginComponentInstallResult.Success(record)
        }.getOrElse { error ->
            PluginComponentInstallResult.Failure(
                PluginComponentInstallFailure(
                    code = "install_failed",
                    error = pluginReasonOr(error, R.string.plugin_error_component_install_failed),
                ),
            )
        }
    }

    private fun validateManifest(
        manifest: PluginComponentPackageManifest,
        layout: ComponentPackageLayout,
    ) {
        pluginRequire(manifest.id.isNotBlank(), R.string.plugin_error_component_manifest_missing_id)
        pluginRequire(
            manifest.version.isNotBlank(),
            R.string.plugin_error_component_manifest_missing_version,
        )
        pluginRequire(
            manifest.sha256.matches(SHA_256_REGEX),
            R.string.plugin_error_component_sha256_format_invalid,
        )
        if (!manifest.abi.isNullOrBlank()) {
            pluginRequire(
                manifest.abi in supportedAbis,
                R.string.plugin_error_component_abi_incompatible,
                manifest.abi,
            )
        }
        val payloadFiles = manifest.files.ifEmpty {
            layout.files.keys.filterNot { it == MANIFEST_FILE }
        }
        pluginRequire(payloadFiles.isNotEmpty(), R.string.plugin_error_component_package_empty)
        if (manifest.files.isNotEmpty()) {
            val unlistedFiles = layout.files.keys
                .filterNot { it == MANIFEST_FILE }
                .filterNot { it in manifest.files }
            if (unlistedFiles.isNotEmpty()) {
                throw PluginArgumentException(
                    R.string.plugin_error_component_package_unlisted_files,
                    listOf(unlistedFiles.sorted().joinToString()),
                )
            }
        }
        payloadFiles.forEach { file ->
            pluginRequire(
                file in layout.files,
                R.string.plugin_error_component_package_missing_file,
                file,
            )
        }
        val actual = sha256PayloadFiles(payloadFiles.sorted(), layout)
        pluginRequire(
            actual.equals(manifest.sha256, ignoreCase = true),
            R.string.plugin_error_component_sha256_mismatch,
        )
    }

    private fun installLayout(
        manifest: PluginComponentPackageManifest,
        layout: ComponentPackageLayout,
        source: PluginComponentSource,
    ): File {
        val componentRoot = componentRoot.canonicalFile
        componentRoot.mkdirs()
        val safeId = manifest.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val sourceTag = source.name.lowercase()
        val targetDir = File(componentRoot, "${safeId.take(128)}-$sourceTag-${java.util.UUID.randomUUID()}").canonicalFile
        requireContained(componentRoot, targetDir, R.string.plugin_error_component_install_dir_escape)
        check(targetDir.mkdir())
        try {
            layout.files.filterKeys { it != MANIFEST_FILE }.forEach { (path, bytes) ->
                val target = File(targetDir, path).canonicalFile
                requireContained(targetDir, target, R.string.plugin_error_component_package_path_escape, path)
                check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                target.writeBytes(bytes)
            }
            return targetDir
        } catch (error: Exception) {
            targetDir.deleteRecursively()
            throw error
        }
    }

    private fun requireContained(root: File, target: File, messageRes: Int, vararg formatArgs: Any) {
        val rootPath = root.canonicalFile.path
        val targetPath = target.canonicalFile.path
        pluginRequire(
            targetPath == rootPath || targetPath.startsWith(rootPath + File.separator),
            messageRes,
            *formatArgs,
        )
    }

    private fun readComponentPackage(bytes: ByteArray): ComponentPackageLayout {
        pluginRequire(bytes.size <= maxUncompressedBytes, R.string.plugin_error_component_package_size_exceeded, maxUncompressedBytes)
        val files = linkedMapOf<String, ByteArray>()
        val paths = mutableSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                pluginRequire(++entryCount <= maxFileCount, R.string.plugin_error_component_package_file_count_exceeded, maxFileCount)
                val normalized = normalizePackagePath(if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name)
                pluginRequire(paths.add(normalized), R.string.plugin_error_component_package_duplicate_file, normalized)
                val content = pluginRequireNotNull(
                    zip.readAtMostBytes(maxUncompressedBytes - totalBytes),
                    R.string.plugin_error_component_package_size_exceeded, maxUncompressedBytes,
                )
                totalBytes += content.size.toLong()
                if (!entry.isDirectory) {
                    if (normalized == MANIFEST_FILE) require(content.size <= 1024 * 1024) { "Component metadata exceeds 1 MiB" }
                    files[normalized] = content
                }
                zip.closeEntry()
            }
        }
        files.keys.forEach { path ->
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                pluginRequire(parent !in files, R.string.plugin_error_component_package_illegal_path, path)
                parent = parent.substringBeforeLast('/', "")
            }
        }
        pluginRequire(MANIFEST_FILE in files, R.string.plugin_error_component_package_missing_manifest)
        return ComponentPackageLayout(files)
    }

    private fun normalizePackagePath(rawPath: String): String {
        pluginRequire(rawPath.length <= 1024 && '\u0000' !in rawPath, R.string.plugin_error_component_package_illegal_path, "path")
        val path = rawPath.replace('\\', '/').trim()
        pluginRequire(path.isNotBlank(), R.string.plugin_error_component_package_blank_path)
        pluginRequire(
            !path.startsWith("/"),
            R.string.plugin_error_component_package_absolute_path,
            rawPath,
        )
        pluginRequire(
            !WINDOWS_DRIVE_PATH.matches(path),
            R.string.plugin_error_component_package_windows_drive_path,
            rawPath,
        )
        val segments = path.split('/')
        pluginRequire(
            segments.none { it == ".." },
            R.string.plugin_error_component_package_path_traversal,
            rawPath,
        )
        pluginRequire(
            segments.none { it.isBlank() || it == "." },
            R.string.plugin_error_component_package_illegal_path,
            rawPath,
        )
        return segments.joinToString("/")
    }

    private data class ComponentPackageLayout(
        val files: Map<String, ByteArray>,
    ) {
        fun requireFile(path: String): ByteArray {
            return files[path] ?: throw PluginArgumentException(
                R.string.plugin_error_component_package_missing_file,
                listOf(path),
            )
        }
    }

    private fun sha256PayloadFiles(paths: List<String>, layout: ComponentPackageLayout): String {
        val digest = MessageDigest.getInstance("SHA-256")
        paths.forEach { path ->
            digest.update(layout.requireFile(path))
        }
        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val DEFAULT_MAX_FILE_COUNT = 512
        const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 200L * 1024L * 1024L
        val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:.*")
        val SHA_256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    }
}
