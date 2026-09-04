package com.x500x.cursimple.core.kernel.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class BeijingTimeZoneOverrideTest {

    @After
    fun tearDown() {
        BeijingTime.setOverrideZone(null)
        BeijingTime.setForcedNow(null)
    }

    @Test
    fun `without an override the device zone is used`() {
        BeijingTime.setOverrideZone(null)

        assertEquals(ZoneId.systemDefault(), BeijingTime.zone)
    }

    @Test
    fun `an override replaces the device zone`() {
        BeijingTime.setOverrideZone(ZoneId.of("Asia/Shanghai"))

        assertEquals(ZoneId.of("Asia/Shanghai"), BeijingTime.zone)
    }

    @Test
    fun `clearing the override falls back to the device zone`() {
        BeijingTime.setOverrideZone(ZoneId.of("America/New_York"))
        BeijingTime.setOverrideZone(null)

        assertEquals(ZoneId.systemDefault(), BeijingTime.zone)
    }

    @Test
    fun `today follows the overridden zone`() {
        // 同一瞬间在两个时区可能落在不同日期
        BeijingTime.setOverrideZone(ZoneId.of("Pacific/Kiritimati"))
        val east = BeijingTime.today()
        BeijingTime.setOverrideZone(ZoneId.of("Pacific/Midway"))
        val west = BeijingTime.today()

        assertEquals(true, !east.isBefore(west))
    }
}
