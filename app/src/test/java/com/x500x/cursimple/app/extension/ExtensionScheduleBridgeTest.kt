package com.x500x.cursimple.app.extension

import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem
import com.x500x.cursimple.feature.plugin.extension.ExtensionHostSettings
import com.x500x.cursimple.feature.plugin.extension.ExtensionLoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExtensionScheduleBridgeTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun useFixtureTimeZone() {
        com.x500x.cursimple.core.kernel.time.BeijingTime.setOverrideZone(zone)
    }

    @After fun restoreDeviceTimeZone() {
        com.x500x.cursimple.core.kernel.time.BeijingTime.setOverrideZone(null)
    }

    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `announcement preserves full content and uses manifest color`() {
        val item = ExtensionFeedItem("announcement:101:3", type = "announcement", title = "调课通知", course = "数据结构", publishAt = ms("2026-09-28T16:00"), done = true, content = "周五改到教学楼 A201", summary = "摘要")
        val data = ExtensionData("yuketang-notice", host = ExtensionHostSettings(addToSchedule = true), items = listOf(item))
        val types = listOf(com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec("announcement", "公告", "#D97706"))
        val event = ExtensionScheduleBridge.desiredEvents("yuketang-notice", data, ms("2026-10-01T12:00"), zone, types).single()
        assertEquals("2026-09-28", event.date)
        assertEquals("09:00", event.startTime)
        assertEquals("公告：调课通知", event.title)
        assertEquals(0xFFD97706L, event.colorArgb)
        assertEquals(item.content, event.note)
        assertTrue(event.isValid)
    }

    @Test
    fun `reapplying edited selections removes stale generated events and preserves manual ones`() = kotlinx.coroutines.runBlocking {
        val state = kotlinx.coroutines.flow.MutableStateFlow(listOf(com.x500x.cursimple.core.kernel.model.ScheduleEvent("manual", "手动安排", "2026-10-01", "10:00", "11:00")))
        val repository = object : com.x500x.cursimple.core.data.event.ScheduleEventRepository {
            override val eventsFlow = state
            override suspend fun upsert(event: com.x500x.cursimple.core.kernel.model.ScheduleEvent) { state.value = state.value.filterNot { it.id == event.id } + event }
            override suspend fun remove(eventId: String) { state.value = state.value.filterNot { it.id == eventId } }
        }
        val data = ExtensionData("p", host = ExtensionHostSettings(addToSchedule = true), items = listOf(
            ExtensionFeedItem("hw", "homework", "作业", dueAt = ms("2026-10-08T23:59")),
            ExtensionFeedItem("ann", "announcement", "公告", publishAt = ms("2026-10-01T10:00")),
        ))
        val now = ms("2026-10-01T12:00")
        ExtensionScheduleBridge.apply(repository, "p", data, now)
        ExtensionScheduleBridge.apply(repository, "p", data, now)
        assertEquals(3, state.value.size)
        val filtered = data.copy(host = data.host.copy(schedule = data.host.schedule.copy(includedTypes = setOf("announcement"))))
        ExtensionScheduleBridge.apply(repository, "p", filtered, now)
        assertEquals(setOf("manual", "ext-p-ann"), state.value.map { it.id }.toSet())
        val shifted = filtered.copy(host = filtered.host.copy(schedule = filtered.host.schedule.copy(typeRules = mapOf("announcement" to com.x500x.cursimple.feature.plugin.extension.ExtensionScheduleTypeRule(dayOffset = 1)))))
        ExtensionScheduleBridge.apply(repository, "p", shifted, now)
        assertEquals("2026-10-02", state.value.first { it.id == "ext-p-ann" }.date)
        ExtensionScheduleBridge.apply(repository, "p", null, now)
        assertEquals(listOf("manual"), state.value.map { it.id })
    }

    @Test
    fun `due window picks items inside the reminder window only once`() {
        val now = ms("2026-10-08T12:00")
        val due = ms("2026-10-08T23:59")
        val base = ExtensionData(
            pluginId = "p",
            loginState = ExtensionLoginState.LoggedIn,
            host = ExtensionHostSettings(dueReminderHours = 24),
            items = listOf(
                ExtensionFeedItem(id = "a", title = "a", dueAt = due),
                ExtensionFeedItem(id = "b", title = "b", dueAt = due + 3 * 24 * 3_600_000L),
                ExtensionFeedItem(id = "c", title = "c", dueAt = due, done = true),
            ),
        )
        assertEquals(listOf("a"), ExtensionCoordinator.dueWindowItems(base, now).map { it.id })
        val reminded = base.copy(remindedKeys = setOf("a@$due"))
        assertTrue(ExtensionCoordinator.dueWindowItems(reminded, now).isEmpty())
    }
    @Test
    fun `event source opens content even when sidebar filters hide it`() {
        val item = ExtensionFeedItem("announcement:1", "announcement", "公告", publishAt = ms("2026-10-01T09:00"))
        val data = ExtensionData("p", host = ExtensionHostSettings(addToSchedule = true, showInSidebar = false, feed = com.x500x.cursimple.feature.plugin.extension.ExtensionFeedSettings(includedTypes = emptySet())), items = listOf(item))
        val event = ExtensionScheduleBridge.desiredEvents("p", data, ms("2026-10-01T12:00"), zone).single()
        assertEquals(com.x500x.cursimple.core.kernel.model.ScheduleEventSource("p", item.id), event.source)
        assertEquals(item, ExtensionScheduleBridge.findSource(event, data))
        assertEquals(item, ExtensionScheduleBridge.findSource(event.copy(source = null), data))
        assertEquals(null, ExtensionScheduleBridge.findSource(event.copy(id = "manual", source = null), data))
        assertEquals(null, ExtensionScheduleBridge.findSource(event.copy(source = com.x500x.cursimple.core.kernel.model.ScheduleEventSource("other", item.id)), data))
        assertEquals(null, ExtensionScheduleBridge.findSource(event, data.copy(items = emptyList())))
    }

    @Test
    fun `source metadata remains optional in older backups`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<com.x500x.cursimple.core.kernel.model.ScheduleEvent>("{\"id\":\"manual\",\"title\":\"事务\",\"date\":\"2026-10-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}")
        assertEquals(null, old.source)
        val linked = old.copy(source = com.x500x.cursimple.core.kernel.model.ScheduleEventSource("p", "notice:1"))
        assertEquals(linked, json.decodeFromString<com.x500x.cursimple.core.kernel.model.ScheduleEvent>(json.encodeToString(com.x500x.cursimple.core.kernel.model.ScheduleEvent.serializer(), linked)))
    }

    @Test
    fun `cleanup respects explicit component ownership even when prefixes overlap`() = kotlinx.coroutines.runBlocking {
        val other = com.x500x.cursimple.core.kernel.model.ScheduleEvent("ext-p-other-notice", "其他组件公告", "2026-10-01", "09:00", "09:30", source = com.x500x.cursimple.core.kernel.model.ScheduleEventSource("p-other", "notice"))
        val state = kotlinx.coroutines.flow.MutableStateFlow(listOf(other))
        val repository = object : com.x500x.cursimple.core.data.event.ScheduleEventRepository {
            override val eventsFlow = state
            override suspend fun upsert(event: com.x500x.cursimple.core.kernel.model.ScheduleEvent) { state.value = state.value.filterNot { it.id == event.id } + event }
            override suspend fun remove(eventId: String) { state.value = state.value.filterNot { it.id == eventId } }
        }
        ExtensionScheduleBridge.apply(repository, "p", null, ms("2026-10-01T12:00"))
        assertEquals(listOf(other), state.value)
    }

}
