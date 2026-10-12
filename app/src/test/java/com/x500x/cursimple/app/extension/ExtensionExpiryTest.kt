package com.x500x.cursimple.app.extension

import com.x500x.cursimple.core.data.event.ScheduleEventRepository
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExtensionExpiryTest {
    private val deadline = 1_791_400_000_000L
    private fun data() = ExtensionData("test", loginState = ExtensionLoginState.LoggedIn,
        host = ExtensionHostSettings(ignoreOverdue = true, addToSchedule = true, dueReminderHours = 0,
            schedule = ExtensionScheduleSettings(includeCompleted = true, historyDays = 0)),
        items = listOf(ExtensionFeedItem("homework", "homework", "Report", dueAt = deadline, kind = "task", done = true),
            ExtensionFeedItem("exam", "exam", "Exam", dueAt = deadline, kind = "task")))

    @Test fun deadlineCleanupIsScheduledEvenWithRemindersDisabled() {
        assertEquals(deadline, ExtensionDueScheduler.nextWakeAt(listOf(data()), deadline - 1000))
        assertNull(ExtensionDueScheduler.nextWakeAt(listOf(data()), deadline))
        val restored = data().withItemIgnored("homework", false).withItemIgnored("exam", false)
        assertNull(ExtensionDueScheduler.nextWakeAt(listOf(restored), deadline - 1000))
    }

    @Test fun passingDeadlineRemovesGeneratedEventsWithoutNetworkAndKeepsManualEvents() = runBlocking {
        val manual = ScheduleEvent("manual", "Manual", "2026-10-01", "12:00", "13:00")
        val values = MutableStateFlow(listOf(manual))
        val repository = object : ScheduleEventRepository {
            override val eventsFlow = values
            override suspend fun upsert(event: ScheduleEvent) { values.value = values.value.filterNot { it.id == event.id } + event }
            override suspend fun remove(eventId: String) { values.value = values.value.filterNot { it.id == eventId } }
        }
        ExtensionScheduleBridge.apply(repository, "test", data(), deadline - 1)
        assertEquals(3, values.value.size)
        ExtensionScheduleBridge.apply(repository, "test", data(), deadline)
        assertEquals(listOf(manual), values.value)
        ExtensionScheduleBridge.apply(repository, "test", data().withItemIgnored("exam", false), deadline)
        assertEquals(setOf("manual", "ext-test-exam"), values.value.map { it.id }.toSet())
    }
}
