package com.x500x.cursimple.core.kernel.time

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeijingTimeTest {
    private val farEast = ZoneId.of("Pacific/Kiritimati")
    private val farWest = ZoneId.of("Pacific/Niue")

    @After
    fun clearForcedTime() {
        BeijingTime.setForcedNow(null)
    }

    @Test
    fun `app zone follows the device zone`() {
        assertEquals(ZoneId.systemDefault(), BeijingTime.zone)
    }

    @Test
    fun `todayIn resolves the date of the supplied zone`() {
        val east = BeijingTime.todayIn(farEast)
        val west = BeijingTime.todayIn(farWest)

        assertNotEquals(east, west)
        assertTrue(east.isAfter(west))
    }

    @Test
    fun `today with explicit zone matches todayIn`() {
        assertEquals(BeijingTime.todayIn(farEast), BeijingTime.today(farEast))
        assertEquals(BeijingTime.todayIn(BeijingTime.zone), BeijingTime.today())
    }

    @Test
    fun `dayOfWeek follows the supplied zone`() {
        assertEquals(BeijingTime.todayIn(farEast).dayOfWeek, BeijingTime.dayOfWeek(farEast))
        assertNotEquals(BeijingTime.dayOfWeek(farEast), BeijingTime.dayOfWeek(farWest))
    }

    @Test
    fun `nowTimeIn separates zones by their offset`() {
        val east = BeijingTime.nowTimeIn(farEast)
        val west = BeijingTime.nowTimeIn(farWest)

        assertTrue(east.hour != west.hour)
    }

    @Test
    fun `forced wall clock overrides every zone`() {
        val forced = LocalDateTime.of(2026, 5, 6, 7, 30)
        BeijingTime.setForcedNow(forced)

        assertEquals(LocalDate.of(2026, 5, 6), BeijingTime.todayIn(farEast))
        assertEquals(LocalDate.of(2026, 5, 6), BeijingTime.todayIn(farWest))
        assertEquals(forced.toLocalTime(), BeijingTime.nowTimeIn(farWest))
        assertEquals(forced.dayOfWeek, BeijingTime.dayOfWeek(farWest))
    }

    @Test
    fun `forced nowMillis converts the wall clock with the supplied zone`() {
        val forced = LocalDateTime.of(2026, 5, 6, 7, 30)
        BeijingTime.setForcedNow(forced)

        val east = BeijingTime.nowMillis(farEast)
        val west = BeijingTime.nowMillis(farWest)

        assertEquals(forced.atZone(farEast).toInstant().toEpochMilli(), east)
        assertEquals(Duration.ofHours(25).toMillis(), west - east)
    }
}
