package com.x500x.cursimple.feature.plugin.extension

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ComponentLoginSyncTest {
    @Test fun navigationCannotCancelLoginSyncAndDuplicateConfirmationDoesNotRepeatIt() = runBlocking {
        val appJob = SupervisorJob()
        val pageJob = Job()
        val application = CoroutineScope(appJob + Dispatchers.Unconfined)
        val sync = ComponentLoginSync(application)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        var count = 0
        try {
            CoroutineScope(pageJob + Dispatchers.Unconfined).launch {
                sync.start("sample", "install:1") { count++; started.complete(Unit); finish.await(); completed.complete(Unit) }
            }
            started.await(); pageJob.cancel()
            sync.start("sample", "install:1") { count++ }
            finish.complete(Unit); completed.await()
            sync.start("sample", "install:1") { count++ }
            assertEquals(1, count)
            sync.start("sample", "install:2") { count++ }
            assertEquals(2, count)
        } finally { appJob.cancel(); pageJob.cancel() }
    }

    @Test fun newAccountCancelsTheOldSessionAndSyncsIndependentComponents() = runBlocking {
        val parent = SupervisorJob()
        val sync = ComponentLoginSync(CoroutineScope(parent + Dispatchers.Unconfined))
        val stopped = CompletableDeferred<Unit>()
        var calls = 0
        try {
            sync.start("sample", "old") { try { awaitCancellation() } finally { stopped.complete(Unit) } }
            sync.start("sample", "new") { calls++ }
            stopped.await()
            sync.start("other", "new") { calls++ }
            assertEquals(2, calls)
        } finally { parent.cancel() }
    }
}
