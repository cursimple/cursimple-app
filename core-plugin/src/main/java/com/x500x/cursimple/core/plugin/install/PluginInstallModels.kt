package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.manifest.PluginWebEngineRequirement
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstalledPluginRecord(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String = "",
    @SerialName("version") val version: String,
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("apiVersion") val apiVersion: Int = 0,
    @SerialName("entry") val entry: String = "",
    @SerialName("storagePath") val storagePath: String,
    @SerialName("installedAt") val installedAt: String,
    @SerialName("source") val source: PluginInstallSource,
    @SerialName("permissions") val permissions: List<PluginPermission> = emptyList(),
    @SerialName("allowedHosts") val allowedHosts: List<String> = emptyList(),
    @SerialName("webEngine") val webEngine: PluginWebEngineRequirement = PluginWebEngineRequirement(),
    @SerialName("components") val components: List<PluginComponentRequirement> = emptyList(),
    @SerialName("compatibilityStatus") val compatibilityStatus: PluginCompatibilityStatus = PluginCompatibilityStatus.Compatible,
    @SerialName("compatibilityMessage") val compatibilityMessage: String? = null,
    @SerialName("isBundled") val isBundled: Boolean = false,
) {
    val declaredPermissions: List<PluginPermission> get() = permissions
}

@Serializable
enum class PluginCompatibilityStatus {
    @SerialName("compatible")
    Compatible,

    @SerialName("incompatible")
    Incompatible,
}

@Serializable
enum class PluginInstallSource {
    @SerialName("bundled")
    Bundled,

    @SerialName("local")
    Local,

    @SerialName("remote")
    Remote,
}

data class PluginInstallPreview(
    val manifest: PluginManifest,
    val checksumVerified: Boolean,
    val signatureVerified: Boolean,
    val signatureRequired: Boolean = true,
    val source: PluginInstallSource,
)

sealed interface PluginInstallResult {
    data class Success(val record: InstalledPluginRecord) : PluginInstallResult
    data class Failure(val message: String) : PluginInstallResult
}

interface PluginRegistryRepository {
    val installedPluginsFlow: Flow<List<InstalledPluginRecord>>

    suspend fun getInstalledPlugins(): List<InstalledPluginRecord>

    suspend fun find(pluginId: String): InstalledPluginRecord?

    suspend fun saveInstalledPlugin(record: InstalledPluginRecord)

    suspend fun removeInstalledPlugin(pluginId: String)
}
