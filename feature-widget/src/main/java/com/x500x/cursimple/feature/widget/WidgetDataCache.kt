package com.x500x.cursimple.feature.widget

internal const val WIDGET_SHARED_CACHE_KEY = 0

/** Share reads during refresh until [ttlNanos] expires or the key changes. */
internal class WidgetDataCache<T : Any>(private val ttlNanos: Long = DEFAULT_TTL_NANOS) {
    private class Entry<T>(
        val key: Int,
        val atNanos: Long,
        val value: T,
    )

    @Volatile
    private var entry: Entry<T>? = null

    fun get(key: Int, nowNanos: Long): T? {
        val current = entry ?: return null
        if (current.key != key) return null
        val age = nowNanos - current.atNanos
        return if (age in 0 until ttlNanos) current.value else null
    }

    fun put(key: Int, nowNanos: Long, value: T) {
        entry = Entry(key, nowNanos, value)
    }

    /** Invalidate immediately when source data changes. */
    fun clear() {
        entry = null
    }

    companion object {
        const val DEFAULT_TTL_NANOS: Long = 5_000_000_000L
    }
}
