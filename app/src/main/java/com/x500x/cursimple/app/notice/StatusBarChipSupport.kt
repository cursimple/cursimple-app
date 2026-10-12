package com.x500x.cursimple.app.notice

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/** Native live-update capability and the current system permission. */
object StatusBarChipSupport {

    enum class Level {
        /** Unsupported platform or vendor versions produce ordinary notifications only. */
        Unsupported,

        /** On remaining supported systems, respect the platform's promotion permission. */
        PlatformDecides,
    }

    fun level(): Level = decide(Build.VERSION.SDK_INT)

    fun allowed(context: Context): Boolean = level() != Level.Unsupported && runCatching {
        NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }.getOrDefault(false)

    /** OS capability and user permission take precedence over vendor/version guesses. */
    internal fun decide(sdk: Int): Level =
        if (sdk >= Build.VERSION_CODES.BAKLAVA) Level.PlatformDecides else Level.Unsupported

    internal fun prop(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
