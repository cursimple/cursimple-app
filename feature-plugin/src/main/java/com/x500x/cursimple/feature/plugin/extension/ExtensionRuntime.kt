package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.feature.plugin.hardenLocalAccess
import com.x500x.cursimple.feature.plugin.LegacySiteCompat

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
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
    /** A portal may cross SSO intermediates before reaching the script's same-origin entry. */
    val waitForRunUrl: Boolean = false,
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

    data class Failed(
        val message: String,
        /** Structured cause, so callers never have to recognise the (localised) message text. */
        val kind: ExtensionFailureKind = ExtensionFailureKind.Script,
    ) : ExtensionRunResult
}

enum class ExtensionFailureKind {
    /** The page could not be loaded: DNS, connection, TLS, or the gateway refused it. */
    Unreachable,
    /** The component did not finish within its time budget. */
    Timeout,
    /** The component script reported an error or returned unusable output. */
    Script,
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
    // Only Resources is kept (not the Activity), so failure text follows the caller's language.
    private val messages: ExtensionRunMessages = ExtensionRunMessages.from(context.resources)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(request: ExtensionRunRequest): ExtensionRunResult = withContext(Dispatchers.Main) {
        if (!ExtensionUrls.isAllowed(request.url, request.allowedHosts)) {
            return@withContext ExtensionRunResult.Failed(messages.entryNotAllowed(request.url))
        }
        val token = UUID.randomUUID().toString()
        val done = CompletableDeferred<ExtensionRunResult>()
        val webView = WebView(appContext)
        val nativeJob = SupervisorJob(coroutineContext[Job])
        val nativeScope = CoroutineScope(coroutineContext + nativeJob)
        var injected = false
        try {
            webView.hardenLocalAccess()
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
                blockNetworkLoads = request.isolated
                if (request.waitForRunUrl) cacheMode = WebSettings.LOAD_NO_CACHE
                request.userAgent?.let { userAgentString = it }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            val legacySiteCompat = if (!request.isolated && request.waitForRunUrl) {
                LegacySiteCompat(appContext, request.allowedHosts, { request.userAgent.orEmpty() }).also { it.install(webView) }
            } else null
            webView.addJavascriptInterface(ExtensionBridge(token, request, done, webView, nativeScope, nativeFetch, messages), BRIDGE_NAME)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): android.webkit.WebResourceResponse? =
                    req?.let { legacySiteCompat?.interceptDocument(it) }

                override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                    // Block navigation beyond declared hosts while the native bridge is attached.
                    return request.isolated || !ExtensionUrls.isAllowed(req?.url?.toString().orEmpty(), request.allowedHosts)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (injected || view == null) return
                    if (!request.isolated && !ExtensionUrls.isAllowed(url.orEmpty(), request.allowedHosts)) return
                    if (!extensionEntryPageReached(request, url.orEmpty())) return
                    injected = true
                    view.evaluateJavascript(buildExtensionScript(request, token, useBridge = true, messages = messages), null)
                }

                override fun onReceivedError(view: WebView?, req: WebResourceRequest?, error: WebResourceError?) {
                    if (req?.isForMainFrame == true) {
                        done.complete(ExtensionRunResult.Failed(
                            messages.cannotOpen(request.url, error?.description?.toString()),
                            ExtensionFailureKind.Unreachable,
                        ))
                    }
                }
            }
            if (request.isolated) webView.loadDataWithBaseURL("https://cursimple-extension.invalid/",
                "<!doctype html><html><head></head><body></body></html>", "text/html", "UTF-8", null)
            else webView.loadUrl(request.url)
            withTimeoutOrNull(request.timeoutMs) { done.await() }
                ?: ExtensionRunResult.Failed(messages.timeout(request.timeoutMs / 1000), ExtensionFailureKind.Timeout)
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
        private val messages: ExtensionRunMessages,
    ) {
        @JavascriptInterface
        fun request(callToken: String?, id: String?, command: String?, payload: String?) {
            if (callToken != token || id == null || id.length > 64 || payload == null || payload.length > 128 * 1024) return
            scope.launch {
                val response = try {
                    check(request.isolated && request.mode == ExtensionRunMode.DeliverNotifications) { messages.bridge(BridgeError.UnsupportedMode) }
                    val values = extensionJson.parseToJsonElement(payload) as? JsonObject ?: error(messages.bridge(BridgeError.BadRequest))
                    val value = when (command) {
                        "network.fetch" -> nativeFetch(request, values)
                        "mail.send" -> ExtensionSmtpTransport.send(request, values)
                        "crypto.hmac" -> ExtensionNativeTransport.hmac(values)
                        "notification.receipt" -> {
                            require(PluginPermission.NotificationReceive.id in request.permissions) { messages.bridge(BridgeError.ReceiptPermission) }
                            JsonPrimitive(request.onReceipt?.invoke(values) ?: error(messages.bridge(BridgeError.ReceiptUnavailable)))
                        }
                        "notification.session" -> {
                            require(PluginPermission.SecureStorage.id in request.permissions && PluginPermission.NotificationReceive.id in request.permissions) { messages.bridge(BridgeError.SessionPermission) }
                            JsonPrimitive(request.onSession?.invoke(values) ?: error(messages.bridge(BridgeError.SessionUnavailable)))
                        }
                        else -> error(messages.bridge(BridgeError.UnsupportedCall))
                    }
                    buildJsonObject { put("ok", true); put("value", value) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { buildJsonObject { put("ok", false); put("error", failure.message ?: messages.bridge(BridgeError.CallFailed)) } }
                if (!done.isCompleted) view.evaluateJavascript(
                    "window.__CurSimpleNativeReply && window.__CurSimpleNativeReply(${JsonPrimitive(id)},${componentUiJsonForScript(response.toString())});", null)
            }
        }
        @JavascriptInterface
        fun complete(callToken: String?, payload: String?) {
            if (callToken != token) return
            done.complete(parseExtensionPayload(payload, request.maxOutputBytes, messages))
        }

        @JavascriptInterface
        fun fail(callToken: String?, message: String?) {
            if (callToken != token) return
            done.complete(ExtensionRunResult.Failed(message?.take(500)?.ifBlank { null } ?: messages.scriptError()))
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

internal fun extensionEntryPageReached(request: ExtensionRunRequest, url: String): Boolean {
    if (request.isolated || !request.waitForRunUrl) return true
    val expected = runCatching { URI(request.url) }.getOrNull() ?: return false
    val actual = runCatching { URI(url) }.getOrNull() ?: return false
    fun URI.httpsPort() = if (port == -1) 443 else port
    return actual.scheme.equals("https", true) && expected.scheme.equals("https", true) &&
        actual.rawUserInfo == null && actual.host.equals(expected.host, true) &&
        actual.httpsPort() == expected.httpsPort() && actual.path == expected.path
}

/** Parse items independently so one malformed row cannot discard the snapshot. */
internal fun parseExtensionPayload(
    payload: String?,
    maxOutputBytes: Int,
    messages: ExtensionRunMessages = ExtensionRunMessages.Plain,
): ExtensionRunResult {
    if (payload.isNullOrBlank()) return ExtensionRunResult.Failed(messages.noResult())
    if (payload.length > maxOutputBytes) return ExtensionRunResult.Failed(messages.tooLarge(payload.length))
    val root = runCatching { extensionJson.parseToJsonElement(payload).jsonObject }.getOrNull()
        ?: return ExtensionRunResult.Failed(messages.notJson())
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
        if (uri.scheme != "https" || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { allowed ->
            val normalized = allowed.trim().lowercase()
            normalized.isNotEmpty() && normalized.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) &&
                (host == normalized || host.endsWith(".$normalized"))
        }
    }

}

/**
 * Construct ctx and call entry code. Without [useBridge], publish results on window for host
 * polling.
 */
internal fun buildExtensionScript(
    request: ExtensionRunRequest,
    token: String,
    useBridge: Boolean,
    messages: ExtensionRunMessages = ExtensionRunMessages.Plain,
): String {
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
            "messages" to JsonObject(messages.scriptTemplates().mapValues { JsonPrimitive(it.value) }),
        ),
    )
    return """
(function () {
  var CONFIG = ${componentUiJsonForScript(config.toString())};
  if (window.__cursimpleExtensionToken === CONFIG.token) return;
  window.__cursimpleExtensionToken = CONFIG.token;
  window.__cursimpleExtensionResult = null;
  var bridge = CONFIG.useBridge ? window.$BRIDGE_JS_NAME : null;
  function say(key, value) { return String(CONFIG.messages[key]).replace("{0}", value === undefined ? "" : value); }
  var nativePending = new Map(), nativeSequence = 0;
  window.__CurSimpleNativeReply = function(id, response) {
    var pending = nativePending.get(id); if (!pending) return;
    nativePending.delete(id); response.ok ? pending.resolve(response.value) : pending.reject(new Error(response.error));
  };
  function nativeRequest(command, payload) {
    if (!CONFIG.isolated || !bridge || !bridge.request) return Promise.reject(new Error(say("transportUnavailable")));
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
    if (CONFIG.permissions.indexOf(permission) < 0) throw new Error(say("permissionMissing", permission));
  }
  function hostAllowed(url) {
    var host;
    try { var u = new URL(url, location.href); if (u.protocol !== "https:" || u.username || u.password) return false; host = u.hostname.toLowerCase(); }
    catch (_) { return false; }
    return CONFIG.allowedHosts.some(function (h) { h = String(h).trim().toLowerCase(); return /^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(h) && (host === h || host.endsWith("." + h)); });
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
    }, receipt: function(messageId,targetId,status,error,retryAt) {
      need("notification.receive"); return nativeRequest("notification.receipt", {messageId:messageId,targetId:targetId,status:status,error:error||"",retryAt:retryAt||0});
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
        if (!hostAllowed(url)) return Promise.reject(new Error(say("accessDenied", url)));
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
    report("fail", say("loadFailed", error && error.message ? error.message : String(error)));
    return;
  }
  var fn = entry[CONFIG.mode];
  if (typeof fn !== "function") { report("fail", say("functionMissing", CONFIG.mode)); return; }
  Promise.resolve().then(function () { return fn(ctx); }).then(function (result) {
    result = result && typeof result === "object" ? result : {};
    var all = items.slice();
    if (Array.isArray(result.items)) { need("feed.write"); all = all.concat(result.items); }
    var copy = Object.assign({}, result);
    delete copy.items;
    var payload = JSON.stringify({ result: copy, items: all, state: state });
    if (payload.length > CONFIG.maxOutputBytes) throw new Error(say("tooLarge"));
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

internal fun parseLoginPollResult(
    raw: String?,
    maxOutputBytes: Int,
    messages: ExtensionRunMessages = ExtensionRunMessages.Plain,
): ExtensionRunResult? {
    val text = decodeEvaluateJavascriptString(raw) ?: return null
    val obj = runCatching { extensionJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull
    val payload = (obj["payload"] as? JsonPrimitive)?.contentOrNull
    return if (kind == "ok") parseExtensionPayload(payload, maxOutputBytes, messages)
    else ExtensionRunResult.Failed(payload ?: messages.scriptError())
}

/** Unwrap evaluateJavascript's JSON-encoded string result; null remains literal null. */
internal fun decodeEvaluateJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
    return runCatching { (extensionJson.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull }.getOrNull()
}
