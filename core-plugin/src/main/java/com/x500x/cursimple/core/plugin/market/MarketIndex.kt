package com.x500x.cursimple.core.plugin.market

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
