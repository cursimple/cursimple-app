package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pin the documented vendor fallback list so unsupported guesses cannot replace working system
 * banners.
 */
class SelfDrawnNoticeTest {

    @Test
    fun vivo_andHuawei_areSelfDrawnOnly() {
        assertEquals(SelfDrawnNotice.Reason.Vivo, SelfDrawnNotice.reasonFor(VendorRom.Vivo))
        assertEquals(SelfDrawnNotice.Reason.Huawei, SelfDrawnNotice.reasonFor(VendorRom.Huawei))
    }

    @Test
    fun othersKeepTheSystemBanner() {
        listOf(
            VendorRom.Xiaomi,
            VendorRom.Honor,
            VendorRom.Oppo,
            VendorRom.OnePlus,
            VendorRom.Samsung,
            VendorRom.Meizu,
            VendorRom.Other,
        ).forEach { assertNull(it.name, SelfDrawnNotice.reasonFor(it)) }
    }
}
