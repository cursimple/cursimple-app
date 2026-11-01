package com.x500x.cursimple.feature.plugin

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class WebViewSecurityTest {
    @Test fun upgradedLegacyLibrariesKeepSecurityFixesOnRealWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext)
            view.hardenLocalAccess()
            view.settings.javaScriptEnabled = true
            val assets = instrumentation.targetContext.assets
            val script = legacyDocumentStartScript(
                listOf("school.example.edu"),
                assets.open("webcompat/jquery-3.7.1.min.js").bufferedReader().use { it.readText() },
                assets.open("webcompat/jquery-migrate-3.6.0.min.js").bufferedReader().use { it.readText() },
            )
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(webView: WebView, url: String) {
                    webView.evaluateJavascript(script) {
                        webView.evaluateJavascript("""
                            (function() {
                              var jq = window.jQuery;
                              var value = JSON.parse('{"__proto__":{"cursimplePolluted":true}}');
                              jq.extend(true, {}, value);
                              return jq.fn.jquery === '3.7.1' && jq.migrateVersion === '3.6.0' &&
                                !({}).cursimplePolluted &&
                                !jq.migrateIsPatchEnabled('self-closed-tags') &&
                                jq.htmlPrefilter('<div/>') === '<div/>' &&
                                jq('<div>').text('课程').text() === '课程';
                            })()
                        """.trimIndent()) { value -> result.set(value); done.countDown() }
                    }
                }
            }
            view.loadDataWithBaseURL("https://school.example.edu/", "<html><body></body></html>", "text/html", "UTF-8", null)
        }
        try {
            assertTrue("WebView callback timed out", done.await(30, TimeUnit.SECONDS))
            assertEquals("true", result.get())
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Suppress("DEPRECATION")
    @Test fun localAccessAndMixedContentAreDisabledOnRealWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = WebView(instrumentation.targetContext)
            try {
                view.settings.allowFileAccess = true
                view.settings.allowContentAccess = true
                view.settings.allowUniversalAccessFromFileURLs = true
                view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                view.hardenLocalAccess()
                assertFalse(view.settings.allowFileAccess)
                assertFalse(view.settings.allowContentAccess)
                assertFalse(view.settings.allowFileAccessFromFileURLs)
                assertFalse(view.settings.allowUniversalAccessFromFileURLs)
                assertTrue(view.settings.safeBrowsingEnabled)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
            } finally {
                view.destroy()
            }
        }
    }
}
