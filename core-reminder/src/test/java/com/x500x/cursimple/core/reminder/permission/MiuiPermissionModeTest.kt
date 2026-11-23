package com.x500x.cursimple.core.reminder.permission

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiuiPermissionModeTest {
    @Test
    fun explicitAppOpsDecisionsAreReflected() {
        assertEquals(true, miuiPermissionFromMode(AppOpsManager.MODE_ALLOWED))
        assertEquals(false, miuiPermissionFromMode(AppOpsManager.MODE_IGNORED))
        assertEquals(false, miuiPermissionFromMode(AppOpsManager.MODE_ERRORED))
    }

    @Test
    fun unspecifiedDecisionsAreNotReportedAsDenied() {
        assertNull(miuiPermissionFromMode(AppOpsManager.MODE_DEFAULT))
        assertNull(miuiPermissionFromMode(AppOpsManager.MODE_FOREGROUND))
    }
}
