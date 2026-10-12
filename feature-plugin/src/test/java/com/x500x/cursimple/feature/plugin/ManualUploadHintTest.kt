package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import com.x500x.cursimple.core.plugin.manifest.PluginNetworkCaptureSpec
import org.junit.Assert.*
import org.junit.Test

class ManualUploadHintTest {
    private val request = WebSessionRequest(token = "test", sessionId = "test-session", title = "课表", pluginId = "importer", startUrl = "https://login.example",
        allowedHosts = listOf("login.example", "courses.example", "proxy.example"), entryScript = "async function run() {}",
        manualUploadHintDelayMs = 5_000, networkCaptures = listOf(PluginNetworkCaptureSpec(id = "table", urlHost = "proxy.example", urlPathContains = "/eams/courseTable")))
    @Test fun fiveSecondsOnEitherCourseHostEnablesTheHint() {
        for (host in listOf("courses.example", "proxy.example")) {
            val url = "https://$host/eams/courseTable.action"
            assertFalse(shouldShowManualUploadHint(request, url, 1000, 5999, false))
            assertTrue(shouldShowManualUploadHint(request, url, 1000, 6000, false))
        }
    }
    @Test fun loginPagesBusyStatesAndUnrelatedSitesHaveNoHint() {
        assertFalse(shouldShowManualUploadHint(request, "https://login.example/login", 0, 6000, false))
        assertFalse(shouldShowManualUploadHint(request, "https://evil.example/eams/courseTable", 0, 6000, false))
        assertFalse(shouldShowManualUploadHint(request, "https://courses.example/eams/courseTable", 0, 6000, true))
    }
    @Test fun oldPluginsRemainOptedOutAndNavigationRestartsTheDelay() {
        val url = "https://courses.example/eams/courseTable"
        assertFalse(shouldShowManualUploadHint(request.copy(manualUploadHintDelayMs = null), url, 0, 10000, false))
        assertFalse(shouldShowManualUploadHint(request, url, 9000, 10000, false))
    }
}
