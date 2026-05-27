package com.x500x.cursimple.core.plugin.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory ring buffer of recent plugin log entries. Reachable from anywhere via the singleton
 * [PluginLogBuffer.instance]. The in-app diagnostics viewer subscribes to [snapshots].
 */
class PluginLogBuffer(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lock = Any()
    private val entries = ArrayDeque<PluginLogEntry>(capacity)
    private val nextSequence = AtomicLong(0L)
    private val _snapshots = MutableStateFlow<List<PluginLogEntry>>(emptyList())

    val snapshots: StateFlow<List<PluginLogEntry>> = _snapshots.asStateFlow()

    fun add(entry: PluginLogEntry) {
        synchronized(lock) {
            if (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
            _snapshots.value = entries.toList()
        }
    }

    fun nextSequence(): Long = nextSequence.incrementAndGet()

    fun snapshot(): List<PluginLogEntry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            _snapshots.value = emptyList()
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 2000
        val instance: PluginLogBuffer by lazy { PluginLogBuffer() }
    }
}
