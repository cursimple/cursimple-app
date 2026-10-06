package com.x500x.cursimple.app.notice

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * Combine vendor capabilities with the system API; promotion permission alone can misreport
 * chip support.
 */
object StatusBarChipSupport {

    enum class Level {
        /** Unsupported platform or vendor versions produce ordinary notifications only. */
        Unsupported,

        /**
         * Supported ColorOS-family systems use standard live-update fields despite API
         * permission results.
         */
        AlwaysOn,

        /** On remaining supported systems, respect the platform's promotion permission. */
        PlatformDecides,
    }

    fun level(): Level = cached ?: decide(
        sdk = Build.VERSION.SDK_INT,
        vendor = VendorRom.current(),
        oneUiVersion = prop("ro.build.version.oneui")?.toIntOrNull(),
        oplusMajor = prop("ro.build.version.oplusrom")?.let(::leadingNumber)
            ?: prop("ro.build.version.opporom")?.let(::leadingNumber),
    ).also { cached = it }

    /** Whether this device currently permits chip presentation. */
    fun allowed(context: Context): Boolean = when (level()) {
        Level.Unsupported -> false
        Level.AlwaysOn -> true
        Level.PlatformDecides -> runCatching {
            NotificationManagerCompat.from(context).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    internal fun decide(sdk: Int, vendor: VendorRom, oneUiVersion: Int?, oplusMajor: Int?): Level {
        if (sdk < Build.VERSION_CODES.BAKLAVA) return Level.Unsupported
        if (vendor == VendorRom.Samsung && (oneUiVersion ?: 0) < ONE_UI_8_5) return Level.Unsupported
        // Overlay-only systems must not request ongoing promotion that would suppress their banners.
        if (SelfDrawnNotice.reasonFor(vendor) != null) return Level.Unsupported
        if (vendor == VendorRom.Oppo || vendor == VendorRom.OnePlus) {
            return if ((oplusMajor ?: 16) >= 16) Level.AlwaysOn else Level.PlatformDecides
        }
        return Level.PlatformDecides
    }

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
