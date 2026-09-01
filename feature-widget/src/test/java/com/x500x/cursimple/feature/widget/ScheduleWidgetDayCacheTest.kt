package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class ScheduleWidgetDayCacheTest {
    private val ttlNanos = 5_000_000_000L

    @Test
    fun `list reuses the day the header just loaded`() {
        val cache = ScheduleWidgetDayCache(ttlNanos)
        val day = dayData(offset = 0)

        cache.put(appWidgetId = 7, nowNanos = 1_000L, data = day)

        assertSame(day, cache.get(appWidgetId = 7, nowNanos = 1_000L + ttlNanos - 1))
    }

    @Test
    fun `an expired entry is loaded again`() {
        val cache = ScheduleWidgetDayCache(ttlNanos)
        cache.put(appWidgetId = 7, nowNanos = 1_000L, data = dayData(offset = 0))

        assertNull(cache.get(appWidgetId = 7, nowNanos = 1_000L + ttlNanos))
    }

    @Test
    fun `another widget does not read the entry`() {
        val cache = ScheduleWidgetDayCache(ttlNanos)
        cache.put(appWidgetId = 7, nowNanos = 1_000L, data = dayData(offset = 0))

        assertNull(cache.get(appWidgetId = 8, nowNanos = 1_000L))
    }

    @Test
    fun `a new day replaces the previous entry`() {
        val cache = ScheduleWidgetDayCache(ttlNanos)
        cache.put(appWidgetId = 7, nowNanos = 1_000L, data = dayData(offset = 0))
        val switched = dayData(offset = 1)
        cache.put(appWidgetId = 7, nowNanos = 2_000L, data = switched)

        assertSame(switched, cache.get(appWidgetId = 7, nowNanos = 2_000L))
    }

    private fun dayData(offset: Int): ScheduleWidgetDayData {
        val date = LocalDate.of(2026, 9, 21).plusDays(offset.toLong())
        return ScheduleWidgetDayData(
            offset = offset,
            manualOffset = 0,
            targetDate = date,
            weekdayLabel = "星期一",
            sourceDate = date,
            rows = emptyList(),
        )
    }
}
