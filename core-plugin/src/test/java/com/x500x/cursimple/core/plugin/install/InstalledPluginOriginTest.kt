package com.x500x.cursimple.core.plugin.install

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledPluginOriginTest {
    @Test fun `installed source survives reload independently of release repository`() {
        val record = InstalledPluginRecord(
            pluginId = "qa.plugin", name = "插件", version = "1", versionCode = 1,
            storagePath = "/qa", installedAt = "2026-10-04", source = PluginInstallSource.Remote,
            sourceRepo = "me/plugin", registrySource = "me/private-market",
        )
        val reloaded = Json.decodeFromString<InstalledPluginRecord>(Json.encodeToString(record))
        assertEquals("me/private-market", reloaded.registrySource)
        assertEquals("me/plugin", reloaded.sourceRepo)
    }

    @Test fun `old installed records remain compatible when source registry was not recorded`() {
        val old = """{"pluginId":"old","name":"插件","version":"1","versionCode":1,"storagePath":"/qa","installedAt":"2026-10-04","source":"remote","sourceRepo":"me/plugin"}"""
        val record = Json.decodeFromString<InstalledPluginRecord>(old)
        assertNull(record.registrySource)
        assertEquals("me/plugin", record.sourceRepo)
    }
}
