package com.x500x.cursimple.app.notice

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * 这台手机能不能出 Android 16 实时活动的状态栏胶囊。
 *
 * 不能只问 [NotificationManagerCompat.canPostPromotedNotifications]：它两头都会说错。
 * ColorOS 16 上它回 false，流体云照样把胶囊画出来；One UI 8.0 也回 false，那边是真没有
 * （平台内部开关还关着，8.5 才打开）。所以先按系统认一遍，认不出的再听系统 API 的。
 */
object StatusBarChipSupport {

    enum class Level {
        /** Android 16 以前、One UI 8.5 以前，或只用自己画的系统：发了也只是一条普通通知 */
        Unsupported,

        /** ColorOS / OxygenOS / realme UI 16 起：流体云直接接标准接口，系统 API 的回答不可信 */
        AlwaysOn,

        /** 其余 Android 16：Pixel、HyperOS、MagicOS……系统说关着就是用户真关了 */
        PlatformDecides,
    }

    fun level(): Level = cached ?: decide(
        sdk = Build.VERSION.SDK_INT,
        vendor = VendorRom.current(),
        oneUiVersion = prop("ro.build.version.oneui")?.toIntOrNull(),
        oplusMajor = prop("ro.build.version.oplusrom")?.let(::leadingNumber)
            ?: prop("ro.build.version.opporom")?.let(::leadingNumber),
    ).also { cached = it }

    /** 系统眼下肯不肯给这个应用画胶囊。不支持的机型算不肯。 */
    fun allowed(context: Context): Boolean = when (level()) {
        Level.Unsupported -> false
        Level.AlwaysOn -> true
        Level.PlatformDecides -> runCatching {
            NotificationManagerCompat.from(context).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    internal fun decide(sdk: Int, vendor: VendorRom, oneUiVersion: Int?, oplusMajor: Int?): Level {
        if (sdk < Build.VERSION_CODES.BAKLAVA) return Level.Unsupported
        // One UI 的版本号写成 80500 这样：主版本 ×10000 + 次版本 ×100
        if (vendor == VendorRom.Samsung && (oneUiVersion ?: 0) < ONE_UI_8_5) return Level.Unsupported
        // 只用自己画的系统：标准胶囊不画（vivo 原子岛要单独申请），胶囊要的 ongoing 还会让横幅和锁屏提醒全没了
        if (SelfDrawnNotice.reasonFor(vendor) != null) return Level.Unsupported
        // Oplus 家读不出版本号时也按 16 算：能跑到这里已经是 Android 16 了
        if (vendor == VendorRom.Oppo || vendor == VendorRom.OnePlus) {
            return if ((oplusMajor ?: 16) >= 16) Level.AlwaysOn else Level.PlatformDecides
        }
        return Level.PlatformDecides
    }

    /** 「V16.0.0」「16.1」都取出 16 */
    internal fun leadingNumber(raw: String): Int? =
        Regex("""\d+""").find(raw)?.value?.toIntOrNull()

    private const val ONE_UI_8_5 = 80500

    @Volatile
    private var cached: Level? = null

    internal fun prop(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
