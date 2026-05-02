package com.kebiao.viewer.core.kernel.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

object BeijingTime {
    const val DEFAULT_ZONE_ID: String = "Asia/Shanghai"
    val zone: ZoneId = ZoneId.of(DEFAULT_ZONE_ID)

    fun today(): LocalDate = LocalDate.now(zone)

    fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)

    fun dayOfWeek(zone: ZoneId = BeijingTime.zone): DayOfWeek = LocalDate.now(zone).dayOfWeek

    fun resolveZone(timeZoneId: String?): ZoneId =
        timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
}
