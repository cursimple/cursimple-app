package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentWidgetSizeTest {
    @Test
    fun usesTheActualMinimumBoundsWhenAllBoundsAreAvailable() {
        assertEquals(ComponentWidgetSize(240, 110), componentWidgetSize(240, 420, 110, 260))
    }

    @Test
    fun fallsBackToMaximumBoundsWhenMinimumBoundsAreMissing() {
        assertEquals(ComponentWidgetSize(420, 260), componentWidgetSize(0, 420, 0, 260))
    }

    @Test
    fun keepsEachLauncherSizeAsASeparateRenderTarget() {
        assertEquals(
            listOf(ComponentWidgetSize(180, 110), ComponentWidgetSize(330, 148)),
            componentWidgetSizes(listOf(
                ComponentWidgetSize(180, 110),
                ComponentWidgetSize(330, 148),
                ComponentWidgetSize(180, 110),
            ), ComponentWidgetSize(330, 230)),
        )
    }

    @Test
    fun fallsBackWhenTheLauncherAdvertisesNoUsableSize() {
        assertEquals(
            listOf(ComponentWidgetSize(330, 230)),
            componentWidgetSizes(listOf(ComponentWidgetSize(0, 0)), ComponentWidgetSize(330, 230)),
        )
    }
}
