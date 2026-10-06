package com.x500x.cursimple.app.download

import android.content.Context
import java.net.URI

/**
 * Persist preferred mirrors by source name and download purpose, not version-specific URLs.
 * Cache probe latency by host.
 */
interface MirrorPreferenceStore {
    fun preferred(cacheKey: String): String?

    fun recordSuccess(cacheKey: String, sourceName: String)

    /** Clear a failed preferred source before the next race. */
    fun recordFailure(cacheKey: String, sourceName: String)

    /** Null for missing or expired probe measurements. */
    fun probeLatency(mirrorHost: String): Long?

    /** Null latency marks a failed probe. */
    fun recordProbe(mirrorHost: String, latencyMillis: Long?)

    /** Clear latency with download failure to avoid selecting a stale result. */
    fun invalidate(mirrorHost: String)
    /** Temporarily downgrade failed text hosts. */
    fun textFailureUntil(mirrorHost: String): Long = 0L
    fun recordTextFailure(mirrorHost: String) = Unit

    /**
     * Smoothed throughput in KiB/s; null when unknown or stale. Throughput guides selection
     * independently of header latency.
     */
    fun speedKBps(mirrorHost: String): Long? = null
    fun recordSpeed(mirrorHost: String, kBps: Long) = Unit

    /** File-transfer failure cooldown deadline. */
    fun downloadFailureUntil(mirrorHost: String): Long = 0L
    fun recordDownloadFailure(mirrorHost: String) = Unit

    companion object {
        const val PROBE_CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000

        /** Expire measurements as network conditions change. */
        const val SPEED_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** Weight new samples without letting transient jitter dominate ranking. */
        const val SPEED_SMOOTHING = 0.5

        const val DOWNLOAD_COOLDOWN_MILLIS = 5L * 60 * 1000

        fun smoothSpeed(previous: Long?, measured: Long): Long =
            if (previous == null || previous <= 0L) measured
            else (previous * (1 - SPEED_SMOOTHING) + measured * SPEED_SMOOTHING).toLong().coerceAtLeast(1L)

        /** Key preferences by purpose and origin host; API, raw and release routes differ. */
        fun cacheKeyOf(request: DownloadRequest): String {
            val host = hostOf(request.url)
            return "${request.purpose}:$host"
        }

        fun hostOf(url: String): String {
            return runCatching { URI(url).host }.getOrNull().orEmpty()
        }

        fun isProbeFresh(recordedAtMillis: Long, nowMillis: Long): Boolean {
            return nowMillis - recordedAtMillis in 0 until PROBE_CACHE_TTL_MILLIS
        }
    }
}

class SharedPrefsMirrorPreferenceStore(context: Context) : MirrorPreferenceStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("mirror_preferences", Context.MODE_PRIVATE)

    override fun preferred(cacheKey: String): String? {
        return prefs.getString("preferred:$cacheKey", null)
    }

    override fun recordSuccess(cacheKey: String, sourceName: String) {
        prefs.edit().putString("preferred:$cacheKey", sourceName).apply()
    }

    override fun recordFailure(cacheKey: String, sourceName: String) {
        if (prefs.getString("preferred:$cacheKey", null) == sourceName) {
            prefs.edit().remove("preferred:$cacheKey").apply()
        }
    }

    override fun probeLatency(mirrorHost: String): Long? {
        val recordedAt = prefs.getLong("probeAt:$mirrorHost", 0L)
        if (!MirrorPreferenceStore.isProbeFresh(recordedAt, System.currentTimeMillis())) {
            return null
        }
        if (!prefs.contains("probeMs:$mirrorHost")) return null
        return prefs.getLong("probeMs:$mirrorHost", Long.MAX_VALUE)
    }

    override fun recordProbe(mirrorHost: String, latencyMillis: Long?) {
        prefs.edit()
            .putLong("probeMs:$mirrorHost", latencyMillis ?: Long.MAX_VALUE)
            .putLong("probeAt:$mirrorHost", System.currentTimeMillis())
            .apply()
    }

    override fun invalidate(mirrorHost: String) {
        prefs.edit()
            .remove("probeMs:$mirrorHost")
            .remove("probeAt:$mirrorHost")
            .apply()
    }
    override fun speedKBps(mirrorHost: String): Long? {
        val at = prefs.getLong("speedAt:$mirrorHost", 0L)
        if (System.currentTimeMillis() - at !in 0 until MirrorPreferenceStore.SPEED_TTL_MILLIS) return null
        return prefs.getLong("speedKBps:$mirrorHost", 0L).takeIf { it > 0L }
    }

    override fun recordSpeed(mirrorHost: String, kBps: Long) {
        if (mirrorHost.isBlank() || kBps <= 0L) return
        val merged = MirrorPreferenceStore.smoothSpeed(speedKBps(mirrorHost), kBps)
        prefs.edit().putLong("speedKBps:$mirrorHost", merged).putLong("speedAt:$mirrorHost", System.currentTimeMillis()).apply()
    }

    override fun downloadFailureUntil(mirrorHost: String): Long = prefs.getLong("downloadFailedUntil:$mirrorHost", 0L)
    override fun recordDownloadFailure(mirrorHost: String) {
        prefs.edit().putLong("downloadFailedUntil:$mirrorHost", System.currentTimeMillis() + MirrorPreferenceStore.DOWNLOAD_COOLDOWN_MILLIS).apply()
    }

    override fun textFailureUntil(mirrorHost: String): Long = prefs.getLong("textFailedUntil:$mirrorHost", 0L)
    override fun recordTextFailure(mirrorHost: String) {
        prefs.edit().putLong("textFailedUntil:$mirrorHost", System.currentTimeMillis() + 5 * 60_000L).apply()
    }
}
