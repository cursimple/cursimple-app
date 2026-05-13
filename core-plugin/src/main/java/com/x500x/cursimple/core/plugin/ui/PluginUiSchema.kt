package com.x500x.cursimple.core.plugin.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginUiSchema(
    @SerialName("banners") val banners: List<BannerContribution> = emptyList(),
    @SerialName("actionButtons") val actionButtons: List<ActionButtonContribution> = emptyList(),
    @SerialName("courseBadges") val courseBadges: List<CourseBadgeRule> = emptyList(),
)

@Serializable
data class BannerContribution(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("message") val message: String,
)

@Serializable
data class ActionButtonContribution(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("actionType") val actionType: UiActionType,
    @SerialName("payload") val payload: String? = null,
)

@Serializable
enum class UiActionType {
    @SerialName("sync")
    Sync,

    @SerialName("open_market")
    OpenMarket,

    @SerialName("show_message")
    ShowMessage,
}

@Serializable
data class CourseBadgeRule(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("titleContains") val titleContains: String? = null,
    @SerialName("dayOfWeek") val dayOfWeek: Int? = null,
    @SerialName("startNode") val startNode: Int? = null,
    @SerialName("endNode") val endNode: Int? = null,
)
