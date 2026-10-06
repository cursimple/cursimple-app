package com.x500x.cursimple.app.update

import android.content.Context

/** Persist automatic-check timing across foreground coroutine restarts. */
class UpdatePollStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastCheckAtMillis(): Long = prefs.getLong(KEY_LAST_CHECK_AT, 0L)

    fun setLastCheckAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, millis).apply()
    }

    fun lastFailureAtMillis(): Long = prefs.getLong(KEY_LAST_FAILURE_AT, 0L)

    fun setLastFailureAtMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_FAILURE_AT, millis).apply()
    }

    fun lastCheckIncludedPrerelease(): Boolean? =
        if (prefs.contains(KEY_LAST_CHANNEL_BETA)) prefs.getBoolean(KEY_LAST_CHANNEL_BETA, false) else null

    fun setLastCheckIncludedPrerelease(includePrerelease: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_CHANNEL_BETA, includePrerelease).apply()
    }

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
 * Whether this process has performed its initial check; startup bypasses the periodic interval.
 */
object UpdateAutoCheckSession {
    @Volatile
    var checkedThisLaunch: Boolean = false
}

const val UPDATE_POLL_TICK_MILLIS: Long = 60 * 1000L

const val UPDATE_RECHECK_INTERVAL_MILLIS: Long = 15 * 60 * 1000L

const val UPDATE_RETRY_AFTER_FAILURE_MILLIS: Long = 5 * 60 * 1000L

/**
 * Check initially, retry failures after [retryAfterFailureMillis], and recheck successes after
 * [recheckIntervalMillis].
 */
fun autoUpdateCheckDue(
    nowMillis: Long,
    checkedThisLaunch: Boolean,
    lastCheckAtMillis: Long,
    lastFailureAtMillis: Long,
    channelChanged: Boolean = false,
    recheckIntervalMillis: Long = UPDATE_RECHECK_INTERVAL_MILLIS,
    retryAfterFailureMillis: Long = UPDATE_RETRY_AFTER_FAILURE_MILLIS,
): Boolean = when {
    !checkedThisLaunch || channelChanged -> true
    lastFailureAtMillis > lastCheckAtMillis -> nowMillis - lastFailureAtMillis >= retryAfterFailureMillis
    else -> nowMillis - lastCheckAtMillis >= recheckIntervalMillis
}
