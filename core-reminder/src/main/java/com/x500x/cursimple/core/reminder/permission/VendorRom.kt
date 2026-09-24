package com.x500x.cursimple.core.reminder.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 手机跑的是哪家的系统。
 *
 * 各家的后台管控差别很大：小米国行不开「自启动」时，从最近任务划掉应用就等于强行停止，
 * 挂着的闹钟会被系统一并清掉；华为、荣耀要在「应用启动管理」里手动放开三项。
 * 引导用户去哪一页、说什么话，都得先知道是哪家。
 */
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
            // 先认系统属性：换过品牌的机型（比如荣耀独立前后）只看厂商名会认错
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

/**
 * 小米系统里几项不对外公开、但能查到的权限。
 *
 * 是 MIUI / HyperOS 自己加的 AppOps（Telegram 也是这样查的），不在 AOSP 里；
 * 查不到（非小米、系统改了实现）时返回 null，界面按「未知」处理，退回让用户自己确认。
 */
object MiuiPermissions {
    private const val OP_AUTO_START = 10008
    private const val OP_SHOW_WHEN_LOCKED = 10020
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    /** 自启动：国行没开时，划掉应用就是强行停止，闹钟全没了。 */
    fun autoStart(context: Context): Boolean? = checkOp(context, OP_AUTO_START)

    /** 后台弹出界面：闹钟响时直接亮出全屏界面、弹悬浮窗都要它。 */
    fun backgroundStartActivity(context: Context): Boolean? = checkOp(context, OP_BACKGROUND_START_ACTIVITY)

    /** 锁屏显示：锁屏上亮出闹钟界面要它。 */
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
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }
}
