package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ComponentSessionNetworkTest {
    private class Cookies : ComponentSessionCookies {
        private val saved = linkedMapOf<String, String>()
        override suspend fun get(url: String) = saved.entries.joinToString("; ") { "${it.key}=${it.value}" }
        override suspend fun save(url: String, values: List<String>, active: () -> Boolean) {
            check(active()); values.forEach { value ->
                val pair = value.substringBefore(';').split('=', limit = 2)
                saved[pair[0]] = pair.getOrElse(1) { "" }
            }
        }
    }
    private fun fixture(body: suspend (MockWebServer, ComponentSessionNetwork, Cookies) -> Unit) = runBlocking {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer().apply { useHttps(serverCerts.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .followRedirects(false).pingInterval(20, TimeUnit.SECONDS).build()
        val cookies = Cookies()
        val origin = server.url("/").toString().trimEnd('/')
        val network = ComponentSessionNetwork(origin, listOf("localhost"),
            setOf(PluginPermission.NetworkFetch, PluginPermission.WebReadCookies), "ComponentFixture", cookies, client)
        try { body(server, network, cookies) } finally { network.close(); server.shutdown() }
    }
    private fun payload(url: String, id: String = "test", method: String = "GET", body: String = "") = buildJsonObject {
        put("url", url); put("requestId", id); put("method", method); put("body", body)
    }
    @Test fun cookiesArePersistedBeforeRedirectAndBeforeReply() = fixture { server, network, cookies ->
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ready").addHeader("Set-Cookie", "csrftoken=fixture; Path=/"))
        server.enqueue(MockResponse().setBody("ready").addHeader("Set-Cookie", "sessionid=fixture-session; Path=/; HttpOnly"))
        val response = network.fetch(payload(server.url("/login").toString()))
        assertEquals("ready", response.fieldText("body"))
        assertTrue(cookies.get(server.url("/").toString()).contains("sessionid=fixture-session"))
        server.takeRequest(2, TimeUnit.SECONDS)
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Cookie")!!.contains("csrftoken=fixture"))
    }
    @Test fun sessionPostUsesOriginRefererAndBodyWithoutReturningCookieHeaders() = fixture { server, network, cookies ->
        cookies.save(server.url("/").toString(), listOf("csrftoken=fixture")) { true }
        server.enqueue(MockResponse().setResponseCode(204).addHeader("Set-Cookie", "sessionid=fixture-session"))
        val origin = server.url("/").toString().trimEnd('/')
        val response = network.fetch(buildJsonObject {
            put("url", "$origin/session"); put("requestId", "exchange"); put("method", "POST"); put("body", "{\"proof\":\"fixture\"}")
            put("referrer", "$origin/login"); put("headers", buildJsonObject { put("Content-Type", "application/json"); put("X-CSRFToken", "fixture") })
        })
        assertEquals("204", response.fieldText("status"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(origin, request.getHeader("Origin")); assertEquals("$origin/login", request.getHeader("Referer"))
        assertEquals("csrftoken=fixture", request.getHeader("Cookie")); assertEquals("fixture", request.getHeader("X-CSRFToken"))
        assertFalse(response.toString().contains("fixture-session"))
    }
    @Test fun redirectsCannotForwardCredentialsToAnotherHost() = fixture { server, network, _ ->
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", "https://example.invalid/steal"))
        try { network.fetch(payload(server.url("/login").toString(), method = "POST", body = "fixture")); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, server.requestCount)
    }
    @Test fun cancelBeforeDispatchDoesNotSendARequest() = fixture { server, network, _ ->
        network.cancelRequest("cancelled")
        try { network.fetch(payload(server.url("/login").toString(), "cancelled")); fail() }
        catch (_: IllegalStateException) { }
        assertEquals(0, server.requestCount)
    }
    @Test fun socketMessagesAndHandshakeCookiesWaitUntilJavaScriptReadsThem() = fixture { server, network, cookies ->
        server.enqueue(MockResponse().addHeader("Set-Cookie", "socketnonce=fixture").withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("fixture-confirmation") }
        }))
        val id = network.openSocket(server.url("/socket").toString().replace("https:", "wss:"))
        delay(200)
        val events = mutableListOf<JsonObject>()
        withTimeout(5_000) { while (events.none { it.fieldText("type") == "message" }) {
            events += network.pollSocket(id)["events"]!!.jsonArray.map { it.jsonObject }
        } }
        assertEquals("fixture-confirmation", events.first { it.fieldText("type") == "message" }.fieldText("data"))
        assertTrue(cookies.get(server.url("/").toString()).contains("socketnonce=fixture"))
        assertEquals(server.url("/").toString().trimEnd('/'), server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Origin"))
        network.closeSocket(id)
    }
    @Test fun pendingReadResumesOnConfirmationWithoutJavaScriptTimers() = fixture { server, network, _ ->
        val peer = CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { peer.complete(webSocket) }
        }))
        val id = network.openSocket(server.url("/socket").toString().replace("https:", "wss:"))
        peer.await()
        withTimeout(5_000) { while (network.pollSocket(id)["events"]!!.jsonArray.none { it.jsonObject.fieldText("type") == "open" }) { } }
        val message = coroutineScope {
        val waiting = async { withTimeout(5_000) {
            var message: String? = null
            while (message == null) message = network.pollSocket(id)["events"]!!.jsonArray
                .firstOrNull { it.jsonObject.fieldText("type") == "message" }?.jsonObject?.fieldText("data")
            message
        } }
        delay(100); peer.await().send("fixture-confirmation")
        waiting.await()
        }
        assertEquals("fixture-confirmation", message); network.closeSocket(id)
    }
    @Test fun closedPageRejectsNewConnections() = fixture { server, network, _ ->
        network.close()
        try { network.openSocket(server.url("/socket").toString().replace("https:", "wss:")); fail() } catch (_: IllegalArgumentException) { }
        try { network.fetch(payload(server.url("/login").toString())); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }
}
