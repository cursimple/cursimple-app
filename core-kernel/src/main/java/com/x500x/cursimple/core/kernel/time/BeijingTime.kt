package com.x500x.cursimple.core.kernel.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * 应用统一的当前时间来源。
 *
 * 应用时区等于设备时区：[zone] 返回 [ZoneId.systemDefault]，不再固定为某个地区时区。
 * 带 [ZoneId] 参数的函数按传入时区计算，调用方通过传入 [zone] 获得设备本地结果。
 *
 * 开发者模式可以用 [setForcedNow] / [setForcedToday] 覆盖当前时间。被覆盖的值是墙上时钟，
 * 已经处于目标时区，因此覆盖生效时不再按 [ZoneId] 二次换算，只有 [nowMillis] 需要用时区
 * 把墙上时钟还原成时间戳。
 */
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

    fun today(): LocalDate = todayIn(zone)

    fun today(zone: ZoneId): LocalDate = todayIn(zone)

    fun todayIn(zone: ZoneId): LocalDate = forcedDateTime.get()?.toLocalDate() ?: LocalDate.now(zone)

    fun nowTimeIn(zone: ZoneId): LocalTime = forcedDateTime.get()?.toLocalTime() ?: LocalTime.now(zone)

    fun nowDateTimeIn(zone: ZoneId): LocalDateTime = forcedDateTime.get() ?: LocalDateTime.now(zone)

    fun nowMillis(zone: ZoneId): Long {
        val forced = forcedDateTime.get() ?: return System.currentTimeMillis()
        return forced.atZone(zone).toInstant().toEpochMilli()
    }

    fun dayOfWeek(zone: ZoneId = BeijingTime.zone): DayOfWeek = todayIn(zone).dayOfWeek
}
