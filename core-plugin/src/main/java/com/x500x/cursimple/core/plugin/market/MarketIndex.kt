package com.x500x.cursimple.core.plugin.market

import com.x500x.cursimple.core.plugin.component.PluginComponentType
import com.x500x.cursimple.core.plugin.security.PluginSignatureInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarketIndexPayload(
    @SerialName("indexId") val indexId: String,
    @SerialName("generatedAt") val generatedAt: String,
    @SerialName("plugins") val plugins: List<MarketPluginEntry> = emptyList(),
)

@Serializable
data class SignedMarketIndex(
    @SerialName("payload") val payload: MarketIndexPayload,
    @SerialName("signature") val signature: PluginSignatureInfo,
)

@Serializable
data class MarketPluginEntry(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String,
    @SerialName("version") val version: String,
    @SerialName("downloadUrl") val downloadUrl: String,
    @SerialName("description") val description: String = "",
)

@Serializable
data class ComponentMarketIndexPayload(
    @SerialName("indexId") val indexId: String,
    @SerialName("generatedAt") val generatedAt: String,
    @SerialName("components") val components: List<ComponentMarketIndexEntry> = emptyList(),
)

@Serializable
data class ComponentMarketIndexEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: PluginComponentType,
    @SerialName("version") val version: String,
    @SerialName("abi") val abi: String? = null,
    @SerialName("downloadUrl") val downloadUrl: String? = null,
    @SerialName("description") val description: String = "",
)
