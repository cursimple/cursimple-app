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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.util.UUID

/** 入口脚本导出的两个函数，宿主按用途挑一个调 */
enum class ExtensionRunMode(val function: String) {
    CheckLogin("checkLogin"),
    Sync("sync"),
}

/** 跑一次入口脚本要的全部东西；由 [ExtensionRunRequest.from] 从 manifest 和存档里拼出来 */
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
                // manifest 里写得再长也不许挂过三分钟：后台任务被系统掐掉前总得先交差
                timeoutMs = manifest.limits.timeoutMs.coerceIn(10_000L, 180_000L),
                maxOutputBytes = manifest.limits.maxOutputBytes.coerceIn(64 * 1024, 8 * 1024 * 1024),
            )
        }
    }
}

sealed interface ExtensionRunResult {
    /** 脚本正常返回；[result] 是入口函数的返回值本身 */
    data class Completed(
        val result: JsonObject,
        val items: List<ExtensionFeedItem>,
        val state: Map<String, JsonElement>,
    ) : ExtensionRunResult

    data class Failed(val message: String) : ExtensionRunResult
}

/**
 * 在看不见的 WebView 里跑扩展组件的入口脚本。
 *
 * 为什么不用 QuickJS 之类的纯 JS 引擎：组件要带着登录态去请求站点接口，Cookie 在 WebView 的
 * CookieManager 里，只有同源页面里的 fetch 才会自动带上、也不会被 CORS 拦。所以先打开组件声明的
 * runUrl（同站点一个很轻的页面），页面加载完再把 ctx 和入口脚本一起注入进去。
 *
 * 入口脚本是**原样拼进注入脚本**里的，不走 eval / new Function：站点若设了 CSP，eval 会被拦。
 * 结果经 [ExtensionBridge] 交回，带一个本次随机生成的 token，别的页面脚本冒充不了。
 */
class ExtensionRuntime(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(request: ExtensionRunRequest): ExtensionRunResult = withContext(Dispatchers.Main) {
        if (!ExtensionUrls.isAllowed(request.url, request.allowedHosts)) {
            return@withContext ExtensionRunResult.Failed("入口地址不在组件声明的站点里：${request.url}")
        }
        val token = UUID.randomUUID().toString()
        val done = CompletableDeferred<ExtensionRunResult>()
        val webView = WebView(appContext)
        var injected = false
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // 后台跑不看页面，图片一张都不用下
                loadsImagesAutomatically = false
                blockNetworkImage = true
                request.userAgent?.let { userAgentString = it }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webView.addJavascriptInterface(ExtensionBridge(token, request, done), BRIDGE_NAME)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                    // 跳出声明站点的一律拦下：后台页面上还挂着桥，不能让它落到别人的页面里
                    return !ExtensionUrls.isAllowed(req?.url?.toString().orEmpty(), request.allowedHosts)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (injected || view == null) return
                    if (!ExtensionUrls.isAllowed(url.orEmpty(), request.allowedHosts)) return
                    injected = true
                    view.evaluateJavascript(buildExtensionScript(request, token, useBridge = true), null)
                }

                override fun onReceivedError(view: WebView?, req: WebResourceRequest?, error: WebResourceError?) {
                    if (req?.isForMainFrame == true) {
                        done.complete(ExtensionRunResult.Failed("打不开 ${request.url}：${error?.description ?: "网络错误"}"))
                    }
                }
            }
            webView.loadUrl(request.url)
            withTimeoutOrNull(request.timeoutMs) { done.await() }
                ?: ExtensionRunResult.Failed("组件运行超时（${request.timeoutMs / 1000} 秒）")
        } finally {
            runCatching {
                webView.stopLoading()
                webView.removeJavascriptInterface(BRIDGE_NAME)
                webView.destroy()
            }
        }
    }

    /** 页面脚本只能通过它交结果；token 对不上的调用一律不理 */
    private class ExtensionBridge(
        private val token: String,
        private val request: ExtensionRunRequest,
        private val done: CompletableDeferred<ExtensionRunResult>,
    ) {
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

/** 把入口脚本交回来的 JSON 拆开；条目逐条解析，坏掉的那条丢掉，不连累别的 */
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
        // 只认网页链接，别的协议（intent:、javascript:）一律不要
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

/** 地址模板与白名单 */
object ExtensionUrls {
    private val PLACEHOLDER = Regex("""\{settings\.([A-Za-z0-9_]+)\}""")

    /** 用户没改过的项按 manifest 里的默认值补齐，脚本拿到的永远是完整的一份 */
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
            // 模板里只拼主机名、路径片段，带斜杠、问号之类的一律不认，免得拼出别的站点
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

    fun settingBoolean(settings: Map<String, JsonElement>, key: String): Boolean? =
        (settings[key] as? JsonPrimitive)?.booleanOrNull
}

/**
 * 注入页面的那段脚本：造 ctx、原样拼进入口脚本、调入口函数、把结果交回。
 *
 * [useBridge] 为 false 时（登录页）没有 JS 桥，结果写在 window 上，由宿主轮询去读——
 * 登录页上用户会点到别的网页，那里不该挂着宿主的桥。
 */
internal fun buildExtensionScript(request: ExtensionRunRequest, token: String, useBridge: Boolean): String {
    val config = JsonObject(
        mapOf(
            "token" to JsonPrimitive(token),
            "mode" to JsonPrimitive(request.mode.function),
            "settings" to request.settings,
            "state" to request.state,
            "allowedHosts" to JsonArray(request.allowedHosts.map(::JsonPrimitive)),
            "permissions" to JsonArray(request.permissions.map(::JsonPrimitive)),
            "maxOutputBytes" to JsonPrimitive(request.maxOutputBytes),
            "useBridge" to JsonPrimitive(useBridge),
        ),
    )
    return """
(function () {
  var CONFIG = $config;
  if (window.__cursimpleExtensionToken === CONFIG.token) return;
  window.__cursimpleExtensionToken = CONFIG.token;
  window.__cursimpleExtensionResult = null;
  var bridge = CONFIG.useBridge ? window.$BRIDGE_JS_NAME : null;
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
    settings: Object.freeze(CONFIG.settings || {}),
    now: function () { return Date.now(); },
    state: Object.freeze({
      get: function (key) { return state[key]; },
      set: function (key, value) { state[key] = value === undefined ? null : JSON.parse(JSON.stringify(value)); }
    }),
    network: Object.freeze({
      fetch: function (url, init) {
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
        sync: typeof sync === "function" ? sync : undefined
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
 * 入口脚本是按 ES 模块写的（export async function sync…），拼进普通脚本前把 export 摘掉；
 * import 语句不支持，组件得是单文件。
 */
internal fun normalizeExtensionEntrySource(source: String): String = source
    .replace(Regex("""(?m)^\s*export\s*\{[^}]*\}\s*;?\s*$"""), "")
    .replace(Regex("""\bexport\s+default\s+"""), "")
    .replace(Regex("""\bexport\s+(?=(async\s+)?function\b|const\b|let\b|var\b|class\b)"""), "")

/** 登录页轮询读到的那段结果 */
internal fun parseLoginPollResult(raw: String?, maxOutputBytes: Int): ExtensionRunResult? {
    val text = decodeEvaluateJavascriptString(raw) ?: return null
    val obj = runCatching { extensionJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull
    val payload = (obj["payload"] as? JsonPrimitive)?.contentOrNull
    return if (kind == "ok") parseExtensionPayload(payload, maxOutputBytes) else ExtensionRunResult.Failed(payload ?: "组件脚本出错")
}

/** evaluateJavascript 把返回的字符串再套一层 JSON 引号，null 就是字面量 "null" */
internal fun decodeEvaluateJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
    return runCatching { (extensionJson.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull }.getOrNull()
}
