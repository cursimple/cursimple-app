package com.x500x.cursimple.core.kernel.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Date-picker values encode UTC midnight in both directions, independent of device offset. */
fun LocalDate.toDatePickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
