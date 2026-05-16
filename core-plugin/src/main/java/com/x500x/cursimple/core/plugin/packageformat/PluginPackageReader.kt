package com.x500x.cursimple.core.plugin.packageformat

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

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
                    val normalizedPath = normalizeEntryPath(entry.name)
                    require(normalizedPath !in files) { "插件包包含重复文件: $normalizedPath" }
                    require(files.size < maxFileCount) { "插件包文件数量超过限制: $maxFileCount" }
                    val content = zip.readBytes()
                    totalBytes += content.size.toLong()
                    require(totalBytes <= maxUncompressedBytes) { "插件包解压后体积超过限制: $maxUncompressedBytes" }
                    files[normalizedPath] = content
                }
                zip.closeEntry()
            }
        }
        val layout = PluginPackageLayout(normalizePackageRoot(files))
        val manifest = layout.decodeManifest(json)
        require(manifest.entry.isNotBlank()) { "插件 manifest 缺少 entry" }
        val entryPath = normalizeEntryPath(manifest.entry)
        require(entryPath in layout.files) { "插件包缺少入口文件: ${manifest.entry}" }
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

    private fun normalizeEntryPath(rawPath: String): String {
        val path = rawPath.replace('\\', '/').trim()
        require(path.isNotBlank()) { "插件包包含空路径" }
        require(!path.startsWith("/")) { "插件包包含绝对路径: $rawPath" }
        require(!WINDOWS_DRIVE_PATH.matches(path)) { "插件包包含 Windows 盘符路径: $rawPath" }
        val segments = path.split('/')
        require(segments.none { it == ".." }) { "插件包包含路径穿越: $rawPath" }
        require(segments.none { it.isBlank() || it == "." }) { "插件包包含非法路径: $rawPath" }
        return segments.joinToString("/")
    }

    private companion object {
        const val DEFAULT_MAX_FILE_COUNT = 512
        const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L
        val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:.*")
    }
}
