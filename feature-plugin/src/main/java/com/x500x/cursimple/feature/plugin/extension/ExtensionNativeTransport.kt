package com.x500x.cursimple.feature.plugin.extension

import android.util.Base64
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ExtensionNativeTransport {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun fetch(request: ExtensionRunRequest, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        require(PluginPermission.NetworkProxy.id in request.permissions) { "组件未声明宿主网络传输权限" }
        val url = payload.fieldText("url")
        require(ExtensionUrls.isAllowed(url, request.allowedHosts)) { "请求地址不属于已绑定目标" }
        val uri = URI(url)
        require(uri.rawUserInfo == null && uri.fragment == null) { "请求地址无效" }
        val method = payload.fieldText("method").ifBlank { "GET" }.uppercase()
        require(method in setOf("GET", "POST")) { "不支持此请求方法" }
        val body = payload.fieldText("body")
        require(body.toByteArray().size <= 64 * 1024) { "请求正文过大" }
        val builder = Request.Builder().url(url)
        (payload["headers"] as? JsonObject)?.forEach { (name, value) ->
            require(name.lowercase() !in setOf("host", "cookie", "content-length", "connection")) { "请求头不支持" }
            builder.header(name, (value as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        if (method == "POST") {
            val contentType = (payload["headers"] as? JsonObject)?.entries?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "application/json; charset=utf-8"
            builder.post(body.toRequestBody(contentType.toMediaType()))
        }
        try {
            val timeout = (payload["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.coerceIn(1_000, 40_000) ?: 20_000
            client.newBuilder().readTimeout(timeout, TimeUnit.MILLISECONDS).callTimeout(timeout, TimeUnit.MILLISECONDS)
                .build().newCall(builder.build()).execute().use { response ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val input = response.body.byteStream()
                while (output.size() <= 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= 1024 * 1024) { "平台响应过大" }
                buildJsonObject { put("status", response.code); put("ok", response.isSuccessful); put("body", String(bytes, Charsets.UTF_8)) }
            }
        } catch (error: java.io.IOException) {
            buildJsonObject { put("ok", false); put("status", 0); put("body", ""); put("ambiguous", true); put("error", "网络结果未确认，请在目标平台核对后重试") }
        }
    }

    fun hmac(payload: JsonObject): JsonPrimitive {
        val key = payload.fieldText("key")
        require(key.isNotEmpty() && key.length <= 4096) { "签名密钥无效" }
        val message = payload.fieldText("message")
        require(message.length <= 64 * 1024)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return JsonPrimitive(Base64.encodeToString(mac.doFinal(message.toByteArray()), Base64.NO_WRAP))
    }
}

internal fun JsonObject.fieldText(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
