package com.x500x.cursimple.feature.plugin.extension

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test

class ComponentOrientationTest {
    @Test fun leavingReaderRestoresTheUsersOriginalOrientationAfterMultipleSwitches() {
        var current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val changes = mutableListOf<Int>()
        val orientation = ComponentOrientation({ current }, { current = it; changes += it })
        orientation.request("landscape")
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, current)
        orientation.request("portrait")
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, current)
        orientation.request("restore")
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, current)
        orientation.restore()
        assertEquals(3, changes.size)
        assertThrows(IllegalStateException::class.java) { orientation.request("sideways") }
        assertEquals(3, changes.size)
    }
}
