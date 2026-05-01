package com.kebiao.viewer.feature.plugin

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import org.json.JSONObject
import java.security.MessageDigest
import java.time.OffsetDateTime

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebSessionScreen(
    request: WebSessionRequest,
    onFinish: (WebSessionPacket) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentUrl = remember { mutableStateOf(request.startUrl) }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val isFinishing = remember { mutableStateOf(false) }
    val blockedUrl = remember { mutableStateOf<String?>(null) }
    val pageError = remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(request.title, style = MaterialTheme.typography.titleLarge)
            Text("允许域名：${request.allowedHosts.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onCancel) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val webView = webViewState.value ?: return@Button
                        if (isFinishing.value) {
                            return@Button
                        }
                        isFinishing.value = true
                        captureWebSessionPacket(
                            webView = webView,
                            request = request,
                            currentUrl = currentUrl.value,
                        ) { packet ->
                            isFinishing.value = false
                            onFinish(packet)
                        }
                    },
                    enabled = !isFinishing.value,
                ) {
                    Text(if (isFinishing.value) "正在回传..." else "手动完成并回传")
                }
            }
            if (!request.completionUrlContains.isNullOrBlank()) {
                Text(
                    text = "到达目标教务页面后会自动继续；如果没有自动继续，可以手动回传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "当前页面：${currentUrl.value.ifBlank { "等待加载" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            blockedUrl.value?.let { url ->
                Text(
                    text = "已拦截非白名单跳转：$url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            pageError.value?.let { error ->
                Text(
                    text = "页面加载失败：$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = true
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                return handleNavigation(url.orEmpty())
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                webRequest: WebResourceRequest?,
                            ): Boolean {
                                return handleNavigation(webRequest?.url?.toString().orEmpty())
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                webRequest: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (webRequest?.isForMainFrame == true) {
                                    currentUrl.value = webRequest.url?.toString().orEmpty()
                                    pageError.value = "${error?.errorCode ?: 0}: ${error?.description?.toString().orEmpty()}"
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val target = url.orEmpty()
                                currentUrl.value = target
                                pageError.value = null
                                if (
                                    !isFinishing.value &&
                                    shouldAutoComplete(target, request.completionUrlContains)
                                ) {
                                    val webView = view ?: return
                                    isFinishing.value = true
                                    captureWebSessionPacket(
                                        webView = webView,
                                        request = request,
                                        currentUrl = target,
                                    ) { packet ->
                                        isFinishing.value = false
                                        onFinish(packet)
                                    }
                                }
                            }

                            private fun handleNavigation(target: String): Boolean {
                                if (target.isBlank() || isInternalWebViewUrl(target)) {
                                    return false
                                }
                                if (!isAllowedHost(target, request.allowedHosts)) {
                                    currentUrl.value = target
                                    blockedUrl.value = target
                                    return true
                                }
                                currentUrl.value = target
                                blockedUrl.value = null
                                pageError.value = null
                                return false
                            }
                        }
                        loadUrl(request.startUrl)
                        webViewState.value = this
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

private fun captureWebSessionPacket(
    webView: WebView,
    request: WebSessionRequest,
    currentUrl: String,
    onCaptured: (WebSessionPacket) -> Unit,
) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.flush()
    val cookies = collectCookies(cookieManager, request.allowedHosts, currentUrl)
    webView.evaluateJavascript(
        """
        (() => {
            const readStorage = (name) => {
              const snapshot = {};
              try {
                const storage = window[name];
                for (let i = 0; i < storage.length; i++) {
                  const key = storage.key(i);
                  if (key) {
                    snapshot[key] = storage.getItem(key) || "";
                  }
                }
              } catch (error) {}
              return snapshot;
            };
            const fields = {};
            try {
              ${request.captureSelectors.joinToString("\n") { selector ->
                  val nodeName = "node_${selector.hashCode().toString().replace("-", "_")}"
                  """try { const $nodeName = document.querySelector(${selector.quoteJs()}); if ($nodeName) { fields[${selector.quoteJs()}] = (($nodeName.value || $nodeName.textContent || "") + "").trim(); } } catch (error) {}"""
              }}
            } catch (error) {}
            let html = "";
            try {
              html = document.documentElement ? (document.documentElement.outerHTML || "") : "";
            } catch (error) {}
            try {
              return JSON.stringify({
                html,
                localStorageSnapshot: readStorage("localStorage"),
                sessionStorageSnapshot: readStorage("sessionStorage"),
                capturedFields: fields
              });
            } catch (error) {
              return "{}";
            }
        })();
        """.trimIndent(),
    ) { raw ->
        val payload = decodeJavascriptPayload(raw)
        onCaptured(
            WebSessionPacket(
                finalUrl = currentUrl,
                cookies = cookies,
                localStorageSnapshot = payload.optJSONObject("localStorageSnapshot").toStringMap(),
                sessionStorageSnapshot = payload.optJSONObject("sessionStorageSnapshot").toStringMap(),
                htmlDigest = sha256(payload.optString("html", "")),
                capturedFields = payload.optJSONObject("capturedFields").toStringMap(),
                timestamp = OffsetDateTime.now().toString(),
            ),
        )
    }
}

private fun collectCookies(
    cookieManager: CookieManager,
    allowedHosts: List<String>,
    currentUrl: String,
): Map<String, String> {
    val cookieSources = buildList {
        if (currentUrl.isNotBlank()) {
            add(currentUrl)
        }
        allowedHosts.forEach { add("https://$it/") }
    }.distinct()
    val cookies = linkedMapOf<String, String>()
    cookieSources.forEach { source ->
        cookieManager.getCookie(source)
            .orEmpty()
            .split(";")
            .mapNotNull { token ->
                val pair = token.trim().split("=", limit = 2)
                if (pair.size == 2) {
                    pair[0] to pair[1]
                } else {
                    null
                }
            }.forEach { (key, value) ->
                cookies[key] = value
            }
    }
    return cookies
}

private fun shouldAutoComplete(
    url: String,
    completionUrlContains: String?,
): Boolean {
    return completionUrlContains
        ?.takeIf(String::isNotBlank)
        ?.let { expected -> url.contains(expected, ignoreCase = true) }
        ?: false
}

internal fun isAllowedHost(url: String, allowedHosts: List<String>): Boolean {
    return runCatching { java.net.URL(url).host.lowercase() }.getOrNull()?.let { host ->
        allowedHosts.any { allowed ->
            host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}")
        }
    } ?: false
}

private fun isInternalWebViewUrl(url: String): Boolean {
    return url.startsWith("about:", ignoreCase = true) ||
        url.startsWith("javascript:", ignoreCase = true) ||
        url.startsWith("data:", ignoreCase = true) ||
        url.startsWith("blob:", ignoreCase = true)
}

private fun String.quoteJs(): String = buildString {
    append('"')
    forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(char)
        }
    }
    append('"')
}

private fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun decodeJavascriptPayload(raw: String?): JSONObject {
    val candidate = raw.orEmpty().trim()
    if (candidate.isBlank() || candidate == "null") {
        return JSONObject()
    }
    val normalized = runCatching {
        when (val payload = JSONObject("""{"payload":$candidate}""").opt("payload")) {
            is JSONObject -> payload.toString()
            is String -> payload
            else -> ""
        }
    }.getOrElse {
        candidate
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
    }
    return runCatching { JSONObject(normalized.ifBlank { "{}" }) }
        .getOrDefault(JSONObject())
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) {
        return emptyMap()
    }
    val result = linkedMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        result[key] = optString(key, "")
    }
    return result
}
