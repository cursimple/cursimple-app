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
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.plugin.logging.PluginLogger
import com.kebiao.viewer.core.plugin.web.WebCapturedPacket
import com.kebiao.viewer.core.plugin.web.WebSessionCaptureSpec
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.security.MessageDigest
import java.time.OffsetDateTime

private const val MAX_CAPTURE_SELECTOR_COUNT = 64
private const val MAX_CAPTURE_SELECTOR_LENGTH = 2048
private const val MAX_CAPTURE_SELECTOR_SCRIPT_LENGTH = 262144
private const val MAX_CAPTURED_FIELD_LENGTH = 4096
private const val MAX_STORAGE_ENTRY_COUNT = 256
private const val MAX_STORAGE_VALUE_LENGTH = 8192
private const val MAX_HTML_CAPTURE_CHARS = 524288

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebSessionScreen(
    request: WebSessionRequest,
    onFinish: (WebSessionPacket) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentUrl = remember(request.token) { mutableStateOf(request.startUrl) }
    val webViewState = remember(request.token) { mutableStateOf<WebView?>(null) }
    val isFinishing = remember(request.token) { mutableStateOf(false) }
    val isCapturing = remember(request.token) { mutableStateOf(false) }
    val blockedUrl = remember(request.token) { mutableStateOf<String?>(null) }
    val pageError = remember(request.token) { mutableStateOf<String?>(null) }
    val pageTitle = remember(request.token) { mutableStateOf("") }
    val loadProgress = remember(request.token) { mutableStateOf(0) }
    val popupUrl = remember(request.token) { mutableStateOf<String?>(null) }
    val popupWebViewState = remember(request.token) { mutableStateOf<WebView?>(null) }
    val consoleError = remember(request.token) { mutableStateOf<String?>(null) }
    val capturedPackets = remember(request.token) { mutableStateOf<Map<String, WebCapturedPacket>>(emptyMap()) }
    val autoNavigatedUrls = remember(request.token) { mutableStateOf<Set<String>>(emptySet()) }
    val webViewInitFailed = remember(request.token) { mutableStateOf(false) }
    val requiredPacketCount = remember(request) { requiredCapturePacketCount(request) }
    val statusText = androidx.compose.runtime.derivedStateOf {
        when {
            pageError.value != null -> "页面加载失败：${pageError.value}"
            blockedUrl.value != null -> "已拦截非白名单跳转：${blockedUrl.value}"
            popupUrl.value != null -> "已接管新窗口跳转：${popupUrl.value}"
            consoleError.value != null -> "页面脚本提示：${consoleError.value}"
            isCapturing.value -> "正在采集会话数据：${currentUrl.value}"
            capturedPackets.value.isNotEmpty() -> {
                if (requiredPacketCount > 0) {
                    "已捕获数据包 ${capturedPackets.value.count { it.value.id in requiredCapturePacketIds(request) }}/$requiredPacketCount"
                } else {
                    "正在等待可用会话数据"
                }
            }
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

    fun foregroundWebView(): WebView? = popupWebViewState.value ?: webViewState.value

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
            PluginLogger.warn(
                "plugin.web_session.navigation.blocked",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "url" to PluginLogger.sanitizeUrl(target),
                    "allowedHostCount" to request.allowedHosts.size,
                ),
            )
            return true
        }
        currentUrl.value = target
        blockedUrl.value = null
        popupUrl.value = null
        pageError.value = null
        consoleError.value = null
        return false
    }

    fun finishWithPacket(packet: WebSessionPacket, packets: Map<String, WebCapturedPacket>) {
        if (isFinishing.value) {
            return
        }
        isFinishing.value = true
        val finalPacket = aggregateWebSessionPacket(request, packet, packets)
        PluginLogger.info(
            "plugin.web_session.capture.complete",
            mapOf(
                "pluginId" to request.pluginId,
                "sessionId" to request.sessionId,
                "finalUrl" to PluginLogger.sanitizeUrl(finalPacket.finalUrl),
                "capturedPacketCount" to finalPacket.capturedPackets.size,
                "requiredPacketCount" to requiredCapturePacketCount(request),
            ),
        )
        onFinish(finalPacket)
    }

    fun handleCapturedSnapshot(packet: WebSessionPacket, forceFinish: Boolean) {
        val newPackets = readyCaptureSpecs(request, packet)
            .filterNot { capturedPackets.value.containsKey(it.id) }
            .associate { spec -> spec.id to packet.toCapturedPacket(spec.id) }
        val updatedPackets = capturedPackets.value + newPackets
        if (newPackets.isNotEmpty()) {
            capturedPackets.value = updatedPackets
            PluginLogger.info(
                "plugin.web_session.capture.packet_ready",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "packetIds" to newPackets.keys.joinToString(","),
                    "capturedPacketCount" to updatedPackets.size,
                    "requiredPacketCount" to requiredCapturePacketCount(request),
                ),
            )
        }
        if (forceFinish || hasAllRequiredCapturePackets(request, updatedPackets)) {
            finishWithPacket(packet, updatedPackets)
        }
    }

    fun probeWebSession(view: WebView?, target: String, forceFinish: Boolean = false) {
        val webView = view ?: return
        if (!forceFinish && effectiveCaptureSpecs(request).isEmpty()) {
            return
        }
        if (!forceFinish && hasAllRequiredCapturePackets(request, capturedPackets.value)) {
            return
        }
        if (isFinishing.value || isCapturing.value || !isForegroundWebView(webView)) {
            return
        }
        isCapturing.value = true
        PluginLogger.info(
            "plugin.web_session.capture.probe",
            mapOf(
                "pluginId" to request.pluginId,
                "sessionId" to request.sessionId,
                "url" to PluginLogger.sanitizeUrl(target),
                "capturedPacketCount" to capturedPackets.value.size,
                "requiredPacketCount" to requiredCapturePacketCount(request),
            ),
        )
        captureWebSessionPacket(
            webView = webView,
            request = request,
            currentUrl = target,
        ) { packet ->
            isCapturing.value = false
            handleCapturedSnapshot(packet, forceFinish)
        }
    }

    fun handleAutoNavigation(view: WebView?, target: String): Boolean {
        val webView = view ?: return false
        val nextUrl = autoNavigateTargetForRequest(request, target, autoNavigatedUrls.value) ?: return false
        autoNavigatedUrls.value = autoNavigatedUrls.value + nextUrl
        PluginLogger.info(
            "plugin.web_session.auto_navigate",
            mapOf(
                "pluginId" to request.pluginId,
                "sessionId" to request.sessionId,
                "fromUrl" to PluginLogger.sanitizeUrl(target),
                "toUrl" to PluginLogger.sanitizeUrl(nextUrl),
            ),
        )
        webView.loadUrl(nextUrl)
        return true
    }

    LaunchedEffect(request.token) {
        while (true) {
            delay(800)
            val webView = foregroundWebView() ?: continue
            val target = webView.url?.toString()?.takeIf(String::isNotBlank) ?: currentUrl.value
            probeWebSession(webView, target)
        }
    }

    DisposableEffect(request.token) {
        onDispose {
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
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
                    val webView = foregroundWebView() ?: return@Button
                    if (isFinishing.value || isCapturing.value) {
                        return@Button
                    }
                    PluginLogger.info(
                        "plugin.web_session.manual_complete.start",
                        mapOf(
                            "pluginId" to request.pluginId,
                            "sessionId" to request.sessionId,
                            "finalUrl" to PluginLogger.sanitizeUrl(currentUrl.value),
                        ),
                    )
                    val target = webView.url?.toString()?.takeIf(String::isNotBlank) ?: currentUrl.value
                    probeWebSession(webView, target, forceFinish = true)
                },
                enabled = !isFinishing.value && !isCapturing.value,
            ) {
                Text(if (isFinishing.value || isCapturing.value) "正在回传..." else "手动完成并回传")
            }
        }
        if (effectiveCaptureSpecs(request).isNotEmpty()) {
            Text(
                text = "正在等待插件声明的数据包，全部必需数据到齐后会自动继续。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            Text(
                text = statusText.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PluginWebViewHost(
            request = request,
            webViewState = webViewState,
            popupWebViewState = popupWebViewState,
            webViewInitFailed = webViewInitFailed,
            currentUrl = currentUrl,
            blockedUrl = blockedUrl,
            pageError = pageError,
            pageTitle = pageTitle,
            loadProgress = loadProgress,
            popupUrl = popupUrl,
            consoleError = consoleError,
            isForegroundWebView = ::isForegroundWebView,
            handleNavigation = ::handleNavigation,
            handleAutoNavigation = ::handleAutoNavigation,
            probeWebSession = { view, target -> probeWebSession(view, target) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp),
        )
    }
}

@Composable
private fun PluginWebViewHost(
    request: WebSessionRequest,
    webViewState: androidx.compose.runtime.MutableState<WebView?>,
    popupWebViewState: androidx.compose.runtime.MutableState<WebView?>,
    webViewInitFailed: androidx.compose.runtime.MutableState<Boolean>,
    currentUrl: androidx.compose.runtime.MutableState<String>,
    blockedUrl: androidx.compose.runtime.MutableState<String?>,
    pageError: androidx.compose.runtime.MutableState<String?>,
    pageTitle: androidx.compose.runtime.MutableState<String>,
    loadProgress: androidx.compose.runtime.MutableState<Int>,
    popupUrl: androidx.compose.runtime.MutableState<String?>,
    consoleError: androidx.compose.runtime.MutableState<String?>,
    isForegroundWebView: (WebView?) -> Boolean,
    handleNavigation: (WebView?, String) -> Boolean,
    handleAutoNavigation: (WebView?, String) -> Boolean,
    probeWebSession: (WebView?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val webViewHostState = remember(request.token) { mutableStateOf<FrameLayout?>(null) }

    LaunchedEffect(request.token, webViewHostState.value) {
        val host = webViewHostState.value ?: return@LaunchedEffect
        if (webViewState.value != null || webViewInitFailed.value) {
            return@LaunchedEffect
        }
        delay(16)
        if (webViewState.value != null || webViewInitFailed.value) {
            return@LaunchedEffect
        }
        var createdWebView: WebView? = null
        val webView = runCatching {
            WebView(host.context).also { createdWebView = it }.apply {
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
                    probeWebSession = probeWebSession,
                    handleNavigation = handleNavigation,
                    handleAutoNavigation = handleAutoNavigation,
                )
            }
        }.getOrElse { error ->
            createdWebView?.destroy()
            webViewInitFailed.value = true
            pageError.value = "WebView 初始化失败，请稍后重试。"
            PluginLogger.error(
                "plugin.web_session.webview.init.failure",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "source" to "main",
                    "errorClass" to error::class.java.name,
                    "errorMessage" to error.message.orEmpty(),
                ),
                error,
            )
            return@LaunchedEffect
        }
        host.removeAllViews()
        host.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        webViewState.value = webView
        webView.loadUrl(request.startUrl)
    }

    DisposableEffect(request.token) {
        onDispose {
            val host = webViewHostState.value
            host?.removeAllViews()
            webViewHostState.value = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                FrameLayout(context).also { webViewHostState.value = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (webViewState.value == null && !webViewInitFailed.value) {
            Text(
                text = "正在准备网页登录环境…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        popupWebViewState.value?.let { popupWebView ->
            DisposableEffect(popupWebView) {
                onDispose { popupWebView.destroy() }
            }
            AndroidView(
                factory = { popupWebView },
                modifier = Modifier.fillMaxSize(),
            )
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
    probeWebSession: (WebView?, String) -> Unit,
    handleNavigation: (WebView?, String) -> Boolean,
    handleAutoNavigation: (WebView?, String) -> Boolean,
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
                PluginLogger.warn(
                    "plugin.web_session.console_error",
                    mapOf(
                        "pluginId" to request.pluginId,
                        "sessionId" to request.sessionId,
                        "messageLength" to message.length,
                        "messageHash" to PluginLogger.sha256(message),
                    ),
                )
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
            var createdWebView: WebView? = null
            val popupWebView = runCatching {
                WebView(hostWebView.context).also { createdWebView = it }.apply {
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
                        probeWebSession = probeWebSession,
                        handleNavigation = handleNavigation,
                        handleAutoNavigation = handleAutoNavigation,
                    )
                }
            }.getOrElse { error ->
                createdWebView?.destroy()
                pageError.value = "浏览器弹窗初始化失败，请重试。"
                PluginLogger.error(
                    "plugin.web_session.webview.init.failure",
                    mapOf(
                        "pluginId" to request.pluginId,
                        "sessionId" to request.sessionId,
                        "source" to "popup",
                        "errorClass" to error::class.java.name,
                        "errorMessage" to error.message.orEmpty(),
                    ),
                    error,
                )
                return false
            }
            popupUrl.value = "已打开新窗口"
            PluginLogger.info(
                "plugin.web_session.popup.opened",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "currentUrl" to PluginLogger.sanitizeUrl(currentUrl.value),
                ),
            )
            onPopupWebViewCreated(popupWebView)
            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            if (window != null && window === popupWebViewState.value) {
                onPopupWebViewClosed(window)
                PluginLogger.info(
                    "plugin.web_session.popup.closed",
                    mapOf(
                        "pluginId" to request.pluginId,
                        "sessionId" to request.sessionId,
                        "currentUrl" to PluginLogger.sanitizeUrl(currentUrl.value),
                    ),
                )
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
            PluginLogger.info(
                "plugin.web_session.page_started",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "url" to PluginLogger.sanitizeUrl(target),
                ),
            )
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
            PluginLogger.error(
                "plugin.web_session.page_error",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "url" to PluginLogger.sanitizeUrl(currentUrl.value),
                    "errorCode" to (error?.errorCode ?: 0),
                    "errorDescription" to error?.description?.toString().orEmpty(),
                ),
            )
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
            PluginLogger.error(
                "plugin.web_session.http_error",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "url" to PluginLogger.sanitizeUrl(currentUrl.value),
                    "statusCode" to (errorResponse?.statusCode ?: 0),
                    "reasonPhrase" to errorResponse?.reasonPhrase.orEmpty(),
                ),
            )
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (!isForegroundWebView(view)) {
                return
            }
            val target = url.orEmpty()
            currentUrl.value = target
            view?.scrollTo(0, 0)
            loadProgress.value = 100
            PluginLogger.info(
                "plugin.web_session.page_finished",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "url" to PluginLogger.sanitizeUrl(target),
                ),
            )
            if (handleAutoNavigation(view, target)) {
                return
            }
            probeWebSession(view, target)
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
    val startedAt = System.currentTimeMillis()
    val rawCaptureSelectors = rawCaptureSelectorsForRequest(request)
    val captureSelectors = rawCaptureSelectors.boundedCaptureSelectors()
    PluginLogger.info(
        "plugin.web_session.capture.start",
        mapOf(
            "pluginId" to request.pluginId,
            "sessionId" to request.sessionId,
            "url" to PluginLogger.sanitizeUrl(currentUrl),
            "captureSelectorCount" to captureSelectors.size,
            "captureSelectorDroppedCount" to (rawCaptureSelectors.size - captureSelectors.size),
            "capturePacketCount" to effectiveCaptureSpecs(request).size,
        ),
    )
    val cookies = if (request.extractCookies) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            collectCookies(cookieManager, request.allowedHosts, currentUrl)
        }.getOrDefault(emptyMap())
    } else {
        emptyMap()
    }
    val selectorScript = captureSelectors.toCaptureSelectorArrayScript()
    val script = """
        (() => {
            const shouldCaptureLocalStorage = ${request.extractLocalStorage};
            const shouldCaptureSessionStorage = ${request.extractSessionStorage};
            const shouldCaptureHtml = ${request.extractHtmlDigest};
            const MAX_STORAGE_ENTRY_COUNT = $MAX_STORAGE_ENTRY_COUNT;
            const MAX_STORAGE_VALUE_LENGTH = $MAX_STORAGE_VALUE_LENGTH;
            const MAX_CAPTURED_FIELD_LENGTH = $MAX_CAPTURED_FIELD_LENGTH;
            const MAX_HTML_CAPTURE_CHARS = $MAX_HTML_CAPTURE_CHARS;
            const truncate = (value, limit) => {
              const text = (value ?? "") + "";
              return text.length > limit ? text.slice(0, limit) : text;
            };
            const readStorage = (name) => {
              const snapshot = {};
              try {
                const storage = window[name];
                const limit = Math.min(storage.length, MAX_STORAGE_ENTRY_COUNT);
                for (let i = 0; i < limit; i++) {
                  const key = storage.key(i);
                  if (key) {
                    snapshot[key] = truncate(storage.getItem(key), MAX_STORAGE_VALUE_LENGTH);
                  }
                }
              } catch (error) {}
              return snapshot;
            };
            const selectors = $selectorScript;
            const fields = {};
            try {
              selectors.forEach((selector) => {
                try {
                  const node = document.querySelector(selector);
                  if (node) {
                    fields[selector] = truncate((node.value || node.textContent || "").trim(), MAX_CAPTURED_FIELD_LENGTH);
                  }
                } catch (error) {}
              });
            } catch (error) {}
            let html = "";
            try {
              if (shouldCaptureHtml) {
                html = truncate(document.documentElement ? (document.documentElement.outerHTML || "") : "", MAX_HTML_CAPTURE_CHARS);
              }
            } catch (error) {}
            try {
              return JSON.stringify({
                html,
                localStorageSnapshot: shouldCaptureLocalStorage ? readStorage("localStorage") : {},
                sessionStorageSnapshot: shouldCaptureSessionStorage ? readStorage("sessionStorage") : {},
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
                    localStorageSnapshot = if (request.extractLocalStorage) {
                        payload.optJSONObject("localStorageSnapshot").toStringMap()
                    } else {
                        emptyMap()
                    },
                    sessionStorageSnapshot = if (request.extractSessionStorage) {
                        payload.optJSONObject("sessionStorageSnapshot").toStringMap()
                    } else {
                        emptyMap()
                    },
                    htmlDigest = if (request.extractHtmlDigest) {
                        sha256(payload.optString("html", ""))
                    } else {
                        ""
                    },
                    capturedFields = payload.optJSONObject("capturedFields").toStringMap(),
                    timestamp = OffsetDateTime.now().toString(),
                )
            }.getOrDefault(fallbackPacket)
            PluginLogger.info(
                if (packet == fallbackPacket) "plugin.web_session.capture.fallback" else "plugin.web_session.capture.success",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "finalUrl" to PluginLogger.sanitizeUrl(packet.finalUrl),
                    "cookieCount" to packet.cookies.size,
                    "localStorageCount" to packet.localStorageSnapshot.size,
                    "sessionStorageCount" to packet.sessionStorageSnapshot.size,
                    "capturedFieldCount" to packet.capturedFields.size,
                    "htmlDigest" to packet.htmlDigest,
                    "elapsedMs" to (System.currentTimeMillis() - startedAt),
                ),
            )
            runCatching { onCaptured(packet) }
        }
    }.onFailure {
        PluginLogger.error(
            "plugin.web_session.capture.failure",
            mapOf(
                "pluginId" to request.pluginId,
                "sessionId" to request.sessionId,
                "finalUrl" to PluginLogger.sanitizeUrl(currentUrl),
                "elapsedMs" to (System.currentTimeMillis() - startedAt),
            ),
            it,
        )
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
        htmlDigest = if (request.extractHtmlDigest) sha256("") else "",
        capturedFields = captureSelectorsForRequest(request).associateWith { "" },
        capturedPackets = emptyMap(),
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

fun effectiveCaptureSpecs(request: WebSessionRequest): List<WebSessionCaptureSpec> {
    if (request.capturePackets.isNotEmpty()) {
        return request.capturePackets
    }
    val legacyCompletion = request.completionUrlContains?.takeIf(String::isNotBlank)
    if (legacyCompletion == null && request.captureSelectors.isEmpty()) {
        return emptyList()
    }
    return listOf(
        WebSessionCaptureSpec(
            id = request.sessionId,
            required = true,
            urlContains = legacyCompletion,
            captureSelectors = request.captureSelectors,
        ),
    )
}

fun captureSelectorsForRequest(request: WebSessionRequest): List<String> {
    return rawCaptureSelectorsForRequest(request).boundedCaptureSelectors()
}

fun requiredCapturePacketIds(request: WebSessionRequest): Set<String> {
    return effectiveCaptureSpecs(request)
        .filter(WebSessionCaptureSpec::required)
        .map(WebSessionCaptureSpec::id)
        .toSet()
}

fun requiredCapturePacketCount(request: WebSessionRequest): Int {
    return requiredCapturePacketIds(request).size
}

fun readyCaptureSpecs(request: WebSessionRequest, packet: WebSessionPacket): List<WebSessionCaptureSpec> {
    return effectiveCaptureSpecs(request).filter { spec -> isCaptureSpecReady(spec, packet) }
}

fun hasAllRequiredCapturePackets(
    request: WebSessionRequest,
    packets: Map<String, WebCapturedPacket>,
): Boolean {
    val requiredIds = requiredCapturePacketIds(request)
    return requiredIds.isNotEmpty() && packets.keys.containsAll(requiredIds)
}

fun isCaptureSpecReady(spec: WebSessionCaptureSpec, packet: WebSessionPacket): Boolean {
    if (spec.id.isBlank()) {
        return false
    }
    val urlContains = spec.urlContains
    val urlHost = spec.urlHost
    val urlPathContains = spec.urlPathContains
    if (!urlContains.isNullOrBlank() && !packet.finalUrl.contains(urlContains, ignoreCase = true)) {
        return false
    }
    if (!urlHost.isNullOrBlank() && !urlHostMatches(packet.finalUrl, urlHost)) {
        return false
    }
    if (!urlPathContains.isNullOrBlank() && !urlPathContains(packet.finalUrl, urlPathContains)) {
        return false
    }
    if (packet.cookies.size < spec.minCookieCount) {
        return false
    }
    if (packet.localStorageSnapshot.size < spec.minLocalStorageCount) {
        return false
    }
    if (packet.sessionStorageSnapshot.size < spec.minSessionStorageCount) {
        return false
    }
    if (!spec.requiredCookies.all(packet.cookies::containsKey)) {
        return false
    }
    if (!spec.requiredLocalStorageKeys.all(packet.localStorageSnapshot::containsKey)) {
        return false
    }
    if (!spec.requiredSessionStorageKeys.all(packet.sessionStorageSnapshot::containsKey)) {
        return false
    }
    val requiredSelectors = spec.requiredSelectors.ifEmpty { spec.captureSelectors }
        .filter(String::isNotBlank)
        .distinct()
    val boundedSelectors = requiredSelectors.boundedCaptureSelectors()
    if (boundedSelectors.size != requiredSelectors.size) {
        return false
    }
    return boundedSelectors.all { selector -> !packet.capturedFields[selector].isNullOrBlank() }
}

fun aggregateWebSessionPacket(
    request: WebSessionRequest,
    latestPacket: WebSessionPacket,
    packets: Map<String, WebCapturedPacket>,
): WebSessionPacket {
    if (packets.isEmpty()) {
        return latestPacket
    }
    val orderedPackets = effectiveCaptureSpecs(request)
        .mapNotNull { spec -> packets[spec.id]?.let { spec.id to it } }
        .toMap() + packets.filterKeys { id -> effectiveCaptureSpecs(request).none { it.id == id } }
    val mergedCookies = linkedMapOf<String, String>()
    val mergedLocalStorage = linkedMapOf<String, String>()
    val mergedSessionStorage = linkedMapOf<String, String>()
    val mergedFields = linkedMapOf<String, String>()
    orderedPackets.values.forEach { packet ->
        mergedCookies.putAll(packet.cookies)
        mergedLocalStorage.putAll(packet.localStorageSnapshot)
        mergedSessionStorage.putAll(packet.sessionStorageSnapshot)
        packet.capturedFields.forEach { (key, value) ->
            mergedFields[key] = value
            mergedFields["${packet.id}.$key"] = value
        }
    }
    mergedCookies.putAll(latestPacket.cookies)
    mergedLocalStorage.putAll(latestPacket.localStorageSnapshot)
    mergedSessionStorage.putAll(latestPacket.sessionStorageSnapshot)
    latestPacket.capturedFields.forEach { (key, value) -> mergedFields[key] = value }
    return latestPacket.copy(
        cookies = mergedCookies,
        localStorageSnapshot = mergedLocalStorage,
        sessionStorageSnapshot = mergedSessionStorage,
        capturedFields = mergedFields,
        capturedPackets = orderedPackets,
    )
}

fun WebSessionPacket.toCapturedPacket(packetId: String): WebCapturedPacket {
    return WebCapturedPacket(
        id = packetId,
        finalUrl = finalUrl,
        cookies = cookies,
        localStorageSnapshot = localStorageSnapshot,
        sessionStorageSnapshot = sessionStorageSnapshot,
        htmlDigest = htmlDigest,
        capturedFields = capturedFields,
        timestamp = timestamp,
    )
}

private fun urlHostMatches(url: String, expectedHost: String): Boolean {
    val actualHost = runCatching { java.net.URL(url).host.lowercase() }.getOrNull() ?: return false
    val expected = expectedHost.lowercase()
    return actualHost == expected || actualHost.endsWith(".$expected")
}

private fun urlPathContains(url: String, expectedPathPart: String): Boolean {
    val path = runCatching { java.net.URL(url).path }.getOrDefault("")
    return path.contains(expectedPathPart, ignoreCase = true)
}

fun isAllowedHost(url: String, allowedHosts: List<String>): Boolean {
    return runCatching { java.net.URL(url).host.lowercase() }.getOrNull()?.let { host ->
        allowedHosts.any { allowed ->
            host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}")
        }
    } ?: false
}

fun autoNavigateTargetForRequest(
    request: WebSessionRequest,
    currentUrl: String,
    alreadyNavigatedUrls: Set<String>,
): String? {
    val trigger = request.autoNavigateOnUrlContains?.trim().orEmpty()
    val target = request.autoNavigateToUrl?.trim().orEmpty()
    if (trigger.isBlank() || target.isBlank()) {
        return null
    }
    val matchesTrigger = if (trigger.startsWith("http", ignoreCase = true)) {
        currentUrl.contains(trigger, ignoreCase = true)
    } else {
        urlPathContains(currentUrl, trigger)
    }
    if (!matchesTrigger) {
        return null
    }
    if (currentUrl.equals(target, ignoreCase = true)) {
        return null
    }
    if (alreadyNavigatedUrls.any { it.equals(target, ignoreCase = true) }) {
        return null
    }
    if (!isAllowedHost(target, request.allowedHosts)) {
        return null
    }
    return target
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
    val knownEamsNoise = listOf(
        "beangle is not defined",
        "jQuery is not defined",
    )
    return knownEamsNoise.none { normalized.contains(it, ignoreCase = true) }
}

private fun rawCaptureSelectorsForRequest(request: WebSessionRequest): List<String> {
    return (
        request.captureSelectors +
            effectiveCaptureSpecs(request).flatMap { spec -> spec.captureSelectors + spec.requiredSelectors }
        )
        .filter(String::isNotBlank)
        .distinct()
}

private fun List<String>.boundedCaptureSelectors(): List<String> {
    return asSequence()
        .filter { it.isNotBlank() && it.length <= MAX_CAPTURE_SELECTOR_LENGTH }
        .distinct()
        .take(MAX_CAPTURE_SELECTOR_COUNT)
        .toList()
}

internal fun List<String>.toCaptureSelectorArrayScript(): String {
    val boundedSelectors = boundedCaptureSelectors()
    if (boundedSelectors.isEmpty()) {
        return "[]"
    }
    val builder = StringBuilder()
    builder.append('[')
    var hasSelector = false
    for (selector in boundedSelectors) {
        val checkpoint = builder.length
        if (hasSelector) {
            builder.append(',')
        }
        if (!builder.appendQuotedJsString(selector)) {
            builder.setLength(checkpoint)
            continue
        }
        if (builder.length > MAX_CAPTURE_SELECTOR_SCRIPT_LENGTH) {
            builder.setLength(checkpoint)
            break
        }
        hasSelector = true
    }
    builder.append(']')
    return builder.toString()
}

private fun StringBuilder.appendQuotedJsString(value: String): Boolean {
    if (value.isBlank() || value.length > MAX_CAPTURE_SELECTOR_LENGTH) {
        return false
    }
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(char)
        }
    }
    append('"')
    return true
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
