package com.x500x.cursimple.core.reminder.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Distinguish blocking access from advisory reliability settings. */
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
        // Unqueryable vendor status must not create a permanent missing-permission badge.
        val state = AlarmPermissionState(
            granted = AlarmPermission.entries.toSet() - AlarmPermission.VendorAutoStart,
        )

        assertTrue(state.missing.isEmpty())
    }
}
