package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Runs a multi-store restore with a full snapshot available for rollback on partial failure. */
internal suspend fun <T> runBackupRestoreTransaction(
    snapshot: suspend () -> AppBackupPayload,
    apply: suspend (AppBackupPayload) -> T,
    rollback: suspend (AppBackupPayload) -> Unit,
    incoming: AppBackupPayload,
): T {
    val previous = snapshot()
    return try {
        apply(incoming)
    } catch (failure: Throwable) {
        runCatching {
            withContext(NonCancellable) { rollback(previous) }
        }.onFailure(failure::addSuppressed)
        throw failure
    }
}
