package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.security.PluginChecksums
import kotlinx.serialization.json.Json

data class PluginPackageLayout(
    val files: Map<String, ByteArray>,
) {
    fun requireFile(path: String): ByteArray {
        return files[path] ?: throw IllegalArgumentException("插件包缺少文件: $path")
    }

    fun readText(path: String): String = requireFile(path).toString(Charsets.UTF_8)

    fun decodeManifest(json: Json): PluginManifest {
        return json.decodeFromString(readText(MANIFEST_FILE))
    }

    fun decodeValidatedManifest(json: Json): PluginManifest {
        val manifest = decodeManifest(json)
        requireSafePluginId(manifest.id)
        val normalizedEntry = normalizePluginPackagePath(manifest.entry)
        require(normalizedEntry in files) { "插件包缺少入口文件: ${manifest.entry}" }
        return manifest.copy(entry = normalizedEntry)
    }

    fun decodeChecksums(json: Json): PluginChecksums {
        return json.decodeFromString(readText(CHECKSUMS_FILE))
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val CHECKSUMS_FILE = "checksums.json"
        const val SIGNATURE_FILE = "signature.json"
    }
}
