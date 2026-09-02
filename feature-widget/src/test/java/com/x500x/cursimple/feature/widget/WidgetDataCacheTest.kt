package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class WidgetDataCacheTest {
    private val ttlNanos = 5_000_000_000L

    @Test
    fun `list reuses the day the header just loaded`() {
        val cache = WidgetDataCache<ScheduleWidgetDayData>(ttlNanos)
        val day = dayData(offset = 0)

        cache.put(key = 7, nowNanos = 1_000L, value = day)

        assertSame(day, cache.get(key = 7, nowNanos = 1_000L + ttlNanos - 1))
    }

    @Test
    fun `an expired entry is loaded again`() {
        val cache = WidgetDataCache<ScheduleWidgetDayData>(ttlNanos)
        cache.put(key = 7, nowNanos = 1_000L, value = dayData(offset = 0))

        assertNull(cache.get(key = 7, nowNanos = 1_000L + ttlNanos))
    }

    @Test
    fun `another widget does not read the entry`() {
        val cache = WidgetDataCache<ScheduleWidgetDayData>(ttlNanos)
        cache.put(key = 7, nowNanos = 1_000L, value = dayData(offset = 0))

        assertNull(cache.get(key = 8, nowNanos = 1_000L))
    }

    @Test
    fun `a new day replaces the previous entry`() {
        val cache = WidgetDataCache<ScheduleWidgetDayData>(ttlNanos)
        cache.put(key = 7, nowNanos = 1_000L, value = dayData(offset = 0))
        val switched = dayData(offset = 1)
        cache.put(key = 7, nowNanos = 2_000L, value = switched)

        assertSame(switched, cache.get(key = 7, nowNanos = 2_000L))
    }

    @Test
    fun `widgets sharing one snapshot read the same entry`() {
        val cache = WidgetDataCache<ScheduleWidgetDayData>(ttlNanos)
        val data = dayData(offset = 0)

        cache.put(WIDGET_SHARED_CACHE_KEY, nowNanos = 1_000L, value = data)

        assertSame(data, cache.get(WIDGET_SHARED_CACHE_KEY, nowNanos = 1_500L))
        assertNull(cache.get(WIDGET_SHARED_CACHE_KEY, nowNanos = 1_000L + ttlNanos))
    }

    private fun dayData(offset: Int): ScheduleWidgetDayData {
        val date = LocalDate.of(2026, 9, 21).plusDays(offset.toLong())
        return ScheduleWidgetDayData(
            offset = offset,
            manualOffset = 0,
            targetDate = date,
            sourceDate = date,
            rows = emptyList(),
        )
    }
}
