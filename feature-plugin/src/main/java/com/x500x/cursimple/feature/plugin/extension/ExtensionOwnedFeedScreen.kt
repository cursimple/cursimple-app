package com.x500x.cursimple.feature.plugin.extension

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/** 组件自带 UI 的通用容器；页面、排版和详情均来自组件包。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ExtensionOwnedFeedScreen(
    record: InstalledPluginRecord,
    data: ExtensionData,
    uiSource: String,
    actions: ExtensionHostActions,
    onOpenSettings: () -> Unit,
    onUiFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember(record.installKey) { mutableStateOf(false) }
    var pageReady by remember(record.installKey) { mutableStateOf(false) }
    val webView = remember(record.installKey) {
        ExtensionWebViewPool.acquire(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) onUiFailure()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) onUiFailure()
                    return true
                }
            }
        }
    }
    val bridge = remember(record.installKey) {
        ExtensionUiBridge(
            onSync = {
                if (!syncing) {
                    scope.launch {
                        syncing = true
                        runCatching { actions.syncNow(record) }
                        syncing = false
                    }
                }
            },
            onOpenSettings = onOpenSettings,
        )
    }
    DisposableEffect(webView, bridge) {
        webView.addJavascriptInterface(bridge, "CurSimpleExtensionUi")
        webView.loadDataWithBaseURL("https://cursimple-extension.invalid/", uiSource, "text/html", "UTF-8", null)
        onDispose {
            webView.removeJavascriptInterface("CurSimpleExtensionUi")
            ExtensionWebViewPool.release(webView)
        }
    }
    LaunchedEffect(data, syncing, pageReady, webView) {
        if (!pageReady) return@LaunchedEffect
        val json = extensionJson.encodeToString(ExtensionData.serializer(), data)
        webView.evaluateJavascript("window.CurSimpleExtensionUiSetData && window.CurSimpleExtensionUiSetData($json);", null)
        webView.evaluateJavascript("window.CurSimpleExtensionUiSetSyncing && window.CurSimpleExtensionUiSetSyncing($syncing);", null)
    }
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (syncing) CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
    }
}

private class ExtensionUiBridge(
    private val onSync: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    @JavascriptInterface fun sync() = onSync()
    @JavascriptInterface fun openSettings() = onOpenSettings()
}
