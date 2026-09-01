package com.x500x.cursimple.core.kernel.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日期选择器用所选日期的 UTC 零点表示选中值，与设备时区无关。
 * 两个方向都必须固定按 UTC 换算，否则负偏移时区会把日期整体退回一天。
 */
fun LocalDate.toDatePickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
