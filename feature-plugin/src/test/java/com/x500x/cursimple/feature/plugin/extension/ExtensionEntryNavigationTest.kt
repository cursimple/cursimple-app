package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ExtensionEntryNavigationTest {
    private val request = ExtensionRunRequest(
        pluginId = "school-test", mode = ExtensionRunMode.Sync,
        url = "https://academic.school.example/eams/home.action",
        entrySource = "", allowedHosts = listOf("school.example"), permissions = emptySet(),
        userAgent = null, settings = JsonObject(emptyMap()), state = JsonObject(emptyMap()),
        timeoutMs = 10_000, maxOutputBytes = 64 * 1024, waitForRunUrl = true,
    )

    @Test fun schoolQueryWaitsThroughSsoUntilTheAcademicEntryLoads() {
        val navigation = listOf(
            "https://gateway.school.example/portal/shortcut.html",
            "https://cas.school.example/login",
            "https://academic.school.example/eams/localLogin.action",
            "https://academic.school.example/eams/home.action?language=zh",
        )
        assertEquals(navigation.last(), navigation.firstOrNull { extensionEntryPageReached(request, it) })
    }

    @Test fun lookalikeSitesAndDifferentPortsCannotStartTheQuery() {
        assertFalse(extensionEntryPageReached(request, "https://academic.school.example.attacker.test/eams/home.action"))
        assertFalse(extensionEntryPageReached(request, "https://academic.school.example:4443/eams/home.action"))
        assertFalse(extensionEntryPageReached(request, "http://academic.school.example/eams/home.action"))
        assertFalse(extensionEntryPageReached(request, "https://user@academic.school.example/eams/home.action"))
        assertTrue(extensionEntryPageReached(request, "https://academic.school.example:443/eams/home.action"))
    }

    @Test fun existingComponentsKeepTheirDeclaredRedirectBehavior() {
        assertTrue(extensionEntryPageReached(request.copy(waitForRunUrl = false), "https://cas.school.example/login"))
    }

    @Test fun offlinePageCaptureDoesNotWaitForAnOnlineNavigation() {
        assertTrue(extensionEntryPageReached(request.copy(isolated = true), "https://cursimple-extension.invalid/"))
    }
}
