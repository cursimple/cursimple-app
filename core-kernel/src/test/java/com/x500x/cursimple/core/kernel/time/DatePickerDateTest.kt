package com.x500x.cursimple.core.kernel.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class DatePickerDateTest {

    private lateinit var systemZone: TimeZone

    @Before
    fun captureZone() {
        systemZone = TimeZone.getDefault()
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(systemZone)
    }

    /** 独立于被测代码算出选择器给出的毫秒数：所选日期的 UTC 零点。 */
    private fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `picked day survives a default zone behind utc`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val picked = LocalDate.of(2026, 9, 7)
        assertEquals(picked, datePickerMillisToLocalDate(pickerMillis(picked)))
        assertEquals(pickerMillis(picked), picked.toDatePickerMillis())
    }

    @Test
    fun `picked day survives a default zone ahead of utc`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val picked = LocalDate.of(2026, 9, 7)
        assertEquals(picked, datePickerMillisToLocalDate(pickerMillis(picked)))
        assertEquals(pickerMillis(picked), picked.toDatePickerMillis())
    }

    @Test
    fun `conversion ignores the default zone entirely`() {
        val picked = LocalDate.of(2026, 9, 7)
        val expected = pickerMillis(picked)
        for (zoneId in listOf("America/Los_Angeles", "UTC", "Asia/Shanghai", "Pacific/Kiritimati")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            assertEquals(zoneId, expected, picked.toDatePickerMillis())
            assertEquals(zoneId, picked, datePickerMillisToLocalDate(expected))
        }
    }

    @Test
    fun `every day of a term start week keeps its own date`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        var date = LocalDate.of(2026, 8, 31)
        repeat(14) {
            assertEquals(date, datePickerMillisToLocalDate(pickerMillis(date)))
            date = date.plusDays(1)
        }
    }
}
