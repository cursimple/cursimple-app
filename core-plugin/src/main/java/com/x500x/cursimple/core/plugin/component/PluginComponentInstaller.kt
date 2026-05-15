package com.x500x.cursimple.core.plugin.component

import android.os.Build
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream

class PluginComponentInstaller(
    private val componentRoot: File,
    private val repository: PluginComponentRepository,
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

    private suspend fun installPackage(
        bytes: ByteArray,
        source: PluginComponentSource,
    ): PluginComponentInstallResult {
        return runCatching {
            val layout = readComponentPackage(bytes)
            val manifest = json.decodeFromString<PluginComponentPackageManifest>(
                layout.requireFile(MANIFEST_FILE).toString(Charsets.UTF_8),
            )
            validateManifest(manifest, layout)
            val target = installLayout(manifest, layout)
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
            repository.save(record)
            PluginComponentInstallResult.Success(record)
        }.getOrElse { error ->
            PluginComponentInstallResult.Failure(
                PluginComponentInstallFailure(
                    code = "install_failed",
                    message = error.message ?: "组件安装失败",
                ),
            )
        }
    }

    private fun validateManifest(
        manifest: PluginComponentPackageManifest,
        layout: ComponentPackageLayout,
    ) {
        require(manifest.id.isNotBlank()) { "组件 manifest 缺少 id" }
        require(manifest.version.isNotBlank()) { "组件 manifest 缺少 version" }
        require(manifest.sha256.matches(SHA_256_REGEX)) { "组件 SHA-256 格式无效" }
        if (!manifest.abi.isNullOrBlank()) {
            require(manifest.abi in supportedAbis) { "组件 ABI 不兼容: ${manifest.abi}" }
        }
        val payloadFiles = manifest.files.ifEmpty {
            layout.files.keys.filterNot { it == MANIFEST_FILE }
        }
        require(payloadFiles.isNotEmpty()) { "组件包没有有效文件" }
        payloadFiles.forEach { file ->
            require(file in layout.files) { "组件包缺少文件: $file" }
        }
        val actual = sha256PayloadFiles(payloadFiles.sorted(), layout)
        require(actual.equals(manifest.sha256, ignoreCase = true)) { "组件 SHA-256 校验失败" }
    }

    private fun installLayout(
        manifest: PluginComponentPackageManifest,
        layout: ComponentPackageLayout,
    ): File {
        componentRoot.mkdirs()
        val safeId = manifest.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeVersion = manifest.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeAbi = manifest.abi?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "any"
        val targetDir = File(componentRoot, "$safeId-$safeVersion-$safeAbi")
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        val rootPath = targetDir.canonicalFile.toPath()
        layout.files
            .filterKeys { it != MANIFEST_FILE }
            .forEach { (path, bytes) ->
                val target = File(targetDir, path).canonicalFile
                require(target.toPath().startsWith(rootPath)) { "组件包路径越界: $path" }
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
        return targetDir
    }

    private fun readComponentPackage(bytes: ByteArray): ComponentPackageLayout {
        val files = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val normalized = normalizePackagePath(entry.name)
                    require(normalized !in files) { "组件包包含重复文件: $normalized" }
                    require(files.size < maxFileCount) { "组件包文件数量超过限制: $maxFileCount" }
                    val content = zip.readBytes()
                    totalBytes += content.size.toLong()
                    require(totalBytes <= maxUncompressedBytes) { "组件包解压后体积超过限制: $maxUncompressedBytes" }
                    files[normalized] = content
                }
                zip.closeEntry()
            }
        }
        require(MANIFEST_FILE in files) { "组件包缺少 manifest.json" }
        return ComponentPackageLayout(files)
    }

    private fun normalizePackagePath(rawPath: String): String {
        val path = rawPath.replace('\\', '/').trim()
        require(path.isNotBlank()) { "组件包包含空路径" }
        require(!path.startsWith("/")) { "组件包包含绝对路径: $rawPath" }
        require(!WINDOWS_DRIVE_PATH.matches(path)) { "组件包包含 Windows 盘符路径: $rawPath" }
        val segments = path.split('/')
        require(segments.none { it == ".." }) { "组件包包含路径穿越: $rawPath" }
        require(segments.none { it.isBlank() || it == "." }) { "组件包包含非法路径: $rawPath" }
        return segments.joinToString("/")
    }

    private data class ComponentPackageLayout(
        val files: Map<String, ByteArray>,
    ) {
        fun requireFile(path: String): ByteArray {
            return files[path] ?: throw IllegalArgumentException("组件包缺少文件: $path")
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
