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
 * 一条提醒能不能按时响，取决于这几项。
 *
 * 分成两档：[blocking] 里的缺一项闹钟就是哑的，创建提醒前必须拦住用户；
 * 其余的只影响可靠性，给出提示即可。
 */
enum class AlarmPermission {
    /** 通知权限，没有它闹钟通知发不出来。 */
    Notifications,

    /** 精确闹钟，没有它只能落在一个模糊的时间窗里。 */
    ExactAlarm,

    /** 全屏通知，锁屏上直接亮出闹钟界面要靠它。 */
    FullScreenIntent,

    /** 电池优化白名单，没加进去时系统休眠可能压后甚至吞掉闹钟。 */
    BatteryUnrestricted,

    /** 厂商的自启动/后台运行白名单，应用被清理后能不能自己回来。 */
    VendorAutoStart,
    ;

    companion object {
        /** 缺了就等于闹钟不会响的那几项。 */
        val blocking: Set<AlarmPermission> = setOf(Notifications, ExactAlarm)
    }
}

/** 各项权限当前的状态；[VendorAutoStart] 系统不提供查询接口，永远按未知处理。 */
data class AlarmPermissionState(
    val granted: Set<AlarmPermission>,
) {
    /** 缺失项，[VendorAutoStart] 查不到就不算缺失，只在界面上单独提示。 */
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

/**
 * 每一项权限对应的设置入口。
 *
 * 一律返回候选列表而不是单个 Intent：厂商系统上第一个常常不存在，
 * 逐个试过去，最后总能落到应用详情页，不会出现「点了没反应」。
 */
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
            // Android 8 以前这个页面认的是另一对 extra，两套都带上
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

    /**
     * 电池优化。
     *
     * 已经在白名单里时不能再发「请求加入白名单」那条 Intent：系统会直接返回，
     * 界面上什么都不会出现，用户就觉得「点了没进设置，也没法关掉」。
     * 这种情况下改为打开白名单总列表，从那里能取消授权。
     */
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

    /** 厂商的自启动 / 后台运行白名单，组件名取自各家安全中心。 */
    fun vendorAutoStart(context: Context): List<Intent> =
        VENDOR_AUTO_START_COMPONENTS.map { (pkg, activity) ->
            Intent().setClassName(pkg, activity)
        } + appDetails(context)

    /** 后台弹出界面：小米、vivo、OPPO 上闹钟想直接亮出界面要靠它。 */
    fun backgroundPopup(context: Context): List<Intent> = listOf(
        Intent()
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
            )
            .putExtra("extra_pkgname", context.packageName),
        Intent()
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            )
            .putExtra("extra_pkgname", context.packageName),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
        ).putExtra("packagename", context.packageName),
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
        ),
        appDetails(context),
    )

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri(context))

    /** 有没有厂商专用的自启动页面，用来决定要不要在权限页里露出这一项。 */
    fun hasVendorAutoStartPage(context: Context): Boolean =
        VENDOR_AUTO_START_COMPONENTS.any { (pkg, activity) ->
            resolves(context, Intent().setClassName(pkg, activity))
        }

    fun resolves(context: Context, intent: Intent): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }.getOrDefault(false)

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /**
     * 各家自启动 / 后台管理页面。
     *
     * 同一厂商列多条是因为不同系统版本换过组件名，逐个试过去才稳。
     */
    private val VENDOR_AUTO_START_COMPONENTS: List<Pair<String, String>> = listOf(
        // 小米 MIUI / HyperOS
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // 华为 EMUI / HarmonyOS
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        // 荣耀
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // OPPO / realme ColorOS
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // vivo / iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // 一加
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        // 三星
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        // 魅族
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
    )
}

/**
 * 逐个试候选 Intent，第一个能起来的就用它。
 *
 * 返回 false 表示一条都没打开，调用方应当提示用户手动去系统设置里找。
 */
fun launchFirstAvailableSetting(context: Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        val candidate = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!AlarmSettingsIntents.resolves(context, candidate)) continue
        if (runCatching { context.startActivity(candidate) }.isSuccess) return true
    }
    return false
}
