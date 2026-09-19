package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build

/**
 * 小组件自检。
 *
 * 「桌面上找不到课简的小组件」在不同手机上有完全不同的原因：系统没解析到 provider、
 * 预览图不是位图被启动器过滤、启动器根本不支持一键添加、或者装的是鸿蒙 NEXT 这种
 * 没有安卓兼容层的系统。隔着屏幕猜不出来，这里把判断依据一次性列出来。
 */
data class WidgetDiagnosticsReport(
    val lines: List<Pair<String, String>>,
) {
    /** 便于用户整段复制发出来。 */
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
            // 系统说「支持」但这家桌面实际不响应时，如实写出来，
            // 免得自检信息本身把人带偏（vivo 上就是这样）
            add(
                appContext.getString(R.string.widget_diag_pin) to appContext.getString(
                    when {
                        !WidgetCatalog.isPinSupported(appContext) -> R.string.widget_diag_pin_unsupported
                        WidgetCatalog.pinLikelyIgnored(appContext) -> R.string.widget_diag_pin_ignored
                        else -> R.string.widget_diag_pin_supported
                    },
                ),
            )
            // 桌面受理了请求却什么都不弹时，要能看出我们是按什么顺序请求的
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

    /**
     * 系统描述，带上鸿蒙 / EMUI 版本。
     *
     * 鸿蒙 NEXT 已经没有安卓兼容层，安卓应用连同它的小组件都不会出现在那上面，
     * 自检里必须把这点写清楚，否则用户会一直以为是应用没适配。
     */
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

    /** 矢量预览图会被部分厂商选择器整项过滤掉，所以要能看出它到底是什么。 */
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
