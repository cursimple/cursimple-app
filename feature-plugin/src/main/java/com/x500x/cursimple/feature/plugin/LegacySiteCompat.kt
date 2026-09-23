package com.x500x.cursimple.feature.plugin

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * 插件网页会话的「老站兼容层」，给教务系统、校园 VPN 代理这类老旧站点补回
 * 新版 Chromium 拿掉、而它们又离不开的两样东西。只在插件声明的白名单域名上生效。
 *
 * ## 为什么要有它
 *
 * 以深信服 aTrust 为代表的 SSL VPN 在免客户端模式下，不直接回业务页面，而是先回一个
 * 几 KB 的自举壳。壳页靠两样东西完成握手：
 *
 * 1. **`document.domain`**：壳页与网关页分属不同子域（如 `jwc3-…-s.atrust.x.edu.cn`
 *    与 `atrust.x.edu.cn`），双方都把 `document.domain` 设成共同的父域后才能互相
 *    调用。Chromium 从 115 左右起默认按「源」隔离（origin-keyed agent cluster），
 *    这个赋值变成了空操作，跨子域访问一律被拦，握手永远完不成，页面就一直卡着。
 * 2. **jQuery**：代理会把页面里的 jQuery 剥掉，指望本机客户端再注入一份。
 *    WebView 里没有这个客户端，壳页一用 `$` 就报错。
 *
 * 改 UA、改 Referer 都没用，缺的不是身份而是运行环境。
 *
 * ## 怎么补
 *
 * 1. Chromium 给这类老站留了官方开关：文档响应头带 `Origin-Agent-Cluster: ?0`，
 *    该站就退回按「站点」隔离，`document.domain` 重新生效。WebView 没有改响应头的 API，
 *    所以对白名单域名下的页面导航由 App 代发请求、补上这个头再交回 WebView，
 *    Cookie 与 WebView 双向同步。
 * 2. 在页面任何脚本执行之前注入 jQuery（附 Migrate，老教务常用 `$.browser`、`.live()`
 *    这类 1.9 起删掉的接口）。页面自己带了 jQuery 的，加载时会照常覆盖掉这一份。
 *
 * ## 刻意不做的
 *
 * - **POST 不代发**：拦截回调拿不到请求体，代发会丢数据。POST 照走 WebView 自己的网络栈。
 * - **XHR / fetch 不代发**：只动文档导航（Accept 里带 `application/xhtml+xml` 的那种），
 *   脚本发的请求原样放行。
 * - **不自动跟随重定向**：重定向不能交给 WebView 处理（拦截路径不支持 3xx），而放行又会
 *   让同一个 GET 发两次——单点登录的一次性 ticket 会因此失效。所以收到 3xx 时返回一个
 *   立刻 `location.replace` 到目标地址的小页面，每一跳都只发一次。
 * - **出错就放行**：代发失败（证书、超时等）返回 null，交还给 WebView 按原样处理。
 */
internal class LegacySiteCompat(
    private val context: Context,
    private val allowedHosts: List<String>,
    private val userAgent: () -> String,
) {

    /** 在 WebView 上装好文档起始脚本（注入 jQuery）。WebView 太老不支持就跳过。 */
    fun install(webView: WebView) {
        if (allowedHosts.isEmpty()) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            PluginLogger.info("plugin.web_compat.document_start_unsupported", emptyMap())
            return
        }
        val script = documentStartScript() ?: return
        runCatching {
            // 起始脚本按「源」规则投放，而白名单是按域名写的（还可能带非默认端口，
            // 比如 aTrust 门户的 :4443），所以这里对所有源投放，由脚本自己按域名判断
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }.onFailure { error ->
            PluginLogger.error(
                "plugin.web_compat.document_start_failed",
                mapOf("error" to (error.message ?: error.javaClass.simpleName)),
            )
        }
    }

    /**
     * 白名单域名下的页面导航由 App 代发，并补上 `Origin-Agent-Cluster: ?0`。
     * 不符合条件或代发失败都返回 null，交给 WebView 自己去请求。
     */
    fun interceptDocument(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        val scheme = request.url?.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return null
        if (!isAllowedHost(url, allowedHosts)) return null
        if (!isDocumentNavigation(request.method, request.isForMainFrame, request.acceptHeader())) return null

        val cookieManager = CookieManager.getInstance()
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull()
            ?: return null
        return runCatching<WebResourceResponse?> {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "GET"
            forwardRequestHeaders(
                requestHeaders = request.requestHeaders.orEmpty(),
                cookie = cookieManager.getCookie(url),
                fallbackUserAgent = currentUserAgent(),
            ).forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val status = connection.responseCode
            val headerFields = connection.headerFields.orEmpty()
            // 先把 Cookie 存回 WebView：后面不管是正常页面还是重定向小页面，
            // 下一次请求都得带着这些 Cookie
            headerFields.entries
                .filter { (name, _) -> name.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }
                .forEach { cookieManager.setCookie(url, it) }
            cookieManager.flush()

            val location = connection.getHeaderField("Location")
            if (status in 300..399 && !location.isNullOrBlank()) {
                val target = runCatching { URL(URL(url), location).toString() }.getOrNull()
                connection.disconnect()
                if (target == null) return@runCatching null
                logIntercept(url, status, redirectTo = target)
                return@runCatching WebResourceResponse(
                    "text/html",
                    "utf-8",
                    200,
                    "OK",
                    mapOf(
                        ORIGIN_AGENT_CLUSTER to LEGACY_AGENT_CLUSTER,
                        "Cache-Control" to "no-store",
                    ),
                    ByteArrayInputStream(redirectStubHtml(target).toByteArray(Charsets.UTF_8)),
                )
            }
            // 304 之类没有正文的状态交不回 WebView；前面已经去掉了条件请求头，正常不会出现
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED || status in 100..199) {
                connection.disconnect()
                return@runCatching null
            }

            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            val (mimeType, charset) = parseContentType(connection.contentType)
            val headers = legacyDocumentResponseHeaders(headerFields)
            val reason = connection.responseMessage?.takeIf(String::isNotBlank) ?: httpReasonPhrase(status)
            connection.disconnect()
            logIntercept(url, status, redirectTo = null)
            WebResourceResponse(
                mimeType ?: "text/html",
                charset,
                status,
                reason,
                headers,
                ByteArrayInputStream(body),
            )
        }.getOrElse { error ->
            connection.disconnect()
            PluginLogger.info(
                "plugin.web_compat.intercept_fallback",
                mapOf(
                    "url" to PluginLogger.sanitizeUrl(url),
                    "error" to (error.message ?: error.javaClass.simpleName),
                ),
            )
            null
        }
    }

    private fun currentUserAgent(): String =
        userAgent().ifBlank { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("") }

    private fun logIntercept(url: String, status: Int, redirectTo: String?) {
        PluginLogger.info(
            "plugin.web_compat.document_intercepted",
            buildMap {
                put("url", PluginLogger.sanitizeUrl(url))
                put("status", status)
                redirectTo?.let { put("redirectTo", PluginLogger.sanitizeUrl(it)) }
            },
        )
    }

    private fun documentStartScript(): String? {
        val jquery = readAsset(JQUERY_ASSET) ?: return null
        val migrate = readAsset(JQUERY_MIGRATE_ASSET).orEmpty()
        return legacyDocumentStartScript(
            allowedHosts = allowedHosts,
            jquerySource = jquery,
            migrateSource = migrate,
        )
    }

    private fun readAsset(path: String): String? =
        runCatching { context.assets.open(path).use { String(it.readBytes(), Charsets.UTF_8) } }
            .onFailure { error ->
                PluginLogger.error(
                    "plugin.web_compat.asset_missing",
                    mapOf("path" to path, "error" to (error.message ?: error.javaClass.simpleName)),
                )
            }
            .getOrNull()

    private fun WebResourceRequest.acceptHeader(): String? =
        requestHeaders.orEmpty().entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value

    companion object {
        const val ORIGIN_AGENT_CLUSTER = "Origin-Agent-Cluster"
        /** `?0` 即「不按源隔离」，`document.domain` 在这个站点上重新生效。 */
        const val LEGACY_AGENT_CLUSTER = "?0"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val JQUERY_ASSET = "webcompat/jquery-1.12.4.min.js"
        private const val JQUERY_MIGRATE_ASSET = "webcompat/jquery-migrate-1.4.1.min.js"
    }
}

/**
 * 这个请求是不是一次「页面导航」。
 *
 * 浏览器导航时的 Accept 会带 `application/xhtml+xml`，脚本发的 XHR / fetch 不会——
 * jQuery 要 HTML 时的 Accept 只有 `text/html` 加一个通配，不含它。只有导航才代发。
 */
internal fun isDocumentNavigation(method: String?, isMainFrame: Boolean, accept: String?): Boolean {
    if (!method.orEmpty().ifBlank { "GET" }.equals("GET", ignoreCase = true)) return false
    if (isMainFrame) return true
    return accept.orEmpty().contains("application/xhtml+xml", ignoreCase = true)
}

/**
 * 代发时要带的请求头。
 *
 * - Cookie 拦截回调里拿不到，得从 WebView 的 CookieManager 取；
 * - 去掉条件请求头：服务器回 304 时没有正文可交回 WebView；
 * - 去掉 Accept-Encoding，让 HttpURLConnection 自己协商并透明解压；
 * - 去掉 WebView 自己加的 `X-Requested-With: <包名>`，新版 WebView 默认已不发它，
 *   留着反而像个非浏览器客户端。脚本设的 `XMLHttpRequest` 不在导航请求里，不受影响。
 */
internal fun forwardRequestHeaders(
    requestHeaders: Map<String, String>,
    cookie: String?,
    fallbackUserAgent: String,
): Map<String, String> {
    val dropped = setOf(
        "host", "cookie", "accept-encoding", "connection", "content-length",
        "if-none-match", "if-modified-since", "if-match", "if-unmodified-since", "if-range",
    )
    val result = linkedMapOf<String, String>()
    requestHeaders.forEach { (name, value) ->
        val lower = name.lowercase(Locale.ROOT)
        if (lower in dropped) return@forEach
        if (lower == "x-requested-with" && !value.equals("XMLHttpRequest", ignoreCase = true)) return@forEach
        result[name] = value
    }
    if (result.keys.none { it.equals("User-Agent", ignoreCase = true) } && fallbackUserAgent.isNotBlank()) {
        result["User-Agent"] = fallbackUserAgent
    }
    if (!cookie.isNullOrBlank()) {
        result["Cookie"] = cookie
    }
    return result
}

/**
 * 交回 WebView 的响应头：原样保留，补上 `Origin-Agent-Cluster: ?0`。
 *
 * 去掉的几项：正文已经解压、长度也变了，编码与长度头会误导解析；Set-Cookie 已经
 * 存进 CookieManager，再交回去会重复写；逐跳头对 WebView 没意义。
 */
internal fun legacyDocumentResponseHeaders(headerFields: Map<String?, List<String>>): Map<String, String> {
    val dropped = setOf("content-encoding", "content-length", "transfer-encoding", "connection", "set-cookie", "keep-alive")
    val headers = linkedMapOf<String, String>()
    headerFields.forEach { (name, values) ->
        if (name == null) return@forEach
        if (name.lowercase(Locale.ROOT) in dropped) return@forEach
        if (name.equals(LegacySiteCompat.ORIGIN_AGENT_CLUSTER, ignoreCase = true)) return@forEach
        headers[name] = values.joinToString(", ")
    }
    headers[LegacySiteCompat.ORIGIN_AGENT_CLUSTER] = LegacySiteCompat.LEGACY_AGENT_CLUSTER
    return headers
}

/** `text/html; charset=GBK` → (`text/html`, `GBK`)。没写字符集时交给页面里的 meta 决定。 */
internal fun parseContentType(contentType: String?): Pair<String?, String?> {
    if (contentType.isNullOrBlank()) return null to null
    val parts = contentType.split(';').map { it.trim() }
    val mime = parts.firstOrNull()?.takeIf(String::isNotBlank)?.lowercase(Locale.ROOT)
    val charset = parts.drop(1)
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"', '\'')
        ?.takeIf(String::isNotBlank)
    return mime to charset
}

/** 3xx 的替身页：立刻跳到目标地址，不留历史记录。 */
internal fun redirectStubHtml(target: String): String {
    // JSON 转义管引号和反斜杠，但不管 `<`：地址里要是带着 `</script>` 会提前截断脚本
    val literal = JsonPrimitive(target).toString().replace("<", "\\u003c")
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<script>location.replace($literal);</script></head><body></body></html>"
}

/**
 * 文档起始脚本：域名在白名单里、页面还没有 jQuery 时，注入 jQuery 与 Migrate。
 *
 * 只新增 `jQuery` / `$` 两个全局；页面之后自己加载 jQuery 会直接覆盖它们。
 * Migrate 的控制台提示关掉，免得刷屏盖住真正的错误。
 */
internal fun legacyDocumentStartScript(
    allowedHosts: List<String>,
    jquerySource: String,
    migrateSource: String,
): String {
    val hosts = JsonArray(
        allowedHosts.map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank).map(::JsonPrimitive),
    ).toString().replace("<", "\\u003c")
    return """
        (function () {
          try {
            var host = (location.hostname || "").toLowerCase();
            var allowed = $hosts;
            var hit = allowed.some(function (h) {
              return host === h || host.slice(-(h.length + 1)) === "." + h;
            });
            if (!hit || window.jQuery) { return; }
            $jquerySource
            ;window.jQuery && (window.jQuery.migrateMute = true);
            $migrateSource
            ;window.__cursimpleLegacyJQuery = true;
          } catch (e) {}
        })();
    """.trimIndent()
}
