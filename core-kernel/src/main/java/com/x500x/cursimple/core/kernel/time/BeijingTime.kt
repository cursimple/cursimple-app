package com.x500x.cursimple.core.kernel.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

object BeijingTime {
    const val DEFAULT_ZONE_ID: String = "Asia/Shanghai"
    val zone: ZoneId = ZoneId.of(DEFAULT_ZONE_ID)

    private val forcedDateTime = AtomicReference<LocalDateTime?>(null)

    /** Developer-mode override; null clears it. Process-wide. */
    fun setForcedNow(dateTime: LocalDateTime?) {
        forcedDateTime.set(dateTime)
    }

    fun setForcedToday(date: LocalDate?) {
        forcedDateTime.set(date?.atStartOfDay())
    }

    fun forcedNow(): LocalDateTime? = forcedDateTime.get()

    fun forcedToday(): LocalDate? = forcedDateTime.get()?.toLocalDate()

    fun today(): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now(zone)

    fun today(zone: ZoneId): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now(zone)

    fun todayIn(zone: ZoneId): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now(zone)

    fun nowTimeIn(zone: ZoneId): LocalTime = forcedDateTime.get()?.toLocalTime() ?: LocalTime.now(zone)

    fun nowDateTimeIn(zone: ZoneId): LocalDateTime = forcedDateTime.get() ?: LocalDateTime.now(zone)

    fun nowMillis(zone: ZoneId): Long {
        val forced = forcedDateTime.get() ?: return System.currentTimeMillis()
        return forced.atZone(zone).toInstant().toEpochMilli()
    }

    fun dayOfWeek(zone: ZoneId = BeijingTime.zone): DayOfWeek = todayIn(zone).dayOfWeek
}
