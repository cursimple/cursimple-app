package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.security.PluginChecksums
import kotlinx.serialization.json.Json

data class PluginPackageLayout(
    val files: Map<String, ByteArray>,
) {
    fun requireFile(path: String): ByteArray {
        return files[path] ?: throw PluginArgumentException(
            R.string.plugin_error_package_missing_file,
            listOf(path),
        )
    }

    fun readText(path: String): String {
        val bytes = requireFile(path)
        if (path in setOf(MANIFEST_FILE, CHECKSUMS_FILE, SIGNATURE_FILE)) {
            require(bytes.size <= 1024 * 1024) { "Plugin metadata exceeds 1 MiB" }
        }
        return bytes.toString(Charsets.UTF_8).also {
            if (path in setOf(MANIFEST_FILE, CHECKSUMS_FILE, SIGNATURE_FILE)) requireBoundedPluginJson(it)
        }
    }

    fun decodeManifest(json: Json): PluginManifest {
        return json.decodeFromString(readText(MANIFEST_FILE))
    }

    fun decodeValidatedManifest(json: Json): PluginManifest {
        val manifest = decodeManifest(json)
        requireSafePluginId(manifest.id)
        val normalizedEntry = normalizePluginPackagePath(manifest.entry)
        pluginRequire(
            normalizedEntry in files,
            R.string.plugin_error_package_missing_entry_file,
            manifest.entry,
        )
        manifest.extension?.ui?.let { ui ->
            PluginExtensionUiPage.entries.forEach { page ->
                ui.entryFor(page)?.let { path ->
                    val normalizedUi = normalizePluginPackagePath(path)
                    pluginRequire(normalizedUi in files, R.string.plugin_error_package_missing_ui_file, path)
                }
            }
        }
        manifest.extension?.widgets.orEmpty().forEach { widget ->
            val path = normalizePluginPackagePath(widget.entry)
            pluginRequire(path in files, R.string.plugin_error_package_missing_ui_file, widget.entry)
        }
        manifest.home?.let { home ->
            val path = normalizePluginPackagePath(home.entry)
            pluginRequire(path in files, R.string.plugin_error_package_missing_ui_file, home.entry)
            require(home.sections.isNotEmpty() && home.sections.size <= 12) { "Invalid plugin home sections" }
            require(home.sections.map { it.id }.toSet().size == home.sections.size) { "Duplicate plugin home sections" }
            require(home.sections.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) }) { "Invalid plugin home section ID" }
            (listOf(home.runUrl, home.loginUrl) + home.sections.mapNotNull { it.url }).forEach { url ->
                val uri = java.net.URI(url)
                require(uri.scheme == "https" && uri.rawUserInfo == null && uri.host != null) { "Invalid plugin home URL" }
                require(manifest.allowedHosts.any { allowed ->
                    uri.host.equals(allowed, true) || uri.host.endsWith(".$allowed", true)
                }) { "Plugin home URL is outside declared hosts" }
            }
        }
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
