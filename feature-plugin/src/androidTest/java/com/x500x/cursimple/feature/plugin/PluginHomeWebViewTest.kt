package com.x500x.cursimple.feature.plugin

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PluginHomeWebViewTest {
    @get:Rule val compose = createComposeRule()

    @SuppressLint("SetJavaScriptEnabled")
    @Test fun percentageHeightPortalRendersItsEntriesAndResizes() {
        val loaded = AtomicBoolean(false)
        val height = mutableStateOf(400.dp)
        lateinit var webView: WebView
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(48.dp))
                AndroidView(
                    factory = { context ->
                        webView = WebView(context).apply {
                            applyPluginBrowserSettings("")
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                                    WebResourceResponse("text/html", "UTF-8", """
                                        <!doctype html><html><head>
                                        <meta name="viewport" content="width=device-width,initial-scale=1">
                                        <style>html,body,#app{height:100%;margin:0}</style></head>
                                        <body><div id="app"></div><script>
                                        if (document.getElementById('app').clientHeight > 0) {
                                          document.getElementById('app').textContent = 'Academic system';
                                        }
                                        </script></body></html>
                                    """.trimIndent().byteInputStream())

                                override fun onPageFinished(view: WebView?, url: String?) { loaded.set(true) }
                            }
                        }
                        createPluginHomeWebViewHost(context, webView, "https://portal.example.edu/")
                    },
                    modifier = Modifier.fillMaxWidth().height(height.value),
                )
            }
        }
        compose.waitUntil(15_000) { loaded.get() }
        fun evaluate(script: String): String {
            val result = AtomicReference<String?>(null)
            compose.runOnIdle { webView.evaluateJavascript(script) { result.set(it) } }
            compose.waitUntil(5_000) { result.get() != null }
            return result.get()!!
        }
        try {
            assertEquals("\"Academic system\"", evaluate("document.getElementById('app').textContent"))
            val original = evaluate("document.getElementById('app').clientHeight").toInt()
            assertTrue(original > 100)
            compose.runOnIdle { height.value = 220.dp }
            compose.waitForIdle()
            val resized = evaluate("document.getElementById('app').clientHeight").toInt()
            assertTrue(resized in 100 until original)
            assertEquals(resized.toString(), evaluate("document.documentElement.clientHeight"))
        } finally {
            compose.runOnIdle { webView.stopLoading(); webView.destroy() }
        }
    }
}
