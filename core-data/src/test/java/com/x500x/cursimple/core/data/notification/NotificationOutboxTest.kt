package com.x500x.cursimple.core.data.notification

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificationOutboxTest {
    @get:Rule val folder = TemporaryFolder()
    private fun queue() = NotificationOutbox(File(folder.root, "outbox.json"))
    private fun event() = OutboundNotification("id", "class", "Class", "Room A", "schedule", "Schedule", expiresAt = System.currentTimeMillis() + 3_600_000)
    private val targets = listOf(NotificationTarget("one", "One"), NotificationTarget("two", "Two"))

    @Test fun `event is admitted once per receiver and each successful target survives reload`() = runBlocking {
        val q = queue(); val e = event()
        assertTrue(q.enqueue("receiver", e, targets)); assertFalse(q.enqueue("receiver", e, targets))
        q.receipt("receiver", e.id, NotificationReceipt("one", "sent"))
        q.receipt("receiver", e.id, NotificationReceipt("two", "failed", "HTTP 429"))
        val persisted = queue().list("receiver").single()
        assertEquals(setOf("two"), persisted.pendingTargets(System.currentTimeMillis()))
        assertTrue(q.enqueue("other", e, targets))
    }

    @Test fun `paused targets and source cancellation prevent future sends`() = runBlocking {
        val q = queue(); val e = event()
        q.enqueue("receiver", e, targets)
        q.receipt("receiver", e.id, NotificationReceipt("one", "sent"))
        q.retainTargets("receiver", setOf("one"))
        val row = q.list().single()
        assertTrue(row.pendingTargets(System.currentTimeMillis()).isEmpty())
        assertEquals("sent", row.receipts.first { it.targetId == "one" }.status)
        assertEquals("skipped", row.receipts.first { it.targetId == "two" }.status)
        q.cancelSource("schedule")
        assertNull(q.begin("receiver", e.id, System.currentTimeMillis()))
    }

    @Test fun `unknown outcome needs manual retry which preserves acknowledged recipients`() = runBlocking {
        val q = queue(); val e = event()
        q.enqueue("receiver", e, targets)
        q.receipt("receiver", e.id, NotificationReceipt("one", "sent"))
        q.receipt("receiver", e.id, NotificationReceipt("two", "unknown"))
        assertTrue(q.list().single().pendingTargets(System.currentTimeMillis()).isEmpty())
        q.retry("receiver", e.id)
        val row = q.list().single()
        assertTrue(row.manualRetry)
        assertEquals(setOf("two"), row.pendingTargets(System.currentTimeMillis()))
    }

    @Test fun `filters expiry and leases stop duplicate concurrent batches`() = runBlocking {
        val q = queue(); val e = event()
        assertFalse(q.enqueue("receiver", e, listOf(NotificationTarget("one", "One", kinds = setOf("memo.due")))))
        q.enqueue("receiver", e, targets)
        val now = System.currentTimeMillis()
        assertNotNull(q.begin("receiver", e.id, now)); assertNull(q.begin("receiver", e.id, now))
        assertTrue(q.list().single().pendingTargets(e.expiresAt).isEmpty())
    }

    @Test fun `interrupted sending is recovered as unknown instead of automatically sent twice`() = runBlocking {
        val q = queue(); val e = event()
        q.enqueue("receiver", e, targets)
        q.receipt("receiver", e.id, NotificationReceipt("one", "sending"))
        val restored = queue()
        restored.recoverUnconfirmed("receiver")
        assertEquals("unknown", restored.list().single().receipts.single().status)
        assertEquals(setOf("two"), restored.list().single().pendingTargets(System.currentTimeMillis()))
    }

    @Test fun `accepted targets are not resent while queued targets keep querying beyond send retry limits`() = runBlocking {
        val q = queue(); val e = event(); q.enqueue("receiver", e, targets)
        q.receipt("receiver", e.id, NotificationReceipt("one", "accepted"))
        q.receipt("receiver", e.id, NotificationReceipt("two", "queued"))
        val now = System.currentTimeMillis()
        val started = requireNotNull(q.begin("receiver", e.id, now))
        assertEquals(0, started.attempts)
        assertEquals(setOf("two"), started.copy(attempts = 6).pendingTargets(now))
        q.retry("receiver", e.id)
        val retried = q.list().single()
        assertEquals("accepted", retried.receipts.first { it.targetId == "one" }.status)
        assertEquals(setOf("two"), retried.pendingTargets(now))
    }

    @Test fun `interrupted result lookup resumes as queued without resending an accepted message`() = runBlocking {
        val q = queue(); val e = event(); q.enqueue("receiver", e, targets)
        q.receipt("receiver", e.id, NotificationReceipt("one", "accepted"))
        q.receipt("receiver", e.id, NotificationReceipt("two", "querying"))
        val restored = queue(); restored.recoverUnconfirmed("receiver")
        val row = restored.list().single()
        assertEquals("queued", row.receipts.first { it.targetId == "two" }.status)
        assertEquals(setOf("two"), row.pendingTargets(System.currentTimeMillis()))
        q.cancelSource("schedule")
        assertTrue(q.list().single().pendingTargets(System.currentTimeMillis()).isEmpty())
    }
}
