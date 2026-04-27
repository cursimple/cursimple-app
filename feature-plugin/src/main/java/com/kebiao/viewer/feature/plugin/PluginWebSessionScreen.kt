package com.kebiao.viewer.feature.plugin

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
            ) {
                Button(onClick = onCancel) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val webView = webViewState.value ?: return@Button
                        val cookies = CookieManager.getInstance()
                            .getCookie(currentUrl.value)
                            .orEmpty()
                            .split(";")
                            .mapNotNull {
                                val pair = it.trim().split("=", limit = 2)
                                if (pair.size == 2) pair[0] to pair[1] else null
                            }
                            .toMap()
                        webView.evaluateJavascript(
                            """
                            (() => {
                                const local = {};
                                const session = {};
                                for (let i = 0; i < localStorage.length; i++) {
                                  const key = localStorage.key(i);
                                  local[key] = localStorage.getItem(key) || "";
                                }
                                for (let i = 0; i < sessionStorage.length; i++) {
                                  const key = sessionStorage.key(i);
                                  session[key] = sessionStorage.getItem(key) || "";
                                }
                                const fields = {};
                                ${request.captureSelectors.joinToString("\n") { selector ->
                                    """const node_${selector.hashCode().toString().replace("-", "_")} = document.querySelector(${selector.quoteJs()}); if (node_${selector.hashCode().toString().replace("-", "_")} ) { fields[${selector.quoteJs()}] = (node_${selector.hashCode().toString().replace("-", "_")}.value || node_${selector.hashCode().toString().replace("-", "_")}.textContent || "").trim(); }"""
                                }}
                                return JSON.stringify({
                                  html: document.documentElement.outerHTML,
                                  localStorageSnapshot: local,
                                  sessionStorageSnapshot: session,
                                  capturedFields: fields
                                });
                            })();
                            """.trimIndent(),
                        ) { raw ->
                            val payload = decodeJavascriptPayload(raw)
                            onFinish(
                                WebSessionPacket(
                                    finalUrl = currentUrl.value,
                                    cookies = cookies,
                                    localStorageSnapshot = payload.optJSONObject("localStorageSnapshot").toStringMap(),
                                    sessionStorageSnapshot = payload.optJSONObject("sessionStorageSnapshot").toStringMap(),
                                    htmlDigest = sha256(payload.optString("html", "")),
                                    capturedFields = payload.optJSONObject("capturedFields").toStringMap(),
                                    timestamp = OffsetDateTime.now().toString(),
                                ),
                            )
                        }
                    },
                ) {
                    Text("完成并回传数据包")
                }
            }
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                val target = url.orEmpty()
                                if (!isAllowedHost(target, request.allowedHosts)) {
                                    return true
                                }
                                currentUrl.value = target
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                currentUrl.value = url.orEmpty()
                            }
                        }
                        loadUrl(request.startUrl)
                        webViewState.value = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun isAllowedHost(url: String, allowedHosts: List<String>): Boolean {
    return runCatching { java.net.URL(url).host.lowercase() }.getOrNull()?.let { host ->
        allowedHosts.any { allowed ->
            host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}")
        }
    } ?: false
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

private fun decodeJavascriptPayload(raw: String?): JSONObject {
    val normalized = raw.orEmpty()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
    return JSONObject(normalized.ifBlank { "{}" })
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
