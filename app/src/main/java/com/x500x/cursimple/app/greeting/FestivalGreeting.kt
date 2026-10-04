package com.x500x.cursimple.app.greeting

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.kernel.time.BeijingTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 一条问候用到的资源：标题，和几句里随机挑一句的正文。 */
internal data class GreetingRes(@StringRes val title: Int, @ArrayRes val lines: Int)

/**
 * 节日、节气当天的一句问候，安安静静出现在通知栏里，给课表添一点人情味。
 *
 * 哪天是什么节日、节气查 [FestivalDataset]（联网下载的日期表），不在代码里写死。
 * 只在节日当天发一条（国庆只在 10 月 1 日），每天上午随机一个时刻；那会儿没发出去
 * （手机省电、App 被收走），打开 App 时补上。一天最多一条，撞上好几个节日时随机挑一个。
 * 不写进更新公告、设置里也不放开关，留点惊喜；不想收的人可以在系统通知设置里关掉这个渠道。
 */
internal object FestivalGreeting {
    private const val CHANNEL_ID = "festival_greeting"
    private const val NOTIFICATION_ID = 0x464753
    private const val PREFS = "festival_greeting"
    private const val KEY_LAST_DATE = "last_posted_date"

    /** 定时任务在这个窗口里随机挑一刻：早于八点会吵醒人，太晚了又不像当天的问候 */
    internal val WINDOW_START: LocalTime = LocalTime.of(8, 0)
    internal const val WINDOW_MINUTES = 180L

    /** 过了随机窗口还没发，打开 App 时再补 */
    private val CATCH_UP_AFTER: LocalTime = WINDOW_START.plusMinutes(WINDOW_MINUTES)

    fun postIfDue(context: Context, catchUp: Boolean = false) {
        val app = context.applicationContext
        val now = BeijingTime.nowDateTime()
        val today = now.toLocalDate()
        if (now.toLocalTime().isBefore(if (catchUp) CATCH_UP_AFTER else WINDOW_START)) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_DATE, null) == today.toString()) return
        val greeting = greetingFor(app, today) ?: return
        if (!app.canPostNotifications()) return
        app.post(greeting)
        prefs.edit().putString(KEY_LAST_DATE, today.toString()).apply()
    }

    private fun greetingFor(context: Context, date: LocalDate): GreetingRes? {
        // 日期以联网下来的数据表为准；本机一次都没下成功过时才现算
        FestivalDataset.idsOn(context, date)?.let { ids ->
            return ids.mapNotNull(::greetingResForId).randomOrNull()
        }
        val festivals = FestivalCalendar.festivalsOn(date, lunarDayOf(date), lunarDayOf(date.plusDays(1)))
        val candidates = festivals.map { it.greetingRes() } +
            listOfNotNull(FestivalCalendar.solarTermOn(date)?.greetingRes())
        return candidates.randomOrNull()
    }

    /** 农历用系统自带的 ICU 换算；个别 ROM 上换算出错时只认公历节日和节气。 */
    private fun lunarDayOf(date: LocalDate): LunarDay? = runCatching {
        val calendar = android.icu.util.ChineseCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Shanghai"))
        calendar.timeInMillis = date.atTime(12, 0)
            .atZone(TimeZone.getTimeZone("Asia/Shanghai").toZoneId())
            .toInstant()
            .toEpochMilli()
        LunarDay(
            month = calendar.get(android.icu.util.Calendar.MONTH) + 1,
            day = calendar.get(android.icu.util.Calendar.DAY_OF_MONTH),
            leapMonth = calendar.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1,
        )
    }.getOrNull()

    private fun Context.canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // 调用方先过了 canPostNotifications()（通知开关 + POST_NOTIFICATIONS），lint 跨函数看不出来；
    // 用户在这之间撤销权限时 notify 抛的 SecurityException 由下面的 runCatching 兜住
    @SuppressLint("MissingPermission")
    private fun Context.post(greeting: GreetingRes) {
        createChannel()
        val lines = resources.getStringArray(greeting.lines)
        val text = lines.random()
        val openIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(greeting.title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun Context.createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            // 不响铃、不弹横幅：问候不该在课堂上把人吓一跳，出现在通知栏里就够了
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.greeting_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.greeting_channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/** 每天在上午的随机时刻看一眼今天是不是节日。 */
class FestivalGreetingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        FestivalGreeting.postIfDue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "festival_greeting"

        /** 每次启动都重新挑一个时刻，天天差不多同一分钟发就不像真人了。 */
        fun schedule(context: Context) {
            val now = BeijingTime.nowDateTime()
            val offset = Random.nextLong(FestivalGreeting.WINDOW_MINUTES)
            var target = LocalDateTime.of(now.toLocalDate(), FestivalGreeting.WINDOW_START).plusMinutes(offset)
            if (!target.isAfter(now)) target = target.plusDays(1)
            val request = PeriodicWorkRequestBuilder<FestivalGreetingWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, target).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

internal fun Festival.greetingRes(): GreetingRes = when (this) {
    Festival.NewYear -> GreetingRes(R.string.greeting_title_festival_new_year, R.array.greeting_lines_festival_new_year)
    Festival.Valentine -> GreetingRes(R.string.greeting_title_festival_valentine, R.array.greeting_lines_festival_valentine)
    Festival.WomensDay -> GreetingRes(R.string.greeting_title_festival_womens_day, R.array.greeting_lines_festival_womens_day)
    Festival.ArborDay -> GreetingRes(R.string.greeting_title_festival_arbor_day, R.array.greeting_lines_festival_arbor_day)
    Festival.AprilFools -> GreetingRes(R.string.greeting_title_festival_april_fools, R.array.greeting_lines_festival_april_fools)
    Festival.LaborDay -> GreetingRes(R.string.greeting_title_festival_labor_day, R.array.greeting_lines_festival_labor_day)
    Festival.YouthDay -> GreetingRes(R.string.greeting_title_festival_youth_day, R.array.greeting_lines_festival_youth_day)
    Festival.ChildrensDay -> GreetingRes(R.string.greeting_title_festival_childrens_day, R.array.greeting_lines_festival_childrens_day)
    Festival.TeachersDay -> GreetingRes(R.string.greeting_title_festival_teachers_day, R.array.greeting_lines_festival_teachers_day)
    Festival.NationalDay -> GreetingRes(R.string.greeting_title_festival_national_day, R.array.greeting_lines_festival_national_day)
    Festival.ChristmasEve -> GreetingRes(R.string.greeting_title_festival_christmas_eve, R.array.greeting_lines_festival_christmas_eve)
    Festival.Christmas -> GreetingRes(R.string.greeting_title_festival_christmas, R.array.greeting_lines_festival_christmas)
    Festival.MothersDay -> GreetingRes(R.string.greeting_title_festival_mothers_day, R.array.greeting_lines_festival_mothers_day)
    Festival.FathersDay -> GreetingRes(R.string.greeting_title_festival_fathers_day, R.array.greeting_lines_festival_fathers_day)
    Festival.NewYearsEve -> GreetingRes(R.string.greeting_title_festival_new_years_eve, R.array.greeting_lines_festival_new_years_eve)
    Festival.SpringFestival -> GreetingRes(R.string.greeting_title_festival_spring_festival, R.array.greeting_lines_festival_spring_festival)
    Festival.Lantern -> GreetingRes(R.string.greeting_title_festival_lantern, R.array.greeting_lines_festival_lantern)
    Festival.DragonHead -> GreetingRes(R.string.greeting_title_festival_dragon_head, R.array.greeting_lines_festival_dragon_head)
    Festival.DragonBoat -> GreetingRes(R.string.greeting_title_festival_dragon_boat, R.array.greeting_lines_festival_dragon_boat)
    Festival.Qixi -> GreetingRes(R.string.greeting_title_festival_qixi, R.array.greeting_lines_festival_qixi)
    Festival.MidAutumn -> GreetingRes(R.string.greeting_title_festival_mid_autumn, R.array.greeting_lines_festival_mid_autumn)
    Festival.DoubleNinth -> GreetingRes(R.string.greeting_title_festival_double_ninth, R.array.greeting_lines_festival_double_ninth)
    Festival.Laba -> GreetingRes(R.string.greeting_title_festival_laba, R.array.greeting_lines_festival_laba)
}

internal fun SolarTerm.greetingRes(): GreetingRes = when (this) {
    SolarTerm.SpringEquinox -> GreetingRes(R.string.greeting_title_term_spring_equinox, R.array.greeting_lines_term_spring_equinox)
    SolarTerm.PureBrightness -> GreetingRes(R.string.greeting_title_term_pure_brightness, R.array.greeting_lines_term_pure_brightness)
    SolarTerm.GrainRain -> GreetingRes(R.string.greeting_title_term_grain_rain, R.array.greeting_lines_term_grain_rain)
    SolarTerm.StartOfSummer -> GreetingRes(R.string.greeting_title_term_start_of_summer, R.array.greeting_lines_term_start_of_summer)
    SolarTerm.GrainBuds -> GreetingRes(R.string.greeting_title_term_grain_buds, R.array.greeting_lines_term_grain_buds)
    SolarTerm.GrainInEar -> GreetingRes(R.string.greeting_title_term_grain_in_ear, R.array.greeting_lines_term_grain_in_ear)
    SolarTerm.SummerSolstice -> GreetingRes(R.string.greeting_title_term_summer_solstice, R.array.greeting_lines_term_summer_solstice)
    SolarTerm.MinorHeat -> GreetingRes(R.string.greeting_title_term_minor_heat, R.array.greeting_lines_term_minor_heat)
    SolarTerm.MajorHeat -> GreetingRes(R.string.greeting_title_term_major_heat, R.array.greeting_lines_term_major_heat)
    SolarTerm.StartOfAutumn -> GreetingRes(R.string.greeting_title_term_start_of_autumn, R.array.greeting_lines_term_start_of_autumn)
    SolarTerm.EndOfHeat -> GreetingRes(R.string.greeting_title_term_end_of_heat, R.array.greeting_lines_term_end_of_heat)
    SolarTerm.WhiteDew -> GreetingRes(R.string.greeting_title_term_white_dew, R.array.greeting_lines_term_white_dew)
    SolarTerm.AutumnEquinox -> GreetingRes(R.string.greeting_title_term_autumn_equinox, R.array.greeting_lines_term_autumn_equinox)
    SolarTerm.ColdDew -> GreetingRes(R.string.greeting_title_term_cold_dew, R.array.greeting_lines_term_cold_dew)
    SolarTerm.FrostDescent -> GreetingRes(R.string.greeting_title_term_frost_descent, R.array.greeting_lines_term_frost_descent)
    SolarTerm.StartOfWinter -> GreetingRes(R.string.greeting_title_term_start_of_winter, R.array.greeting_lines_term_start_of_winter)
    SolarTerm.MinorSnow -> GreetingRes(R.string.greeting_title_term_minor_snow, R.array.greeting_lines_term_minor_snow)
    SolarTerm.MajorSnow -> GreetingRes(R.string.greeting_title_term_major_snow, R.array.greeting_lines_term_major_snow)
    SolarTerm.WinterSolstice -> GreetingRes(R.string.greeting_title_term_winter_solstice, R.array.greeting_lines_term_winter_solstice)
    SolarTerm.MinorCold -> GreetingRes(R.string.greeting_title_term_minor_cold, R.array.greeting_lines_term_minor_cold)
    SolarTerm.MajorCold -> GreetingRes(R.string.greeting_title_term_major_cold, R.array.greeting_lines_term_major_cold)
    SolarTerm.StartOfSpring -> GreetingRes(R.string.greeting_title_term_start_of_spring, R.array.greeting_lines_term_start_of_spring)
    SolarTerm.RainWater -> GreetingRes(R.string.greeting_title_term_rain_water, R.array.greeting_lines_term_rain_water)
    SolarTerm.AwakeningOfInsects -> GreetingRes(R.string.greeting_title_term_awakening_of_insects, R.array.greeting_lines_term_awakening_of_insects)
}
