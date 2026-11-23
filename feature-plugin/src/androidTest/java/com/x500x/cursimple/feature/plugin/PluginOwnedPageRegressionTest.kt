package com.x500x.cursimple.feature.plugin

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.home.PAGE_BRIDGE_NAME
import com.x500x.cursimple.feature.plugin.home.PluginPageJsBridge
import com.x500x.cursimple.feature.plugin.home.PluginPageStorage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Exercises an externally supplied plugin page; the host test contains no school-specific renderer. */
class PluginOwnedPageRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chinesePageStoresResultsReopensOfflineAndHandlesBack() = exercise(false, "zh-CN", false)

    @Test fun darkBlueNarrowPageKeepsFiltersReadableAtLargeText() = exercise(true, "zh-TW", true)

    @Test fun englishPageUsesTheSameBridgeAndStorage() = exercise(false, "en", false)

    @SuppressLint("SetJavaScriptEnabled")
    private fun exercise(dark: Boolean, locale: String, narrow: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pageFixtureQa") == "true")
        check(context.packageName == "com.x500x.cursimple.feature.plugin.test")
        val htmlFile = File(context.getExternalFilesDir(null), "page-qa.html")
        check(htmlFile.isFile) { "Copy the current plugin-owned HTML to ${htmlFile.path} before running this test." }
        val html = htmlFile.readText()
        val storage = PluginPageStorage(context, "page-fixture-${UUID.randomUUID()}")
        val runCount = AtomicInteger()
        val networkCount = AtomicInteger()
        val requestedUrls = ConcurrentLinkedQueue<String>()
        val account = buildJsonObject { put("id", "demo"); put("name", "演示同学"); put("school", "演示大学") }
        val section = buildJsonObject {
            put("id", "grades")
            put("columns", buildJsonArray { listOf("课程名称", "总评成绩", "学分", "学年学期").forEach { add(it) } })
            put("rows", buildJsonArray {
                add(buildJsonArray { listOf("数据库原理", "88", "3", "2025-2026-1").forEach { add(it) } })
                add(buildJsonArray { listOf("程序设计", "95", "2", "2025-2026-2").forEach { add(it) } })
            })
            put("summary", buildJsonArray { add(buildJsonObject { put("label", "已获学分 / 总学分"); put("value", "106.5 / 175") }) })
        }
        lateinit var view: WebView
        fun reply(id: String, value: JsonObject) {
            view.post { view.evaluateJavascript("CurSimplePage.__resolve(${jsStringLiteral(id)},true,${jsStringLiteral(value.toString())})", null) }
        }
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                AndroidView(
                    factory = { nativeContext ->
                        WebView(nativeContext).also { browser ->
                            view = browser
                            browser.hardenLocalAccess()
                            browser.settings.javaScriptEnabled = true
                            browser.settings.blockNetworkLoads = true
                            browser.settings.textZoom = if (narrow) 130 else 100
                            browser.webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse {
                                    val uri = request?.url
                                    requestedUrls.add(uri.toString())
                                    // loadData uses a data: document and may request a local icon.
                                    val local = uri?.scheme == "data" ||
                                        (uri?.scheme == "https" && uri.host == "plugin-page.cursimple.invalid")
                                    if (!local) networkCount.incrementAndGet()
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                                }
                            }
                            browser.addJavascriptInterface(PluginPageJsBridge { id, method, raw ->
                                val args = Json.parseToJsonElement(raw).jsonObject
                                val result = when (method) {
                                    "init" -> buildJsonObject {
                                        put("locale", locale); put("credentialsSaved", false)
                                        put("theme", buildJsonObject {
                                            put("background", if (dark) "#111820" else "#f5f8f6")
                                            put("surface", if (dark) "#182431" else "#ffffff")
                                            put("surfaceContainerLow", if (dark) "#1c2a38" else "#eef4f0")
                                            put("onSurface", if (dark) "#eef2f9" else "#1b2a22")
                                            put("onSurfaceVariant", if (dark) "#c5cfde" else "#4d6357")
                                            put("primary", if (dark) "#b2cdff" else "#2e7d5b")
                                            put("onPrimary", if (dark) "#102e55" else "#ffffff")
                                            put("primaryContainer", if (dark) "#253e65" else "#c9ecd9")
                                            put("onPrimaryContainer", if (dark) "#dce8ff" else "#0a3d26")
                                            put("outlineVariant", if (dark) "#64748b" else "#c3d2c8")
                                        })
                                    }
                                    "storageGet" -> buildJsonObject { put("value", storage.get(args.getValue("key").jsonPrimitive.content)?.let(::JsonPrimitive) ?: JsonNull) }
                                    "storageSet" -> {
                                        storage.set(args.getValue("key").jsonPrimitive.content, args.getValue("value").jsonPrimitive.content)
                                        JsonObject(emptyMap())
                                    }
                                    "run" -> {
                                        runCount.incrementAndGet()
                                        buildJsonObject { put("loggedIn", true); put("account", account); put("section", section) }
                                    }
                                    else -> JsonObject(emptyMap())
                                }
                                reply(id, result)
                            }, PAGE_BRIDGE_NAME)
                            browser.loadDataWithBaseURL(
                                "https://plugin-page.cursimple.invalid/", pluginPageDocument(html), "text/html", "utf-8", null,
                            )
                        }
                    },
                    modifier = if (narrow) Modifier.width(320.dp).fillMaxSize() else Modifier.fillMaxSize(),
                )
            }
        }
        fun evaluate(script: String): String {
            val result = AtomicReference<String?>()
            compose.runOnUiThread { view.evaluateJavascript(script) { result.set(it) } }
            compose.waitUntil(5_000) { result.get() != null }
            return result.get()!!
        }
        fun waitFor(script: String) = compose.waitUntil(10_000) { evaluate(script) == "true" }
        try {
            waitFor("!!document.querySelector('[data-act=\"check\"]')")
            assertEquals(0, runCount.get())
            evaluate("document.querySelector('[data-act=\"check\"]').click()")
            waitFor("document.body.textContent.indexOf('演示同学') >= 0 && !S.busy")
            evaluate("document.querySelector('[data-act=\"open\"][data-v=\"grades\"]').click()")
            waitFor("document.body.textContent.indexOf('数据库原理') >= 0 && !S.busy")
            assertEquals(2, runCount.get())
            evaluate("document.querySelector('[data-act=\"refresh\"]').click()")
            waitFor("document.body.textContent.indexOf('数据库原理') >= 0 && !S.busy")
            assertEquals(3, runCount.get())
            assertEquals("\"106.5 / 175\"", evaluate("document.querySelector('.block .v').textContent"))
            assertEquals("\"学年学期\"", evaluate("document.querySelector('.filter-tab[aria-selected=\"true\"], .filter-only').textContent"))
            assertEquals("false", evaluate("!!document.querySelector('select[data-act=\"filter-column\"]')"))
            evaluate("document.querySelector('[data-act=\"filter-column\"][data-v=\"2\"]').click()")
            assertEquals("\"学分\"", evaluate("document.querySelector('.filter-tab[aria-selected=\"true\"]').textContent"))
            evaluate("document.querySelector('[data-act=\"filter\"][data-v=\"3\"]').click()")
            assertEquals("true", evaluate("document.body.textContent.indexOf('数据库原理') >= 0"))
            assertEquals("false", evaluate("document.body.textContent.indexOf('程序设计') >= 0"))
            evaluate("document.querySelector('[data-act=\"filter-column\"][data-v=\"3\"]').click()")
            evaluate("document.querySelector('[data-act=\"filter\"][data-v=\"2025-2026-2\"]').click()")
            assertEquals("false", evaluate("document.body.textContent.indexOf('数据库原理') >= 0"))
            assertEquals("true", evaluate("document.body.textContent.indexOf('程序设计') >= 0"))
            assertEquals("true", evaluate("document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
            val screenshot = File(context.getExternalFilesDir(null), "page-qa-$locale.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { image ->
                screenshot.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            evaluate("window.dispatchEvent(new Event('cursimple-back'))")
            assertEquals("false", evaluate("window.__CurSimpleComponentHasBack"))
            // loadData is asynchronous: wait for a new document, not the previous menu's text.
            evaluate("window.__qaPreviousDocument = true")
            compose.runOnUiThread {
                view.loadDataWithBaseURL("https://plugin-page.cursimple.invalid/", pluginPageDocument(html), "text/html", "utf-8", null)
            }
            waitFor("!window.__qaPreviousDocument && document.body.textContent.indexOf('演示同学') >= 0 && !!document.querySelector('[data-act=\"open\"]')")
            evaluate("document.querySelector('[data-act=\"open\"][data-v=\"grades\"]').click()")
            assertEquals("true", evaluate("document.body.textContent.indexOf('数据库原理') >= 0"))
            assertEquals(3, runCount.get())
            assertEquals("no page may fetch a school URL; requested: $requestedUrls", 0, networkCount.get())
        } finally {
            compose.runOnUiThread { view.removeJavascriptInterface(PAGE_BRIDGE_NAME); view.stopLoading(); view.destroy() }
            storage.clear()
        }
    }
}
