package com.x500x.cursimple.app.greeting

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalGreetingWindowTest {
    @Test
    fun greetingDeliveryIsLimitedToTheMorning() {
        assertFalse(FestivalGreeting.canDeliverAt(LocalTime.of(7, 59, 59)))
        assertTrue(FestivalGreeting.canDeliverAt(LocalTime.of(8, 0)))
        assertTrue(FestivalGreeting.canDeliverAt(LocalTime.of(11, 59, 59)))
        assertFalse(FestivalGreeting.canDeliverAt(LocalTime.NOON))
        assertFalse(FestivalGreeting.canDeliverAt(LocalTime.of(23, 59)))
    }

    @Test
    fun openingTheAppOnlyCatchesUpBetweenElevenAndNoon() {
        assertFalse(FestivalGreeting.canDeliverAt(LocalTime.of(10, 59, 59), catchUp = true))
        assertTrue(FestivalGreeting.canDeliverAt(LocalTime.of(11, 0), catchUp = true))
        assertTrue(FestivalGreeting.canDeliverAt(LocalTime.of(11, 59, 59), catchUp = true))
        assertFalse(FestivalGreeting.canDeliverAt(LocalTime.NOON, catchUp = true))
    }
}
