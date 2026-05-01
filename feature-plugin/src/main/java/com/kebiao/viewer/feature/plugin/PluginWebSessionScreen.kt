package com.kebiao.viewer.feature.plugin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.runtime.DisposableEffect
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
    val pageTitle = remember { mutableStateOf("") }
    val loadProgress = remember { mutableStateOf(0) }
    val popupUrl = remember { mutableStateOf<String?>(null) }
    val popupWebViewState = remember { mutableStateOf<WebView?>(null) }
    val consoleError = remember { mutableStateOf<String?>(null) }
    val statusText = remember(
        currentUrl.value,
        blockedUrl.value,
        pageError.value,
        pageTitle.value,
        loadProgress.value,
        popupUrl.value,
        consoleError.value,
    ) {
        when {
            pageError.value != null -> "页面加载失败：${pageError.value}"
            blockedUrl.value != null -> "已拦截非白名单跳转：${blockedUrl.value}"
            popupUrl.value != null -> "已接管新窗口跳转：${popupUrl.value}"
            consoleError.value != null -> "页面脚本提示：${consoleError.value}"
            loadProgress.value in 1..99 -> "页面加载中 ${loadProgress.value}%：${currentUrl.value}"
            pageTitle.value.isNotBlank() -> "页面标题：${pageTitle.value}"
            currentUrl.value.isNotBlank() -> "当前页面：${currentUrl.value}"
            else -> "当前页面：等待加载"
        }
    }

    fun isForegroundWebView(view: WebView?): Boolean {
        val popupWebView = popupWebViewState.value
        return if (popupWebView != null) {
            view === popupWebView
        } else {
            view === webViewState.value
        }
    }

    fun handleNavigation(view: WebView?, target: String): Boolean {
        if (target.isBlank() || isInternalWebViewUrl(target)) {
            return false
        }
        if (!isForegroundWebView(view)) {
            return false
        }
        if (!isAllowedHost(target, request.allowedHosts)) {
            currentUrl.value = target
            blockedUrl.value = target
            popupUrl.value = null
            return true
        }
        currentUrl.value = target
        blockedUrl.value = null
        popupUrl.value = null
        pageError.value = null
        consoleError.value = null
        return false
    }

    fun completeIfNeeded(view: WebView?, target: String) {
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

    DisposableEffect(Unit) {
        onDispose {
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(request.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "允许域名：${request.allowedHosts.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            configurePluginWebView(
                                request = request,
                                isForegroundWebView = ::isForegroundWebView,
                                popupWebViewState = popupWebViewState,
                                currentUrl = currentUrl,
                                blockedUrl = blockedUrl,
                                pageError = pageError,
                                pageTitle = pageTitle,
                                loadProgress = loadProgress,
                                popupUrl = popupUrl,
                                consoleError = consoleError,
                                onPopupWebViewCreated = { popupWebView ->
                                    popupWebViewState.value = popupWebView
                                },
                                onPopupWebViewClosed = { popupWebView ->
                                    if (popupWebViewState.value === popupWebView) {
                                        popupWebViewState.value = null
                                        popupUrl.value = null
                                        currentUrl.value = webViewState.value?.url?.toString().orEmpty()
                                        blockedUrl.value = null
                                        pageTitle.value = ""
                                        loadProgress.value = 0
                                        pageError.value = null
                                        consoleError.value = null
                                    }
                                },
                                completeIfNeeded = ::completeIfNeeded,
                                handleNavigation = ::handleNavigation,
                            )
                            webViewState.value = this
                            loadUrl(request.startUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                popupWebViewState.value?.let { popupWebView ->
                    DisposableEffect(popupWebView) {
                        onDispose {
                            popupWebView.destroy()
                        }
                    }
                    AndroidView(
                        factory = { popupWebView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun WebView.configurePluginWebView(
    request: WebSessionRequest,
    isForegroundWebView: (WebView?) -> Boolean,
    popupWebViewState: androidx.compose.runtime.MutableState<WebView?>,
    currentUrl: androidx.compose.runtime.MutableState<String>,
    blockedUrl: androidx.compose.runtime.MutableState<String?>,
    pageError: androidx.compose.runtime.MutableState<String?>,
    pageTitle: androidx.compose.runtime.MutableState<String>,
    loadProgress: androidx.compose.runtime.MutableState<Int>,
    popupUrl: androidx.compose.runtime.MutableState<String?>,
    consoleError: androidx.compose.runtime.MutableState<String?>,
    onPopupWebViewCreated: (WebView) -> Unit,
    onPopupWebViewClosed: (WebView) -> Unit,
    completeIfNeeded: (WebView?, String) -> Unit,
    handleNavigation: (WebView?, String) -> Boolean,
) {
    applyPluginBrowserSettings(request)
    val hostWebView = this
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (isForegroundWebView(view)) {
                loadProgress.value = newProgress
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (isForegroundWebView(view)) {
                pageTitle.value = title.orEmpty()
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val message = consoleMessage?.message().orEmpty()
            if (
                isForegroundWebView(hostWebView) &&
                consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                shouldSurfaceConsoleError(message)
            ) {
                consoleError.value = message
            }
            return false
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popupWebView = WebView(hostWebView.context).apply {
                configurePluginWebView(
                    request = request,
                    isForegroundWebView = isForegroundWebView,
                    popupWebViewState = popupWebViewState,
                    currentUrl = currentUrl,
                    blockedUrl = blockedUrl,
                    pageError = pageError,
                    pageTitle = pageTitle,
                    loadProgress = loadProgress,
                    popupUrl = popupUrl,
                    consoleError = consoleError,
                    onPopupWebViewCreated = onPopupWebViewCreated,
                    onPopupWebViewClosed = onPopupWebViewClosed,
                    completeIfNeeded = completeIfNeeded,
                    handleNavigation = handleNavigation,
                )
            }
            popupUrl.value = "已打开新窗口"
            onPopupWebViewCreated(popupWebView)
            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            if (window != null && window === popupWebViewState.value) {
                onPopupWebViewClosed(window)
            }
        }
    }
    webViewClient = object : WebViewClient() {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleNavigation(view, url.orEmpty())
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            webRequest: WebResourceRequest?,
        ): Boolean {
            return handleNavigation(view, webRequest?.url?.toString().orEmpty())
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!isForegroundWebView(view)) {
                return
            }
            val target = url.orEmpty()
            currentUrl.value = target
            popupUrl.value = null
            loadProgress.value = 0
            pageError.value = null
            consoleError.value = null
            view?.scrollTo(0, 0)
        }

        override fun onReceivedError(
            view: WebView?,
            webRequest: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (!isForegroundWebView(view) || webRequest?.isForMainFrame != true) {
                return
            }
            currentUrl.value = webRequest.url?.toString().orEmpty()
            pageError.value = "${error?.errorCode ?: 0}: ${error?.description?.toString().orEmpty()}"
        }

        override fun onReceivedHttpError(
            view: WebView?,
            webRequest: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            if (!isForegroundWebView(view) || webRequest?.isForMainFrame != true) {
                return
            }
            currentUrl.value = webRequest.url?.toString().orEmpty()
            pageError.value = "HTTP ${errorResponse?.statusCode ?: 0}: ${errorResponse?.reasonPhrase.orEmpty()}"
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (!isForegroundWebView(view)) {
                return
            }
            val target = url.orEmpty()
            currentUrl.value = target
            view?.scrollTo(0, 0)
            loadProgress.value = 100
            completeIfNeeded(view, target)
        }
    }
}

private fun WebView.applyPluginBrowserSettings(request: WebSessionRequest) {
    settings.javaScriptEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.setSupportMultipleWindows(true)
    settings.loadsImagesAutomatically = true
    settings.blockNetworkImage = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.defaultTextEncodingName = "UTF-8"
    settings.textZoom = 100
    settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    request.userAgent?.takeIf(String::isNotBlank)?.let { settings.userAgentString = it }
    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = true
    scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

private fun captureWebSessionPacket(
    webView: WebView,
    request: WebSessionRequest,
    currentUrl: String,
    onCaptured: (WebSessionPacket) -> Unit,
) {
    val fallbackPacket = emptyWebSessionPacket(request, currentUrl)
    val cookies = runCatching {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        collectCookies(cookieManager, request.allowedHosts, currentUrl)
    }.getOrDefault(emptyMap())
    val script = """
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
    """.trimIndent()
    runCatching {
        webView.evaluateJavascript(script) { raw ->
            val packet = runCatching {
                val payload = decodeJavascriptPayload(raw)
                WebSessionPacket(
                    finalUrl = currentUrl,
                    cookies = cookies,
                    localStorageSnapshot = payload.optJSONObject("localStorageSnapshot").toStringMap(),
                    sessionStorageSnapshot = payload.optJSONObject("sessionStorageSnapshot").toStringMap(),
                    htmlDigest = sha256(payload.optString("html", "")),
                    capturedFields = payload.optJSONObject("capturedFields").toStringMap(),
                    timestamp = OffsetDateTime.now().toString(),
                )
            }.getOrDefault(fallbackPacket)
            runCatching { onCaptured(packet) }
        }
    }.onFailure {
        runCatching { onCaptured(fallbackPacket) }
    }
}

private fun emptyWebSessionPacket(
    request: WebSessionRequest,
    currentUrl: String,
): WebSessionPacket {
    return WebSessionPacket(
        finalUrl = currentUrl,
        cookies = emptyMap(),
        localStorageSnapshot = emptyMap(),
        sessionStorageSnapshot = emptyMap(),
        htmlDigest = sha256(""),
        capturedFields = request.captureSelectors.associateWith { "" },
        timestamp = OffsetDateTime.now().toString(),
    )
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

fun isAllowedHost(url: String, allowedHosts: List<String>): Boolean {
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

fun shouldSurfaceConsoleError(message: String?): Boolean {
    val normalized = message.orEmpty().trim()
    if (normalized.isBlank()) {
        return false
    }
    return !normalized.contains("beangle is not defined", ignoreCase = true)
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

fun decodeJavascriptPayload(raw: String?): JSONObject {
    return runCatching { JSONObject(normalizeJavascriptPayload(raw)) }
        .getOrDefault(JSONObject())
}

fun normalizeJavascriptPayload(raw: String?): String {
    val candidate = raw.orEmpty().trim()
    if (candidate.isBlank() || candidate == "null") {
        return "{}"
    }
    val normalized = if (candidate.startsWith("\"") && candidate.endsWith("\"")) {
        candidate
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
    } else {
        candidate
    }
    return normalized.trim()
        .takeIf { it.startsWith("{") && it.endsWith("}") }
        ?: "{}"
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
