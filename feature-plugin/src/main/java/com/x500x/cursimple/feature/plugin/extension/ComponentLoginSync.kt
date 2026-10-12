package com.x500x.cursimple.feature.plugin.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One application-owned sync per saved session, even when its login page disappears. */
class ComponentLoginSync(private val scope: CoroutineScope) {
    private data class Entry(val session: String, val job: Job)
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun start(owner: String, session: String, work: suspend () -> Unit) {
        if (entries[owner]?.session == session) return
        entries.remove(owner)?.job?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) { work() }
        entries[owner] = Entry(session, job)
        job.invokeOnCompletion { failure ->
            if (failure != null) synchronized(this) {
                if (entries[owner]?.job === job) entries.remove(owner)
            }
        }
        job.start()
    }
}
