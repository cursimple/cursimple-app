package com.x500x.cursimple.core.kernel.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared clock with device or override zone. Forced values are wall time in the target zone;
 * only nowMillis converts them into timestamps.
 */
object BeijingTime {
    val zone: ZoneId
        get() = overrideZone.get() ?: ZoneId.systemDefault()

    private val overrideZone = AtomicReference<ZoneId?>(null)

    fun setOverrideZone(zone: ZoneId?) {
        overrideZone.set(zone)
    }

    private val forcedDateTime = AtomicReference<LocalDateTime?>(null)

    /** Advanced-tools override; null clears it. Process-wide. */
    fun setForcedNow(dateTime: LocalDateTime?) {
        forcedDateTime.set(dateTime)
    }

    fun setForcedToday(date: LocalDate?) {
        forcedDateTime.set(date?.atStartOfDay())
    }

    fun today(): LocalDate = todayIn(zone)

    fun today(zone: ZoneId): LocalDate = todayIn(zone)

    fun todayIn(zone: ZoneId): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now(zone)

    fun nowTimeIn(zone: ZoneId): LocalTime = forcedDateTime.get()?.toLocalTime() ?: LocalTime.now(zone)

    fun nowDateTimeIn(zone: ZoneId): LocalDateTime = forcedDateTime.get() ?: LocalDateTime.now(zone)

    fun nowDateTime(): LocalDateTime = nowDateTimeIn(zone)

    fun nowMillis(zone: ZoneId): Long {
        val forced = forcedDateTime.get() ?: return System.currentTimeMillis()
        return forced.atZone(zone).toInstant().toEpochMilli()
    }

    fun dayOfWeek(zone: ZoneId = BeijingTime.zone): DayOfWeek = todayIn(zone).dayOfWeek
}
