package com.x500x.cursimple.core.reminder.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Detect vendor background policy before choosing permission guidance. */
enum class VendorRom {
    Xiaomi,
    Huawei,
    Honor,
    Oppo,
    Vivo,
    OnePlus,
    Samsung,
    Meizu,
    Other,
    ;

    companion object {
        @Volatile
        private var cached: VendorRom? = null

        fun current(): VendorRom = cached ?: detect().also { cached = it }

        private fun detect(): VendorRom {
            // Prefer OS properties over manufacturer names for rebranded devices.
            when {
                !prop("ro.mi.os.version.name").isNullOrBlank() ||
                    !prop("ro.miui.ui.version.name").isNullOrBlank() -> return Xiaomi
                !prop("ro.build.version.magic").isNullOrBlank() -> return Honor
                !prop("ro.build.version.emui").isNullOrBlank() ||
                    !prop("hw_sc.build.platform.version").isNullOrBlank() -> return Huawei
                !prop("ro.vivo.os.version").isNullOrBlank() -> return Vivo
                !prop("ro.build.version.oplusrom").isNullOrBlank() ||
                    !prop("ro.build.version.opporom").isNullOrBlank() -> {
                    return if (Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)) OnePlus else Oppo
                }
            }
            return when (Build.MANUFACTURER.lowercase()) {
                "xiaomi", "redmi", "poco" -> Xiaomi
                "huawei" -> Huawei
                "honor" -> Honor
                "oppo", "realme" -> Oppo
                "vivo", "iqoo" -> Vivo
                "oneplus" -> OnePlus
                "samsung" -> Samsung
                "meizu" -> Meizu
                else -> Other
            }
        }

        private fun prop(key: String): String? = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getMethod("get", String::class.java).invoke(null, key) as? String
        }.getOrNull()
    }
}

/** Read explicit decisions from MIUI AppOps where the system exposes them. */
object MiuiPermissions {
    private const val OP_AUTO_START = 10008
    private const val OP_INSTALL_SHORTCUT = 10017
    private const val OP_SHOW_WHEN_LOCKED = 10020
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    fun autoStart(context: Context): Boolean? = checkOp(context, OP_AUTO_START)

    fun installShortcut(context: Context): Boolean? = checkOp(context, OP_INSTALL_SHORTCUT)

    fun backgroundStartActivity(context: Context): Boolean? = checkOp(context, OP_BACKGROUND_START_ACTIVITY)

    fun showWhenLocked(context: Context): Boolean? = checkOp(context, OP_SHOW_WHEN_LOCKED)

    private fun checkOp(context: Context, op: Int): Boolean? {
        if (VendorRom.current() != VendorRom.Xiaomi) return null
        return runCatching {
            val manager = context.getSystemService(AppOpsManager::class.java) ?: return null
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val mode = method.invoke(manager, op, Process.myUid(), context.packageName) as Int
            miuiPermissionFromMode(mode)
        }.getOrNull()
    }
}

internal fun miuiPermissionFromMode(mode: Int): Boolean? = when (mode) {
    AppOpsManager.MODE_ALLOWED -> true
    AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
    else -> null
}
