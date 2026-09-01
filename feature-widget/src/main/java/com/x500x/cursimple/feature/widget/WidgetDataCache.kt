package com.x500x.cursimple.feature.widget

/** 同一份数据被所有小组件实例共用时的缓存键。 */
internal const val WIDGET_SHARED_CACHE_KEY = 0

/**
 * 一次刷新里头部和列表共用同一份读取结果的短时缓存；超出 [ttlNanos] 或换了缓存键就重新读。
 */
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

    companion object {
        const val DEFAULT_TTL_NANOS: Long = 5_000_000_000L
    }
}
