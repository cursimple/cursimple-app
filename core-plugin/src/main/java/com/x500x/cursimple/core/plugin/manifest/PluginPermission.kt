package com.x500x.cursimple.core.plugin.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginPermission(val id: String) {
    @SerialName("web.navigate")
    WebNavigate("web.navigate"),

    @SerialName("web.read_dom")
    WebReadDom("web.read_dom"),

    @SerialName("web.read_cookies")
    WebReadCookies("web.read_cookies"),

    @SerialName("web.inject_script")
    WebInjectScript("web.inject_script"),

    @SerialName("web.capture_packet")
    WebCapturePacket("web.capture_packet"),

    @SerialName("network.fetch")
    NetworkFetch("network.fetch"),

    @SerialName("schedule.write")
    ScheduleWrite("schedule.write"),

    @SerialName("storage.plugin")
    StoragePlugin("storage.plugin"),

    @SerialName("component.use")
    ComponentUse("component.use"),

    /** 扩展组件交出条目（作业、公告……）给宿主做通知与日历 */
    @SerialName("feed.write")
    FeedWrite("feed.write"),

    ;

    companion object {
        val Network: PluginPermission = NetworkFetch
    }
}
