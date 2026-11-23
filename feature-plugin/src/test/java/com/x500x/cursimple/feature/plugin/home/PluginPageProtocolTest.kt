package com.x500x.cursimple.feature.plugin.home

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginPageProtocolTest {
    private val hosts = listOf("atrust.yangtzeu.edu.cn")

    @Test fun storageKeysAreShortAndSafe() {
        assertTrue(validStorageKey("state"))
        assertTrue(validStorageKey("cache.v2_a-b"))
        assertFalse(validStorageKey(""))
        assertFalse(validStorageKey("../state"))
        assertFalse(validStorageKey("a".repeat(65)))
    }

    @Test fun actionIdsAreShortAndSafe() {
        assertTrue(validActionId("check"))
        assertFalse(validActionId("save page"))
        assertFalse(validActionId(""))
    }

    @Test fun signInAddressesMustBeHttpsOnADeclaredHostWithoutCredentials() {
        assertTrue(validLoginUrl("https://atrust.yangtzeu.edu.cn:4443/", hosts))
        assertFalse(validLoginUrl("http://atrust.yangtzeu.edu.cn/", hosts))
        assertFalse(validLoginUrl("https://u:p@atrust.yangtzeu.edu.cn/", hosts))
        assertFalse(validLoginUrl("https://evil.example/", hosts))
        assertFalse(validLoginUrl("not a url", hosts))
    }

    @Test fun loginActionsDefaultToNoCaptureAndAreCapped() {
        val actions = parseLoginActions(buildJsonArray {
            add(buildJsonObject { put("id", "check"); put("label", "Check") })
            add(buildJsonObject { put("id", "save"); put("label", "Save"); put("capture", true) })
        })
        assertEquals(listOf(false, true), actions.map { it.capture })
        assertThrows(PluginPageCodeException::class.java) {
            parseLoginActions(buildJsonArray { repeat(4) { add(buildJsonObject { put("id", "a$it"); put("label", "L") }) } })
        }
    }

    @Test fun loginActionsNeedAnIdAndALabel() {
        assertThrows(PluginPageCodeException::class.java) {
            parseLoginActions(buildJsonArray { add(buildJsonObject { put("label", "No id") }) })
        }
        assertThrows(PluginPageCodeException::class.java) {
            parseLoginActions(buildJsonArray { add(buildJsonObject { put("id", "check"); put("label", "") }) })
        }
    }

    @Test fun noActionsMeansAnEmptyList() {
        assertTrue(parseLoginActions(null).isEmpty())
        assertTrue(parseLoginActions(JsonPrimitive("nope")).isEmpty())
    }

    @Test fun capturedChinesePagesUseTheSameByteLimitAsTheRuntime() {
        val chinese = "课".repeat(MAX_CAPTURED_PAGE_CHARS / 3)
        assertEquals(chinese, acceptedCapturedPage(chinese))
        assertNull(acceptedCapturedPage(chinese + "课"))
        assertNull(acceptedCapturedPage("a".repeat(MAX_CAPTURED_PAGE_CHARS + 1)))
        assertNull(acceptedCapturedPage(null))
    }
}
