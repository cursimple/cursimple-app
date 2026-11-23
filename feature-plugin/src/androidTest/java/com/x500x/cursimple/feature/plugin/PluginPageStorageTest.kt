package com.x500x.cursimple.feature.plugin

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.home.MAX_STORAGE_VALUE_CHARS
import com.x500x.cursimple.feature.plugin.home.PluginPageCodeException
import com.x500x.cursimple.feature.plugin.home.PluginPageStorage
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PluginPageStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedStoragePersistsAcrossInstancesAndIsolatesInstallations() {
        check(context.packageName == "com.x500x.cursimple.feature.plugin.test")
        val key = "page-qa-${UUID.randomUUID()}"
        val local = PluginPageStorage(context, "$key:local")
        val remote = PluginPageStorage(context, "$key:remote")
        try {
            local.set("state", "演示缓存 fixture-academic-result")
            assertEquals("演示缓存 fixture-academic-result", PluginPageStorage(context, "$key:local").get("state"))
            assertNull(remote.get("state"))
            val root = File(context.filesDir, "plugin-page-storage")
            assertTrue(root.listFiles().orEmpty().none { it.readBytes().toString(Charsets.UTF_8).contains("fixture-academic-result") })
            local.remove("state")
            assertNull(PluginPageStorage(context, "$key:local").get("state"))
        } finally {
            local.clear()
            remote.clear()
        }
    }

    @Test fun refusedOversizedWriteKeepsThePreviousCache() {
        val owner = "page-qa-${UUID.randomUUID()}"
        val storage = PluginPageStorage(context, owner)
        try {
            storage.set("state", "previous")
            assertThrows(PluginPageCodeException::class.java) {
                storage.set("state", "x".repeat(MAX_STORAGE_VALUE_CHARS + 1))
            }
            assertEquals("previous", PluginPageStorage(context, owner).get("state"))
        } finally {
            storage.clear()
        }
    }
}
