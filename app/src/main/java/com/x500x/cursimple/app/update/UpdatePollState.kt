package com.x500x.cursimple.app.update

import android.content.Context

/**
 * 自动检查更新的节流状态。
 *
 * 存上次检查的时间是为了跨进程节流：检查协程活在组合里，切后台再回来会重新起一个，
 * 只靠内存里的标志会导致每次切回前台都重查一遍。
 */
class UpdatePollStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次顺利查完（有新版、已是最新、没有发布都算）的时间。 */
    fun lastCheckAtMillis(): Long = prefs.getLong(KEY_LAST_CHECK_AT, 0L)

    fun setLastCheckAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, millis).apply()
    }

    /** 上次没查成（没网、镜像全挂）的时间。 */
    fun lastFailureAtMillis(): Long = prefs.getLong(KEY_LAST_FAILURE_AT, 0L)

    fun setLastFailureAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_FAILURE_AT, millis).apply()
    }

    /** 上次查的是不是含测试版的那条；换了通道时上次的结果不作数，得马上重查。 */
    fun lastCheckIncludedPrerelease(): Boolean? =
        if (prefs.contains(KEY_LAST_CHANNEL_BETA)) prefs.getBoolean(KEY_LAST_CHANNEL_BETA, false) else null

    fun setLastCheckIncludedPrerelease(includePrerelease: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_CHANNEL_BETA, includePrerelease).apply()
    }

    /** 换了版本就从头算：装上新版后，上一版查到的结果不再作数。 */
    fun invalidateFor(versionCode: Int) {
        if (prefs.getInt(KEY_VERSION_CODE, -1) == versionCode) return
        prefs.edit()
            .putInt(KEY_VERSION_CODE, versionCode)
            .putLong(KEY_LAST_CHECK_AT, 0L)
            .putLong(KEY_LAST_FAILURE_AT, 0L)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "app_update_poll"
        const val KEY_LAST_CHECK_AT = "last_full_check_at"
        const val KEY_LAST_FAILURE_AT = "last_failure_at"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_LAST_CHANNEL_BETA = "last_channel_beta"
    }
}

/**
 * 这次启动（进程）里有没有自动查过更新。
 *
 * 每次打开 App 都查一次，不受 [UPDATE_RECHECK_INTERVAL_MILLIS] 限制：检查现在只读一份几百字节的
 * 版本清单，便宜得很，而用户最希望的就是一打开就知道有没有新版。
 */
object UpdateAutoCheckSession {
    @Volatile
    var checkedThisLaunch: Boolean = false
}

/** 检查协程多久醒一次看看到没到该查的时候。醒来本身不产生流量。 */
const val UPDATE_POLL_TICK_MILLIS: Long = 60 * 1000L

/** App 开着或切回前台时，距上次查完至少隔这么久才再查。 */
const val UPDATE_RECHECK_INTERVAL_MILLIS: Long = 30 * 60 * 1000L

/** 上次没查成时，隔这么久再试。 */
const val UPDATE_RETRY_AFTER_FAILURE_MILLIS: Long = 5 * 60 * 1000L

/**
 * 现在该不该自动查一次更新。
 *
 * 这次启动还没查过就查；查过了就看上一次的结果：没查成的隔 [retryAfterFailureMillis] 重试，
 * 查成了的隔 [recheckIntervalMillis] 再查。
 *
 * 以前是先向 api.github.com 发条件请求探「有没有变」，变了才完整检查；但那个接口在国内
 * 常常连不上，探测一直给「不知道」，于是再也不跑完整检查，有新版也从不弹窗。
 */
fun autoUpdateCheckDue(
    nowMillis: Long,
    checkedThisLaunch: Boolean,
    lastCheckAtMillis: Long,
    lastFailureAtMillis: Long,
    /** 刚切换了「测试版更新」开关：上次查的是另一条通道，结果不作数。 */
    channelChanged: Boolean = false,
    recheckIntervalMillis: Long = UPDATE_RECHECK_INTERVAL_MILLIS,
    retryAfterFailureMillis: Long = UPDATE_RETRY_AFTER_FAILURE_MILLIS,
): Boolean = when {
    !checkedThisLaunch || channelChanged -> true
    lastFailureAtMillis > lastCheckAtMillis -> nowMillis - lastFailureAtMillis >= retryAfterFailureMillis
    else -> nowMillis - lastCheckAtMillis >= recheckIntervalMillis
}
