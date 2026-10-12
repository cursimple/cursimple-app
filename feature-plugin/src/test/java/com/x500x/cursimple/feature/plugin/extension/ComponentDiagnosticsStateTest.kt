package com.x500x.cursimple.feature.plugin.extension

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ComponentDiagnosticsStateTest {
    @Test fun enabledStateIsIndependentPersistentAndRemovedOnUninstall() = runBlocking {
        val directory = Files.createTempDirectory("component-tools").toFile()
        try {
            val store = ExtensionStore(directory)
            store.update("one") { it.copy(diagnosticsEnabled = true) }
            store.update("two") { it.copy(diagnosticsEnabled = true) }
            store.update("one") { it.copy(diagnosticsEnabled = false) }
            val restored = ExtensionStore(directory)
            assertFalse(restored.get("one").diagnosticsEnabled)
            assertTrue(restored.get("two").diagnosticsEnabled)
            restored.remove("two")
            assertFalse(ExtensionStore(directory).get("two").diagnosticsEnabled)
        } finally { directory.deleteRecursively() }
    }
    @Test fun existingInstallationsStartWithHiddenEntry() {
        val data = Json.decodeFromString<ExtensionData>("""{"pluginId":"old"}""")
        assertFalse(data.diagnosticsEnabled)
    }
}
