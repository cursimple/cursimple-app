package com.x500x.cursimple.feature.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import com.x500x.cursimple.core.data.toLocale
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.feature.plugin.home.*
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon

private const val PAGE_BASE_URL = "https://plugin-page.cursimple.invalid/"

/**
 * Hosts a plugin's own home page. The page is the plugin's HTML and draws everything on it; this screen loads it,
 * answers its calls through [PluginPageController], and shows the sign-in sheet the page asks for.
 */
@Composable
internal fun PluginHomeScreen(
    record: InstalledPluginRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = false,
    onRegisterNavigator: (((() -> Unit)?) -> Unit)? = null,
) {
    com.x500x.cursimple.feature.plugin.ui.OwnEmbeddedPageGestures()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val appLanguage = com.x500x.cursimple.core.data.AppLocale.current(context)
    val resolvedLocale = appLanguage.toLocale() ?: resources.configuration.locales[0]
    val language = resolvedLocale.toLanguageTag()
    val currentTheme by rememberUpdatedState(hostThemeJson())
    var pkg by remember(record.packageRevision) { mutableStateOf<PluginPagePackage?>(null) }
    var loadFailed by remember(record.packageRevision) { mutableStateOf(false) }
    var loginRequest by remember { mutableStateOf<PluginPageLoginRequest?>(null) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var loginBusy by remember { mutableStateOf<com.x500x.cursimple.feature.plugin.home.PluginPageLoginBusy?>(null) }
    var loginNavigate by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val view = remember(record.packageRevision) { WebView(context) }

    LaunchedEffect(record.packageRevision) {
        loadFailed = false
        pkg = try {
            withContext(Dispatchers.IO) {
                val store = PluginFileStore(context)
                val manifest = store.loadManifest(record)
                val home = requireNotNull(manifest.home)
                PluginPagePackage(manifest, store.readText(record, home.entry), store.readText(record, home.page))
            }
        } catch (_: Exception) {
            loadFailed = true
            null
        }
    }

    val controller = remember(record.packageRevision, pkg) {
        pkg?.let { loaded ->
            PluginPageController(
                context = context,
                record = record,
                manifest = loaded.manifest,
                script = loaded.script,
                storage = PluginPageStorage(
                    context,
                    // Stable across plugin updates: an update must keep the account and cached results.
                    record.pluginId.substringBefore(":local"),
                ),
                credentials = WebLoginCredentialStore(context),
                scope = scope,
                language = language,
                theme = { currentTheme },
                respond = { id, ok, text ->
                    view.evaluateJavascript(
                        "window.CurSimplePage && window.CurSimplePage.__resolve(${jsStringLiteral(id)}, $ok, ${jsStringLiteral(text)})",
                        null,
                    )
                },
                emit = { name, text ->
                    view.evaluateJavascript(
                        "window.CurSimplePage && window.CurSimplePage.__emit(${jsStringLiteral(name)}, ${jsStringLiteral(text)})",
                        null,
                    )
                },
                onOpenLogin = { request ->
                    loginNavigate = null
                    loginMessage = null
                    loginRequest = request
                },
                onCloseLogin = {
                    loginRequest = null
                    loginMessage = null
                    loginBusy = null
                    loginNavigate = null
                },
                onLoginMessage = { text -> loginBusy = null; loginMessage = text.take(500) },
                onLoginBusy = { busy -> loginBusy = busy },
                onLoginNavigate = { url -> loginNavigate = (loginNavigate?.first ?: 0) + 1 to url },
            )
        }
    }

    DisposableEffect(view) {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = false
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= 29) view.settings.blockNetworkLoads = true
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            // The page is local: any navigation or network request it tries is refused.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        onDispose {
            view.removeJavascriptInterface(PAGE_BRIDGE_NAME)
            view.stopLoading()
            view.destroy()
        }
    }

    LaunchedEffect(controller) {
        val loaded = pkg ?: return@LaunchedEffect
        val active = controller ?: return@LaunchedEffect
        view.addJavascriptInterface(PluginPageJsBridge { id, method, args -> active.handle(id, method, args) }, PAGE_BRIDGE_NAME)
        view.loadDataWithBaseURL(PAGE_BASE_URL, pluginPageDocument(loaded.page), "text/html", "utf-8", null)
    }

    val manifest = pkg?.manifest
    fun navigateBack() {
        // Reuse the same opt-in back event as other plugin-owned pages.
        view.evaluateJavascript("window.__CurSimpleComponentHasBack === true") { handled ->
            if (handled == "true") {
                view.evaluateJavascript("window.dispatchEvent(new Event('cursimple-back'));", null)
            } else onBack()
        }
    }
    BackHandler(onBack = ::navigateBack)
    DisposableEffect(Unit) {
        onRegisterNavigator?.invoke(::navigateBack)
        onDispose { onRegisterNavigator?.invoke(null) }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (showTopBar) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = ::navigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plugin_home_back))
                    }
                    Text(
                        record.name,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                loadFailed -> Text(
                    stringResource(R.string.plugin_home_load_failed),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                controller == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().weight(1f))
            }
        }
    }

    loginRequest?.let { request ->
        val active = controller
        if (manifest != null && active != null) PluginLoginSheet(
            record = record,
            manifest = manifest,
            request = request,
            message = loginMessage,
            busy = loginBusy,
            navigate = loginNavigate,
            onAction = { action, html -> active.onLoginAction(action.id, html) },
            onPageLoaded = { url, loading -> active.onLoginPage(url, loading) },
            onCredentialTyped = active::onCredentialTyped,
            onDismiss = {
                active.onLoginDismissed()
                loginRequest = null
                loginMessage = null
                loginBusy = null
                loginNavigate = null
            },
        )
    }
    BackHandler {
        if (loginRequest != null) {
            controller?.onLoginDismissed()
            loginRequest = null
            loginMessage = null
            loginBusy = null
            loginNavigate = null
        } else {
            navigateBack()
        }
    }
}

private data class PluginPagePackage(val manifest: PluginManifest, val script: String, val page: String)

/** Theme colours the page can draw with, so it matches the app in light and dark mode. */
@Composable
private fun hostThemeJson(): JsonObject {
    val colors = MaterialTheme.colorScheme
    return buildJsonObject {
        put("dark", colors.surface.luminance() < 0.5f)
        put("primary", colors.primary.cssHex())
        put("onPrimary", colors.onPrimary.cssHex())
        put("primaryContainer", colors.primaryContainer.cssHex())
        put("onPrimaryContainer", colors.onPrimaryContainer.cssHex())
        put("background", colors.background.cssHex())
        put("surface", colors.surface.cssHex())
        put("surfaceContainer", colors.surfaceContainer.cssHex())
        put("surfaceContainerLow", colors.surfaceContainerLow.cssHex())
        put("onSurface", colors.onSurface.cssHex())
        put("onSurfaceVariant", colors.onSurfaceVariant.cssHex())
        put("outlineVariant", colors.outlineVariant.cssHex())
        put("error", colors.error.cssHex())
    }
}

private fun Color.cssHex(): String = "#%06x".format(toArgb() and 0xFFFFFF)

/** Quote a Kotlin string as a JavaScript string literal, safe inside a script tag. */
internal fun jsStringLiteral(value: String): String = JsonPrimitive(value).toString().replace("<", "\\u003c")

/** Puts the bridge shim into the plugin page's head, so the page starts with `window.CurSimplePage` ready. */
internal fun pluginPageDocument(page: String): String {
    val shim = "<script>$PLUGIN_PAGE_SHIM</script>"
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(page)
    return if (head != null) page.replaceRange(head.range, head.value + shim) else shim + page
}

private const val PLUGIN_PAGE_SHIM = """
(function () {
  var pending = {}, seq = 0, listeners = {};
  function parse(text) { return text ? JSON.parse(text) : null; }
  window.CurSimplePage = {
    call: function (method, args) {
      return new Promise(function (resolve, reject) {
        var text;
        try { text = JSON.stringify(args || {}); }
        catch (e) { reject({ code: "invalid_argument", detail: "" }); return; }
        if (text.length > 5000000) { reject({ code: "payload_too_large", detail: "" }); return; }
        var id = "c" + (++seq);
        var timer = setTimeout(function () {
          if (!pending[id]) return;
          delete pending[id];
          reject({ code: "bridge_timeout", detail: "" });
        }, method === "run" ? 110000 : 15000);
        pending[id] = { resolve: resolve, reject: reject, timer: timer };
        try { CurSimplePageHost.call(id, method, text); }
        catch (e) { clearTimeout(timer); delete pending[id]; reject({ code: "bridge_unavailable", detail: "" }); }
      });
    },
    on: function (name, fn) { (listeners[name] = listeners[name] || []).push(fn); },
    __resolve: function (id, ok, text) {
      var p = pending[id];
      if (!p) { return; }
      clearTimeout(p.timer);
      delete pending[id];
      var value;
      try { value = parse(text); }
      catch (e) { p.reject({ code: "invalid_response", detail: "" }); return; }
      if (ok) { p.resolve(value); } else { p.reject(value); }
    },
    __emit: function (name, text) {
      var value = parse(text);
      (listeners[name] || []).forEach(function (fn) { try { fn(value); } catch (e) {} });
    }
  };
})();
"""

/** The school sign-in sheet. Its buttons and help text come from the plugin's request. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PluginLoginSheet(
    record: InstalledPluginRecord,
    manifest: PluginManifest,
    request: PluginPageLoginRequest,
    message: String?,
    busy: com.x500x.cursimple.feature.plugin.home.PluginPageLoginBusy?,
    navigate: Pair<Int, String>?,
    onAction: (PluginPageLoginAction, String?) -> Unit,
    onPageLoaded: (String, Boolean) -> Unit,
    onCredentialTyped: (WebLoginCredential) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { WebLoginCredentialStore(context) }
    val view = remember(record.packageRevision) { WebView(context) }
    // A new account is held until the plugin page confirms the sign-in, then saved without asking again.
    val assist = remember(record.packageRevision) {
        WebLoginAssist(record.pluginId, manifest.allowedHosts, store, {
            if (store.find(record.pluginId, it.host) != it) onCredentialTyped(it)
        }).also { it.autoSubmitOnce = request.autoSubmit }
    }
    var progress by remember { mutableIntStateOf(0) }
    LaunchedEffect(navigate) { navigate?.let { (_, url) -> view.loadUrl(url) } }
    var pageError by remember { mutableStateOf<String?>(null) }
    val popups = remember { mutableListOf<WebView>() }
    DisposableEffect(view) {
        view.applyPluginBrowserSettings(manifest.userAgent.orEmpty())
        // Same legacy-site handling as the import session, so the academic home page is never a bare fragment.
        val legacySiteCompat = LegacySiteCompat(context.applicationContext, manifest.allowedHosts, userAgent = { manifest.userAgent.orEmpty() })
        legacySiteCompat.install(view)
        assist.install(view)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, value: Int) { progress = value }
            override fun onCreateWindow(parent: WebView?, dialog: Boolean, gesture: Boolean, result: android.os.Message?): Boolean {
                if (result == null || popups.size >= 4) return false
                val popup = WebView(context)
                popups += popup
                popup.hardenLocalAccess()
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                manifest.userAgent?.let { popup.settings.userAgentString = it }
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(child: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (isAllowedHost(url, manifest.allowedHosts)) view.loadUrl(url)
                        return url != "about:blank"
                    }
                }
                (result.obj as? WebView.WebViewTransport)?.webView = popup
                result.sendToTarget()
                return true
            }
        }
        view.settings.setSupportMultipleWindows(true)
        view.settings.javaScriptCanOpenWindowsAutomatically = true
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                request?.let { legacySiteCompat.interceptDocument(it) } ?: super.shouldInterceptRequest(view, request)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                val blocked = !isAllowedHost(url, manifest.allowedHosts)
                if (blocked) pageError = context.resources.getString(R.string.plugin_web_status_blocked,
                    runCatching { java.net.URI(url).host }.getOrNull().orEmpty())
                return blocked
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // Lets the plugin page cover the sheet while its system loads.
                if (url != null && isAllowedHost(url, manifest.allowedHosts)) onPageLoaded(url, true)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.let { assist.onPageFinished(it, url) }
                // Lets the plugin page notice when the user reaches its system and confirm the sign-in itself.
                if (url != null && isAllowedHost(url, manifest.allowedHosts)) onPageLoaded(url, false)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) pageError = failure?.description?.toString()
            }
        }
        onDispose {
            view.stopLoading()
            CookieManager.getInstance().flush()
            view.removeJavascriptInterface(WebLoginAssist.BRIDGE_NAME)
            view.destroy()
            popups.forEach { it.destroy() }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.plugin_home_close)) }
                    Text(request.title.ifBlank { stringResource(R.string.plugin_home_login) },
                        Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (request.actions.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    request.actions.forEach { action ->
                        AppOutlinedButton(onClick = {
                            if (action.capture && isAllowedHost(view.url.orEmpty(), manifest.allowedHosts)) {
                                view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                    onAction(action, pageSource(raw))
                                }
                            } else {
                                onAction(action, null)
                            }
                        }) { Text(action.label) }
                    }
                }
                if (request.helpText.isNotBlank()) Text(request.helpText, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
                (message ?: pageError)?.let { failure ->
                    Text(failure, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { hostContext -> createPluginHomeWebViewHost(hostContext, view, request.url) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Same overlay as the timetable import; the scrim also keeps taps off the page meanwhile.
                    busy?.let { PluginWorkingOverlay(stageLabel = it.text.ifBlank { null }, title = it.title) }
                }
            }
        }
    }
}

/** The page source a JavaScript evaluation returned, or null when it was not a string. */
private fun pageSource(raw: String?): String? = runCatching {
    (Json.parseToJsonElement(raw.orEmpty()) as? JsonPrimitive)?.content
}.getOrNull()

internal fun createPluginHomeWebViewHost(context: Context, view: WebView, startUrl: String): FrameLayout =
    FrameLayout(context).apply {
        addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // Percentage-height portals must load after their native viewport has a real size.
        doOnLayout { view.loadUrl(startUrl) }
    }
