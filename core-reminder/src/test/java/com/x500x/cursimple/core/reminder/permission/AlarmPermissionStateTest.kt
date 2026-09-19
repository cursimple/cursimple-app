package com.x500x.cursimple.core.reminder.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 哪些权限缺了必须拦住用户，哪些只是提示。 */
class AlarmPermissionStateTest {

    @Test
    fun `notifications and exact alarms are the blocking pair`() {
        assertEquals(
            setOf(AlarmPermission.Notifications, AlarmPermission.ExactAlarm),
            AlarmPermission.blocking,
        )
    }

    @Test
    fun `missing battery allowlist alone does not block creating reminders`() {
        val state = AlarmPermissionState(
            granted = setOf(
                AlarmPermission.Notifications,
                AlarmPermission.ExactAlarm,
                AlarmPermission.FullScreenIntent,
            ),
        )

        assertEquals(listOf(AlarmPermission.BatteryUnrestricted), state.missing)
        assertTrue("只差电池白名单时不该拦住用户", state.missingBlocking.isEmpty())
    }

    @Test
    fun `missing exact alarm blocks`() {
        val state = AlarmPermissionState(granted = setOf(AlarmPermission.Notifications))

        assertEquals(listOf(AlarmPermission.ExactAlarm), state.missingBlocking)
    }

    @Test
    fun `vendor auto start is never counted as missing`() {
        // 厂商自启动没有可查的接口，算进缺失项只会变成一个永远消不掉的红点
        val state = AlarmPermissionState(
            granted = AlarmPermission.entries.toSet() - AlarmPermission.VendorAutoStart,
        )

        assertTrue(state.missing.isEmpty())
    }
}
