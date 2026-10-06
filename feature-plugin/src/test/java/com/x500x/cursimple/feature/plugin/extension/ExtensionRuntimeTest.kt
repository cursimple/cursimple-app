package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSetting
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExtensionRuntimeTest {

    @Test
    fun `payload keeps good items and drops broken ones`() {
        val payload = """
            {"result":{"account":{"id":"1","name":"小明"}},
             "items":[
               {"id":"a","title":"作业一","dueAt":1790700000000,"url":"javascript:alert(1)"},
               {"id":"","title":"没有 id"},
               {"title":"也没有 id"},
               {"id":"a","title":"重复的 id"},
               {"id":"b","title":"公告","type":"announcement","unknownField":true}
             ],
             "state":{"cache":{"x":1}}}
        """.trimIndent()
        val result = parseExtensionPayload(payload, 1_000_000) as ExtensionRunResult.Completed
        assertEquals(listOf("a", "b"), result.items.map { it.id })
        assertEquals("", result.items[0].url)
        assertTrue(result.state.containsKey("cache"))
    }

    @Test
    fun `payload that is too large or not json fails`() {
        assertTrue(parseExtensionPayload("x".repeat(100), 10) is ExtensionRunResult.Failed)
        assertTrue(parseExtensionPayload("[1,2]", 1_000) is ExtensionRunResult.Failed)
        assertTrue(parseExtensionPayload(null, 1_000) is ExtensionRunResult.Failed)
    }

    @Test
    fun `export keywords are stripped so the module can be inlined`() {
        val source = """
            export async function sync(ctx) { return {}; }
            export function checkLogin(ctx) { return {}; }
            export const __test__ = { a: 1 };
            export class Foo {}
            export { sync };
            const exported = "export async function inside a string stays";
        """.trimIndent()
        val out = normalizeExtensionEntrySource(source)
        assertFalse(Regex("""(?m)^\s*export\b""").containsMatchIn(out))
        assertTrue(out.contains("async function sync(ctx)"))
        assertTrue(out.contains("const __test__"))
        assertTrue(out.contains("class Foo"))
    }

    @Test
    fun `url templates only accept host-like values`() {
        val spec = PluginExtensionSpec(
            loginUrl = "https://{settings.site}/web",
            runUrl = "https://{settings.site}/api",
            settings = listOf(
                PluginExtensionSetting(key = "site", type = "select", label = "站点", default = JsonPrimitive("campus.portal.example")),
            ),
        )
        val defaults = ExtensionUrls.effectiveSettings(spec, emptyMap())
        assertEquals("https://campus.portal.example/web", ExtensionUrls.resolve(spec.loginUrl, defaults))
        val picked = ExtensionUrls.effectiveSettings(spec, mapOf("site" to JsonPrimitive("www.portal.example")))
        assertEquals("https://www.portal.example/api", ExtensionUrls.resolve(spec.runUrl, picked))
        val evil = mapOf("site" to JsonPrimitive("evil.com/x?"))
        assertEquals("https:///web", ExtensionUrls.resolve(spec.loginUrl, evil))
    }

    @Test
    fun `allowed hosts match exactly or as subdomain over https`() {
        val hosts = listOf("portal.example")
        assertTrue(ExtensionUrls.isAllowed("https://campus.portal.example/web", hosts))
        assertTrue(ExtensionUrls.isAllowed("https://portal.example/", hosts))
        assertFalse(ExtensionUrls.isAllowed("http://campus.portal.example/", hosts))
        assertFalse(ExtensionUrls.isAllowed("https://portal.example.evil.com/", hosts))
        assertFalse(ExtensionUrls.isAllowed("https://evilportal.example/", hosts))
    }

    @Test
    fun `evaluateJavascript strings are unwrapped`() {
        assertEquals(null, decodeEvaluateJavascriptString("null"))
        assertEquals("{\"kind\":\"ok\"}", decodeEvaluateJavascriptString("\"{\\\"kind\\\":\\\"ok\\\"}\""))
    }

    @Test
    fun `first sync after login builds a baseline without new items`() {
        val before = ExtensionData(pluginId = "p", loginState = ExtensionLoginState.LoggedIn, baselineReady = false)
        val run = ExtensionRunResult.Completed(JsonObject(emptyMap()), listOf(item("a"), item("b")), emptyMap())
        val merged = mergeSyncResult(before, run, now = 1_000L)
        assertTrue(merged.newItems.isEmpty())
        assertTrue(merged.data.baselineReady)
        assertEquals(1_000L, merged.data.items.first().firstSeenAt)
    }

    @Test
    fun `later syncs report only unseen ids and keep firstSeenAt`() {
        val before = ExtensionData(
            pluginId = "p",
            loginState = ExtensionLoginState.LoggedIn,
            baselineReady = true,
            items = listOf(item("a").copy(firstSeenAt = 10L)),
            remindedKeys = setOf("a@100", "gone@5"),
        )
        val run = ExtensionRunResult.Completed(
            JsonObject(mapOf("message" to JsonPrimitive("部分没取到"))),
            listOf(item("a", due = 100L), item("c", due = 200L)),
            emptyMap(),
        )
        val merged = mergeSyncResult(before, run, now = 2_000L)
        assertEquals(listOf("c"), merged.newItems.map { it.id })
        assertEquals(10L, merged.data.items.first { it.id == "a" }.firstSeenAt)
        assertEquals(setOf("a@100"), merged.data.remindedKeys)
        assertEquals("部分没取到", merged.data.lastMessage)
    }

    @Test
    fun `list view groups urgent items first`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 9, 29)
        val noon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        val items = listOf(
            item("overdue", due = noon - hour),
            item("today", due = noon + hour),
            item("week", due = noon + 3 * 24 * hour),
            item("later", due = noon + 30 * 24 * hour),
            item("notice"),
            item("done", due = noon + hour).copy(done = true),
        )
        val groups = groupFeed(items, today, noon, zone).associate { (group, list) -> group to list.map { it.id } }
        assertEquals(listOf("overdue"), groups[FeedGroup.Overdue])
        assertEquals(listOf("today"), groups[FeedGroup.Today])
        assertEquals(listOf("week"), groups[FeedGroup.Week])
        assertEquals(listOf("later"), groups[FeedGroup.Later])
        assertEquals(listOf("notice"), groups[FeedGroup.Notices])
        assertEquals(listOf("done"), groups[FeedGroup.Done])
    }

    private fun item(id: String, due: Long? = null) = ExtensionFeedItem(id = id, title = id, dueAt = due)
}
