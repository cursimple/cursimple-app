package com.x500x.cursimple.app.update

import android.content.Context

/**
 * 后台轮询检查更新的节流状态。
 *
 * 存 ETag 是为了发条件请求：服务端没变时回 304、不带响应体，一次几百字节。
 * 存上次检查时间是为了跨进程节流——轮询协程活在组合里，切后台再回来会重新起一个，
 * 只靠内存里的标志会导致每次切回前台都重查一遍。
 */
class UpdatePollStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次条件请求拿到的 ETag，按「是否含测试版」分开存，两条路查的是不同接口。 */
    fun etag(includePrerelease: Boolean): String? =
        prefs.getString(etagKey(includePrerelease), null)

    fun setEtag(includePrerelease: Boolean, etag: String?) {
        prefs.edit().apply {
            if (etag.isNullOrBlank()) remove(etagKey(includePrerelease)) else putString(etagKey(includePrerelease), etag)
        }.apply()
    }

    fun lastPeekAtMillis(): Long = prefs.getLong(KEY_LAST_PEEK_AT, 0L)

    fun setLastPeekAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_PEEK_AT, millis).apply()
    }

    fun lastFullCheckAtMillis(): Long = prefs.getLong(KEY_LAST_FULL_CHECK_AT, 0L)

    fun setLastFullCheckAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_FULL_CHECK_AT, millis).apply()
    }

    /** 换了版本就把 ETag 作废：装上新版后同一个 304 不再代表「我已经是最新」。 */
    fun invalidateFor(versionCode: Int) {
        if (prefs.getInt(KEY_VERSION_CODE, -1) == versionCode) return
        prefs.edit()
            .putInt(KEY_VERSION_CODE, versionCode)
            .remove(etagKey(true))
            .remove(etagKey(false))
            .putLong(KEY_LAST_PEEK_AT, 0L)
            .putLong(KEY_LAST_FULL_CHECK_AT, 0L)
            .apply()
    }

    private fun etagKey(includePrerelease: Boolean) =
        if (includePrerelease) KEY_ETAG_PRERELEASE else KEY_ETAG_STABLE

    private companion object {
        const val PREFS_NAME = "app_update_poll"
        const val KEY_ETAG_STABLE = "etag_stable"
        const val KEY_ETAG_PRERELEASE = "etag_prerelease"
        const val KEY_LAST_PEEK_AT = "last_peek_at"
        const val KEY_LAST_FULL_CHECK_AT = "last_full_check_at"
        const val KEY_VERSION_CODE = "version_code"
    }
}

/** 轮询协程多久醒一次。醒来只查表决定要不要发请求，本身不产生流量。 */
const val UPDATE_POLL_TICK_MILLIS: Long = 60 * 1000L

/** 轮询节拍：两次条件请求之间至少隔这么久。 */
const val UPDATE_PEEK_INTERVAL_MILLIS: Long = 15 * 60 * 1000L

/**
 * 条件请求探不通时，退回完整检查的最小间隔。
 *
 * 源站被墙时 [AppUpdateChecker.peek] 一直给 Unknown，此时只能靠走镜像的完整检查兜底；
 * 那一趟流量大得多，所以间隔放宽到几小时，不跟着轮询节拍走。
 */
const val UPDATE_FULL_CHECK_FALLBACK_MILLIS: Long = 6 * 60 * 60 * 1000L

/**
 * 这一轮该做什么。
 *
 * 分三种：什么都不用做、发一次条件请求、直接跑完整检查。
 */
enum class UpdatePollAction { Skip, Peek, FullCheck }

/**
 * 依据上次探测/完整检查的时间决定这一轮的动作。
 *
 * 从没查过时先跑一次完整检查：此时既没有 ETag 可比，也还不知道有没有新版，
 * 条件请求只会白跑一趟。
 */
fun updatePollAction(
    nowMillis: Long,
    lastPeekAtMillis: Long,
    lastFullCheckAtMillis: Long,
    hasEtag: Boolean,
    peekIntervalMillis: Long = UPDATE_PEEK_INTERVAL_MILLIS,
    fullCheckFallbackMillis: Long = UPDATE_FULL_CHECK_FALLBACK_MILLIS,
): UpdatePollAction = when {
    lastFullCheckAtMillis <= 0L -> UpdatePollAction.FullCheck
    // 没有 ETag 就发不出有意义的条件请求，等冷却期到了直接走完整检查
    !hasEtag && nowMillis - lastFullCheckAtMillis >= fullCheckFallbackMillis -> UpdatePollAction.FullCheck
    !hasEtag -> UpdatePollAction.Skip
    nowMillis - lastPeekAtMillis >= peekIntervalMillis -> UpdatePollAction.Peek
    else -> UpdatePollAction.Skip
}
