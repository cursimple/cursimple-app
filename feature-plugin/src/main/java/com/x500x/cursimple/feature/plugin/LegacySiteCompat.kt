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
 * Allowlisted legacy document compatibility: inject jQuery/Migrate and opt out of origin-keyed
 * clustering. Proxy document GET only; preserve cookies, handle redirects once, and return
 * control to WebView on failure. POST and script requests retain their native paths.
 */
internal class LegacySiteCompat(
    private val context: Context,
    private val allowedHosts: List<String>,
    private val userAgent: () -> String,
) {

    /** Install document-start compatibility scripts when WebView supports them. */
    fun install(webView: WebView) {
        if (allowedHosts.isEmpty()) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            PluginLogger.info("plugin.web_compat.document_start_unsupported", emptyMap())
            return
        }
        val script = documentStartScript() ?: return
        runCatching {
            // Origin rules include ports, while the allowlist uses hosts; enforce host checks inside the script.
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }.onFailure { error ->
            PluginLogger.error(
                "plugin.web_compat.document_start_failed",
                mapOf("error" to (error.message ?: error.javaClass.simpleName)),
            )
        }
    }

    /**
     * Proxy allowlisted document navigation with Origin-Agent-Cluster: ?0; null preserves
     * WebView handling.
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
            // Persist response cookies before either content rendering or redirect navigation.
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
        /** ?0 enables legacy site-keyed behavior for document.domain. */
        const val LEGACY_AGENT_CLUSTER = "?0"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val JQUERY_ASSET = "webcompat/jquery-1.12.4.min.js"
        private const val JQUERY_MIGRATE_ASSET = "webcompat/jquery-migrate-1.4.1.min.js"
    }
}

/**
 * Navigation Accept includes application/xhtml+xml; use it to distinguish document requests
 * from script fetches.
 */
internal fun isDocumentNavigation(method: String?, isMainFrame: Boolean, accept: String?): Boolean {
    if (!method.orEmpty().ifBlank { "GET" }.equals("GET", ignoreCase = true)) return false
    if (isMainFrame) return true
    return accept.orEmpty().contains("application/xhtml+xml", ignoreCase = true)
}

/**
 * Read cookies from CookieManager; remove conditional, encoding and application-identifying
 * headers before proxying navigation.
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
 * Add the cluster opt-out header; strip decoded length/encoding, consumed cookies and
 * hop-by-hop headers.
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

/** Parse MIME and charset separately; leave unspecified charset to document metadata. */
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

internal fun redirectStubHtml(target: String): String {
    // Escape angle brackets as well as JSON quotes to prevent script-tag termination.
    val literal = JsonPrimitive(target).toString().replace("<", "\\u003c")
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<script>location.replace($literal);</script></head><body></body></html>"
}

/** Inject jQuery/Migrate only for allowed hosts without jQuery; suppress migration chatter. */
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
