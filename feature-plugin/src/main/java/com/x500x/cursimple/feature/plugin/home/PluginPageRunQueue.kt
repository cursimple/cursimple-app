package com.x500x.cursimple.feature.plugin.home

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes page-script execution without rejecting concurrent UI refresh requests. */
internal class PluginPageRunQueue {
    private val lock = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = lock.withLock { block() }
}
