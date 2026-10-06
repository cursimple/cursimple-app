package com.x500x.cursimple.app.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.x500x.cursimple.core.data.widget.ComponentWidgetDefinition
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import kotlinx.serialization.json.Json
import com.x500x.cursimple.feature.widget.ComponentWidgetHit
import com.x500x.cursimple.feature.widget.ComponentWidgetRender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Isolated local HTML rendering. No credentials, network bridge or service-specific code. */
@SuppressLint("SetJavaScriptEnabled")
internal object ComponentWidgetRenderer {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun render(context: Context, record: InstalledPluginRecord, manifest: PluginManifest,
        data: ExtensionData, definition: ComponentWidgetDefinition, width: Float, height: Float): ComponentWidgetRender {
        val store = PluginFileStore(context)
        val html = withContext(Dispatchers.IO) { store.loadExtensionUi(record, definition.spec.entry) }
        return withTimeout(12_000) { withContext(Dispatchers.Main) {
            val token = UUID.randomUUID().toString()
            val ready = CompletableDeferred<String>()
            val density = context.resources.displayMetrics.density
            val w = (width * density).toInt().coerceIn(1, 2000)
            val h = (height * density).toInt().coerceIn(1, 2200)
            require(w.toLong() * h * 4 <= 12_000_000) { "Widget exceeds bitmap budget" }
            val state = JSONObject(codec.encodeToString(ExtensionData.serializer(), data)).apply {
                put("manifest", JSONObject(codec.encodeToString(PluginManifest.serializer(), manifest)))
                put("context", JSONObject().put("width", width).put("height", height).put("widgetId", definition.spec.id)
                    .put("fontScale", context.resources.configuration.fontScale)
                    .put("language", context.resources.configuration.locales[0].toLanguageTag())
                    .put("timeZone", com.x500x.cursimple.core.kernel.time.BeijingTime.zone.id)
                    .put("nowMillis", com.x500x.cursimple.core.kernel.time.BeijingTime.nowMillis(com.x500x.cursimple.core.kernel.time.BeijingTime.zone)))
            }
            val host = "$token.component-widget.invalid"
            val origin = "https://$host"
            val base = "$origin/assets/${definition.spec.entry}"
            val json = state.toString().replace("<", "\\u003c")
            val policy = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; base-uri 'none'\">"
            val script = policy + "<script>window.WebSocket=function(){throw new Error('Widget network unavailable')};window.CurSimpleWidget=Object.freeze({state:$json,ready:function(hits){WidgetRenderBridge.ready('$token',JSON.stringify(hits||[]));}});</script>"
            val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
            val document = if (head != null) html.replaceRange(head.range.last + 1, head.range.last + 1, script) else script + html
            val bridge = object {
                @JavascriptInterface fun ready(key: String, hits: String) {
                    if (key == token && hits.length <= 16_384) ready.complete(hits)
                }
            }
            val web = WebView(context)
            try {
                web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                web.settings.apply {
                    javaScriptEnabled = true
                    textZoom = 100
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = false
                    domStorageEnabled = false
                }
                web.addJavascriptInterface(bridge, "WidgetRenderBridge")
                web.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR)
                            android.util.Log.e("ComponentWidget", message.message().take(300))
                        return true
                    }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                        return runCatching {
                            val uri = requireNotNull(request).url
                            require(uri.scheme == "https" && uri.host == host)
                            require(uri.path.orEmpty().startsWith("/assets/"))
                            if (uri.toString() == base) return WebResourceResponse("text/html", "UTF-8", document.byteInputStream())
                            val file = store.readFile(record, uri.path.orEmpty().removePrefix("/assets/"))
                            require(file.isFile && file.length() <= 2_000_000)
                            val mime = when (file.extension) {
                                "js" -> "application/javascript"; "css" -> "text/css"; "svg" -> "image/svg+xml"
                                "png" -> "image/png"; "html" -> "text/html"; else -> "application/octet-stream"
                            }
                            WebResourceResponse(mime, "UTF-8", file.inputStream())
                        }.getOrElse { WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "".byteInputStream()) }
                    }
                }
                web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                web.layout(0, 0, w, h)
                web.loadUrl(base)
                val areas = JSONArray(ready.await())
                require(areas.length() <= 12)
                val hits = (0 until areas.length()).map { index ->
                    val area = areas.getJSONObject(index)
                    val x = area.getDouble("x").toFloat(); val y = area.getDouble("y").toFloat()
                    val aw = area.getDouble("width").toFloat(); val ah = area.getDouble("height").toFloat()
                    val action = area.optString("action", "feed")
                    require(listOf(x, y, aw, ah).all { it.isFinite() } && x >= 0 && y >= 0 && aw > 0 && ah > 0)
                    require(x + aw <= width + 1 && y + ah <= height + 1 && action in setOf("feed", "settings", "about"))
                    ComponentWidgetHit(x, y, aw, ah, action)
                }
                // Let the renderer commit the frame after the component's ready callback.
                delay(100)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                @Suppress("DEPRECATION")
                web.capturePicture().draw(Canvas(bitmap))
                ComponentWidgetRender(bitmap, hits)
            } finally {
                web.removeJavascriptInterface("WidgetRenderBridge")
                web.stopLoading()
                web.destroy()
            }
        } }
    }
}
