package com.x500x.cursimple.app.download

import android.content.Context
import java.net.URI

/**
 * 记住每类下载上次走通的镜像，跨进程与跨实例共享。
 *
 * 记住的是镜像的名字而不是具体地址：更新检查这类请求的地址随版本号变化，
 * 按地址记就永远命中不了。只在两种情况下重新试全部镜像：从来没有记录（首次），
 * 或记住的镜像失效（下载失败时清除）。探测延迟按镜像域名缓存，有效期内不重复测速。
 */
interface MirrorPreferenceStore {
    /** 上次走通的镜像名；没有或刚失效时为 null。 */
    fun preferred(cacheKey: String): String?

    fun recordSuccess(cacheKey: String, sourceName: String)

    /** 记住的镜像下载失败时调用，清除记录让下次重新竞速。 */
    fun recordFailure(cacheKey: String, sourceName: String)

    /** 缓存的探测延迟；超出有效期或从未探测时为 null。 */
    fun probeLatency(mirrorHost: String): Long?

    /** latencyMillis 为 null 表示探测失败。 */
    fun recordProbe(mirrorHost: String, latencyMillis: Long?)

    /** 下载失败时连探测缓存一并清掉，避免按陈旧延迟再选中坏镜像。 */
    fun invalidate(mirrorHost: String)

    companion object {
        const val PROBE_CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000

        /** 按用途加原始地址的域名区分：同为 GitHub 下载，API、raw 与安装包各自可用的镜像不同。 */
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
}
