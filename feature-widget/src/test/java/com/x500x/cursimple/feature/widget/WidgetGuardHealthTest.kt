package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGuardHealthTest {
    @Test
    fun `all slots present is healthy`() {
        assertEquals(WidgetGuardHealth.Healthy, widgetGuardHealth(registeredSlotCount = 3, expectedSlotCount = 3))
    }

    @Test
    fun `losing part of the chain still lets it heal itself`() {
        assertEquals(WidgetGuardHealth.Degraded, widgetGuardHealth(registeredSlotCount = 1, expectedSlotCount = 3))
        assertEquals(WidgetGuardHealth.Degraded, widgetGuardHealth(registeredSlotCount = 2, expectedSlotCount = 3))
    }

    @Test
    fun `an empty chain is broken because nothing of its own can restart it`() {
        assertEquals(WidgetGuardHealth.Broken, widgetGuardHealth(registeredSlotCount = 0, expectedSlotCount = 3))
    }

    @Test
    fun `more slots than expected is still healthy`() {
        assertEquals(WidgetGuardHealth.Healthy, widgetGuardHealth(registeredSlotCount = 5, expectedSlotCount = 3))
    }

    @Test
    fun `expecting no slots never reports a problem`() {
        assertEquals(WidgetGuardHealth.Healthy, widgetGuardHealth(registeredSlotCount = 0, expectedSlotCount = 0))
    }
}
