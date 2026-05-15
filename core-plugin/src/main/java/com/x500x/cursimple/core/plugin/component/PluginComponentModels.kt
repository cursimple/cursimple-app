package com.x500x.cursimple.core.plugin.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginComponentType {
    @SerialName("engine_chromium")
    EngineChromium,

    @SerialName("opencv_native")
    OpenCvNative,

    @SerialName("onnx_runtime")
    OnnxRuntime,

    @SerialName("onnx_model")
    OnnxModel,

    @SerialName("generic_asset")
    GenericAsset,
}

@Serializable
enum class PluginComponentStatus {
    @SerialName("not_installed")
    NotInstalled,

    @SerialName("needs_consent")
    NeedsConsent,

    @SerialName("downloading")
    Downloading,

    @SerialName("installed")
    Installed,

    @SerialName("failed")
    Failed,

    @SerialName("incompatible")
    Incompatible,
}

@Serializable
data class InstalledPluginComponentRecord(
    @SerialName("id") val id: String,
    @SerialName("type") val type: PluginComponentType,
    @SerialName("version") val version: String,
    @SerialName("abi") val abi: String? = null,
    @SerialName("storagePath") val storagePath: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("source") val source: PluginComponentSource,
    @SerialName("installedAt") val installedAt: String,
    @SerialName("status") val status: PluginComponentStatus = PluginComponentStatus.Installed,
    @SerialName("message") val message: String? = null,
)

@Serializable
enum class PluginComponentSource {
    @SerialName("local")
    Local,

    @SerialName("remote")
    Remote,
}

@Serializable
data class PluginComponentPackageManifest(
    @SerialName("id") val id: String,
    @SerialName("type") val type: PluginComponentType,
    @SerialName("version") val version: String,
    @SerialName("abi") val abi: String? = null,
    @SerialName("sha256") val sha256: String,
    @SerialName("files") val files: List<String> = emptyList(),
)

sealed interface PluginComponentInstallResult {
    data class Success(val record: InstalledPluginComponentRecord) : PluginComponentInstallResult
    data class Failure(val reason: PluginComponentInstallFailure) : PluginComponentInstallResult
}

@Serializable
data class PluginComponentInstallFailure(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
)
