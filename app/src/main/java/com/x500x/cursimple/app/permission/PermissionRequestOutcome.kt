package com.x500x.cursimple.app.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Result of one runtime permission request. */
enum class PermissionRequestOutcome {
    Granted,

    /** Denied, but the system can present another request. */
    Denied,

    /** Permanently denied; requires app settings. */
    PermanentlyDenied,
}

/**
 * Use post-request [canAskAgain] to distinguish retryable denial from settings-only recovery.
 */
fun permissionRequestOutcome(granted: Boolean, canAskAgain: Boolean): PermissionRequestOutcome = when {
    granted -> PermissionRequestOutcome.Granted
    canAskAgain -> PermissionRequestOutcome.Denied
    else -> PermissionRequestOutcome.PermanentlyDenied
}

/** Find the Activity through Context wrappers, or return null. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
