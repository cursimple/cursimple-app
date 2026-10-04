package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 名单只放厂商文档写明了的系统：多认一家会把本来好好的系统横幅换成自绘的，
 * 少认一家则是一个都不弹。两条都比「猜」好不到哪去，所以逐条钉住。
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
