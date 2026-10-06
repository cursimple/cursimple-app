package com.x500x.cursimple.app.extension

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NotificationRuntimeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun secureBindingsAreEncryptedAndBoundToTheirComponent() = runBlocking {
        val id = "notification-crypto-fixture"
        val other = "notification-crypto-other"
        val secure = ExtensionSecureConfig(context)
        val prefs = context.getSharedPreferences(ExtensionSecureConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val configuration = ExtensionSecureConfiguration(buildJsonObject { put("secret", "fixture-only-secret") }, listOf("notification.fixture.invalid"))
        try {
            secure.save(id, configuration)
            val encrypted = requireNotNull(prefs.getString(id, null))
            assertFalse(encrypted.contains("fixture-only-secret"))
            assertEquals(configuration, secure.read(id))
            prefs.edit().putString(other, encrypted).commit()
            var rejected = false
            try { secure.read(other) } catch (_: IllegalStateException) { rejected = true }
            assertTrue("其他组件不能使用复制来的密文", rejected)
        } finally { secure.remove(id); secure.remove(other) }
    }
    private fun request(source: String, callback: suspend (JsonObject) -> Boolean) = ExtensionRunRequest(
        pluginId = "notification-fixture", mode = ExtensionRunMode.DeliverNotifications,
        url = "https://cursimple-extension.invalid/", entrySource = source,
        allowedHosts = listOf("cursimple-extension.invalid", "notification.fixture.invalid"),
        permissions = setOf("notification.receive", "network.proxy"), userAgent = null,
        settings = JsonObject(emptyMap()), state = JsonObject(emptyMap()), timeoutMs = 20_000, maxOutputBytes = 64 * 1024,
        isolated = true, secureConfiguration = buildJsonObject { put("token", "fixture-only") },
        notifications = JsonArray(listOf(buildJsonObject { put("id", "message-1") })), onReceipt = callback,
    )

    @Test fun isolatedRuntimeTransportsAndPersistsReceipts() = runBlocking {
        val receipts = mutableListOf<String>()
        var calls = 0
        val source = """
            export async function deliverNotifications(ctx) {
              const id = ctx.notifications[0].id;
              const sign = await ctx.crypto.hmacSha256('fixture-key', 'message');
              const allowed = await ctx.notification.receipt(id, 'target-1', 'sending', '');
              if (allowed === false) return {stopped:true};
              const response = await ctx.network.fetch('https://notification.fixture.invalid/send', {
                method:'POST',headers:{Authorization:'Bearer '+ctx.secureConfiguration.token},
                body:JSON.stringify({id,sign})
              });
              const result = await response.json();
              await ctx.notification.receipt(id, 'target-1', result.ok ? 'sent':'failed', '');
              return {confirmed: result.ok};
            }
        """.trimIndent()
        val runtime = ExtensionRuntime(context) { _, payload ->
            calls++
            assertEquals("POST", payload["method"]!!.jsonPrimitive.content)
            assertEquals("Bearer fixture-only", payload["headers"]!!.jsonObject["Authorization"]!!.jsonPrimitive.content)
            assertTrue(payload["body"]!!.jsonPrimitive.content.contains("sign"))
            buildJsonObject { put("ok", true); put("status", 200); put("body", "{\"ok\":true}") }
        }
        val result = runtime.run(request(source) { receipts += it["status"]!!.jsonPrimitive.content; true })
        assertTrue(result.toString(), result is ExtensionRunResult.Completed)
        assertEquals(1, calls)
        assertEquals(listOf("sending", "sent"), receipts)
        assertEquals(true, (result as ExtensionRunResult.Completed).result["confirmed"]!!.jsonPrimitive.boolean)
    }

    @Test fun changedOrPausedBindingStopsBeforeNetwork() = runBlocking {
        var calls = 0
        val source = """
            export async function deliverNotifications(ctx) {
              if (await ctx.notification.receipt('message-1','target-1','sending','') === false) return {stopped:true};
              await ctx.network.fetch('https://notification.fixture.invalid/send', {method:'POST',body:'{}'});
              return {};
            }
        """.trimIndent()
        val result = ExtensionRuntime(context) { _, _ -> calls++; buildJsonObject { put("ok", true) } }
            .run(request(source) { false })
        assertEquals(0, calls)
        assertTrue(result is ExtensionRunResult.Completed)
        assertEquals(true, (result as ExtensionRunResult.Completed).result["stopped"]!!.jsonPrimitive.boolean)
    }

    @Test fun undeclaredDestinationIsRejectedBeforeAnyNetworkRequest() = runBlocking {
        val receipts = mutableListOf<String>()
        val source = """
            export async function deliverNotifications(ctx) {
              try {
                await ctx.network.fetch('https://undeclared.fixture.invalid/send',{method:'POST',body:'{}'});
              } catch (error) { await ctx.notification.receipt('message-1','target-1','failed',error.message); }
              return {};
            }
        """.trimIndent()
        val result = ExtensionRuntime(context).run(request(source) { receipts += it["status"]!!.jsonPrimitive.content; true })
        assertTrue(result is ExtensionRunResult.Completed)
        assertEquals(listOf("failed"), receipts)
    }

    @Test fun encryptedSessionCallbackCanStopBeforeDelivery() = runBlocking {
        var sessionCalls = 0
        var networkCalls = 0
        val source = """
            export async function deliverNotifications(ctx) {
              if (ctx.secureSessions['target-1'].cursor !== 'old-cursor') throw new Error('missing session');
              const saved = await ctx.notification.session('target-1', {cursor:'new-cursor',token:'fixture-only'});
              if (saved === false) return {stopped:true};
              await ctx.network.fetch('https://notification.fixture.invalid/send',{method:'POST',body:'{}'});
              return {};
            }
        """.trimIndent()
        val run = request(source) { true }.copy(
            permissions = setOf("notification.receive", "network.proxy", "storage.secure"),
            secureSessions = buildJsonObject { put("target-1", buildJsonObject { put("cursor", "old-cursor") }) },
            onSession = {
                sessionCalls++
                assertEquals("target-1", it["targetId"]!!.jsonPrimitive.content)
                assertEquals("new-cursor", it["value"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
                false
            },
        )
        val result = ExtensionRuntime(context) { _, _ -> networkCalls++; buildJsonObject { put("ok", true) } }.run(run)
        assertEquals(1, sessionCalls)
        assertEquals(0, networkCalls)
        assertTrue(result.toString(), result is ExtensionRunResult.Completed)
        assertEquals(true, (result as ExtensionRunResult.Completed).result["stopped"]!!.jsonPrimitive.boolean)
    }

    @Test fun mailBridgeRejectsUndeclaredServerBeforeConnecting() = runBlocking {
        val source = """
            export async function deliverNotifications(ctx) {
              try {
                await ctx.mail.send({host:'undeclared.fixture.invalid',port:465,username:'sender@example.com',password:'fixture',from:'sender@example.com',to:'receiver@example.com',subject:'Fixture',text:'Fixture'});
                return {rejected:false};
              } catch(error) { return {rejected:error.message.includes('已绑定')}; }
            }
        """.trimIndent()
        val result = ExtensionRuntime(context).run(request(source) { true })
        assertTrue(result.toString(), result is ExtensionRunResult.Completed)
        assertEquals(true, (result as ExtensionRunResult.Completed).result["rejected"]!!.jsonPrimitive.boolean)
    }

    @Test fun queuedDeliveryReportsSubmissionAndLaterQuerySeparately() = runBlocking {
        val receipts = mutableListOf<String>()
        var persisted = JsonObject(emptyMap())
        val source = """
            export async function deliverNotifications(ctx) {
              await ctx.notification.session('target-1',{reference:'fixture-receipt'});
              await ctx.notification.receipt('message-1','target-1','queued','等待平台结果');
              await ctx.notification.receipt('message-1','target-1','querying','');
              await ctx.notification.receipt('message-1','target-1','accepted','平台已接收');
              return {};
            }
        """.trimIndent()
        val run = request(source) { receipts += it["status"]!!.jsonPrimitive.content; true }.copy(
            permissions = setOf("notification.receive", "storage.secure"),
            onSession = { persisted = it["value"]!!.jsonObject; true },
        )
        val result = ExtensionRuntime(context).run(run)
        assertTrue(result.toString(), result is ExtensionRunResult.Completed)
        assertEquals("fixture-receipt", persisted["reference"]!!.jsonPrimitive.content)
        assertEquals(listOf("queued", "querying", "accepted"), receipts)
        assertFalse(receipts.contains("sent"))
    }
}
