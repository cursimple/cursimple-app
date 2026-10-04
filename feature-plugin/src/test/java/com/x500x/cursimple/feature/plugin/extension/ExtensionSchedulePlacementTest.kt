package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ExtensionSchedulePlacementTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ms("2026-10-01T12:00")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun item(type: String = "homework", done: Boolean = false) = ExtensionFeedItem(
        id = type, type = type, title = type, done = done,
        publishAt = ms("2026-09-28T16:00"), dueAt = if (type == "homework") ms("2026-10-08T23:59") else null,
    )
    private fun placed(item: ExtensionFeedItem, config: ExtensionScheduleSettings = ExtensionScheduleSettings()) = placeExtensionItem(item, config, zone)!!
    private fun data(items: List<ExtensionFeedItem>, config: ExtensionScheduleSettings = ExtensionScheduleSettings()) = ExtensionData("test", host = ExtensionHostSettings(addToSchedule = true, schedule = config), items = items)

    @Test fun `announcement is on publication date even after being read`() {
        val pair = extensionScheduleItems(data(listOf(item("announcement", done = true))), now, zone).single()
        assertEquals(LocalDate.of(2026, 9, 28), pair.second.date)
        assertEquals(LocalTime.of(9, 0), pair.second.startTime)
        assertEquals(LocalTime.of(9, 30), pair.second.endTime)
    }

    @Test fun `default homework uses deadline and configured duration before it`() {
        val place = placed(item())
        assertEquals(LocalDate.of(2026, 10, 8), place.date)
        assertEquals(LocalTime.of(23, 29), place.startTime)
        assertEquals(LocalTime.of(23, 59), place.endTime)
    }

    @Test fun `exam prefers start date and same day actual window`() {
        val exam = item("exam").copy(startAt = ms("2026-10-10T09:00"), dueAt = ms("2026-10-10T11:00"))
        assertEquals(ExtensionSchedulePlacement(LocalDate.of(2026, 10, 10), LocalTime.of(9, 0), LocalTime.of(11, 0)), placed(exam))
    }

    @Test fun `cross day exam stays on start date without an invalid overnight block`() {
        val exam = item("exam").copy(startAt = ms("2026-10-10T22:00"), dueAt = ms("2026-10-11T01:00"))
        val place = placed(exam)
        assertEquals(LocalDate.of(2026, 10, 10), place.date)
        assertEquals(LocalTime.of(22, 30), place.endTime)
    }

    @Test fun `midnight deadline never changes selected day`() {
        val place = placed(item().copy(dueAt = ms("2026-10-09T00:00")))
        assertEquals(LocalDate.of(2026, 10, 9), place.date)
        assertEquals(LocalTime.MIDNIGHT, place.startTime)
        assertEquals(LocalTime.of(0, 30), place.endTime)
    }

    @Test fun `early deadline is clamped to same day`() {
        val place = placed(item().copy(dueAt = ms("2026-10-09T00:05")))
        assertEquals(LocalTime.MIDNIGHT, place.startTime)
        assertEquals(LocalTime.of(0, 5), place.endTime)
    }

    @Test fun `publication choice and negative offset use fixed time`() {
        val rule = ExtensionScheduleTypeRule(ExtensionScheduleDateSource.Publish, -1, ExtensionScheduleTimeMode.Fixed)
        val config = ExtensionScheduleSettings(typeRules = mapOf("homework" to rule), defaultStartTime = "14:15", durationMinutes = 45)
        val place = placed(item(), config)
        assertEquals(LocalDate.of(2026, 9, 27), place.date)
        assertEquals(LocalTime.of(14, 15), place.startTime)
        assertEquals(LocalTime.of(15, 0), place.endTime)
    }

    @Test fun `type selections can include only announcements or nothing`() {
        val items = listOf(item(), item("announcement"), item("exam"), item("notice"))
        assertEquals(listOf("announcement"), extensionScheduleItems(data(items, ExtensionScheduleSettings(includedTypes = setOf("announcement"))), now, zone).map { it.first.id })
        assertTrue(extensionScheduleItems(data(items, ExtensionScheduleSettings(includedTypes = emptySet())), now, zone).isEmpty())
    }

    @Test fun `completed tasks and read notices have independent policies`() {
        val items = listOf(item(done = true), item("announcement", done = true))
        assertEquals(listOf("announcement"), extensionScheduleItems(data(items), now, zone).map { it.first.id })
        val config = ExtensionScheduleSettings(includeCompleted = true, includeReadNotices = false)
        assertEquals(listOf("homework"), extensionScheduleItems(data(items, config), now, zone).map { it.first.id })
    }

    @Test fun `history removes old dates and never removes future events`() {
        val items = listOf(item(), item("announcement").copy(publishAt = ms("2026-08-01T12:00")))
        assertEquals(listOf("homework"), extensionScheduleItems(data(items), now, zone).map { it.first.id })
        assertEquals(2, extensionScheduleItems(data(items, ExtensionScheduleSettings(historyDays = 0)), now, zone).size)
    }

    @Test fun `missing chosen date falls back to available item date then first sync`() {
        val rule = ExtensionScheduleTypeRule(dateSource = ExtensionScheduleDateSource.Due)
        val config = ExtensionScheduleSettings(typeRules = mapOf("notice" to rule))
        assertEquals(LocalDate.of(2026, 9, 28), placed(item("notice"), config).date)
        val undated = ExtensionFeedItem("undated", type = "notice", title = "undated", firstSeenAt = now)
        assertEquals(LocalDate.of(2026, 10, 1), placed(undated, config).date)
        assertNull(placeExtensionItem(undated.copy(firstSeenAt = 0), config, zone))
    }

    @Test fun `invalid default clock and late times still make valid intervals`() {
        assertEquals(LocalTime.of(9, 0), placed(item("announcement"), ExtensionScheduleSettings(defaultStartTime = "broken")).startTime)
        val late = placed(item("announcement"), ExtensionScheduleSettings(defaultStartTime = "23:59"))
        assertTrue(late.endTime > late.startTime)
        assertEquals(LocalTime.of(23, 59), late.endTime)
    }

    @Test fun `old stored settings acquire defaults and new settings survive round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<ExtensionHostSettings>("{\"addToSchedule\":true}")
        assertTrue(old.schedule.includes("announcement"))
        val rule = ExtensionScheduleTypeRule(ExtensionScheduleDateSource.Due, -2, ExtensionScheduleTimeMode.Fixed)
        val saved = old.copy(schedule = ExtensionScheduleSettings(includedTypes = setOf("homework"), typeRules = mapOf("homework" to rule)))
        assertEquals(saved, json.decodeFromString<ExtensionHostSettings>(json.encodeToString(saved)))
    }

    @Test fun `main switch off returns no placements`() {
        val disabled = data(listOf(item())).let { it.copy(host = it.host.copy(addToSchedule = false)) }
        assertTrue(extensionScheduleItems(disabled, now, zone).isEmpty())
    }

    @Test fun `task without deadline remains a task for completion policy`() {
        val undatedHomework = item(done = true).copy(dueAt = null, kind = "task")
        assertFalse(undatedHomework.isNotice())
        assertTrue(extensionScheduleItems(data(listOf(undatedHomework)), now, zone).isEmpty())
        assertEquals(1, extensionScheduleItems(data(listOf(undatedHomework), ExtensionScheduleSettings(includeCompleted = true)), now, zone).size)
    }

    // 下面几条钉住「宿主不认类型名」：换个组件用别的 id，语义照样认得出来。

    @Test fun `declared task role wins even when the item has no deadline`() {
        val undatedTask = ExtensionFeedItem(
            id = "a", type = "zuoye", title = "作业", kind = "task", done = true,
            publishAt = ms("2026-09-28T16:00"), dueAt = null,
        )
        assertFalse(undatedTask.isNotice())
        // 已完成的任务按任务策略隐藏；当成公告的话会因为它「读过了」而留下
        assertTrue(extensionScheduleItems(data(listOf(undatedTask)), now, zone).isEmpty())
    }

    @Test fun `declared notice role is honoured even with a deadline present`() {
        val custom = item(type = "gonggao").copy(kind = "notice", dueAt = ms("2026-10-20T23:59"))
        assertTrue(custom.isNotice())
        assertEquals(LocalDate.of(2026, 9, 28), placed(custom).date)
    }

    @Test fun `items without a declared role are inferred from their times`() {
        // 老条目没有 kind：宿主不认类型名，只看有没有开始或截止时间；下次同步时会盖上清单声明的角色
        val withDeadline = item(type = "announcement", done = true).copy(dueAt = ms("2026-10-20T23:59"))
        assertFalse(withDeadline.isNotice())
        assertEquals(LocalDate.of(2026, 10, 20), placed(withDeadline).date)
        val publishOnly = item(type = "homework").copy(dueAt = null)
        assertTrue(publishOnly.isNotice())
        assertEquals(LocalDate.of(2026, 9, 28), placed(publishOnly).date)
    }

    @Test fun `manifest roles are applied to items that do not carry one`() {
        val manifest = extensionJson.decodeFromString(
            PluginManifest.serializer(),
            """
            {"id":"t","name":"t","version":"1.0.0","versionCode":1,"entry":"main.js","apiVersion":4,
             "extension":{"title":"t","loginUrl":"https://a.example/web","runUrl":"https://a.example/api",
             "feedTypes":[{"id":"zuoye","label":"作业","kind":"task"},{"id":"gonggao","label":"公告","kind":"notice"}]}}
            """.trimIndent(),
        )
        val items = applyDeclaredKinds(
            listOf(item(type = "zuoye").copy(dueAt = null), item(type = "gonggao").copy(dueAt = ms("2026-10-20T23:59"))),
            manifest,
        )
        assertFalse(items[0].isNotice())
        assertTrue(items[1].isNotice())
    }

    @Test fun `settings grouping follows declared roles not hardcoded ids`() {
        val types = listOf(
            PluginFeedTypeSpec("zuoye", "作业", kind = "task"),
            PluginFeedTypeSpec("gonggao", "公告", kind = "notice"),
        )
        assertEquals(setOf("zuoye"), typesByKind(types, "task"))
        assertEquals(setOf("gonggao"), typesByKind(types, "notice"))
    }
}
