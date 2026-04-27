package com.kebiao.viewer.core.plugin.install

import com.kebiao.viewer.core.plugin.manifest.PluginManifest
import com.kebiao.viewer.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstalledPluginRecord(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String,
    @SerialName("version") val version: String,
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("storagePath") val storagePath: String,
    @SerialName("installedAt") val installedAt: String,
    @SerialName("source") val source: PluginInstallSource,
    @SerialName("declaredPermissions") val declaredPermissions: List<PluginPermission>,
    @SerialName("allowedHosts") val allowedHosts: List<String>,
    @SerialName("isBundled") val isBundled: Boolean = false,
)

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
