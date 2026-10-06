package com.x500x.cursimple.feature.plugin.extension

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.booleanOrNull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID

/**
 * Business pages belong to components; the host provides generic configuration, session, sync
 * and navigation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ExtensionOwnedPage(
    record: InstalledPluginRecord,
    manifest: PluginManifest,
    data: ExtensionData,
    html: String,
    page: PluginExtensionUiPage,
    actions: ExtensionHostActions,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemove: () -> Unit = {},
    openWidgetAbout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    com.x500x.cursimple.feature.plugin.ui.OwnEmbeddedPageGestures()
    val context = LocalContext.current
    val assetStore = remember(context) { PluginFileStore(context) }
    val scope = rememberCoroutineScope()
    val store = remember { ExtensionStore.get(context) }
    val downloads = remember(record.pluginId) { ExtensionDownloads(context, record.pluginId) }
    val currentData by rememberUpdatedState(data)
    val currentActions by rememberUpdatedState(actions)
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    var activePage by remember(record.packageRevision, page) { mutableStateOf(page) }
    var source by remember(record.packageRevision, page, html) { mutableStateOf<String?>(html) }
    var ready by remember(record.packageRevision, activePage, source) { mutableStateOf(false) }
    var loadError by remember(record.packageRevision, activePage) { mutableStateOf<String?>(null) }
    var removing by remember(record.packageRevision) { mutableStateOf(false) }
    var removeConfirmation by remember(record.packageRevision) { mutableStateOf(false) }
    LaunchedEffect(record.packageRevision, activePage, html) {
        if (!actions.isCurrent(record)) { loadError = "组件已更新或移除"; return@LaunchedEffect }
        store.update(record.pluginId) { it }
        source = if (activePage == page) html else actions.loadUi(record, activePage)
        if (source == null) loadError = "组件未提供此页面"
    }
    BackHandler(enabled = activePage != page) { activePage = page }
    val spec = requireNotNull(manifest.extension)
    val settings = ExtensionUrls.effectiveSettings(spec, data.settings)
    val loginUrl = ExtensionUrls.resolve(spec.loginUrl, settings)
    val origin = if (ExtensionUrls.isAllowed(loginUrl, manifest.allowedHosts)) {
        val uri = URI(loginUrl)
        "${uri.scheme}://${uri.rawAuthority}"
    } else "https://cursimple-extension.invalid"
    val currentOrigin by rememberUpdatedState(origin)
    val entry = spec.ui?.entryFor(activePage) ?: "ui/index.html"
    val baseUrl = "$origin$ASSET_PREFIX$entry"
    val token = remember(record.packageRevision, activePage, source, origin) { UUID.randomUUID().toString() }
    // Reuse one WebView through page changes so AndroidView never points at a discarded instance.
    val webView = remember(record.packageRevision) { ExtensionWebViewPool.acquire(context) }
    val bridge: ComponentPageBridge = remember(webView) {
        ComponentPageBridge(webView) { callToken, id, command, payload ->
            scope.launch(Dispatchers.Main.immediate) {
                val response = try {
                    check(!removing) { "组件正在移除" }
                    check(bridgeActive(webView) && currentActions.isCurrent(record)) { "组件已更新或移除" }
                    val value: JsonElement = when (command) {
                        "notification.config.get", "notification.config.save", "notification.history",
                        "notification.test", "notification.retry", "notification.fetch" -> {
                            require(spec.notificationReceiver && PluginPermission.NotificationReceive in manifest.permissions) { "组件未声明通知出口能力" }
                            currentActions.notificationCommand(record, command, payload)
                        }
                        "debug.logs" -> currentActions.debugLogs(record)
                        "debug.refreshWidget" -> { currentActions.refreshWidget(record); JsonPrimitive(true) }
                        "debug.advancedTools" -> {
                            val enabled = (payload["enabled"] as? JsonPrimitive)?.booleanOrNull
                                ?: error("高级工具参数无效")
                            currentActions.setAdvancedToolsEnabled(enabled)
                            JsonPrimitive(enabled)
                        }
                        "settings.update" -> {
                            val next = store.update(record.pluginId) { updateComponentSettings(it, manifest, payload) }
                            currentActions.onDataChanged(record.pluginId)
                            extensionJson.encodeToJsonElement(ExtensionData.serializer(), next)
                        }
                        "host.update" -> {
                            val host = decodeHostSettings(payload)
                            val next = store.update(record.pluginId) { it.copy(host = host) }
                            currentActions.onDataChanged(record.pluginId)
                            extensionJson.encodeToJsonElement(ExtensionData.serializer(), next)
                        }
                        "sync" -> {
                            when (val outcome = currentActions.syncNow(record)) {
                                is ExtensionSyncOutcome.Synced -> buildJsonObject { put("count", outcome.data.items.size) }
                                is ExtensionSyncOutcome.Failed -> error(outcome.message)
                                is ExtensionSyncOutcome.LoginRequired -> error("请重新登录组件")
                                is ExtensionSyncOutcome.Skipped -> error("组件尚未登录或未启用")
                            }
                        }
                        "item.markRead" -> extensionJson.encodeToJsonElement(ExtensionData.serializer(),
                            currentActions.markRead(record, (payload["itemId"] as? JsonPrimitive)?.contentOrNull.orEmpty()))
                        "item.ignore" -> extensionJson.encodeToJsonElement(ExtensionData.serializer(),
                            currentActions.setItemIgnored(record, (payload["itemId"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                                (payload["ignored"] as? JsonPrimitive)?.booleanOrNull ?: error("忽略状态无效")))
                        "login.check" -> {
                            CookieManager.getInstance().flush()
                            val before = store.get(record.pluginId)
                            val (_, script) = currentActions.loadPackage(record)
                            val request = ExtensionRunRequest.from(manifest, script, before, ExtensionRunMode.CheckLogin).copy(
                                url = ExtensionUrls.resolve(spec.runUrl, ExtensionUrls.effectiveSettings(spec, before.settings)),
                            )
                            val run = try {
                                withTimeout(35_000) { ExtensionRuntime(context).run(request) }
                            } catch (_: TimeoutCancellationException) {
                                error("账号校验超时，请检查网络后重新检查登录结果")
                            }
                            if (run is ExtensionRunResult.Failed) error(run.message)
                            run as ExtensionRunResult.Completed
                            check(!removing && webView.tag == callToken && currentActions.isCurrent(record)) { "组件已更新或移除" }
                            val saved = store.updateIfPresent(record.pluginId) {
                                if (it.sessionRevision != before.sessionRevision) null else acceptComponentLogin(it, run.result)
                            } ?: error("登录上下文已变更，请重新登录")
                            currentActions.onDataChanged(record.pluginId)
                            if ((payload["navigate"] as? JsonPrimitive)?.booleanOrNull != false) activePage = PluginExtensionUiPage.Settings
                            scope.launch { runCatching { currentActions.syncNow(record) } }
                            extensionJson.encodeToJsonElement(ExtensionData.serializer(), saved)
                        }
                        "logout" -> {
                            clearExtensionCookies(manifest.allowedHosts)
                            val saved = store.update(record.pluginId, ::loggedOutComponent)
                            currentActions.onDataChanged(record.pluginId)
                            extensionJson.encodeToJsonElement(ExtensionData.serializer(), saved)
                        }
                        "web.cookie" -> {
                            require(PluginPermission.WebReadCookies in manifest.permissions) { "组件未声明 Cookie 读取权限" }
                            val name = (payload["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            require(name.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Cookie 名称无效" }
                            val value = CookieManager.getInstance().getCookie("$currentOrigin/").orEmpty().split(';')
                                .map(String::trim).firstOrNull { it.substringBefore('=') == name }?.substringAfter('=')
                            value?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull
                        }
                        "qr.encode" -> {
                            val text = (payload["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            require(text.isNotBlank() && text.length <= 4096) { "二维码内容无效" }
                            JsonPrimitive(componentQrImage(text))
                        }
                        "media.open" -> {
                            val url = (payload["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val attachment = currentData.items.asSequence().flatMap { it.attachments.asSequence() }.firstOrNull { it.url == url }
                                ?: error("附件不属于当前组件内容")
                            val saved = downloads.find(url)?.let { downloads.file(it.id) }
                            if (saved != null) {
                                openExtensionFile(context, saved.second, saved.first.mime)
                            } else {
                                val file = ExtensionMediaLoader(context).fetch(url, attachment.name, attachment.type, userAgent = manifest.userAgent)
                                check(!removing && bridgeActive(webView)) { "组件已关闭" }
                                openExtensionFile(context, file.file, file.mime)
                            }
                            JsonPrimitive(true)
                        }
                        "media.download" -> {
                            val url = (payload["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val attachment = currentData.items.asSequence().flatMap { it.attachments.asSequence() }.firstOrNull { it.url == url }
                                ?: error("附件不属于当前组件内容")
                            val entry = downloads.save(url, attachment.name, attachment.type, manifest.userAgent)
                            extensionJson.encodeToJsonElement(ExtensionDownload.serializer(), entry)
                        }
                        "media.downloads" -> extensionJson.encodeToJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(ExtensionDownload.serializer()), downloads.list(),
                        )
                        "media.openSaved" -> {
                            val (entry, file) = downloads.file((payload["id"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                            openExtensionFile(context, file, entry.mime)
                            JsonPrimitive(true)
                        }
                        "media.delete" -> JsonPrimitive(downloads.delete((payload["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()))
                        // Save only images belonging to the current content.
                        "media.saveImage" -> {
                            val url = (payload["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val image = currentData.items.asSequence().flatMap { it.images.asSequence() }.firstOrNull { it.url == url }
                                ?: error("图片不属于当前组件内容")
                            val file = ExtensionMediaLoader(context).fetch(url, image.name.ifBlank { "image" }, "image/*", userAgent = manifest.userAgent)
                            check(!removing && bridgeActive(webView)) { "组件已关闭" }
                            val name = file.name.takeIf { '.' in it } ?: "${file.name}.${file.mime.substringAfter('/', "jpg").substringBefore('+')}"
                            JsonPrimitive(saveExtensionImage(context, file.file, name, file.mime))
                        }
                        "ui.login" -> { activePage = PluginExtensionUiPage.Login; JsonPrimitive(true) }
                        "ui.settings" -> { if (page == PluginExtensionUiPage.Feed) onOpenSettings() else activePage = PluginExtensionUiPage.Settings; JsonPrimitive(true) }
                        "ui.feed" -> { if (page == PluginExtensionUiPage.Feed) activePage = PluginExtensionUiPage.Feed else currentActions.openFeed(record); JsonPrimitive(true) }
                        "ui.close" -> { if (activePage != page) activePage = page else onBack(); JsonPrimitive(true) }
                        "ui.openExternal" -> {
                            val url = (payload["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val declaredExternal = setOfNotNull(manifest.homepage, manifest.supportUrl).contains(url)
                            require((declaredExternal || ExtensionUrls.isAllowed(url, manifest.allowedHosts)) &&
                                java.net.URI(url).rawUserInfo == null && url.length <= 2048) { "外部链接不在组件声明的站点里" }
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            JsonPrimitive(true)
                        }
                        "component.remove" -> { require(page == PluginExtensionUiPage.Settings) { "请在组件设置中移除" }; removeConfirmation = true; JsonPrimitive(true) }
                        else -> error("不支持的组件调用：$command")
                    }
                    buildJsonObject { put("ok", true); put("value", value) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    buildJsonObject { put("ok", false); put("error", failure.message ?: "组件调用失败") }
                }
                if (webView.tag == callToken) {
                    webView.evaluateJavascript("window.__CurSimpleComponentReply && window.__CurSimpleComponentReply(${JsonPrimitive(id)},${componentUiJsonForScript(response.toString())});", null)
                }
            }
        }
    }
    // Sample time belongs to the snapshot; unrelated recompositions must not rebuild component DOM or interrupt scrolling.
    val snapshot = remember(activePage, data, manifest, colors, fontScale, openWidgetAbout) { buildJsonObject {
        put("version", com.x500x.cursimple.core.plugin.PluginApiVersion.CURRENT)
        put("page", activePage.name.lowercase())
        put("context", buildJsonObject {
            put("fontScale", fontScale)
            put("theme", buildJsonObject {
                fun color(key: String, value: androidx.compose.ui.graphics.Color) = put(key, "#%06x".format(value.toArgb() and 0xffffff))
                put("dark", colors.background.luminance() < 0.5f)
                color("background", colors.background); color("surface", colors.surface)
                color("surfaceVariant", colors.surfaceVariant); color("surfaceContainerLow", colors.surfaceContainerLow)
                color("onSurface", colors.onSurface); color("onSurfaceVariant", colors.onSurfaceVariant)
                color("outlineVariant", colors.outlineVariant); color("primary", colors.primary)
                color("onPrimary", colors.onPrimary); color("primaryContainer", colors.primaryContainer)
                color("onPrimaryContainer", colors.onPrimaryContainer); color("error", colors.error)
                color("errorContainer", colors.errorContainer)
            })
            put("timeZone", com.x500x.cursimple.core.kernel.time.BeijingTime.zone.id)
            put("nowMillis", com.x500x.cursimple.core.kernel.time.BeijingTime.nowMillis(com.x500x.cursimple.core.kernel.time.BeijingTime.zone))
            put("widgetAbout", openWidgetAbout)
        })
        put("data", extensionJson.encodeToJsonElement(ExtensionData.serializer(), currentData))
        put("manifest", extensionJson.encodeToJsonElement(PluginManifest.serializer(), manifest))
    } }
    // Keep bridge and WebView settings alive across owned-page changes.
    DisposableEffect(webView, bridge) {
        webView.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; textZoom = 100; manifest.userAgent?.let { userAgentString = it } }
        CookieManager.getInstance().setAcceptCookie(true)
        bridge.attachToView()
        webView.webChromeClient = WebChromeClient()
        onDispose {
            bridge.close()
            webView.tag = null
            webView.removeJavascriptInterface("CurSimpleExtensionUi")
            ExtensionWebViewPool.release(webView)
        }
    }
    // Page changes replace only content and WebViewClient.
    DisposableEffect(webView, source, baseUrl, origin) {
        webView.tag = token
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            override fun onPageFinished(view: WebView?, url: String?) { ready = true }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return null
                if ("${uri.scheme}://${uri.encodedAuthority}" != origin || !uri.path.orEmpty().startsWith(ASSET_PREFIX)) return null
                return runCatching {
                    val path = uri.path.orEmpty().removePrefix(ASSET_PREFIX)
                    val file = assetStore.readFile(record, path)
                    require(file.isFile)
                    val mime = when (file.extension.lowercase()) {
                        "js" -> "application/javascript"; "css" -> "text/css"; "html" -> "text/html"
                        "svg" -> "image/svg+xml"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, "UTF-8", file.inputStream())
                }.getOrElse { WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream()) }
            }
        }
        source?.let { pageHtml -> webView.loadDataWithBaseURL(baseUrl, componentUiHtml(pageHtml, snapshot, token, manifest), "text/html", "UTF-8", null) }
        onDispose { ready = false }
    }
    LaunchedEffect(snapshot, ready, webView) {
        if (ready) webView.evaluateJavascript("window.__CurSimpleComponentPush && window.__CurSimpleComponentPush(${componentUiJsonForScript(snapshot.toString())});", null)
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (!ready && loadError == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
        loadError?.let { Text(it, modifier = Modifier.align(Alignment.Center)) }
    }
    if (removeConfirmation) AlertDialog(
        onDismissRequest = { removeConfirmation = false },
        title = { Text("移除 ${record.name}？") },
        text = { Text("移除组件及其登录、缓存、提醒和课表事务。") },
        confirmButton = { TextButton(onClick = {
            removeConfirmation = false; removing = true; bridge.close()
            scope.launch {
                clearExtensionCookies(manifest.allowedHosts)
                store.remove(record.pluginId)
                currentActions.onDataChanged(record.pluginId)
                onRemove()
            }
        }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { removeConfirmation = false }) { Text("取消") } },
    )
}

/** Whether the instance still owns a component page; page changes update its tag. */
private fun bridgeActive(view: WebView): Boolean = view.tag != null

private class ComponentPageBridge(private val view: WebView, private val request: (String, String, String, JsonObject) -> Unit) {
    fun attachToView() = view.addJavascriptInterface(this, "CurSimpleExtensionUi")
    @Volatile private var active = true
    @JavascriptInterface fun request(callToken: String, id: String, command: String, payload: String) {
        if (!active || callToken != view.tag || payload.length > 256_000 || id.length > 100 || command.length > 100) return
        val args = runCatching { extensionJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        view.post { if (active && callToken == view.tag) request(callToken, id, command, args) }
    }
    @JavascriptInterface fun sync() { view.post { if (active) request(view.tag?.toString().orEmpty(), "legacy-sync", "sync", JsonObject(emptyMap())) } }
    @JavascriptInterface fun openSettings() { view.post { if (active) request(view.tag?.toString().orEmpty(), "legacy-settings", "ui.settings", JsonObject(emptyMap())) } }
    fun close() { active = false }
}

private fun componentQrImage(text: String): String {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600)
    val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val bytes = ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
    bitmap.recycle()
    return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private const val ASSET_PREFIX = "/__cursimple_component__/"

internal fun componentUiHtml(html: String, snapshot: JsonObject, token: String, manifest: PluginManifest): String {
    val script = """
(function(){
 const token=${JsonPrimitive(token)}, pending=new Map(), listeners=new Set(); let seq=0;
 let state=${componentUiJsonForScript(snapshot.toString())};
 window.__CurSimpleComponentReply=function(id,response){if(id==='legacy-sync'&&window.CurSimpleExtensionUiSetSyncing)window.CurSimpleExtensionUiSetSyncing(false);const p=pending.get(id);if(!p)return;pending.delete(id);clearTimeout(p.timer);response.ok?p.resolve(response.value):p.reject(new Error(response.error||'组件调用失败'));};
 window.__CurSimpleComponentPush=function(value){state=value;listeners.forEach(fn=>fn(state));if(window.CurSimpleExtensionUiSetData)window.CurSimpleExtensionUiSetData(state.data);};
 window.CurSimpleComponent=Object.freeze({version:${com.x500x.cursimple.core.plugin.PluginApiVersion.CURRENT},get state(){return state;},subscribe(fn){listeners.add(fn);fn(state);return()=>listeners.delete(fn);},request(command,payload){return new Promise((resolve,reject)=>{const id=String(++seq);const timer=setTimeout(()=>{pending.delete(id);reject(new Error('操作超时，请重试'));},(command==='sync'||command==='item.markRead'||command==='notification.test')?185000:45000);pending.set(id,{resolve,reject,timer});window.CurSimpleExtensionUi.request(token,id,command,JSON.stringify(payload||{}));});}});
 const nativeFetch=window.fetch.bind(window), hosts=${extensionJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), manifest.allowedHosts)}, network=${PluginPermission.NetworkFetch in manifest.permissions};
 const allowed=h=>hosts.some(x=>h===x||h.endsWith('.'+x));
 window.fetch=function(url,init){const u=new URL(typeof url==='string'?url:url.url,location.href),local=u.origin===location.origin&&u.pathname.startsWith('$ASSET_PREFIX');if(!local&&(!network||u.protocol!=='https:'||!allowed(u.hostname)))return Promise.reject(new Error('请求地址不在组件声明的站点内'));return nativeFetch(url,init);};
 // WebSocket destinations use the same declared-host restrictions as fetch.
 const NativeWebSocket=window.WebSocket;
 window.WebSocket=function(url,protocols){const u=new URL(String(url),location.href);if(u.protocol!=='wss:'||!network||!allowed(u.hostname))throw new Error('连接地址不在组件声明的站点内');return protocols===undefined?new NativeWebSocket(url):new NativeWebSocket(url,protocols);};
 window.WebSocket.prototype=NativeWebSocket.prototype;
})();
""".trimIndent()
    val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
    val sdk = "<script>$script</script>"
    return if (head != null) html.substring(0, head.range.last + 1) + sdk + html.substring(head.range.last + 1) else sdk + html
}
