package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.pluginRequireNotNull
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

class PluginPackageReader(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val maxFileCount: Int = DEFAULT_MAX_FILE_COUNT,
    private val maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
) {
    fun read(bytes: ByteArray): PluginPackageLayout {
        val files = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val normalizedPath = normalizePluginPackagePath(entry.name)
                    pluginRequire(
                        normalizedPath !in files,
                        R.string.plugin_error_package_duplicate_file,
                        normalizedPath,
                    )
                    pluginRequire(
                        files.size < maxFileCount,
                        R.string.plugin_error_package_file_count_exceeded,
                        maxFileCount,
                    )
                    val content = pluginRequireNotNull(
                        zip.readAtMostBytes(maxUncompressedBytes - totalBytes),
                        R.string.plugin_error_package_size_exceeded,
                        maxUncompressedBytes,
                    )
                    totalBytes += content.size.toLong()
                    files[normalizedPath] = content
                }
                zip.closeEntry()
            }
        }
        val layout = PluginPackageLayout(normalizePackageRoot(files))
        val manifest = layout.decodeValidatedManifest(json)
        pluginRequire(manifest.entry.isNotBlank(), R.string.plugin_error_manifest_missing_entry)
        return layout
    }

    private fun normalizePackageRoot(files: Map<String, ByteArray>): Map<String, ByteArray> {
        if (PluginPackageLayout.MANIFEST_FILE in files) {
            return files
        }
        val rootNames = files.keys.map { it.substringBefore('/') }.toSet()
        if (rootNames.size != 1) {
            return files
        }
        val rootPrefix = "${rootNames.single()}/"
        val rootManifest = "$rootPrefix${PluginPackageLayout.MANIFEST_FILE}"
        if (rootManifest !in files) {
            return files
        }
        return files.mapKeys { (path, _) -> path.removePrefix(rootPrefix) }
    }

    private companion object {
        const val DEFAULT_MAX_FILE_COUNT = 512
        const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L
    }
}

internal fun normalizePluginPackagePath(rawPath: String): String {
    val path = rawPath.replace('\\', '/').trim()
    pluginRequire(path.isNotBlank(), R.string.plugin_error_package_blank_path)
    pluginRequire(!path.startsWith("/"), R.string.plugin_error_package_absolute_path, rawPath)
    pluginRequire(
        !WINDOWS_DRIVE_PATH.matches(path),
        R.string.plugin_error_package_windows_drive_path,
        rawPath,
    )
    val segments = path.split('/')
    pluginRequire(segments.none { it == ".." }, R.string.plugin_error_package_path_traversal, rawPath)
    pluginRequire(
        segments.none { it.isBlank() || it == "." },
        R.string.plugin_error_package_illegal_path,
        rawPath,
    )
    return segments.joinToString("/")
}

internal fun requireSafePluginId(id: String): String {
    pluginRequire(SAFE_PLUGIN_ID.matches(id), R.string.plugin_error_plugin_id_charset, id)
    return id
}

private val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:.*")
private val SAFE_PLUGIN_ID = Regex("[A-Za-z0-9._-]+")
