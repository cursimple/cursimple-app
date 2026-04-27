package com.kebiao.viewer.core.plugin.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginPermission {
    @SerialName("network")
    Network,

    @SerialName("web_session")
    WebSession,

    @SerialName("schedule_read")
    ScheduleRead,

    @SerialName("schedule_write")
    ScheduleWrite,

    @SerialName("ui_contribution")
    UiContribution,

    @SerialName("alarm_manage")
    AlarmManage,

    @SerialName("storage_plugin")
    StoragePlugin,

    @SerialName("notify")
    Notify,
}
