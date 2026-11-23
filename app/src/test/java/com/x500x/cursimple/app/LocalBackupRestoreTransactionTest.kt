package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBackupRestoreTransactionTest {
    private fun snapshot(name: String) = AppBackupPayload(
        version = AppBackupPayload.CURRENT_VERSION,
        stores = listOf(PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, listOf())),
        createdAt = if (name == "old") 1L else 2L,
    )

    @Test
    fun failedRestoreRollsBackToTheSnapshotTakenBeforeWriting() = runBlocking {
        var current = snapshot("old")
        val incoming = snapshot("new")

        try {
            runBackupRestoreTransaction(
                snapshot = { current },
                apply = { next ->
                    current = next
                    error("synthetic restore failure")
                },
                rollback = { previous -> current = previous },
                incoming = incoming,
            )
            error("expected restore to fail")
        } catch (_: IllegalStateException) {
            // Expected. The snapshot must still be restored below.
        }

        assertEquals(1L, current.createdAt)
    }
}
