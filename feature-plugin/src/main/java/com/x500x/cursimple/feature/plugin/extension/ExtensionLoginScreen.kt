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
 * 扩展组件的登录页：直接打开站点自己的登录页面，用户怎么登（扫码、密码、短信、验证码）都由站点处理。
 *
 * 宿主只做一件事：每隔一会儿在当前页面上跑一次组件的 checkLogin，登上了就把账号交回去、关掉这一页。
 * 这一页不挂 JS 桥（用户可能点到别的网页），结果写在 window 上，由这里轮询读取。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExtensionLoginScreen(
    title: String,
    buildRequest: () -> ExtensionRunRequest,
    /** 见 PluginExtensionSpec.loginViewportWidth */
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
    // 当前这一轮 checkLogin 的 token 与开跑时间；换页时清掉，下一拍重新注入
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
                        // 还在跑；太久没回音就作废，下一拍重来
                        if (System.currentTimeMillis() - current.second > LOGIN_ATTEMPT_TIMEOUT_MILLIS) attempt = null
                    }
                    result is ExtensionRunResult.Completed && result.result.boolean("loggedIn") == true -> {
                        finished = true
                        // 登录态要落盘：进程被回收后后台同步还得用
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
                        // 不给 MATCH_PARENT 的话 AndroidView 按 WRAP_CONTENT 算，WebView 进入「高度随内容」模式：
                        // 页面里的 height:100% 全变成 0，登录框被居中挤到屏幕外面去
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
                        // 最好在页面脚本跑之前就把 viewport 改掉：站点的脚本按窗口宽高排版，晚了就排歪了
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
                                // 换页了，上一页里那一轮检查作废
                                attempt = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                // 老 WebView 不支持提前注入：页面出来后再补一刀，横向至少能缩进屏幕
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

/** 把页面的 viewport 改成固定宽度，站点后来再改回去也盯着改掉 */
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
 * 退出登录：把组件声明的那几个站点上的 Cookie 全部作废。
 *
 * CookieManager 没有「按域名删」的接口，只能逐个名字写一条立即过期的；
 * 父域 Cookie（Domain=.example.com）还得带上 Domain 才删得掉，所以两种都写一遍。
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
