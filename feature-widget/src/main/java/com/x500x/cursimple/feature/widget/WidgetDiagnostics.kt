package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build

/**
 * Report provider parsing, preview format, launcher support and platform compatibility for
 * widget diagnostics.
 */
data class WidgetDiagnosticsReport(
    val lines: List<Pair<String, String>>,
) {
    fun asText(): String = lines.joinToString("\n") { (label, value) -> "$label: $value" }
}

object WidgetDiagnostics {

    fun collect(context: Context): WidgetDiagnosticsReport {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val packageName = appContext.packageName
        val installedProviders = runCatching {
            manager.installedProviders.filter { it.provider.packageName == packageName }
        }.getOrDefault(emptyList())

        val lines = buildList {
            add(appContext.getString(R.string.widget_diag_device) to "${Build.MANUFACTURER} ${Build.MODEL}")
            add(appContext.getString(R.string.widget_diag_system) to appContext.systemDescription())
            add(appContext.getString(R.string.widget_diag_launcher) to appContext.launcherPackage())
            // Distinguish reported pinning support from known launcher nonresponse.
            add(
                appContext.getString(R.string.widget_diag_pin) to appContext.getString(
                    when {
                        !WidgetCatalog.isPinSupported(appContext) -> R.string.widget_diag_pin_unsupported
                        WidgetCatalog.pinLikelyIgnored(appContext) -> R.string.widget_diag_pin_ignored
                        else -> R.string.widget_diag_pin_supported
                    },
                ),
            )
            add(
                appContext.getString(R.string.widget_diag_pin_order) to WidgetCatalog.entries(appContext)
                    .firstOrNull()
                    ?.let { entry ->
                        WidgetCatalog.pinCandidates(appContext, entry)
                            .joinToString(" → ") { it.className.substringAfterLast('.') }
                    }
                    .orEmpty()
                    .ifBlank { appContext.getString(R.string.widget_diag_pin_order_empty) },
            )
            add(
                appContext.getString(R.string.widget_diag_vendor) to
                    WidgetCatalog.detectLauncherVendor(appContext).name,
            )

            WidgetCatalog.entries(appContext).forEach { entry ->
                val components = listOf(entry.provider) + entry.vendorProviders
                val parsed = components.count { component ->
                    installedProviders.any { it.provider == component }
                }
                val placed = WidgetCatalog.installedCount(appContext, entry)
                add(
                    entry.title to appContext.getString(
                        R.string.widget_diag_entry_value,
                        parsed,
                        placed,
                    ),
                )
            }

            add(
                appContext.getString(R.string.widget_diag_preview) to
                    appContext.previewImageKind(installedProviders.firstOrNull()?.previewImage ?: 0),
            )
            add(
                appContext.getString(R.string.widget_diag_vendor_autostart) to appContext.getString(
                    if (
                        com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
                            .hasVendorAutoStartPage(appContext)
                    ) {
                        R.string.widget_diag_vendor_autostart_present
                    } else {
                        R.string.widget_diag_vendor_autostart_absent
                    },
                ),
            )
        }
        return WidgetDiagnosticsReport(lines)
    }

    /** Include vendor OS details and explain platforms without Android compatibility. */
    private fun Context.systemDescription(): String {
        val android = getString(
            R.string.widget_diag_system_android,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
        )
        val harmony = systemProperty("hw_sc.build.platform.version")
        val emui = systemProperty("ro.build.version.emui")
        return when {
            !harmony.isNullOrBlank() ->
                getString(R.string.widget_diag_system_with_extra, android, "HarmonyOS $harmony")
            !emui.isNullOrBlank() ->
                getString(R.string.widget_diag_system_with_extra, android, emui)
            else -> android
        }
    }

    private fun Context.launcherPackage(): String =
        WidgetCatalog.homeLauncherPackage(this).ifBlank { getString(R.string.widget_diag_launcher_unknown) }

    private fun Context.previewImageKind(previewResId: Int): String {
        if (previewResId == 0) return getString(R.string.widget_diag_preview_missing)
        val drawable = runCatching {
            androidx.core.content.ContextCompat.getDrawable(this, previewResId)
        }.getOrNull() ?: return getString(R.string.widget_diag_preview_undecodable)
        return getString(
            if (drawable is BitmapDrawable) {
                R.string.widget_diag_preview_bitmap
            } else {
                R.string.widget_diag_preview_vector
            },
        )
    }

    private fun systemProperty(key: String): String? = runCatching {
        @Suppress("PrivateApi")
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getMethod("get", String::class.java)
        get.invoke(null, key) as? String
    }.getOrNull()
}
