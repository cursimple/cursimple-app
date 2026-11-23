package com.x500x.cursimple.feature.plugin

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.x500x.cursimple.feature.plugin.home.PAGE_BRIDGE_NAME
import com.x500x.cursimple.feature.plugin.home.PluginPageJsBridge
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Local documents only: these tests never contact a school or use the user's credentials. */
class PluginPageBridgeTest {
    @get:Rule val compose = createComposeRule()

    @SuppressLint("SetJavaScriptEnabled")
    private fun check(script: String, expected: String, reply: String? = null, expectCalls: Int = 1) {
        val calls = AtomicInteger()
        lateinit var view: WebView
        compose.setContent {
            AndroidView(
                factory = { context ->
                    WebView(context).also { browser ->
                        view = browser
                        browser.hardenLocalAccess()
                        browser.settings.javaScriptEnabled = true
                        browser.settings.blockNetworkLoads = true
                        browser.addJavascriptInterface(PluginPageJsBridge { id, _, _ ->
                            calls.incrementAndGet()
                            if (reply != null) browser.post {
                                browser.evaluateJavascript(
                                    "CurSimplePage.__resolve(${jsStringLiteral(id)}, true, ${jsStringLiteral(reply)})", null,
                                )
                            }
                        }, PAGE_BRIDGE_NAME)
                        browser.loadDataWithBaseURL(
                            "https://plugin-page.cursimple.invalid/",
                            pluginPageDocument("<html><head></head><body><script>$script</script></body></html>"),
                            "text/html", "utf-8", null,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val result = AtomicReference<String?>()
        try {
            compose.waitUntil(10_000) {
                compose.runOnUiThread { view.evaluateJavascript("window.result || ''") { result.set(it) } }
                result.get() == jsStringLiteral(expected)
            }
            assertEquals(expectCalls, calls.get())
        } finally {
            compose.runOnUiThread {
                view.removeJavascriptInterface(PAGE_BRIDGE_NAME)
                view.stopLoading()
                view.destroy()
            }
        }
    }

    @Test fun missingHostReplyEndsWithARecoverableTimeout() = check("""
        var later = window.setTimeout;
        window.setTimeout = function(fn, ms) { return later(fn, ms >= 10000 ? 20 : ms); };
        CurSimplePage.call('init').then(function() { window.result = 'unexpected'; },
          function(e) { window.result = e.code; });
    """, "bridge_timeout")

    @Test fun malformedHostReplyRejectsInsteadOfLeavingThePageBusy() = check("""
        CurSimplePage.call('init').then(function() { window.result = 'unexpected'; },
          function(e) { window.result = e.code; });
    """, "invalid_response", reply = "not json")

    @Test fun oversizedRequestsFailBeforeReachingTheNativeBridge() = check("""
        CurSimplePage.call('storageSet', {value: 'x'.repeat(5000001)}).catch(function(e) { window.result = e.code; });
    """, "payload_too_large", expectCalls = 0)

    @Test fun cyclicArgumentsFailWithAnExplicitReason() = check("""
        var value = {}; value.self = value;
        CurSimplePage.call('storageSet', value).catch(function(e) { window.result = e.code; });
    """, "invalid_argument", expectCalls = 0)

    @Test fun normalResponsesStillReachThePlugin() = check("""
        CurSimplePage.call('init').then(function(value) { window.result = value.locale; });
    """, "zh-CN", reply = """{"locale":"zh-CN"}""")
}
