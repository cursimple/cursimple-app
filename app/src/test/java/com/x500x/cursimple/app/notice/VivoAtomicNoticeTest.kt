package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom
import org.junit.Assert.*
import org.junit.Test

class VivoAtomicNoticeTest {
    @Test fun vivoUsesItsLocalProtocolOnlyWhenLiveUpdatesWereRequested() {
        assertTrue(VivoAtomicNotice.usesProtocol(VendorRom.Vivo, focus = true, plain = false))
        assertFalse(VivoAtomicNotice.usesProtocol(VendorRom.Vivo, focus = false, plain = false))
        assertFalse(VivoAtomicNotice.usesProtocol(VendorRom.Vivo, focus = true, plain = true))
        assertFalse(VivoAtomicNotice.usesProtocol(VendorRom.Samsung, focus = true, plain = false))
    }

    @Test fun thePayloadCarriesTheSceneTheFrameworkMatchesOn() {
        // Unknown scenes are dropped silently, so the constant must stay in sync with the payload.
        assertEquals("TRAIN", VivoAtomicNotice.SCENE)
    }

    @Test fun displaySelectionRespectsPrivateLockScreenAndTheAodSwitch() {
        assertEquals(0x10111, VivoAtomicNotice.displayMask(lockScreen = true, aod = true))
        assertEquals(0x00111, VivoAtomicNotice.displayMask(lockScreen = true, aod = false))
        assertEquals(0x00101, VivoAtomicNotice.displayMask(lockScreen = false, aod = true))
        assertEquals(0x00101, VivoAtomicNotice.displayMask(lockScreen = false, aod = false))
    }
}
