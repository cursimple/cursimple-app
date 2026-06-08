package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.component.PluginComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComponentMarketScreenTest {
    @Test
    fun `component market entry key includes version abi and download url`() {
        val arm64 = marketEntry(
            version = "1.0.0",
            abi = "arm64-v8a",
            downloadUrl = "https://example.com/runtime-arm64.zip",
        )
        val x64 = marketEntry(
            version = "1.0.0",
            abi = "x86_64",
            downloadUrl = "https://example.com/runtime-x64.zip",
        )
        val newer = marketEntry(
            version = "1.1.0",
            abi = "arm64-v8a",
            downloadUrl = "https://example.com/runtime-arm64-1.1.zip",
        )

        assertNotEquals(componentMarketEntryKey(arm64), componentMarketEntryKey(x64))
        assertNotEquals(componentMarketEntryKey(arm64), componentMarketEntryKey(newer))
        assertEquals(
            "runtime.onnx:1.0.0:arm64-v8a:https://example.com/runtime-arm64.zip",
            componentMarketEntryKey(arm64),
        )
    }

    private fun marketEntry(
        version: String,
        abi: String,
        downloadUrl: String,
    ): ComponentMarketEntry {
        return ComponentMarketEntry(
            id = "runtime.onnx",
            name = "Runtime",
            type = PluginComponentType.OnnxRuntime,
            version = version,
            abi = abi,
            downloadUrl = downloadUrl,
        )
    }
}
