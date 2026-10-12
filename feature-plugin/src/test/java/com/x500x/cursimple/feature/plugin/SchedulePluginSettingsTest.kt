package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulePluginSettingsTest {
    @Test fun labelsFollowTheAppsLanguageWithAStableFallback() {
        val row = PluginTimetableRowSetting("midday", "显示午间课", titles = mapOf("en" to "Show midday classes", "zh-TW" to "顯示午間課"),
            startNode = 3, endNode = 3, slotLabel = "午间课")
        assertEquals("Show midday classes", localizedTimetableRowTitle(row, "en-US"))
        assertEquals("顯示午間課", localizedTimetableRowTitle(row, "zh-Hant-HK"))
        assertEquals("显示午间课", localizedTimetableRowTitle(row, "zh-CN"))
    }
}
