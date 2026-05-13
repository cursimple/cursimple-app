package com.x500x.cursimple.core.kernel.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

object BeijingTime {
    val zone: ZoneId
        get() = ZoneId.systemDefault()

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

    fun today(): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now()

    @Suppress("UNUSED_PARAMETER")
    fun today(zone: ZoneId): LocalDate = today()

    @Suppress("UNUSED_PARAMETER")
    fun todayIn(zone: ZoneId): LocalDate = today()

    @Suppress("UNUSED_PARAMETER")
    fun nowTimeIn(zone: ZoneId): LocalTime = forcedDateTime.get()?.toLocalTime() ?: LocalTime.now()

    @Suppress("UNUSED_PARAMETER")
    fun nowDateTimeIn(zone: ZoneId): LocalDateTime = forcedDateTime.get() ?: LocalDateTime.now()

    @Suppress("UNUSED_PARAMETER")
    fun nowMillis(zone: ZoneId): Long {
        val forced = forcedDateTime.get() ?: return System.currentTimeMillis()
        return forced.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Suppress("UNUSED_PARAMETER")
    fun dayOfWeek(zone: ZoneId = BeijingTime.zone): DayOfWeek = today().dayOfWeek
}
