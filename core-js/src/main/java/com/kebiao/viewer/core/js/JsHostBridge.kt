package com.kebiao.viewer.core.js

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface JsHostBridge {
    fun httpRequest(requestJson: String): String
    fun log(message: String)
    fun nowIso(): String
}

@Serializable
data class JsHttpRequest(
    @SerialName("method") val method: String = "GET",
    @SerialName("url") val url: String,
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("body") val body: String? = null,
    @SerialName("contentType") val contentType: String? = null,
    @SerialName("timeoutMs") val timeoutMs: Long? = null,
)

@Serializable
data class JsHttpResponse(
    @SerialName("status") val status: Int,
    @SerialName("headers") val headers: Map<String, String>,
    @SerialName("body") val body: String,
)

class DefaultJsHostBridge(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : JsHostBridge {

    private val baseClient = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .build()

    override fun httpRequest(requestJson: String): String {
        val payload = json.decodeFromString<JsHttpRequest>(requestJson)
        val method = payload.method.uppercase()
        val timeoutMs = payload.timeoutMs ?: DEFAULT_TIMEOUT_MS

        val client = baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(payload.url)
        payload.headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        if (method == "GET" || method == "HEAD") {
            requestBuilder.method(method, null)
        } else {
            val mediaType = (payload.contentType ?: "application/json; charset=utf-8").toMediaType()
            requestBuilder.method(method, (payload.body ?: "").toRequestBody(mediaType))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseHeaders = buildMap {
                response.headers.names().forEach { header ->
                    put(header, response.header(header).orEmpty())
                }
            }
            val body = response.body.string()
            return json.encodeToString(
                JsHttpResponse(
                    status = response.code,
                    headers = responseHeaders,
                    body = body,
                ),
            )
        }
    }

    override fun log(message: String) {
        Log.i(TAG, message)
    }

    override fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val current = cookieStore[key].orEmpty().toMutableList()
            val merged = (current + cookies)
                .distinctBy { "${it.name}@${it.domain}:${it.path}" }
                .toMutableList()
            cookieStore[key] = merged
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val key = url.host
            val now = System.currentTimeMillis()
            val validCookies = cookieStore[key]
                .orEmpty()
                .filterNot { it.expiresAt < now }
            cookieStore[key] = validCookies.toMutableList()
            return validCookies
        }
    }

    private companion object {
        const val TAG = "JsHostBridge"
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
