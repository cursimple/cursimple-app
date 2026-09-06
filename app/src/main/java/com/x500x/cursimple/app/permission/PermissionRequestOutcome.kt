package com.x500x.cursimple.app.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 一次运行时权限请求的结果。 */
enum class PermissionRequestOutcome {
    /** 已授予。 */
    Granted,

    /** 本次拒绝，再点还能弹系统弹窗。 */
    Denied,

    /** 系统不会再弹窗，只能去应用详情页里开。 */
    PermanentlyDenied,
}

/**
 * 判断这次请求之后该怎么引导。
 *
 * [canAskAgain] 取自请求返回后的 shouldShowRequestPermissionRationale：
 * 拒绝之后它仍为真表示系统还会再弹，为假则表示被永久拒绝，
 * 此时再点按钮不会有任何反应，必须把用户送到应用详情页。
 */
fun permissionRequestOutcome(granted: Boolean, canAskAgain: Boolean): PermissionRequestOutcome = when {
    granted -> PermissionRequestOutcome.Granted
    canAskAgain -> PermissionRequestOutcome.Denied
    else -> PermissionRequestOutcome.PermanentlyDenied
}

/** 从 Compose 的 Context 里找出宿主 Activity，拿不到时返回 null。 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
