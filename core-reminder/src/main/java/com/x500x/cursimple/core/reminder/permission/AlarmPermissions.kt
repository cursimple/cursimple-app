package com.x500x.cursimple.core.reminder.permission

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Missing [blocking] access prevents reminder creation; other requirements affect reliability
 * only.
 */
enum class AlarmPermission {
    /** Notification permission enables alarm notification presentation. */
    Notifications,

    /** Exact-alarm access avoids windowed timing fallback. */
    ExactAlarm,

    FullScreenIntent,

    BatteryUnrestricted,

    /** Vendor background-start allowlist for process recovery. */
    VendorAutoStart,
    ;

    companion object {
        val blocking: Set<AlarmPermission> = setOf(Notifications, ExactAlarm)
    }
}

/** VendorAutoStart remains unknown when the platform exposes no query. */
data class AlarmPermissionState(
    val granted: Set<AlarmPermission>,
) {
    /** Unknown vendor access is guidance, not a confirmed missing permission. */
    val missing: List<AlarmPermission> = AlarmPermission.entries.filter {
        it != AlarmPermission.VendorAutoStart && it !in granted
    }

    val missingBlocking: List<AlarmPermission> = missing.filter { it in AlarmPermission.blocking }
}

fun readAlarmPermissionState(context: Context): AlarmPermissionState {
    val appContext = context.applicationContext
    val granted = buildSet {
        if (hasNotificationPermission(appContext)) add(AlarmPermission.Notifications)
        if (canScheduleExactAlarms(appContext)) add(AlarmPermission.ExactAlarm)
        if (canUseFullScreenIntent(appContext)) add(AlarmPermission.FullScreenIntent)
        if (isIgnoringBatteryOptimizations(appContext)) add(AlarmPermission.BatteryUnrestricted)
    }
    return AlarmPermissionState(granted = granted)
}

fun hasNotificationPermission(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
}

fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}

/** Return ordered settings candidates with app-details fallback. */
object AlarmSettingsIntents {

    fun forPermission(context: Context, permission: AlarmPermission): List<Intent> = when (permission) {
        AlarmPermission.Notifications -> notifications(context)
        AlarmPermission.ExactAlarm -> exactAlarm(context)
        AlarmPermission.FullScreenIntent -> fullScreenIntent(context)
        AlarmPermission.BatteryUnrestricted -> batteryOptimization(context)
        AlarmPermission.VendorAutoStart -> vendorAutoStart(context)
    }

    fun notifications(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra("app_package", context.packageName)
            .putExtra("app_uid", context.applicationInfo.uid),
        appDetails(context),
    )

    fun exactAlarm(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(packageUri(context)),
            )
            add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        add(appDetails(context))
    }

    fun fullScreenIntent(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(packageUri(context)),
            )
        }
        add(appDetails(context))
    }

    /** Already-exempt apps open the allowlist instead of an ignored exemption request. */
    fun batteryOptimization(context: Context): List<Intent> = buildList {
        if (isIgnoringBatteryOptimizations(context)) {
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            @Suppress("BatteryLife")
            add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(packageUri(context)),
            )
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        add(appDetails(context))
    }

    /** Prefer package-specific vendor pages, falling back to their full lists. */
    fun vendorAutoStart(context: Context): List<Intent> = buildList {
        if (VendorRom.current() == VendorRom.Xiaomi) {
            add(
                Intent("miui.intent.action.OP_AUTO_START")
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .putExtra("extra_pkgname", context.packageName),
            )
        }
        addAll(perAppVendorPermissionPages(context))
        addAll(VENDOR_AUTO_START_COMPONENTS.map { (pkg, activity) -> Intent().setClassName(pkg, activity) })
        add(appDetails(context))
    }

    /** Vendor battery policy is separate from the platform optimization exemption. */
    fun vendorBatterySaver(context: Context): List<Intent> = buildList {
        when (VendorRom.current()) {
            VendorRom.Xiaomi -> {
                listOf("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY", "miui.intent.action.POWER_HIDE_MODE_APP_LIST")
                    .forEach { action ->
                        add(
                            Intent(action)
                                .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                                .putExtra("package_name", context.packageName)
                                .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager)),
                        )
                    }
            }
            VendorRom.Samsung -> add(
                Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                    .setPackage("com.samsung.android.lool")
                    .putExtra("activity_type", 2),
            )
            VendorRom.Vivo -> add(
                Intent().setClassName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
                ),
            )
            VendorRom.Huawei -> addAll(huaweiProtectCandidates("com.huawei.systemmanager"))
            VendorRom.Honor -> addAll(huaweiProtectCandidates("com.hihonor.systemmanager"))
            else -> Unit
        }
        addAll(batteryOptimization(context))
    }

    fun hasVendorBatterySaver(): Boolean =
        VendorRom.current() in setOf(VendorRom.Xiaomi, VendorRom.Samsung, VendorRom.Vivo, VendorRom.Huawei, VendorRom.Honor)

    private fun huaweiProtectCandidates(securityPkg: String): List<Intent> = listOf(
        Intent().setClassName(securityPkg, "$securityPkg.optimize.process.ProtectActivity"),
        Intent().setClassName(securityPkg, "$securityPkg.powermanager.HwPowerManagerActivity"),
    )

    /** Vendor-specific package extras target this app's permission page. */
    private fun perAppVendorPermissionPages(context: Context): List<Intent> = listOf(
        // vivo / iQOO
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
        ).putExtra("packagename", context.packageName)
            .putExtra("pkgname", context.packageName),
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        ).putExtra("extra_pkgname", context.packageName),
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        ).putExtra("extra_pkgname", context.packageName),
        // OPPO / realme ColorOS
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.PermissionManagerActivity",
        ).putExtra("packageName", context.packageName),
        // Use current and legacy vendor security package names for version coverage.
        Intent().setClassName(
            "com.oplus.safecenter",
            "com.coloros.safecenter.permission.PermissionManagerActivity",
        ).putExtra("packageName", context.packageName),
        // Fall back to app details without a stable vendor-specific destination.
    )

    /** Background pop-up access controls direct alarm screens; prefer app-specific settings. */
    fun backgroundPopup(context: Context): List<Intent> =
        perAppVendorPermissionPages(context) + listOf(
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
            ),
            appDetails(context),
        )

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri(context))

    /** Detect known vendor control first; package visibility alone cannot prove absence. */
    fun hasVendorAutoStartPage(context: Context): Boolean =
        VendorRom.current() != VendorRom.Other ||
            VENDOR_AUTO_START_COMPONENTS.any { (pkg, activity) ->
                resolves(context, Intent().setClassName(pkg, activity))
            }

    fun resolves(context: Context, intent: Intent): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }.getOrDefault(false)

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Try component names across vendor OS versions. */
    private val VENDOR_AUTO_START_COMPONENTS: List<Pair<String, String>> = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
        // OPPO / realme ColorOS
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.oplus.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        // Omit signature-protected vendor destinations inaccessible to third-party apps.
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
    )
}

/** Return false after all Intent candidates fail; callers then provide manual guidance. */
fun launchFirstAvailableSetting(context: Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        val candidate = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Try launching directly rather than filtering through incomplete package visibility.
        if (runCatching { context.startActivity(candidate) }.isSuccess) return true
    }
    return false
}
