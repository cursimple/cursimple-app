package com.x500x.cursimple.feature.plugin.extension

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.x500x.cursimple.feature.plugin.R
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.UUID

/**
 * Legacy login polls checkLogin on the site's page without attaching a native bridge; the user
 * controls authentication.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExtensionLoginScreen(
    title: String,
    buildRequest: () -> ExtensionRunRequest,
    viewportWidth: Int? = null,
    onLoggedIn: (ExtensionAccount?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = remember { buildRequest() }
    com.x500x.cursimple.feature.plugin.ui.OwnEmbeddedPageGestures()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf<Pair<String, Long>?>(null) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (!finished) {
            delay(LOGIN_POLL_MILLIS)
            val url = view.url.orEmpty()
            if (!ExtensionUrls.isAllowed(url, request.allowedHosts)) continue
            val current = attempt
            if (current == null) {
                val token = UUID.randomUUID().toString()
                attempt = token to System.currentTimeMillis()
                view.evaluateJavascript(buildExtensionScript(request, token, useBridge = false), null)
                continue
            }
            view.evaluateJavascript("window.__cursimpleExtensionResult || null") { raw ->
                val result = parseLoginPollResult(raw, request.maxOutputBytes)
                when {
                    result == null -> {
                        if (System.currentTimeMillis() - current.second > LOGIN_ATTEMPT_TIMEOUT_MILLIS) attempt = null
                    }
                    result is ExtensionRunResult.Completed && result.result.boolean("loggedIn") == true -> {
                        finished = true
                        CookieManager.getInstance().flush()
                        val account = (result.result["account"] as? JsonObject)?.let { obj ->
                            runCatching { extensionJson.decodeFromJsonElement(ExtensionAccount.serializer(), obj) }.getOrNull()
                        }
                        onLoggedIn(account)
                    }
                    result is ExtensionRunResult.Failed -> {
                        hint = result.message
                        attempt = null
                    }
                    else -> attempt = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                runCatching {
                    view.stopLoading()
                    view.destroy()
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.extension_login_cancel))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.extension_login_title, title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pageTitle.ifBlank { stringResource(R.string.extension_login_subtitle) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.extension_login_reload))
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = hint ?: stringResource(R.string.extension_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        // Use MATCH_PARENT to prevent content-sized WebView collapsing percentage-height login layouts.
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        request.userAgent?.let { settings.userAgentString = it }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, value: String?) {
                                pageTitle = value.orEmpty()
                            }
                        }
                        val viewportScript = viewportWidth?.coerceIn(320, 2560)?.let(::desktopViewportScript)
                        val injectedAtStart = viewportScript != null &&
                            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                            runCatching {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    viewportScript,
                                    request.allowedHosts.map { "https://$it" }.toSet(),
                                )
                            }.isSuccess
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                attempt = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                // Older WebViews apply viewport fallback after loading.
                                if (viewportScript != null && !injectedAtStart &&
                                    ExtensionUrls.isAllowed(url.orEmpty(), request.allowedHosts)
                                ) {
                                    view?.evaluateJavascript(viewportScript, null)
                                }
                            }
                        }
                        loadUrl(request.url)
                        webView = this
                    }
                },
            )
        }
    }
}

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun desktopViewportScript(width: Int): String = """
(function () {
  var content = "width=$width, user-scalable=yes";
  function fix() {
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      if (!document.head) return;
      meta = document.createElement("meta");
      meta.name = "viewport";
      document.head.appendChild(meta);
    }
    if (meta.getAttribute("content") !== content) meta.setAttribute("content", content);
  }
  fix();
  new MutationObserver(fix).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["content"] });
})();
""".trimIndent()

private const val LOGIN_POLL_MILLIS = 1_500L
private const val LOGIN_ATTEMPT_TIMEOUT_MILLIS = 20_000L

/**
 * Expire each named cookie for host and parent-domain variants; CookieManager has no per-domain
 * deletion API.
 */
fun clearExtensionCookies(allowedHosts: List<String>) {
    val manager = CookieManager.getInstance()
    allowedHosts.forEach { host ->
        val url = "https://$host/"
        val names = manager.getCookie(url).orEmpty()
            .split(';')
            .mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotBlank) }
            .distinct()
        val parent = host.substringAfter('.', "").takeIf { it.contains('.') }
        names.forEach { name ->
            manager.setCookie(url, "$name=; Max-Age=0; Path=/")
            manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$host")
            parent?.let { manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$it") }
        }
    }
    manager.flush()
}
