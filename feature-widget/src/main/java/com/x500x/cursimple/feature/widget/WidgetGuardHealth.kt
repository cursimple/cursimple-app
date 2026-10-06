package com.x500x.cursimple.feature.widget

enum class WidgetGuardHealth {
    Healthy,

    Degraded,

    /** No registered slots remain; external events are required to restart the chain. */
    Broken,
}

/** Any surviving slot can rebuild the chain; zero slots means no self-trigger remains. */
fun widgetGuardHealth(registeredSlotCount: Int, expectedSlotCount: Int): WidgetGuardHealth = when {
    expectedSlotCount <= 0 -> WidgetGuardHealth.Healthy
    registeredSlotCount <= 0 -> WidgetGuardHealth.Broken
    registeredSlotCount >= expectedSlotCount -> WidgetGuardHealth.Healthy
    else -> WidgetGuardHealth.Degraded
}
