package com.x500x.cursimple.core.plugin.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PluginTimetableSchemaTest {
    @Test fun olderUiSchemasStayCompatibleWithoutSettings() {
        val schema = Json.decodeFromString<PluginUiSchema>("""{"banners":[],"actionButtons":[],"courseBadges":[]}""")
        assertTrue(schema.timetableRows.isEmpty())
        assertTrue(schema.timetableRowFormat.isEmpty())
    }
    @Test fun timetableRowSettingsDefaultToVisibleAndRetainPluginLabels() {
        val schema = Json.decodeFromString<PluginUiSchema>("""{"timetableRows":[{"id":"midday","title":"显示午间课","startNode":3,"endNode":3,"slotLabel":"午间课"}]}""")
        assertTrue(schema.timetableRows.single().defaultVisible)
        assertEquals("午间课", schema.timetableRows.single().slotLabel)
    }
}
