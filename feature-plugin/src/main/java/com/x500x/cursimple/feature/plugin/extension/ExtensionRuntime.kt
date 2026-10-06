package com.x500x.cursimple.feature.plugin.extension

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.UUID

enum class ExtensionRunMode(val function: String) {
    CheckLogin("checkLogin"),
    Sync("sync"),
    ItemAction("performItemAction"),
    DeliverNotifications("deliverNotifications"),
}

/** Runtime inputs assembled by [ExtensionRunRequest.from]. */
data class ExtensionRunRequest(
    val pluginId: String,
    val mode: ExtensionRunMode,
    val url: String,
    val entrySource: String,
    val allowedHosts: List<String>,
    val permissions: Set<String>,
    val userAgent: String?,
    val settings: JsonObject,
    val state: JsonObject,
    val timeoutMs: Long,
    val maxOutputBytes: Int,
    val action: JsonObject = JsonObject(emptyMap()),
    val isolated: Boolean = false,
    val secureConfiguration: JsonObject = JsonObject(emptyMap()),
    val secureSessions: JsonObject = JsonObject(emptyMap()),
    val notifications: JsonArray = JsonArray(emptyList()),
    val onReceipt: (suspend (JsonObject) -> Boolean)? = null,
    val onSession: (suspend (JsonObject) -> Boolean)? = null,
) {
    companion object {
        fun from(
            manifest: PluginManifest,
            entrySource: String,
            data: ExtensionData,
            mode: ExtensionRunMode,
        ): ExtensionRunRequest {
            val spec = requireNotNull(manifest.extension) { "not an extension" }
            val settings = ExtensionUrls.effectiveSettings(spec, data.settings)
            val template = if (mode == ExtensionRunMode.CheckLogin) spec.loginUrl else spec.runUrl
            return ExtensionRunRequest(
                pluginId = manifest.id,
                mode = mode,
                url = ExtensionUrls.resolve(template, settings),
                entrySource = entrySource,
                allowedHosts = manifest.allowedHosts,
                permissions = manifest.permissions.map(PluginPermission::id).toSet(),
                userAgent = manifest.userAgent?.takeIf(String::isNotBlank),
                settings = JsonObject(settings),
                state = JsonObject(data.state),
                timeoutMs = manifest.limits.timeoutMs.coerceIn(10_000L, 180_000L),
                maxOutputBytes = manifest.limits.maxOutputBytes.coerceIn(64 * 1024, 8 * 1024 * 1024),
            )
        }
    }
}

sealed interface ExtensionRunResult {
    data class Completed(
        val result: JsonObject,
        val items: List<ExtensionFeedItem>,
        val state: Map<String, JsonElement>,
    ) : ExtensionRunResult

    data class Failed(val message: String) : ExtensionRunResult
}

/**
 * Run entry code on a same-origin page to preserve cookies. Inject code directly rather than
 * eval; accept bridge results only with the run token.
 */
class ExtensionRuntime(
    context: Context,
    private val nativeFetch: suspend (ExtensionRunRequest, JsonObject) -> JsonObject = ExtensionNativeTransport::fetch,
) {

    private val appContext = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(request: ExtensionRunRequest): ExtensionRunResult = withContext(Dispatchers.Main) {
        if (!ExtensionUrls.isAllowed(request.url, request.allowedHosts)) {
            return@withContext ExtensionRunResult.Failed("入口地址不在组件声明的站点里：${request.url}")
        }
        val token = UUID.randomUUID().toString()
        val done = CompletableDeferred<ExtensionRunResult>()
        val webView = WebView(appContext)
        val nativeJob = SupervisorJob(coroutineContext[Job])
        val nativeScope = CoroutineScope(coroutineContext + nativeJob)
        var injected = false
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
                blockNetworkLoads = request.isolated
                request.userAgent?.let { userAgentString = it }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webView.addJavascriptInterface(ExtensionBridge(token, request, done, webView, nativeScope, nativeFetch), BRIDGE_NAME)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                    // Block navigation beyond declared hosts while the native bridge is attached.
                    return request.isolated || !ExtensionUrls.isAllowed(req?.url?.toString().orEmpty(), request.allowedHosts)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (injected || view == null) return
                    if (!request.isolated && !ExtensionUrls.isAllowed(url.orEmpty(), request.allowedHosts)) return
                    injected = true
                    view.evaluateJavascript(buildExtensionScript(request, token, useBridge = true), null)
                }

                override fun onReceivedError(view: WebView?, req: WebResourceRequest?, error: WebResourceError?) {
                    if (req?.isForMainFrame == true) {
                        done.complete(ExtensionRunResult.Failed("打不开 ${request.url}：${error?.description ?: "网络错误"}"))
                    }
                }
            }
            if (request.isolated) webView.loadDataWithBaseURL("https://cursimple-extension.invalid/",
                "<!doctype html><html><head></head><body></body></html>", "text/html", "UTF-8", null)
            else webView.loadUrl(request.url)
            withTimeoutOrNull(request.timeoutMs) { done.await() }
                ?: ExtensionRunResult.Failed("组件运行超时（${request.timeoutMs / 1000} 秒）")
        } finally {
            nativeJob.cancel()
            runCatching {
                webView.stopLoading()
                webView.removeJavascriptInterface(BRIDGE_NAME)
                webView.destroy()
            }
        }
    }

    /** Accept script results only with the expected run token. */
    private class ExtensionBridge(
        private val token: String,
        private val request: ExtensionRunRequest,
        private val done: CompletableDeferred<ExtensionRunResult>,
        private val view: WebView,
        private val scope: CoroutineScope,
        private val nativeFetch: suspend (ExtensionRunRequest, JsonObject) -> JsonObject,
    ) {
        @JavascriptInterface
        fun request(callToken: String?, id: String?, command: String?, payload: String?) {
            if (callToken != token || id == null || id.length > 64 || payload == null || payload.length > 128 * 1024) return
            scope.launch {
                val response = try {
                    check(request.isolated && request.mode == ExtensionRunMode.DeliverNotifications) { "此运行模式不支持宿主传输" }
                    val values = extensionJson.parseToJsonElement(payload) as? JsonObject ?: error("请求格式错误")
                    val value = when (command) {
                        "network.fetch" -> nativeFetch(request, values)
                        "mail.send" -> ExtensionSmtpTransport.send(request, values)
                        "crypto.hmac" -> ExtensionNativeTransport.hmac(values)
                        "notification.receipt" -> {
                            require(PluginPermission.NotificationReceive.id in request.permissions) { "组件未声明通知出口权限" }
                            JsonPrimitive(request.onReceipt?.invoke(values) ?: error("通知回执不可用"))
                        }
                        "notification.session" -> {
                            require(PluginPermission.SecureStorage.id in request.permissions && PluginPermission.NotificationReceive.id in request.permissions) { "组件未声明加密会话权限" }
                            JsonPrimitive(request.onSession?.invoke(values) ?: error("会话存储不可用"))
                        }
                        else -> error("不支持的运行时调用")
                    }
                    buildJsonObject { put("ok", true); put("value", value) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { buildJsonObject { put("ok", false); put("error", failure.message ?: "运行时调用失败") } }
                if (!done.isCompleted) view.evaluateJavascript(
                    "window.__CurSimpleNativeReply && window.__CurSimpleNativeReply(${JsonPrimitive(id)},${componentUiJsonForScript(response.toString())});", null)
            }
        }
        @JavascriptInterface
        fun complete(callToken: String?, payload: String?) {
            if (callToken != token) return
            done.complete(parseExtensionPayload(payload, request.maxOutputBytes))
        }

        @JavascriptInterface
        fun fail(callToken: String?, message: String?) {
            if (callToken != token) return
            done.complete(ExtensionRunResult.Failed(message?.take(500)?.ifBlank { null } ?: "组件脚本出错"))
        }

        @JavascriptInterface
        fun log(callToken: String?, level: String?, message: String?) {
            if (callToken != token) return
            val fields = mapOf("pluginId" to request.pluginId, "message" to message?.take(1_000))
            when (level) {
                "error" -> PluginLogger.error("extension.script.log", fields)
                "warn" -> PluginLogger.warn("extension.script.log", fields)
                else -> PluginLogger.info("extension.script.log", fields)
            }
        }
    }

    companion object {
        internal const val BRIDGE_NAME = "CurSimpleExtension"
    }
}

/** Parse items independently so one malformed row cannot discard the snapshot. */
internal fun parseExtensionPayload(payload: String?, maxOutputBytes: Int): ExtensionRunResult {
    if (payload.isNullOrBlank()) return ExtensionRunResult.Failed("组件没有返回结果")
    if (payload.length > maxOutputBytes) return ExtensionRunResult.Failed("组件返回的数据太大（${payload.length} 字节）")
    val root = runCatching { extensionJson.parseToJsonElement(payload).jsonObject }.getOrNull()
        ?: return ExtensionRunResult.Failed("组件返回的不是 JSON 对象")
    val result = root["result"] as? JsonObject ?: JsonObject(emptyMap())
    val items = (root["items"] as? JsonArray).orEmpty()
        .asSequence()
        .mapNotNull { element -> runCatching { decodeFeedItem(element) }.getOrNull() }
        .distinctBy { it.id }
        .take(MAX_FEED_ITEMS)
        .toList()
    val state = (root["state"] as? JsonObject)?.toMap().orEmpty()
    return ExtensionRunResult.Completed(result, items, state)
}

private fun decodeFeedItem(element: JsonElement): ExtensionFeedItem? {
    val obj = element as? JsonObject ?: return null
    val item = extensionJson.decodeFromJsonElement(ExtensionFeedItem.serializer(), obj)
    if (item.id.isBlank() || item.title.isBlank()) return null
    return item.copy(
        title = item.title.take(200),
        summary = item.summary.take(500),
        content = item.content.take(100_000),
        images = item.images.mapNotNull { image -> extensionMediaUrl(image.url)?.let { image.copy(url = it, name = image.name.take(200)) } }
            .distinctBy { it.url },
        attachments = item.attachments.mapNotNull { attachment -> extensionMediaUrl(attachment.url)?.let {
            attachment.copy(url = it, name = attachment.name.take(200), type = attachment.type.take(100), size = attachment.size?.takeIf { size -> size > 0L })
        } }.distinctBy { it.url },
        // Accept web URLs only; reject executable and application schemes.
        url = item.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),
        firstSeenAt = 0L,
    )
}

internal val extensionJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

private const val MAX_FEED_ITEMS = 2_000


object ExtensionUrls {
    private val PLACEHOLDER = Regex("""\{settings\.([A-Za-z0-9_]+)\}""")

    fun effectiveSettings(
        spec: com.x500x.cursimple.core.plugin.manifest.PluginExtensionSpec,
        saved: Map<String, JsonElement>,
    ): Map<String, JsonElement> = buildMap {
        spec.settings.forEach { setting -> setting.default?.let { put(setting.key, it) } }
        spec.settings.forEach { setting -> saved[setting.key]?.let { put(setting.key, it) } }
    }

    fun resolve(template: String, settings: Map<String, JsonElement>): String =
        PLACEHOLDER.replace(template) { match ->
            val value = (settings[match.groupValues[1]] as? JsonPrimitive)?.contentOrNull.orEmpty()
            // Restrict URL placeholders to host and path fragments; reject separators that can change destinations.
            value.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }.orEmpty()
        }

    fun isAllowed(url: String, allowedHosts: List<String>): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { allowed ->
            val normalized = allowed.trim().lowercase()
            host == normalized || host.endsWith(".$normalized")
        }
    }

}

/**
 * Construct ctx and call entry code. Without [useBridge], publish results on window for host
 * polling.
 */
internal fun buildExtensionScript(request: ExtensionRunRequest, token: String, useBridge: Boolean): String {
    val config = JsonObject(
        mapOf(
            "token" to JsonPrimitive(token),
            "mode" to JsonPrimitive(request.mode.function),
            "settings" to request.settings,
            "state" to request.state,
            "action" to request.action,
            "isolated" to JsonPrimitive(request.isolated),
            "secureConfiguration" to request.secureConfiguration,
            "secureSessions" to request.secureSessions,
            "notifications" to request.notifications,
            "allowedHosts" to JsonArray(request.allowedHosts.map(::JsonPrimitive)),
            "permissions" to JsonArray(request.permissions.map(::JsonPrimitive)),
            "maxOutputBytes" to JsonPrimitive(request.maxOutputBytes),
            "useBridge" to JsonPrimitive(useBridge),
        ),
    )
    return """
(function () {
  var CONFIG = ${componentUiJsonForScript(config.toString())};
  if (window.__cursimpleExtensionToken === CONFIG.token) return;
  window.__cursimpleExtensionToken = CONFIG.token;
  window.__cursimpleExtensionResult = null;
  var bridge = CONFIG.useBridge ? window.$BRIDGE_JS_NAME : null;
  var nativePending = new Map(), nativeSequence = 0;
  window.__CurSimpleNativeReply = function(id, response) {
    var pending = nativePending.get(id); if (!pending) return;
    nativePending.delete(id); response.ok ? pending.resolve(response.value) : pending.reject(new Error(response.error));
  };
  function nativeRequest(command, payload) {
    if (!CONFIG.isolated || !bridge || !bridge.request) return Promise.reject(new Error("宿主传输不可用"));
    return new Promise(function(resolve,reject) { var id = String(++nativeSequence);
      nativePending.set(id, {resolve:resolve,reject:reject}); bridge.request(CONFIG.token,id,command,JSON.stringify(payload)); });
  }
  function report(kind, payload) {
    if (bridge) {
      if (kind === "ok") bridge.complete(CONFIG.token, payload); else bridge.fail(CONFIG.token, payload);
    } else {
      window.__cursimpleExtensionResult = JSON.stringify({ kind: kind, payload: payload });
    }
  }
  function need(permission) {
    if (CONFIG.permissions.indexOf(permission) < 0) throw new Error("组件没有声明权限：" + permission);
  }
  function hostAllowed(url) {
    var host;
    try { var u = new URL(url, location.href); if (u.protocol !== "https:") return false; host = u.hostname.toLowerCase(); }
    catch (_) { return false; }
    return CONFIG.allowedHosts.some(function (h) { h = String(h).toLowerCase(); return host === h || host.endsWith("." + h); });
  }
  function log(level, args) {
    var text;
    try { text = Array.prototype.map.call(args, function (a) { return typeof a === "string" ? a : JSON.stringify(a); }).join(" "); }
    catch (_) { text = String(args[0]); }
    if (bridge && bridge.log) bridge.log(CONFIG.token, level, String(text).slice(0, 1000));
    else if (window.console) console[level === "error" ? "error" : level === "warn" ? "warn" : "log"]("[extension]", text);
  }
  var state = CONFIG.state || {};
  var items = [];
  var ctx = Object.freeze({
    mode: CONFIG.mode,
    action: Object.freeze(CONFIG.action || {}),
    secureConfiguration: Object.freeze(CONFIG.secureConfiguration || {}),
    notifications: Object.freeze(CONFIG.notifications || []),
    notification: Object.freeze({ session: function(targetId,value) {
      need("storage.secure"); return nativeRequest("notification.session", {targetId:targetId,value:value});
    }, receipt: function(messageId,targetId,status,error) {
      need("notification.receive"); return nativeRequest("notification.receipt", {messageId:messageId,targetId:targetId,status:status,error:error||""});
    }}),
    secureSessions: Object.freeze(CONFIG.secureSessions || {}),
    mail: Object.freeze({ send: function(payload) { need("network.proxy"); return nativeRequest("mail.send",payload); } }),
    crypto: Object.freeze({ hmacSha256: function(key,message) { return nativeRequest("crypto.hmac", {key:key,message:message}); }}),
    settings: Object.freeze(CONFIG.settings || {}),
    now: function () { return Date.now(); },
    state: Object.freeze({
      get: function (key) { return state[key]; },
      set: function (key, value) { state[key] = value === undefined ? null : JSON.parse(JSON.stringify(value)); }
    }),
    network: Object.freeze({
      fetch: function (url, init) {
        if (CONFIG.isolated) {
          need("network.proxy");
          var nativeOptions = init || {};
          return nativeRequest("network.fetch", {url:String(url),method:nativeOptions.method||"GET",headers:nativeOptions.headers||{},body:nativeOptions.body||"",timeoutMs:nativeOptions.timeoutMs||20000}).then(function(response){
            return {ok:response.ok,status:response.status,ambiguous:response.ambiguous,error:response.error,
              text:function(){return Promise.resolve(response.body);},json:function(){return Promise.resolve(JSON.parse(response.body));}};
          });
        }
        need("network.fetch");
        if (!hostAllowed(url)) return Promise.reject(new Error("不允许访问：" + url));
        var options = Object.assign({ credentials: "include" }, init || {});
        return window.fetch(url, options);
      }
    }),
    web: Object.freeze({
      cookie: function (name) {
        need("web.read_cookies");
        var parts = String(document.cookie || "").split(";");
        for (var i = 0; i < parts.length; i++) {
          var pair = parts[i].trim(), at = pair.indexOf("=");
          if (at > 0 && pair.slice(0, at) === name) {
            try { return decodeURIComponent(pair.slice(at + 1)); } catch (_) { return pair.slice(at + 1); }
          }
        }
        return null;
      }
    }),
    feed: Object.freeze({
      add: function (item) { need("feed.write"); items.push(item); }
    }),
    log: Object.freeze({
      info: function () { log("info", arguments); },
      warn: function () { log("warn", arguments); },
      error: function () { log("error", arguments); }
    })
  });
  var entry;
  try {
    entry = (function () {
${normalizeExtensionEntrySource(request.entrySource)}
;
      return {
        checkLogin: typeof checkLogin === "function" ? checkLogin : undefined,
        sync: typeof sync === "function" ? sync : undefined,
        performItemAction: typeof performItemAction === "function" ? performItemAction : undefined
        ,deliverNotifications: typeof deliverNotifications === "function" ? deliverNotifications : undefined
      };
    })();
  } catch (error) {
    report("fail", "组件脚本加载失败：" + (error && error.message ? error.message : String(error)));
    return;
  }
  var fn = entry[CONFIG.mode];
  if (typeof fn !== "function") { report("fail", "组件没有导出 " + CONFIG.mode + " 函数"); return; }
  Promise.resolve().then(function () { return fn(ctx); }).then(function (result) {
    result = result && typeof result === "object" ? result : {};
    var all = items.slice();
    if (Array.isArray(result.items)) { need("feed.write"); all = all.concat(result.items); }
    var copy = Object.assign({}, result);
    delete copy.items;
    var payload = JSON.stringify({ result: copy, items: all, state: state });
    if (payload.length > CONFIG.maxOutputBytes) throw new Error("组件返回的数据太大");
    report("ok", payload);
  }).catch(function (error) {
    report("fail", error && error.message ? error.message : String(error));
  });
})();
""".trimIndent()
}

private const val BRIDGE_JS_NAME = ExtensionRuntime.BRIDGE_NAME

/**
 * Strip ES-module exports before injection; imports are unsupported, requiring a single entry
 * bundle.
 */
internal fun normalizeExtensionEntrySource(source: String): String = source
    .replace(Regex("""(?m)^\s*export\s*\{[^}]*\}\s*;?\s*$"""), "")
    .replace(Regex("""\bexport\s+default\s+"""), "")
    .replace(Regex("""\bexport\s+(?=(async\s+)?function\b|const\b|let\b|var\b|class\b)"""), "")

internal fun parseLoginPollResult(raw: String?, maxOutputBytes: Int): ExtensionRunResult? {
    val text = decodeEvaluateJavascriptString(raw) ?: return null
    val obj = runCatching { extensionJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull
    val payload = (obj["payload"] as? JsonPrimitive)?.contentOrNull
    return if (kind == "ok") parseExtensionPayload(payload, maxOutputBytes) else ExtensionRunResult.Failed(payload ?: "组件脚本出错")
}

/** Unwrap evaluateJavascript's JSON-encoded string result; null remains literal null. */
internal fun decodeEvaluateJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
    return runCatching { (extensionJson.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull }.getOrNull()
}
