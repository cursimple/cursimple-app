package com.x500x.cursimple.app.notice

import com.x500x.cursimple.app.notice.StatusBarChipSupport.Level
import com.x500x.cursimple.core.reminder.permission.VendorRom
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarChipSupportTest {

    private fun decide(sdk: Int, vendor: VendorRom, oneUi: Int? = null, oplus: Int? = null) =
        StatusBarChipSupport.decide(sdk, vendor, oneUi, oplus)

    @Test
    fun beforeAndroid16_isUnsupportedEverywhere() {
        VendorRom.entries.forEach { assertEquals(Level.Unsupported, decide(35, it, 80500, 16)) }
    }

    @Test
    fun colorOs16_isAlwaysOn_evenWithoutReadableVersion() {
        assertEquals(Level.AlwaysOn, decide(36, VendorRom.Oppo, oplus = 16))
        assertEquals(Level.AlwaysOn, decide(36, VendorRom.OnePlus, oplus = 17))
        assertEquals(Level.AlwaysOn, decide(36, VendorRom.Oppo))
    }

    @Test
    fun olderOplusRomOnAndroid16_fallsBackToPlatform() {
        assertEquals(Level.PlatformDecides, decide(36, VendorRom.Oppo, oplus = 15))
    }

    @Test
    fun oneUi_needs8_5() {
        assertEquals(Level.Unsupported, decide(36, VendorRom.Samsung, oneUi = 80000))
        assertEquals(Level.Unsupported, decide(36, VendorRom.Samsung))
        assertEquals(Level.PlatformDecides, decide(36, VendorRom.Samsung, oneUi = 80500))
        assertEquals(Level.PlatformDecides, decide(37, VendorRom.Samsung, oneUi = 90000))
    }

    @Test
    fun untestedSkins_useThePlatformAnswer() {
        listOf(VendorRom.Xiaomi, VendorRom.Honor, VendorRom.Other).forEach {
            assertEquals(Level.PlatformDecides, decide(36, it))
        }
    }

    @Test
    fun vivo_neverGetsTheStandardChip() {
        assertEquals(Level.Unsupported, decide(36, VendorRom.Vivo))
        assertEquals(Level.Unsupported, decide(37, VendorRom.Vivo))
    }

    @Test
    fun leadingNumber_parsesRomVersions() {
        assertEquals(16, StatusBarChipSupport.leadingNumber("V16.0.0"))
        assertEquals(15, StatusBarChipSupport.leadingNumber("15.1"))
        assertEquals(null, StatusBarChipSupport.leadingNumber("V"))
    }
}
