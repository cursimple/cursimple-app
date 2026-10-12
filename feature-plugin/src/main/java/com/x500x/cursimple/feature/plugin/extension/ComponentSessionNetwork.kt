package com.x500x.cursimple.feature.plugin.extension

import android.webkit.CookieManager
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface ComponentSessionCookies {
    suspend fun get(url: String): String
    suspend fun save(url: String, values: List<String>, active: () -> Boolean)
}

private object WebViewSessionCookies : ComponentSessionCookies {
    override suspend fun get(url: String): String = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(url).orEmpty()
    }
    override suspend fun save(url: String, values: List<String>, active: () -> Boolean) = withContext(Dispatchers.Main) {
        if (values.isEmpty()) return@withContext
        val manager = CookieManager.getInstance()
        try {
            withTimeout(10_000) {
                values.forEach { value ->
                    suspendCancellableCoroutine<Unit> { continuation ->
                        check(active()) { "请求已取消" }
                        manager.setCookie(url, value) { accepted ->
                            if (continuation.isActive) {
                                if (accepted) continuation.resume(Unit)
                                else continuation.resumeWithException(IOException("无法保存登录会话，请重试"))
                            }
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IOException("登录会话保存超时，请重试")
        }
        manager.flush()
    }
}

/** Generic page-owned session transport. Protocol messages stay in the component. */
internal class ComponentSessionNetwork(
    private val origin: String,
    private val allowedHosts: List<String>,
    private val permissions: Set<PluginPermission>,
    private val userAgent: String?,
    private val cookies: ComponentSessionCookies = WebViewSessionCookies,
    private val client: OkHttpClient = defaultClient,
) {
    private val closed = AtomicBoolean(false)
    private val calls = ConcurrentHashMap<String, Call>()
    private val cancelledRequests = ConcurrentHashMap.newKeySet<String>()
    private val sockets = ConcurrentHashMap<String, SocketState>()

    private fun checked(url: String, socket: Boolean = false): URI {
        require(!closed.get()) { "页面网络会话已关闭" }
        require(PluginPermission.NetworkFetch in permissions) { "组件未声明网络权限" }
        val uri = URI(url)
        require(uri.scheme == if (socket) "wss" else "https") { "连接必须使用加密传输" }
        require(uri.rawUserInfo == null && uri.fragment == null) { "连接地址无效" }
        val https = if (socket) "https:" + url.substringAfter(':') else url
        require(ExtensionUrls.isAllowed(https, allowedHosts)) { "连接地址不在组件声明的站点内" }
        return uri
    }

    private fun sameOrigin(uri: URI): Boolean {
        val base = URI(origin)
        fun port(u: URI) = if (u.port == -1) 443 else u.port
        return uri.host.equals(base.host, ignoreCase = true) && port(uri) == port(base)
    }

    /** Follow same-origin redirects and await CookieManager before replying to JavaScript. */
    suspend fun fetch(payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        require(PluginPermission.WebReadCookies in permissions) { "组件未声明登录会话权限" }
        val id = payload.fieldText("requestId")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "请求标识无效" }
        var url = payload.fieldText("url")
        var method = payload.fieldText("method").ifBlank { "GET" }.uppercase()
        require(method in setOf("GET", "POST")) { "不支持此请求方法" }
        val body = payload.fieldText("body")
        require(body.toByteArray().size <= 64 * 1024) { "请求正文过大" }
        val headers = payload["headers"] as? JsonObject ?: JsonObject(emptyMap())
        require(headers.size <= 40) { "请求头过多" }
        val timeout = payload["timeoutMs"]?.jsonPrimitive?.longOrNull?.coerceIn(1_000, 60_000) ?: 20_000
        val requestClient = client.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS).build()
        try {
            repeat(6) {
                check(id !in cancelledRequests) { "请求已取消" }
                val uri = checked(url)
                require(sameOrigin(uri)) { "登录请求不能跨越当前站点" }
                val builder = Request.Builder().url(url).header("Origin", origin)
                userAgent?.let { builder.header("User-Agent", it) }
                headers.forEach { (name, value) ->
                    require(name.lowercase() !in setOf("cookie", "host", "connection", "content-length", "origin", "user-agent", "referer")) { "请求头不支持" }
                    val text = value.jsonPrimitive.content
                    require(text.length <= 8192) { "请求头过长" }
                    builder.header(name, text)
                }
                val referrer = payload.fieldText("referrer").ifBlank { "$origin/" }
                require(sameOrigin(checked(referrer))) { "请求来源无效" }
                builder.header("Referer", referrer)
                cookies.get(url).takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
                if (method == "POST") {
                    val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.jsonPrimitive?.content
                        ?: "application/json; charset=utf-8"
                    builder.post(body.toRequestBody(contentType.toMediaType()))
                }
                val call = requestClient.newCall(builder.build())
                require(calls.putIfAbsent(id, call) == null) { "请求已经在处理" }
                try {
                    if (id in cancelledRequests || closed.get()) call.cancel()
                    call.execute().use { response ->
                        check(!closed.get() && calls[id] === call && !call.isCanceled()) { "请求已取消" }
                        cookies.save(url, response.headers.values("Set-Cookie")) {
                            !closed.get() && calls[id] === call && !call.isCanceled() && id !in cancelledRequests
                        }
                        check(!closed.get() && calls[id] === call && !call.isCanceled()) { "请求已取消" }
                        if (response.code in setOf(301, 302, 303, 307, 308) && response.header("Location") != null) {
                            url = uri.resolve(response.header("Location")).toString()
                            if (response.code == 303 || (response.code in setOf(301, 302) && method == "POST")) method = "GET"
                        } else {
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            response.body.byteStream().use { input ->
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(output.size() + count <= 1024 * 1024) { "平台响应过大" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            return@withContext buildJsonObject {
                                put("status", response.code); put("body", output.toString(Charsets.UTF_8.name()))
                                put("contentType", response.header("Content-Type").orEmpty())
                            }
                        }
                    }
                } finally { calls.remove(id, call) }
            }
            error("页面重定向次数过多")
        } catch (error: IOException) {
            throw IOException("登录网络请求中断或超时，请重试", error)
        } finally { cancelledRequests.remove(id) }
    }

    fun cancelRequest(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "请求标识无效" }
        require(cancelledRequests.size < 1024) { "取消请求过多" }
        cancelledRequests.add(id)
        calls.remove(id)?.cancel()
    }

    suspend fun openSocket(url: String): String {
        val uri = checked(url, socket = true)
        val id = UUID.randomUUID().toString()
        val state = SocketState()
        val request = Request.Builder().url(url).header("Origin", origin)
        userAgent?.let { request.header("User-Agent", it) }
        if (sameOrigin(uri) && PluginPermission.WebReadCookies in permissions) {
            cookies.get("https:" + url.substringAfter(':')).takeIf(String::isNotBlank)?.let { request.header("Cookie", it) }
        }
        check(!closed.get()) { "页面网络会话已关闭" }
        synchronized(sockets) {
            require(sockets.size < 3) { "连接数量已达上限" }
            sockets[id] = state
        }
        state.socket = client.newWebSocket(request.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get() || state.ended) { webSocket.cancel(); return }
                if (sameOrigin(uri) && PluginPermission.WebReadCookies in permissions) {
                    // OkHttp's reader starts after onOpen: persist handshake cookies first.
                    val stored = runCatching { runBlocking {
                        cookies.save("https:" + url.substringAfter(':'), response.headers.values("Set-Cookie")) {
                            !closed.get() && !state.ended
                        }
                    } }.isSuccess
                    if (!stored) { state.end("error"); webSocket.cancel(); return }
                }
                state.add(buildJsonObject { put("type", "open") })
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.toByteArray().size > 128 * 1024 || !state.add(buildJsonObject { put("type", "message"); put("data", text) })) {
                    state.end("error"); webSocket.cancel()
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(if (code == 1005) 1000 else code, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { state.end("close", code) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { state.end("error") }
        })
        state.expiry = expiryExecutor.schedule({ state.end("error"); state.socket?.cancel() }, 5, TimeUnit.MINUTES)
        return id
    }

    fun sendSocket(id: String, text: String) {
        require(text.toByteArray().size <= 128 * 1024) { "连接消息过大" }
        val state = sockets[id] ?: error("连接已关闭")
        check(!state.ended && state.socket?.send(text) == true) { "连接暂时不可用" }
    }

    suspend fun pollSocket(id: String): JsonObject {
        val state = sockets[id] ?: return buildJsonObject { put("events", buildJsonArray { add(buildJsonObject { put("type", "close") }) }) }
        val events = state.awaitEvents()
        return buildJsonObject { put("events", events) }
    }

    fun closeSocket(id: String) {
        sockets.remove(id)?.let { it.end("close"); it.socket?.cancel(); it.expiry?.cancel(false); it.drain() }
    }

    fun closeConnections() {
        calls.keys.toList().forEach(::cancelRequest)
        sockets.keys.toList().forEach(::closeSocket)
    }

    fun close() { closed.set(true); closeConnections() }

    private class SocketState {
        var socket: WebSocket? = null
        var expiry: java.util.concurrent.ScheduledFuture<*>? = null
        @Volatile var ended = false
        private val events = ArrayDeque<JsonObject>()
        private var bytes = 0
        private val changed = Channel<Unit>(Channel.CONFLATED)
        @Synchronized fun add(event: JsonObject): Boolean {
            if (ended) return false
            val size = event.toString().toByteArray().size
            if (bytes + size > 512 * 1024 || events.size >= 64) return false
            events.add(event); bytes += size
            changed.trySend(Unit)
            return true
        }
        @Synchronized fun end(type: String, code: Int = 1006) {
            if (ended) return
            ended = true
            events.add(buildJsonObject { put("type", type); put("code", code) })
            changed.trySend(Unit)
        }
        @Synchronized fun drain(): JsonArray = JsonArray(events.toList()).also { events.clear(); bytes = 0 }
        suspend fun awaitEvents(): JsonArray {
            val available = drain()
            if (available.isNotEmpty()) return available
            withTimeoutOrNull(25_000) { changed.receive() }
            return drain()
        }
    }

    private companion object {
        val defaultClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS).build()
        val expiryExecutor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "component-session-expiry").apply { isDaemon = true } }
    }
}
