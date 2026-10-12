package com.x500x.cursimple.core.plugin.ui

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginUiSchema(
    @SerialName("banners") val banners: List<BannerContribution> = emptyList(),
    @SerialName("actionButtons") val actionButtons: List<ActionButtonContribution> = emptyList(),
    @SerialName("courseBadges") val courseBadges: List<CourseBadgeRule> = emptyList(),
    @SerialName("timetableRows") val timetableRows: List<PluginTimetableRowSetting> = emptyList(),
    @SerialName("timetableRowFormat") val timetableRowFormat: List<ClassSlotTime> = emptyList(),
)

/** Optional display rows belong to the importing plugin; time and period data remain intact. */
@Serializable
data class PluginTimetableRowSetting(
    val id: String,
    val title: String,
    val titles: Map<String, String> = emptyMap(),
    val startNode: Int,
    val endNode: Int,
    val slotLabel: String,
    val defaultVisible: Boolean = true,
    val startTime: String = "",
    val endTime: String = "",
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
