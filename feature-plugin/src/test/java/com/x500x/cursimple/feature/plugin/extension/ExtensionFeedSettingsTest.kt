package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExtensionFeedSettingsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ms("2026-10-01T12:00")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private val items = listOf(
        ExtensionFeedItem("homework", "homework", "作业", publishAt = ms("2026-09-01T10:00"), dueAt = ms("2026-10-08T23:59")),
        ExtensionFeedItem("exam", "exam", "考试", startAt = ms("2026-10-02T09:00"), dueAt = ms("2026-10-03T11:00"), done = true),
        ExtensionFeedItem("announcement", "announcement", "公告", publishAt = ms("2026-09-28T09:00"), done = true),
        ExtensionFeedItem("notice", "notice", "通知", publishAt = ms("2026-09-30T08:00")),
    )
    private fun data(settings: ExtensionFeedSettings = ExtensionFeedSettings()) = ExtensionData("p", host = ExtensionHostSettings(addToSchedule = true, feed = settings), items = items)

    @Test fun `default feed shows everything without changing saved items`() {
        val before = data()
        assertEquals(items, extensionFeedItems(before, now, zone))
        assertEquals(items, before.items)
    }

    @Test fun `announcements only and empty selection are distinct from all types`() {
        assertEquals(listOf("announcement"), extensionFeedItems(data(ExtensionFeedSettings(includedTypes = setOf("announcement"))), now, zone).map { it.id })
        assertTrue(extensionFeedItems(data(ExtensionFeedSettings(includedTypes = emptySet())), now, zone).isEmpty())
    }

    @Test fun `task completion and read notices can be filtered independently`() {
        val filtered = extensionFeedItems(data(ExtensionFeedSettings(includeCompleted = false, includeReadNotices = true)), now, zone)
        assertEquals(setOf("homework", "announcement", "notice"), filtered.map { it.id }.toSet())
        val unread = extensionFeedItems(data(ExtensionFeedSettings(includeCompleted = true, includeReadNotices = false)), now, zone)
        assertEquals(setOf("homework", "exam", "notice"), unread.map { it.id }.toSet())
    }

    @Test fun `history uses selected display date and future dates survive`() {
        val smart = data(ExtensionFeedSettings(historyDays = 7))
        assertTrue(extensionFeedItems(smart, now, zone).any { it.id == "homework" })
        val byPublish = smart.copy(host = smart.host.copy(feed = smart.host.feed.copy(dateSource = ExtensionScheduleDateSource.Publish)))
        assertFalse(extensionFeedItems(byPublish, now, zone).any { it.id == "homework" })
        assertTrue(extensionFeedItems(byPublish, now, zone).any { it.id == "exam" })
    }

    @Test fun `calendar rule changes never alter event settings or timestamps`() {
        val before = data()
        val changed = before.copy(host = before.host.copy(feed = ExtensionFeedSettings(includedTypes = emptySet(), dateSource = ExtensionScheduleDateSource.Publish)))
        assertTrue(extensionFeedItems(changed, now, zone).isEmpty())
        assertEquals(extensionScheduleItems(before, now, zone), extensionScheduleItems(changed, now, zone))
        assertEquals(before.items, changed.items)
    }

    @Test fun `old preferences default to week while list preference survives persistence`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<ExtensionHostSettings>("{\"showInSidebar\":true}")
        assertEquals(ExtensionFeedView.Month, old.feed.defaultView)
        val saved = old.copy(feed = ExtensionFeedSettings(defaultView = ExtensionFeedView.List, includedTypes = setOf("announcement"), includeReadNotices = false, historyDays = 30))
        assertEquals(saved, json.decodeFromString<ExtensionHostSettings>(json.encodeToString(saved)))
    }

    @Test fun `missing requested date falls back without dropping item`() {
        assertEquals(items[2].publishAt, extensionItemAnchor(items[2], ExtensionScheduleDateSource.Due))
        assertEquals(items[1].startAt, extensionItemAnchor(items[1], ExtensionScheduleDateSource.Automatic))
        val undated = ExtensionFeedItem("x", "homework", "作业", firstSeenAt = now)
        assertEquals(now, extensionItemAnchor(undated, ExtensionScheduleDateSource.Automatic))
    }
}
